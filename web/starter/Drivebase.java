package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

/*
 * ============================================================================
 *  Drivebase  --  STARTER STUB (owned by Student A)
 * ============================================================================
 *  The four mecanum drive motors live here. This starter version builds the
 *  motors from RobotConfig but does NOT drive yet -- the driving math is what
 *  you will fill in during the lessons.
 * ============================================================================
 */
public class Drivebase implements Subsystem {

    private final DcMotor frontLeft;
    private final DcMotor frontRight;
    private final DcMotor backLeft;
    private final DcMotor backRight;

    public Drivebase(HardwareMap hardwareMap) {
        // Motors come back as [FL, FR, BL, BR] with directions already applied.
        DcMotor[] driveMotors = RobotConfig.getDriveMotors(hardwareMap);
        frontLeft  = driveMotors[0];
        frontRight = driveMotors[1];
        backLeft   = driveMotors[2];
        backRight  = driveMotors[3];
    }

    /** Read the sticks and drive. TODO: compute and apply the four wheel powers. */
    public void driveFieldCentric(Gamepad gamepad, double headingRadians) {
        // TODO (lesson): read gamepad, rotate by heading, mix, apply power.
    }

    /** Stop all four drive motors. */
    public void stop() {
        frontLeft.setPower(0.0);
        frontRight.setPower(0.0);
        backLeft.setPower(0.0);
        backRight.setPower(0.0);
    }

    @Override
    public void update() {
        // Drive is applied immediately when commanded; nothing to advance here.
    }
}
