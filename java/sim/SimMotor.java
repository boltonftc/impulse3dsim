package sim;

import com.qualcomm.robotcore.hardware.DcMotorEx;

// Browser-side implementation of the FTC DcMotor API.
// Records commanded power so the harness can prove student code reached hardware.
public class SimMotor implements DcMotorEx {

    private final String name;
    private Direction direction = Direction.FORWARD;
    private double power = 0.0;
    private double velocity = 0.0;
    private RunMode mode = RunMode.RUN_WITHOUT_ENCODER;
    private ZeroPowerBehavior zeroPowerBehavior = ZeroPowerBehavior.BRAKE;
    private int targetPosition = 0;

    private int setPowerCalls = 0;
    private double maxAbsPower = 0.0;

    public SimMotor(String name) {
        this.name = name;
    }

    public int getSetPowerCalls() { return setPowerCalls; }
    public double getMaxAbsPower() { return maxAbsPower; }

    /** Effective power after direction, as the sim/physics layer would consume it. */
    public double getEffectivePower() {
        return direction == Direction.REVERSE ? -power : power;
    }

    // DcMotorSimple
    @Override public void setDirection(Direction direction) { this.direction = direction; }
    @Override public Direction getDirection() { return direction; }

    @Override public void setPower(double power) {
        power = Math.max(-1.0, Math.min(1.0, power));
        this.power = power;
        this.setPowerCalls++;
        this.maxAbsPower = Math.max(this.maxAbsPower, Math.abs(power));
    }
    @Override public double getPower() { return power; }

    // DcMotorEx velocity (rad/s); the sim reads this to spin a flywheel closed-loop.
    @Override public void setVelocity(double angularRate) { this.velocity = angularRate; this.setPowerCalls++; }
    @Override public double getVelocity() { return velocity; }

    // DcMotor
    @Override public void setMode(RunMode mode) { this.mode = mode; }
    @Override public RunMode getMode() { return mode; }
    @Override public void setZeroPowerBehavior(ZeroPowerBehavior b) { this.zeroPowerBehavior = b; }
    @Override public ZeroPowerBehavior getZeroPowerBehavior() { return zeroPowerBehavior; }
    @Override public int getCurrentPosition() { return 0; }
    @Override public void setTargetPosition(int position) { this.targetPosition = position; }
    @Override public int getTargetPosition() { return targetPosition; }
    @Override public boolean isBusy() { return false; }

    // HardwareDevice
    @Override public String getDeviceName() { return name; }
    @Override public String getConnectionInfo() { return "sim"; }
    @Override public int getVersion() { return 1; }
    @Override public void close() { }
}
