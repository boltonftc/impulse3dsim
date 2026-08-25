package org.firstinspires.ftc.teamcode;

// Every mechanism promises it can "update" itself once per loop. Robot's scheduler
// (Robot.update()) calls update() on every registered subsystem, once per cycle, so all
// mechanisms make progress "at the same time" on a single thread -- no multithreading needed.
// Rule: update() must NEVER block (no sleep()); it reads a timer/sensor and nudges state
// forward by one small slice, then returns immediately.
public interface Subsystem {
    void update();
}
