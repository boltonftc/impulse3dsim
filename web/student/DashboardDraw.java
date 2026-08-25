package org.firstinspires.ftc.teamcode;

import com.acmerobotics.dashboard.canvas.Canvas;

/*
 * ============================================================================
 *  DashboardDraw  --  shared FTC Dashboard field-overlay drawing
 * ============================================================================
 *
 *  TeleOp (MecanumDrive) and autonomous (SimpleAuto) both draw the robot the
 *  same way on the dashboard's field view, so that drawing lives here once
 *  instead of being copied into each OpMode.
 *
 *  The field view uses INCHES with the origin at the field center, so a
 *  Pinpoint x/y goes straight in. All of this is a no-op in the simulator (the
 *  dashboard is a stub there) and only renders on a real Control Hub.
 *
 *  This class is never instantiated -- it is one static helper.
 * ============================================================================
 */
public final class DashboardDraw {

    private DashboardDraw() { }

    // Chassis radius (inches) used to size the robot marker.
    private static final double ROBOT_RADIUS_IN = 9.0;

    /*
     * robot()  --  draw a circle for the chassis plus a short line pointing the
     * way the robot is facing (its heading, in radians).
     */
    public static void robot(Canvas field, double xInches, double yInches, double headingRadians) {
        field.setStroke("#3F51B5");
        field.strokeCircle(xInches, yInches, ROBOT_RADIUS_IN);
        double noseX = xInches + ROBOT_RADIUS_IN * Math.cos(headingRadians);
        double noseY = yInches + ROBOT_RADIUS_IN * Math.sin(headingRadians);
        field.strokeLine(xInches, yInches, noseX, noseY);
    }
}
