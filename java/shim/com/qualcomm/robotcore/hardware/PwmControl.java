package com.qualcomm.robotcore.hardware;

// Servo pulse-width control (FTC SDK API): widen the signal range to reach a servo's full travel.
public interface PwmControl {

    class PwmRange {
        public static final double usFrameDefault = 20000;
        public static final double usPulseLowerDefault = 600;
        public static final double usPulseUpperDefault = 2400;
        public static final PwmRange defaultRange = new PwmRange(usPulseLowerDefault, usPulseUpperDefault);

        public final double usPulseLower;
        public final double usPulseUpper;
        public final double usFrame;

        public PwmRange(double usPulseLower, double usPulseUpper) {
            this(usPulseLower, usPulseUpper, usFrameDefault);
        }

        public PwmRange(double usPulseLower, double usPulseUpper, double usFrame) {
            this.usPulseLower = usPulseLower;
            this.usPulseUpper = usPulseUpper;
            this.usFrame = usFrame;
        }
    }

    void setPwmRange(PwmRange range);
    PwmRange getPwmRange();
    void setPwmEnable();
    void setPwmDisable();
    boolean isPwmEnabled();
}
