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
        Vector2D spawnWorldPos = cellCenter(5, 1);
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
                cellCenter(5, 1),
                EnumSet.of(PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_corridor_01",
                cellCenter(5, 2),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));

        nodes.add(new PathNode(
                "L01_node_fork",
                cellCenter(5, 3),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT, PathNode.Dir.RIGHT),
                PathNode.Dir.UP));

        nodes.add(new PathNode(
                "L01_node_center_01",
                cellCenter(5, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_door",
                cellCenter(5, 5),
                EnumSet.of(PathNode.Dir.UP)));

        nodes.add(new PathNode(
                "L01_node_left_01",
                cellCenter(4, 3),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_left_02",
                cellCenter(3, 3),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_left_turn",
                cellCenter(2, 3),
                EnumSet.of(PathNode.Dir.RIGHT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_left_approach",
                cellCenter(2, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_left_end",
                cellCenter(2, 5),
                EnumSet.of(PathNode.Dir.UP)));

        nodes.add(new PathNode(
                "L01_node_right_01",
                cellCenter(6, 3),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_02",
                cellCenter(7, 3),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_turn",
                cellCenter(8, 3),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_right_approach",
                cellCenter(8, 4),
                EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT)));
        nodes.add(new PathNode(
                "L01_node_right_end",
                cellCenter(8, 5),
                EnumSet.of(PathNode.Dir.UP)));
        nodes.add(new PathNode(
                "L01_node_exit_approach",
                cellCenter(9, 4),
                EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.DOWN)));
        nodes.add(new PathNode(
                "L01_node_exit_terminal",
                cellCenter(9, 5),
                EnumSet.of(PathNode.Dir.UP)));

        return nodes;
    }

    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> list = new ArrayList<>();

        list.add(new EntitySpawnInfo(
                        "L01_plate_left",
                        "dock_plate",
                        cellCenter(2, 5),
                        "L01_node_left_end")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                        "L01_plate_right",
                        "dock_plate",
                        cellCenter(8, 5),
                        "L01_node_right_end")
                .putProp("autoDock", true));

        list.add(new EntitySpawnInfo(
                "L01_exit_00",
                "exit_terminal",
                cellCenter(9, 5),
                "L01_node_exit_terminal"));

        return list;
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(new DoorInfo(
                "L01_door_01",
                cellCenter(5, 5),
                false,
                Set.of("L01_plate_left", "L01_plate_right")));
        return doors;
    }

    /** 第一关世界坐标约定：所有路径节点和可见物件位于对应格子的几何中心。 */
    private static Vector2D cellCenter(int col, int row) {
        return new Vector2D(
                (col + 0.5) * TILE_SIZE,
                (row + 0.5) * TILE_SIZE);
    }
}
