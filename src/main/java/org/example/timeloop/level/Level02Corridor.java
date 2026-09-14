package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 第二关「低姿穿行 + 门房」· L2-A 门房链（开发三，卡号 L02-A-DEV3）。
 *
 * <p>依据 L02-门房与双残影-PM裁决.md §十一（官方解 R1 不进房）+ §十三.2（几何修正）：
 * R2 的进房路线不得经过门外板，否则 E1 与玩家在窗口起点同刻争抢门外板、写入顺序不可控。
 * 因此门外板留在第 6 列（R1 路线），R2 改走第 5 列从南侧进房门。</p>
 *
 * <p>刻表（tileSize 48、baseSpeed 2 → 24 刻/格）：出生点→门外板 9 格=216；窗口 (216,396) 时长 180；
 * 出生点→房门南邻点 7 格=168（早于窗口 48 刻）；跨门刻 252；上内板刻 276；
 * 门外板→主驻留板 12 格=288（396 离开 → 684 到主板）；出生点→闸门 14 格=336；R3 解锁刻 684。</p>
 */
public final class Level02Corridor {

    private Level02Corridor() {}

    public static final double TILE_SIZE = 48.0;
    public static final long DURATION_TICKS = 1200L;
    public static final int MAX_ROUNDS = 4;
    public static final int ECHO_LIFE_L = 2;

    public static final int GRID_COLS = 24;
    public static final int GRID_ROWS = 14;

    /** 每格走行刻数（baseSpeed 2 px/tick、tileSize 48）。 */
    public static final long TICKS_PER_TILE = 24L;

    public static final String NODE_SPAWN = "L02_node_spawn";
    public static final String NODE_PLATE_DOOR = "L02_node_plate_door";
    public static final String NODE_DOOR_ROOM = "L02_node_door_room";
    public static final String NODE_DOOR_SOUTH = "L02_node_door_south";
    public static final String NODE_PLATE_INNER = "L02_node_plate_inner";
    public static final String NODE_PLATE_MAIN = "L02_node_plate_main";
    public static final String NODE_EXIT = "L02_node_exit_terminal";

    public static final String PLATE_DOOR = "L02_plate_door";
    public static final String PLATE_INNER = "L02_plate_inner";
    public static final String PLATE_MAIN = "L02_plate_main";
    public static final String DOOR_ROOM = "L02_door_room";
    public static final String DOOR_EXIT = "L02_door_exit";
    public static final String EXIT = "L02_exit_00";

    /** 窗口起点：R1 踩住门外板的刻。 */
    public static final long WINDOW_START = 216L;
    /** 窗口终点：R1 离开门外板的刻（房门同刻回锁）。 */
    public static final long WINDOW_END = 396L;
    public static final long R2_SOUTH_ARRIVAL = 168L;
    public static final long R2_DOOR_CROSS = 252L;
    public static final long R2_INNER_ARRIVAL = 276L;
    public static final long R1_MAIN_ARRIVAL = 684L;

    // ---- L2-B：短射线走廊（裁决 §二「短射线走廊（保留）」；卡 §三）----
    /** 射线机制 ID（`ray` 类型词，符合稳定 ID 规范）。 */
    public static final String RAY_CORRIDOR = "L02_ray_01";
    /** 竖直判定线所在世界 x（第 8/9 格之间，玩家沿主走廊往返时必然穿过）。 */
    public static final double RAY_X = 9.0 * TILE_SIZE;
    /** 预警起点（共享 roundTick 内的偏移）。 */
    public static final long RAY_WARNING_START_TICK = 0L;
    /** 预警时长：72 刻 = 1.2 s（README §三：1.0–1.4 s）。 */
    public static final long RAY_WARNING_DURATION_TICKS = 72L;
    /** 激活起点 = 预警终点。 */
    public static final long RAY_ACTIVE_START_TICK = 72L;
    /** 激活时长：60 刻 = 1.0 s（README §三：0.8–1.2 s）。 */
    public static final long RAY_ACTIVE_DURATION_TICKS = 60L;
    /** 判定宽度：0.20 × tileSize（README §三：0.18–0.25 × tileSize）。 */
    public static final double RAY_HIT_WIDTH = 0.20 * TILE_SIZE;

    public static LevelData build() {
        return new LevelData(
                TILE_SIZE,
                buildGrid(),
                buildPathNodes(),
                buildEntities(),
                buildDoors(),
                cellCenter(2, 11),
                DURATION_TICKS,
                MAX_ROUNDS,
                ECHO_LIFE_L);
    }

