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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二关装配（{@link Level02Assembly}）的机关 / 门 / 通行性集成测试。
 *
 * <p>覆盖五件事：</p>
 * <ol>
 *   <li><b>普通驻留板实时性</b>：站上板 → 门立刻开；离开板 → 门立刻回锁（第二关四块普通板都不锁存，
 *       与第一关右板 / 本关 {@code L02_plate_switch} 的 {@code role=switch} 锁存语义不同）；</li>
 *   <li><b>多门通行性</b>：三扇门各自挡住自己的门格，解锁后放行，且互不影响
 *       （{@code Level01Assembly.isPassable} 只查一扇门，这里必须逐门检查）；</li>
 *   <li><b>终点闸三条件</b>：内板 + 主板 + <b>锁存开关</b>同时成立才解锁；内板 / 主板一松立刻回锁，
 *       而开关一旦踩过（锁存）就<b>不需要有人压着</b>；没踩过开关时即使内板 + 主板被占也必须保持锁定
 *       （反例，见 {@link #exitGateStaysLockedWhileTheSwitchWasNeverStepped()}）；</li>
 *   <li><b>真·驾驶通关链路</b>：玩家真开到开关格踩下、离开（锁存保留）→ 走到出口旁按 E → {@code RESULT}；</li>
 *   <li><b>渲染投影</b>：4 板 + 1 开关 + 2 门 + 1 终点；终点闸与出口同格，该格只投影 {@code EXIT}。</li>
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

        // 离开中继板 (17,9) → (18,9)
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(18), player(a).x(), 1e-9);
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
    void exitGateRequiresInnerMainAndSwitchAtTheSameTime() {
        Level02Assembly a = started();
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        ExitTerminal exit = a.exitTerminal();
        DockingPlate switchPlate = a.dockingPlate(Level02Corridor.PLATE_SWITCH).orElseThrow();

        assertFalse(exitGate.isUnlocked(), "开局终点闸锁着");
        assertFalse(exit.interact(10L, 0), "闸门锁着时 E 不得结算");

        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow().tryEnter("echo_2", 2, 100L));
        assertFalse(exitGate.isUnlocked(), "只有内板 → 终点闸仍锁");

        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, 200L));
        assertFalse(exitGate.isUnlocked(), "内板 + 主板 → 终点闸仍锁（还差开关）");

        assertTrue(switchPlate.tryEnter("player", 0, 300L));
        assertTrue(switchPlate.isLatched(), "踩上开关即锁存");
        assertTrue(exitGate.isUnlocked(), "内板 + 主板 + 锁存开关 → 终点闸解锁");
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_INNER));
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_MAIN));
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_SWITCH), "锁存位并入门条件判定");
        assertTrue(exit.isDoorUnlocked(), "出口终端随终点闸一起武装");
        assertTrue(exit.interact(400L, 0), "闸门解锁后 interact 应成功");

        // 开关的锁存语义：人离开后门条件依然成立（否则解锁那一刻没人压着，本关无解）。
        assertTrue(switchPlate.tryExit("player", 0, 450L));
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState(), "人确实已离开开关");
        assertTrue(switchPlate.isLatched(), "离开不得清除锁存");
        assertTrue(exitGate.isUnlocked(), "开关锁存后离开 → 终点闸不得回锁");

        // 门格通行性实时回锁：内板 / 主板任一块释放 → Door 立刻回到 LOCKED
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryExit("echo_1", 1, 500L));
        assertFalse(exitGate.isUnlocked(), "主板一松 → 终点闸门格立刻回锁（开关已锁存也救不回来）");
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, 600L));
        assertTrue(exitGate.isUnlocked(), "主板重新被占 → 终点闸再次解锁");
    }

    // ---------- 4. 真·驾驶：踩下锁存开关（随后离开）→ 走到出口旁按 E 通关 ----------

    @Test
    void drivingOntoTheLatchingSwitchAndLeavingItStillUnlocksTheExitGate() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // E1 压外闸板、E2 压中继板：玩家才过得了 D1 / D2，真开进右上角内室。
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow().tryEnter("echo_2", 2, tick));
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        DockingPlate switchPlate = a.dockingPlate(Level02Corridor.PLATE_SWITCH).orElseThrow();
        assertFalse(switchPlate.isLatched(), "开局开关未锁存");

        // 真·驾驶到开关格 (18,3)：寻路避开已被残影压住的板格与出口格（避免顺路把自己压上去）。
        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_SWITCH,
                Set.of(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_RELAY,
                        Level02Corridor.NODE_PLATE_INNER, Level02Corridor.NODE_PLATE_MAIN,
                        Level02Corridor.NODE_EXIT));
        tick = parkOnDock(a, tick);

        assertEquals(MovementState.DOCKED, player(a).movementState(), "应停在锁存开关上");
        assertEquals(cellX(18), player(a).x(), 1e-9);
        assertEquals(cellY(3), player(a).y(), 1e-9);
        assertTrue(switchPlate.isLatched(), "踩上开关即锁存（role=switch）");
        assertEquals(DockingPlate.State.OCCUPIED, switchPlate.getState(), "此刻人正压着开关");
        assertFalse(exitGate.isUnlocked(), "只踩了开关、内板 + 主板无人 → 终点闸仍锁");

        // 离开开关：占用释放，但锁存位保留 —— 解锁那一刻不需要有人压着开关。
        tick = drive(a, tick, LogicalKey.DIR_LEFT);
        assertEquals(cellX(17), player(a).x(), 1e-9, "离开开关一格");
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState(), "人已不在开关上");
        assertTrue(switchPlate.isLatched(), "离开不得清除锁存");
        assertTrue(a.isPlateOccupied(Level02Corridor.PLATE_SWITCH),
                "锁存后即使没人站着，D3 所需的开关条件仍然成立");
        assertFalse(exitGate.isUnlocked(), "开关 + 无人压内板 / 主板 → 终点闸仍锁");

        // 内板 P3 / 主板 P4 由两条残影压住（设计里 E2 / E1 的角色）。
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow()
                .tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow()
                .tryEnter("echo_1", 1, tick));
        assertTrue(exitGate.isUnlocked(), "锁存开关 + 内板 + 主板 → 终点闸解锁");

        // 真·走到出口旁 (24,2)（距出口 1 格 = 48 ≤ 72 宽容半径）后按 E 通关。
        tick = driveTo(a, tick, Level02Corridor.nodeId(17, 3), Level02Corridor.nodeId(24, 2),
                Set.of(Level02Corridor.NODE_PLATE_SWITCH, Level02Corridor.NODE_EXIT));
        assertEquals(cellX(24), player(a).x(), 1e-9);
        assertEquals(cellY(2), player(a).y(), 1e-9);
        a.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, a.phase(), "闸门解锁后在半径内按 E 应通关");
    }

    /**
     * 反例：开关<b>没被踩过</b>（未锁存）时，即使内板 + 主板都被残影压住，终点闸也必须保持锁定。
     *
     * <p>锁存开关是本关唯一的「不占 actor」的第三条件；若它被错接成普通板（或锁存位被漏掉），
     * 这条会先红，而不是等到玩家在游戏里打不通关才发现。</p>
     */
    @Test
    void exitGateStaysLockedWhileTheSwitchWasNeverStepped() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        DockingPlate switchPlate = a.dockingPlate(Level02Corridor.PLATE_SWITCH).orElseThrow();
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        assertFalse(switchPlate.isLatched(), "开局开关未锁存");

        // 三缺一：内板 + 主板齐备，但没人碰过开关。
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow()
                .tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow()
                .tryEnter("echo_1", 1, tick));

        assertFalse(exitGate.isUnlocked(), "内板 + 主板被占，但开关没踩过 → 终点闸必须保持锁定");
        assertFalse(a.exitTerminal().isDoorUnlocked(), "出口终端不得被武装");
        assertFalse(a.isPlateOccupied(Level02Corridor.PLATE_SWITCH),
                "没人踩过开关 → 门条件视角也不成立");

        // 推进若干逻辑刻（玩家真的走动）也不得凭空解锁。
        a.tick(press(tick++, LogicalKey.DIR_UP));
        for (int i = 0; i < 30; i++) {
            a.tick(InputIntent.empty(tick++));
        }
        assertFalse(switchPlate.isLatched(), "玩家没走到开关格 → 不得锁存");
        assertEquals(DockingPlate.State.UNOCCUPIED, switchPlate.getState());
        assertFalse(exitGate.isUnlocked(), "推进过程中终点闸不得自行解锁");

        // 画面同样不得把未触发的开关画成 ON。
        RenderViews.Mechanism switchView = a.renderViews().mechanisms().stream()
                .filter(m -> m.kind() == RenderViews.MechanismKind.SWITCH)
                .findFirst()
                .orElseThrow(() -> new AssertionError("渲染投影缺少锁存开关"));
        assertEquals(Level02Corridor.PLATE_SWITCH, switchView.id());
        assertFalse(switchView.active(), "未锁存的开关在画面上必须是 OFF");
    }

    @Test
    void exitGateCellIsBlockedWhileLockedAndPermittedRightAfterUnlock() {
        Level02Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 只开两扇过路门（外闸 + 内室门）；终点闸的内板 / 主板暂时无人压。
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow().tryEnter("echo_2", 2, tick));

        // 真·驾驶到开关格踩下（锁存），再走到出口旁一格 (24,2)。
        tick = driveTo(a, tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_SWITCH,
                Set.of(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_RELAY,
                        Level02Corridor.NODE_PLATE_INNER, Level02Corridor.NODE_PLATE_MAIN,
                        Level02Corridor.NODE_EXIT));
        tick = parkOnDock(a, tick);
        DockingPlate switchPlate = a.dockingPlate(Level02Corridor.PLATE_SWITCH).orElseThrow();
        assertTrue(switchPlate.isLatched(), "踩上即锁存");

        tick = driveTo(a, tick, Level02Corridor.NODE_PLATE_SWITCH, Level02Corridor.nodeId(24, 2),
                Set.of(Level02Corridor.NODE_EXIT));
        assertEquals(cellX(24), player(a).x(), 1e-9);
        assertEquals(cellY(2), player(a).y(), 1e-9);

        // 只有锁存开关成立（内板 / 主板无人）→ 终点闸锁着 → 门格 (25,2) 不可进入
        Door exitGate = a.door(Level02Corridor.DOOR_EXIT).orElseThrow();
        assertTrue(switchPlate.isLatched());
        assertFalse(exitGate.isUnlocked(), "只有锁存开关 → 终点闸锁着");
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(24), player(a).x(), 1e-9, "终点闸锁着时不得进入门格 (25,2)");

        // 补上内板 + 主板 → 终点闸解锁（实时）→ 同一按键即可穿过门格
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(exitGate.isUnlocked(), "锁存开关 + 内板 + 主板 → 终点闸解锁");
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(25), player(a).x(), 1e-9, "终点闸解锁后应允许进入门格 (25,2)");
        assertTrue(switchPlate.isLatched(), "全程开关保持锁存（没有人再压着它）");
    }

    // ---------- 5. 渲染投影 ----------

    @Test
    void renderProjectionHasFourPlatesOneSwitchTwoDoorsAndOneExitAtTheExitCell() {
        Level02Assembly a = started();
        List<RenderViews.Mechanism> mechanisms = a.renderViews().mechanisms();

        Map<RenderViews.MechanismKind, List<String>> idsByKind = new HashMap<>();
        for (RenderViews.Mechanism m : mechanisms) {
            idsByKind.computeIfAbsent(m.kind(), k -> new ArrayList<>()).add(m.id());
        }
        assertEquals(8, mechanisms.size(), "4 板 + 1 开关 + 2 门 + 1 终点");
        assertEquals(Set.of(Level02Corridor.PLATE_GATE, Level02Corridor.PLATE_RELAY,
                        Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN),
                Set.copyOf(idsByKind.get(RenderViews.MechanismKind.PLATE)),
                "四块普通驻留板投影成 PLATE");
        assertEquals(List.of(Level02Corridor.PLATE_SWITCH), idsByKind.get(RenderViews.MechanismKind.SWITCH),
                "锁存开关必须投影成 SWITCH（role=switch 的表现变体，不是第 5 块 PLATE）");
        assertEquals(Set.of(Level02Corridor.DOOR_GATE, Level02Corridor.DOOR_RELAY),
                Set.copyOf(idsByKind.get(RenderViews.MechanismKind.DOOR)),
                "只投影两扇普通门；终点闸与出口同格，不投影 DOOR");
        assertEquals(List.of(Level02Corridor.EXIT), idsByKind.get(RenderViews.MechanismKind.EXIT));

        // 「板 ↔ 它作用的那扇门」的数字角标：开门组 P1/D1 = 1、P2/D2 = 2；终点闸组不带数字。
        assertEquals("1", tagOf(mechanisms, Level02Corridor.PLATE_GATE));
        assertEquals("2", tagOf(mechanisms, Level02Corridor.PLATE_RELAY));
        assertEquals("1", tagOf(mechanisms, Level02Corridor.DOOR_GATE));
        assertEquals("2", tagOf(mechanisms, Level02Corridor.DOOR_RELAY));
        assertNull(tagOf(mechanisms, Level02Corridor.PLATE_INNER));
        assertNull(tagOf(mechanisms, Level02Corridor.PLATE_MAIN));
        assertNull(tagOf(mechanisms, Level02Corridor.PLATE_SWITCH));
        assertNull(tagOf(mechanisms, Level02Corridor.EXIT), "终点闸格不带角标（同色即同组）");

        // 色系分组：作用于终点闸 D3 的三块（P3 / P4 / 开关）为 true，其余为 false。
        assertFalse(gateGroupOf(mechanisms, Level02Corridor.PLATE_GATE));
        assertFalse(gateGroupOf(mechanisms, Level02Corridor.PLATE_RELAY));
        assertTrue(gateGroupOf(mechanisms, Level02Corridor.PLATE_INNER));
        assertTrue(gateGroupOf(mechanisms, Level02Corridor.PLATE_MAIN));
        assertTrue(gateGroupOf(mechanisms, Level02Corridor.PLATE_SWITCH));
        assertFalse(gateGroupOf(mechanisms, Level02Corridor.DOOR_GATE));
        assertFalse(gateGroupOf(mechanisms, Level02Corridor.DOOR_RELAY));
        assertFalse(gateGroupOf(mechanisms, Level02Corridor.EXIT));

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

    private static String tagOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanismOf(mechanisms, id).tag();
    }

    private static boolean gateGroupOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanismOf(mechanisms, id).gateGroup();
    }

    private static RenderViews.Mechanism mechanismOf(List<RenderViews.Mechanism> mechanisms, String id) {
        return mechanisms.stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("渲染投影缺少机关: " + id));
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
