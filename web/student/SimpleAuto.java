package org.firstinspires.ftc.teamcode;

import com.pedropathing.follower.Follower;
import com.pedropathing.follower.FollowerConstants;
import com.pedropathing.control.PIDFCoefficients;
import com.pedropathing.control.FilteredPIDFCoefficients;
import com.pedropathing.geometry.BezierLine;
import com.pedropathing.geometry.Pose;
import com.pedropathing.paths.PathChain;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.dashboard.canvas.Canvas;

/*
 * ============================================================================
 *  SimpleAuto  --  a CAPTURED autonomous (paths + button actions)
 * ============================================================================
 *
 *  This file is written to look like what the Impulse simulator's "path
 *  capture" tool produces: you drive the robot around in the sim, tap buttons
 *  at the spots where you want mechanisms to act, and it writes out an
 *  autonomous just like this one. Reading it teaches you the shape of a real
 *  captured auto so you can hand-edit or extend it later.
 *
 *  TWO DIFFERENT JOBS, TWO DIFFERENT TOOLS
 *    1. DRIVING along a path to exact field spots is a closed-loop control
 *       problem, so we use Pedro Pathing's Follower (with a Pinpoint-based
 *       Localizer and a mecanum Drivetrain adapter). This is separate from the
 *       Drivebase subsystem, which is the driver-controlled TeleOp drive.
 *    2. MECHANISMS (intake, dumper) are the SAME subsystems from TeleOp. We
 *       build the same Robot and call the same robot.update() scheduler, so a
 *       "Button Y" in a captured path runs the exact dumper code the driver
 *       uses. Write the mechanism once, use it in TeleOp and auto.
 *
 *  HOW A CAPTURED BUTTON BECOMES CODE (the key idea we discussed)
 *    The capture tool records the DISCRETE command each button is bound to --
 *    for example Y -> robot.dumper.dump(), A -> robot.intake.forward(),
 *    X -> robot.intake.off(). It emits that exact call at the point in the
 *    path where you tapped it. No toggle guessing: auto always commands an
 *    exact state, which is why the subsystems expose discrete commands and let
 *    the INPUT LAYER (TeleOp) decide gestures like toggling.
 *
 *  HOW WE "WAIT" FOR A MECHANISM WITHOUT FREEZING (no sleep!)
 *    Some actions finish over time (the dumper's raise/hold/lower). We never
 *    sleep(). Instead, after starting the action we run a small loop that keeps
 *    pumping BOTH updates until the robot reports it is no longer busy:
 *        while (opModeIsActive() && robot.isBusy()) {
 *            robot.update();     // advance the dumper's state machine
 *            follower.update();  // hold our position on the path
 *        }
 *    Actions that do not take time (intake on/off) need no wait at all.
 * ============================================================================
 */
@Autonomous(name = "SimpleAuto (OOP)", group = "Dumper OOP")
public class SimpleAuto extends LinearOpMode {

    // ── TUNING CONSTANTS ("defines") ─────────────────────────────────────
    // `static final` = a named value that never changes (Java's "#define").
    // Maximum motor power while following a path (0.0-1.0). Lower = slower and
    // more precise. The capture tool writes this; tune it for your robot.
    private static final double SPEED = 0.5;

    // ── SET YOUR STARTING POSITION HERE ──────────────────────────────────
    // Before the match you PLACE the robot on the field, then tell the code
    // exactly where you put it. X/Y are inches from your field's ORIGIN, and
    // HEADING is the way the robot points (degrees, counter-clockwise positive,
    // 0 = facing +X). This is the ONE place to set the robot's starting spot;
    // if it does not match where you physically set the robot down, every path
    // is shifted by the difference. On the FTC Dashboard field the origin is
    // the CENTER of the 144" x 144" (12 ft x 12 ft) area.
    private static final double START_X_IN        = 0.0;
    private static final double START_Y_IN        = -44.0;   // within +/- 48 for an 8 ft field
    private static final double START_HEADING_DEG = 90.0;

