package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
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

    private static final double PLATE_LEFT_REGION_TOP_Y = 6 * 48.0;      // 新左驻留板 (4,6) 的 autoDock 区域上边界 = 288
    private static final double SPAWN_Y = 2.5 * 48.0;                    // 出生节点 (10,2) 中心 = 120
    private Level01Assembly assembly;

    @AfterEach
    void cleanupAssembly() {
        if (assembly != null) {
            assembly.cleanup();
            assembly = null;
        }
    }

    @Test
    void roundEndReturnsToPlayingAndSecondRoundAcceptsInput() {
        Level01Assembly a = started();
        long tick = runFirstRoundToEnd(a);

        // 轮末事务结束于 READY，app 必须接回 PLAYING，否则第 2 轮完全无法推进
        assertEquals(GamePhase.PLAYING, a.phase(), "轮末后应回到 PLAYING");
        assertEquals(2, a.hudContext().currentRound());
        assertEquals(0, a.hudContext().roundTick());

        // 轮初复位（ENT-3 resetTo）：角色回到出生节点中心与初始朝向，而不是停在上轮落点
        RenderViews.Player atSpawn = player(a);
        assertEquals(SPAWN_Y, atSpawn.y(), 1e-9, "轮初应回到出生点，实际 y=" + atSpawn.y());
        assertEquals(Direction.DOWN, atSpawn.direction());

        // 第 2 轮必须能立即移动（出生点 (10,2) 有 DOWN 出口）
        for (int i = 0; i < 10; i++) {
            a.tick(hold(tick++, LogicalKey.DIR_DOWN));
        }
        assertEquals(GamePhase.PLAYING, a.phase(), "第 2 轮仍在推进");
        assertEquals(2, a.hudContext().currentRound());
        assertTrue(player(a).y() > SPAWN_Y, "第 2 轮必须能移动，实际 y=" + player(a).y());
    }

    @Test
    void dockEventIsWrittenIntoTheCurrentRoundRecording() {
        Level01Assembly a = started();
        long tick = 0;
        a.tick(InputIntent.empty(tick++));
        tick = driveLeftPlateRoute(a, tick);
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

        // 轮初复位后活玩家在出生点，左驻留板此刻必定空闲
        assertTrue(player(a).y() < PLATE_LEFT_REGION_TOP_Y,
                "轮初复位后活玩家不应留在驻留板上，实际 y=" + player(a).y());

        // 走到残影录到 DOCK_ENTERED 之后的刻（新地图左板在 roundTick ≈ 420 被压住）；
        // 按轮内刻判断，不能用累计输入刻号
        while (a.hudContext().roundTick() < 500) {
            a.tick(InputIntent.empty(tick++));
        }
        assertTrue(a.isPlateOccupied("L01_plate_left"),
                "第 2 轮该由上一轮残影占住左驻留板（录制→回放闭环）");
    }

    // ---------- 工具 ----------

    /**
     * 第 1 轮的左驻留板路线（新地图 18 格 = 432 刻）：
     * (10,2) ↓ (10,3) ← (9,3) ← (8,3) ↓ (8,10) ← (7,10) ← (6,10) ← (5,10) ↑ (5,9) ↑ (5,8) ← (4,8) ↑ (4,7) ↑ (4,6)。
     * 返回推进到停驻后的下一个输入刻号。
     */
    private static long driveLeftPlateRoute(Level01Assembly a, long tick) {
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 24);   // (10,2) → (10,3)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 48);   // (10,3) → (8,3)
        tick = drive(a, tick, LogicalKey.DIR_DOWN, 168);  // (8,3) → (8,10)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 72);   // (8,10) → (5,10)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);     // (5,10) → (5,8)
        tick = drive(a, tick, LogicalKey.DIR_LEFT, 24);   // (5,8) → (4,8)
        tick = drive(a, tick, LogicalKey.DIR_UP, 48);     // (4,8) → 左驻留板 (4,6) 中心
        return tick;
    }

    /** 第 1 轮从首个移动输入开始跑到轮末，其中 432 个有效逻辑刻走到左驻留板并停驻。 */
    private static long runFirstRoundToEnd(Level01Assembly a) {
        long tick = 0;
        a.tick(InputIntent.empty(tick++));
        tick = driveLeftPlateRoute(a, tick);
        a.tick(InputIntent.empty(tick++));                // 停驻
        while (a.hudContext().currentRound() == 1) {
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
