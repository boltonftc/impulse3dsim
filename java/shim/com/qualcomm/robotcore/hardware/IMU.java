// IMU -- the Control Hub's built-in Inertial Measurement Unit. Its gyroscope tracks how far the
// robot has rotated, so its main output is heading (yaw). This shim exposes the same small slice
// of the real FTC SDK API that the lessons use: initialize, read yaw, and reset yaw.
package com.qualcomm.robotcore.hardware;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

public interface IMU {

    class Parameters {
        public final RevHubOrientationOnRobot imuOrientationOnRobot;

        public Parameters(RevHubOrientationOnRobot orientationOnRobot) {
            this.imuOrientationOnRobot = orientationOnRobot;
        }
    }

    boolean initialize(Parameters parameters);

    void resetYaw();

    YawPitchRollAngles getRobotYawPitchRollAngles();
}
