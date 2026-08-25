package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.HardwareMap;

/*
 * ============================================================================
 *  Localization  --  STARTER STUB (owned by Student D)
 * ============================================================================
 *  Knows WHERE the robot is (x, y) and WHICH WAY it faces (heading), using the
 *  goBILDA Pinpoint. This starter finds the sensor; reading fresh data each
 *  loop and handing out the position is what you fill in during the lessons.
 * ============================================================================
 */
public class Localization implements Subsystem {

    private final GoBildaPinpointDriver pinpoint;   // null if not configured

    public Localization(HardwareMap hardwareMap) {
        GoBildaPinpointDriver found = null;
        try {
            found = hardwareMap.get(GoBildaPinpointDriver.class, RobotConfig.PINPOINT);
        } catch (IllegalArgumentException e) {
            found = null;   // not configured -- run without position tracking
        }
        pinpoint = found;
        // TODO (lesson): configure the Pinpoint from RobotConfig.configurePinpoint(pinpoint).
    }

    @Override
    public void update() {
        // TODO (lesson): pull fresh data from the Pinpoint and store it.
    }

    /** Robot heading in radians (0 = the direction it started). */
    public double getHeadingRadians() {
        return 0.0;   // TODO (lesson): return the real heading.
    }

    /** Robot heading in degrees. */
    public double getHeadingDegrees() {
        return 0.0;   // TODO (lesson)
    }

    /** Robot X position on the field, in inches. */
    public double getXInches() {
        return 0.0;   // TODO (lesson)
    }

    /** Robot Y position on the field, in inches. */
    public double getYInches() {
        return 0.0;   // TODO (lesson)
    }

    /** True if a real Pinpoint was found and configured. */
    public boolean hasPinpoint() {
        return pinpoint != null;
    }

    /** Re-zero "forward" to the current spot. */
    public void reset() {
        // TODO (lesson): reset the Pinpoint position and IMU.
    }
}
