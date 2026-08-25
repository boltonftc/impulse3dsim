package org.firstinspires.ftc.teamcode;

import com.pedropathing.geometry.Pose;
import com.pedropathing.localization.Localizer;
import com.pedropathing.math.Vector;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;

/*
 * ============================================================================
 *  PinpointLocalizer  --  the autonomous "where am I?" for Pedro Pathing
 * ============================================================================
 *
 *  TeleOp reads position through the Localization subsystem. Autonomous is
 *  different: Pedro Pathing's Follower needs a Localizer object of its OWN to
 *  steer along a path. THIS class is that Localizer.
 *
 *  It is backed by the SAME physical goBILDA Pinpoint the Localization
 *  subsystem uses, configured from the SAME single source of truth
 *  (RobotConfig.configurePinpoint), so the two can never disagree about the
 *  one sensor they share. There is no camera and no sensor fusion here -- the
 *  Pinpoint's dead-wheel odometry alone drives the autonomous.
 *
 *  Works unchanged on the sim (a simulated Pinpoint reads ground truth) and on
 *  the real robot (the real I2C driver); only what hardwareMap.get() hands back
 *  differs.
 * ============================================================================
 */
public class PinpointLocalizer implements Localizer {

    private final GoBildaPinpointDriver pinpoint;
    private Pose currentPose = new Pose(0, 0, 0);
    private Pose currentVelocity = new Pose(0, 0, 0);
    private double totalHeading = 0.0, lastHeadingRad = 0.0;

    public PinpointLocalizer(HardwareMap hardwareMap) {
        pinpoint = hardwareMap.get(GoBildaPinpointDriver.class, RobotConfig.PINPOINT);
        RobotConfig.configurePinpoint(pinpoint);   // ROBOT MUST BE STATIONARY here
    }

    @Override
    public Pose getPose() { return currentPose; }

    @Override
    public Pose getVelocity() { return currentVelocity; }

    @Override
    public Vector getVelocityVector() {
        Vector v = new Vector();
        v.setComponents(currentVelocity.getX(), currentVelocity.getY());
        return v;
    }

    // Tell the Pinpoint itself where the robot is (its own setPosition re-anchors) --
    // this is exactly what a real Pinpoint-based localizer does; no extra offset math needed here.
    @Override
    public void setStartPose(Pose pose) {
        pinpoint.setPosition(new Pose2D(DistanceUnit.INCH, pose.getX(), pose.getY(), AngleUnit.RADIANS, pose.getHeading()));
        pinpoint.update();
        currentPose = pose;
        lastHeadingRad = pose.getHeading();
    }

    // Re-anchor to an arbitrary pose mid-match -- identical to setStartPose();
    // a real Pinpoint's setPosition() is the same call either way.
    @Override
    public void setPose(Pose pose) {
        setStartPose(pose);
    }

    // Called by Follower each tick.
    @Override
    public void update() {
        pinpoint.update();

        double xIn = DistanceUnit.INCH.fromMm(pinpoint.getPosX(DistanceUnit.MM));
        double yIn = DistanceUnit.INCH.fromMm(pinpoint.getPosY(DistanceUnit.MM));
        double headingRad = pinpoint.getHeading(AngleUnit.RADIANS);
        currentPose = new Pose(xIn, yIn, headingRad);

        // unwrap heading so multi-turn spins accumulate instead of wrapping at +-PI
        double delta = headingRad - lastHeadingRad;
        if (delta > Math.PI) delta -= 2 * Math.PI;
        if (delta < -Math.PI) delta += 2 * Math.PI;
        totalHeading += delta;
        lastHeadingRad = headingRad;

        double vxIn = DistanceUnit.INCH.fromMm(pinpoint.getVelX(DistanceUnit.MM));
        double vyIn = DistanceUnit.INCH.fromMm(pinpoint.getVelY(DistanceUnit.MM));
        currentVelocity = new Pose(vxIn, vyIn, 0);
    }

    @Override
    public double getTotalHeading() { return totalHeading; }

    // Tuning scalars a real robot uses to correct systematic sensor error (e.g. from a measured
    // 64-inch test drive). The Pinpoint reports true distances in the sim, so these stay at 1.0;
    // real-robot teams can expose/tune these the same way Pedro's other localizers do.
    @Override
    public double getForwardMultiplier() { return 1.0; }
    @Override
    public double getLateralMultiplier() { return 1.0; }
    @Override
    public double getTurningMultiplier() { return 1.0; }

    @Override
    public void resetIMU() throws InterruptedException {
        pinpoint.recalibrateIMU();   // ROBOT MUST BE STATIONARY
    }

    @Override
    public double getIMUHeading() { return pinpoint.getHeading(AngleUnit.RADIANS); }

    @Override
    public boolean isNAN() {
        return Double.isNaN(currentPose.getX()) || Double.isNaN(currentPose.getY()) || Double.isNaN(currentPose.getHeading());
    }
}
