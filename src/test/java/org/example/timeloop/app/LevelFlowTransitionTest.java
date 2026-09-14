package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.Level03Pursuit;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.ui.Level02ObjectiveViewModel;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关卡流转（{@link LevelFlow}）的切换守卫测试 —— 项目方验收口径：
 * <b>第一关通关后跳转到第二关；第一关没通过就不许跳转</b>。
 *
 * <ul>
 *   <li><b>通关（{@code RESULT}）</b>：真·驾驶第一关到通关 → 切到第二关，关卡数据 / HUD 计数 /
 *       目标提示全部换成第二关；第一关被 {@code stop()}（不再推进、不再消费事件）；</li>
 *   <li><b>失败（{@code FAILED}，轮次耗尽）</b>：绝不切换，玩家留在第一关，既有重开行为不变，
 *       重开后再次失败仍然不切换；</li>
 *   <li><b>幂等</b>：第二关清关不存在的第三关 —— 切换条件只在 {@code LEVEL_01 && RESULT} 成立。</li>
 * </ul>
 *
 * <p>第一关驾驶脚本与 {@code Level01AssemblyLevel01FlowTest} 使用同一套路线（该文件里的
 * {@code driveLeftPlateRoute} / {@code driveRightPlateRoute}），此处按 {@link LevelFlow} 重写一份，
 * 不改动既有测试。</p>
 */
class LevelFlowTransitionTest {

    private LevelFlow flow;

    @AfterEach
    void cleanupFlow() {
        if (flow != null) {
            flow.cleanup();
            flow = null;
        }
    }

    @Test
    void clearingLevel01LoadsLevel02AndStopsLevel01() {
        flow = started();

        long tick = runLevel01ToClear();
        assertEquals(GamePhase.RESULT, flow.phase(), "第一关应已通关");
        assertEquals(LevelFlow.LevelId.LEVEL_01, flow.activeLevel());
        assertTrue(flow.level02().isEmpty(), "第一关通关前不得装配第二关");

        // 切关前的第一关证据：给左板挂一个残影占用 → 切关后它不应再被事件改动
        Level01Assembly l1 = flow.level01();
        DockingPlate l1LeftPlate = l1.dockingPlate("L01_plate_left").orElseThrow();
        l1LeftPlate.reset();
        assertTrue(l1LeftPlate.tryEnter("echo_1", 1, tick));
        assertTrue(l1.isPlateOccupied("L01_plate_left"), "切关前：第一关占用注册表里有这块板");
        long l1TickBefore = l1.hudContext().roundTick();
        String l1ObjectiveBefore = l1.objectiveView().text();

        assertTrue(flow.switchToNextLevelIfCleared(), "第一关 RESULT → 必须切到第二关");
        assertEquals(LevelFlow.LevelId.LEVEL_02, flow.activeLevel());
        assertTrue(l1.isStopped(), "切关后第一关必须已停止推进");
        assertFalse(l1.isPlateOccupied("L01_plate_left"),
                "切关后第一关机关已 dispose（从占用注册表注销）");
        assertFalse(flow.switchToNextLevelIfCleared(), "已切到第二关 → 再问也是 no-op（幂等）");

        Level02Assembly l2 = flow.level02().orElseThrow();
        assertTrue(l2.isPlaying());
        assertFalse(l2.isStopped());

        // 关卡数据换成第二关
        assertEquals(Level02Corridor.MAX_ROUNDS, flow.activeLevelData().getMaxRounds());
        assertEquals(Level02Corridor.DURATION_TICKS, flow.activeLevelData().getDurationTicks());
        assertEquals(Level02Corridor.cellCenter(1, 13), flow.activeLevelData().getSpawnPos());
        assertTrue(flow.activeLevelData().getEntitySpawnList().stream()
                        .anyMatch(e -> Level02Corridor.PLATE_GATE.equals(e.getId())),
                "当前关卡数据必须带第二关的机关");
        assertNotEquals(Level01Footsteps.build().getDurationTicks(),
                flow.activeLevelData().getDurationTicks());

        // HUD 计数与目标提示同样换主人（不得残留第一关的文本 / 计数）
        assertEquals(Level02Corridor.MAX_ROUNDS, flow.hudContext().maxRounds(), "HUD 轮次上限来自第二关");
        assertEquals(1, flow.hudContext().currentRound());
        assertEquals(0, flow.hudContext().roundTick());
        Level02ObjectiveViewModel l2Objective = l2.objectiveView();
        assertEquals(l2Objective.text(), flow.objectiveText(),
                "目标提示必须来自 Level02ObjectiveViewModel");
        assertNotEquals(l1ObjectiveBefore, flow.objectiveText(), "不得残留第一关的目标文本");
        assertFalse(flow.objectiveText().contains("开关"), "第二关提示不应带第一关的开关措辞");
        assertEquals(1, l2Objective.currentRound());
        assertEquals(Level02Corridor.MAX_ROUNDS, l2Objective.maxRounds());

        // 交付：只允许第二关推进；第一关 roundTick 冻结
        assertEquals(Level02Corridor.cellCenter(1, 13), positionOf(flow.renderViews()),
                "切关后渲染视图是第二关出生点");
        long l2TickBefore = flow.hudContext().roundTick();
        long driveTick = 100L;
        flow.tick(press(driveTick++, LogicalKey.DIR_UP));
        for (int i = 0; i < 30; i++) {
            flow.tick(InputIntent.empty(driveTick++));
        }
        assertEquals(l1TickBefore, l1.hudContext().roundTick(),
                "切换后第一关不得再收到任何逻辑推进");
        assertTrue(flow.hudContext().roundTick() > l2TickBefore, "切换后只有第二关在推进");
        assertEquals(Direction.UP, flow.renderViews().player().direction());
        assertTrue(flow.renderViews().player().y() < Level02Corridor.cellCenter(1, 13).y(),
                "第二关玩家应按输入向上移动");

        // 第一关不再消费事件：已 dispose → ECHO_DISAPPEARED 不再释放残影占用
        l1.eventBus().dispatch(GameEvent.echoDisappeared("echo_1", tick + 1, 1));
        assertEquals(DockingPlate.State.OCCUPIED, l1LeftPlate.getState(),
                "第一关已卸载 → 事件不得再改动它的机关状态");
    }

