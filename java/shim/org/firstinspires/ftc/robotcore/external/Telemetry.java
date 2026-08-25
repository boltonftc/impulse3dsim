package org.firstinspires.ftc.robotcore.external;

public interface Telemetry {
    Item addData(String caption, Object value);
    Item addData(String caption, String format, Object... args);
    Line addLine();
    Line addLine(String lineCaption);
    boolean removeItem(Item item);
    void clear();
    void clearAll();
    boolean update();
    void setAutoClear(boolean autoClear);
    boolean isAutoClear();
    Telemetry.Log log();

    interface Item {
        String getCaption();
        Item setCaption(String caption);
        Item setValue(Object value);
        Item setValue(String format, Object... args);
    }

    interface Line {
        Item addData(String caption, Object value);
        Item addData(String caption, String format, Object... args);
    }

    interface Log {
        void add(String entry);
        void clear();
    }
}