    // ── PLAYFIELD SIZE ───────────────────────────────────────
    // The size of your test area, in inches (square). 144 = a full 12 ft FTC
    // field; 96 = an 8 ft x 8 ft area. This does NOT resize the FTC Dashboard
    // background (that image is always 144"), but we draw this boundary on the
    // field view so you can see your smaller area -- and it is a reminder to
    // keep the start pose and every waypoint within +/- FIELD_SIZE_IN / 2.
    private static final double FIELD_SIZE_IN = 96.0;

    @Override
    public void runOpMode() throws InterruptedException {

        // ── MECHANISMS: the same Robot/subsystems TeleOp uses ────────────
        // Building Robot also configures its Localization (Pinpoint). In auto,
        // Pedro's Follower is the AUTHORITY for driving position; Robot's own
        // Localization simply reads the same Pinpoint along for the ride and we
        // don't use its pose here. We build Robot for its intake and dumper.
        // HOLD THE ROBOT STILL during INIT -- the Pinpoint calibrates now.
        Robot robot = new Robot(hardwareMap);

        // FTC Dashboard handle -- used to draw the robot's tracked position on
        // the dashboard's top-down field view while auto runs. No-op in the sim.
        FtcDashboard dashboard = FtcDashboard.getInstance();

        // ── DRIVING: Pedro Pathing setup (closed-loop path following) ────
        // The Follower needs a Localizer (where am I?) and a Drivetrain (how do
        // I push the wheels?). These are the two Pedro adapter classes in this
        // package. Both this Localizer and Robot's Localization read the one
        // physical Pinpoint; that is fine because in auto only Pedro steers.
        PinpointLocalizer localizer = new PinpointLocalizer(hardwareMap);
        MecanumDrivetrainSubsystem drivetrain = new MecanumDrivetrainSubsystem(hardwareMap);

        // FollowerConstants are the path-following tuning numbers. The capture
        // tool writes these; they match the sim robot and are a good start on a
        // real robot. Retune on hardware if pathing overshoots or oscillates.
        FollowerConstants constants = new FollowerConstants()
                .translationalPIDFCoefficients(new PIDFCoefficients(0.3, 0, 0.03, 0))
                .headingPIDFCoefficients(new PIDFCoefficients(2.0, 0, 0.1, 0))
                .drivePIDFCoefficients(new FilteredPIDFCoefficients(0.02, 0, 0.0005, 0.6, 0))
                .mass(13.0)
                .forwardZeroPowerAcceleration(30)
                .lateralZeroPowerAcceleration(50)
                .centripetalScaling(0.0005)
                .holdPointTranslationalScaling(0.45)
                .holdPointHeadingScaling(0.35);

        Follower follower = new Follower(constants, localizer, drivetrain);
        // Draws the path in the simulator; does nothing on a real robot.
        MecanumDrivetrainSubsystem.registerForVisualization(hardwareMap, follower);

        // ── Poses ─────────────────────────────────────────────────────────
        // Each captured waypoint becomes a Pose(x, y, headingRadians). Field
        // units are inches; heading 0 = facing +X, counter-clockwise positive.
        Pose pose1 = new Pose(0.0, -36.0, Math.toRadians(90.0));
        Pose pose2 = new Pose(24.0, -36.0, Math.toRadians(90.0));
        Pose pose3 = new Pose(24.0, -12.0, Math.toRadians(90.0));
        Pose pose4 = new Pose(0.0, -12.0, Math.toRadians(90.0));

        // ── Path chains ─────────────────────────────────────────────────
        // The capture tool splits the path at every button press, making one
        // PathChain per segment. Between chains, it inserts the button action.
        // A BezierLine is just a straight path from one pose to the next.
        PathChain chain1 = follower.pathBuilder()
                .addPath(new BezierLine(pose1, pose2))
                .setConstantHeadingInterpolation(Math.toRadians(90.0))
                .build();

        PathChain chain2 = follower.pathBuilder()
                .addPath(new BezierLine(pose2, pose3))
                .setConstantHeadingInterpolation(Math.toRadians(90.0))
                .build();

        PathChain chain3 = follower.pathBuilder()
                .addPath(new BezierLine(pose3, pose4))
                .setConstantHeadingInterpolation(Math.toRadians(90.0))
                .build();

        // ── Tell the localizer where the robot is starting ───────────────
        // This seeds the Pinpoint with your field pose (from the constants at
        // the top of the class), so from here on the tracked position is in
        // FIELD inches -- not "inches from wherever it powered on." Set the
        // robot down on this exact spot before pressing START.
        Pose startPose = new Pose(START_X_IN, START_Y_IN, Math.toRadians(START_HEADING_DEG));
        follower.setStartingPose(startPose);

        // The planned route, for drawing on the dashboard field (start + the
        // four captured waypoints). Comparing this gray route with the live
        // blue robot marker is how you SEE a wrong start pose or drift.
        Pose[] plan = { startPose, pose1, pose2, pose3, pose4 };

        // ── Wait for START ───────────────────────────────────────────────
        telemetry.addLine("Captured auto ready. Hold the robot still, then press START.");
        telemetry.update();
        waitForStart();
        if (isStopRequested()) return;

        // ── Drive from the spawn tile to the first captured pose ─────────
        // The robot may spawn a little away from pose1, so close that gap first.
        follower.update();
        Pose actualStart = follower.getPose();
        double distToStart = Math.hypot(actualStart.getX() - pose1.getX(),
                actualStart.getY() - pose1.getY());
        if (distToStart > 1.0) {
            PathChain driveToStart = follower.pathBuilder()
                    .addPath(new BezierLine(actualStart, pose1))
                    .setLinearHeadingInterpolation(actualStart.getHeading(), pose1.getHeading())
                    .build();
            follower.followPath(driveToStart, SPEED, true);
            // Follow loop: pump the follower to drive, and the scheduler to keep
            // mechanisms alive, until the follower says it is done.
            while (opModeIsActive() && follower.isBusy()) {
                follower.update();
                robot.update();
                telemetry.addData("Phase", "Drive to start");
                telemetry.update();
                dashField(dashboard, follower, plan, "Drive to start");
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        //  Follow chain 1, then the captured "Button Y" action (a dump)
        // ═══════════════════════════════════════════════════════════════════
        follower.followPath(chain1, SPEED, true);
        while (opModeIsActive() && follower.isBusy()) {
            follower.update();   // drive along the path
            robot.update();      // keep intake/dumper state machines advancing
            Pose p = follower.getPose();
            telemetry.addData("Phase", "Chain 1");
            telemetry.addData("Pos", "(%.1f, %.1f)", p.getX(), p.getY());
            telemetry.update();
            dashField(dashboard, follower, plan, "Chain 1");
        }

        // ── Button Y action: start a dump, then WAIT for it to finish ────
        // dump() only STARTS the raise/hold/lower. Because a dump takes time,
        // the capture tool follows it with this pump-wait so the auto does not
        // drive away mid-dump. Note we pump follower.update() too, so the robot
        // holds its position on the path while the bucket cycles.
        robot.dumper.dump();
        while (opModeIsActive() && robot.isBusy()) {
            robot.update();      // advance the dumper's state machine
            follower.update();   // hold position while dumping
            telemetry.addData("Phase", "Button Y (dumping)");
            telemetry.addData("Dumper", robot.dumper.getState());
            telemetry.update();
            dashField(dashboard, follower, plan, "Button Y (dumping)");
        }

        // ═══════════════════════════════════════════════════════════════════
        //  Follow chain 2, then the captured "Button A" action (intake on)
        // ═══════════════════════════════════════════════════════════════════
        follower.followPath(chain2, SPEED, true);
        while (opModeIsActive() && follower.isBusy()) {
            follower.update();
            robot.update();
            telemetry.addData("Phase", "Chain 2");
            telemetry.update();
            dashField(dashboard, follower, plan, "Chain 2");
        }

        // ── Button A action: start the intake. This is a DISCRETE command
        // that finishes instantly (it just sets the intake state), so there is
        // NO pump-wait -- the intake keeps running by itself as we drive on.
        robot.intake.forward();

        // ═══════════════════════════════════════════════════════════════════
        //  Follow chain 3, then the captured "Button X" action (intake off)
        // ═══════════════════════════════════════════════════════════════════
        follower.followPath(chain3, SPEED, true);
        while (opModeIsActive() && follower.isBusy()) {
            follower.update();
            robot.update();      // intake is still running here, applied each loop
            telemetry.addData("Phase", "Chain 3");
            telemetry.update();
            dashField(dashboard, follower, plan, "Chain 3");
        }

        // ── Button X action: stop the intake. Discrete, no wait needed. ──
        robot.intake.off();
        robot.update();  // apply the "off" immediately

        // ── Done: stop everything ────────────────────────────────────────
        drivetrain.breakFollowing();
        MecanumDrivetrainSubsystem.unregisterVisualization(hardwareMap);
        robot.intake.off();
        robot.drive.stop();

        ElapsedTime totalTimer = new ElapsedTime();
        Pose finalPose = follower.getPose();
        telemetry.addData("Status", "COMPLETE");
        telemetry.addData("Final", "(%.1f, %.1f) h=%.1f",
                finalPose.getX(), finalPose.getY(), Math.toDegrees(finalPose.getHeading()));
        telemetry.update();

        // Keep drawing the final position on the dashboard while we idle, so
        // you can read where the robot ended up versus where it should be.
        while (opModeIsActive()) {
            dashField(dashboard, follower, plan, "COMPLETE");
            idle();
        }
    }

    /*
     * dashField()  --  draw the robot's tracked pose (and the planned route) on
     * the FTC Dashboard field view, and stream the numbers to its graphs.
     * Call it once per loop. It does nothing in the simulator; on a real
     * Control Hub, open the dashboard's Field view to watch the robot track.
     */
    private void dashField(FtcDashboard dashboard, Follower follower, Pose[] plan, String phase) {
        TelemetryPacket packet = new TelemetryPacket();
        Pose pose = follower.getPose();
        packet.put("Phase", phase);
        packet.put("X (in)", pose.getX());
        packet.put("Y (in)", pose.getY());
        packet.put("Heading (deg)", Math.toDegrees(pose.getHeading()));

        Canvas field = packet.fieldOverlay();

        // Planned route in gray: a small circle at each waypoint and straight
        // lines between them. This is where the robot is SUPPOSED to go.
        field.setStroke("#9E9E9E");
        for (int i = 0; i < plan.length; i++) {
            field.strokeCircle(plan[i].getX(), plan[i].getY(), 1.0);
            if (i > 0) {
                field.strokeLine(plan[i - 1].getX(), plan[i - 1].getY(),
                        plan[i].getX(), plan[i].getY());
            }
        }

        // Your custom playfield boundary (orange), centered on the origin, so
        // you can see your smaller area inside the dashboard's fixed 144" field.
        double half = FIELD_SIZE_IN / 2.0;
        field.setStroke("#FF9800");
        field.strokeLine(-half, -half,  half, -half);
        field.strokeLine( half, -half,  half,  half);
        field.strokeLine( half,  half, -half,  half);
        field.strokeLine(-half,  half, -half, -half);

        // The robot where it currently THINKS it is, in blue, with a nose line
        // showing its heading. Gap between blue and gray = localization error.
        DashboardDraw.robot(field, pose.getX(), pose.getY(), pose.getHeading());

        dashboard.sendTelemetryPacket(packet);
    }
}
