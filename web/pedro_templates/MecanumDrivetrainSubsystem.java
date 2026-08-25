package org.firstinspires.ftc.teamcode;

import com.pedropathing.Drivetrain;
import com.pedropathing.math.Vector;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

// Pedro Pathing Drivetrain adapter for a standard mecanum chassis. Works unchanged on the
// sim and the real robot -- it only ever calls the standard DcMotor.setPower() interface.
// Autonomous uses THIS (closed-loop path following); TeleOp drives the same four motors
// directly from gamepad sticks. Motor names must match your hardware configuration:
// leftFront, rightFront, leftBack, rightBack.
public class MecanumDrivetrainSubsystem extends Drivetrain {

    private final DcMotor leftFront, rightFront, leftBack, rightBack;
    private volatile double xVel = 0, yVel = 0;

    public MecanumDrivetrainSubsystem(HardwareMap hardwareMap) {
        leftFront  = hardwareMap.get(DcMotor.class, "leftFront");
        rightFront = hardwareMap.get(DcMotor.class, "rightFront");
        leftBack   = hardwareMap.get(DcMotor.class, "leftBack");
        rightBack  = hardwareMap.get(DcMotor.class, "rightBack");
    }

    // Pedro sums three control vectors (field-frame) into a movement command; we rotate that
    // into robot-frame forward/strafe/turn using the current heading, then mix into wheel powers.
    @Override
    public double[] calculateDrive(Vector corrective, Vector heading, Vector pathing, double robotHeading) {
        double fieldX = 0, fieldY = 0, turn = 0;
        if (corrective != null) { fieldX += corrective.getXComponent(); fieldY += corrective.getYComponent(); }
        if (pathing != null)    { fieldX += pathing.getXComponent();    fieldY += pathing.getYComponent(); }

        double cos = Math.cos(robotHeading);
        double sin = Math.sin(robotHeading);

        if (heading != null) {
            // Pedro's heading vector points along the robot's heading with magnitude = PID
            // output; project onto the forward unit vector to recover the signed value.
            double pidOutput = heading.getXComponent() * cos + heading.getYComponent() * sin;
            turn = -pidOutput;   // our mecanum mix uses turn>0 = CW; Pedro's positive = CCW
        }

        // Project the field-frame vector onto the robot's forward/right axes. This is the standard
        // real-robot FTC/Pedro convention: forward=(cos,sin), right=(sin,-cos). The same math runs
        // unchanged on a real robot -- the sim's driveBody strafe sign was fixed to match reality,
        // so this no longer needs the inverted sign it briefly carried.
        double forward     = fieldX * cos + fieldY * sin;
        double strafeRight = fieldX * sin - fieldY * cos;

        double fl = forward + strafeRight + turn;
        double fr = forward - strafeRight - turn;
        double bl = forward - strafeRight + turn;
        double br = forward + strafeRight - turn;

        double maxPow = Math.max(Math.max(Math.abs(fl), Math.abs(fr)), Math.max(Math.abs(bl), Math.abs(br)));
        if (maxPow > 1.0) { fl /= maxPow; fr /= maxPow; bl /= maxPow; br /= maxPow; }

        double scale = getMaxPowerScaling();
        return new double[] { fl * scale, fr * scale, bl * scale, br * scale };
    }

    @Override
    public void runDrive(double[] drivePowers) {
        if (drivePowers == null || drivePowers.length < 4) return;
        leftFront.setPower(safe(drivePowers[0]));
        rightFront.setPower(safe(drivePowers[1]));
        leftBack.setPower(safe(drivePowers[2]));
        rightBack.setPower(safe(drivePowers[3]));
    }

    private double safe(double p) { return Double.isNaN(p) || Double.isInfinite(p) ? 0.0 : p; }

    @Override
    public void breakFollowing() {
        leftFront.setPower(0); rightFront.setPower(0); leftBack.setPower(0); rightBack.setPower(0);
    }

    @Override public void updateConstants() { /* constants don't change at runtime */ }
    @Override public void startTeleopDrive() { breakFollowing(); }
    @Override public void startTeleopDrive(boolean brakeMode) { startTeleopDrive(); }
    @Override public double xVelocity() { return xVel; }
    @Override public double yVelocity() { return yVel; }
    @Override public void setXVelocity(double v) { this.xVel = v; }
    @Override public void setYVelocity(double v) { this.yVel = v; }
    @Override public double getVoltage() { return 12.0; }

    @Override
    public String debugString() {
        return "MecanumDrivetrainSubsystem[FL=" + leftFront.getPower() + " FR=" + rightFront.getPower()
                + " BL=" + leftBack.getPower() + " BR=" + rightBack.getPower() + "]";
    }
}
