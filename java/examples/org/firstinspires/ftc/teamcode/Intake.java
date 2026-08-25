package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

/*
 * ============================================================================
 *  Intake  --  owned by Student B
 * ============================================================================
 *
 *  The intake is one motor that pulls game elements into the robot. It can be
 *  running FORWARD, turned OFF, or running in REVERSE (to clear a jam).
 *
 *  This class is written as a small STATE MACHINE:
 *    - an `enum` lists the three situations the intake can be in
 *    - button presses change which situation we are in (the "state")
 *    - update() looks at the state each loop and sets the motor power to match
 *
 *  The intake has no sensors and no timing, so its state machine is simple.
 *  We still write it this way so it matches the shape of every other
 *  subsystem -- once you understand one, you understand them all.
 * ============================================================================
 */
public class Intake implements Subsystem {

    /*
     * WHAT IS AN ENUM?
     * An enum ("enumeration") is a fixed, named set of values. Here it lists
     * the only three situations the intake can be in. Using an enum instead
     * of numbers like 0, 1, 2 means:
     *    - the names read like English (State.FORWARD, not 0)
     *    - the compiler stops us from using an invalid state by accident
     */
    public enum State {
        FORWARD,   // pulling game elements in
        OFF,       // stopped
        REVERSE    // spitting back out to clear a jam
    }

    // ── TUNING CONSTANTS ("defines") ─────────────────────────────────────
    // These `static final` values are our version of a "#define". Java has no
    // #define, so we use `static final` (a name that never changes) instead.
    // Putting the numbers up here -- with names -- means:
    //    - you tune the robot by editing ONE labeled line, not hunting through code
    //    - the same value cannot drift out of sync in two places
    //    - the number's MEANING is obvious from its name
    // `static` = one shared copy for the whole class. `final` = cannot change.
    private static final double INTAKE_FORWARD_POWER =  1.0;   // full speed pulling in
    private static final double INTAKE_REVERSE_POWER = -0.5;   // gentler push-out to un-jam

    // ── Hardware and state ───────────────────────────────────────────────
    private final DcMotor motor;         // the physical intake motor
    private State state = State.OFF;     // current situation; starts stopped

    /*
     * CONSTRUCTOR
     * Runs once when Robot builds this subsystem. It looks up the intake motor
     * from the hardware map by its configured name and leaves it stopped.
     * The name "intake_motor" MUST match the Driver Hub configuration.
     */
    public Intake(HardwareMap hardwareMap) {
        motor = hardwareMap.dcMotor.get(RobotConfig.INTAKE_MOTOR);
        motor.setPower(0.0);
    }

    // ── COMMAND METHODS ──────────────────────────────────────────────────
    // These are the intake's whole public vocabulary: three DISCRETE, plain
    // commands. "Discrete" means each one names an exact end state (forward,
    // off, reverse) -- none of them is a "toggle." "Idempotent" means calling
    // the same one twice does no harm: forward() then forward() just stays
    // forward. They only change the STATE; update() does the motor work.
    //
    // WHY DISCRETE INSTEAD OF A toggle() METHOD?
    // Because THE INPUT LAYER DECIDES THE GESTURE. The subsystem should only
    // know "how to be forward / off / reverse," not "what a button press
    // means." Whether tapping button A should toggle, or hold-to-run, or do
    // something different in autonomous, is a decision for the OpMode that
    // reads the controller -- not for the intake. Keeping the gesture out of
    // here means:
    //    - Autonomous can command an EXACT state (intake.forward()) without
    //      guessing "what will a toggle do from whatever state we're in?"
    //    - TeleOp can build ANY feel it wants (see MecanumDrive, where A
    //      toggles off/forward) on top of these same simple commands.

    /** Run the intake inward (collecting game elements). */
    public void forward() {
        state = State.FORWARD;
    }

    /** Run the intake outward (spitting back out to clear a jam). */
    public void reverse() {
        state = State.REVERSE;
    }

    /** Stop the intake. */
    public void off() {
        state = State.OFF;
    }

    /*
     * update()  --  APPLY THE STATE
     * Called every loop by the scheduler. It reads the current state and sets
     * the motor power to match. Because this runs every loop, the motor always
     * reflects the latest state, and there is never a blocking wait.
     */
    @Override
    public void update() {
        switch (state) {
            case FORWARD:
                motor.setPower(INTAKE_FORWARD_POWER);
                break;
            case REVERSE:
                motor.setPower(INTAKE_REVERSE_POWER);
                break;
            case OFF:
                motor.setPower(0.0);
                break;
        }
    }

    /** Lets the OpMode show the intake state on the Driver Hub. */
    public State getState() {
        return state;
    }
}
