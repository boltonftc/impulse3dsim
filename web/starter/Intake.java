package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

/*
 * ============================================================================
 *  Intake  --  STARTER STUB (owned by Student B)
 * ============================================================================
 *  One motor that pulls game elements in. Written as a small state machine:
 *  the command methods choose a State, and update() (which you fill in) makes
 *  the motor match that State.
 * ============================================================================
 */
public class Intake implements Subsystem {

    public enum State {
        FORWARD,   // pulling game elements in
        OFF,       // stopped
        REVERSE    // spitting back out to clear a jam
    }

    private final DcMotor motor;
    private State state = State.OFF;

    public Intake(HardwareMap hardwareMap) {
        motor = hardwareMap.dcMotor.get(RobotConfig.INTAKE_MOTOR);
        motor.setPower(0.0);
    }

    /** Run the intake inward. */
    public void forward() {
        state = State.FORWARD;
    }

    /** Run the intake outward (to clear a jam). */
    public void reverse() {
        state = State.REVERSE;
    }

    /** Stop the intake. */
    public void off() {
        state = State.OFF;
    }

    @Override
    public void update() {
        // TODO (lesson): set motor power to match the current state.
    }

    /** Lets the OpMode show the intake state on the Driver Hub. */
    public State getState() {
        return state;
    }
}
