package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.Objects;

/** A directed exit from one path node to an adjacent node. */
public record PathExit(Direction direction, String targetNodeId) {

    public PathExit {
        Objects.requireNonNull(direction, "direction");
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("targetNodeId must not be blank");
        }
    }
}
