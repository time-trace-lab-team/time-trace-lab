package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

import java.util.Objects;
import java.util.Set;

/**
 * autoDock 的只读投影。不会暴露 DockingPlate、注册表或 UI 对象。
 */
public record AutoDockView(String mechanismId,
                           String pathNodeId,
                           DockRegionView region,
                           Vector2D center,
                           Set<PathNode.Dir> legalExitDirections,
                           DockOccupancyView occupancy,
                           long reentryBlockedAtTick) {

    public AutoDockView {
        if (mechanismId == null || mechanismId.isBlank()) {
            throw new IllegalArgumentException("autoDock view 缺少 mechanismId");
        }
        if (pathNodeId == null || pathNodeId.isBlank()) {
            throw new IllegalArgumentException("autoDock view 缺少 pathNodeId");
        }
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(center, "center");
        legalExitDirections = Set.copyOf(Objects.requireNonNull(legalExitDirections, "legalExitDirections"));
        Objects.requireNonNull(occupancy, "occupancy");
        if (reentryBlockedAtTick < -1) {
            throw new IllegalArgumentException("reentryBlockedAtTick 无效");
        }
    }
}
