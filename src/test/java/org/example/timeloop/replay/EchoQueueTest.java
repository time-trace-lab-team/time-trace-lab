package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4 基础自动测试：双残影滑动窗口的代际与边界淘汰。
 */
class EchoQueueTest {

    private static EchoState echoOfRound(int sourceRound) {
        TimelineRecording r = new TimelineRecording(3, sourceRound);
        for (int i = 0; i < 3; i++) {
            r.record(new PlayerFrame(i, sourceRound * 100.0 + i, 0, Direction.RIGHT, false,
                    MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        }
        r.seal();
        return EchoState.of(r);
    }

    @Test
    void slidingWindow_matchesReadmeTable() {
        // README 表格（L=2）：第 3 轮 E1+E2；第 4 轮 E2+E3。
        EchoQueue q = new EchoQueue(2);
        q.addOnRoundEnd(echoOfRound(1), 2);
        assertEquals(List.of(1), q.activeEchoes(2).stream().map(EchoState::sourceRound).toList());

        q.addOnRoundEnd(echoOfRound(2), 3);
        assertEquals(List.of(1, 2), q.activeEchoes(3).stream().map(EchoState::sourceRound).toList(),
                "第 3 轮应为 E1+E2");

        q.addOnRoundEnd(echoOfRound(3), 4);
        assertEquals(List.of(2, 3), q.activeEchoes(4).stream().map(EchoState::sourceRound).toList(),
                "第 4 轮应为 E2+E3，E1 已在边界淘汰");
        assertEquals(2, q.size(), "容量固定为 2");
    }

    @Test
    void evictionOnlyAtRoundBoundary() {
        EchoQueue q = new EchoQueue(2);
        q.addOnRoundEnd(echoOfRound(1), 2);
        q.addOnRoundEnd(echoOfRound(2), 3);
        // 第 3 轮内任意时刻 E1 都仍在（age=2 是最后有效轮，但轮中不移除）。
        assertEquals(2, q.activeEchoes(3).size());
    }

    @Test
    void lifetimeOne_onlyNewestActive() {
        // 第一/二关 L=1：任何时候只有一个活跃残影。
        EchoQueue q = new EchoQueue(1);
        q.addOnRoundEnd(echoOfRound(1), 2);
        assertEquals(List.of(1), q.activeEchoes(2).stream().map(EchoState::sourceRound).toList());
        q.addOnRoundEnd(echoOfRound(2), 3);
        assertEquals(List.of(2), q.activeEchoes(3).stream().map(EchoState::sourceRound).toList());
        assertEquals(1, q.size());
    }

    @Test
    void duplicateSourceRound_rejected() {
        EchoQueue q = new EchoQueue(2);
        q.addOnRoundEnd(echoOfRound(1), 2);
        assertThrows(IllegalArgumentException.class, () -> q.addOnRoundEnd(echoOfRound(1), 3),
                "一轮只能生成一条记录");
    }

    @Test
    void lifetimes_derivedFromSharedContext() {
        EchoQueue q = new EchoQueue(2);
        q.addOnRoundEnd(echoOfRound(1), 2);
        q.addOnRoundEnd(echoOfRound(2), 3);
        TickContext ctx = new TickContext(0, 3, 3, 4);
        List<EchoLifetime> views = q.lifetimes(ctx);
        assertEquals(2, views.size());
        assertEquals(1, views.get(0).getSourceRound());
        assertEquals(2, views.get(0).getAge(), "第 3 轮 E1 为最后有效轮");
        assertTrue(views.get(0).isLastEffectiveRound());
        assertFalse(views.get(1).isLastEffectiveRound());
    }

    @Test
    void clear_removesEverything() {
        EchoQueue q = new EchoQueue(2);
        q.addOnRoundEnd(echoOfRound(1), 2);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.activeEchoes(2).size());
    }

    @Test
    void invalidLifetime_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new EchoQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new EchoQueue(3),
                "L 不能超过容量 2");
    }
}
