package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.ArrayList;
import java.util.List;

// The container that owns every subsystem. TeleOp and Autonomous both build a Robot, so a
// mechanism written once (e.g. Intake) works everywhere. Robot.update() is the scheduler:
// call it once per loop and every subsystem gets a turn to advance its own state.
public class Robot {

    public final Intake intake;

    private final List<Subsystem> subsystems = new ArrayList<>();

    public Robot(HardwareMap hardwareMap) {
        intake = new Intake(hardwareMap);
        subsystems.add(intake);
    }

    public void update() {
        for (Subsystem subsystem : subsystems) {
            subsystem.update();
        }
    }
}