    private static List<PathNode> buildPathNodes() {
        List<PathNode> nodes = new ArrayList<>();

        for (int col = 2; col <= 16; col++) {
            nodes.add(node(nodeIdFor(col, 11), col, 11, corridorDirs(col)));
        }
        for (int row = 7; row <= 10; row++) {
            nodes.add(node(nodeIdFor(6, row), 6, row, EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        }
        nodes.add(node(NODE_PLATE_DOOR, 6, 6, EnumSet.of(PathNode.Dir.DOWN)));

        for (int row = 7; row <= 10; row++) {
            nodes.add(node(verticalId5(row), 5, row, EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        }
        nodes.add(node(NODE_DOOR_ROOM, 5, 6, EnumSet.of(PathNode.Dir.UP, PathNode.Dir.DOWN)));
        nodes.add(node(NODE_PLATE_INNER, 5, 5, EnumSet.of(PathNode.Dir.DOWN)));

        return nodes;
    }

    private static EnumSet<PathNode.Dir> corridorDirs(int col) {
        EnumSet<PathNode.Dir> dirs = EnumSet.noneOf(PathNode.Dir.class);
        if (col > 2) {
            dirs.add(PathNode.Dir.LEFT);
        }
        if (col < 16) {
            dirs.add(PathNode.Dir.RIGHT);
        }
        if (col == 5 || col == 6) {
            dirs.add(PathNode.Dir.UP);
        }
        return dirs;
    }

    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> entities = new ArrayList<>();
        entities.add(new EntitySpawnInfo(PLATE_DOOR, "dock_plate", cellCenter(6, 6), NODE_PLATE_DOOR)
                .putProp("autoDock", true));
        entities.add(new EntitySpawnInfo(PLATE_INNER, "dock_plate", cellCenter(5, 5), NODE_PLATE_INNER)
                .putProp("autoDock", true));
        entities.add(new EntitySpawnInfo(PLATE_MAIN, "dock_plate", cellCenter(13, 11), NODE_PLATE_MAIN)
                .putProp("autoDock", true));
        entities.add(new EntitySpawnInfo(EXIT, "exit_terminal", cellCenter(16, 11), NODE_EXIT));

        // L2-B：一束低风险时滞射线，竖直跨在主走廊第 8/9 格之间（玩家去闸门必然穿过）。
        // 端点只有机制语义；遮挡与绘制由 render（开发一）与 app 负责。
        entities.add(new EntitySpawnInfo(RAY_CORRIDOR, "ray",
                new Vector2D(RAY_X, 10.5 * TILE_SIZE), "L02_node_c9_r11")
                .putProp("endX", RAY_X)
                .putProp("endY", 12.5 * TILE_SIZE)
                .putProp("warningStartTick", RAY_WARNING_START_TICK)
                .putProp("warningDurationTicks", RAY_WARNING_DURATION_TICKS)
                .putProp("activeStartTick", RAY_ACTIVE_START_TICK)
                .putProp("activeDurationTicks", RAY_ACTIVE_DURATION_TICKS));
        return entities;
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(new DoorInfo(DOOR_ROOM, cellCenter(5, 6), false, Set.of(PLATE_DOOR)));
        doors.add(new DoorInfo(DOOR_EXIT, cellCenter(16, 11), false, Set.of(PLATE_INNER, PLATE_MAIN)));
        return doors;
    }

    private static TileType[][] buildGrid() {
        TileType[][] grid = new TileType[GRID_ROWS][GRID_COLS];
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                grid[row][col] = TileType.FLOOR;
            }
        }
        for (int col = 0; col < GRID_COLS; col++) {
            grid[0][col] = TileType.WALL;
            grid[GRID_ROWS - 1][col] = TileType.WALL;
        }
        for (int row = 0; row < GRID_ROWS; row++) {
            grid[row][0] = TileType.WALL;
            grid[row][GRID_COLS - 1] = TileType.WALL;
        }
        grid[4][5] = TileType.WALL;
        grid[5][4] = TileType.WALL;
        grid[6][4] = TileType.WALL;
        grid[5][6] = TileType.WALL;

        grid[11][2] = TileType.SPAWN_POINT;
        return grid;
    }

    private static PathNode node(String id, int col, int row, EnumSet<PathNode.Dir> dirs) {
        return new PathNode(id, cellCenter(col, row), dirs);
    }

    private static String verticalId5(int row) {
        return row == 7 ? NODE_DOOR_SOUTH : "L02_node_c5_r" + row;
    }

    private static String nodeIdFor(int col, int row) {
        if (col == 2 && row == 11) {
            return NODE_SPAWN;
        }
        if (col == 13 && row == 11) {
            return NODE_PLATE_MAIN;
        }
        if (col == 16 && row == 11) {
            return NODE_EXIT;
        }
        return "L02_node_c" + col + "_r" + row;
    }

    private static Vector2D cellCenter(int col, int row) {
        return new Vector2D((col + 0.5) * TILE_SIZE, (row + 0.5) * TILE_SIZE);
    }
}