package org.example.timeloop.level;

import org.example.timeloop.level.model.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Level01Footsteps {

    private static final double TILE_SIZE = 48.0;
    private static final long DURATION_TICKS = 16 * 60L;
    private static final int MAX_ROUNDS = 3;
    private static final int ECHO_LIFE_L = 1;

    public static LevelData build() {
        TileType[][] grid = createTileGrid();
        Vector2D spawnWorldPos = new Vector2D(TILE_SIZE * 5.0, TILE_SIZE * 1.0);
        List<PathNode> nodeList = buildPathNodes();
        List<EntitySpawnInfo> entityList = buildEntities();
        List<DoorInfo> doors = buildDoors();

        LevelData levelData = new LevelData(
                TILE_SIZE,
                grid,
                nodeList,
                entityList,
                doors,
                spawnWorldPos,
                DURATION_TICKS,
                MAX_ROUNDS,
                ECHO_LIFE_L
        );
        LevelDataValidator.validateFirstLevel(levelData);
        return levelData;
    }

    private static TileType[][] createTileGrid() {
        int width = 11;
        int height = 9;
        TileType[][] grid = new TileType[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = TileType.WALL;
            }
        }
        for (int x = 4; x <= 6; x++) grid[1][x] = TileType.FLOOR;
        for (int x = 4; x <= 6; x++) grid[2][x] = TileType.FLOOR;
        for (int x = 4; x <= 6; x++) grid[3][x] = TileType.FLOOR;
        for (int y = 3; y <= 5; y++) grid[y][2] = TileType.FLOOR;
        grid[5][2] = TileType.FLOOR;
        for (int y = 3; y <= 5; y++) grid[y][8] = TileType.FLOOR;
        grid[5][8] = TileType.FLOOR;
        grid[1][5] = TileType.SPAWN_POINT;
        return grid;
    }

    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();

        PathNode forkNode = new PathNode(
                "L01_node_fork",
                new Vector2D(5 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT)
        );
        nodes.add(forkNode);

        PathNode leftNode = new PathNode(
                "L01_node_left_end",
                new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)
        );
        nodes.add(leftNode);

        PathNode rightNode = new PathNode(
                "L01_node_right_end",
                new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)
        );
        nodes.add(rightNode);

        PathNode exitNode = new PathNode(
                "L01_node_exit_terminal",
                new Vector2D(9 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)
        );
        nodes.add(exitNode);

        return nodes;
    }

    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> list = new ArrayList<>();

        list.add(new EntitySpawnInfo(
                        "L01_plate_left",
                        "dock_plate",
                        new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE),
                        "L01_node_left_end")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                        "L01_plate_right",
                        "dock_plate",
                        new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE),
                        "L01_node_right_end")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                "L01_exit_00",
                "exit_terminal",
                new Vector2D(9 * TILE_SIZE, 5 * TILE_SIZE),
                "L01_node_exit_terminal"));

        return list;
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(new DoorInfo(
                "L01_door_01",
                new Vector2D(5 * TILE_SIZE, 5 * TILE_SIZE),
                false,
                Set.of("L01_plate_left", "L01_plate_right")));
        return doors;
    }
}
