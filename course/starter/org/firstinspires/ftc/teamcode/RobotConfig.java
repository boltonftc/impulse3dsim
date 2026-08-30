package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/*
 * ============================================================================
 *  RobotConfig  --  the ONE place that describes this physical robot
 * ============================================================================
 *
 *  Every fact about the hardware -- the device names, which way each pod
 *  counts, which motors are reversed, the pod offsets and pod type -- lives
 *  here and NOWHERE ELSE. Subsystems and the autonomous adapters all read
 *  their configuration from this class.
 *
 *  WHY THIS EXISTS:
 *    The same physical Pinpoint is used by two different objects: the
 *    Localization subsystem (TeleOp) and the PinpointLocalizer (auto).
 *    The same four drive motors are used by the Drivebase subsystem and the
 *    MecanumDrivetrainSubsystem. If each of those configured the hardware on
 *    its own, the two copies could DISAGREE -- and because hardwareMap.get()
 *    hands back the SAME device instance, whichever constructor ran LAST would
 *    silently win. Fixing a setting in one file but not the other looks fixed
 *    but is still broken. Putting the settings here removes that trap: change
 *    a value once and every user of that device gets it.
 *
 *  This class is never instantiated. It is only constants and helper methods.
 * ============================================================================
 */
public final class RobotConfig {

    // Private constructor: this class is a bag of constants and static helpers,
    // never an object. Making it uninstantiable documents that intent.
    private RobotConfig() { }

    // ── DEVICE NAMES ─────────────────────────────────────────────────────
    // These strings MUST match the names in the Driver Hub configuration.
    // Every subsystem looks its hardware up by these constants, so a rename
    // only ever happens here.
    public static final String PINPOINT          = "pinpoint";
    public static final String FRONT_LEFT_MOTOR  = "front_left_motor";
    public static final String FRONT_RIGHT_MOTOR = "front_right_motor";
    public static final String BACK_LEFT_MOTOR   = "back_left_motor";
    public static final String BACK_RIGHT_MOTOR  = "back_right_motor";
    public static final String INTAKE_MOTOR      = "intake_motor";
    public static final String DUMP_SERVO        = "dump_servo";

    // ── DRIVE MOTOR DIRECTIONS ───────────────────────────────────────────
    // The left motors are mounted as a mirror image of the right, so they are
    // reversed by default. REAL ROBOT check (do once): command "forward" and
    // confirm every wheel drives the robot forward. If a wheel spins the wrong
    // way, flip its boolean here -- ONE place, used by both the Drivebase
    // subsystem and the autonomous drivetrain adapter.
    public static final boolean FRONT_LEFT_REVERSED  = true;
    public static final boolean FRONT_RIGHT_REVERSED = false;
    public static final boolean BACK_LEFT_REVERSED   = true;
    public static final boolean BACK_RIGHT_REVERSED  = false;

    // ── PINPOINT PHYSICAL CONFIG ─────────────────────────────────────────
    // Pod offsets from the robot's CENTER OF ROTATION, in millimeters (only
    // the perpendicular offset per pod matters). REAL ROBOT: measure these.
    // SIMULATOR: the pods sit at the center, so both are 0.
    public static final double POD_X_OFFSET_MM = 0.0;   // sideways offset of the forward pod
    public static final double POD_Y_OFFSET_MM = 0.0;   // forward offset of the strafe pod

    // Which goBILDA pods you have:
    //   4-Bar Odometry Pack   -> goBILDA_4_BAR_POD
    //   Swingarm Odometry Pod -> goBILDA_SWINGARM_POD
    public static final GoBildaPinpointDriver.GoBildaOdometryPods POD_TYPE =
            GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD;

    // Which way each pod counts. REAL ROBOT sanity check (do this ONCE):
    //   push the robot FORWARD by hand      -> X must count UP
    //   push the robot LEFT by hand         -> Y must count UP   (RIGHT counts DOWN)
    //   spin the robot COUNTER-clockwise    -> heading must count UP
    // This is the frame Pedro Pathing assumes: +X forward, +Y left, CCW+ heading.
    // If a pod counts the wrong way, change its FORWARD to REVERSED right here
    // -- this is the single place that setting lives. (On the course robot the
    // strafe pod needed REVERSED; check yours and set it to match.)
    public static final GoBildaPinpointDriver.EncoderDirection X_POD_DIRECTION =
            GoBildaPinpointDriver.EncoderDirection.FORWARD;
    public static final GoBildaPinpointDriver.EncoderDirection Y_POD_DIRECTION =
            GoBildaPinpointDriver.EncoderDirection.FORWARD;


    // ── HELPERS ──────────────────────────────────────────────────────────

    /*
     * configurePinpoint()  --  apply the pod configuration to a Pinpoint.
     * Both the Localization subsystem and the PinpointLocalizer call this,
     * so the offsets, pod type, and encoder directions can never drift apart.
     * NOTE: resetPosAndIMU() also calibrates the gyro -- HOLD THE ROBOT STILL.
     */
    public static void configurePinpoint(GoBildaPinpointDriver pinpoint) {
        pinpoint.setOffsets(POD_X_OFFSET_MM, POD_Y_OFFSET_MM, DistanceUnit.MM);
        pinpoint.setEncoderResolution(POD_TYPE);
        pinpoint.setEncoderDirections(X_POD_DIRECTION, Y_POD_DIRECTION);
        pinpoint.resetPosAndIMU();
    }

    /*
     * getDriveMotors()  --  fetch the four drive motors and apply their
     * directions, returned in the standard order [FL, FR, BL, BR]. Both the
     * Drivebase subsystem and the autonomous drivetrain adapter use this, so
     * the motor names and reversals live in exactly one place.
     */
    public static DcMotor[] getDriveMotors(HardwareMap hardwareMap) {
        DcMotor frontLeft  = hardwareMap.dcMotor.get(FRONT_LEFT_MOTOR);
        DcMotor frontRight = hardwareMap.dcMotor.get(FRONT_RIGHT_MOTOR);
        DcMotor backLeft   = hardwareMap.dcMotor.get(BACK_LEFT_MOTOR);
        DcMotor backRight  = hardwareMap.dcMotor.get(BACK_RIGHT_MOTOR);

        frontLeft.setDirection(FRONT_LEFT_REVERSED   ? DcMotor.Direction.REVERSE : DcMotor.Direction.FORWARD);
        frontRight.setDirection(FRONT_RIGHT_REVERSED ? DcMotor.Direction.REVERSE : DcMotor.Direction.FORWARD);
        backLeft.setDirection(BACK_LEFT_REVERSED     ? DcMotor.Direction.REVERSE : DcMotor.Direction.FORWARD);
        backRight.setDirection(BACK_RIGHT_REVERSED   ? DcMotor.Direction.REVERSE : DcMotor.Direction.FORWARD);

        return new DcMotor[] { frontLeft, frontRight, backLeft, backRight };
    }
}
