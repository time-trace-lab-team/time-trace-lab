package org.example.timeloop.level;

import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Set;

public interface LevelGeometry {

    double getTileSize();

    Vector2D getSpawnPosition();

    /** Immutable path-node values; callers cannot mutate the geometry through this view. */
    List<PathNode> getPathNodes();

    Set<PathNode.Dir> getValidExits(String nodeId);

    boolean isConnected(String nodeIdA, String nodeIdB);

    boolean isWall(Vector2D position);

    boolean isDoorClosed(String doorId);

    boolean hasNode(String nodeId);

    Set<String> getNeighbors(String nodeId);
}
