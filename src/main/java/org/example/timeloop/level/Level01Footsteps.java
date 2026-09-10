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

        int[][] floorCells = {
                {5, 1}, {5, 2}, {5, 3}, {5, 4}, {5, 5},
                {4, 3}, {3, 3}, {2, 3}, {2, 4}, {2, 5},
                {6, 3}, {7, 3}, {8, 3}, {8, 4}, {8, 5}, {9, 4}, {9, 5}
        };
        for (int[] cell : floorCells) {
            grid[cell[1]][cell[0]] = TileType.FLOOR;
        }
        grid[1][5] = TileType.SPAWN_POINT;
        return grid;
    }

    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();

        nodes.add(new PathNode(
                "L01_node_spawn",
                new Vector2D(5 * TILE_SIZE, 1 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_corridor_01",
                new Vector2D(5 * TILE_SIZE, 2 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));

        nodes.add(new PathNode(
                "L01_node_fork",
                new Vector2D(5 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT),
                PathNode.Dir.UP));

        nodes.add(new PathNode(
                "L01_node_center_01",
                new Vector2D(5 * TILE_SIZE, 4 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_door",
                new Vector2D(5 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)));

        nodes.add(new PathNode(
                "L01_node_left_01",
                new Vector2D(4 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_left_02",
                new Vector2D(3 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_left_turn",
                new Vector2D(2 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.RIGHT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_left_approach",
                new Vector2D(2 * TILE_SIZE, 4 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_left_end",
                new Vector2D(2 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)));

        nodes.add(new PathNode(
                "L01_node_right_01",
                new Vector2D(6 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_02",
                new Vector2D(7 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_turn",
                new Vector2D(8 * TILE_SIZE, 3 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_right_approach",
                new Vector2D(8 * TILE_SIZE, 4 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_end",
                new Vector2D(8 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)));
        nodes.add(new PathNode(
                "L01_node_exit_approach",
                new Vector2D(9 * TILE_SIZE, 4 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_exit_terminal",
                new Vector2D(9 * TILE_SIZE, 5 * TILE_SIZE),
                EnumSet.of(PathNode.Dir.UP)));

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
