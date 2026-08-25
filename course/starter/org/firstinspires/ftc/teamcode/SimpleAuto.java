package org.firstinspires.ftc.teamcode;

import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.control.PIDFCoefficients;
import com.pedropathing.control.FilteredPIDFCoefficients;
import com.pedropathing.geometry.Pose;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

/*
 * ============================================================================
 *  SimpleAuto  --  STARTER STUB  (a captured autonomous)
 * ============================================================================
 *  Autonomous uses the SAME Robot/subsystems as TeleOp for its mechanisms, and
 *  Pedro Pathing's Follower (with a Pinpoint localizer + mecanum drivetrain
 *  adapter) to drive along paths. This starter wires those pieces together;
 *  the captured paths and button actions are what you add during the lessons.
 * ============================================================================
 */
@Autonomous(name = "SimpleAuto (OOP)", group = "Dumper OOP")
public class SimpleAuto extends LinearOpMode {

    private static final double START_X_IN        = 0.0;
    private static final double START_Y_IN        = -44.0;
    private static final double START_HEADING_DEG = 90.0;

    @Override
    public void runOpMode() throws InterruptedException {
        // Mechanisms: the same Robot/subsystems TeleOp uses.
        Robot robot = new Robot(hardwareMap);

        // Driving: Pedro Pathing's closed-loop path follower.
        PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap);
        MecanumDrivetrainSubsystem drivetrain = new MecanumDrivetrainSubsystem(hardwareMap);

        FollowerConstants constants = new FollowerConstants()
                .translationalPIDFCoefficients(new PIDFCoefficients(0.3, 0, 0.03, 0))
                .headingPIDFCoefficients(new PIDFCoefficients(2.0, 0, 0.1, 0))
                .drivePIDFCoefficients(new FilteredPIDFCoefficients(0.02, 0, 0.0005, 0.6, 0))
                .mass(13.0);

        Follower follower = new Follower(constants, localizer, drivetrain);
        follower.setStartingPose(new Pose(START_X_IN, START_Y_IN, Math.toRadians(START_HEADING_DEG)));

        telemetry.addLine("Captured auto ready. Hold the robot still, then press START.");
        telemetry.update();
        waitForStart();
        if (isStopRequested()) return;

        // TODO (lesson): build path chains, follow them, and run the captured
        //                button actions (dump, intake on/off) between segments.

        while (opModeIsActive()) {
            idle();
        }
    }
}
