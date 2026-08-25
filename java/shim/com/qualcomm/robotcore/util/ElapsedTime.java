package com.qualcomm.robotcore.util;

// Minimal FTC SDK ElapsedTime: a stopwatch. reset() marks "now" as zero; seconds()/milliseconds()
// report how long ago that was. Used by state-machine subsystems (e.g. Dumper) to time a step
// without blocking the loop.
public class ElapsedTime {

    public enum Resolution { SECONDS, MILLISECONDS }

    private double startTime;

    public ElapsedTime() {
        reset();
    }

    public ElapsedTime(double startTimeSeconds) {
        this.startTime = startTimeSeconds;
    }

    public void reset() {
        startTime = System.nanoTime() * 1e-9;
    }

    public double startTime() {
        return startTime;
    }

    public double seconds() {
        return System.nanoTime() * 1e-9 - startTime;
    }

    public double milliseconds() {
        return seconds() * 1000.0;
    }

    public double time() {
        return seconds();
    }

    public double time(Resolution resolution) {
        return resolution == Resolution.SECONDS ? seconds() : milliseconds();
    }

    @Override
    public String toString() {
        return String.format("%.3f s", seconds());
    }
}