    @Test
    void failedLevel01NeverSwitchesToLevel02() {
        flow = started();

        long tick = runLevel01ToFailure(1L);
        assertEquals(GamePhase.FAILED, flow.phase(), "轮次耗尽应进入 FAILED");
        assertFalse(flow.isPlaying());

        assertFalse(flow.switchToNextLevelIfCleared(), "FAILED 不得触发切关");
        assertEquals(LevelFlow.LevelId.LEVEL_01, flow.activeLevel(), "失败后仍停在第一关");
        assertTrue(flow.level02().isEmpty(), "失败时不得装配第二关");
        assertFalse(flow.level01().isStopped(), "失败时第一关仍活着（可重开）");

        // 既有失败处理不变：R 重开后仍在第一关，且再次耗尽轮次仍不切关
        flow.restart();
        assertEquals(GamePhase.PLAYING, flow.phase(), "失败后重开应回到 PLAYING");
        assertEquals(1, flow.hudContext().currentRound(), "重开回到第 1 轮");
        assertEquals(LevelFlow.LevelId.LEVEL_01, flow.activeLevel());

        runLevel01ToFailure(tick);
        assertEquals(GamePhase.FAILED, flow.phase());
        assertFalse(flow.switchToNextLevelIfCleared(), "重开后再次 FAILED 仍不得切关");
        assertEquals(LevelFlow.LevelId.LEVEL_01, flow.activeLevel());
        assertTrue(flow.level02().isEmpty());
    }

