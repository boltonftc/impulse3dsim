package sim;

import com.qualcomm.robotcore.hardware.Servo;

// Browser-side positional servo: clamps and records the commanded position [0, 1].
public class SimServo implements Servo {

    private final String name;
    private Direction direction = Direction.FORWARD;
    private double position = 0.0;

    public SimServo(String name) {
        this.name = name;
    }

    @Override public void setDirection(Direction direction) { this.direction = direction; }
    @Override public Direction getDirection() { return direction; }
    @Override public void setPosition(double position) { this.position = Math.max(0.0, Math.min(1.0, position)); }
    @Override public double getPosition() { return position; }
    @Override public void scaleRange(double min, double max) { }

    @Override public String getDeviceName() { return name; }
    @Override public String getConnectionInfo() { return "sim"; }
    @Override public int getVersion() { return 1; }
    @Override public void close() { }
}
