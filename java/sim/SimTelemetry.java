package sim;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;

// Browser-side Telemetry. Buffers items; on update() emits at most ~5x/sec to a
// pluggable sink (the host routes it to the on-screen HUD) so a fast control loop
// can't flood the console.
public class SimTelemetry implements Telemetry {

    // Host installs this to forward a telemetry snapshot to JS; may stay null.
    public static java.util.function.Consumer<String> sink;
    // Per-instance sink (preferred): lets each alliance slot route to its own HUD.
    public java.util.function.Consumer<String> out;

    private final List<String> pending = new ArrayList<>();
    private boolean autoClear = true;
    private int updateCount = 0;
    private long lastEmitMs = 0;

    public int getUpdateCount() { return updateCount; }

    @Override
    public Item addData(String caption, Object value) {
        pending.add(caption + " : " + value);
        return new SimItem(caption);
    }

    @Override
    public Item addData(String caption, String format, Object... args) {
        pending.add(caption + " : " + String.format(format, args));
        return new SimItem(caption);
    }

    @Override public Line addLine() { return addLine(""); }

    @Override
    public Line addLine(String lineCaption) {
        if (lineCaption != null && !lineCaption.isEmpty()) pending.add(lineCaption);
        return new SimLine();
    }

    @Override public boolean removeItem(Item item) { return false; }
    @Override public void clear() { pending.clear(); }
    @Override public void clearAll() { pending.clear(); }

    @Override
    public boolean update() {
        updateCount++;
        long now = System.currentTimeMillis();
        if (now - lastEmitMs >= 200) {
            lastEmitMs = now;
            java.util.function.Consumer<String> s = out != null ? out : sink;
            if (s != null) s.accept(String.join(" | ", pending));
        }
        if (autoClear) pending.clear();
        return true;
    }

    @Override public void setAutoClear(boolean autoClear) { this.autoClear = autoClear; }
    @Override public boolean isAutoClear() { return autoClear; }

    @Override
    public Log log() {
        return new Log() {
            @Override public void add(String entry) { System.out.println("[LOG] " + entry); }
            @Override public void clear() { }
        };
    }

    private static class SimItem implements Item {
        private String caption;
        SimItem(String caption) { this.caption = caption; }
        @Override public String getCaption() { return caption; }
        @Override public Item setCaption(String caption) { this.caption = caption; return this; }
        @Override public Item setValue(Object value) { return this; }
        @Override public Item setValue(String format, Object... args) { return this; }
    }

    private class SimLine implements Line {
        @Override public Item addData(String caption, Object value) { return SimTelemetry.this.addData(caption, value); }
        @Override public Item addData(String caption, String format, Object... args) { return SimTelemetry.this.addData(caption, format, args); }
    }
}