    /**
     * 第二关失败：按重开键是<b>在第二关重来</b>（回第 1 轮），不得退回第一关。
     *
     * <p>玩家预期（项目方口径）：一旦进到第二关，这一关就是「当前关」；
     * 失败重开只重置本关，不会把已经通关的第一关再放回来，也不会让第一关复活推进。</p>
     */
    @Test
    void failedLevel02RestartsLevel02AndNeverFallsBackToLevel01() {
        flow = started();
        long tick = runLevel01ToClear();
        assertTrue(flow.switchToNextLevelIfCleared(), "第一关通关 → 进入第二关");
        Level02Assembly l2 = flow.level02().orElseThrow();
        long l1TickAtSwitch = flow.level01().hudContext().roundTick();

        runLevel02ToFailure(tick);
        assertEquals(GamePhase.FAILED, flow.phase(), "第二关 4 轮耗尽应进入 FAILED");
        assertEquals(LevelFlow.LevelId.LEVEL_02, flow.activeLevel(), "失败后仍停在第二关");
        assertTrue(flow.level01().isStopped(), "已通关的第一关不得因为第二关失败而复活");

        flow.restart();

        assertEquals(GamePhase.PLAYING, flow.phase(), "第二关失败后重开应回到 PLAYING");
        assertEquals(LevelFlow.LevelId.LEVEL_02, flow.activeLevel(), "重开的是第二关，不是第一关");
        assertEquals(1, flow.hudContext().currentRound(), "重开回到第二关第 1 轮");
        assertEquals(Level02Corridor.MAX_ROUNDS, flow.hudContext().maxRounds(), "轮次上限仍是第二关的 4");
        assertEquals(0L, flow.hudContext().roundTick(), "重开后刻数归零");
        assertTrue(flow.level01().isStopped(), "重开第二关不得让第一关复活（它仍是已卸载状态）");
        assertEquals(l1TickAtSwitch, flow.level01().hudContext().roundTick(),
                "第一关的刻数在第二关重开后仍必须冻结");
        assertFalse(l2.isStopped(), "第二关自身仍在运行");

        // 机关与射线都回到初始态，不带上一局的残留。
        assertEquals(Ray.State.OFF, l2.rays().get(0).getState(), "重开后射线回到周期起点 OFF");
        for (String plateId : List.of(Level02Corridor.PLATE_GATE, Level02Corridor.PLATE_RELAY,
                Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_SWITCH)) {
            assertFalse(l2.isPlateOccupied(plateId), "重开后 " + plateId + " 不得残留占用");
        }
        assertEquals(Level02Corridor.cellCenter(1, 13), positionOf(flow.renderViews()),
                "重开后玩家回到第二关出生点");
    }

    /**
     * 第二关通关 → 装载第三关，并且第二关被 {@code stop()}。
     *
     * <p>与「第一关通关 → 第二关」同一条守卫链：真驾驶通关（{@code RESULT}）才切，
     * 切完第三关的数据 / HUD / 目标提示全部换主人，第二关的刻数从此冻结。</p>
     */
    @Test
    void clearingLevel02LoadsLevel03AndStopsLevel02() {
        flow = started();
        long tick = runLevel01ToClear();
        assertTrue(flow.switchToNextLevelIfCleared(), "第一关通关 → 进入第二关");
        tick = runLevel02ToClear(tick);

        assertEquals(GamePhase.RESULT, flow.phase(), "第二关应已通关");
        assertEquals(LevelFlow.LevelId.LEVEL_02, flow.activeLevel(), "确认前仍停在第二关");
        assertTrue(flow.level03().isEmpty(), "第二关通关前不得装配第三关");

        Level02Assembly l2 = flow.level02().orElseThrow();
        long l2TickAtSwitch = l2.hudContext().roundTick();
        String l2ObjectiveBefore = l2.objectiveView().text();

        assertTrue(flow.switchToNextLevelIfCleared(), "第二关 RESULT → 必须切到第三关");
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel());
        assertTrue(l2.isStopped(), "切关后第二关必须已停止推进");
        assertFalse(flow.switchToNextLevelIfCleared(), "已切到第三关 → 再问也是 no-op（幂等）");

        Level03Assembly l3 = flow.level03().orElseThrow();
        assertTrue(l3.isPlaying());
        assertFalse(l3.isStopped());

