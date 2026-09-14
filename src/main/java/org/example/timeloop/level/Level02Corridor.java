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
 * 第二关「闸链」· 新地图（28×16）关卡数据。
 *
 * <p><b>唯一事实来源</b>：项目方桌面工具 {@code Level02Render.java} 的 {@code build()} / {@code MAP} /
 * {@code entities()} / {@code doors()}。本类是该数据的忠实移植（同样的格、同样的 ID、同样的刻表常量），
 * 不含任何本地发明。</p>
 *
 * <p><b>地形</b>（{@link #MAP}，{@code #} 墙 / {@code .} 地板）：以「厅 + 1 格厚墙」为主，只有
 * <b>一处</b>真正的单通道 —— 通道 A：第 5 列 r6~r8（3 格，两侧墙 c4/c6），把北厅接到南厅。
 * 每个地板格都是路径节点，ID 为 {@code L02_node_c<col>_r<row>}，可走方向由四邻是否地板推得。
 * 共 300 个节点；{@code GroundWallLayer} 不绘制的「黑洞格」7 个，全在边框上。</p>
 *
 * <p><b>机关</b>：5 块<b>普通驻留板</b>（{@code dock_plate} + {@code autoDock=true}，
 * <b>没有</b> {@code role=switch}、<b>不锁存</b> —— 占即开、离即关）、3 扇门、1 个出口终端、
 * 1 束射线。压板的人自己永远走不过自己开的门，这正是本关必须使用残影的原因。</p>
 *
 * <p><b>官方解（3 轮，每格 24 刻）</b>：</p>
 * <ul>
 *   <li><b>R1</b>：出生点走 10 格到 {@link #PLATE_GATE}（刻 {@link #GATE_WINDOW_START}）造 D1 窗口，
 *       站在板上到刻 {@link #GATE_WINDOW_END}；再走 10 格到 {@link #PLATE_MAIN}
 *       （刻 {@link #MAIN_ARRIVAL}）驻留到轮末 → 生成 E1；</li>
 *   <li><b>R2</b>：借 E1 的窗口在刻 {@link #GATE_DOOR_CROSS} 跨过 {@link #DOOR_GATE} 进东翼，
 *       走 19 格到 {@link #PLATE_RELAY}（刻 {@link #RELAY_WINDOW_START}）踩住到刻
 *       {@link #RELAY_WINDOW_END} 造 D2 窗口，再走 9 格到 {@link #PLATE_INNER}
 *       （刻 {@link #INNER_ARRIVAL}）驻留到轮末 → 生成 E2；</li>
 *   <li><b>R3</b>：E1 复现「闸板窗口 + 主板」，E2 复现「中继窗口 + 内板」；玩家在窗口内
 *       （刻 {@link #RELAY_DOOR_CROSS}）跨过 {@link #DOOR_RELAY} 进右上角内室，
 *       在刻 {@link #CORE_ARRIVAL} 踩住 {@link #PLATE_CORE}；
 *       刻 {@link #EXIT_UNLOCK_TICK} 内板 + 主板 + 终结板同刻被占 → {@link #DOOR_EXIT} 解锁 → 按 E 通关。</li>
 * </ul>
 */
public final class Level02Corridor {

    private Level02Corridor() {}

    // ================= 网格与轮参数 =================

    public static final double TILE_SIZE = 48.0;
    public static final int GRID_COLS = 28;
    public static final int GRID_ROWS = 16;

    /** 每格走行刻数（baseSpeed 2 px/tick、tileSize 48）。 */
    public static final long TICKS_PER_TILE = 24L;

    public static final long DURATION_TICKS = 1200L;
    public static final int MAX_ROUNDS = 4;
    public static final int ECHO_LIFE_L = 2;

    /**
     * {@code #} 墙、{@code .} 地板（逐字移植自项目方桌面工具的 {@code Level02Render.MAP}）。
     *
     * <p>与桌面工具一致：栅格是 {@code [row][col]}，第 0 行在上。</p>
     */
    public static final String[] MAP = {
            "############################",   // 0
            "#...........#..............#",   // 1
            "#.....#.....#..............#",   // 2
            "#.....#.....#..............#",   // 3
            "#...........#..............#",   // 4
            "#.....#.....#########.######",   // 5  ← r5 墙，缺口 (21,5) = D2
            "#...#.#.....#......#..#....#",   // 6  ┐  东翼隔断 c22（r6-8）
            "#...#.#.....#..#...#..#....#",   // 7  ├ 通道 A：c5 单通道（两侧墙 c4 / c6）
            "#.###.#######..#...#..#....#",   // 8  ┘
            "#.......#...#..#...#.......#",   // 9
            "#.......#...#......#....##.#",   // 10
            "#.......#..................#",   // 11 ← c12 缺口 = D1
            "#.......#...#...####.......#",   // 12
            "#...........#......#.......#",   // 13
            "#...........#..............#",   // 14
            "############################",   // 15
    };

    // ================= 机制 ID（桌面工具 P1..P5 / D1..D3） =================

    /** P1 外闸板：开 D1。 */
    public static final String PLATE_GATE = "L02_plate_gate";
    /** P2 中继板：普通驻留板（占即开、离即关），开 D2。 */
    public static final String PLATE_RELAY = "L02_plate_relay";
    /** P3 内板：右下角，终点闸三块之一。 */
    public static final String PLATE_INNER = "L02_plate_inner";
    /** P4 主板：北厅东侧，终点闸三块之一。 */
    public static final String PLATE_MAIN = "L02_plate_main";
    /** P5 终结板：右上角内室，终点闸三块之一（距出口 48 ≤ 72）。 */
    public static final String PLATE_CORE = "L02_plate_core";

    /** D1 外闸：东翼唯一入口，需 {@link #PLATE_GATE}。 */
    public static final String DOOR_GATE = "L02_door_gate";
    /** D2 内室门：右上角内室唯一入口，需 {@link #PLATE_RELAY}。 */
    public static final String DOOR_RELAY = "L02_door_relay";
    /** D3 终点闸：需 {@link #PLATE_INNER} + {@link #PLATE_MAIN} + {@link #PLATE_CORE} 同时被占。 */
    public static final String DOOR_EXIT = "L02_door_exit";

    public static final String EXIT = "L02_exit_00";
    public static final String RAY_CORRIDOR = "L02_ray_01";

    // ================= 机关所在格 {col, row} =================

    public static final int[] SPAWN_CELL = {1, 13};
    public static final int[] CELL_PLATE_GATE = {3, 5};
    public static final int[] CELL_PLATE_MAIN = {9, 1};
    public static final int[] CELL_PLATE_CORE = {24, 2};
    public static final int[] CELL_PLATE_RELAY = {18, 11};
    public static final int[] CELL_PLATE_INNER = {25, 13};
    public static final int[] CELL_DOOR_GATE = {12, 11};
    public static final int[] CELL_DOOR_RELAY = {21, 5};
    /** 终点闸与出口终端同格。 */
    public static final int[] CELL_DOOR_EXIT = {25, 2};

    // ================= 路径节点 ID =================

    public static final String NODE_SPAWN = nodeId(SPAWN_CELL[0], SPAWN_CELL[1]);
    public static final String NODE_PLATE_GATE = nodeId(CELL_PLATE_GATE[0], CELL_PLATE_GATE[1]);
    public static final String NODE_PLATE_MAIN = nodeId(CELL_PLATE_MAIN[0], CELL_PLATE_MAIN[1]);
    public static final String NODE_PLATE_CORE = nodeId(CELL_PLATE_CORE[0], CELL_PLATE_CORE[1]);
    public static final String NODE_PLATE_RELAY = nodeId(CELL_PLATE_RELAY[0], CELL_PLATE_RELAY[1]);
    public static final String NODE_PLATE_INNER = nodeId(CELL_PLATE_INNER[0], CELL_PLATE_INNER[1]);
    public static final String NODE_DOOR_GATE = nodeId(CELL_DOOR_GATE[0], CELL_DOOR_GATE[1]);
    public static final String NODE_DOOR_RELAY = nodeId(CELL_DOOR_RELAY[0], CELL_DOOR_RELAY[1]);
    /** 出口终端（与终点闸 D3 同格）所在节点。 */
    public static final String NODE_EXIT = nodeId(CELL_DOOR_EXIT[0], CELL_DOOR_EXIT[1]);

    // ================= 射线（x=288，竖直线段横穿南厅） =================

    /** 竖直判定线所在世界 x（第 5/6 格之间）。 */
    public static final double RAY_X = 6.0 * TILE_SIZE;
    public static final double RAY_Y0 = 12.5 * TILE_SIZE;
    public static final double RAY_Y1 = 14.5 * TILE_SIZE;

    public static final long RAY_WARNING_START_TICK = 60L;
    /** 预警时长：72 刻 = 1.2 s（README §三：60–84 刻）。 */
    public static final long RAY_WARNING_DURATION_TICKS = 72L;
    /** 激活起点 = 预警终点。 */
    public static final long RAY_ACTIVE_START_TICK = 132L;
    /** 激活时长：60 刻 = 1.0 s（README §三：48–72 刻）。 */
    public static final long RAY_ACTIVE_DURATION_TICKS = 60L;
    /** 周期 = 预警起点 + 预警时长 + 激活时长 = 192 刻。 */
    public static final long RAY_CYCLE_TICKS = RAY_WARNING_START_TICK
            + RAY_WARNING_DURATION_TICKS + RAY_ACTIVE_DURATION_TICKS;
    /** 判定宽度：0.20 × tileSize（README §三：0.18–0.25 × tileSize）。 */
    public static final double RAY_HIT_WIDTH = 0.20 * TILE_SIZE;

    // ================= 官方解刻表（每格 24 刻） =================

    /** R1 踩上外闸板 P1 的刻（出生点→P1 = 10 格）。 */
    public static final long GATE_WINDOW_START = 240L;
    /** R1 松开外闸板 P1 的刻（D1 窗口 [240, 384)，时长 144 刻）。 */
    public static final long GATE_WINDOW_END = 384L;
    /** R1 抵达主板 P4 的刻（P1→P4 = 10 格；384 + 240）。 */
    public static final long MAIN_ARRIVAL = 624L;

    /** 借 E1 窗口跨过 D1 的刻（出生点→D1 = 13 格）。 */
    public static final long GATE_DOOR_CROSS = 312L;

    /** R2 踩上中继板 P2 的刻（出生点→P2 = 19 格）。 */
    public static final long RELAY_WINDOW_START = 456L;
    /** R2 松开中继板 P2 的刻（D2 窗口 [456, 744)，时长 288 刻）。 */
    public static final long RELAY_WINDOW_END = 744L;
    /** R2 抵达内板 P3 的刻（P2→P3 = 9 格；744 + 216）。 */
    public static final long INNER_ARRIVAL = 960L;

    /** R3 玩家抵达 D2 的刻（出生点→D2 = 28 格）。 */
    public static final long RELAY_DOOR_REACH = 672L;
    /** R3 玩家跨过 D2 的刻（抵达后 1 格 = 24 刻，落 D2 窗口内且距终点 48 刻）。 */
    public static final long RELAY_DOOR_CROSS = 696L;
    /** R3 玩家踩上终结板 P5 的刻（D2→P5 = 6 格；696 + 144）。 */
    public static final long CORE_ARRIVAL = 840L;
    /** 三块板同刻被占、终点闸解锁的刻（= {@link #INNER_ARRIVAL}）。 */
    public static final long EXIT_UNLOCK_TICK = 960L;

    // ================= 构建 =================

    public static LevelData build() {
        TileType[][] grid = new TileType[GRID_ROWS][GRID_COLS];
        List<PathNode> nodes = new ArrayList<>();

        for (int row = 0; row < GRID_ROWS; row++) {
            String line = MAP[row];
            if (line.length() != GRID_COLS) {
                throw new IllegalStateException(
                        "第 " + row + " 行宽度 " + line.length() + " ≠ " + GRID_COLS);
            }
            for (int col = 0; col < GRID_COLS; col++) {
                if (line.charAt(col) == '#') {
                    grid[row][col] = TileType.WALL;
                    continue;
                }
                grid[row][col] = TileType.FLOOR;
                EnumSet<PathNode.Dir> dirs = EnumSet.noneOf(PathNode.Dir.class);
                if (isOpen(col, row - 1)) {
                    dirs.add(PathNode.Dir.UP);
                }
                if (isOpen(col, row + 1)) {
                    dirs.add(PathNode.Dir.DOWN);
                }
                if (isOpen(col - 1, row)) {
                    dirs.add(PathNode.Dir.LEFT);
                }
                if (isOpen(col + 1, row)) {
                    dirs.add(PathNode.Dir.RIGHT);
                }
                nodes.add(new PathNode(nodeId(col, row), cellCenter(col, row), dirs));
            }
        }

        grid[SPAWN_CELL[1]][SPAWN_CELL[0]] = TileType.SPAWN_POINT;

        return new LevelData(
                TILE_SIZE,
                grid,
                nodes,
                buildEntities(),
                buildDoors(),
                cellCenter(SPAWN_CELL[0], SPAWN_CELL[1]),
                DURATION_TICKS,
                MAX_ROUNDS,
                ECHO_LIFE_L);
    }

    /** 该格是否是可以走的地板（越界或墙 → false）。 */
    public static boolean isOpen(int col, int row) {
        return row >= 0 && row < GRID_ROWS
                && col >= 0 && col < GRID_COLS
                && MAP[row].charAt(col) != '#';
    }

    /** 桌面工具的节点 ID 规则：{@code L02_node_c<col>_r<row>}。 */
    public static String nodeId(int col, int row) {
        return "L02_node_c" + col + "_r" + row;
    }

    public static String nodeIdOf(int[] cell) {
        return nodeId(cell[0], cell[1]);
    }

    /** 格中心世界坐标。 */
    public static Vector2D cellCenter(int col, int row) {
        return new Vector2D((col + 0.5) * TILE_SIZE, (row + 0.5) * TILE_SIZE);
    }

    private static List<EntitySpawnInfo> buildEntities() {
        List<EntitySpawnInfo> entities = new ArrayList<>();
        entities.add(plate(PLATE_GATE, CELL_PLATE_GATE));
        entities.add(plate(PLATE_RELAY, CELL_PLATE_RELAY));
        entities.add(plate(PLATE_INNER, CELL_PLATE_INNER));
        entities.add(plate(PLATE_MAIN, CELL_PLATE_MAIN));
        entities.add(plate(PLATE_CORE, CELL_PLATE_CORE));

        entities.add(new EntitySpawnInfo(EXIT, "exit_terminal",
                cellCenter(CELL_DOOR_EXIT[0], CELL_DOOR_EXIT[1]), NODE_EXIT));

        entities.add(new EntitySpawnInfo(RAY_CORRIDOR, "ray",
                new Vector2D(RAY_X, RAY_Y0), nodeId(6, 13))
                .putProp("endX", RAY_X)
                .putProp("endY", RAY_Y1)
                .putProp("warningStartTick", RAY_WARNING_START_TICK)
                .putProp("warningDurationTicks", RAY_WARNING_DURATION_TICKS)
                .putProp("activeStartTick", RAY_ACTIVE_START_TICK)
                .putProp("activeDurationTicks", RAY_ACTIVE_DURATION_TICKS));
        return entities;
    }

    /** 普通驻留板：{@code dock_plate} + {@code autoDock=true}，不含 {@code role=switch}（不锁存）。 */
    private static EntitySpawnInfo plate(String id, int[] cell) {
        return new EntitySpawnInfo(id, "dock_plate",
                cellCenter(cell[0], cell[1]), nodeIdOf(cell))
                .putProp("autoDock", true);
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(door(DOOR_GATE, CELL_DOOR_GATE, Set.of(PLATE_GATE)));
        doors.add(door(DOOR_RELAY, CELL_DOOR_RELAY, Set.of(PLATE_RELAY)));
        doors.add(door(DOOR_EXIT, CELL_DOOR_EXIT, Set.of(PLATE_INNER, PLATE_MAIN, PLATE_CORE)));
        return doors;
    }

    private static DoorInfo door(String id, int[] cell, Set<String> requiredPlateIds) {
        return new DoorInfo(id, cellCenter(cell[0], cell[1]), false, requiredPlateIds);
    }
}
