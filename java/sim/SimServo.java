package sim;

import com.qualcomm.robotcore.hardware.ServoImplEx;

// Browser-side positional servo: clamps and records the commanded position [0, 1] and its PWM range.
public class SimServo implements ServoImplEx {

    private final String name;
    private Direction direction = Direction.FORWARD;
    private double position = 0.0;
    private PwmRange pwmRange = PwmRange.defaultRange;
    private boolean pwmEnabled = true;

    public SimServo(String name) {
        this.name = name;
    }

    @Override public void setDirection(Direction direction) { this.direction = direction; }
    @Override public Direction getDirection() { return direction; }
    @Override public void setPosition(double position) { this.position = Math.max(0.0, Math.min(1.0, position)); }
    @Override public double getPosition() { return position; }
    /** Position the hardware actually goes to: a REVERSE servo mirrors the commanded value. */
    public double getEffectivePosition() { return direction == Direction.REVERSE ? 1.0 - position : position; }
    /** Where the pulse lands across the servo's full 500..2500 us travel (0..1); 0.5 stays centered for any symmetric range. */
    public double getPulseFraction() {
        double us = pwmRange.usPulseLower + getEffectivePosition() * (pwmRange.usPulseUpper - pwmRange.usPulseLower);
        return Math.max(0.0, Math.min(1.0, (us - 500.0) / 2000.0));
    }
    @Override public void scaleRange(double min, double max) { }

    @Override public void setPwmRange(PwmRange range) { if (range != null) pwmRange = range; }
    @Override public PwmRange getPwmRange() { return pwmRange; }
    @Override public void setPwmEnable() { pwmEnabled = true; }
    @Override public void setPwmDisable() { pwmEnabled = false; }
    @Override public boolean isPwmEnabled() { return pwmEnabled; }

    @Override public String getDeviceName() { return name; }
    @Override public String getConnectionInfo() { return "sim"; }
    @Override public int getVersion() { return 1; }
    @Override public void close() { }
}
