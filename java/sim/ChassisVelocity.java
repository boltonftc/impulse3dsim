package sim;

// Chassis velocity in the robot's local frame (plain-class port of the v1 record
// to avoid invokedynamic under CheerpJ). vx=right, vy=forward, omega=CCW+.
public final class ChassisVelocity {
    private final double vx;
    private final double vy;
    private final double omega;

    public ChassisVelocity(double vx, double vy, double omega) {
        this.vx = vx;
        this.vy = vy;
        this.omega = omega;
    }

    public double vx() { return vx; }
    public double vy() { return vy; }
    public double omega() { return omega; }

    public double linearSpeed() { return Math.sqrt(vx * vx + vy * vy); }
    public boolean isStationary() {
        return Math.abs(vx) < 1e-6 && Math.abs(vy) < 1e-6 && Math.abs(omega) < 1e-6;
    }
}
