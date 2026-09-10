package org.example.timeloop.mechanism;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DockingPlateRegistry {

    private static final DockingPlateRegistry INSTANCE = new DockingPlateRegistry();
    private final Map<String, DockingPlate> plates = new ConcurrentHashMap<>();

    private DockingPlateRegistry() {}

    public static DockingPlateRegistry getInstance() {
        return INSTANCE;
    }

    public void register(DockingPlate plate) {
        DockingPlate existing = plates.putIfAbsent(plate.getId(), plate);
        if (existing != null) {
            throw new IllegalArgumentException("重复的驻留板 ID: " + plate.getId());
        }
    }

    public void unregister(String id) {
        plates.remove(id);
    }

    public DockingPlate get(String id) {
        return plates.get(id);
    }

    public boolean isOccupied(String id) {
        DockingPlate plate = plates.get(id);
        return plate != null && plate.isOccupied();
    }

    public void resetAll() {
        for (DockingPlate plate : plates.values()) {
            plate.reset();
        }
    }

    public void clear() {
        plates.clear();
    }
}
