package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level03Pursuit;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.LevelResult;
import org.example.timeloop.ui.Level03ObjectiveViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关装配（{@link Level03Assembly}）的机关 / 射线 / 目标投影集成测试。
 *
 * <p>覆盖四件事：</p>
 * <ol>
 *   <li><b>渲染投影</b>：4 个 {@code PLATE} + 3 个 {@code DOOR} + 1 个 {@code EXIT}；终点格
 *       （{@code L03_door_exit} 与出口同格）<b>只</b>投影一个 {@code EXIT}，不叠一个 {@code DOOR}；</li>
 *   <li><b>射线随共享 {@code roundTick} 推进</b>：逐刻把 {@link Ray} 的权威状态与
 *       {@code roundTick % RAY_CYCLE_TICKS} 算出的期望状态比对（刻 552 必须 ACTIVE，
 *       刻 551 / 612 等周期其余段落必须非 ACTIVE）—— 若装配漏掉 {@code RayFactory.updateAll}
 *       或另起计时器，这条会先红；</li>
 *   <li><b>{@code objectiveView()} 在真实玩法状态下不得抛异常</b>：玩家已在 B 支路而门 A 早已回锁
 *       （{@code doorAOpen=false && inBranchB=true}）是第三关必然出现的合法局面，
 *       这里用**真驾驶**走到分岔口 J 来复现，而不是拼一个 VM 参数；</li>
 *   <li><b>官方解的一条端到端链路</b>：B 板 / C 板由残影压住（门 B / 门 C 开）→ 玩家真开到出口旁
 *       → D 板供能（终点闸解锁）→ 真按 {@code E} → {@code RESULT}。</li>
 * </ol>
 *
 * <p>驾驶脚本不手抄地图路线：{@link #route} 用 {@link Level03Pursuit#isOpen} 现算 BFS 最短格子路线
 * （每格 {@link Level03Pursuit#TICKS_PER_TILE} 刻），与 {@code Level02AssemblyTest} 同一做法。</p>
 */
class Level03AssemblyTest {

    private static final double TILE = Level03Pursuit.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level03Pursuit.TICKS_PER_TILE;
    private static final String PLATE_D = Level03Pursuit.PLATE_D;
    private static final String NODE_DOOR_B = Level03Pursuit.NODE_DOOR_B;
    private static final String NODE_DOOR_C = Level03Pursuit.NODE_DOOR_C;

    /** 寻路用的方向顺序（固定顺序 → 脚本可复现）。 */
    private static final List<LogicalKey> MOVE_ORDER = List.of(
            LogicalKey.DIR_UP, LogicalKey.DIR_DOWN, LogicalKey.DIR_LEFT, LogicalKey.DIR_RIGHT);
    private static final Map<LogicalKey, int[]> MOVE_DELTA = Map.of(
            LogicalKey.DIR_UP, new int[] {0, -1},
            LogicalKey.DIR_DOWN, new int[] {0, 1},
            LogicalKey.DIR_LEFT, new int[] {-1, 0},
            LogicalKey.DIR_RIGHT, new int[] {1, 0});

    private Level03Assembly assembly;

    @AfterEach
    void cleanupAssembly() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
    }

    // ---------- 1. 渲染投影 ----------

    @Test
    void renderProjectionHasFourPlatesThreeDoorsAndOneExitAtTheExitCell() {
        Level03Assembly a = started();
        List<RenderViews.Mechanism> mechanisms = a.renderViews().mechanisms();
        assertEquals(8, mechanisms.size(), "4 板 + 3 门 + 1 终点");

        Map<String, RenderViews.MechanismKind> kindById = new HashMap<>();
        int plates = 0;
        int doors = 0;
        int exits = 0;
        for (RenderViews.Mechanism m : mechanisms) {
            kindById.put(m.id(), m.kind());
            switch (m.kind()) {
                case PLATE -> plates++;
                case DOOR -> doors++;
                case EXIT -> exits++;
                case SWITCH -> throw new AssertionError("第三关没有锁存开关，不得投影 SWITCH: " + m.id());
            }
        }
        assertEquals(4, plates, "L3 四块板都是普通驻留板（role 不存在 → latching=false）");
        assertEquals(3, doors, "门 A / 门 B / 门 C 三扇普通门");
        assertEquals(1, exits, "终点格只投影一个 EXIT");

        for (String plateId : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_D)) {
            assertEquals(RenderViews.MechanismKind.PLATE, kindById.get(plateId), plateId + " 应投影成 PLATE");
        }
        for (String doorId : List.of(Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B, Level03Pursuit.DOOR_C)) {
            assertEquals(RenderViews.MechanismKind.DOOR, kindById.get(doorId), doorId + " 应投影成 DOOR");
        }
        assertEquals(RenderViews.MechanismKind.EXIT, kindById.get(Level03Pursuit.EXIT));
        assertFalse(kindById.containsKey(Level03Pursuit.DOOR_EXIT),
                "终点供能闸与出口同格，不得再投影一个 DOOR（同格叠画）");
        assertNullTagAndGateGroup(mechanisms);

        // 终点格：只有一个 EXIT，且坐标就是出口格中心；该格没有任何 DOOR。
        Vector2D exitCell = Level03Pursuit.cellCenter(
                Level03Pursuit.CELL_EXIT[0], Level03Pursuit.CELL_EXIT[1]);
        RenderViews.Mechanism exitView = mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.EXIT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("渲染投影缺少 EXIT"));
        assertEquals(Level03Pursuit.EXIT, exitView.id());
        assertEquals(exitCell.x(), exitView.x(), 1e-9);
        assertEquals(exitCell.y(), exitView.y(), 1e-9);
        assertFalse(mechanisms.stream().anyMatch(m -> m.kind() == RenderViews.MechanismKind.DOOR
                        && Math.abs(m.x() - exitCell.x()) < 1e-9 && Math.abs(m.y() - exitCell.y()) < 1e-9),
                "终点格只能有一个 EXIT，不得同时叠一个 DOOR");
        assertFalse(exitView.active(), "开局 D 板无人 → 终点闸未解锁 → EXIT 未激活");

        // 门格上的 DOOR 坐标 = 门所在格中心（画在门口而不是别处）。
        RenderViews.Mechanism doorA = mechanismOf(mechanisms, Level03Pursuit.DOOR_A);
        assertEquals(Level03Pursuit.cellCenter(
                        Level03Pursuit.CELL_DOOR_A[0], Level03Pursuit.CELL_DOOR_A[1]).x(),
                doorA.x(), 1e-9);
        assertFalse(doorA.active(), "开局没人压 A 板 → 门 A 锁着");
    }

    private static void assertNullTagAndGateGroup(List<RenderViews.Mechanism> mechanisms) {
        for (RenderViews.Mechanism m : mechanisms) {
            if (m.tag() != null) {
                throw new AssertionError("第三关没有角标需求，tag 必须是 null: " + m.id());
            }
            if (m.gateGroup()) {
                throw new AssertionError("第三关没有组色需求，gateGroup 必须是 false: " + m.id());
            }
        }
    }

    private static RenderViews.Mechanism mechanismOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanisms.stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("渲染投影缺少机关: " + id));
    }

    // ---------- 2. 射线随共享 roundTick 推进 ----------

    @Test
    void rayStateFollowsTheSharedRoundTickAtEveryTick() {
        Level03Assembly a = started();
        assertEquals(1, a.rays().size(), "第三关只有一束射线（L03_ray_b）");
        assertEquals(Level03Pursuit.RAY_B, a.rays().get(0).getId());

        a.tick(InputIntent.empty(0L)); // 首个方向输入出现前不推进逻辑刻
        a.tick(press(1L, LogicalKey.DIR_UP));

        // 逐刻比对：装配每逻辑刻用共享 roundTick 驱动射线，因此
        // 「刻 T 观察到的状态」= Ray.update(T-1) 的结果（tick 先 updateAll(tick) 再 advance）。
        // 若装配漏掉 updateAll 或另起计时器，这条会先红。
        long tick = 1L;
        while (tick < Level03Pursuit.DURATION_TICKS) {
            if (!a.isPlaying()) {
                throw new AssertionError("第 1 轮在刻 " + tick + " 之前就结束了，射线断言不成立");
            }
            long roundTick = a.hudContext().roundTick();
            assertEquals(expectedRayActive(roundTick - 1L), a.isRayActive(),
                    "刻 " + roundTick + " 的射线状态必须由共享 roundTick 决定");
            a.tick(InputIntent.empty(tick + 1));
            tick++;
        }

        // 关键刻（夹具前提：ACTIVE 起点 552、周期 612）逐点钉死，避免整段循环「恰好自洽」。
        assertEquals(552L, Level03Pursuit.RAY_ACTIVE_START_TICK);
        assertEquals(612L, Level03Pursuit.RAY_CYCLE_TICKS);
        assertTrue(expectedRayActive(552L), "update(552) 必须落在 ACTIVE 段内（刻表前提）");
        assertFalse(expectedRayActive(551L), "update(551) 还在预警段，不是 ACTIVE");
        assertFalse(expectedRayActive(612L), "刻 612 是下一周期起点，不再是 ACTIVE");
        assertFalse(expectedRayActive(191L), "刻 191 在 OFF 段，不是 ACTIVE");
    }

    /**
     * 与 {@link Level03Pursuit} 常量等价、但独立算出的期望状态（不在生产代码里复用同一表达式）。
     *
     * @param drivenTick 装配对该刻调用 {@code Ray.update(drivenTick)} 时落在哪个状态段
     */
    private static boolean expectedRayActive(long drivenTick) {
        long cycleTick = drivenTick % Level03Pursuit.RAY_CYCLE_TICKS;
        return cycleTick >= Level03Pursuit.RAY_ACTIVE_START_TICK
                && cycleTick < Level03Pursuit.RAY_ACTIVE_START_TICK + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS;
    }

    // ---------- 3. objectiveView() 在「已进 B 支路、门 A 已回锁」时不得抛异常 ----------

    @Test
    void objectiveViewStaysValidWhilePlayerIsInBranchBWithDoorAAlreadyRelocked() {
        Level03Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 真驾驶要过门 A：用残影占用 A 板把门 A 压开（该板是普通板，有人压着才开）。
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板被压 → 门 A 开");

        // 真开到分岔口 J（18 格 = 432 刻，与 SPAWN_TO_PLATE_A_TICKS + DOOR_A_TO_FORK_TICKS 一致）。
        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_J,
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_EXIT,
                        NODE_DOOR_B, NODE_DOOR_C));
        assertEquals(432L, a.hudContext().roundTick(),
                "按刻表，出生点 → 门 A（14 格）→ J（4 格）= 18 格 = 432 刻");
        assertTrue(a.inBranchB(), "J(17,10) 属于 B 支路集合");

        // 关键局面：门 A 此刻已经回锁（刻 432 > 窗口结束 384），而玩家已经在 B 支路里。
        a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryExit("echo_1", 1, tick);
        assertFalse(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板一松 → 门 A 回锁");

        Level03ObjectiveViewModel atFork = a.objectiveView();   // 修掉假不变量前：这里抛 IllegalArgumentException
        assertFalse(atFork.doorAOpen(), "门 A 已回锁");
        assertTrue(atFork.inBranchB(), "玩家确实已经在 B 支路里");
        assertFalse(atFork.rayActive(), "刻 432 射线尚未 ACTIVE");
        assertTrue(atFork.text().contains("沿 B 支路往上走"), atFork.text());

        // 再推进到射线 ACTIVE 那一刻：提示切成「按 Space 下潜」，投影仍然合法。
        // 装配先 updateAll(roundTick) 再 advance，因此刻 552 的 update 结果在刻 553 才可见。
        while (!a.isRayActive()) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(553L, a.hudContext().roundTick(), "射线在刻 553 才可观察到 ACTIVE（见射线刻表口径）");
        Level03ObjectiveViewModel atRay = a.objectiveView();
        assertTrue(atRay.rayActive(), "刻 553 射线 ACTIVE");
        assertTrue(atRay.inBranchB(), "玩家仍在 B 支路");
        assertFalse(atRay.doorAOpen(), "门 A 仍然是锁的");
        assertTrue(atRay.text().contains("Space"), atRay.text());
        assertTrue(atRay.text().contains("下潜"), atRay.text());
    }

    // ---------- 4. 官方解的一条端到端链路 ----------

    /**
     * 官方解链路：残影压开 B 板 / C 板 → 玩家真开到出口旁 → 按 {@code E} 无效（D 板未供能）
     * → D 板供能（终点供能闸解锁）→ 按 {@code E} 通关。
     *
     * <p>地图与通行性约束（不是测试取巧）：出口格 {@code (25,6)} 与终点供能闸 {@code L03_door_exit}
     * 同格，而 {@code isPassable} 与 L1 / L2 同构地<b>挡住未解锁的门格</b>，所以供能之前玩家进不了出口格
     * ——他停在正下方的 {@code (25,7)}（距出口 1 格 = 48 &lt; 宽容半径 72，交互照常生效），
     * 等到 D 板供能后再按 {@code E}。这正是刻表 {@code PLATE_D_ARRIVAL(1056) > EXIT_ARRIVAL(840)} 的
     * 设计意图：玩家要在出口旁<b>等</b>供能。</p>
     */
    @Test
    void branchBAndCBeyondEchoesPlusPoweredExitLetThePlayerClearTheLevel() {
        Level03Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 官方解：第二轮玩家驻留 B 板到轮末 → 第三轮门 B 由 E₂ 开；E₁ 在 C 板上撑开 C 窗口。
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_B).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_C).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_B).orElseThrow().isUnlocked(), "B 板被压 → 门 B 开");
        assertTrue(a.door(Level03Pursuit.DOOR_C).orElseThrow().isUnlocked(), "C 板被压 → 门 C 开");
        // 门 A 是内区唯一入口，玩家要真开进去就必须有人压着 A 板（刻表上 E₁ 的 A 窗口是 336..384）。
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板被压 → 门 A 开");

        // 真开到出口正下方 (25,7)：门 A 之外的控制线与 J 右侧主通道都走得通；
        // 寻路避开四块板格 —— 顺路压上 D 板会掩盖「D 板供能」这一步。
        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.nodeId(25, 7),
                allPlateCells());
        assertEquals(cellX(25), player(a).x(), 1e-9);
        assertEquals(cellY(7), player(a).y(), 1e-9);

        // 终点供能闸锁着 → 进不了出口格（与 L1 / L2「门格未解锁不可通行」同一条规则）。
        assertFalse(a.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(), "D 板无人 → 终点闸锁着");
        assertFalse(a.exitTerminal().isDoorUnlocked(), "出口终端未被武装");
        tick = drive(a, tick, LogicalKey.DIR_UP);
        assertEquals(cellY(7), player(a).y(), 1e-9, "终点闸锁着时不得进入出口格 (25,6)");

        // 只按 E 也不够：闸没解锁 → 不结算。
        a.tick(pressKey(tick++, LogicalKey.INTERACT));
        assertEquals(GamePhase.PLAYING, a.phase(), "闸没解锁时按 E 不得通关");
        assertFalse(a.exitTerminal().isTriggered());

        // E₁ 驻留 D 板（刻表 1056）→ 终点闸解锁 → 出口武装，目标提示同步改口。
        assertTrue(a.dockingPlate(PLATE_D).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(), "D 板被压 → 终点闸解锁");
        assertTrue(a.exitTerminal().isDoorUnlocked(), "出口终端随终点闸一起武装");
        assertTrue(a.objectiveView().exitPowered());
        assertEquals("出口已供能：到出口旁按 E 通关", a.objectiveView().text());

        // 闸解锁后同一按键即可走进出口格，并在半径内按 E → RESULT。
        tick = drive(a, tick, LogicalKey.DIR_UP);
        assertEquals(cellY(6), player(a).y(), 1e-9, "终点闸解锁后应允许进入出口格 (25,6)");
        a.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, a.phase(), "闸门解锁后在半径内按 E 应通关");
        assertTrue(a.isFinalPhase());
        LevelResult result = a.result().orElseThrow(() -> new AssertionError("通关后必须有结算投影"));
        assertTrue(result.cleared(), "结算应为通关");
        assertEquals(LevelFlow.LevelId.LEVEL_03.title(), result.levelName(),
                "RecordingSession 的关卡名必须与当前 LevelId 一致");
        assertEquals(Level03Pursuit.MAX_ROUNDS, result.maxRounds());
    }

    /** 本关四块驻留板所在节点（寻路时避开，避免玩家顺路把自己压上某块板）。 */
    private static Set<String> allPlateCells() {
        return Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_B,
                Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_D);
    }

    // ---------- 驾驶与寻路 ----------

    private Level03Assembly started() {
        assembly = new Level03Assembly();
        assembly.start();
        assertTrue(assembly.isPlaying());
        return assembly;
    }

    private static RenderViews.Player player(Level03Assembly a) {
        return a.renderViews().player();
    }

    /** 直线推进一格（24 刻）：首刻为新按下边沿，其余为按住。 */
    private static long drive(Level03Assembly a, long tick, LogicalKey key) {
        a.tick(press(tick++, key));
        for (long i = 1; i < TICKS_PER_TILE; i++) {
            a.tick(hold(tick++, key));
        }
        return tick;
    }

    /** 从 {@code fromNodeId} 沿 BFS 最短路线开到 {@code toNodeId}。 */
    private static long driveTo(Level03Assembly a, long tick, String fromNodeId, String toNodeId,
                                Set<String> avoidNodeIds) {
        for (LogicalKey key : route(fromNodeId, toNodeId, avoidNodeIds)) {
            tick = drive(a, tick, key);
        }
        return tick;
    }

    /**
     * 用关卡数据现算一条最短格子路线（BFS，四方向）。
     *
     * @param avoidNodeIds 寻路时不经过的节点（例如已由残影压住的板格、当前锁着的门格）
     */
    private static List<LogicalKey> route(String fromNodeId, String toNodeId, Set<String> avoidNodeIds) {
        Map<String, String> cameFrom = new HashMap<>();
        Map<String, LogicalKey> cameBy = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        cameFrom.put(fromNodeId, null);
        queue.add(fromNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toNodeId)) {
                break;
            }
            int[] cell = cellOf(current);
            for (LogicalKey key : MOVE_ORDER) {
                int[] delta = MOVE_DELTA.get(key);
                int col = cell[0] + delta[0];
                int row = cell[1] + delta[1];
                if (!Level03Pursuit.isOpen(col, row)) {
                    continue;
                }
                String next = Level03Pursuit.nodeId(col, row);
                if (avoidNodeIds.contains(next) || cameFrom.containsKey(next)) {
                    continue;
                }
                cameFrom.put(next, current);
                cameBy.put(next, key);
                queue.add(next);
            }
        }
        if (!cameFrom.containsKey(toNodeId)) {
            throw new IllegalStateException(
                    "测试路线不可达: " + fromNodeId + " -> " + toNodeId + " avoid=" + avoidNodeIds);
        }
        LinkedList<LogicalKey> path = new LinkedList<>();
        for (String node = toNodeId; !node.equals(fromNodeId); node = cameFrom.get(node)) {
            path.addFirst(cameBy.get(node));
        }
        return path;
    }

    /** 节点 ID → 格坐标 {col, row}（由关卡数据的节点世界坐标反推，不硬编码）。 */
    private static int[] cellOf(String nodeId) {
        for (PathNode node : Level03Pursuit.build().getPathNodes()) {
            if (node.getId().equals(nodeId)) {
                return new int[] {
                        (int) Math.round(node.getWorldPos().x() / TILE - 0.5),
                        (int) Math.round(node.getWorldPos().y() / TILE - 0.5)};
            }
        }
        throw new IllegalArgumentException("未知节点: " + nodeId);
    }

    private static double cellX(int col) {
        return (col + 0.5) * TILE;
    }

    private static double cellY(int row) {
        return (row + 0.5) * TILE;
    }

    private static InputIntent press(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(directionOf(key)));
    }

    private static InputIntent hold(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(key), List.of());
    }

    /** 非方向键（如 E）的新按下：无方向边沿。 */
    private static InputIntent pressKey(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of());
    }

    private static Direction directionOf(LogicalKey key) {
        return switch (key) {
            case DIR_UP -> Direction.UP;
            case DIR_DOWN -> Direction.DOWN;
            case DIR_LEFT -> Direction.LEFT;
            case DIR_RIGHT -> Direction.RIGHT;
            default -> throw new IllegalArgumentException("不是方向键: " + key);
        };
    }
}
