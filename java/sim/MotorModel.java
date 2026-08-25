package sim;

public interface MotorModel {
    double update(double power, double currentSpeedRadSec, double deltaSec);
    double getMaxSpeedRadSec();
}
