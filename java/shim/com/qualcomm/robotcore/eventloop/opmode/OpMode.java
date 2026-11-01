package com.qualcomm.robotcore.eventloop.opmode;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.HashMap;

public abstract class OpMode {
    /** FTC SDK 10.3+: survives from one OpMode to the next (not across robot restarts) -- e.g. auto -> TeleOp. */
    public static final HashMap<String, Object> blackboard = new HashMap<>();

    public Gamepad gamepad1 = new Gamepad();
    public Gamepad gamepad2 = new Gamepad();
    public Telemetry telemetry;
    public HardwareMap hardwareMap;

    /** Seconds since OpMode started. Updated before each loop iteration by the runner. */
    public double time;

    private volatile long startTimeNanos = System.nanoTime();

    public abstract void init();
    public abstract void loop();
    public void init_loop() {}
    public void start() {}
    public void stop() {}

    public double getRuntime() {
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
    }

    public void resetStartTime() {
        startTimeNanos = System.nanoTime();
    }
}
