// YawPitchRollAngles -- an immutable snapshot of the IMU's orientation. The one students use is
// yaw (heading). Stored internally in radians; getters convert to whatever AngleUnit is asked for.
package org.firstinspires.ftc.robotcore.external.navigation;

public class YawPitchRollAngles {

    private final double yawRad;
    private final double pitchRad;
    private final double rollRad;

    public YawPitchRollAngles(AngleUnit angleUnit, double yaw, double pitch, double roll, long acquisitionTime) {
        this.yawRad   = angleUnit.toRadians(yaw);
        this.pitchRad = angleUnit.toRadians(pitch);
        this.rollRad  = angleUnit.toRadians(roll);
    }

    public double getYaw(AngleUnit unit)   { return unit.fromRadians(yawRad); }
    public double getPitch(AngleUnit unit) { return unit.fromRadians(pitchRad); }
    public double getRoll(AngleUnit unit)  { return unit.fromRadians(rollRad); }

    public double getYaw()   { return getYaw(AngleUnit.DEGREES); }
    public double getPitch() { return getPitch(AngleUnit.DEGREES); }
    public double getRoll()  { return getRoll(AngleUnit.DEGREES); }
}
