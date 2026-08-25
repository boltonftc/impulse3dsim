// DistanceUnit -- MM, CM, METER, INCH. Java 8-safe (no switch expressions).
package org.firstinspires.ftc.robotcore.external.navigation;

public enum DistanceUnit {
    MM,
    CM,
    METER,
    INCH;

    public double fromMm(double mm) {
        switch (this) {
            case MM: return mm;
            case CM: return mm / 10.0;
            case METER: return mm / 1000.0;
            case INCH: return mm / 25.4;
            default: return mm;
        }
    }

    public double toMm(double value) {
        switch (this) {
            case MM: return value;
            case CM: return value * 10.0;
            case METER: return value * 1000.0;
            case INCH: return value * 25.4;
            default: return value;
        }
    }

    public double fromUnit(DistanceUnit source, double value) {
        return fromMm(source.toMm(value));
    }

    public double toInches(double value) {
        return INCH.fromMm(toMm(value));
    }

    public double toMeters(double value) {
        return METER.fromMm(toMm(value));
    }
}
