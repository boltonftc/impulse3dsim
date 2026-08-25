package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.ArrayList;
import java.util.List;

/*
 * ============================================================================
 *  Robot  --  the container that owns every subsystem
 * ============================================================================
 *
 *  WHY THIS CLASS EXISTS
 *  Instead of every OpMode building the drive, intake, dumper, and
 *  localization separately, we build them ONCE here. Then an OpMode just
 *  writes:
 *
 *        Robot robot = new Robot(hardwareMap);
 *
 *  ...and it instantly has the whole robot. TeleOp and Autonomous share the
 *  exact same subsystems, so a mechanism a student writes works everywhere.
 *
 *  THE SCHEDULER LIVES HERE
 *  Robot holds a List of every Subsystem. Its update() method walks that
 *  list and calls update() on each one. When the main loop calls
 *  robot.update() once per cycle, every mechanism gets a turn to advance its
 *  own state by one small slice. That single loop is the "scheduler" -- and
 *  it runs on ONE thread, so we never need multithreading.
 * ============================================================================
 */
public class Robot {

    // ── The subsystems, each owned by a different student ────────────────
    // These are `public` so an OpMode can call, e.g., robot.intake.forward().
    // `final` means once built in the constructor, the reference never changes.
    public final Drivebase drive;
    public final Intake intake;
    public final Dumper dumper;
    public final Localization localization;

    // The scheduler's to-do list: every subsystem that needs update() called.
    private final List<Subsystem> subsystems = new ArrayList<>();

    /*
     * CONSTRUCTOR
     * A constructor is the special method that runs ONCE when you write
     * `new Robot(hardwareMap)`. Its job here is to build every subsystem
     * (handing each one the hardwareMap so it can find its motors/servos)
     * and add them all to the scheduler list.
     *
     * `hardwareMap` is the robot's "phone book": you ask it for a device by
     * name (the same name shown in the Driver Hub configuration) and it hands
     * you back that motor, servo, or sensor.
     */
    public Robot(HardwareMap hardwareMap) {
        drive        = new Drivebase(hardwareMap);
        intake       = new Intake(hardwareMap);
        dumper       = new Dumper(hardwareMap);
        localization = new Localization(hardwareMap);

        // Register each subsystem with the scheduler. Order matters a little:
        // we update localization first so the freshest sensor data is ready
        // for the rest of the loop.
        subsystems.add(localization);
        subsystems.add(drive);
        subsystems.add(intake);
        subsystems.add(dumper);
    }

    /*
     * update()  --  THE SCHEDULER
     * Call this ONCE at the top of every loop. It gives every subsystem a
     * turn to do its small slice of work. Because each subsystem's update()
     * returns quickly (no waiting), all mechanisms make progress "at the same
     * time" even though we only have one thread.
     */
    public void update() {
        for (Subsystem subsystem : subsystems) {
            subsystem.update();
        }
    }

    /*
     * isBusy()  --  is any mechanism still finishing a timed action?
     * Autonomous uses this to decide when to wait. It reports true while any
     * subsystem is in the middle of a self-completing action -- right now that
     * is only the Dumper's raise/hold/lower cycle.
     *
     * The intake is deliberately NOT counted: it runs steadily until told to
     * stop, so it has no "finishing" moment to wait for. Only transient
     * actions (ones that start, run for a bit, and end on their own) belong
     * here. As students add more mechanisms with cycles, they OR them in:
     *     return dumper.isBusy() || lift.isBusy() || ...;
     */
    public boolean isBusy() {
        return dumper.isBusy();
    }
}
