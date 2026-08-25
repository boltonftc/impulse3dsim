package com.qualcomm.robotcore.eventloop.opmode;

/**
 * Primary student-facing OpMode. Students override runOpMode().
 * The runner injects hardware/gamepad/telemetry, then drives the lifecycle.
 */
public abstract class LinearOpMode extends OpMode {

    private volatile boolean isStarted = false;
    private volatile boolean stopRequested = false;
    private final Object startLock = new Object();
    private volatile long lastYieldNanos = System.nanoTime();
    private volatile Thread opModeThread;
    private volatile Throwable uncaughtException;

    /** Student overrides this. Entry point for all OpMode logic. */
    public abstract void runOpMode() throws InterruptedException;

    public void waitForStart() {
        synchronized (startLock) {
            while (!isStarted && !stopRequested) {
                try {
                    startLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        lastYieldNanos = System.nanoTime();
    }

    public boolean opModeIsActive() {
        boolean active = isStarted && !stopRequested && !Thread.currentThread().isInterrupted();
        if (active) {
            idle();
        }
        return active;
    }

    public boolean opModeInInit() {
        return !isStarted && !stopRequested;
    }

    public boolean isStopRequested() {
        return stopRequested || Thread.currentThread().isInterrupted();
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void idle() {
        lastYieldNanos = System.nanoTime();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void sleep(long milliseconds) {
        lastYieldNanos = System.nanoTime();
        try {
            long end = System.currentTimeMillis() + milliseconds;
            while (System.currentTimeMillis() < end && !stopRequested) {
                long remaining = end - System.currentTimeMillis();
                if (remaining > 0) {
                    Thread.sleep(Math.min(remaining, 20));
                    lastYieldNanos = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void init() { }

    @Override
    public void loop() { }

    /** Launch the dedicated OpMode thread that runs runOpMode(). */
    public void internalStart() {
        opModeThread = new Thread(() -> {
            try {
                runOpMode();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "LinearOpMode-Worker");
        opModeThread.setDaemon(true);
        opModeThread.setUncaughtExceptionHandler((t, e) -> uncaughtException = e);
        lastYieldNanos = System.nanoTime();
        opModeThread.start();
    }

    public Throwable getUncaughtException() {
        return uncaughtException;
    }

    public void internalNotifyStart() {
        lastYieldNanos = System.nanoTime();
        isStarted = true;
        synchronized (startLock) {
            startLock.notifyAll();
        }
    }

    public void internalRequestStop() {
        stopRequested = true;
        isStarted = true;
        synchronized (startLock) {
            startLock.notifyAll();
        }
    }

    public long getLastYieldNanos() {
        return lastYieldNanos;
    }

    public Thread getOpModeThread() {
        return opModeThread;
    }
}
