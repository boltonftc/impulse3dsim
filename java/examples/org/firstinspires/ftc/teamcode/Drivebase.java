package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

/*
 * ============================================================================
 *  Drivebase  --  owned by Student A
 * ============================================================================
 *
 *  This subsystem owns the four mecanum drive motors and turns joystick input
 *  into motor power. A mecanum drivetrain can move in any direction AND spin
 *  at the same time.
 *
 *  FIELD-CENTRIC vs ROBOT-CENTRIC
 *    - Robot-centric: "forward" on the stick means "the direction the robot is
 *      facing." If the robot is turned around, forward looks backward to you.
 *    - Field-centric: "forward" on the stick always means "away from the
 *      driver," no matter which way the robot is pointed. To do this we need
 *      the robot's heading (which way it is facing), which Localization
 *      provides from the Pinpoint.
 *
 *  Unlike Intake and Dumper, driving is a CONTINUOUS command: the OpMode calls
 *  driveFieldCentric() every single loop with the latest sticks. So this
 *  subsystem applies power immediately in that method, and its update() has
 *  nothing left to do.
 * ============================================================================
 */
public class Drivebase implements Subsystem {

    // ── TUNING CONSTANTS ("defines") ─────────────────────────────────────
    // `static final` = a named value that never changes (Java's "#define").

    // Overall speed limit. 1.0 = full speed. Lower it (e.g. 0.6) to make the
    // robot easier to control, or for new drivers.
    private static final double DRIVE_SPEED_SCALE = 1.0;

    // Set to true for field-centric driving (needs a working heading), or
    // false for simple robot-centric driving. Handy fallback while first
    // bringing up the Pinpoint on a new robot: if heading is not trusted yet,
    // set this to false and the robot still drives normally.
    private static final boolean FIELD_CENTRIC = true;

    // ── Hardware ─────────────────────────────────────────────────────────
    // The four drive motors. Names MUST match the Driver Hub configuration.
    private final DcMotor frontLeft;
    private final DcMotor frontRight;
    private final DcMotor backLeft;
    private final DcMotor backRight;

    /*
     * CONSTRUCTOR
     * Runs once when Robot builds this subsystem. It gets the four drive motors
     * from RobotConfig, which also applies each motor's direction (the left
     * side is reversed there). Motor names and directions live in RobotConfig
     * so the autonomous drivetrain adapter uses the exact same wiring.
     */
    public Drivebase(HardwareMap hardwareMap) {
        // Motors come back with directions already applied, as [FL, FR, BL, BR].
        DcMotor[] driveMotors = RobotConfig.getDriveMotors(hardwareMap);
        frontLeft  = driveMotors[0];
        frontRight = driveMotors[1];
        backLeft   = driveMotors[2];
        backRight  = driveMotors[3];
    }

    /*
     * driveFieldCentric()  --  the main TeleOp drive method
     * Called every loop with the current gamepad and the robot's heading (in
     * radians, from Localization). It reads the sticks, rotates them into
     * field coordinates if FIELD_CENTRIC is on, computes the four wheel
     * powers with the mecanum formula, and applies them immediately.
     *
     * @param gamepad       the driver's controller (gamepad1)
     * @param headingRadians which way the robot is facing (0 = start direction)
     */
    public void driveFieldCentric(Gamepad gamepad, double headingRadians) {
        // Read the driver's intent from the sticks and triggers.
        // Note: pushing the left stick UP gives a NEGATIVE y, so we flip it so
        // that "stick up" = "drive forward" = positive.
        double drive  = -gamepad.left_stick_y;   // forward / backward
        double strafe =  gamepad.left_stick_x;   // left / right (sideways)
        double turn   =  gamepad.right_trigger - gamepad.left_trigger;  // spin

        // If field-centric, rotate the drive/strafe request by the negative of
        // the robot's heading. This converts "away from the driver" into
        // "relative to the robot" so the wheels do the right thing. If
        // field-centric is off, we leave the values as-is (robot-centric).
        double forward = drive;
        double right   = strafe;
        if (FIELD_CENTRIC) {
            double cos = Math.cos(-headingRadians);
            double sin = Math.sin(-headingRadians);
            right   = strafe * cos - drive * sin;
            forward = strafe * sin + drive * cos;
        }

        // Mix forward/strafe/turn into four normalized wheel powers. This is
        // the same mecanum formula the autonomous drivetrain uses, so it lives
        // in one place (MecanumKinematics). Result order is [FL, FR, BL, BR].
        double[] power = MecanumKinematics.mix(forward, right, turn);

        // Apply the overall speed limit and send the power to each motor.
        frontLeft.setPower(power[0]  * DRIVE_SPEED_SCALE);
        frontRight.setPower(power[1] * DRIVE_SPEED_SCALE);
        backLeft.setPower(power[2]   * DRIVE_SPEED_SCALE);
        backRight.setPower(power[3]  * DRIVE_SPEED_SCALE);
    }

    /** Stop all four drive motors. Autonomous calls this when it finishes. */
    public void stop() {
        frontLeft.setPower(0.0);
        frontRight.setPower(0.0);
        backLeft.setPower(0.0);
        backRight.setPower(0.0);
    }

    /*
     * update()
     * Driving is commanded every loop by driveFieldCentric(), so there is no
     * leftover slice of work to do here. We still provide the method because
     * the Subsystem contract requires it -- and someday a student might add
     * "background" drive behavior (like slowing down near a wall) right here.
     */
    @Override
    public void update() {
        // Nothing to advance -- drive is applied immediately when commanded.
    }
}