        // 关卡数据换成第三关。
        assertEquals(Level03Pursuit.MAX_ROUNDS, flow.activeLevelData().getMaxRounds());
        assertEquals(Level03Pursuit.DURATION_TICKS, flow.activeLevelData().getDurationTicks());
        assertEquals(Level03Pursuit.cellCenter(
                        Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]),
                flow.activeLevelData().getSpawnPos());
        assertTrue(flow.activeLevelData().getEntitySpawnList().stream()
                        .anyMatch(e -> Level03Pursuit.PLATE_A.equals(e.getId())),
                "当前关卡数据必须带第三关的机关");
        assertNotEquals(Level02Corridor.DURATION_TICKS, flow.activeLevelData().getDurationTicks());

        // HUD 计数与目标提示同样换主人（不得残留第二关的文本 / 计数）。
        assertEquals(Level03Pursuit.DURATION_TICKS, flow.hudContext().durationTicks(),
                "HUD 时长来自第三关");
        assertEquals(1, flow.hudContext().currentRound());
        assertEquals(0, flow.hudContext().roundTick());
        assertEquals(l3.objectiveView().text(), flow.objectiveText(),
                "目标提示必须来自 Level03ObjectiveViewModel");
        assertNotEquals(l2ObjectiveBefore, flow.objectiveText(), "不得残留第二关的目标文本");
        assertTrue(flow.objectiveText().contains("A → C → D"), flow.objectiveText());

        // 交付：只允许第三关推进；第二关 roundTick 冻结。
        assertEquals(Level03Pursuit.cellCenter(
                        Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]),
                positionOf(flow.renderViews()), "切关后渲染视图是第三关出生点");
        long l3TickBefore = flow.hudContext().roundTick();
        flow.tick(press(tick++, LogicalKey.DIR_UP));
        for (int i = 0; i < 30; i++) {
            flow.tick(InputIntent.empty(tick++));
        }
        assertEquals(l2TickAtSwitch, l2.hudContext().roundTick(),
                "切换后第二关不得再收到任何逻辑推进");
        assertTrue(flow.hudContext().roundTick() > l3TickBefore, "切换后只有第三关在推进");
        assertTrue(flow.renderViews().player().y()
                        < Level03Pursuit.cellCenter(
                                Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]).y(),
                "第三关玩家应按输入向上移动（出生点 (2,13) 上方是通的）");
    }

    /**
     * 第三关失败：{@code activeLevel} 仍是 {@code LEVEL_03}，重开回第三关第 1 轮，
     * 第一关 / 第二关都保持已卸载（不得复活、也不得退回去）。
     */
    @Test
    void failedLevel03RestartsLevel03AndNeverFallsBackToLevel02() {
        flow = started();
        long tick = runLevel01ToClear();
        assertTrue(flow.switchToNextLevelIfCleared(), "第一关通关 → 进入第二关");
        tick = runLevel02ToClear(tick);
        assertTrue(flow.switchToNextLevelIfCleared(), "第二关通关 → 进入第三关");
        Level03Assembly l3 = flow.level03().orElseThrow();
        long l2TickAtSwitch = flow.level02().orElseThrow().hudContext().roundTick();

        runLevel03ToFailure(tick);
        assertEquals(GamePhase.FAILED, flow.phase(), "第三关 3 轮耗尽应进入 FAILED");
        assertFalse(flow.isPlaying());
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel(), "失败后仍停在第三关");
        assertFalse(flow.switchToNextLevelIfCleared(), "第三关 FAILED 不得触发切关");
        assertTrue(flow.level01().isStopped(), "已通关的第一关不得因第三关失败而复活");
        assertTrue(flow.level02().orElseThrow().isStopped(), "已通关的第二关不得复活");

        flow.restart();

        assertEquals(GamePhase.PLAYING, flow.phase(), "第三关失败后重开应回到 PLAYING");
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel(), "重开的是第三关，不是第二关");
        assertEquals(1, flow.hudContext().currentRound(), "重开回到第三关第 1 轮");
        assertEquals(Level03Pursuit.MAX_ROUNDS, flow.hudContext().maxRounds(), "轮次上限仍是第三关的 3");
        assertEquals(0L, flow.hudContext().roundTick(), "重开后刻数归零");
        assertFalse(l3.isStopped(), "第三关自身仍在运行");
        assertTrue(flow.level01().isStopped(), "重开第三关不得让第一关复活");
        assertTrue(flow.level02().orElseThrow().isStopped(), "重开第三关不得让第二关复活");
        assertEquals(l2TickAtSwitch, flow.level02().orElseThrow().hudContext().roundTick(),
                "第二关的刻数在第三关重开后仍必须冻结");

        // 机关与射线都回到初始态，不带上一局的残留。
        assertEquals(Ray.State.OFF, l3.rays().get(0).getState(), "重开后射线回到周期起点 OFF");
        for (String plateId : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_D)) {
            assertFalse(l3.isPlateOccupied(plateId), "重开后 " + plateId + " 不得残留占用");
        }
        for (String doorId : List.of(Level03Pursuit.DOOR_A, Level03Pursuit.DOOR_B,
                Level03Pursuit.DOOR_C, Level03Pursuit.DOOR_EXIT)) {
            assertFalse(l3.door(doorId).orElseThrow().isUnlocked(), "重开后 " + doorId + " 必须回锁");
        }
        assertEquals(Level03Pursuit.cellCenter(
                        Level03Pursuit.SPAWN_CELL[0], Level03Pursuit.SPAWN_CELL[1]),
                positionOf(flow.renderViews()), "重开后玩家回到第三关出生点");
    }

    /** 第三关是最后一关：通关后不再切关（幂等），第三关保持运行，也没有第四关被装配出来。 */
    @Test
    void clearingLevel03NeverSwitchesAnyFurther() {
        flow = started();
        long tick = runLevel01ToClear();
        assertTrue(flow.switchToNextLevelIfCleared(), "第一关通关 → 进入第二关");
        tick = runLevel02ToClear(tick);
        assertTrue(flow.switchToNextLevelIfCleared(), "第二关通关 → 进入第三关");
        runLevel03ToClear(tick);

        assertEquals(GamePhase.RESULT, flow.phase(), "第三关应已通关");
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel(), "仍在第三关");
        assertFalse(flow.switchToNextLevelIfCleared(), "最后一关通关后不得再切关");
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel());
        assertFalse(flow.level03().orElseThrow().isStopped(), "第三关仍在（可作为重开入口）");
        assertTrue(flow.isFinalPhase());

        // 重开仍在第三关（不会因为「通关」而跳到别处）。
        flow.restart();
        assertEquals(LevelFlow.LevelId.LEVEL_03, flow.activeLevel());
        assertEquals(1, flow.hudContext().currentRound());
        assertEquals(GamePhase.PLAYING, flow.phase());
    }

    // ---------- 第一关驾驶脚本（与 Level01AssemblyLevel01FlowTest 同路线） ----------

    /** 真·驾驶第一关到通关（RESULT）：第 1 轮压左板，第 2 轮压右板后在半径内按 E。 */
    private long runLevel01ToClear() {
        long tick = 0;
        flow.tick(InputIntent.empty(tick++));
        tick = driveLeftPlateRoute(tick);
        flow.tick(InputIntent.empty(tick++));
        while (flow.hudContext().currentRound() == 1) {
            flow.tick(InputIntent.empty(tick++));
        }
        tick = driveRightPlateRoute(tick);
        for (int i = 0; i < 3; i++) {
            flow.tick(InputIntent.empty(tick++));            // 走到机关中心并停驻
        }
        flow.tick(pressKey(tick, LogicalKey.INTERACT));      // 右板中心距出口 1 格 → 半径内结算
        assertEquals(GamePhase.RESULT, flow.phase(), "第一关驾驶脚本应能通关");
        return tick;
    }

    /** 只给一个方向输入启动逻辑刻，之后空输入耗完所有轮次 → FAILED。 */
    private long runLevel01ToFailure(long tick) {
        flow.tick(press(tick++, LogicalKey.DIR_DOWN));
        long guard = tick + 20_000L;
        while (flow.phase() != GamePhase.FAILED && tick < guard) {
            flow.tick(InputIntent.empty(tick++));
        }
        assertTrue(tick < guard, "轮次耗尽应在有限刻内到达 FAILED");
        return tick;
    }

    /** 只给一个方向输入启动第二关逻辑刻，之后空输入耗完 4 轮 → FAILED。 */
    private void runLevel02ToFailure(long tick) {
        long guard = tick + 40_000L;
        flow.tick(press(tick++, LogicalKey.DIR_DOWN));
        while (flow.phase() != GamePhase.FAILED && tick < guard) {
            flow.tick(InputIntent.empty(tick++));
        }
        assertTrue(tick < guard, "第二关 4 轮耗尽应在有限刻内到达 FAILED");
    }

    // ---------- 第二关 / 第三关驾驶脚本 ----------

    /**
     * 真·驾驶第二关到通关（RESULT）：残影压开外闸板 / 中继板 → 玩家真开到锁存开关踩下 → 离开
     * （锁存保留）→ 残影压住内板 + 主板 → 走到出口旁按 E。
     *
     * <p>与 {@code Level02AssemblyTest.drivingOntoTheLatchingSwitchAndLeavingItStillUnlocksTheExitGate}
     * 同一路线与同一套 BFS 驾驶脚本（本文件自带一份，不改动既有测试）。</p>
     */
    private long runLevel02ToClear(long tick) {
        Level02Assembly l2 = flow.level02().orElseThrow();
        assertTrue(l2.dockingPlate(Level02Corridor.PLATE_GATE).orElseThrow()
                .tryEnter("echo_1", 1, tick), "残影压外闸板");
        assertTrue(l2.dockingPlate(Level02Corridor.PLATE_RELAY).orElseThrow()
                .tryEnter("echo_2", 2, tick), "残影压中继板");

        // 真·驾驶到锁存开关 (18,3)：避开已被残影压住的板格与出口格。
        tick = driveTo(tick, Level02Corridor.NODE_SPAWN, Level02Corridor.NODE_PLATE_SWITCH,
                Set.of(Level02Corridor.NODE_PLATE_GATE, Level02Corridor.NODE_PLATE_RELAY,
                        Level02Corridor.NODE_PLATE_INNER, Level02Corridor.NODE_PLATE_MAIN,
                        Level02Corridor.NODE_EXIT));
        for (int i = 0; i < 3; i++) {
            flow.tick(InputIntent.empty(tick++));   // 走到机关中心并停驻
        }
        DockingPlate switchPlate = l2.dockingPlate(Level02Corridor.PLATE_SWITCH).orElseThrow();
        assertTrue(switchPlate.isLatched(), "踩上开关即锁存");

        // 离开开关一格：占用释放、锁存保留。
        tick = drive(tick, LogicalKey.DIR_LEFT);

        // 残影压住内板 + 主板 → 终点闸解锁。
        assertTrue(l2.dockingPlate(Level02Corridor.PLATE_INNER).orElseThrow()
                .tryEnter("echo_2", 2, tick));
        assertTrue(l2.dockingPlate(Level02Corridor.PLATE_MAIN).orElseThrow()
                .tryEnter("echo_1", 1, tick));
        assertTrue(l2.door(Level02Corridor.DOOR_EXIT).orElseThrow().isUnlocked(), "终点闸应已解锁");

        tick = driveTo(tick, Level02Corridor.nodeId(17, 3), Level02Corridor.nodeId(24, 2),
                Set.of(Level02Corridor.NODE_PLATE_SWITCH, Level02Corridor.NODE_EXIT));
        flow.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, flow.phase(), "第二关驾驶脚本应能通关");
        return tick;
    }

    /**
     * 真·驾驶第三关到通关（RESULT）：残影压开 A / B / C 板 → 玩家真开到出口正下方 (25,7)
     * → 等到 D 板供能刻（{@code PLATE_D_ARRIVAL}）→ 按 E。
     *
     * <p>刻表上「玩家到出口」早于「D 板供能」（840 &lt; 1056），而出口格与终点供能闸同格、
     * 未解锁不可通行，所以玩家停在出口正下方（距出口 1 格 = 48 ≤ 宽容半径 72），等到供能再按 E
     * —— 这正是第三关的设计意图。</p>
     */
    private long runLevel03ToClear(long tick) {
        Level03Assembly l3 = flow.level03().orElseThrow();
        for (String plateId : List.of(Level03Pursuit.PLATE_A, Level03Pursuit.PLATE_B,
                Level03Pursuit.PLATE_C, Level03Pursuit.PLATE_D)) {
            assertTrue(l3.dockingPlate(plateId).orElseThrow().tryEnter("echo_1", 1, tick),
                    "残影应能压住 " + plateId);
        }
        assertTrue(l3.isPlateOccupied(Level03Pursuit.PLATE_D), "注入后 D 板应被占");
        tick = driveTo(tick, Level03Pursuit.NODE_SPAWN, Level03Pursuit.nodeId(25, 7),
                Set.of(Level03Pursuit.NODE_PLATE_A, Level03Pursuit.NODE_PLATE_B,
                        Level03Pursuit.NODE_PLATE_C, Level03Pursuit.NODE_PLATE_D));
        assertEquals(Level03Pursuit.cellCenter(25, 7).x(), positionOf(flow.renderViews()).x(), 1e-9,
                "应停在出口正下方 (25,7)");
        assertEquals(Level03Pursuit.cellCenter(25, 7).y(), positionOf(flow.renderViews()).y(), 1e-9);
        while (flow.hudContext().roundTick() < Level03Pursuit.PLATE_D_ARRIVAL) {
            flow.tick(InputIntent.empty(tick++));
        }
        // 供能刻之后：终点闸必须已解锁，玩家仍在宽容半径内（否则按 E 白按）。
        assertTrue(l3.door(Level03Pursuit.DOOR_EXIT).orElseThrow().isUnlocked(),
                "D 板供能 → 终点闸解锁");
        assertTrue(l3.exitTerminal().isInInteractRange(positionOf(flow.renderViews())),
                "玩家应仍在出口的宽容半径内");
        flow.tick(pressKey(tick, LogicalKey.INTERACT));
        assertEquals(GamePhase.RESULT, flow.phase(), "第三关驾驶脚本应能通关");
        return tick;
    }

    // ---------- BFS 驾驶脚本（与 Level02AssemblyTest 同做法，按 LevelFlow 入口重写） ----------

    /** 寻路用的方向顺序（固定顺序 → 脚本可复现）。 */
    private static final List<LogicalKey> MOVE_ORDER = List.of(
            LogicalKey.DIR_UP, LogicalKey.DIR_DOWN, LogicalKey.DIR_LEFT, LogicalKey.DIR_RIGHT);

    /** 从 {@code fromNodeId} 沿 BFS 最短路线开到 {@code toNodeId}（每格固定 24 刻）。 */
    private long driveTo(long tick, String fromNodeId, String toNodeId, Set<String> avoidNodeIds) {
        LevelFlow.LevelId level = flow.activeLevel();
        for (LogicalKey key : route(level, fromNodeId, toNodeId, avoidNodeIds)) {
            tick = drive(tick, key);
        }
        return tick;
    }

    /** 直线推进一格（24 刻）：首刻为新按下边沿，其余为按住。 */
    private long drive(long tick, LogicalKey key) {
        flow.tick(press(tick++, key));
        for (long i = 1; i < TICKS_PER_TILE; i++) {
            flow.tick(hold(tick++, key));
        }
        return tick;
    }

    /**
     * 用当前关卡数据现算一条最短格子路线（BFS，四方向）。
     *
     * @param level        当前关卡（两关的地图 / 节点编号规则不同）
     * @param avoidNodeIds 寻路时不经过的节点（例如已由残影压住的板格、当前锁着的门格）
     */
    private static List<LogicalKey> route(LevelFlow.LevelId level, String fromNodeId, String toNodeId,
                                          Set<String> avoidNodeIds) {
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
            int[] cell = cellOf(level, current);
            for (LogicalKey key : MOVE_ORDER) {
                int[] delta = MOVE_DELTA.get(key);
                int col = cell[0] + delta[0];
                int row = cell[1] + delta[1];
                if (!isOpen(level, col, row)) {
                    continue;
                }
                String next = nodeId(level, col, row);
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

    /** 节点 ID → 格坐标 {col, row}（由当前关卡数据的节点世界坐标反推，不硬编码）。 */
    private static int[] cellOf(LevelFlow.LevelId level, String nodeId) {
        double tile = level == LevelFlow.LevelId.LEVEL_03
                ? Level03Pursuit.TILE_SIZE : Level02Corridor.TILE_SIZE;
        for (PathNode node : levelNodes(level)) {
            if (node.getId().equals(nodeId)) {
                return new int[] {
                        (int) Math.round(node.getWorldPos().x() / tile - 0.5),
                        (int) Math.round(node.getWorldPos().y() / tile - 0.5)};
            }
        }
        throw new IllegalArgumentException("未知节点: " + nodeId);
    }

    private static final Map<LogicalKey, int[]> MOVE_DELTA = Map.of(
            LogicalKey.DIR_UP, new int[] {0, -1},
            LogicalKey.DIR_DOWN, new int[] {0, 1},
            LogicalKey.DIR_LEFT, new int[] {-1, 0},
            LogicalKey.DIR_RIGHT, new int[] {1, 0});

    private static final double TILE = Level02Corridor.TILE_SIZE;
    private static final long TICKS_PER_TILE = Level02Corridor.TICKS_PER_TILE;

    /**
     * 该格在给定关卡地图上是否可走。
     *
     * <p>两关的 {@code isOpen} 都是「越界或 {@code '#'} → false」，本文件按关卡直接分派，
     * 避免为测试在生产代码里加「当前关卡」的静态查询接口。</p>
     */
    private static boolean isOpen(LevelFlow.LevelId level, int col, int row) {
        return level == LevelFlow.LevelId.LEVEL_03
                ? Level03Pursuit.isOpen(col, row)
                : Level02Corridor.isOpen(col, row);
    }

    /** 给定关卡地图上的节点 ID（两关的编号规则不同，各自沿用关卡常量里的口径）。 */
    private static String nodeId(LevelFlow.LevelId level, int col, int row) {
        return level == LevelFlow.LevelId.LEVEL_03
                ? Level03Pursuit.nodeId(col, row)
                : Level02Corridor.nodeId(col, row);
    }

    /** 给定关卡的路径节点（由关卡数据现算，不硬编码坐标）。 */
    private static List<PathNode> levelNodes(LevelFlow.LevelId level) {
        return level == LevelFlow.LevelId.LEVEL_03
                ? Level03Pursuit.build().getPathNodes()
                : Level02Corridor.build().getPathNodes();
    }
    /** 只给一个方向输入启动第三关逻辑刻，之后空输入耗完 3 轮 → FAILED。 */
    private void runLevel03ToFailure(long tick) {
        long guard = tick + 40_000L;
        flow.tick(press(tick++, LogicalKey.DIR_DOWN));
        while (flow.phase() != GamePhase.FAILED && tick < guard) {
            flow.tick(InputIntent.empty(tick++));
        }
        assertTrue(tick < guard, "第三关 3 轮耗尽应在有限刻内到达 FAILED");
    }

    /** 新地图左驻留板路线（18 格 = 432 刻）。 */
    private long driveLeftPlateRoute(long tick) {
        tick = drive(tick, LogicalKey.DIR_DOWN, 24);
        tick = drive(tick, LogicalKey.DIR_LEFT, 48);
        tick = drive(tick, LogicalKey.DIR_DOWN, 168);
        tick = drive(tick, LogicalKey.DIR_LEFT, 72);
        tick = drive(tick, LogicalKey.DIR_UP, 48);
        tick = drive(tick, LogicalKey.DIR_LEFT, 24);
        tick = drive(tick, LogicalKey.DIR_UP, 48);
        return tick;
    }

    /** 新地图右驻留板路线（22 格 = 528 刻）。 */
    private long driveRightPlateRoute(long tick) {
        tick = drive(tick, LogicalKey.DIR_DOWN, 48);
        tick = drive(tick, LogicalKey.DIR_RIGHT, 48);
        tick = drive(tick, LogicalKey.DIR_DOWN, 24);
        tick = drive(tick, LogicalKey.DIR_RIGHT, 240);
        tick = drive(tick, LogicalKey.DIR_DOWN, 72);
        tick = drive(tick, LogicalKey.DIR_LEFT, 96);
        return tick;
    }

    private LevelFlow started() {
        LevelFlow started = new LevelFlow();
        started.start();
        assertTrue(started.isPlaying());
        return started;
    }

    private long drive(long tick, LogicalKey key, int ticks) {
        flow.tick(press(tick++, key));
        for (int i = 1; i < ticks; i++) {
            flow.tick(hold(tick++, key));
        }
        return tick;
    }

    private static Vector2D positionOf(RenderViews.Frame frame) {
        return new Vector2D(frame.player().x(), frame.player().y());
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
