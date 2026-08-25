// AngleUnit -- DEGREES or RADIANS, normalized (matches real FTC SDK shape).
package org.firstinspires.ftc.robotcore.external.navigation;

public enum AngleUnit {
    DEGREES,
    RADIANS;

    public double fromDegrees(double degrees) {
        return this == RADIANS ? Math.toRadians(degrees) : degrees;
    }

    public double fromRadians(double radians) {
        return this == DEGREES ? Math.toDegrees(radians) : radians;
    }

    public double toDegrees(double value) {
        return this == RADIANS ? Math.toDegrees(value) : value;
    }

    public double toRadians(double value) {
        return this == DEGREES ? Math.toRadians(value) : value;
    }

    public double fromUnit(AngleUnit source, double value) {
        return fromRadians(source.toRadians(value));
    }
}
