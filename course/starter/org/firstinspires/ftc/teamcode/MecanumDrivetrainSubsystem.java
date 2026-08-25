/**
 * MecanumDrivetrainSubsystem — Portable Pedro Pathing Drivetrain for mecanum chassis.
 *
 * ┌───────────────────────────────────────────────────────────────────┐
 * │  COMPATIBILITY: Works on both the Impulse 3D Sim and a real      │
 * │  FTC robot. Uses only the standard DcMotor interface — no        │
 * │  sim-specific imports.                                           │
 * └───────────────────────────────────────────────────────────────────┘
 *
 * WHERE THIS FITS IN THE OOP PACKAGE:
 *   Your TeleOp drives with the Drivebase subsystem (open-loop, driver
 *   sticks). Autonomous is different: it must FOLLOW A PATH to exact field
 *   positions, which needs a closed-loop controller. That controller is Pedro
 *   Pathing's Follower, and the Follower needs a "drivetrain" object it can
 *   push wheel powers through. THIS class is that adapter -- it is only used
 *   by the captured autonomous, not by TeleOp.
 *
 * Pedro's Follower calls calculateDrive() to compute wheel powers, then
 * runDrive() to apply them. This class bridges those calls to the four
 * mecanum drive motors via DcMotor.setPower().
 *
 * Motor mapping (standard FTC mecanum):
 *   index 0 = front left
 *   index 1 = front right
 *   index 2 = back left
 *   index 3 = back right
 */
package org.firstinspires.ftc.teamcode;

import com.pedropathing.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Vector;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

public class MecanumDrivetrainSubsystem extends Drivetrain {

    private final DcMotor[] motors;

    // Cached velocities (inches/s) — set by Pedro's Follower each tick
    private volatile double xVel = 0;
    private volatile double yVel = 0;

    /**
     * Create a MecanumDrivetrainSubsystem from four DcMotor instances.
     *
     * @param motors array of 4 DcMotor [FL, FR, BL, BR]
     */
    public MecanumDrivetrainSubsystem(DcMotor[] motors) {
        if (motors == null || motors.length != 4) {
            throw new IllegalArgumentException("MecanumDrivetrainSubsystem requires exactly 4 motors");
        }
        this.motors = motors;
    }

    /**
     * Convenience constructor — pulls the four drive motors (with their
     * directions already applied) from RobotConfig, the same source the
     * Drivebase subsystem uses, so the two can never disagree about motor
     * names or reversals.
     */
    public MecanumDrivetrainSubsystem(HardwareMap hardwareMap) {
        this.motors = RobotConfig.getDriveMotors(hardwareMap);
    }

    /**
     * Convert Pedro's corrective/heading/pathing vectors into mecanum wheel powers.
     *
     * @param corrective translational error correction vector (field frame)
     * @param heading    heading error correction vector
     * @param pathing    feedforward path-following vector (field frame)
     * @param robotHeading current robot heading in radians
     * @return double[4] wheel powers [FL, FR, BL, BR] in [-1, 1]
     */
    @Override
    public double[] calculateDrive(Vector corrective, Vector heading, Vector pathing, double robotHeading) {
        // Sum the control vectors (field-frame)
        double fieldX = 0;
        double fieldY = 0;
        double turn = 0;

        if (corrective != null) {
            fieldX += corrective.getXComponent();
            fieldY += corrective.getYComponent();
        }
        if (pathing != null) {
            fieldX += pathing.getXComponent();
            fieldY += pathing.getYComponent();
        }

        // Rotate field-frame vector into robot-frame
        double cos = Math.cos(robotHeading);
        double sin = Math.sin(robotHeading);

        if (heading != null) {
            // Pedro's heading vector = Vector(pidOutput, robotHeading)
            // Project onto robot forward unit vector to recover signed PID output.
            // Negate: our mecanum has turn>0 = CW, Pedro's positive = CCW.
            double pidOutput = heading.getXComponent() * cos + heading.getYComponent() * sin;
            turn = -pidOutput;
        }

        double forward     = fieldX * cos + fieldY * sin;   // along robot forward
        double strafeRight = fieldX * sin - fieldY * cos;    // along robot right

        // Mix into four normalized wheel powers using the same formula the
        // TeleOp Drivebase uses (shared in MecanumKinematics). Order [FL,FR,BL,BR].
        double[] power = MecanumKinematics.mix(forward, strafeRight, turn);

        double scale = getMaxPowerScaling();
        return new double[] { power[0] * scale, power[1] * scale,
                              power[2] * scale, power[3] * scale };
    }

