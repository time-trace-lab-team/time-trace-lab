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
 * 第三关《追赶过去》关卡数据（开发三 / L03-DEV3）。
 *
 * <p><b>三条路线</b>（设定书 §3.1）：</p>
 * <ul>
 *   <li><b>外侧控制路线</b>（门 A 之外，西侧竖廊）：出生点 → {@link #PLATE_A} → {@link #PLATE_C}
 *       → {@link #PLATE_D}。第一轮玩家与后续第一残影 {@code E₁} 走这条线；</li>
 *   <li><b>B 板支路</b>：{@link #DOOR_A} → 分岔口 {@link #NODE_J} → {@link #RAY_B} → {@link #PLATE_B}。
 *       第二轮玩家与第三轮第二残影 {@code E₂} 走这条线；</li>
 *   <li><b>最终主通道</b>：{@link #DOOR_A} → {@link #NODE_J} → {@link #DOOR_B} → {@link #DOOR_C}
 *       → {@link #EXIT}。第三轮当前玩家走这条线，<b>不经过射线</b>。</li>
 * </ul>
 *
 * <p><b>冻结的空间关系</b>（设定书 §3.2）：门 A(13,10) 是内区唯一入口（第 13 列分隔墙上唯一的缺口）；
 * A/C/D 全在门 A 之外的外区；门 A 与射线不重叠、也不遮挡射线；B 支路（第 17 列竖廊 + 折到 (16,3)）
 * 与主通道（J 向右的折线：门 B(21,7) → 门 C(23,9) → 出口(25,6)）<b>只在分岔口 J(17,10) 相交</b>；
 * 射线格 (17,5) 的左右邻格都是墙，因此它只横跨 B 支路一格；门 A / 门 B / 门 C / 射线<b>都没有旁路</b>。</p>
 *
 * <p><b>终点供能的实现方式</b>：{@code ExitTerminal} 在引擎里只能被一扇 {@code Door} 的
 * {@code DOOR_UNLOCKED} 武装（{@code associatedDoorId} 必须是 {@code door} 类型），因此「D 板为出口供能」
 * 落成 {@link #DOOR_EXIT}：它与出口<b>同格</b>、只要求 {@link #PLATE_D}，不新增机关类型/事件类型，
 * 也不改任何公共契约（与第一关、第二关「终点闸与出口同格」同构）。D 板只供能，<b>不直接通关</b>：
 * 仍须当前玩家在半径内按 {@code E}（设定书 §4.1）。</p>
 *
 * <p><b>刻表怎么来的</b>：24 刻/格（2 世界单位/刻 × 48）。段长 → 路线刻 → 窗口刻 → 公平性刻，
 * 常量按此依赖顺序声明（Java 静态初始化禁止前向引用）；五个公平性不等式（设定书 §6.4）由
 * {@code Level03PursuitGeometryTest} 逐条断言。</p>
 */
public final class Level03Pursuit {

    private Level03Pursuit() {
    }

    // ---------- ① 网格与关卡参数 ----------

    public static final double TILE_SIZE = 48.0;
    public static final int GRID_COLS = 28;
    public static final int GRID_ROWS = 16;

    /** 每格走行刻数（baseSpeed 2 px/tick、tileSize 48）。 */
    public static final long TICKS_PER_TILE = 24L;

    public static final long DURATION_TICKS = 1800L;
    public static final int MAX_ROUNDS = 3;
    public static final int ECHO_LIFE_L = 2;

    /**
     * 地形（28×16 · 地板 260 格 = 58%）。{@code S}=出生点；机关与门都落在可走格上（与第一、二关同一做法）；
     * 字母只是给设计图对照用的标记，引擎一律按 {@code 非 '#' = 可走} 处理。
     *
     * <pre>
     * 第 1-12 列   外区：北西厅 A(3,2) / 北东厅 C(10,2) / 南西厅 S(2,13) / 南东厅 D(10,13)
     * 第 13 列     分隔墙：只有门 A 那一格 (13,10) 是通的 —— 内区唯一入口
     * 第 14-26 列  内区：B 支路（第 17 列，J(17,10) 向上经射线(17,5) 折到 B(16,3)）
     *              + 主通道（J 向右折线：门 B(21,7) → 门 C(23,9) → 出口(25,6)）
     *              + 侧室全是死路（I_N / I_W / 南侧大房 / 右缘凹室），不构成旁路
     * </pre>
     *
     * <p><b>为什么这样摆</b>：11 个功能格任意三点不共线（横/竖/斜）—— 旧图 A/C/B 同在第 2 行、
     * 门 A/J/门 B/门 C/出口 同在第 10 行，排成一条线；新图走成锯齿：B 掉到第 3 行、门 B 上到第 7 行、
     * 门 C 下到第 9 行、出口再上到第 6 行，射线独占第 5 行、D 板在第 13 行。射线格左右两侧都是墙，
     * 因此射线只横跨 B 支路一格。</p>
     */
    public static final String[] MAP = {
            "############################", // 0
            "#............#....#....#...#", // 1
            "#..A##....C..#....#....#...#", // 2  A(3,2) / C(10,2)
            "#...##.......#..B......#...#", // 3  B(16,3)
            "#.....##.....####.#....##..#", // 4
            "##.#######.###..#R#....##..#", // 5  射线(17,5)：左右邻格 (16,5)/(18,5) 都是墙
            "#............#..#.#######x.#", // 6  出口 + 供能闸(25,6)
            "#.....#......#..#.#..b..#..#", // 7  门 B(21,7)
            "#............#....#.###.#..#", // 8
            "###.#######.##..#...###c...#", // 9  门 C(23,9)
            "#............a...JJ....#####", // 10 门 A(13,10) / 分岔口 J(17,10)
            "##.#######.####.##.#########", // 11
            "#.....##.....#.............#", // 12
            "#.S...##..D..#.............#", // 13 出生点(2,13) / D 板(10,13)
            "#............#.............#", // 14
            "############################", // 15
    };

    // ---------- ② 稳定 ID（必须通过 StableIdValidator）----------

    public static final String PLATE_A = "L03_plate_a";
    public static final String PLATE_B = "L03_plate_b";
    public static final String PLATE_C = "L03_plate_c";
    public static final String PLATE_D = "L03_plate_d";

    public static final String DOOR_A = "L03_door_a";
    public static final String DOOR_B = "L03_door_b";
    public static final String DOOR_C = "L03_door_c";
    /** 终点供能闸：与出口同格，只要求 D 板（{@code ExitTerminal} 只能被 Door 武装）。 */
    public static final String DOOR_EXIT = "L03_door_exit";

    public static final String RAY_B = "L03_ray_b";
    public static final String EXIT = "L03_exit_00";

    // ---------- ③ 机关所在格 {列, 行} ----------

    public static final int[] SPAWN_CELL = {2, 13};
    public static final int[] CELL_PLATE_A = {3, 2};
    public static final int[] CELL_PLATE_C = {10, 2};
    public static final int[] CELL_PLATE_D = {10, 13};
    public static final int[] CELL_PLATE_B = {16, 3};
    public static final int[] CELL_DOOR_A = {13, 10};
    public static final int[] CELL_FORK_J = {17, 10};
    public static final int[] CELL_DOOR_B = {21, 7};
    public static final int[] CELL_DOOR_C = {23, 9};
    public static final int[] CELL_EXIT = {25, 6};
    public static final int[] CELL_RAY = {17, 5};

    // ---------- ④ 关键路径节点 ----------

    public static final String NODE_SPAWN = nodeIdOf(SPAWN_CELL);
    public static final String NODE_PLATE_A = nodeIdOf(CELL_PLATE_A);
    public static final String NODE_PLATE_B = nodeIdOf(CELL_PLATE_B);
    public static final String NODE_PLATE_C = nodeIdOf(CELL_PLATE_C);
    public static final String NODE_PLATE_D = nodeIdOf(CELL_PLATE_D);
    public static final String NODE_DOOR_A = nodeIdOf(CELL_DOOR_A);
    public static final String NODE_J = nodeIdOf(CELL_FORK_J);
    public static final String NODE_DOOR_B = nodeIdOf(CELL_DOOR_B);
    public static final String NODE_DOOR_C = nodeIdOf(CELL_DOOR_C);
    public static final String NODE_EXIT = nodeIdOf(CELL_EXIT);

    // ---------- ⑤ 段长（几何 ⇒ 刻）----------

    /** 出生点 → A 板：14 格（BFS 实算，与设计图 36 格 = A/B/C 三段之和一致）。 */
    public static final long SPAWN_TO_PLATE_A_TICKS = 14 * TICKS_PER_TILE;
    /** A 板 → C 板：9 格。 */
    public static final long PLATE_A_TO_C_TICKS = 9 * TICKS_PER_TILE;
    /** C 板 → D 板：13 格。 */
    public static final long PLATE_C_TO_D_TICKS = 13 * TICKS_PER_TILE;

    /** 出生点 → 门 A：14 格（与出生点→A 同长，玩家正好在门开的刻抵达门外）。 */
    public static final long SPAWN_TO_DOOR_A_TICKS = 14 * TICKS_PER_TILE;
    /** 门 A → 分岔口 J：4 格。 */
    public static final long DOOR_A_TO_FORK_TICKS = 4 * TICKS_PER_TILE;
    /** J → 射线：5 格（沿第 17 列向上）。 */
    public static final long FORK_TO_RAY_TICKS = 5 * TICKS_PER_TILE;
    /** 射线 → B 板：3 格（≥ 2.5 格，故受击的迟到量取满 30 刻）。 */
    public static final long RAY_TO_PLATE_B_TICKS = 3 * TICKS_PER_TILE;
    /** J → 门 B：7 格（主通道向右折上）。 */
    public static final long FORK_TO_DOOR_B_TICKS = 7 * TICKS_PER_TILE;
    /** 门 B → 门 C：4 格。 */
    public static final long DOOR_B_TO_DOOR_C_TICKS = 4 * TICKS_PER_TILE;
    /** 门 C → 出口：5 格。 */
    public static final long DOOR_C_TO_EXIT_TICKS = 5 * TICKS_PER_TILE;

    /**
     * E₁ 在 A 板上驻留的格数 = 门 A 窗口宽度。
     *
     * <p>取 2 格（48 刻）而不是更大值：A 窗口越长，E₁ 抵达 C 越晚，后面全部刻表顺延；
     * 48 刻已足够「第二轮玩家与第三轮玩家 + {@code E₂} 依次穿门」（他们都在窗口一开始的刻抵达门外）。</p>
     */
    public static final int HOLD_A_TILES = 2;

    /**
     * E₁ 在 C 板上驻留的格数 = 门 C 窗口宽度。
     *
     * <p>由「正解要留余量」与「受击必失败」共同唯一确定：正解穿门 C 于
     * {@link #DOOR_C_CROSS_TICK}，窗口必须再留 {@link #SUCCESS_MARGIN_TICKS} 刻，同时窗口结束刻必须
     * <b>早于</b>受击路线的门 C 抵达刻 —— 6 格 = 144 刻恰好同时满足（见
     * {@code Level03PursuitGeometryTest.fairnessInequalitiesHold}）。</p>
     */
    public static final int HOLD_C_TILES = 6;

    // ---------- ⑥ 公平性常量（设定书 §6.4）----------

    /**
     * 正解穿过门 C 后到门 C 关闭的余量。
     *
     * <p><b>为什么是 24 而不是设定书建议的 30–45</b>：全局减速是 0.5× / 60 刻，即受击恰好迟
     * {@link #HIT_DELAY_TICKS} = 30 刻。而「正解要留余量」与「受击必须错过门 C」共用同一段余量
     * （{@code margin + 迟到超出量 = 30}）：设定书 §11.3 的「≥30 刻余量」与 §6.4 的「受击必失败」
     * 在既有全局参数下<b>不可能同时成立</b>（要同时成立就得增强全局减速，而设定书 §6.4 末条明令禁止）。
     * 本关取 24 刻（= 1 格），把剩下的 6 刻留给「受击 / 等待路线必须失败」。</p>
     */
    public static final long SUCCESS_MARGIN_TICKS = 24L;

    /** 受击迟到量：0.5× 持续 60 刻 ⇒ 少走 60 单位 ⇒ 折算 60 / 2 = 30 刻。 */
    public static final long HIT_DELAY_TICKS = 30L;

    /** 等待射线关闭的迟到量：抵达射线时正处 ACTIVE，等到 ACTIVE 结束即 60 刻。 */
    public static final long WAIT_FOR_OFF_DELAY_TICKS = 60L;

    // ---------- ⑦ 路线关键刻 ----------

    /** 出生点 → A 板抵达刻（= A 板开始占用 = E₁ 开门的刻 = 玩家穿门 A 的刻）。 */
    public static final long GATE_A_WINDOW_START = SPAWN_TO_PLATE_A_TICKS;
    /** A 板驻留窗口结束：E₁ 在刻 384 离开 A，门 A 回锁（窗口 2 格 = 48 刻）。 */
    public static final long GATE_A_WINDOW_END = GATE_A_WINDOW_START + HOLD_A_TILES * TICKS_PER_TILE;

    /** 第二轮 / 第三轮玩家穿过门 A 的刻（= 门 A 窗口起点；他们正好在门开那一刻抵达门外）。 */
    public static final long DOOR_A_CROSS_TICK = GATE_A_WINDOW_START;
    /** 第二轮玩家抵达 J 的刻。 */
    public static final long FORK_ARRIVAL = DOOR_A_CROSS_TICK + DOOR_A_TO_FORK_TICKS;
    /** 第二轮玩家抵达射线的刻：射线此时<b>必须 ACTIVE</b>，否则「必须下潜」的教学不成立。 */
    public static final long RAY_CROSS_TICK = FORK_ARRIVAL + FORK_TO_RAY_TICKS;
    /** 第二轮正解抵达 B 的刻（= 第三轮 E₂ 在 B 就位刻 = 门 B 开启刻）。 */
    public static final long PLATE_B_ARRIVAL = RAY_CROSS_TICK + RAY_TO_PLATE_B_TICKS;
    /** 第三轮玩家穿过门 B 的刻（门 B 由 E₂ 在刻 {@link #PLATE_B_ARRIVAL} 开启）。 */
    public static final long DOOR_B_CROSS_TICK = PLATE_B_ARRIVAL;
    /** 第三轮玩家穿过门 C 的刻。 */
    public static final long DOOR_C_CROSS_TICK = DOOR_B_CROSS_TICK + DOOR_B_TO_DOOR_C_TICKS;
    /** 第三轮玩家抵达出口的刻。 */
    public static final long EXIT_ARRIVAL = DOOR_C_CROSS_TICK + DOOR_C_TO_EXIT_TICKS;

    // ---------- ⑧ 窗口刻（由公平性反推）----------

    /** A 板 → C 板抵达刻：E₁ 在刻 600 踩上 C，门 C 开启。 */
    public static final long PLATE_C_ARRIVAL = GATE_A_WINDOW_END + PLATE_A_TO_C_TICKS;
    /** 门 C 关闭刻：正解穿过门 C 后还须留 {@link #SUCCESS_MARGIN_TICKS} 刻余量。 */
    public static final long PLATE_C_WINDOW_END = DOOR_C_CROSS_TICK + SUCCESS_MARGIN_TICKS;
    /**
     * C 板 → D 板抵达刻：E₁ 在刻 1056 踩上 D 并驻留到轮末，出口由此供能。
     *
     * <p><b>注意</b>：本图 C→D（13 格）比「门 C→出口」（5 格）长，所以 D 供能晚于玩家抵达出口
     * —— 玩家要在出口<b>等</b>到这一刻再按 {@code E}。这是设计意图（出口在门 C 之后不远，
     * 而外区控制线绕得远），不是缺陷：轮长 1800 刻，供能 1056 刻，余量充足。</p>
     */
    public static final long PLATE_D_ARRIVAL = PLATE_C_WINDOW_END + PLATE_C_TO_D_TICKS;

    /** B 板仍有用的最晚抵达刻 = 门 C 关闭刻 − 门 B→门 C 路程。 */
    public static final long LATEST_USEFUL_B_ARRIVAL =
            PLATE_C_WINDOW_END - DOOR_B_TO_DOOR_C_TICKS;
    /** 受击路线的 B 抵达刻。 */
    public static final long LAGGED_B_ARRIVAL = PLATE_B_ARRIVAL + HIT_DELAY_TICKS;
    /** 等待路线的 B 抵达刻。 */
    public static final long WAIT_FOR_OFF_B_ARRIVAL = PLATE_B_ARRIVAL + WAIT_FOR_OFF_DELAY_TICKS;
    /** 受击路线穿过门 C 的刻（必须晚于门 C 关闭刻）。 */
    public static final long LATE_DOOR_C_ARRIVAL = LAGGED_B_ARRIVAL + DOOR_B_TO_DOOR_C_TICKS;

    // ---------- ⑨ 时滞射线（横跨 B 支路竖廊：y 固定、x 跨一格）----------

    /** 射线所在行的行中心 y。 */
    public static final double RAY_Y = (CELL_RAY[1] + 0.5) * TILE_SIZE;
    /** 射线西端点 x（第 9 列西边界）。 */
    public static final double RAY_X0 = CELL_RAY[0] * TILE_SIZE;
    /** 射线东端点 x（第 9 列东边界）。 */
    public static final double RAY_X1 = (CELL_RAY[0] + 1) * TILE_SIZE;
    /** 命中判定的横向半宽（沿用第二关口径）。 */
    public static final double RAY_HIT_WIDTH = 0.20 * TILE_SIZE;

    /**
     * 关闭（OFF）时长：一个周期里射线不发射的刻数。
     *
     * <p>与「预警 + 激活」共同决定发射频率：周期 = OFF + 预警 + 激活。
     * 本值不影响预警/激活时长，也不挪动绝对刻锚点 {@link #RAY_CROSS_TICK}（靠 {@link
     * #RAY_CYCLE_OFFSET_TICKS} 保持相位）。</p>
     */
    public static final long RAY_OFF_DURATION_TICKS = 240L;
    /** 预警时长：72 刻 ≥ 24–30 刻反应预算 + 6–10 刻输入缓冲（设定书 §6.4）。 */
    public static final long RAY_WARNING_DURATION_TICKS = 72L;
    /** 激活时长。 */
    public static final long RAY_ACTIVE_DURATION_TICKS = 60L;
    /** 周期内预警起点（数值上等于 OFF 段长度）。 */
    public static final long RAY_WARNING_START_TICK = RAY_OFF_DURATION_TICKS;
    /** 周期内激活起点（通常 = 预警起点 + 预警时长）。 */
    public static final long RAY_ACTIVE_START_TICK = RAY_WARNING_START_TICK + RAY_WARNING_DURATION_TICKS;
    /** 一个完整周期 = OFF + 预警 + 激活；状态只由共享 {@code roundTick} 决定（无独立计时器）。 */
    public static final long RAY_CYCLE_TICKS = RAY_OFF_DURATION_TICKS
            + RAY_WARNING_DURATION_TICKS + RAY_ACTIVE_DURATION_TICKS;
    /**
     * 周期原点：使绝对刻 {@link #RAY_CROSS_TICK}（玩家抵达射线那一刻）恰好落在 ACTIVE 段内。
     *
     * <p>反推依据：{@code floorMod(RAY_CROSS_TICK − RAY_CYCLE_OFFSET_TICKS, RAY_CYCLE_TICKS)
     * == RAY_ACTIVE_START_TICK}，即 {@code 552 − 240 ≡ 312 (mod 372)}。</p>
     */
    public static final long RAY_CYCLE_OFFSET_TICKS = 240L;
    /** 抵达射线时刻在周期内的相位（夹具前提：必须落在 [activeStart, activeStart+activeDuration)）。 */
    public static final long RAY_CROSS_PHASE =
            Math.floorMod(RAY_CROSS_TICK - RAY_CYCLE_OFFSET_TICKS, RAY_CYCLE_TICKS);

    // ---------- 构建 ----------

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

    /** 路径节点 ID：{@code L03_node_c<col>_r<row>}。 */
    public static String nodeId(int col, int row) {
        return "L03_node_c" + col + "_r" + row;
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
        entities.add(plate(PLATE_A, CELL_PLATE_A));
        entities.add(plate(PLATE_B, CELL_PLATE_B));
        entities.add(plate(PLATE_C, CELL_PLATE_C));
        entities.add(plate(PLATE_D, CELL_PLATE_D));

        entities.add(new EntitySpawnInfo(EXIT, "exit_terminal",
                cellCenter(CELL_EXIT[0], CELL_EXIT[1]), NODE_EXIT));

        // 射线横跨 B 支路竖廊：y 固定在第 9 行行中心，x 跨第 9 列一格。
        entities.add(new EntitySpawnInfo(RAY_B, "ray", new Vector2D(RAY_X0, RAY_Y), nodeIdOf(CELL_RAY))
                .putProp("endX", RAY_X1)
                .putProp("endY", RAY_Y)
                .putProp("warningStartTick", RAY_WARNING_START_TICK)
                .putProp("warningDurationTicks", RAY_WARNING_DURATION_TICKS)
                .putProp("activeStartTick", RAY_ACTIVE_START_TICK)
                .putProp("activeDurationTicks", RAY_ACTIVE_DURATION_TICKS)
                .putProp("cycleOffsetTicks", RAY_CYCLE_OFFSET_TICKS));
        return entities;
    }

    /** 普通驻留板：{@code dock_plate} + {@code autoDock=true}（四块都不锁存，占即开、离即关）。 */
    private static EntitySpawnInfo plate(String id, int[] cell) {
        return new EntitySpawnInfo(id, "dock_plate",
                cellCenter(cell[0], cell[1]), nodeIdOf(cell))
                .putProp("autoDock", true);
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(door(DOOR_A, CELL_DOOR_A, Set.of(PLATE_A)));
        doors.add(door(DOOR_B, CELL_DOOR_B, Set.of(PLATE_B)));
        doors.add(door(DOOR_C, CELL_DOOR_C, Set.of(PLATE_C)));
        // 终点供能闸：与出口同格，只要求 D 板；D 板只「供能」，通关仍须当前玩家按 E。
        doors.add(door(DOOR_EXIT, CELL_EXIT, Set.of(PLATE_D)));
        return doors;
    }

    private static DoorInfo door(String id, int[] cell, Set<String> requiredPlateIds) {
        return new DoorInfo(id, cellCenter(cell[0], cell[1]), false, requiredPlateIds);
    }
}
