package com.qualcomm.robotcore.eventloop.opmode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simulator-only: the SDK's {@code OpMode.blackboard}, but one per ROBOT. On a real field each robot
 * has its own Control Hub and so its own blackboard; in the sim both robots share one JVM, so without
 * this a 2-player match would let one robot's auto hand its pose to the other robot's TeleOp.
 * Student code is unchanged ({@code blackboard.put/get/remove}); every call is routed to the map of
 * the robot whose OpMode thread is calling. The host sets the slot before it builds an OpMode, and
 * the OpMode's threads inherit it.
 */
public final class SimBlackboard extends HashMap<String, Object> {

    private static final InheritableThreadLocal<Integer> SLOT = new InheritableThreadLocal<Integer>() {
        @Override protected Integer initialValue() { return 0; }
    };
    private final HashMap<Integer, HashMap<String, Object>> perSlot = new HashMap<>();

    /** Host only: the calling thread (and threads it creates from now on) belong to this robot slot. */
    public static void setSlot(int slot) { SLOT.set(slot); }

    private synchronized HashMap<String, Object> mine() {
        return perSlot.computeIfAbsent(SLOT.get(), k -> new HashMap<>());
    }

    @Override public Object get(Object key) { return mine().get(key); }
    @Override public Object getOrDefault(Object key, Object def) { return mine().getOrDefault(key, def); }
    @Override public Object put(String key, Object value) { return mine().put(key, value); }
    @Override public Object putIfAbsent(String key, Object value) { return mine().putIfAbsent(key, value); }
    @Override public void putAll(Map<? extends String, ?> m) { mine().putAll(m); }
    @Override public Object remove(Object key) { return mine().remove(key); }
    @Override public boolean containsKey(Object key) { return mine().containsKey(key); }
    @Override public boolean containsValue(Object value) { return mine().containsValue(value); }
    @Override public int size() { return mine().size(); }
    @Override public boolean isEmpty() { return mine().isEmpty(); }
    @Override public void clear() { mine().clear(); }
    @Override public Set<String> keySet() { return mine().keySet(); }
    @Override public Collection<Object> values() { return mine().values(); }
    @Override public Set<Map.Entry<String, Object>> entrySet() { return mine().entrySet(); }
    @Override public String toString() { return mine().toString(); }
}
