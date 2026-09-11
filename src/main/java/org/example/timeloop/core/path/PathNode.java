package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable junction or endpoint in an orthogonal path graph.
 *
 * <p>The exit list deliberately retains author-declared data rather than
 * accepting a map: duplicate directions can therefore be rejected at the
 * validation boundary instead of being silently overwritten.</p>
 */
public record PathNode(
        String id,
        PathPoint center,
        List<PathExit> exits
) {

    public PathNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("path node id must not be blank");
        }
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(exits, "exits");
        exits = List.copyOf(exits);
        if (exits.isEmpty()) {
            throw new IllegalArgumentException("path node '" + id + "' must declare at least one exit");
        }

        for (int index = 0; index < exits.size(); index++) {
            PathExit exit = Objects.requireNonNull(exits.get(index), "exit at index " + index);
            for (int previous = 0; previous < index; previous++) {
                if (exits.get(previous).direction() == exit.direction()) {
                    throw new IllegalArgumentException(
                            "path node '" + id + "' declares duplicate direction " + exit.direction());
                }
            }
        }
    }

    /** Looks up a declared exit without exposing mutable graph state. */
    public Optional<PathExit> exitFor(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return exitFor(exits, direction);
    }

    private static Optional<PathExit> exitFor(List<PathExit> exits, Direction direction) {
        for (PathExit exit : exits) {
            if (exit.direction() == direction) {
                return Optional.of(exit);
            }
        }
        return Optional.empty();
    }
}
