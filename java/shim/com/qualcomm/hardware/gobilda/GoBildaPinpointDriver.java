/**
 * GoBildaPinpointDriver -- FTC SDK-compatible shim for the goBILDA Pinpoint
 * Odometry Computer (SKU 3110-0002 / part of the 4-Bar Odometry Pack).
 *
 * This class matches the public API of com.qualcomm.hardware.gobilda.
 * GoBildaPinpointDriver from the real FTC SDK so that the SAME team code runs
 * unchanged in this simulator and on a real robot. In the sim,
 * sim.GoBildaPinpointDriverImpl extends this class and supplies pose data
 * from the simulated world. On a real robot the SDK's own driver talks to the
 * hardware over I2C.
 *
 * COORDINATE FRAME
 * After resetPosAndIMU()/setPosition() the pose is anchored to whatever field
 * frame you provide. Heading is CCW-positive, matching Pedro Pathing's
 * heading-0-facing-+X convention once you set your real field start pose.
 */
package com.qualcomm.hardware.gobilda;

import com.qualcomm.robotcore.hardware.HardwareDevice;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

public class GoBildaPinpointDriver implements HardwareDevice {

    public static final byte DEFAULT_ADDRESS = 0x31;

    // goBILDA odometry pod resolutions, in encoder ticks per millimeter.
    public static final double goBILDA_SWINGARM_POD = 13.26291192;
    public static final double goBILDA_4_BAR_POD    = 19.89436789;

    protected double xOffsetMm = 0.0;
    protected double yOffsetMm = 0.0;
    protected double encoderResolution = goBILDA_4_BAR_POD;
    protected EncoderDirection xEncoderDirection = EncoderDirection.FORWARD;
    protected EncoderDirection yEncoderDirection = EncoderDirection.FORWARD;
    protected double yawScalar = 1.0;

    // "Live" values -- in the sim, GoBildaPinpointDriverImpl pushes these in each tick;
    // update() latches them into the snapshot the getters return (matches real hardware,
    // where your readings are stale until you call update()).
    protected volatile double liveXmm, liveYmm, liveHrad;
    protected volatile double liveVXmm, liveVYmm, liveVHrad;
    protected volatile int liveEncoderX, liveEncoderY;

    private double posXmm, posYmm, hRad;
    private double velXmm, velYmm, velHrad;
    private int encoderX, encoderY;

    public GoBildaPinpointDriver() { }

    public enum EncoderDirection { FORWARD, REVERSED }

    public enum GoBildaOdometryPods { goBILDA_SWINGARM_POD, goBILDA_4_BAR_POD }

    public enum DeviceStatus {
        NOT_READY, READY, CALIBRATING,
        FAULT_X_POD_NOT_DETECTED, FAULT_Y_POD_NOT_DETECTED, FAULT_NO_PODS_DETECTED,
        FAULT_IMU_RUNAWAY, FAULT_BAD_READ
    }

    public enum ReadData { ONLY_UPDATE_HEADING }

    @Override public String getDeviceName() { return "goBILDA Pinpoint Odometry Computer"; }
    @Override public String getConnectionInfo() { return "I2C (simulated)"; }
    @Override public int getVersion() { return 1; }
    @Override public void close() { }

    /** Read new data. CALL ONCE AT THE TOP OF EVERY LOOP -- nothing updates until you do. */
    public synchronized void update() {
        posXmm = liveXmm; posYmm = liveYmm; hRad = liveHrad;
        velXmm = liveVXmm; velYmm = liveVYmm; velHrad = liveVHrad;
        encoderX = liveEncoderX; encoderY = liveEncoderY;
    }

    /** Faster partial update -- only ONLY_UPDATE_HEADING is supported. */
    public synchronized void update(ReadData data) {
        if (data == ReadData.ONLY_UPDATE_HEADING) {
            hRad = liveHrad;
            velHrad = liveVHrad;
        }
    }

    public synchronized void setOffsets(double xOffset, double yOffset, DistanceUnit distanceUnit) {
        this.xOffsetMm = distanceUnit.toMm(xOffset);
        this.yOffsetMm = distanceUnit.toMm(yOffset);
    }

