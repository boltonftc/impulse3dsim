package sim;

import com.qualcomm.robotcore.hardware.CRServo;

// Browser-side CRServo: records commanded power so the sim can spin a mechanism.
public class SimCRServo implements CRServo {

    private final String name;
    private Direction direction = Direction.FORWARD;
    private double power = 0.0;

    public SimCRServo(String name) {
        this.name = name;
    }

    /** Power after direction, as the sim/physics layer would consume it. */
    public double getEffectivePower() {
        return direction == Direction.REVERSE ? -power : power;
    }

    @Override public void setDirection(Direction direction) { this.direction = direction; }
    @Override public Direction getDirection() { return direction; }
    @Override public void setPower(double power) { this.power = Math.max(-1.0, Math.min(1.0, power)); }
    @Override public double getPower() { return power; }

    @Override public String getDeviceName() { return name; }
    @Override public String getConnectionInfo() { return "sim"; }
    @Override public int getVersion() { return 1; }
    @Override public void close() { }
}
