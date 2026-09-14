package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.render.RenderViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * 第二关装配（{@link Level02Assembly}）的机关 / 门 / 通行性集成测试。
 *
 * <p>覆盖四件事：</p>
 * <ol>
 *   <li><b>普通驻留板实时性</b>：站上板 → 门立刻开；离开板 → 门立刻回锁（第二关五块板都不锁存，
 *       与第一关右板的 {@code role=switch} 锁存语义不同）；</li>
 *   <li><b>多门通行性</b>：三扇门各自挡住自己的门格，解锁后放行，且互不影响
 *       （{@code Level01Assembly.isPassable} 只查一扇门，这里必须逐门检查）；</li>
 *   <li><b>终点闸三条件</b>：内板 + 主板 + 终结板<b>同刻</b>被占才解锁，任一块释放立刻回锁（门格上的
 *       {@code Door} 状态实时回锁；出口终端的「已解锁」按机制契约在本轮内保持，轮末 {@code reset()} 归零）；</li>
 *   <li><b>渲染投影</b>：5 板 + 2 门 + 1 终点；终点闸与出口同格，该格只投影 {@code EXIT}。</li>
 * </ol>
 *
 * <p>驾驶脚本不手抄地图：{@link #route} 用 {@link Level02Corridor#isOpen} 现算 BFS 最短格子路线
 * （每格 {@link Level02Corridor#TICKS_PER_TILE} 刻），因此地图改版后脚本自动跟着走。</p>
 */
class Level02AssemblyTest {

    private static final double TILE = Level02Corridor.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level02Corridor.TICKS_PER_TILE;

    /** 寻路用的方向顺序（固定顺序 → 脚本可复现）。 */
    private static final List<LogicalKey> MOVE_ORDER = List.of(
            LogicalKey.DIR_UP, LogicalKey.DIR_DOWN, LogicalKey.DIR_LEFT, LogicalKey.DIR_RIGHT);
    private static final Map<LogicalKey, int[]> MOVE_DELTA = Map.of(
            LogicalKey.DIR_UP, new int[] {0, -1},
            LogicalKey.DIR_DOWN, new int[] {0, 1},
            LogicalKey.DIR_LEFT, new int[] {-1, 0},
            LogicalKey.DIR_RIGHT, new int[] {1, 0});

    private Level02Assembly assembly;

    @AfterEach
    void cleanupAssembly() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
    }

    // ---------- 1. 普通驻留板：实时开 / 实时回锁 ----------

    @Test
    void plainPlateOpensItsDoorWhileHeldAndRelocksRightAfterLeaving() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++)); // 首个方向输入出现前不推进逻辑刻

        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_GATE, Set.of());
        tick = parkOnDock(a, tick); // 抵达机关中心后停驻（同 L1 测试脚本）

        RenderViews.Player docked = player(a);
        assertEquals(MovementState.DOCKED, docked.movementState(), "应在外闸板上停驻");
        assertEquals(cellX(3), docked.x(), 1e-9);
        assertEquals(cellY(5), docked.y(), 1e-9);
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_GATE), "玩家压着外闸板");
        Door gateDoor = a.door(Level02Corridor.DOOR_GATE).orElseThrow();
        assertTrue(gateDoor.isUnlocked(), "普通驻留板：踩住即开闸");

        // 离开板格 (3,5) → (2,5)：普通板离开即释放，闸门立刻回锁
        tick = drive(a, tick, LogicalKey.DIR_LEFT);
        assertEquals(cellX(2), player(a).x(), 1e-9);
        assertFalse(a.isPlateOccupied(Level02Corridor.PLATE_GATE), "离开板格后不得残留占用");
        assertFalse(gateDoor.isUnlocked(), "离开驻留板 → 闸门立刻回锁（不锁存）");
    }

    @Test
    void relayPlateOpensRelayDoorInRealTimeAndDoorsStayIndependent() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // E1（测试以机制 API 注入的残影占用）压住外闸板 → 外闸保持开启
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow()
                .tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level02Corridor.DOOR_GATE).orElseThrow().isUnlocked());
        // 残影自始至终没碰中继板 → 内室门应保持锁着
        assertFalse(a.door(Level02Corridor.DOOR_RELAY).orElseThrow().isUnlocked());

        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_RELAY,
                Set.of(Level02Corridor.NODE_DOOR_RELAY, Level02Corridor.NODE_EXIT));
        tick = parkOnDock(a, tick);

        assertEquals(MovementState.DOCKED, player(a).movementState(), "应在中继板上停驻");
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_RELAY));
        assertTrue(a.door(Level02Corridor.DOOR_RELAY).orElseThrow().isUnlocked(),
                "踩住中继板 → 内室门开");
        assertTrue(a.door(Level02Corridor.DOOR_GATE).orElseThrow().isUnlocked(),
                "外闸仍由 E1 压着 → 两扇门互不影响");
        assertFalse(a.door(Level02Corridor.DOOR_EXIT).orElseThrow().isUnlocked(),
                "终点闸与另两扇门无关，仍锁着");

        // 离开中继板 (18,11) → (19,11)
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(19), player(a).x(), 1e-9);
        assertFalse(a.isPlateOccupied(Level02Corridor.PLATE_RELAY), "普通板离开即释放");
        assertFalse(a.door(Level02Corridor.DOOR_RELAY).orElseThrow().isUnlocked(),
                "中继板一松 → 内室门立刻回锁");
        assertTrue(a.door(Level02Corridor.DOOR_GATE).orElseThrow().isUnlocked(),
                "外闸不受影响");
    }

    // ---------- 2. 多门通行性：门格挡路 / 解锁放行 ----------

    @Test
    void everyDoorBlocksItsOwnCellUntilItsPlateIsHeld() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // (1) 外闸锁着：走到门外一格 (11,11)，再按 RIGHT 也进不去门格 (12,11)
        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.nodeId(11, 11),
                allDoorCells());
        assertEquals(cellX(11), player(a).x(), 1e-9);
        assertEquals(cellY(11), player(a).y(), 1e-9);
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(11), player(a).x(), 1e-9, "外闸锁着时不得进入门格 (12,11)");
        assertEquals(cellY(11), player(a).y(), 1e-9);

        // (2) E1 压住外闸板 → 外闸解锁 → 同一按键即可进门格
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level02Corridor.DOOR_GATE).orElseThrow().isUnlocked());
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(12), player(a).x(), 1e-9, "外闸解锁后应允许进入门格 (12,11)");
        assertEquals(cellY(11), player(a).y(), 1e-9);

        // (3) 内室门锁着：绕到门外一格 (21,6)（寻路避开中继板格，玩家不会顺路把自己压上去）
        Set<String> avoid = Set.of(Level02Corridor.NODE_PLATE_RELAY,
                Level02Corridor.NODE_DOOR_RELAY, Level02Corridor.NODE_EXIT);
        tick = driveTo(a, tick, Level02Corridor.nodeId(12, 11), Level02Corridor.nodeId(21, 6), avoid);
        assertEquals(cellX(21), player(a).x(), 1e-9);
        assertEquals(cellY(6), player(a).y(), 1e-9);
        assertFalse(a.door(Level02Corridor.DOOR_RELAY).orElseThrow().isUnlocked(),
                "没人压中继板 → 内室门锁着");
        tick = drive(a, tick, LogicalKey.DIR_UP);
        assertEquals(cellY(6), player(a).y(), 1e-9, "内室门锁着时不得进入门格 (21,5)");

        // (4) E2 压住中继板 → 内室门解锁 → 放行；外闸仍开（两门互不影响）
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.door(Level02Corridor.DOOR_RELAY).orElseThrow().isUnlocked());
        tick = drive(a, tick, LogicalKey.DIR_UP);
        assertEquals(cellY(5), player(a).y(), 1e-9, "内室门解锁后应允许进入门格 (21,5)");
        assertTrue(a.door(Level02Corridor.DOOR_GATE).orElseThrow().isUnlocked());
        assertFalse(a.door(Level02Corridor.DOOR_EXIT).orElseThrow().isUnlocked(),
                "终点闸不受外闸 / 内室门影响");
    }

    // ---------- 3. 终点闸三条件 ----------

    @Test
    void exitGateRequiresInnerMainAndCorePlatesAtTheSameTime() {
        Level02Assembly a = started();
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        ExitTerminal exit = a.exitTerminal();

        assertFalse(exitGate.isUnlocked(), "开局终点闸锁着");
        assertFalse(exit.interact(10L, 0), "闸门锁着时 E 不得结算");

        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow().tryEnter("echo_2", 2, 100L));
        assertFalse(exitGate.isUnlocked(), "只有内板 → 终点闸仍锁");

        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, 200L));
        assertFalse(exitGate.isUnlocked(), "内板 + 主板 → 终点闸仍锁（还差终结板）");

        assertTrue(a.dockingPlate(Level02Corridor.PLATE_CORE).orElseThrow().tryEnter("player", 0, 300L));
        assertTrue(exitGate.isUnlocked(), "三块板同刻被占 → 终点闸解锁");
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_INNER));
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_MAIN));
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_CORE));
        assertTrue(exit.isDoorUnlocked(), "出口终端随终点闸一起武装");
        assertTrue(exit.interact(400L, 0), "闸门解锁后 interact 应成功");

        // 门格通行性实时回锁：任一块板释放 → Door 立刻回到 LOCKED
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryExit("echo_1", 1, 500L));
        assertFalse(exitGate.isUnlocked(), "主板一松 → 终点闸门格立刻回锁");
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, 600L));
        assertTrue(exitGate.isUnlocked(), "主板重新被占 → 终点闸再次解锁");
    }

    // ---------- 4. 真·驾驶：踩上终结板解锁终点闸 → 按 E 通关 ----------

    @Test
    void dockingCoreUnlocksExitGateAndPressingInteractClearsTheLevel() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // E1 压外闸板、E2 压中继板（过两扇门），另 E1/E2 同时压住主板与内板（终点闸三缺一）
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow().tryEnter("echo_2", 2, tick));
        assertFalse(a.door(Level02Corridor.DOOR_EXIT).orElseThrow().isUnlocked());

        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_CORE,
                Set.of(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_RELAY,
                        Level02Corridor.NODE_PLATE_INNER, Level02Corridor.NODE_PLATE_MAIN,
                        Level02Corridor.NODE_EXIT));
        tick = parkOnDock(a, tick);

        assertEquals(MovementState.DOCKED, player(a).movementState(), "应停在终结板上");
        assertEquals(cellX(24), player(a).x(), 1e-9);
        assertEquals(cellY(2), player(a).y(), 1e-9);
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_CORE), "终结板由当前玩家压住");
        assertTrue(a.door(Level02Corridor.DOOR_EXIT).orElseThrow().isUnlocked(),
                "内板 + 主板 + 终结板同刻被占 → 终点闸解锁");

        // 终结板 (24,2) 距出口 (25,2) 恰好 1 格 = 48 ≤ 72 宽容半径 → 原地按 E 结算
        a.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, a.phase(), "闸门解锁后在半径内按 E 应通关");
    }

    @Test
    void exitGateCellIsBlockedWhileLockedAndPermittedRightAfterUnlock() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 只开两扇过路门（外闸 + 内室门）；终点闸三块板暂时无人压
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow().tryEnter("echo_2", 2, tick));

        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_CORE,
                Set.of(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_RELAY,
                        Level02Corridor.NODE_PLATE_INNER, Level02Corridor.NODE_PLATE_MAIN,
                        Level02Corridor.NODE_EXIT));
        tick = parkOnDock(a, tick);

        // 只有终结板被占 → 终点闸锁着 → 门格 (25,2) 不可进入
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_CORE));
        assertFalse(exitGate.isUnlocked(), "只有终结板 → 终点闸锁着");
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(24), player(a).x(), 1e-9, "终点闸锁着时不得进入门格 (25,2)");
        // 这次「想进门格」的尝试已经让玩家离开终结板（离开即释放占用）
        assertFalse(a.isPlateOccupied(Level02Corridor.PLATE_CORE));

        // 走开一格再回来 → 重新压住终结板（机关占用由真实停驻恢复）
        tick = drive(a, tick, LogicalKey.DIR_LEFT);
        assertEquals(cellX(23), player(a).x(), 1e-9);
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        tick = parkOnDock(a, tick);
        assertEquals(cellX(24), player(a).x(), 1e-9);
        assertEquals(MovementState.DOCKED, player(a).movementState());
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_CORE), "回到终结板应重新压住它");
        assertFalse(exitGate.isUnlocked(), "只补上终结板 → 终点闸仍锁（还差内板 + 主板）");

        // 补上内板 + 主板 → 终点闸解锁（实时）→ 同一按键即可穿过门格
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(exitGate.isUnlocked(), "三块板齐备 → 终点闸解锁");
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(25), player(a).x(), 1e-9, "终点闸解锁后应允许进入门格 (25,2)");

        // 离开终结板（该次离板发生在同一刻的通行判定之后）→ 门格立刻回锁
        assertFalse(a.isPlateOccupied(Level02Corridor.PLATE_CORE), "离开终结板即释放占用");
        assertFalse(exitGate.isUnlocked(), "终结板一松 → 终点闸立刻回锁");
    }

    // ---------- 5. 渲染投影 ----------

    @Test
    void renderProjectionHasFivePlatesTwoDoorsAndOneExitAtTheExitCell() {
        Level02Assembly a = started();
        List<RenderViews.Mechanism> mechanisms = a.renderViews().mechanisms();

        Map<RenderViews.MechanismKind, List<String>> idsByKind = new HashMap<>();
        for (RenderViews.Mechanism m : mechanisms) {
            idsByKind.computeIfAbsent(m.kind(), k -> new ArrayList<>()).add(m.id());
        }
        assertEquals(8, mechanisms.size(), "5 板 + 2 门 + 1 终点");
        assertEquals(Set.of(Level02Corridor.PLATE_GATE, Level02Corridor.PLATE_RELAY,
                        Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN,
                        Level02Corridor.PLATE_CORE),
                Set.copyOf(idsByKind.get(RenderViews.MechanismKind.PLATE)));
        assertEquals(Set.of(Level02Corridor.DOOR_GATE, Level02Corridor.DOOR_RELAY),
                Set.copyOf(idsByKind.get(RenderViews.MechanismKind.DOOR)),
                "只投影两扇普通门；终点闸与出口同格，不投影 DOOR");
        assertEquals(List.of(Level02Corridor.EXIT), idsByKind.get(RenderViews.MechanismKind.EXIT));

        Vector2D exitCell = Level02Corridor.cellCenter(25, 2);
        RenderViews.Mechanism exit = mechanisms.stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.EXIT)
                .findFirst()
                .orElseThrow();
        assertEquals(exitCell.x(), exit.x(), 1e-9);
        assertEquals(exitCell.y(), exit.y(), 1e-9);
        assertFalse(mechanisms.stream().anyMatch(m -> m.kind() == RenderViews.MechanismKind.DOOR
                        && Math.abs(m.x() - exitCell.x()) < 1e-9 && Math.abs(m.y() - exitCell.y()) < 1e-9),
                "终点格只能有一个 EXIT，不得同时叠一个 DOOR");
        assertFalse(exit.active(), "开局终点闸未解锁 → EXIT 未激活");
    }

    // ---------- 驾驶与寻路 ----------

    private Level02Assembly started() {
        assembly = new Level02Assembly();
        assembly.start();
        assertTrue(assembly.isPlaying());
        return assembly;
    }

    private static RenderViews.Player player(Level02Assembly a) {
        return a.renderViews().player();
    }

    /** 直线推进一格（24 刻）：首刻为新按下边沿，其余为按住。 */
    private static long drive(Level02Assembly a, long tick, LogicalKey key) {
        a.tick(press(tick++, key));
        for (long i = 1; i < TICKS_PER_TILE; i++) {
            a.tick(hold(tick++, key));
        }
        return tick;
    }

    /**
     * 抵达机关所在格后松开按键停驻（同 L1 测试脚本的「停驻」三刻）。
     *
     * <p>抵达格中心的那一逻辑刻仍在「补齐到中心」的推进分支里，本刻运动状态是 CRUISING；
     * 松手后的下一刻 C3 给出 FREEZE，装配才会投影 DOCKED。</p>
     */
    private static long parkOnDock(Level02Assembly a, long tick) {
        for (int i = 0; i < 3; i++) {
            a.tick(InputIntent.empty(tick++));
        }
        return tick;
    }

    /** 从 {@code fromNodeId} 沿 BFS 最短路线开到 {@code toNodeId}。 */
    private static long driveTo(Level02Assembly a, long tick, String fromNodeId, String toNodeId,
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
                if (!Level02Corridor.isOpen(col, row)) {
                    continue;
                }
                String next = Level02Corridor.nodeId(col, row);
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
        for (PathNode node : Level02Corridor.build().getPathNodes()) {
            if (node.getId().equals(nodeId)) {
                return new int[] {
                        (int) Math.round(node.getWorldPos().x() / TILE - 0.5),
                        (int) Math.round(node.getWorldPos().y() / TILE - 0.5)};
            }
        }
        throw new IllegalArgumentException("未知节点: " + nodeId);
    }

    private static Set<String> allDoorCells() {
        return Set.of(Level02Corridor.NODE_DOOR_GATE, Level02Corridor.NODE_DOOR_RELAY,
                Level02Corridor.NODE_EXIT);
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
