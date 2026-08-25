package sim;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;

// Resident browser-side runner: one long-lived JVM that keeps the ECJ compiler warm and
// services N independent alliance slots (red=0, blue=1). Each slot polls JS for lifecycle
// commands (init/start/stop), compiles its own staged source in-process, hot-loads the
// OpMode, and drives the FTC INIT -> START -> STOP lifecycle while sampling the drivetrain
// to publish chassis velocity + mechanism state to JS. Slots run concurrently: two OpMode
// worker threads and two samplers share the browser main thread cooperatively.
public class OpModeHost {

    // Every native takes a leading slot index so JS can route to the right alliance instance.
    // JS -> Java gamepad axes (-1..1); left_stick_y is inverted (up = -1) like a real pad.
    static native double gpLeftX(int slot);
    static native double gpLeftY(int slot);
    static native double gpRightX(int slot);
    static native double gpRightY(int slot);
    static native double gpLeftTrigger(int slot);    // 0..1
    static native double gpRightTrigger(int slot);   // 0..1
    static native boolean gpRightBumper(int slot);
    static native int gpButtons(int slot);           // packed digital-button bitmask (see decode below)
    static native boolean opActive(int slot);     // JS: should this slot's run keep going
    static native int pollCommand(int slot);      // JS: 0 none, 1 init, 2 start, 3 stop (one-shot)

    // JS -> Java ground-truth pose readback (for the simulated Pinpoint, so a Pedro Follower
    // gets real closed-loop feedback). World meters/radians; axis convention documented on
    // GoBildaPinpointDriverImpl (xFtc=worldZ, yFtc=worldX, heading=yaw -- an axis relabel, so
    // velocity maps the same way with no rotation needed).
    static native double simX(int slot);
    static native double simZ(int slot);
    static native double simYaw(int slot);
    static native double simVelX(int slot);
    static native double simVelZ(int slot);
    static native double simYawRate(int slot);

    // Java -> JS chassis velocity, robot frame (m/s, m/s, rad/s CCW+).
    static native void publish(int slot, double vx, double vy, double omega);
    // Java -> JS mechanism state so the sim can animate them (intake/feeder/shooter spin, hood pos).
    static native void mech(int slot, double intake, double feeder, double shooter, double hood);
    // Java -> JS telemetry snapshot for the HUD.
    static native void telemetry(int slot, String text);
    // Java -> JS run state: 1 compiling, 2 initialized, 3 running, 4 stopped, 5 error.
    static native void status(int slot, int code, String msg);

    static final String DEFAULT_FQN = "org.firstinspires.ftc.teamcode.MecanumTeleOp";
    static final double SHOOTER_MAXV = 30.0;   // rad/s that maps to a full-speed flywheel visual
    static final int SLOTS = 2;                // alliance slots: 0 = red, 1 = blue

