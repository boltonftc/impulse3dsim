package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

/*
 * ============================================================================
 *  Dumper  --  owned by Student C   (the STATE MACHINE example)
 * ============================================================================
 *
 *  The dumper is a servo that tilts a bucket up to dump game elements, then
 *  tilts back down. One button press should run the WHOLE sequence:
 *
 *        raise the bucket -> hold it up so elements slide out -> lower it
 *
 *  THE PROBLEM WITH sleep()
 *  The obvious way to write "raise, wait, lower" is:
 *        servo.setPosition(UP); sleep(400); servo.setPosition(DOWN);
 *  But sleep() FREEZES THE ENTIRE ROBOT for 400 ms -- the driver cannot
 *  steer, nothing else updates. That is unacceptable.
 *
 *  THE FIX: A STATE MACHINE + A STOPWATCH
 *  We break the sequence into named states (an enum) and remember which one
 *  we are in. A stopwatch (ElapsedTime) measures how long we have been in the
 *  current state. Each loop, update() checks the stopwatch and moves to the
 *  next state when enough time has passed -- then returns immediately. The
 *  robot never freezes; the driver keeps full control the whole time.
 * ============================================================================
 */
public class Dumper implements Subsystem {

    /*
     * The four situations the bucket can be in. A servo takes real time to
     * physically travel, so "raising" and "lowering" are their own states
     * (we wait for the servo to finish moving before doing the next thing).
     *
     * WHY IS THIS enum public?
     * The OpMode wants to SHOW the current state on the Driver Hub (see
     * getState() below). For that to work, code outside this class must be
     * allowed to name the type "Dumper.State", so the enum itself is public.
     * Note that being able to READ the state (public) is different from being
     * able to CHANGE it -- the actual `state` field stays private (see below).
     */
    public enum State {
        STOWED,    // resting, bucket level -- the normal state
        RAISING,   // servo commanded up; waiting for it to reach the top
        DUMPING,   // holding at the top so game elements slide out
        LOWERING   // servo commanded down; waiting for it to reach level
    }

    // ── TUNING CONSTANTS ("defines") ─────────────────────────────────────
    // `static final` = a named value that never changes (Java's "#define").
    // Tune the real robot by editing these labeled numbers -- nothing else.
    // SERVO POSITIONS are always between 0.0 and 1.0.
    //
    // These are `private` because they are an internal recipe for THIS
    // subsystem. No other class needs to know the exact servo numbers, and
    // hiding them means a student can retune the dumper without any risk of
    // breaking code elsewhere. "Keep the details private" is the whole idea
    // behind a subsystem: a simple public button (dump()) on the outside, and
    // the messy hardware specifics locked away on the inside.
    private static final double CARRY_POSITION = 0.1;    // bucket level (stowed/holding)
    private static final double DUMP_POSITION  = 0.8;    // bucket tilted (dumping)

    // TIMINGS in milliseconds. These say how long each moving/holding step
    // lasts. If the bucket does not finish moving in time on the real robot,
    // make the matching number bigger.
    private static final double RAISE_TIME_MS = 750;     // time for servo to travel up
    private static final double HOLD_TIME_MS  = 2000;    // time held up to let elements fall
    private static final double LOWER_TIME_MS = 750;     // time for servo to travel down

    // ── Hardware and state ───────────────────────────────────────────────
    // EVERYTHING IN THIS SECTION IS private ON PURPOSE.
    // The rule of thumb: anything that TOUCHES THE HARDWARE, or holds the
    // subsystem's internal bookkeeping, is private. That way the servo can
    // only ever be moved by code inside this file -- so the state machine is
    // the single source of truth for the bucket, and no stray line in an
    // OpMode can command the servo behind its back and confuse the sequence.
    private final Servo servo;               // null if no dump_servo is configured (private: hardware)
    private State state = State.STOWED;       // current situation; starts at rest (private: internal bookkeeping)

    /*
     * ElapsedTime is an FTC stopwatch. We reset() it to 0 at the moment a new
     * state begins, then read milliseconds() each loop to see how long we
     * have been in that state. This is how we "wait" without blocking.
     *
     * Private again: the timer is purely an internal tool for running the
     * sequence. Nothing outside this class should start, stop, or read it.
     */
    private final ElapsedTime timer = new ElapsedTime();

