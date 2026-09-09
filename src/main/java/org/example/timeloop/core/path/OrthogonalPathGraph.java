package org.example.timeloop.core.path;

import org.example.timeloop.core.Direction;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable, validated graph of cardinal path segments.
 *
 * <p>The graph validates topology at construction time so a patrol controller
 * never has to guess how to recover from a malformed map. Map lookup is by
 * declared identifier or direction only; no movement choice depends on map
 * iteration order.</p>
 */
public final class OrthogonalPathGraph {

    private static final double GEOMETRY_EPSILON = 1.0e-9;

    private final Map<String, PathNode> nodesById;
    private final double shortestSegmentLength;

    public OrthogonalPathGraph(List<PathNode> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("path graph must contain at least one node");
        }

        // This is an id index only. Route selection always uses named direct
        // lookups in PathExitSelector, never this collection's iteration order.
        Map<String, PathNode> indexedNodes = new TreeMap<>();
        for (PathNode node : nodes) {
            Objects.requireNonNull(node, "path node");
            if (indexedNodes.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("path graph contains duplicate node id '" + node.id() + "'");
            }
        }

        double shortest = Double.POSITIVE_INFINITY;
        for (PathNode node : nodes) {
            for (PathExit exit : node.exits()) {
                PathNode target = indexedNodes.get(exit.targetNodeId());
                if (target == null) {
                    throw new IllegalArgumentException(
                            "path node '" + node.id() + "' exit " + exit.direction()
                                    + " references missing node '" + exit.targetNodeId() + "'");
                }
                Direction geometricDirection = DirectionGeometry.directionFromTo(
                        node.center(), target.center(), GEOMETRY_EPSILON);
                if (geometricDirection != exit.direction()) {
                    throw new IllegalArgumentException(
                            "path node '" + node.id() + "' exit " + exit.direction()
                                    + " does not match target '" + target.id()
                                    + " at " + geometricDirection);
                }
                shortest = Math.min(shortest, node.center().distanceTo(target.center()));
            }
        }

        this.nodesById = Map.copyOf(indexedNodes);
        this.shortestSegmentLength = shortest;
    }

    /** Returns a node by stable id or fails with the graph context. */
    public PathNode node(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        PathNode node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("path graph has no node '" + nodeId + "'");
        }
        return node;
    }

    /** Looks up a declared neighbour in a named direction. */
    public Optional<PathNode> neighbor(PathNode from, Direction direction) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");
        if (!nodesById.containsKey(from.id())) {
            throw new IllegalArgumentException("node '" + from.id() + "' does not belong to this path graph");
        }
        return from.exitFor(direction).map(exit -> nodesById.get(exit.targetNodeId()));
    }

    /** Returns the length of a declared segment, rejecting nonexistent exits. */
    public double segmentLength(PathNode from, Direction direction) {
        PathNode target = neighbor(from, direction).orElseThrow(() -> new IllegalArgumentException(
                "path node '" + from.id() + "' has no " + direction + " exit"));
        return from.center().distanceTo(target.center());
    }

    /** Smallest positive declared segment, used to prove a finite catch-up bound. */
    public double shortestSegmentLength() {
        return shortestSegmentLength;
    }
}
