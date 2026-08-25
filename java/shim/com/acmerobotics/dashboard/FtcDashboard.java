package com.acmerobotics.dashboard;

import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

// Minimal FTC Dashboard entry point. On a real Control Hub this streams live telemetry graphs
// and the field overlay to a laptop browser at 192.168.43.1:8080. In the sim there is no
// dashboard client to stream to, so sendTelemetryPacket() is a genuine no-op -- student code that
// uses the real Dashboard API (getInstance().sendTelemetryPacket(...)) compiles and runs
// unchanged; it simply has nothing visible to show here.
public final class FtcDashboard {

    private static final FtcDashboard INSTANCE = new FtcDashboard();

    private FtcDashboard() { }

    public static FtcDashboard getInstance() {
        return INSTANCE;
    }

    public void sendTelemetryPacket(TelemetryPacket packet) {
        // no-op in the simulator
    }
}
