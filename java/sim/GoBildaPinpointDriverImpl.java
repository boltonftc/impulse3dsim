package sim;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;

// Simulated goBILDA Pinpoint: feeds the real GoBildaPinpointDriver API from the sim's
// ground-truth robot pose, so captured/Pedro autos read position exactly like a real
// Pinpoint would. Ground-truth axis relabel (matches the assumption that forward =
// (cos heading, sin heading)): xFtc = world Z, yFtc = world X, heading = yaw.
//
// RELATIVE tracking, not absolute: a real Pinpoint has no idea what "ground truth" is -- it
// just integrates from whatever pose it was last told it's at (via setPosition/resetPosAndIMU).
// So this class anchors to the declared pose at the last setPosition() and reports
//   reported = anchor + R(phi) * (groundTruth - groundTruthAtReset)
// every tick, where phi = declaredHeading - physicalYawAtReset. That rotation puts the reported
// frame on the robot's start heading -- so after resetPosAndIMU() driving forward reads +X and
// strafing left reads +Y, exactly like real hardware -- instead of staying locked to world axes.
// Because the reported frame is a rigid body-attached frame, a Pedro follower's field-frame
// command maps straight back to reported motion, so autos converge for any declared start heading.
public class GoBildaPinpointDriverImpl extends com.qualcomm.hardware.gobilda.GoBildaPinpointDriver {

    private final int slot;
    private double anchorXmm = 0, anchorYmm = 0, anchorHrad = 0;   // declared pose at last setPosition
    private double gtResetXmm = 0, gtResetYmm = 0, gtResetHrad = 0; // ground truth at last setPosition
    private double cosPhi = 1.0, sinPhi = 0.0;                      // R(phi), phi = anchorHrad - gtResetHrad

    public GoBildaPinpointDriverImpl(int slot) {
        this.slot = slot;
    }

    private double groundTruthXmm() { return OpModeHost.simZ(slot) * 1000.0; }
    private double groundTruthYmm() { return OpModeHost.simX(slot) * 1000.0; }
    private double groundTruthHrad() { return OpModeHost.simYaw(slot); }

    // Re-anchor: called by resetPosAndIMU() and by team code (real API, e.g. from a Localizer's
    // setStartPose). Latch the declared pose and the ground truth at this instant, and recompute
    // the start-heading rotation so future simUpdate() ticks read relative to this pose.
    @Override
    public synchronized void setPosition(Pose2D pos) {
        super.setPosition(pos);
        anchorXmm = pos.getX(DistanceUnit.MM);
        anchorYmm = pos.getY(DistanceUnit.MM);
        anchorHrad = pos.getHeading(AngleUnit.RADIANS);
        gtResetXmm = groundTruthXmm();
        gtResetYmm = groundTruthYmm();
        gtResetHrad = groundTruthHrad();
        double phi = anchorHrad - gtResetHrad;
        cosPhi = Math.cos(phi);
        sinPhi = Math.sin(phi);
    }

    // Called by OpModeHost's sampler thread each tick (not by team code).
    public void simUpdate() {
        double dX = groundTruthXmm() - gtResetXmm;
        double dY = groundTruthYmm() - gtResetYmm;
        double xFtcMm = anchorXmm + cosPhi * dX - sinPhi * dY;
        double yFtcMm = anchorYmm + sinPhi * dX + cosPhi * dY;
        double yaw = anchorHrad + (groundTruthHrad() - gtResetHrad);

        liveXmm = xFtcMm;
        liveYmm = yFtcMm;
        liveHrad = yaw;

        // Rotate the world-frame velocity into the same reported frame as the position.
        double wVX = OpModeHost.simVelZ(slot) * 1000.0;
        double wVY = OpModeHost.simVelX(slot) * 1000.0;
        liveVXmm = cosPhi * wVX - sinPhi * wVY;
        liveVYmm = sinPhi * wVX + cosPhi * wVY;
        liveVHrad = OpModeHost.simYawRate(slot);

        liveEncoderX = (int) Math.round(xFtcMm * encoderResolution);
        liveEncoderY = (int) Math.round(yFtcMm * encoderResolution);
    }
}

