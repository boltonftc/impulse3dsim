package com.qualcomm.robotcore.hardware;

// Slim v2 shim: same student-facing fields as the FTC Gamepad, no sim/IPC coupling.
public class Gamepad {
    public volatile float left_stick_x;
    public volatile float left_stick_y;   // inverted: up = -1.0
    public volatile float right_stick_x;
    public volatile float right_stick_y;

    public volatile float left_trigger;
    public volatile float right_trigger;

    public volatile boolean dpad_up;
    public volatile boolean dpad_down;
    public volatile boolean dpad_left;
    public volatile boolean dpad_right;

    public volatile boolean a;
    public volatile boolean b;
    public volatile boolean x;
    public volatile boolean y;

    public volatile boolean left_bumper;
    public volatile boolean right_bumper;
    public volatile boolean left_stick_button;
    public volatile boolean right_stick_button;

    public volatile boolean guide;
    public volatile boolean start;
    public volatile boolean back;
}
