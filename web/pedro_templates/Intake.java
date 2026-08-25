package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

// Intake + indexer subsystem: the green/black intake roller and the hopper's paddle-wheel
// feeder always run together (same power), forward to pick up game elements, reverse to
// meter them back out. Discrete command methods only -- no timed state machine needed here,
// so update() has nothing to do yet, but it still implements Subsystem so Robot's scheduler
// can treat every mechanism uniformly.
public class Intake implements Subsystem {

    private final DcMotor intakeMotor;
    private final CRServo feederServo;

    public Intake(HardwareMap hardwareMap) {
        intakeMotor = hardwareMap.get(DcMotor.class, "intake");
        feederServo = hardwareMap.get(CRServo.class, "feeder");
    }

    public void forward() { setPower(1.0); }    // pull game elements in
    public void reverse() { setPower(-1.0); }   // meter elements back out (eject)
    public void off()     { setPower(0.0); }

    private void setPower(double power) {
        intakeMotor.setPower(power);
        feederServo.setPower(power);
    }

    public boolean isRunning() { return Math.abs(intakeMotor.getPower()) > 0.01; }

    @Override
    public void update() {
        // No time-based behavior yet -- forward/reverse/off take effect immediately.
    }
}
