package org.example.timeloop.level;

import org.example.timeloop.level.model.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class Level03Footsteps {

    private Level03Footsteps() {}

    public static LevelData create() {
        long durationTicks = 1080;
        int maxRounds = 4;
        int echoLifeL = 2;
        double tileSize = 48.0;

        Vector2D spawn = new Vector2D(3.5 * tileSize, 13.5 * tileSize);
        List<PathNode> pathNodes = buildPathNodes(tileSize);
        List<EntitySpawnInfo> entities = buildEntities(tileSize);
        List<DoorInfo> doors = buildDoors(tileSize);
        TileType[][] grid = buildGrid();

        return new LevelData(tileSize, grid, pathNodes, entities, doors, spawn,
                durationTicks, maxRounds, echoLifeL);
    }

    private static List<PathNode> buildPathNodes(double tileSize) {
        List<PathNode> nodes = new ArrayList<>();

        EnumSet<PathNode.Dir> allDirs = EnumSet.allOf(PathNode.Dir.class);
        EnumSet<PathNode.Dir> upDown = EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN);
        EnumSet<PathNode.Dir> leftRight = EnumSet.of(PathNode.Dir.LEFT, PathNode.Dir.RIGHT);
        EnumSet<PathNode.Dir> upDownLeft = EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.LEFT);
        EnumSet<PathNode.Dir> upDownRight = EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN, PathNode.Dir.RIGHT);

        nodes.add(new PathNode(
                "start", new Vector2D(3.5 * tileSize, 13.5 * tileSize), allDirs));
        nodes.add(new PathNode(
                "fork", new Vector2D(8.5 * tileSize, 13.5 * tileSize), allDirs));

        nodes.add(new PathNode("left_path_1", new Vector2D(8.5 * tileSize, 10.5 * tileSize), upDown));
        nodes.add(new PathNode("left_path_2", new Vector2D(8.5 * tileSize, 8.5 * tileSize), upDownLeft));
        nodes.add(new PathNode("left_plate", new Vector2D(3.5 * tileSize, 8.5 * tileSize), upDown));
        nodes.add(new PathNode("left_return", new Vector2D(8.5 * tileSize, 8.5 * tileSize), upDownRight));
        nodes.add(new PathNode("left_merge", new Vector2D(8.5 * tileSize, 6.5 * tileSize), upDown));

        nodes.add(new PathNode("right_path_1", new Vector2D(12.5 * tileSize, 13.5 * tileSize), allDirs));
        nodes.add(new PathNode("right_path_2", new Vector2D(12.5 * tileSize, 10.5 * tileSize), upDown));
        nodes.add(new PathNode("right_plate", new Vector2D(12.5 * tileSize, 8.5 * tileSize), upDown));
        nodes.add(new PathNode("right_return", new Vector2D(12.5 * tileSize, 6.5 * tileSize), upDown));
        nodes.add(new PathNode("right_merge", new Vector2D(8.5 * tileSize, 6.5 * tileSize), leftRight));

        nodes.add(new PathNode("door", new Vector2D(5.5 * tileSize, 5.5 * tileSize), allDirs));
        nodes.add(new PathNode("exit_route", new Vector2D(5.5 * tileSize, 3.5 * tileSize), upDown));
        nodes.add(new PathNode("exit_terminal", new Vector2D(5.5 * tileSize, 1.5 * tileSize), upDown));

        return nodes;
    }

    private static List<EntitySpawnInfo> buildEntities(double tileSize) {
        List<EntitySpawnInfo> entities = new ArrayList<>();
        entities.add(new EntitySpawnInfo("DockingPlate", new Vector2D(3.5 * tileSize, 8.5 * tileSize))
                .putProp("autoDock", true));
        entities.add(new EntitySpawnInfo("DockingPlate", new Vector2D(12.5 * tileSize, 8.5 * tileSize))
                .putProp("autoDock", true));
        entities.add(new EntitySpawnInfo("ExitTerminal", new Vector2D(5.5 * tileSize, 1.5 * tileSize)));
        return entities;
    }

    private static List<DoorInfo> buildDoors(double tileSize) {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(new DoorInfo("door_1", new Vector2D(5.5 * tileSize, 5.5 * tileSize), false));
        return doors;
    }

    private static TileType[][] buildGrid() {
        int size = 16;
        TileType[][] grid = new TileType[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                grid[r][c] = TileType.FLOOR;
            }
        }
        for (int i = 0; i < size; i++) {
            grid[0][i] = TileType.WALL;
            grid[size - 1][i] = TileType.WALL;
            grid[i][0] = TileType.WALL;
            grid[i][size - 1] = TileType.WALL;
        }
        for (int r = 6; r <= 12; r++) {
            grid[r][2] = TileType.WALL;
            grid[r][5] = TileType.WALL;
            grid[r][11] = TileType.WALL;
            grid[r][14] = TileType.WALL;
        }
        grid[13][3] = TileType.SPAWN_POINT;
        return grid;
    }
}
