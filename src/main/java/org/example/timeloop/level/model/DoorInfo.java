package org.example.timeloop.level.model;

import org.example.timeloop.level.StableIdValidator;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class DoorInfo {

    private final String id;
    private final Vector2D position;
    private final boolean initiallyOpen;
    private final Set<String> requiredPlateIds;

    /**
     * 兼容尚未迁移的旧关卡数据。第一关使用下面带引用集合的严格构造器。
     */
    public DoorInfo(String id, Vector2D position, boolean initiallyOpen) {
        this.id = id;
        this.position = position;
        this.initiallyOpen = initiallyOpen;
        this.requiredPlateIds = Set.of();
    }

    public DoorInfo(String id,
                    Vector2D position,
                    boolean initiallyOpen,
                    Set<String> requiredPlateIds) {
        this.id = StableIdValidator.requireMechanismId(id, "door", "door.id");
        this.position = position;
        this.initiallyOpen = initiallyOpen;
        if (requiredPlateIds == null) {
            throw new IllegalArgumentException("door.requiredPlateIds 不能为 null");
        }
        TreeSet<String> sortedIds = new TreeSet<>();
        for (String plateId : requiredPlateIds) {
            sortedIds.add(StableIdValidator.requireMechanismId(
                    plateId, "plate", "door.requiredPlateIds"));
        }
        this.requiredPlateIds = Collections.unmodifiableSet(sortedIds);
    }

    public String getId() {
        return id;
    }

    public Vector2D getPosition() {
        return position;
    }

    public boolean isInitiallyOpen() {
        return initiallyOpen;
    }

    public Set<String> getRequiredPlateIds() {
        return requiredPlateIds;
    }
}
