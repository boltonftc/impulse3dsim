package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

/*
 * ============================================================================
 *  MecanumDrive  --  the driver-controlled OpMode (TeleOp)
 * ============================================================================
 *
 *  This is the program a driver runs during the match. Notice how SHORT it is:
 *  all the real work lives inside the subsystems (Drivebase, Intake, Dumper,
 *  Localization). This OpMode's only jobs are:
 *
 *     1. build the Robot
 *     2. every loop: run the scheduler, drive, and read the buttons
 *
 *  This is the payoff of the subsystem design -- the OpMode reads almost like
 *  a plain-English description of the controls.
 *
 *  CONTROLS (gamepad1):
 *     Left stick .......... drive and strafe
 *     Triggers ............ turn (right = clockwise, left = counter-clockwise)
 *     A ................... toggle intake FORWARD / off
 *     B ................... toggle intake REVERSE / off
 *     Y ................... run one full dump cycle
 *     Right bumper ........ reset heading (re-zero field-centric "forward")
 * ============================================================================
 */
@TeleOp(name = "MecanumDrive (OOP)", group = "Dumper OOP")
public class MecanumDrive extends LinearOpMode {

    /*
     * EDGE DETECTION
     * A button is "pressed" for many loops in a row (a human holds it for a
     * fraction of a second, which is dozens of loops). If we acted every loop
     * the button is down, the intake would flip on/off dozens of times per
     * press.
     *
     * The fix: remember whether the button was down LAST loop. We only act on
     * the moment it goes from up -> down (a "rising edge" -- a fresh press).
     * These variables hold last loop's state for each button we care about.
     *
     * THIS IS "THE INPUT LAYER." Notice that the toggle behavior lives HERE,
     * in the OpMode, not inside the Intake subsystem. The subsystem only knows
     * how to be forward/off/reverse; the decision that "tapping A should
     * toggle off<->forward" is an input-layer choice. That separation is why
     * the same intake commands also work cleanly in autonomous.
     */
    private boolean previousA = false;
    private boolean previousB = false;
    private boolean previousY = false;
    private boolean previousRightBumper = false;

    @Override
    public void runOpMode() {
        /*
         * Build the whole robot. This runs every constructor -- finding motors,
         * configuring the Pinpoint, etc. -- so it must happen during INIT,
         * before the match starts. HOLD THE ROBOT STILL: the Pinpoint
         * calibrates its gyro in here.
         */
        Robot robot = new Robot(hardwareMap);

        // FTC Dashboard handle (used in the telemetry section below). Safe in
        // the sim, where it is a no-op; live on a real Control Hub.
        FtcDashboard dashboard = FtcDashboard.getInstance();

        telemetry.addLine("Initialized. Hold the robot still, then press START.");
        telemetry.update();

        // waitForStart() pauses here until the driver presses the START button.
        // This is allowed to block because the match has not begun yet.
        waitForStart();

        // ── THE MAIN LOOP ───────────────────────────────────────────────
        // Runs over and over (~50 times per second) until the match ends or
        // the driver presses STOP. Everything inside must be non-blocking so
        // the loop stays fast and the controls stay responsive.
        while (opModeIsActive()) {

            // 1) RUN THE SCHEDULER. This calls update() on every subsystem:
            //    localization reads fresh sensor data, and the intake and
            //    dumper advance their state machines by one small slice.
            robot.update();

            // 2) DRIVE. Read the latest heading from localization and hand the
            //    gamepad to the drivebase, which applies the wheel powers now.
            double headingRadians = robot.localization.getHeadingRadians();
            robot.drive.driveFieldCentric(gamepad1, headingRadians);

            // 3) READ THE BUTTONS (with edge detection). Each `pressed` is true
            //    only on the single loop where the button first goes down.
            boolean pressedA = gamepad1.a && !previousA;
            boolean pressedB = gamepad1.b && !previousB;
            boolean pressedY = gamepad1.y && !previousY;
            boolean pressedRightBumper = gamepad1.right_bumper && !previousRightBumper;

            if (pressedA) {
                // If the intake is running (either direction), any press stops it
                // first -- forward -> off -> reverse -- so the motor and drive train
                // never slam straight from full forward into reverse. From a stop,
                // A runs it forward.
                if (robot.intake.getState() != Intake.State.OFF) {
                    robot.intake.off();
                } else {
                    robot.intake.forward();
                }
            }
            if (pressedB) {
                // Same rule for B: stop if running, otherwise run in reverse.
                if (robot.intake.getState() != Intake.State.OFF) {
                    robot.intake.off();
                } else {
                    robot.intake.reverse();
                }
            }
            if (pressedY) {
                // This only STARTS the dump. The Dumper's state machine (run by
                // robot.update() above) carries out the raise/hold/lower over
                // the next many loops, all while the driver keeps driving.
                robot.dumper.dump();
            }
            if (pressedRightBumper) {
                robot.localization.reset();
            }

            // Remember this loop's button values for next loop's edge detection.
            previousA = gamepad1.a;
            previousB = gamepad1.b;
            previousY = gamepad1.y;
            previousRightBumper = gamepad1.right_bumper;

            // 4) TELEMETRY. Show the driver what every subsystem is doing.
            double headingDeg = robot.localization.getHeadingDegrees();
            double xIn = robot.localization.getXInches();
            double yIn = robot.localization.getYInches();

            telemetry.addData("Intake", robot.intake.getState());
            telemetry.addData("Dumper", robot.dumper.getState());
            telemetry.addData("Has Pinpoint", robot.localization.hasPinpoint());
            telemetry.addData("Heading (deg)", "%.1f", headingDeg);
            telemetry.addData("Position (in)", "x %.1f  y %.1f", xIn, yIn);
            telemetry.update();

            // 5) FTC DASHBOARD. Mirror the same values to the dashboard's live
            //    graphs and draw the robot on its field view. This is a great
            //    way to watch the Pinpoint position while bringing up a real
            //    robot. It does NOTHING in the simulator (the dashboard is a
            //    no-op stub there); on a real Control Hub, connect a laptop to
            //    the robot's Wi-Fi and open http://192.168.43.1:8080/dash.
            TelemetryPacket packet = new TelemetryPacket();
            packet.put("Intake", robot.intake.getState().toString());
            packet.put("Dumper", robot.dumper.getState().toString());
            packet.put("Has Pinpoint", robot.localization.hasPinpoint());
            packet.put("Heading (deg)", headingDeg);
            packet.put("X (in)", xIn);
            packet.put("Y (in)", yIn);
            DashboardDraw.robot(packet.fieldOverlay(), xIn, yIn, robot.localization.getHeadingRadians());
            dashboard.sendTelemetryPacket(packet);
        }
    }
}
