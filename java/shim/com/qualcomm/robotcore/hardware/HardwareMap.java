package com.qualcomm.robotcore.hardware;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Slim v2 shim: case-insensitive device registry with the get<T>() students use.
public class HardwareMap {

    private final Map<String, Object> deviceMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public final DeviceMapping<DcMotor> dcMotor = new DeviceMapping<>(DcMotor.class);
    public final DeviceMapping<Servo> servo = new DeviceMapping<>(Servo.class);
    public final DeviceMapping<CRServo> crservo = new DeviceMapping<>(CRServo.class);

    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends T> classOrInterface, String deviceName) {
        Object device = deviceMap.get(deviceName);
        if (device == null) {
            throw new IllegalArgumentException(
                    "No " + classOrInterface.getSimpleName() + " named \"" + deviceName + "\" is found");
        }
        if (!classOrInterface.isInstance(device)) {
            throw new IllegalArgumentException(
                    "Device \"" + deviceName + "\" is not a " + classOrInterface.getSimpleName());
        }
        return (T) device;
    }

    public void put(String name, Object device) {
        deviceMap.put(name, device);
        if (device instanceof DcMotor) {
            dcMotor.put(name, (DcMotor) device);
        }
        if (device instanceof Servo) {
            servo.put(name, (Servo) device);
        }
        if (device instanceof CRServo) {
            crservo.put(name, (CRServo) device);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getAll(Class<? extends T> classOrInterface) {
        List<T> result = new ArrayList<>();
        for (Object device : deviceMap.values()) {
            if (classOrInterface.isInstance(device)) {
                result.add((T) device);
            }
        }
        return result;
    }

    public static class DeviceMapping<T> {
        private final Class<T> clazz;
        private final Map<String, T> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        public DeviceMapping(Class<T> clazz) {
            this.clazz = clazz;
        }

        public T get(String name) {
            T device = map.get(name);
            if (device == null) {
                throw new IllegalArgumentException(
                        "No " + clazz.getSimpleName() + " named \"" + name + "\" is found");
            }
            return device;
        }

        public void put(String name, T device) {
            map.put(name, device);
        }

        public List<T> getAll() {
            return new ArrayList<>(map.values());
        }
    }
}
