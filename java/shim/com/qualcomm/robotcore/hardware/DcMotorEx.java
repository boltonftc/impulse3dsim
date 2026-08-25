package com.qualcomm.robotcore.hardware;

// Extended motor: adds closed-loop velocity control on top of DcMotor.
public interface DcMotorEx extends DcMotor {
    void setVelocity(double angularRate);
    double getVelocity();
}
