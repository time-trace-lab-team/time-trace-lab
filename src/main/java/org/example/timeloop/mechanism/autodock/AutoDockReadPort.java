package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Optional;

public interface AutoDockReadPort {

    Optional<AutoDockView> findById(String mechanismId);

    Optional<AutoDockView> findNearest(Vector2D worldPosition, double maxDistance);

    /** 按 stable mechanism ID 排序的不可修改快照。 */
    List<AutoDockView> snapshot();
}
