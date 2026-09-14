package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.ui.Level02ObjectiveViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_CORE)) {
            assertFalse(l2.isPlateOccupied(plateId), "重开后 " + plateId + " 不得残留占用");
        }
        assertEquals(Level02Corridor.cellCenter(1, 13), positionOf(flow.renderViews()),
                "重开后玩家回到第二关出生点");
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
