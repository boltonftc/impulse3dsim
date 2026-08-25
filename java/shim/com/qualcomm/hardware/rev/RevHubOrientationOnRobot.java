// RevHubOrientationOnRobot -- tells the IMU how the Control Hub is physically mounted, so it
// can map the chip's raw axes onto the robot. In the sim the robot is always mounted logo-up /
// USB-forward, so the values are recorded for API fidelity but not otherwise used.
package com.qualcomm.hardware.rev;

public class RevHubOrientationOnRobot {

    public enum LogoFacingDirection { UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD }
    public enum UsbFacingDirection  { UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD }

    public final LogoFacingDirection logoFacingDirection;
    public final UsbFacingDirection  usbFacingDirection;

    public RevHubOrientationOnRobot(LogoFacingDirection logo, UsbFacingDirection usb) {
        this.logoFacingDirection = logo;
        this.usbFacingDirection  = usb;
    }
}
