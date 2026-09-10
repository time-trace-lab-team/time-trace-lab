package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.StableIdValidator;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * autoDock 的纯动态状态快照。
 *
 * <p>几何定义属于关卡数据，不重复写入快照；每个条目只保存稳定机制 ID、占用和
 * 同 tick 防重入边沿。所有值和集合均不可变。</p>
 */
public record AutoDockStateSnapshot(List<DockSnapshot> docks) {

    public AutoDockStateSnapshot {
        Objects.requireNonNull(docks, "autoDock.snapshot.docks");
        List<DockSnapshot> copy = List.copyOf(docks);
        Set<String> ids = new HashSet<>();
        for (DockSnapshot dock : copy) {
            if (!ids.add(dock.mechanismId())) {
                throw new IllegalArgumentException(
                        "autoDock 快照包含重复机制 ID: " + dock.mechanismId());
            }
        }
        docks = copy;
    }

    /** 单个 autoDock 的不可变动态状态。 */
    public record DockSnapshot(String mechanismId,
                               DockOccupancyView occupancy,
                               long reentryBlockedAtTick) {

        public DockSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "plate", "autoDock.snapshot.mechanismId");
            Objects.requireNonNull(occupancy, "autoDock.snapshot.occupancy");
            if (reentryBlockedAtTick < -1) {
                throw new IllegalArgumentException(
                        "autoDock 快照的 reentryBlockedAtTick 无效");
            }
        }
    }
}
