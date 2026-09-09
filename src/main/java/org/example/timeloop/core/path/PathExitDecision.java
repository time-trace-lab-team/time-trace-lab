package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.Objects;

/** Result of applying C2's deterministic five-level node-exit policy. */
public record PathExitDecision(Direction direction, boolean usedQueuedDirection) {

    public PathExitDecision {
        Objects.requireNonNull(direction, "direction");
    }
}
