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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关装配（{@link Level03Assembly}）的机关 / 射线 / 目标投影集成测试 · <b>重排 v3</b>。
 *
 * <p>覆盖五件事：</p>
 * <ol>
 *   <li><b>渲染投影</b>：4 块板（A/B/C 蓝底带 1/2/3 号）+ 2 个开关（S₂/S₃，琥珀胶囊）+
 *       3 扇门（各带同号角标）+ 1 个出口；终点格（{@code L03_door_exit} 与出口同格）<b>只</b>投影一个
 *       {@code EXIT}，不叠一个 {@code DOOR}，且终点组一律不带数字；</li>
 *   <li><b>射线随共享 {@code roundTick} 推进</b>：射线周期 396（OFF 264 / 预警 72 / 激活 60），
 *       绝对锚点仍是 预警起 528、激活起 600；</li>
 *   <li><b>{@code objectiveView()} 在真实玩法状态下不得抛异常</b>：玩家已进 E₂ 支路而门 A 早已回锁，
 *       是第三关必然出现的合法局面 —— 这里用<b>真驾驶</b>复现，而不是拼一个 VM 参数；</li>
 *   <li><b>官方解第一轮的顺序路线</b>（A → C → K）玩家真走到时三块板都必须占板（上一张任务卡的回归）；</li>
 *   <li><b>出口闸三条件</b>：S₂ + S₃ + K 缺一不解锁；齐了才武装、才允许进出口格、按 {@code E} 才通关。</li>
 * </ol>
 *
 * <p>驾驶脚本不手抄地图路线：{@link #route} 用 {@link Level03Pursuit#isOpen} 现算 BFS 最短格子路线
 * （每格 {@link Level03Pursuit#TICKS_PER_TILE} 刻），与 {@code Level02AssemblyTest} 同一做法。</p>
 */
class Level03AssemblyTest {

    private static final double TILE = Level03Pursuit.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level03Pursuit.TICKS_PER_TILE;

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
    void renderProjectionHasFourPlatesTwoSwitchesThreeDoorsAndOneExit() {
        Level03Assembly a = started();
        List<RenderViews.Mechanism> mechanisms = a.renderViews().mechanisms();
        assertEquals(10, mechanisms.size(), "4 板 + 2 开关 + 3 门 + 1 终点");

        Map<String, RenderViews.MechanismKind> kindById = new HashMap<>();
        int plates = 0;
        int switches = 0;
        int doors = 0;
        int exits = 0;
        for (RenderViews.Mechanism m : mechanisms) {
            kindById.put(m.id(), m.kind());
            switch (m.kind()) {
                case PLATE -> plates++;
                case SWITCH -> switches++;
                case DOOR -> doors++;
                case EXIT -> exits++;
            }
        }
        assertEquals(5, plates, "A / C / K / B 四块普通板 + S₂（锁存但按驻留板外观投）");
        assertEquals(1, switches, "只有 S₃ 投影成 SWITCH（琥珀胶囊）");
        assertEquals(3, doors, "门 A / 门 B / 门 C 三扇普通门");
        assertEquals(1, exits, "终点格只投影一个 EXIT");

        for (String plateId : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_K)) {
            assertEquals(RenderViews.MechanismKind.PLATE, kindById.get(plateId), plateId + " 应投影成 PLATE");
        }
        assertEquals(RenderViews.MechanismKind.SWITCH, kindById.get(Level03Pursuit.SWITCH_S3),
                "S₃ 应投影成 SWITCH（胶囊）");
        assertEquals(RenderViews.MechanismKind.PLATE, kindById.get(Level03Pursuit.SWITCH_S2),
                "S₂ 是驻留板外观（琥珀方块），不得画成胶囊按钮");
        for (String doorId : List.of(Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B, Level03Pursuit.DOOR_C)) {
            assertEquals(RenderViews.MechanismKind.DOOR, kindById.get(doorId), doorId + " 应投影成 DOOR");
        }
        assertEquals(RenderViews.MechanismKind.EXIT, kindById.get(Level03Pursuit.EXIT));
        assertFalse(kindById.containsKey(Level03Pursuit.DOOR_EXIT),
                "终点供能闸与出口同格，不得再投影一个 DOOR（同格叠画）");
        assertPairedTagsAndGroups(mechanisms);

        Vector2D exitCell = Level03Pursuit.cellCenter(
                Level03Pursuit.CELL_EXIT[0], Level03Pursuit.CELL_EXIT[1]);
        RenderViews.Mechanism exitView = mechanismOf(mechanisms, Level03Pursuit.EXIT);
        assertEquals(exitCell.x(), exitView.x(), 1e-9);
        assertEquals(exitCell.y(), exitView.y(), 1e-9);
        assertFalse(mechanisms.stream().anyMatch(m -> m.kind() == RenderViews.MechanismKind.DOOR
                        && Math.abs(m.x() - exitCell.x()) < 1e-9 && Math.abs(m.y() - exitCell.y()) < 1e-9),
                "终点格只能有一个 EXIT，不得同时叠一个 DOOR");
        assertFalse(exitView.active(), "开局三条件都不成立 → 终点闸未解锁 → EXIT 未激活");

        RenderViews.Mechanism doorA = mechanismOf(mechanisms, Level03Pursuit.DOOR_A);
        assertEquals(Level03Pursuit.cellCenter(
                        Level03Pursuit.CELL_DOOR_A[0], Level03Pursuit.CELL_DOOR_A[1]).x(),
                doorA.x(), 1e-9);
        assertFalse(doorA.active(), "开局没人压 A 板 → 门 A 锁着");
    }

    /**
     * 板与门共用同一个序号（照设计图 v3：A=1 / B=2 / C=3），且<b>自开局起就在</b>（静态投影，
     * 不看任何玩法状态）；终点组（K 板、S₂/S₃ 开关、出口）琥珀同色，一律不带数字。
     */
    private static void assertPairedTagsAndGroups(List<RenderViews.Mechanism> mechanisms) {
        assertEquals("1", mechanismOf(mechanisms, Level03Pursuit.PLATE_A).tag());
        assertEquals("1", mechanismOf(mechanisms, Level03Pursuit.DOOR_A).tag(), "A 板与门 A 同号");
        assertEquals("2", mechanismOf(mechanisms, Level03Pursuit.PLATE_B).tag());
        assertEquals("2", mechanismOf(mechanisms, Level03Pursuit.DOOR_B).tag(), "B 板与门 B 同号");
        assertEquals("3", mechanismOf(mechanisms, Level03Pursuit.PLATE_C).tag());
        assertEquals("3", mechanismOf(mechanisms, Level03Pursuit.DOOR_C).tag(), "C 板与门 C 同号");

        assertNull(mechanismOf(mechanisms, Level03Pursuit.PLATE_K).tag(),
                "终点组不带数字：同色即同组（照设计图 v3）");
        assertNull(mechanismOf(mechanisms, Level03Pursuit.SWITCH_S2).tag(), "开关不带数字");
        assertNull(mechanismOf(mechanisms, Level03Pursuit.SWITCH_S3).tag(), "开关不带数字");
        assertNull(mechanismOf(mechanisms, Level03Pursuit.EXIT).tag(), "出口不加角标");

        for (String gateGroup : List.of(Level03Pursuit.PLATE_K, Level03Pursuit.SWITCH_S2,
                Level03Pursuit.SWITCH_S3, Level03Pursuit.EXIT)) {
            assertTrue(mechanismOf(mechanisms, gateGroup).gateGroup(),
                    gateGroup + " 属于终点组（琥珀）");
        }
        for (String openingGroup : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B,
                Level03Pursuit.DOOR_C)) {
            assertFalse(mechanismOf(mechanisms, openingGroup).gateGroup(),
                    openingGroup + " 属于开门组（蓝）");
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

        // 关键刻（当前冻结 + 周期原点口径）逐点钉死，避免整段循环「恰好自洽」。
        assertEquals(264L, Level03Pursuit.RAY_OFF_DURATION_TICKS, "OFF 段长度 528 减半 → 264");
        assertEquals(264L, Level03Pursuit.RAY_WARNING_START_TICK, "周期内预警起点 = OFF 段长度");
        assertEquals(336L, Level03Pursuit.RAY_ACTIVE_START_TICK, "周期内激活起点 = 264 + 72");
        assertEquals(396L, Level03Pursuit.RAY_CYCLE_TICKS, "周期 = OFF 264 + 预警 72 + 激活 60");
        assertEquals(264L, Level03Pursuit.RAY_CYCLE_OFFSET_TICKS,
                "周期缩短后相位与绝对刻不再一一对应：原点 = 600 − 336");
        assertEquals(528L, Level03Pursuit.RAY_WARNING_START_ABSOLUTE_TICK,
                "绝对预警起点必须仍是 528（教学锚点不随周期变化）");
        assertEquals(600L, Level03Pursuit.RAY_ACTIVE_START_ABSOLUTE_TICK,
                "绝对激活起点必须仍是 600");
        assertEquals(Level03Pursuit.RAY_CROSS_TICK, Level03Pursuit.RAY_ACTIVE_START_ABSOLUTE_TICK,
                "刻表锚点：E₂ 抵达射线那一刻恰好是 ACTIVE 起点（否则「必须下潜」不成立）");
        assertEquals(Level03Pursuit.RAY_ACTIVE_START_TICK, Level03Pursuit.RAY_CROSS_PHASE,
                "抵达刻在周期内的相位必须等于周期内激活起点");
        assertTrue(expectedRayActive(600L), "update(600) 必须落在 ACTIVE 段内（刻表前提）");
        assertFalse(expectedRayActive(599L), "update(599) 还在预警段，不是 ACTIVE");
        assertFalse(expectedRayActive(660L), "刻 660 是激活结束刻，不再是 ACTIVE");
        assertFalse(expectedRayActive(528L), "刻 528 是预警起点，不是 ACTIVE");
    }

    /**
     * 与 {@link Level03Pursuit} 常量等价、但独立算出的期望状态（不在生产代码里复用同一表达式）。
     *
     * <p>相位 = {@code floorMod(drivenTick - 周期原点, 周期)}，与 {@code Ray.update} 同一口径。</p>
     *
     * @param drivenTick 装配对该刻调用 {@code Ray.update(drivenTick)} 时落在哪个状态段
     */
    private static boolean expectedRayActive(long drivenTick) {
        long cycleTick = Math.floorMod(drivenTick - Level03Pursuit.RAY_CYCLE_OFFSET_TICKS,
                Level03Pursuit.RAY_CYCLE_TICKS);
        return cycleTick >= Level03Pursuit.RAY_ACTIVE_START_TICK
                && cycleTick < Level03Pursuit.RAY_ACTIVE_START_TICK
                + Level03Pursuit.RAY_ACTIVE_DURATION_TICKS;
    }

    // ---------- 3. objectiveView() 在「已进 E₂ 支路、门 A 已回锁」时不得抛异常 ----------

    @Test
    void objectiveViewStaysValidWhilePlayerIsInEcho2BranchWithDoorAAlreadyRelocked() {
        Level03Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 真驾驶要过门 A：用残影占用 A 板把门 A 压开（该板是普通板，有人压着才开）。
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板被压 → 门 A 开");

        // 真开到分岔口 J（出生点 → 门 A 14 格 → J 2 格 = 16 格 = 384 刻）。
        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_J,
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C,
                        Level03Pursuit.NODE_PLATE_K, Level03Pursuit.NODE_PLATE_B,
                        Level03Pursuit.NODE_SWITCH_S2, Level03Pursuit.NODE_SWITCH_S3));
        assertEquals(384L, a.hudContext().roundTick(),
                "按刻表，出生点 → 门 A（14 格）→ J（2 格）= 16 格 = 384 刻");
        assertTrue(a.inEcho2Branch(), "J(15,10) 属于 E₂ 支路集合");

        // 关键局面：门 A 此刻已经回锁（刻 384 是窗口末），而玩家已经在 E₂ 支路里。
        a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryExit("echo_1", 1, tick);
        assertFalse(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板一松 → 门 A 回锁");

        Level03ObjectiveViewModel atFork = a.objectiveView();   // 修掉假不变量前：这里抛 IllegalArgumentException
        assertFalse(atFork.doorAOpen(), "门 A 已回锁");
        assertTrue(atFork.inEcho2Branch(), "玩家确实已经在 E₂ 支路里");
        assertFalse(atFork.rayActive(), "刻 384 射线尚未 ACTIVE");

        // 再推进到射线 ACTIVE 那一刻：提示切成「按 Space 下潜」，投影仍然合法。
        // 装配先 updateAll(roundTick) 再 advance，因此刻 600 的 update 结果在刻 601 才可见。
        while (!a.isRayActive()) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(601L, a.hudContext().roundTick(), "射线在刻 601 才可观察到 ACTIVE");
        Level03ObjectiveViewModel atRay = a.objectiveView();
        assertTrue(atRay.rayActive(), "刻 601 射线 ACTIVE");
        assertTrue(atRay.inEcho2Branch(), "玩家仍在 E₂ 支路");
        assertFalse(atRay.doorAOpen(), "门 A 仍然是锁的");
        assertTrue(atRay.text().contains("Space"), atRay.text());
        assertTrue(atRay.text().contains("下潜"), atRay.text());
    }

    // ---------- 4. 上一张任务卡的回归：官方解第一轮 A → C → K ----------

    /**
     * 官方解第一轮的顺序路线：出生点 → **A**（驻留）→ 离开 → **C**（驻留）→ 离开 → **K**（驻留到轮末）。
     *
     * <p>这是任务卡 {@code TASK-DEV3-L03-PLATE-C-NO-RESPONSE} 的现场：C 不是被单独走到，而是在
     * <b>先驻留过 A、再走过去</b>的顺序里踩到的。三段各用真实按键驱动，每段结束都断言该板确实被占。</p>
     */
    @Test
    void officialFirstRoundRouteDocksOnAThenCThenK() {
        Level03Assembly a = started();
        long tick = 0L;

        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_PLATE_A,
                Set.of(Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_K,
                        Level03Pursuit.NODE_PLATE_B, Level03Pursuit.NODE_SWITCH_S2,
                        Level03Pursuit.NODE_SWITCH_S3));
        assertTrue(a.isPlateOccupied(Level03Pursuit.PLATE_A), "第 1 步：玩家踩 A 板必须占板");
        assertTrue(a.door(Level03Pursuit.DOOR_A).orElseThrow().isUnlocked(), "A 板占 → 门 A 开");

        tick = driveTo(a, tick, Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C,
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_K,
                        Level03Pursuit.NODE_PLATE_B, Level03Pursuit.NODE_SWITCH_S2,
                        Level03Pursuit.NODE_SWITCH_S3));
        assertTrue(a.isPlateOccupied(Level03Pursuit.PLATE_C),
                "第 2 步：玩家踩 C 板必须占板（任务卡报的 P1 就在这一步）");
        assertTrue(a.door(Level03Pursuit.DOOR_C).orElseThrow().isUnlocked(), "C 板占 → 门 C 开");

        tick = driveTo(a, tick, Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_K,
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_C,
                        Level03Pursuit.NODE_PLATE_B, Level03Pursuit.NODE_SWITCH_S2,
                        Level03Pursuit.NODE_SWITCH_S3));
        assertTrue(a.isPlateOccupied(Level03Pursuit.PLATE_K), "第 3 步：玩家踩 K 板必须占板");
    }

    /**
     * 触发场景复原：**C 板先被残影占着**，玩家走到 C 格并停住（此时 `tryEnter` 返回
     * {@code ALREADY_OCCUPIED} → 不驻留），随后残影让出 —— 玩家仍站在格心，却<b>再也不会</b>驻留。
     */
    @Test
    void playerStandingOnAPlateReleasedByAnEchoShouldDockAfterwards() {
        Level03Assembly a = started();
        DockingPlate plateC = a.dockingPlate(Level03Pursuit.PLATE_C).orElseThrow();
        assertTrue(plateC.tryEnter("echo_1", 1, 0L), "夹具前提：残影先占住 C 板");

        long tick = 0L;
        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.NODE_PLATE_C,
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_B,
                        Level03Pursuit.NODE_PLATE_K, Level03Pursuit.NODE_SWITCH_S2,
                        Level03Pursuit.NODE_SWITCH_S3));
        assertEquals(List.of("echo_1", "player"), plateC.getOccupantIds(),
                "残影持板时玩家也必须登记进去（旧单槽位模型会静默丢弃玩家 → 残影一走门就回锁）");
        assertEquals(Level03Pursuit.cellCenter(Level03Pursuit.CELL_PLATE_C[0],
                        Level03Pursuit.CELL_PLATE_C[1]),
                new Vector2D(player(a).x(), player(a).y()),
                "夹具前提：玩家确实停在 C 板格中心（位置在区域内）");

        assertTrue(plateC.tryExit("echo_1", 1, tick), "残影让出 C 板");
        for (int i = 0; i < 120; i++) {
            a.tick(InputIntent.empty(tick++));
        }

        assertTrue(a.isPlateOccupied(Level03Pursuit.PLATE_C),
                "残影让出后，站在板心不动的玩家仍必须被算作占板（门 C 不得回锁）");
        assertEquals(List.of("player"), plateC.getOccupantIds(), "残影已移除，板上只剩玩家");
        assertTrue(a.door(Level03Pursuit.DOOR_C).orElseThrow().isUnlocked(), "门 C 仍开着");
    }

    // ---------- 5. 出口闸三条件 ----------

    /**
     * 出口闸要 <b>S₂ + S₃ + K 三块同时成立</b>：缺一不解锁、进不了出口格、按 {@code E} 无效；
     * 齐了才武装，玩家走进出口格按 {@code E} 才 {@code RESULT}。
     *
     * <p>残影条件用直接注入（等价于「前两轮按刻表踩过 S₂/S₃ 并压住 K」）：本用例测的是
     * <b>闸门条件与出口结算</b>，不是三轮时序（时序由 {@code Level03PursuitChainTest} 覆盖）。</p>
     */
    @Test
    void exitGateNeedsSwitchesPlusKBeforeThePlayerCanClearTheLevel() {
        Level03Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));

        // 门 A / 门 B / 门 C 都压开，玩家才能真开到出口旁
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_A).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_B).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_C).orElseThrow().tryEnter("echo_1", 1, tick));

        tick = driveTo(a, tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.nodeId(25, 14),
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_B,
                        Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_K,
                        Level03Pursuit.NODE_SWITCH_S2, Level03Pursuit.NODE_SWITCH_S3));
        assertEquals(cellX(25), player(a).x(), 1e-9);
        assertEquals(cellY(14), player(a).y(), 1e-9);

        // 三条件都不成立 → 闸锁着、出口未武装
        assertFalse(a.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(), "三条件未齐 → 闸锁着");
        assertFalse(a.exitTerminal().isDoorUnlocked(), "出口终端未被武装");
        assertEquals("出口闸 0/3：S₂✗ S₃✗ K✗", a.objectiveView().gateProgress());

        // 只按 E 不够
        a.tick(pressKey(tick++, LogicalKey.INTERACT));
        assertEquals(GamePhase.PLAYING, a.phase(), "闸没解锁时按 E 不得通关");
        assertFalse(a.exitTerminal().isTriggered());

        // 只满足两块也不够（缺 K）
        assertTrue(a.dockingPlate(Level03Pursuit.SWITCH_S2).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level03Pursuit.SWITCH_S3).orElseThrow().tryEnter("player", 0, tick));
        assertEquals("出口闸 2/3：S₂✓ S₃✓ K✗", a.objectiveView().gateProgress());
        assertFalse(a.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(), "缺 K → 闸仍锁着");

        // 补上 K：三条件齐 → 闸解锁、出口武装、目标提示改口
        assertTrue(a.dockingPlate(Level03Pursuit.PLATE_K).orElseThrow().tryEnter("echo_1", 1, tick));
        assertTrue(a.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(), "三条件齐 → 闸解锁");
        assertTrue(a.exitTerminal().isDoorUnlocked(), "出口终端随闸一起武装");
        assertEquals("出口闸 3/3：S₂✓ S₃✓ K✓", a.objectiveView().gateProgress());
        assertEquals("出口已供能：到出口旁按 E 通关", a.objectiveView().text());

        // 闸解锁后走进出口格并按 E → RESULT
        tick = drive(a, tick, LogicalKey.DIR_RIGHT);
        assertEquals(cellX(26), player(a).x(), 1e-9, "闸解锁后应允许进入出口格 (26,14)");
        a.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, a.phase(), "闸解锁后在半径内按 E 应通关");
        assertTrue(a.isFinalPhase());
        LevelResult result = a.result().orElseThrow(() -> new AssertionError("通关后必须有结算投影"));
        assertTrue(result.cleared(), "结算应为通关");
        assertEquals(LevelFlow.LevelId.LEVEL_03.title(), result.levelName(),
                "RecordingSession 的关卡名必须与当前 LevelId 一致");
        assertEquals(Level03Pursuit.MAX_ROUNDS, result.maxRounds());
    }

    /** 开关的锁存必须随轮末归零（否则下一轮出口闸会白送两块条件）。 */
    @Test
    void switchLatchResetsAtRoundEnd() {
        Level03Assembly a = started();
        long tick = 0;
        a.tick(press(tick++, LogicalKey.DIR_UP));   // 首个方向输入出现后时钟才开始走
        assertTrue(a.dockingPlate(Level03Pursuit.SWITCH_S2).orElseThrow().tryEnter("echo_2", 2, tick));
        assertTrue(a.dockingPlate(Level03Pursuit.SWITCH_S2).orElseThrow().isLatched(), "踩上即锁存");

        while (a.hudContext().currentRound() == 1) {
            a.tick(InputIntent.empty(tick++));
        }

        assertEquals(2, a.hudContext().currentRound(), "轮末必须进入第 2 轮");
        assertFalse(a.dockingPlate(Level03Pursuit.SWITCH_S2).orElseThrow().isLatched(),
                "轮末 reset 后锁存必须归零");
        assertFalse(a.isPlateOccupied(Level03Pursuit.SWITCH_S2), "新一轮开关回到 OFF");
        assertEquals("出口闸 0/3：S₂✗ S₃✗ K✗", a.objectiveView().gateProgress());
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

    /** 用关卡数据现算一条最短格子路线（BFS，四方向）。 */
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