    /*
     * CONSTRUCTOR
     * Runs once when Robot builds this subsystem. It grabs the servo and
     * moves it to the level "carry" position during INIT, so the bucket is
     * already level the instant the servo powers on (no surprise jump).
     * The name "dump_servo" MUST match the Driver Hub configuration.
     *
     * The constructor is public because Robot (a different class) has to be
     * able to build the Dumper with `new Dumper(hardwareMap)`. If it were
     * private, no one outside this file could create a Dumper at all.
     */
    public Dumper(HardwareMap hardwareMap) {
        // Try to find the dump servo. If it is not in the configuration -- e.g.
        // a robot that only has drive motors and an intake so far -- we set it
        // to null and the whole robot still runs (the dumper simply does
        // nothing). This is the same guard Localization uses for the Pinpoint,
        // and it is what keeps one un-built mechanism from crashing the OpMode
        // at INIT and taking every other subsystem (like the intake) down with it.
        Servo foundServo = null;
        try {
            foundServo = hardwareMap.servo.get(RobotConfig.DUMP_SERVO);
        } catch (IllegalArgumentException e) {
            foundServo = null;   // "dump_servo" not configured -- run without it
        }
        servo = foundServo;
        if (servo != null) {
            // Reverse the servo (like reversing a drive motor) so a bigger position
            // number tilts the bucket UP. Reversing mirrors positions around 0.5, so
            // the tuning constants above are set to match: 0.1 stowed, 0.8 dumped.
            servo.setDirection(Servo.Direction.REVERSE);
            servo.setPosition(CARRY_POSITION);
        }
    }

    /*
     * COMMAND METHOD
     * Called once when the driver presses the dump button. It only STARTS the
     * sequence -- it commands the servo upward, resets the stopwatch, and
     * switches to the RAISING state. It does NOT wait. It returns instantly.
     *
     * The `if (state == STOWED)` guard means pressing the button again while a
     * dump is already in progress is ignored, so we never interrupt a cycle.
     *
     * This method is public: it is the safe "button" we offer to the outside
     * world. The main loop calls robot.dumper.dump() and that is ALL it can
     * do to the dumper -- it cannot reach the servo or the state directly.
     * A small, deliberate public surface like this is exactly what keeps the
     * subsystem hard to misuse.
     */
    public void dump() {
        if (servo == null) {
            return;   // no dump servo on this robot -- nothing to do
        }
        if (state == State.STOWED) {
            servo.setPosition(DUMP_POSITION);
            timer.reset();
            state = State.RAISING;
        }
    }

    /*
     * update()  --  ADVANCE THE SEQUENCE
     * Called every loop by the scheduler. This is where the non-blocking
     * "waiting" happens: we check the stopwatch and, when a step's time is up,
     * move to the next state (and command the servo if needed). Then we fall
     * straight through and return -- the robot stays responsive throughout.
     *
     * update() is public because the scheduler (Robot.update()) has to call it
     * once per loop -- that is required by the Subsystem interface. Even though
     * it is public, notice that it is the ONLY place the servo actually moves
     * during a sequence; the public dump() just picks the starting state.
     */
    @Override
    public void update() {
        switch (state) {
            case RAISING:
                // Wait for the servo to finish traveling up, then start holding.
                if (timer.milliseconds() > RAISE_TIME_MS) {
                    timer.reset();
                    state = State.DUMPING;
                }
                break;

            case DUMPING:
                // Held long enough for elements to fall out: command the servo
                // back down and start the lowering wait.
                if (timer.milliseconds() > HOLD_TIME_MS) {
                    if (servo != null) {
                        servo.setPosition(CARRY_POSITION);
                    }
                    timer.reset();
                    state = State.LOWERING;
                }
                break;

            case LOWERING:
                // Wait for the servo to reach level again, then we are done.
                if (timer.milliseconds() > LOWER_TIME_MS) {
                    state = State.STOWED;
                }
                break;

            case STOWED:
                // Nothing to do while resting.
                break;
        }
    }

    /*
     * isBusy() reports whether a dump cycle is in progress. Autonomous uses
     * this to wait for the bucket to finish before moving on -- again, by
     * checking each loop rather than by blocking.
     *
     * Public and READ-ONLY: it hands back a simple true/false ANSWER about the
     * dumper without letting the caller change anything. This is the good kind
     * of public method -- it exposes information, not control.
     */
    public boolean isBusy() {
        return state != State.STOWED;
    }

    /*
     * getState() lets the OpMode SHOW the dumper state on the Driver Hub.
     * Like isBusy(), it is a public "read-only window": callers can look at the
     * private `state` field, but they still cannot assign to it. Reading is
     * public; changing stays private inside dump() and update(). That split --
     * public to read, private to change -- is the heart of encapsulation.
     */
    public State getState() {
        return state;
    }
}
