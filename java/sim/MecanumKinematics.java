package sim;

// Mecanum forward/inverse kinematics (ported verbatim from impulse_3dsim v1).
//   vx    = (r/4) * ( fl - fr - bl + br)        [strafe right]
//   vy    = (r/4) * ( fl + fr + bl + br)        [forward]
//   omega = (r/(4*(lx+ly))) * (-fl + fr - bl + br)   [CCW+]
// Defaults: goBILDA Strafer r=0.0508m (2in), lx+ly=0.3556m (14in).
public class MecanumKinematics {

    private final double wheelRadiusM;
    private final double lxPlusLy;

    public MecanumKinematics() {
        this(0.0508, 0.3556);
    }

    public MecanumKinematics(double wheelRadiusM, double lxPlusLy) {
        if (wheelRadiusM <= 0) {
            throw new IllegalArgumentException("wheelRadiusM must be positive: " + wheelRadiusM);
        }
        if (lxPlusLy <= 0) {
            throw new IllegalArgumentException("lxPlusLy must be positive: " + lxPlusLy);
        }
        this.wheelRadiusM = wheelRadiusM;
        this.lxPlusLy = lxPlusLy;
    }

    public ChassisVelocity forward(double flRadSec, double frRadSec,
                                    double blRadSec, double brRadSec) {
        double r4 = wheelRadiusM / 4.0;
        double vx    = r4 * (flRadSec - frRadSec - blRadSec + brRadSec);
        double vy    = r4 * (flRadSec + frRadSec + blRadSec + brRadSec);
        double omega = (wheelRadiusM / (4.0 * lxPlusLy))
                      * (-flRadSec + frRadSec - blRadSec + brRadSec);
        return new ChassisVelocity(vx, vy, omega);
    }

    public double[] inverse(ChassisVelocity velocity) {
        double invR = 1.0 / wheelRadiusM;
        double vx = velocity.vx();
        double vy = velocity.vy();
        double omega = velocity.omega();
        double fl = invR * ( vx + vy - lxPlusLy * omega);
        double fr = invR * (-vx + vy + lxPlusLy * omega);
        double bl = invR * (-vx + vy - lxPlusLy * omega);
        double br = invR * ( vx + vy + lxPlusLy * omega);
        return new double[]{fl, fr, bl, br};
    }
}