    public synchronized double getXOffset(DistanceUnit distanceUnit) { return distanceUnit.fromMm(xOffsetMm); }
    public synchronized double getYOffset(DistanceUnit distanceUnit) { return distanceUnit.fromMm(yOffsetMm); }

    public synchronized void setEncoderResolution(GoBildaOdometryPods pods) {
        if (pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD) encoderResolution = goBILDA_SWINGARM_POD;
        else if (pods == GoBildaOdometryPods.goBILDA_4_BAR_POD) encoderResolution = goBILDA_4_BAR_POD;
    }

    public synchronized void setEncoderResolution(double ticksPerUnit, DistanceUnit distanceUnit) {
        encoderResolution = 1.0 / distanceUnit.toMm(1.0 / ticksPerUnit);
    }

    public synchronized void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
        this.xEncoderDirection = xEncoder;
        this.yEncoderDirection = yEncoder;
    }

    public synchronized void setYawScalar(double yawScalar) { this.yawScalar = yawScalar; }
    public synchronized float getYawScalar() { return (float) yawScalar; }

    /** ROBOT MUST BE STATIONARY. Recalibrates the IMU; does not move the tracked position. */
    public void recalibrateIMU() { /* sim: IMU is always calibrated */ }

    /** ROBOT MUST BE STATIONARY. Resets the tracked pose to (0,0,0) and recalibrates the IMU. */
    public synchronized void resetPosAndIMU() {
        setPosition(new Pose2D(DistanceUnit.MM, 0, 0, AngleUnit.RADIANS, 0));
    }

    public synchronized Pose2D getPosition() {
        return new Pose2D(DistanceUnit.MM, posXmm, posYmm, AngleUnit.RADIANS, hRad);
    }

    public synchronized double getPosX(DistanceUnit distanceUnit) { return distanceUnit.fromMm(posXmm); }
    public synchronized double getPosY(DistanceUnit distanceUnit) { return distanceUnit.fromMm(posYmm); }
    public synchronized double getHeading(AngleUnit angleUnit) { return angleUnit.fromRadians(hRad); }
    public synchronized double getVelX(DistanceUnit distanceUnit) { return distanceUnit.fromMm(velXmm); }
    public synchronized double getVelY(DistanceUnit distanceUnit) { return distanceUnit.fromMm(velYmm); }

    public synchronized double getHeadingVelocity(UnnormalizedAngleUnit angleUnit) {
        return angleUnit.fromRadians(velHrad);
    }

    public synchronized int getEncoderX() { return encoderX; }
    public synchronized int getEncoderY() { return encoderY; }

    /**
     * Override the tracked pose -- used for a field-relative start (send your known
     * starting pose at auto-init) or sensor fusion (push a corrected pose from vision).
     */
    public synchronized void setPosition(Pose2D pos) {
        posXmm = pos.getX(DistanceUnit.MM);
        posYmm = pos.getY(DistanceUnit.MM);
        hRad   = pos.getHeading(AngleUnit.RADIANS);
        liveXmm = posXmm; liveYmm = posYmm; liveHrad = hRad;
    }

    public synchronized void setPosX(double posX, DistanceUnit distanceUnit) {
        setPosition(new Pose2D(DistanceUnit.MM, distanceUnit.toMm(posX), posYmm, AngleUnit.RADIANS, hRad));
    }

    public synchronized void setPosY(double posY, DistanceUnit distanceUnit) {
        setPosition(new Pose2D(DistanceUnit.MM, posXmm, distanceUnit.toMm(posY), AngleUnit.RADIANS, hRad));
    }

    public synchronized void setHeading(double heading, AngleUnit angleUnit) {
        setPosition(new Pose2D(DistanceUnit.MM, posXmm, posYmm, AngleUnit.RADIANS, angleUnit.toRadians(heading)));
    }

    public int getDeviceID() { return 1; }
    public int getDeviceVersion() { return 1; }
    public synchronized DeviceStatus getDeviceStatus() { return DeviceStatus.READY; }
    public int getLoopTime() { return 1000; }
    public double getFrequency() {
        int lt = getLoopTime();
        return lt != 0 ? 1_000_000.0 / lt : 0.0;
    }
}
