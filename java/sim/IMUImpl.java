package sim;

import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

// Simulated Control Hub IMU: yaw comes from the sim's ground-truth robot heading. resetYaw()
// stores a software offset so the current facing becomes the new zero (like the real BHI260AP),
// and the reported yaw is wrapped to -PI..PI to match the real IMU's reporting range.
public class IMUImpl implements IMU {

    private final int slot;
    private double yawOffsetRad = 0.0;

    public IMUImpl(int slot) { this.slot = slot; }

    @Override
    public boolean initialize(Parameters parameters) {
        // A real BHI260AP reports yaw 0 right after initialize(), whatever way the
        // robot physically faces. Match that: zero the heading to the start facing.
        yawOffsetRad = OpModeHost.simYaw(slot);
        return true;
    }

    @Override
    public void resetYaw() {
        yawOffsetRad = OpModeHost.simYaw(slot);
    }

    @Override
    public YawPitchRollAngles getRobotYawPitchRollAngles() {
        double yaw = OpModeHost.simYaw(slot) - yawOffsetRad;
        yaw = Math.atan2(Math.sin(yaw), Math.cos(yaw));   // normalize to -PI..PI
        return new YawPitchRollAngles(AngleUnit.RADIANS, yaw, 0.0, 0.0, System.nanoTime());
    }
}