    /**
     * Apply wheel powers to the motors.
     *
     * @param drivePowers [FL, FR, BL, BR] powers
     */
    @Override
    public void runDrive(double[] drivePowers) {
        if (drivePowers == null || drivePowers.length < 4) return;
        for (int i = 0; i < 4; i++) {
            double p = drivePowers[i];
            motors[i].setPower(Double.isNaN(p) || Double.isInfinite(p) ? 0.0 : p);
        }
    }

    @Override
    public void startTeleopDrive() {
        for (DcMotor motor : motors) {
            motor.setPower(0);
        }
    }

    @Override
    public void startTeleopDrive(boolean brakeMode) {
        startTeleopDrive();
    }

    @Override
    public void breakFollowing() {
        for (DcMotor motor : motors) {
            motor.setPower(0);
        }
    }

    @Override
    public void updateConstants() {
        // No-op — constants don't change at runtime
    }

    @Override
    public double xVelocity() {
        return xVel;
    }

    @Override
    public double yVelocity() {
        return yVel;
    }

    @Override
    public void setXVelocity(double v) {
        this.xVel = v;
    }

    @Override
    public void setYVelocity(double v) {
        this.yVel = v;
    }

    @Override
    public double getVoltage() {
        return 12.0;  // Nominal battery voltage
    }

    @Override
    public String debugString() {
        return String.format("MecanumDrivetrain[FL=%.2f FR=%.2f BL=%.2f BR=%.2f]",
                motors[0].getPower(), motors[1].getPower(),
                motors[2].getPower(), motors[3].getPower());
    }

    /**
     * Get current motor powers for telemetry debugging.
     * @return double[4] = {FL, FR, BL, BR} current powers
     */
    public double[] debugMotorPowers() {
        return new double[] {
            motors[0].getPower(), motors[1].getPower(),
            motors[2].getPower(), motors[3].getPower()
        };
    }

    // ── Simulator Path Visualization (optional, no-op on real robot) ─────

    /**
     * Register a Follower for path visualization in the simulator.
     * Silently does nothing on real robot hardware.
     */
    public static void registerForVisualization(HardwareMap hardwareMap, Follower follower) {
        try {
            Class<?> registryClass = Class.forName("sim.FollowerRegistry");
            java.lang.reflect.Method method = registryClass.getMethod(
                    "register", int.class, Follower.class);
            int robotIdx = (int) hardwareMap.getClass()
                    .getMethod("getRobotIndex").invoke(hardwareMap);
            method.invoke(null, robotIdx, follower);
        } catch (Throwable ignored) {
            // Not running in the simulator — visualization unavailable
        }
    }

    /**
     * Unregister visualization (call on OpMode cleanup).
     * Silently does nothing on real robot hardware.
     */
    public static void unregisterVisualization(HardwareMap hardwareMap) {
        try {
            Class<?> registryClass = Class.forName("sim.FollowerRegistry");
            java.lang.reflect.Method method = registryClass.getMethod(
                    "unregister", int.class);
            int robotIdx = (int) hardwareMap.getClass()
                    .getMethod("getRobotIndex").invoke(hardwareMap);
            method.invoke(null, robotIdx);
        } catch (Throwable ignored) {
            // Not running in the simulator
        }
    }
}
