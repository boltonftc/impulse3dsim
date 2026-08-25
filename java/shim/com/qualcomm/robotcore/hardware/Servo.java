package com.qualcomm.robotcore.hardware;

// Positional servo: commanded to a normalized position in [0, 1].
public interface Servo extends HardwareDevice {
    enum Direction {
        FORWARD,
        REVERSE
    }

    void setDirection(Direction direction);
    Direction getDirection();
    void setPosition(double position);
    double getPosition();
    void scaleRange(double min, double max);
}
