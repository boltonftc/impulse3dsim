package sim;

// Reads the four shimmed motors, applies per-motor first-order lag, and runs
// mecanum forward kinematics to produce a chassis velocity for the physics world.
// This is the sim-side impl behind the DcMotor shim; students never see it.
public class SimDrivetrain {

    private static final double MAX_RAD_S = 312.0 * 2.0 * Math.PI / 60.0; // goBILDA 312 RPM
    private static final double TAU = 0.15;

    private final SimMotor fl, fr, bl, br;
    private final MotorModel mFl, mFr, mBl, mBr;
    private final MecanumKinematics kin = new MecanumKinematics();
    private double sFl, sFr, sBl, sBr; // wheel angular velocity, rad/s

    public SimDrivetrain(SimMotor fl, SimMotor fr, SimMotor bl, SimMotor br) {
        this.fl = fl; this.fr = fr; this.bl = bl; this.br = br;
        this.mFl = new GoBildaMotorModel(MAX_RAD_S, TAU);
        this.mFr = new GoBildaMotorModel(MAX_RAD_S, TAU);
        this.mBl = new GoBildaMotorModel(MAX_RAD_S, TAU);
        this.mBr = new GoBildaMotorModel(MAX_RAD_S, TAU);
    }

    // Advance the motor models by dt and return {vx, vy, omega} in the robot frame.
    public ChassisVelocity sample(double dt) {
        // The left wheels are mounted as a mirror image of the right, so real team
        // code reverses the left motors (setDirection REVERSE) to make setPower(+)
        // roll them forward. The sim has no mounting model, so we mirror the left
        // side here: negating their effective power reproduces that geometry, so
        // the standard "reverse the left motors" convention drives correctly.
        sFl = mFl.update(-fl.getEffectivePower(), sFl, dt);
        sFr = mFr.update( fr.getEffectivePower(), sFr, dt);
        sBl = mBl.update(-bl.getEffectivePower(), sBl, dt);
        sBr = mBr.update( br.getEffectivePower(), sBr, dt);
        return kin.forward(sFl, sFr, sBl, sBr);
    }
}
