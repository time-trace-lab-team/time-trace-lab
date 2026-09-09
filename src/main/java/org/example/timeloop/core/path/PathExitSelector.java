package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.Objects;

/**
 * Pure implementation of C2's frozen node-exit priority.
 *
 * <p>Every candidate is looked up by its explicit direction and checked with
 * the supplied query. No map or set iteration selects the route.</p>
 */
public final class PathExitSelector {

    private PathExitSelector() {
    }

    /**
     * Selects an exit in this exact order: valid queued direction, straight,
     * one available side direction, passable default exit, then a true-dead-end
     * reversal. An invalid queued direction is not selected and the result
     * marks it as unused so the controller can discard it.
     */
    public static PathExitDecision select(
            OrthogonalPathGraph graph,
            PathNode node,
            Direction arrivalDirection,
            Direction queuedDirection,
            ExitPassability passability) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(arrivalDirection, "arrivalDirection");
        Objects.requireNonNull(passability, "passability");

        if (queuedDirection != null
                && DirectionGeometry.isStraightOrRightAngle(arrivalDirection, queuedDirection)
                && isUsable(graph, node, queuedDirection, passability)) {
            return new PathExitDecision(queuedDirection, true);
        }

        if (isUsable(graph, node, arrivalDirection, passability)) {
            return new PathExitDecision(arrivalDirection, false);
        }

        Direction onlySideDirection = null;
        int usableSideCount = 0;
        for (Direction sideDirection : DirectionGeometry.sideDirections(arrivalDirection)) {
            if (isUsable(graph, node, sideDirection, passability)) {
                usableSideCount++;
                onlySideDirection = sideDirection;
            }
        }
        if (usableSideCount == 1) {
            return new PathExitDecision(onlySideDirection, false);
        }

        if (node.defaultExit().isPresent()) {
            Direction defaultDirection = node.defaultExit().get();
            if (isUsable(graph, node, defaultDirection, passability)) {
                return new PathExitDecision(defaultDirection, false);
            }
        }

        // A reversal is reserved for a genuine dead end.  If straight ahead
        // is unavailable but multiple side exits remain, the map must provide
        // a passable default arrow; silently reversing here would turn a
        // normal junction into an implicit 180-degree turn.
        if (usableSideCount > 1) {
            throw new IllegalStateException(
                    "ambiguous path node '" + node.id() + "' after arriving from " + arrivalDirection
                            + ": " + usableSideCount
                            + " passable side exits require a passable defaultExit");
        }

        Direction reverse = DirectionGeometry.opposite(arrivalDirection);
        if (isUsable(graph, node, reverse, passability)) {
            return new PathExitDecision(reverse, false);
        }

        throw new IllegalStateException(
                "no passable exit at path node '" + node.id() + "' after arriving from " + arrivalDirection);
    }

    private static boolean isUsable(
            OrthogonalPathGraph graph,
            PathNode node,
            Direction direction,
            ExitPassability passability) {
        return node.exitFor(direction)
                .map(exit -> passability.isPassable(node, exit, graph.node(exit.targetNodeId())))
                .orElse(false);
    }
}
