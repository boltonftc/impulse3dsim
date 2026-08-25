package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;

/*
 * ============================================================================
 *  Localization  --  owned by Student D
 * ============================================================================
 *
 *  "Localization" means knowing WHERE the robot is on the field (its x, y
 *  position) and WHICH WAY it is facing (its heading). Everything else uses
 *  this: field-centric driving needs the heading, and autonomous needs the
 *  position.
 *
 *  THE SENSOR:
 *    goBILDA Pinpoint -- two dead-wheel odometry pods plus its own gyro. It
 *    tracks the robot's position continuously and is enough on its own to
 *    drive field-centric and to run autonomous.
 *
 *  This subsystem calls the Pinpoint's update() once per loop (from our own
 *  update()) and hands out the latest heading/position to whoever asks.
 * ============================================================================
 */
public class Localization implements Subsystem {

    // ── CONFIGURATION lives in RobotConfig ───────────────────────────────
    // The pod offsets, pod type, and encoder directions are all defined ONCE
    // in RobotConfig, so this subsystem and the autonomous localizer can never
    // disagree about the one physical Pinpoint they share. Read them there.

    // ── Hardware ─────────────────────────────────────────────────────────
    // These may be null if the device is not in the hardware map, so every use
    // is guarded with an `if (... != null)` check. That is what lets the same
    // code run on a robot that has no Pinpoint or no camera without crashing.
    private final GoBildaPinpointDriver pinpoint;   // null if not configured

    // The most recent position reading, refreshed once per loop in update().
    private Pose2D latestPose = new Pose2D(DistanceUnit.INCH, 0, 0, AngleUnit.RADIANS, 0);

    /*
     * CONSTRUCTOR
     * Runs once when Robot builds this subsystem. It finds the Pinpoint,
     * configures its pods, and zeroes the position. THE ROBOT MUST BE HELD
     * STILL while this runs, because resetPosAndIMU() also calibrates the gyro.
     */
    public Localization(HardwareMap hardwareMap) {
        // Try to find the Pinpoint. If it is not in the configuration, we set
        // it to null and the robot still runs (just without position tracking).
        GoBildaPinpointDriver foundPinpoint = null;
        try {
            foundPinpoint = hardwareMap.get(GoBildaPinpointDriver.class, RobotConfig.PINPOINT);
        } catch (IllegalArgumentException e) {
            foundPinpoint = null;   // "pinpoint" not configured -- run without it
        }
        pinpoint = foundPinpoint;

        // Configure the Pinpoint from the single source of truth (RobotConfig):
        // pod offsets, pod type, encoder directions, and a zeroing reset. The
        // reset also calibrates the gyro, so HOLD THE ROBOT STILL here.
        if (pinpoint != null) {
            RobotConfig.configurePinpoint(pinpoint);
        }
    }

    /*
     * update()  --  refresh our knowledge of where the robot is
     * Called every loop by the scheduler. It pulls fresh data from the
     * Pinpoint over the wire. Everything else in the robot reads the result
     * through the getter methods below.
     */
    @Override
    public void update() {
        if (pinpoint == null) {
            return;   // no sensor -- nothing to update
        }

        // Pull fresh data from the Pinpoint. Everything is stale until we do.
        pinpoint.update();
        latestPose = pinpoint.getPosition();
    }

    // ── GETTERS -- how other subsystems read our position ────────────────

    /** Robot heading in radians (0 = the direction it started). Used by drive. */
    public double getHeadingRadians() {
        return latestPose.getHeading(AngleUnit.RADIANS);
    }

    /** Robot heading in degrees -- easier to read on telemetry. */
    public double getHeadingDegrees() {
        return latestPose.getHeading(AngleUnit.DEGREES);
    }

    /** Robot X position on the field, in inches. */
    public double getXInches() {
        return latestPose.getX(DistanceUnit.INCH);
    }

    /** Robot Y position on the field, in inches. */
    public double getYInches() {
        return latestPose.getY(DistanceUnit.INCH);
    }

    /** True if a real Pinpoint was found and configured. */
    public boolean hasPinpoint() {
        return pinpoint != null;
    }

    /*
     * reset()  --  set the current spot as the new origin (0, 0, heading 0).
     * TeleOp calls this when the driver presses the reset button so "forward"
     * lines up with the field again. HOLD THE ROBOT STILL when calling this.
     */
    public void reset() {
        if (pinpoint != null) {
            pinpoint.resetPosAndIMU();
        }
    }
}
