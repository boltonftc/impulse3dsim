package sim;

// First-order goBILDA motor model (ported verbatim from impulse_3dsim v1).
//   new_speed = current + (power*max_speed - current) * (dt/tau)
// Default drive motor: 312 RPM = 32.67 rad/s, tau = 0.15s. MotorType preset dropped for the spike.
public class GoBildaMotorModel implements MotorModel {

    private final double maxSpeedRadSec;
    private final double tau;

    public GoBildaMotorModel(double maxSpeedRadSec, double tau) {
        if (maxSpeedRadSec <= 0) {
            throw new IllegalArgumentException("maxSpeedRadSec must be positive: " + maxSpeedRadSec);
        }
        if (tau <= 0) {
            throw new IllegalArgumentException("tau must be positive: " + tau);
        }
        this.maxSpeedRadSec = maxSpeedRadSec;
        this.tau = tau;
    }

    @Override
    public double update(double power, double currentSpeedRadSec, double deltaSec) {
        if (deltaSec == 0.0) {
            return currentSpeedRadSec;
        }
        power = Math.max(-1.0, Math.min(1.0, power));
        double targetSpeed = power * maxSpeedRadSec;
        double alpha = deltaSec / tau;
        return currentSpeedRadSec + (targetSpeed - currentSpeedRadSec) * alpha;
    }

    @Override
    public double getMaxSpeedRadSec() {
        return maxSpeedRadSec;
    }
}
