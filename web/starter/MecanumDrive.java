package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

/*
 * ============================================================================
 *  MecanumDrive  --  STARTER STUB  (the driver-controlled OpMode / TeleOp)
 * ============================================================================
 *  This is the program a driver runs during the match. It builds the Robot and
 *  then, every loop, runs the scheduler and drives. The button controls and
 *  telemetry are what you add during the lessons.
 * ============================================================================
 */
@TeleOp(name = "MecanumDrive (OOP)", group = "Dumper OOP")
public class MecanumDrive extends LinearOpMode {

    @Override
    public void runOpMode() {
        // Build the whole robot (finds motors, configures sensors) during INIT.
        Robot robot = new Robot(hardwareMap);

        telemetry.addLine("Initialized. Hold the robot still, then press START.");
        telemetry.update();

        waitForStart();

        while (opModeIsActive()) {
            // 1) Run the scheduler: give every subsystem a slice of work.
            robot.update();

            // 2) Drive using the latest heading from localization.
            double headingRadians = robot.localization.getHeadingRadians();
            robot.drive.driveFieldCentric(gamepad1, headingRadians);

            // 3) TODO (lesson): read the buttons (A/B/Y/right-bumper) with edge
            //    detection to run the intake, dumper, and heading reset.

            // 4) TODO (lesson): telemetry -- show intake/dumper state, heading, position.
            telemetry.update();
        }
    }
}
