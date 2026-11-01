package com.qualcomm.robotcore.hardware;

// A servo with PWM control. In the real SDK this is a class; student code only uses it as a type
// (hardwareMap.get(ServoImplEx.class, ...)), so an interface is enough for the shim.
public interface ServoImplEx extends Servo, PwmControl {
}
