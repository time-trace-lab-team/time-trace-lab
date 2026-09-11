package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.TimelineEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一关轮转接线（PM 侧 app/）集成测试。
 *
 * <p>覆盖自由移动在“跨轮”场景下暴露的三个问题：</p>
 * <ol>
 *   <li>轮末事务结束于 {@code READY}，若不接回 {@code PLAYING}，第 2 轮起 {@code tick()} 直接返回、角色无法移动；</li>
 *   <li>机关边沿从未写入 {@code RecordingSession}，残影 {@code eventsAt} 恒空 → 回放时残影占不了板；</li>
 *   <li>驻留机关的 C3 本地状态未在轮末清理，会带着上一轮的驻留状态进入下一轮。</li>
 * </ol>
 */
class Level01AssemblyRoundLoopTest {

    private static final double PLATE_LEFT_REGION_TOP_Y = 5 * 48.0;      // autoDock 区域上边界 = 240
    private static final long DURATION_TICKS = 16 * 60L;

    private Level01Assembly assembly;

    @AfterEach
    void clearGlobalsAndCleanup() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void roundEndReturnsToPlayingAndSecondRoundAcceptsInput() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);

        // 轮末事务结束于 READY，app 必须接回 PLAYING，否则第 2 轮完全无法推进
        assertEquals(GamePhase.PLAYING, a.phase(), "轮末后应回到 PLAYING");
        assertEquals(2, a.hudContext().currentRound());
        assertEquals(0, a.hudContext().roundTick());

        // 第 2 轮：角色仍站在左驻留板上（轮初复位 API 见开发一 ENT-3），按合法出口必须能离开
        double before = player(a).y();
        for (int i = 0; i < 30; i++) {
            a.tick(press(tick++, LogicalKey.DIR_UP));
        }
        assertEquals(GamePhase.PLAYING, a.phase(), "第 2 轮仍在推进");
        assertEquals(2, a.hudContext().currentRound());
        assertTrue(player(a).y() < before, "第 2 轮必须能移动，实际 y=" + player(a).y());
    }

    @Test
    void dockEventIsWrittenIntoTheCurrentRoundRecording() {
        Level01Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 48);
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 72);
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 48);
        a.tick(InputIntent.empty(tick));

        assertTrue(a.currentRecording().isPresent(), "PLAYING 中应有本轮缓冲");
        List<TimelineEvent> recorded = a.currentRecording().orElseThrow().events();
        assertFalse(recorded.isEmpty(), "驻留进入边沿必须写入记录，否则残影回放占不了板");
        assertTrue(recorded.stream().anyMatch(e ->
                        "L01_plate_left".equals(e.mechanismId())
                                && e.eventType() == TimelineEvent.EventType.DOCK_ENTERED),
                "记录里应含左驻留板的 DOCK_ENTERED，实际=" + recorded);
    }

    @Test
    void echoOccupiesLeftPlateInSecondRound() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);

        // 第 2 轮先把“活玩家”从左驻留板上走开，释放本人占用
        for (int i = 0; i < 30; i++) {
            a.tick(press(tick++, LogicalKey.DIR_UP));
        }
        assertTrue(player(a).y() < PLATE_LEFT_REGION_TOP_Y,
                "活玩家应已走出 autoDock 区域，实际 y=" + player(a).y());

        // 走到残影录到 DOCK_ENTERED 之后的刻：此时只可能是残影在占板
        // （按轮内刻判断，不能用累计输入刻号）
        while (a.hudContext().roundTick() < 260) {
            a.tick(InputIntent.empty(tick++));
        }
        assertTrue(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"),
                "第 2 轮该由上一轮残影占住左驻留板（录制→回放闭环）");
    }

    // ---------- 工具 ----------

    /** 第 1 轮跑到轮末（960 刻，其中前 168 刻走到左驻留板并停驻）。 */
    private static long runFirstRoundToEnd(Level01Assembly a) {
        long tick = 0;
        a.tick(InputIntent.empty(tick++));
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 48);   // 出生点 → 分叉 (5,3)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 72);   // 分叉 → 左端拐角 (2,3)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 48);   // (2,3) → 驻留板 (2,5)
        a.tick(InputIntent.empty(tick++));                // 停驻
        while (tick < DURATION_TICKS) {
            a.tick(InputIntent.empty(tick++));
        }
        return tick;
    }

    private Level01Assembly started() {
        assembly = new Level01Assembly();
        assembly.start();
        assertTrue(assembly.isPlaying());
        return assembly;
    }

    private static RenderViews.Player player(Level01Assembly a) {
        return a.renderViews().player();
    }

    private static long drive(Level01Assembly a, long tick, LogicalKey key, int ticks) {
        a.tick(press(tick++, key));
        for (int i = 1; i < ticks; i++) {
            a.tick(hold(tick++, key));
        }
        return tick;
    }

    private static InputIntent press(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(directionOf(key)));
    }

    private static InputIntent hold(long tick, LogicalKey key) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(key), List.of());
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
