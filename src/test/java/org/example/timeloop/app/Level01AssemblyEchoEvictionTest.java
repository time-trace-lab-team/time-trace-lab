package org.example.timeloop.app;

import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECHO-ACTOR 闭环 · 残影淘汰派发契约（PM 侧 app/）。
 *
 * <p>开发二 v4 卡 §七裁决：`replay/**` 只暴露数据，**派发由装配层做**；淘汰刻 = 轮末边界刻
 * （`durationTicks - 1`），事件用装配**自己的** `GameEventBus`（Phase 1 之后机关只注册在它上面）。</p>
 *
 * <p>本类用包内可见的 `Level01Assembly.eventBus()` 捕获 `ECHO_DISAPPEARED`，验证：</p>
 * <ol>
 *   <li>第 1 轮残影在第 2→3 轮边界被寿命淘汰时，**恰好派发一次**；</li>
 *   <li>`sourceRound` 与 actor 口径为 `echo_1`、`tick` 为边界刻 `durationTicks - 1`；</li>
 *   <li>随后的轮次不会重复派发同一残影。</li>
 * </ol>
 */
class Level01AssemblyEchoEvictionTest {

    private static final long DURATION_TICKS = 16 * 60L;

    @Test
    void evictedEchoIsDispatchedExactlyOnceAtRoundBoundary() {
        Level01Assembly a = new Level01Assembly();
        List<GameEvent> disappeared = new ArrayList<>();
        GameObserver observer = event -> {
            if (GameEvent.ECHO_DISAPPEARED.equals(event.eventType())) {
                disappeared.add(event);
            }
        };
        a.eventBus().register(GameEvent.ECHO_DISAPPEARED, observer);

        a.start();

        // 第 1 轮：按一次 DOWN 让时钟起走，然后跑到轮末
        long tick = 0;
        a.tick(new InputIntent(tick++, java.util.Set.of(LogicalKey.DIR_DOWN), java.util.Set.of(),
                java.util.Set.of(LogicalKey.DIR_DOWN), List.of(org.example.timeloop.core.Direction.DOWN)));
        while (a.hudContext().currentRound() == 1) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(2, a.hudContext().currentRound());
        assertTrue(disappeared.isEmpty(), "第 1 轮残影刚生成，不应有淘汰事件");

        // 第 2 轮：跑到第 2→3 轮边界（第 1 轮残影在该边界超龄淘汰）
        while (a.hudContext().currentRound() == 2) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(3, a.hudContext().currentRound());
        assertEquals(1, disappeared.size(), "第 1 轮残影应在第 2→3 轮边界恰好淘汰一次");
        GameEvent event = disappeared.get(0);
        assertEquals("echo_1", event.sourceId(), "actor 口径必须与 DockingPlate 的匹配约定一致");
        assertEquals(1, event.sourceRound());
        assertEquals(DURATION_TICKS - 1, event.tick(), "淘汰刻 = 刚结束那轮的最后一刻");

        // 第 3 轮继续推进：不得重复派发同一残影
        for (long t = 0; t < 60; t++) {
            a.tick(InputIntent.empty(tick++));
        }
        assertEquals(1, disappeared.size(), "同一残影不得被重复派发");

        a.cleanup();
    }
}
