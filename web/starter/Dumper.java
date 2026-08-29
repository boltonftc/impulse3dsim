package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

/*
 * ============================================================================
 *  Dumper  --  STARTER STUB (owned by Student C)  -- the STATE MACHINE example
 * ============================================================================
 *  A servo that raises a bucket, holds, then lowers -- all from one dump()
 *  press, without ever freezing the robot. The state enum and the public
 *  surface are here; the timed raise/hold/lower logic is what you fill in.
 * ============================================================================
 */
public class Dumper implements Subsystem {

    public enum State {
        STOWED,    // resting -- the normal state
        RAISING,   // servo commanded up; waiting for it to reach the top
        DUMPING,   // holding at the top so game elements slide out
        LOWERING   // servo commanded down; waiting for it to reach level
    }

    private final Servo servo;
    private State state = State.STOWED;
    private final ElapsedTime timer = new ElapsedTime();

    public Dumper(HardwareMap hardwareMap) {
        // Tolerate a robot that has no dump servo yet (e.g. while you are still
        // on the intake lesson): if "dump_servo" is not in the configuration,
        // leave servo null so the Robot constructor does NOT crash at INIT and
        // take the rest of the robot -- including the intake -- down with it.
        Servo foundServo = null;
        try {
            foundServo = hardwareMap.servo.get(RobotConfig.DUMP_SERVO);
        } catch (IllegalArgumentException e) {
            foundServo = null;   // "dump_servo" not configured -- run without it
        }
        servo = foundServo;
    }

    /** Start one full dump cycle. TODO: command the servo up and begin the sequence. */
    public void dump() {
        // TODO (lesson): if STOWED, raise the servo, reset the timer, go to RAISING.
    }

    @Override
    public void update() {
        // TODO (lesson): advance RAISING -> DUMPING -> LOWERING -> STOWED using the timer.
    }

    /** True while a dump cycle is in progress (used by autonomous to wait). */
    public boolean isBusy() {
        return state != State.STOWED;
    }

    /** Lets the OpMode show the dumper state on the Driver Hub. */
    public State getState() {
        return state;
    }
}
