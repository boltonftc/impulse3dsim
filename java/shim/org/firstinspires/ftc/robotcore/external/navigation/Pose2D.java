// Pose2D -- FTC SDK-compatible 2D pose (position + heading), unit-aware and immutable.
// Matches org.firstinspires.ftc.robotcore.external.navigation.Pose2D from the real SDK
// (the type GoBildaPinpointDriver.getPosition() returns) so student code is portable.
package org.firstinspires.ftc.robotcore.external.navigation;

public class Pose2D {

    protected final double x;
    protected final double y;
    protected final DistanceUnit distanceUnit;
    protected final double heading;
    protected final AngleUnit headingUnit;

    public Pose2D(DistanceUnit distanceUnit, double x, double y,
                  AngleUnit headingUnit, double heading) {
        this.x = x;
        this.y = y;
        this.distanceUnit = distanceUnit;
        this.heading = heading;
        this.headingUnit = headingUnit;
    }

    public double getX(DistanceUnit unit) {
        return unit.fromUnit(this.distanceUnit, x);
    }

    public double getY(DistanceUnit unit) {
        return unit.fromUnit(this.distanceUnit, y);
    }

    public double getHeading(AngleUnit unit) {
        return unit.fromUnit(this.headingUnit, heading);
    }

    @Override
    public String toString() {
        return "(Pose2D) x=" + x + ", y=" + y + " " + distanceUnit
                + ", heading=" + heading + " " + headingUnit;
    }
}
