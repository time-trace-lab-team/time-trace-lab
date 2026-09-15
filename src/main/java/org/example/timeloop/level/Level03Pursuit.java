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
 * 第三关《追赶过去》关卡数据（开发三 / L03-DEV3）· <b>重排 v3</b>。
 *
 * <p><b>判定链</b>（v3 冻结）：门 A ← A 板；门 C ← C 板；门 B ← B 板；
 * <b>出口闸 ← {S₂, S₃, K}</b>（三块板同时满足才给出口供能）。</p>
 *
 * <p><b>三条路线</b>：</p>
 * <ul>
 *   <li><b>外侧控制线</b>（第一轮玩家与后续 E₁）：出生点 → A 板 → C 板 → <b>K 板</b>（原 D 的位置，
 *       核心激活驻留板，E₁ 压到轮末）。第一轮录下的 A/C/K 三个驻留窗口就是后面两轮的「时间资源」；</li>
 *   <li><b>E₂ 支路</b>（第二轮玩家 = 第三轮 E₂）：门 A → 分岔 J → 向南 → <b>S₂ 开关</b> → 相位下潜过
 *       时滞射线 → 门 C → 驻留 B 板到轮末；</li>
 *   <li><b>E₃ 主线</b>（第三轮玩家）：门 A → J → 向北 → 在门 B 外等 E₂ 开门 → 穿门 B → 进东北开关室
 *       踩 <b>S₃ 开关</b> → 折回 → 穿门 C → 东南回环 → 出口按 {@code E}。</li>
 * </ul>
 *
 * <p><b>v3 相对上一版的变化</b>：地图整块重排；内区入口仍是门 A(13,10)（中央竖门位置保留）；
 * 分岔口 {@link #CELL_FORK_J}(15,10)；射线是<b>竖向</b> {@link #CELL_RAY}(19,11)（竖跨 E₂ 走廊一格，
 * 上下两侧都是墙）；新增两个<b>锁存开关</b>（关卡数据 {@code role=switch}）
 * {@link #SWITCH_S2}(15,13) 与 {@link #SWITCH_S3}(20,2)；出口闸条件 = {@code {S₂, S₃, K}}。</p>
 *
 * <p><b>2026-09-15 第二版改动（项目方指定）</b>：C 板从 (10,2) 挪到 {@link #CELL_PLATE_C}(12,3)；
 * K 板从 (10,13) 挪到 {@link #CELL_PLATE_K}(7,14)；(6,14) 补一面墙，
 * 于是 K 板格只剩「向右」一个出口 —— 玩家进去就只能停在那里压到轮末。</p>
 *
 * <p><b>门 C 窗口与第二轮的空等（v3 重排后的既有事实，公平性断言按此写）</b>：C 在 (12,3)，
 * A→C 要 12 格（第 1 行走廊绕远），因此门 C 要到刻 {@link #PLATE_C_ARRIVAL}(648) 才开；
 * 而 E₂ 在刻 {@link #E2_DOOR_C_ARRIVAL}(624) 就抵达门口，会空等 {@link #E2_DOOR_C_WAIT_TICKS}(24) 刻。
 * 这 24 刻会抵掉后续路线的一部分迟到量，所以公平性按<b>净迟到</b>算：</p>
 * <ul>
 *   <li>受击（迟 30）：净迟 6 刻 → 穿门 C 于 {@link #LATE_DOOR_C_ARRIVAL}(1062)，仍在窗口末
 *       {@link #PLATE_C_WINDOW_END}(1080) <b>之内</b> —— 本版里「被射线打中」不再必然失败；</li>
 *   <li>等射线关闭（迟 60）：净迟 36 刻 → 穿门 C 于 {@link #WAIT_DOOR_C_ARRIVAL}(1092)，
 *       已晚于窗口末 → <b>仍然失败</b>；</li>
 *   <li>正解：余量 {@link #SUCCESS_MARGIN_TICKS}(24) 刻。</li>
 * </ul>
 *
 * <p><b>为什么 S₂ / S₃ 必须是锁存开关而不是普通板</b>：出口闸的条件是「三块同时成立」，
 * 但 S₂ 是 E₂ 在刻 {@link #SWITCH_S2_ARRIVAL} 路过踩一下就继续往门 C 走、S₃ 是 E₃ 在刻
 * {@link #SWITCH_S3_ARRIVAL} 踩一下就折回，两者都不可能在刻 {@link #PLATE_K_ARRIVAL}（K 板被压住的那一刻）
 * 还留在原地。开关的「踩上即锁存到轮末」正是为这种「一次性兑现」设计的（与第一关开关同语义）。</p>
 *
 * <p><b>刻表怎么来的</b>：24 刻/格（2 世界单位/刻 × 48）。段长 → 路线刻 → 窗口刻 → 公平性刻，
 * 常量按此依赖顺序声明（Java 静态初始化禁止前向引用）；七条公平性不等式（设计文档「公平性」一节）
 * 由 {@code Level03PursuitGeometryTest} 逐条断言。</p>
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
     * 地形（28×16 · 地板 240 格 = 54%）。{@code S}=出生点；机关与门都落在可走格上；
     * 字母只是给设计图对照用的标记，引擎一律按 {@code 非 '#' = 可走} 处理。
     *
     * <pre>
     * 第 1-12 列   外区：北西厅 A(3,2) / 北东厅 C(10,2) / 南西厅 S(2,13) / 南东厅 K(10,13)
     * 第 13 列     分隔墙：只有门 A 那一格 (13,10) 是通的 —— 内区唯一入口
     * 第 14-27 列  内区：
     *              西侧竖廊（第 15-18 列，从 J(15,10) 向北到开关室、向南到 S₂(15,13)）
     *              + 东北开关室（S₃(20,2)，唯一进口是门 B(20,5)）
     *              + 东室（B 板(23,9)）+ 东南回环（门 C(20,11) → 出口(26,14)）
     * </pre>
     */
    public static final String[] MAP = {
            "############################", // 0
            "#............###..........##", // 1
            "#..A##.......###....3.....##", // 2  A(3,2) / S₃(20,2)
            "#...##.C.....###..........##", // 3  C(7,3)
            "#.....##.....#######.#######", // 4
            "##.#######.####.....b#######", // 5  门 B(20,5)：开关室唯一进口
            "#............##....#.#######", // 6
            "#.....#.....K##....#.#....##", // 7  K(12,7)
            "#............##....#.#....##", // 8
            "###.#######.###....#.#.B..##", // 9  B 板(23,9)
            "#............a.J.###.#....##", // 10 门 A(13,10) / 分岔 J(15,10)
            "##.#######.####....Rc..#####", // 11 射线(19,11) 竖跨走廊 / 门 C(20,11)
            "#.....##.....##...##.#....##", // 12
            "#.S...##.....##...##.#....##", // 13 出生点(2,13)
            "#.....#......##...##.2....x#", // 14 S₂(21,14) / 出口(26,14)
            "############################", // 15
    };

    // ---------- ② 稳定 ID（必须通过 StableIdValidator）----------

    public static final String PLATE_A = "L03_plate_a";
    public static final String PLATE_C = "L03_plate_c";
    /** 核心激活驻留板（v2 的 D 板，v3 换位改名）：与两个开关一起给出口供能。 */
    public static final String PLATE_K = "L03_plate_k";
    public static final String PLATE_B = "L03_plate_b";

    /** S₂：第二轮那个黄色按钮改成的<b>驻留板</b>（外观按驻留板投，见 Level03Assembly.SWITCH_IDS），位置在东南回环上。 */
    public static final String SWITCH_S2 = "L03_plate_s2";
    /** S₃ 开关：东北开关室里，锁在门 B 后面，踩上即锁存（{@code role=switch}）。 */
    public static final String SWITCH_S3 = "L03_plate_s3";

    public static final String DOOR_A = "L03_door_a";
    public static final String DOOR_B = "L03_door_b";
    public static final String DOOR_C = "L03_door_c";
    /** 终点供能闸：与出口同格，要求 {@code {S₂, S₃, K}} 三块同时成立。 */
    public static final String DOOR_EXIT = "L03_door_exit";

    public static final String RAY_B = "L03_ray_b";
    public static final String EXIT = "L03_exit_00";

    // ---------- ③ 机关所在格 {列, 行} ----------

    public static final int[] SPAWN_CELL = {2, 13};
    public static final int[] CELL_PLATE_A = {3, 2};
    public static final int[] CELL_PLATE_C = {7, 3};
    public static final int[] CELL_PLATE_K = {12, 7};
    public static final int[] CELL_PLATE_B = {23, 9};
    public static final int[] CELL_SWITCH_S2 = {21, 14};
    public static final int[] CELL_SWITCH_S3 = {20, 2};
    public static final int[] CELL_DOOR_A = {13, 10};
    public static final int[] CELL_FORK_J = {15, 10};
    public static final int[] CELL_DOOR_B = {20, 5};
    public static final int[] CELL_DOOR_C = {20, 11};
    public static final int[] CELL_EXIT = {26, 14};
    public static final int[] CELL_RAY = {19, 11};

    // ---------- ④ 关键路径节点 ----------

    public static final String NODE_SPAWN = nodeIdOf(SPAWN_CELL);
    public static final String NODE_PLATE_A = nodeIdOf(CELL_PLATE_A);
    public static final String NODE_PLATE_B = nodeIdOf(CELL_PLATE_B);
    public static final String NODE_PLATE_C = nodeIdOf(CELL_PLATE_C);
    public static final String NODE_PLATE_K = nodeIdOf(CELL_PLATE_K);
    public static final String NODE_SWITCH_S2 = nodeIdOf(CELL_SWITCH_S2);
    public static final String NODE_SWITCH_S3 = nodeIdOf(CELL_SWITCH_S3);
    public static final String NODE_DOOR_A = nodeIdOf(CELL_DOOR_A);
    public static final String NODE_J = nodeIdOf(CELL_FORK_J);
    public static final String NODE_DOOR_B = nodeIdOf(CELL_DOOR_B);
    public static final String NODE_DOOR_C = nodeIdOf(CELL_DOOR_C);
    public static final String NODE_EXIT = nodeIdOf(CELL_EXIT);

    // ---------- ⑤ 段长（几何 ⇒ 刻）----------

    /** 出生点 → A 板：14 格（BFS 实算）。 */
    public static final long SPAWN_TO_PLATE_A_TICKS = 14 * TICKS_PER_TILE;
    /** A 板 → C 板：12 格（C 在 (12,3)，第 1 行走廊绕远：(4,3)(5,3) 是墙）。 */
    public static final long PLATE_A_TO_C_TICKS = 7 * TICKS_PER_TILE;
    /** C 板 → K 板：18 格。 */
    public static final long PLATE_C_TO_K_TICKS = 9 * TICKS_PER_TILE;

    /** 出生点 → 门 A：14 格（与出生点→A 同长，玩家正好在门开那一刻抵达门外）。 */
    public static final long SPAWN_TO_DOOR_A_TICKS = 14 * TICKS_PER_TILE;
    /** 门 A → 分岔口 J：2 格。 */
    public static final long DOOR_A_TO_FORK_TICKS = 2 * TICKS_PER_TILE;
    /** J → S₂（向南）：3 格。 */
    public static final long FORK_TO_SWITCH_S2_TICKS = 10 * TICKS_PER_TILE;
    /** J → 时滞射线（沿 E₂ 走廊，不经 S₂）：9 格。 */
    public static final long FORK_TO_RAY_TICKS = 9 * TICKS_PER_TILE;
    /** 门 C → S₂（东南回环上、第三轮去出口的路上）：4 格。 */
    public static final long DOOR_C_TO_SWITCH_S2_TICKS = 4 * TICKS_PER_TILE;
    /** S₂ → 时滞射线：6 格。 */
    public static final long SWITCH_S2_TO_RAY_TICKS = 5 * TICKS_PER_TILE;
    /** 射线 → 门 C：1 格。 */
    public static final long RAY_TO_DOOR_C_TICKS = 1 * TICKS_PER_TILE;
    /** 门 C → B 板：5 格。 */
    public static final long DOOR_C_TO_PLATE_B_TICKS = 5 * TICKS_PER_TILE;
    /** J → 门 B（向北再折东）：10 格。 */
    public static final long FORK_TO_DOOR_B_TICKS = 10 * TICKS_PER_TILE;
    /** 门 B → S₃（开关室里面）：3 格。 */
    public static final long SWITCH_S3_TO_DOOR_B_TICKS = 3 * TICKS_PER_TILE;
    /** 门 B → 门 C：6 格（沿第 20 列直下）。 */
    public static final long DOOR_B_TO_DOOR_C_TICKS = 6 * TICKS_PER_TILE;
    /** 门 C → 出口：9 格（东南回环）。 */
    public static final long DOOR_C_TO_EXIT_TICKS = 9 * TICKS_PER_TILE;

    /**
     * E₁ 在 A 板上驻留的格数 = 门 A 窗口宽度（1 格 = 24 刻）。
     *
     * <p>E₁ 在刻 {@link #GATE_A_WINDOW_START} 踩上 A 板、刻 {@link #GATE_A_WINDOW_END} 离开；
     * 第二轮玩家与第三轮玩家都在窗口一开始的刻抵达门外（他们是被门挡住的，门一开就走，
     * 不需要反应时间，因此 24 刻足够；每轮只有一个当前玩家要穿门 A，残影回放不产生移动）。</p>
     *
     * <p>取 1 而不是 2：C 在 (12,3)、A→C 要 12 格，驻留 2 格会让门 C 窗口起刻落到 672，
     * 第二轮在门口空等 48 刻，连「等射线关闭」那条失败路线（迟 60 刻）都会被空等吃掉。</p>
     */
    public static final int HOLD_A_TILES = 1;

    /**
     * E₁ 在 C 板上驻留的格数 = 门 C 窗口宽度（18 格 = 432 刻）。
     *
     * <p>由「E₂ 必须先过门 C 才能拿到 B 板」与「E₃ 必须在同一窗口内二次穿过门 C」共同确定：
     * 窗口从 E₁ 抵达 C 的刻 {@link #PLATE_C_ARRIVAL}(648) 一直到 {@link #PLATE_C_WINDOW_END}(1080)，
     * 中间要容下 E₂ 与 E₃ 两次穿越。</p>
     */
    public static final int HOLD_C_TILES = 22;

    // ---------- ⑥ 公平性常量 ----------

    /**
     * 正解穿过门 C 后保留的余量（刻）。
     *
     * <p>与上一版同一个口径：全局减速 0.5× / 60 刻 ⇒ 受击恰好迟 {@link #HIT_DELAY_TICKS} = 30 刻，
     * 而「正解要留余量」与「受击必须错过门 C」共用同一段余量（{@code margin + 超出量 = 30}）。
     * v3 取 24（剩余 6 刻给「受击 / 等待路线必须失败」），与设计文档「公平性 ④」一致。</p>
     */
    public static final long SUCCESS_MARGIN_TICKS = 24L;

    /** 受击迟到量：0.5× 持续 60 刻 ⇒ 少走 60 单位 ⇒ 折算 60 / 2 = 30 刻。 */
    public static final long HIT_DELAY_TICKS = 30L;

    /** 等待射线关闭的迟到量：抵达射线时正处 ACTIVE，等到 ACTIVE 结束即 60 刻。 */
    public static final long WAIT_FOR_OFF_DELAY_TICKS = 60L;

    // ---------- ⑦ 路线关键刻 ----------

    /** 出生点 → A 板抵达刻（= A 板开始占用 = E₁ 开门 A 的刻 = 玩家穿门 A 的刻）。 */
    public static final long GATE_A_WINDOW_START = SPAWN_TO_PLATE_A_TICKS;
    /** A 板驻留窗口结束：E₁ 在刻 360 离开 A，门 A 回锁。 */
    public static final long GATE_A_WINDOW_END = GATE_A_WINDOW_START + HOLD_A_TILES * TICKS_PER_TILE;

    /** 第二轮 / 第三轮玩家穿过门 A 的刻（= 门 A 窗口起点）。 */
    public static final long DOOR_A_CROSS_TICK = GATE_A_WINDOW_START;
    /** 第二轮 / 第三轮玩家抵达分岔口 J 的刻。 */
    public static final long FORK_ARRIVAL = DOOR_A_CROSS_TICK + DOOR_A_TO_FORK_TICKS;
    /** E₂ 抵达射线的刻：射线此时<b>必须 ACTIVE</b>（＝ {@link #RAY_ACTIVE_START_TICK}）。 */
    public static final long RAY_CROSS_TICK = FORK_ARRIVAL + FORK_TO_RAY_TICKS;
    /** E₁ 抵达 C 板、门 C 开启的刻。 */
    public static final long PLATE_C_ARRIVAL = GATE_A_WINDOW_END + PLATE_A_TO_C_TICKS;
    /** E₂ 抵达门 C 门口的刻（纯几何：射线 → 门 C 1 格）。 */
    public static final long E2_DOOR_C_ARRIVAL = RAY_CROSS_TICK + RAY_TO_DOOR_C_TICKS;
    /**
     * E₂ 实际穿过门 C 的刻 = max(门口抵达刻, 门 C 开启刻)。
     *
     * <p>A→C 12 格之后门 C 要到 {@link #PLATE_C_ARRIVAL}(648) 才开，而 E₂ 在 624 就到门口，
     * 因此 E₂ 会空等 {@link #E2_DOOR_C_WAIT_TICKS}(24) 刻。这 24 刻会抵掉后来每条路线的一部分
     * 迟到量，因此受击 / 等待的后果要按「净迟到」算（见 {@link #LAGGED_B_ARRIVAL}）。</p>
     */
    public static final long DOOR_C_CROSS_BY_E2_TICK = Math.max(E2_DOOR_C_ARRIVAL, PLATE_C_ARRIVAL);
    /** E₂ 在门 C 门口的空等刻数（0 表示门已经开着）。 */
    public static final long E2_DOOR_C_WAIT_TICKS = DOOR_C_CROSS_BY_E2_TICK - E2_DOOR_C_ARRIVAL;
    /** E₂ 抵达 B 板的刻（= 第三轮门 B 开启刻）。 */
    public static final long PLATE_B_ARRIVAL = DOOR_C_CROSS_BY_E2_TICK + DOOR_C_TO_PLATE_B_TICKS;
    /** 门 B 开启刻（E₂ 压上 B 板的那一该）。 */
    public static final long DOOR_B_OPEN_TICK = PLATE_B_ARRIVAL;
    /** 第三轮 E₃ 抵达门 B 门外的刻（必须早于开门刻，否则本轮必失败）。 */
    public static final long E3_DOOR_B_ARRIVAL = FORK_ARRIVAL + FORK_TO_DOOR_B_TICKS;
    /** 第三轮 E₃ 穿过门 B 的刻（= 门 B 开启刻，E₃ 正好在门外等到它开）。 */
    public static final long DOOR_B_CROSS_TICK = DOOR_B_OPEN_TICK;
    /** E₃ 踩上 S₃ 开关的刻（出口闸的第二个条件）。 */
    public static final long SWITCH_S3_ARRIVAL = DOOR_B_CROSS_TICK + SWITCH_S3_TO_DOOR_B_TICKS;
    /** 第三轮玩家穿过门 C 的刻（S₃ → 折回门 B → 直下门 C）。 */
    public static final long DOOR_C_CROSS_TICK = SWITCH_S3_ARRIVAL
            + SWITCH_S3_TO_DOOR_B_TICKS + DOOR_B_TO_DOOR_C_TICKS;
    /** 第三轮玩家抵达出口的刻。 */
    public static final long EXIT_ARRIVAL = DOOR_C_CROSS_TICK + DOOR_C_TO_EXIT_TICKS;
    /** 第三轮玩家在东南回环上踩到 S₂ 的刻（出口闸的第一个条件；S₂ 已不在 E₂ 支路）。 */
    public static final long SWITCH_S2_ARRIVAL = DOOR_C_CROSS_TICK + DOOR_C_TO_SWITCH_S2_TICKS;

    // ---------- ⑧ 窗口刻与公平性反推 ----------

    /** 门 C 关闭刻：正解（E₃）穿过门 C 后还须留 {@link #SUCCESS_MARGIN_TICKS} 刻余量。 */
    public static final long PLATE_C_WINDOW_END = DOOR_C_CROSS_TICK + SUCCESS_MARGIN_TICKS;
    /**
     * E₁ 抵达 K 板的刻：E₁ 离开 C 后走 18 格到 K 并驻留到轮末，出口闸的第三个条件由此满足。
     *
     * <p>K 板满足刻晚于玩家抵达出口的刻（{@link #EXIT_ARRIVAL}）——玩家要在出口
     * <b>等</b>到那一刻再按 {@code E}。这是设计意图（外区控制线绕得远），不是缺陷。</p>
     */
    public static final long PLATE_K_ARRIVAL = PLATE_C_WINDOW_END + PLATE_C_TO_K_TICKS;

    /** E₃ 从「门 B 开启」走到「穿过门 C」的刻数（S₃ → 折回门 B → 直下门 C = 3+3+6 = 12 格）。 */
    public static final long E3_DOOR_B_TO_DOOR_C_TICKS = SWITCH_S3_TO_DOOR_B_TICKS
            + SWITCH_S3_TO_DOOR_B_TICKS + DOOR_B_TO_DOOR_C_TICKS;

    /** B 板仍有用的最晚抵达刻：再晚 E₃ 就赶不上门 C 窗口（= 门 C 关闭刻 − 余量 − E₃ 后段）。 */
    public static final long LATEST_USEFUL_B_ARRIVAL =
            PLATE_C_WINDOW_END - SUCCESS_MARGIN_TICKS - E3_DOOR_B_TO_DOOR_C_TICKS;
    /**
     * 受击路线的 B 抵达刻。
     *
     * <p>迟到量按「净迟到」算：E₂ 在门 C 门口的空等会先抵掉 {@link #E2_DOOR_C_WAIT_TICKS} 刻
     * （受击迟 30 − 空等 24 = 净迟 6），因此实测是 774 而不是 768 + 30 = 798。</p>
     */
    public static final long LAGGED_B_ARRIVAL = Math.max(E2_DOOR_C_ARRIVAL + HIT_DELAY_TICKS,
            PLATE_C_ARRIVAL) + DOOR_C_TO_PLATE_B_TICKS;
    /** 等待路线的 B 抵达刻（净迟 60 − 24 = 36）。 */
    public static final long WAIT_FOR_OFF_B_ARRIVAL = Math.max(
            E2_DOOR_C_ARRIVAL + WAIT_FOR_OFF_DELAY_TICKS, PLATE_C_ARRIVAL)
            + DOOR_C_TO_PLATE_B_TICKS;
    /** 受击路线穿过门 C 的刻（门 C 关闭刻 1080；实测 1062，仍在窗口内）。 */
    public static final long LATE_DOOR_C_ARRIVAL = LAGGED_B_ARRIVAL + E3_DOOR_B_TO_DOOR_C_TICKS;
    /** 等待路线穿过门 C 的刻（必须晚于门 C 关闭刻）。 */
    public static final long WAIT_DOOR_C_ARRIVAL = WAIT_FOR_OFF_B_ARRIVAL + E3_DOOR_B_TO_DOOR_C_TICKS;

    // ---------- ⑨ 时滞射线（竖跨 E₂ 走廊一格：x 固定、y 跨一格）----------

    /** 射线所在列的列中心 x。 */
    public static final double RAY_X = (CELL_RAY[0] + 0.5) * TILE_SIZE;
    /** 射线上端点 y（第 11 行北边界）。 */
    public static final double RAY_Y0 = CELL_RAY[1] * TILE_SIZE;
    /** 射线下端点 y（第 11 行南边界）。 */
    public static final double RAY_Y1 = (CELL_RAY[1] + 1) * TILE_SIZE;
    /** 命中判定的横向半宽（沿用第二关口径）。 */
    public static final double RAY_HIT_WIDTH = 0.20 * TILE_SIZE;

    /**
     * 关闭（OFF）时长：一个周期里射线不发射的刻数。
     *
     * <p>与「预警 + 激活」共同决定发射频率：周期 = OFF + 预警 + 激活。v3 设计文档冻结
     * 「预警起 528、激活起 600、周期 660」，因此 OFF = 528。</p>
     */
    public static final long RAY_OFF_DURATION_TICKS = 528L;
    /** 预警时长：72 刻 ≥ 24–30 刻反应预算 + 6–10 刻输入缓冲（设定书 §6.4）。 */
    public static final long RAY_WARNING_DURATION_TICKS = 72L;
    /** 激活时长。 */
    public static final long RAY_ACTIVE_DURATION_TICKS = 60L;
    /** 周期内预警起点（数值上等于 OFF 段长度）= 528；v3 里它同时就是绝对预警起点。 */
    public static final long RAY_WARNING_START_TICK = RAY_OFF_DURATION_TICKS;
    /** 周期内激活起点 = 预警起点 + 预警时长 = 600（= E₂ 抵达射线的刻）。 */
    public static final long RAY_ACTIVE_START_TICK = RAY_WARNING_START_TICK + RAY_WARNING_DURATION_TICKS;
    /** 一个完整周期 = OFF + 预警 + 激活 = 528 + 72 + 60 = 660；状态只由共享 {@code roundTick} 决定。 */
    public static final long RAY_CYCLE_TICKS = RAY_OFF_DURATION_TICKS
            + RAY_WARNING_DURATION_TICKS + RAY_ACTIVE_DURATION_TICKS;
    /**
     * 周期原点：使绝对刻 {@link #RAY_CROSS_TICK}（玩家抵达射线那一刻）恰好落在 ACTIVE 段内。
     *
     * <p>反推依据：{@code floorMod(RAY_CROSS_TICK − RAY_CYCLE_OFFSET_TICKS, RAY_CYCLE_TICKS)
     * == RAY_ACTIVE_START_TICK}。v3 的绝对刻与周期内刻一一对应（预警起点 528、激活起点 600），
     * 因此原点 = 600 − 600 = 0；写成差值形式是为了以后改锚点时自动跟着走。</p>
     */
    public static final long RAY_CYCLE_OFFSET_TICKS = RAY_CROSS_TICK - RAY_ACTIVE_START_TICK;
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
        entities.add(plate(PLATE_C, CELL_PLATE_C));
        entities.add(plate(PLATE_K, CELL_PLATE_K));
        entities.add(plate(PLATE_B, CELL_PLATE_B));
        // 两个锁存开关：踩上即锁存到轮末（出口闸要它们与 K 板同时成立，见类注释）。
        entities.add(switchPlate(SWITCH_S2, CELL_SWITCH_S2));
        entities.add(switchPlate(SWITCH_S3, CELL_SWITCH_S3));

        entities.add(new EntitySpawnInfo(EXIT, "exit_terminal",
                cellCenter(CELL_EXIT[0], CELL_EXIT[1]), NODE_EXIT));

        // 射线竖跨 E₂ 走廊一格：x 固定在第 19 列列中心，y 跨第 11 行一格。
        entities.add(new EntitySpawnInfo(RAY_B, "ray", new Vector2D(RAY_X, RAY_Y0), nodeIdOf(CELL_RAY))
                .putProp("endX", RAY_X)
                .putProp("endY", RAY_Y1)
                .putProp("warningStartTick", RAY_WARNING_START_TICK)
                .putProp("warningDurationTicks", RAY_WARNING_DURATION_TICKS)
                .putProp("activeStartTick", RAY_ACTIVE_START_TICK)
                .putProp("activeDurationTicks", RAY_ACTIVE_DURATION_TICKS)
                .putProp("cycleOffsetTicks", RAY_CYCLE_OFFSET_TICKS));
        return entities;
    }

    /** 普通驻留板：{@code dock_plate} + {@code autoDock=true}（占即开、离即关）。 */
    private static EntitySpawnInfo plate(String id, int[] cell) {
        return new EntitySpawnInfo(id, "dock_plate",
                cellCenter(cell[0], cell[1]), nodeIdOf(cell))
                .putProp("autoDock", true);
    }

    /** 锁存开关变体：{@code role=switch}（装配层据此把 {@code latching} 传成 true）。 */
    private static EntitySpawnInfo switchPlate(String id, int[] cell) {
        return plate(id, cell).putProp("role", "switch");
    }

    private static List<DoorInfo> buildDoors() {
        List<DoorInfo> doors = new ArrayList<>();
        doors.add(door(DOOR_A, CELL_DOOR_A, Set.of(PLATE_A)));
        doors.add(door(DOOR_B, CELL_DOOR_B, Set.of(PLATE_B)));
        doors.add(door(DOOR_C, CELL_DOOR_C, Set.of(PLATE_C)));
        // 终点供能闸：与出口同格，要求 S₂ + S₃ + K 三块同时成立。
        // D 板只「供能」，通关仍须当前玩家在半径内按 E。
        doors.add(door(DOOR_EXIT, CELL_EXIT, Set.of(SWITCH_S2, SWITCH_S3, PLATE_K)));
        return doors;
    }

    private static DoorInfo door(String id, int[] cell, Set<String> requiredPlateIds) {
        return new DoorInfo(id, cellCenter(cell[0], cell[1]), false, requiredPlateIds);
    }
}
