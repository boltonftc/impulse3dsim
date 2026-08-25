package org.firstinspires.ftc.teamcode;

/*
 * ============================================================================
 *  Subsystem  --  the promise every mechanism makes
 * ============================================================================
 *
 *  WHAT IS AN INTERFACE?
 *  An interface is a list of method names with NO code inside them. It is a
 *  "contract": any class that says `implements Subsystem` is PROMISING to
 *  provide its own version of every method listed here. The compiler will
 *  refuse to build a Subsystem that forgot to write an update() method.
 *
 *  WHY DO WE WANT THIS?
 *  Our robot has several mechanisms (drive, intake, dumper, localization).
 *  Because they ALL implement Subsystem, we can treat them the same way:
 *  we can put them in one list and call update() on each of them in a loop.
 *  That loop IS our "scheduler" (see Robot.java and MecanumDrive.java).
 *
 *  This is a tiny, on-purpose-simple version of the same idea used by the
 *  professional FTC/WPILib libraries, where every subsystem has a
 *  periodic() method the framework calls for you every loop.
 * ============================================================================
 */
public interface Subsystem {

    /*
     * update() is called ONCE PER LOOP (about every 20 milliseconds) by the
     * scheduler. Each subsystem uses it to do ONE SMALL SLICE of work and
     * then return immediately -- for example, check a timer and decide
     * whether to move to the next state.
     *
     * THE ONE RULE: update() must never "block" (never call sleep(), never
     * sit in a loop waiting). If it blocks, the whole robot freezes. We wait
     * for things by checking a stopwatch each loop, not by pausing.
     */
    void update();
}