    // /app/ maps to the web-server origin root; when hosted under a subpath the jars live in a
    // subdirectory, so the browser passes that prefix (e.g. "impulse3dsim/") as the first arg.
    static String APP = "/app/";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && !args[0].isEmpty()) {
            String p = args[0];
            if (!p.endsWith("/")) p += "/";
            APP = "/app/" + p;
        }
        Runner[] runners = new Runner[SLOTS];
        for (int i = 0; i < SLOTS; i++) runners[i] = new Runner(i);
        System.out.println("RUNNER_READY slots=" + SLOTS);
        for (int i = 0; i < SLOTS; i++) status(i, 0, "idle");
        while (true) {
            for (Runner r : runners) {
                try { r.service(); }
                catch (Throwable t) { t.printStackTrace(); status(r.slot, 5, String.valueOf(t)); }
            }
            Thread.sleep(20);
        }
    }

    // One independent alliance instance: its own lifecycle, compile output, op, and sampler.
    // Sources stage to a shared flat /str/ (CheerpJ's string mount has no subdirs) so JS must
    // serialize INIT across slots; each slot compiles to its own /files/<slot>/ so the two slots'
    // loaded classes never collide even when both are running at once.
    static final class Runner {
        final int slot;
        final String strDir, outDir;
        volatile boolean running = false;
        Thread sampler;
        LinearOpMode current;

        Runner(int slot) {
            this.slot = slot;
            this.strDir = "/str/";
            this.outDir = "/files/" + slot + "/";
        }

        // Poll JS for a lifecycle command, then watch for a student crash or a self-ended OpMode.
        void service() throws Exception {
            int cmd = pollCommand(slot);
            if (cmd == 1) init();
            else if (cmd == 2) start();
            else if (cmd == 3) { stop(); status(slot, 4, "stopped"); }

            if (running && current != null) {
                Throwable ex = current.getUncaughtException();
                Thread t = current.getOpModeThread();
                if (ex != null) {
                    System.out.println("OPMODE_CRASH[" + slot + "] " + ex);
                    stop();
                    status(slot, 5, "runtime: " + ex);
                } else if (t != null && !t.isAlive() && current.isStarted()) {
                    System.out.println("OPMODE_ENDED[" + slot + "]");
                    stop();
                    status(slot, 4, "stopped");
                }
            }
        }

        String readFqn() {
            try { return new String(Files.readAllBytes(new File(strDir + "opclass.txt").toPath())).trim(); }
            catch (Exception e) { return DEFAULT_FQN; }
        }

        // All .java files the browser staged for this slot (compiled together as one unit).
        // Prefer the browser-written manifest (files.txt) so files left over from a previously
        // loaded package are never dragged into the compile; fall back to globbing if absent.
        String[] stagedSources() {
            try {
                String list = new String(Files.readAllBytes(new File(strDir + "files.txt").toPath())).trim();
                if (!list.isEmpty()) {
                    String[] names = list.split("\\R");
                    String[] paths = new String[names.length];
                    for (int i = 0; i < names.length; i++) paths[i] = strDir + names[i].trim();
                    return paths;
                }
            } catch (Exception ignored) {}
            File[] fs = new File(strDir).listFiles((d, n) -> n.endsWith(".java"));
            if (fs == null || fs.length == 0) return new String[0];
            String[] paths = new String[fs.length];
            for (int i = 0; i < fs.length; i++) paths[i] = fs[i].getPath();
            return paths;
        }

        // In-process ECJ compile of the staged package sources. Returns null on success, else error text.
        String compile(String[] srcFiles) {
            new File(outDir).mkdirs();
            String[] fixed = {
                "-source", "1.8", "-target", "1.8",
                "-bootclasspath", APP + "jdk-base.jar", "-classpath", APP + "shim.jar:" + APP + "pedro-core.jar",
                "-proc:none", "-nowarn", "-d", outDir };
            String[] a = new String[fixed.length + srcFiles.length];
            System.arraycopy(fixed, 0, a, 0, fixed.length);
            System.arraycopy(srcFiles, 0, a, fixed.length, srcFiles.length);
            StringWriter out = new StringWriter(), err = new StringWriter();
            org.eclipse.jdt.internal.compiler.batch.Main m =
                new org.eclipse.jdt.internal.compiler.batch.Main(
                    new PrintWriter(out), new PrintWriter(err), false, null, null);
            boolean ok = m.compile(a);
            return ok ? null : (err.toString() + out.toString());
        }

        // INIT: compile, construct, start the worker (blocks at waitForStart), begin sampling.
        void init() throws Exception {
            stop();
            status(slot, 1, "compiling");
            String fqn = readFqn();
            long t0 = System.currentTimeMillis();
            String err = compile(stagedSources());
            long ms = System.currentTimeMillis() - t0;
            if (err != null) {
                System.out.println("COMPILE_ERR[" + slot + "] (" + ms + "ms)\n" + err);
                status(slot, 5, err);   // full ECJ text; the browser parses line numbers into editor squiggles
                return;
            }
            System.out.println("COMPILE_OK[" + slot + "] " + ms + "ms");

            HardwareMap hw = new HardwareMap();
            SimMotor fl = new SimMotor("leftFront");
            SimMotor fr = new SimMotor("rightFront");
            SimMotor bl = new SimMotor("leftBack");
            SimMotor br = new SimMotor("rightBack");
            hw.put("leftFront", fl); hw.put("rightFront", fr);
            hw.put("leftBack", bl);  hw.put("rightBack", br);

            // mechanism actuators the student code drives (intake/feeder/shooter/hood)
            final SimMotor intake   = new SimMotor("intake");
            final SimCRServo feeder = new SimCRServo("feeder");
            final SimMotor shooter  = new SimMotor("shooter");
            final SimServo hood     = new SimServo("hood");
            hw.put("intake", intake); hw.put("feeder", feeder);
            hw.put("shooter", shooter); hw.put("hood", hood);

            // DUMPER robot type -- the names the v2 course and competition_code use.
            // The four drive motors are aliased to the SAME SimMotor instances the
            // physics reads, so RobotConfig's snake_case lookups resolve to the real
            // wheels. The intake shares the animated intake motor; the dump servo is
            // its own actuator. (The shooter names above stay registered too, so the
            // shooter robot type keeps working.)
            hw.put("front_left_motor", fl);  hw.put("front_right_motor", fr);
            hw.put("back_left_motor", bl);   hw.put("back_right_motor", br);
            hw.put("intake_motor", intake);
            final SimServo dumpServo = new SimServo("dump_servo");
            hw.put("dump_servo", dumpServo);

            // simulated goBILDA Pinpoint -- ground-truth-backed, for Pedro Pathing captured autos
            final GoBildaPinpointDriverImpl pinpoint = new GoBildaPinpointDriverImpl(slot);
            hw.put("pinpoint", pinpoint);

            // simulated Control Hub IMU -- ground-truth yaw, used for field-centric TeleOp heading
            final IMUImpl imu = new IMUImpl(slot);
            hw.put("imu", imu);

            // fresh classloader each run so an edited+recompiled class is picked up
            ClassLoader loader = new FilesLoader(OpModeHost.class.getClassLoader(), outDir);
            final LinearOpMode op = (LinearOpMode) loader.loadClass(fqn)
                    .getDeclaredConstructor().newInstance();
            op.hardwareMap = hw;
            SimTelemetry st = new SimTelemetry();
            st.out = text -> telemetry(slot, text);   // route this slot's telemetry to its own HUD
            op.telemetry = st;
            current = op;

            final SimDrivetrain drive = new SimDrivetrain(fl, fr, bl, br);
            running = true;
            op.internalStart();   // worker runs runOpMode() up to waitForStart()
            System.out.println("HOST_INIT[" + slot + "] op=" + fqn);
            status(slot, 2, "initialized");

            final int s = slot;
            sampler = new Thread(() -> {
                long last = System.nanoTime(), samples = 0;
                while (running && opActive(s)) {
                    long now = System.nanoTime();
                    double dt = (now - last) / 1e9;
                    if (dt <= 0) dt = 1e-3;
                    last = now;

                    op.gamepad1.left_stick_x  = (float) gpLeftX(s);
                    op.gamepad1.left_stick_y  = (float) gpLeftY(s);
                    op.gamepad1.right_stick_x = (float) gpRightX(s);
                    op.gamepad1.right_stick_y = (float) gpRightY(s);
                    op.gamepad1.left_trigger  = (float) gpLeftTrigger(s);
                    op.gamepad1.right_trigger = (float) gpRightTrigger(s);
                    op.gamepad1.right_bumper  = gpRightBumper(s);
                    int gb = gpButtons(s);   // bit layout must match index.html padButtons()
                    op.gamepad1.a                 = (gb & (1 << 0))  != 0;
                    op.gamepad1.b                 = (gb & (1 << 1))  != 0;
                    op.gamepad1.x                 = (gb & (1 << 2))  != 0;
                    op.gamepad1.y                 = (gb & (1 << 3))  != 0;
                    op.gamepad1.left_bumper       = (gb & (1 << 4))  != 0;
                    op.gamepad1.dpad_up           = (gb & (1 << 5))  != 0;
                    op.gamepad1.dpad_down         = (gb & (1 << 6))  != 0;
                    op.gamepad1.dpad_left         = (gb & (1 << 7))  != 0;
                    op.gamepad1.dpad_right        = (gb & (1 << 8))  != 0;
                    op.gamepad1.left_stick_button = (gb & (1 << 9))  != 0;
                    op.gamepad1.right_stick_button= (gb & (1 << 10)) != 0;
                    op.gamepad1.start             = (gb & (1 << 11)) != 0;
                    op.gamepad1.back              = (gb & (1 << 12)) != 0;
                    op.gamepad1.guide             = (gb & (1 << 13)) != 0;

                    ChassisVelocity cv = drive.sample(dt);
                    publish(s, cv.vx(), cv.vy(), cv.omega());
                    pinpoint.simUpdate();   // refresh ground-truth pose so a Pedro Follower reading pinpoint.update() sees fresh data

                    double shv = shooter.getVelocity();
                    double shooterSpin = Math.abs(shv) > 1e-6 ? shv / SHOOTER_MAXV : shooter.getEffectivePower();
                    shooterSpin = Math.max(-1.0, Math.min(1.0, shooterSpin));
                    mech(s, intake.getEffectivePower(), feeder.getEffectivePower(), shooterSpin, hood.getPosition());

                    samples++;
                    // ~60 Hz control rate; under CheerpJ this loop shares the browser main thread, so a tighter sleep starves rendering.
                    try { Thread.sleep(16); } catch (InterruptedException e) { break; }
                }
                System.out.println("HOST_DONE[" + s + "] samples=" + samples);
            }, "sampler-" + slot);
            sampler.start();
        }

        // START: release waitForStart() so the student loop begins driving.
        void start() {
            if (current != null) {
                current.internalNotifyStart();
                System.out.println("HOST_START[" + slot + "]");
                status(slot, 3, "running");
            }
        }

        void stop() {
            running = false;
            if (current != null) current.internalRequestStop();
            if (sampler != null) {
                sampler.interrupt();
                try { sampler.join(500); } catch (InterruptedException e) {}
                sampler = null;
            }
            if (current != null) {
                Thread t = current.getOpModeThread();
                if (t != null) { t.interrupt(); try { t.join(500); } catch (InterruptedException e) {} }
                current = null;
            }
            publish(slot, 0, 0, 0);
        }
    }

    // First meaningful ECJ line: skip blank lines and the "----------" dividers.
    static String firstLine(String s) {
        for (String raw : s.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("----")) continue;
            return line.length() > 120 ? line.substring(0, 120) : line;
        }
        return "compile error";
    }

    // Loads only the freshly-compiled student class from the slot's output dir; delegates
    // everything else (shim + sim types) to the parent so casts stay type-compatible across reruns.
    static final class FilesLoader extends ClassLoader {
        private final String outDir;
        FilesLoader(ClassLoader parent, String outDir) { super(parent); this.outDir = outDir; }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                File f = new File(outDir + name.replace('.', '/') + ".class");
                byte[] b = Files.readAllBytes(f.toPath());
                return defineClass(name, b, 0, b.length);
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
