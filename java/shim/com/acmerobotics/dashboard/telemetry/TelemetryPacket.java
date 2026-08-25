package com.acmerobotics.dashboard.telemetry;

import com.acmerobotics.dashboard.canvas.Canvas;

import java.util.LinkedHashMap;
import java.util.Map;

// Minimal FTC Dashboard TelemetryPacket: a bag of named values (for the dashboard's live graphs)
// plus a field-overlay Canvas. In the sim, put() just stores the value and fieldOverlay() hands
// back a no-op Canvas -- nothing renders, matching the real dashboard's behavior with no client
// connected. Kept so student/capstone code compiles and runs unchanged from a real robot.
public class TelemetryPacket {

    private final Map<String, Object> data = new LinkedHashMap<String, Object>();
    private final Canvas fieldOverlay = new Canvas();

    public void put(String key, Object value) { data.put(key, value); }
    public void put(String key, double value) { data.put(key, value); }
    public void put(String key, int value)    { data.put(key, value); }
    public void put(String key, boolean value){ data.put(key, value); }

    public Map<String, Object> getData() { return data; }

    public Canvas fieldOverlay() { return fieldOverlay; }
}
