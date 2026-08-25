package org.firstinspires.ftc.teamcode;

/*
 * ============================================================================
 *  MecanumKinematics  --  the ONE mecanum wheel-power formula
 * ============================================================================
 *
 *  A mecanum drivetrain turns three intents -- go forward, strafe sideways,
 *  and spin -- into four individual wheel powers. That formula is the same no
 *  matter WHO is asking:
 *    - the TeleOp Drivebase (driver sticks, open-loop), and
 *    - the autonomous MecanumDrivetrainSubsystem (Pedro's path follower).
 *
 *  Both used to carry their own copy of the math. Keeping it here means the
 *  kinematics can never drift apart between TeleOp and autonomous.
 *
 *  This class is never instantiated -- it is one static helper.
 * ============================================================================
 */
public final class MecanumKinematics {

    private MecanumKinematics() { }

    /*
     * mix()  --  combine a drive request into four mecanum wheel powers.
     * Inputs are in the ROBOT frame:
     *   forward     = +forward / -reverse
     *   strafeRight = +right   / -left
     *   turn        = +clockwise / -counter-clockwise
     * Returns the four powers in the order [FL, FR, BL, BR]. If any wheel would
     * exceed 1.0, all four are divided by the largest magnitude so the motion
     * direction is preserved and only the overall speed is capped.
     */
    public static double[] mix(double forward, double strafeRight, double turn) {
        double fl = forward + strafeRight + turn;
        double fr = forward - strafeRight - turn;
        double bl = forward - strafeRight + turn;
        double br = forward + strafeRight - turn;

        double largest = Math.max(Math.max(Math.abs(fl), Math.abs(fr)),
                                  Math.max(Math.abs(bl), Math.abs(br)));
        if (largest > 1.0) {
            fl /= largest;
            fr /= largest;
            bl /= largest;
            br /= largest;
        }
        return new double[] { fl, fr, bl, br };
    }
}
