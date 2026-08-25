package sim;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;

// Simulated goBILDA Pinpoint: feeds the real GoBildaPinpointDriver API from the sim's
// ground-truth robot pose, so captured/Pedro autos read position exactly like a real
// Pinpoint would. Coordinate convention (matches PedroSimDrivetrain's assumption that
// forward = (cos heading, sin heading)): xFtc = world Z, yFtc = world X, heading = yaw.
// This is an axis relabel, not a rotation, so velocity maps the same way with no
// heading-dependent trig needed.
//
// RELATIVE tracking, not absolute: a real Pinpoint has no idea what "ground truth" is -- it
// just integrates from whatever pose it was last told it's at (via setPosition/resetPosAndIMU).
// So this class stores a ground-truth-to-reported OFFSET, established whenever setPosition()
// is called, and simUpdate() reports (ground truth - offset) every tick. Reporting raw ground
// truth directly (no offset) would silently erase every calibration on the very next tick.
public class GoBildaPinpointDriverImpl extends com.qualcomm.hardware.gobilda.GoBildaPinpointDriver {

    private final int slot;
    private double offsetXmm = 0, offsetYmm = 0, offsetHrad = 0;

    public GoBildaPinpointDriverImpl(int slot) {
        this.slot = slot;
    }

    private double groundTruthXmm() { return OpModeHost.simZ(slot) * 1000.0; }
    private double groundTruthYmm() { return OpModeHost.simX(slot) * 1000.0; }
    private double groundTruthHrad() { return OpModeHost.simYaw(slot); }

    // Re-anchor: called by resetPosAndIMU() and by team code (real API, e.g. from a Localizer's
    // setStartPose). Recompute the offset so future simUpdate() ticks read relative to this pose.
    @Override
    public synchronized void setPosition(Pose2D pos) {
        super.setPosition(pos);
        offsetXmm = groundTruthXmm() - pos.getX(DistanceUnit.MM);
        offsetYmm = groundTruthYmm() - pos.getY(DistanceUnit.MM);
        offsetHrad = groundTruthHrad() - pos.getHeading(AngleUnit.RADIANS);
    }

    // Called by OpModeHost's sampler thread each tick (not by team code).
    public void simUpdate() {
        double xFtcMm = groundTruthXmm() - offsetXmm;
        double yFtcMm = groundTruthYmm() - offsetYmm;
        double yaw = groundTruthHrad() - offsetHrad;

        liveXmm = xFtcMm;
        liveYmm = yFtcMm;
        liveHrad = yaw;

        liveVXmm = OpModeHost.simVelZ(slot) * 1000.0;
        liveVYmm = OpModeHost.simVelX(slot) * 1000.0;
        liveVHrad = OpModeHost.simYawRate(slot);

        liveEncoderX = (int) Math.round(xFtcMm * encoderResolution);
        liveEncoderY = (int) Math.round(yFtcMm * encoderResolution);
    }
}

