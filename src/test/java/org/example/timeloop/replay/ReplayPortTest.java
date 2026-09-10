package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 自动测试：回放端口（ReplayPort）。
 */
class ReplayPortTest {

    private static final int DURATION = 6;
    private static final int MAX_ROUNDS = 4;

    private RoundClock clock;
    private EchoQueue echoQueue;
    private RecordingSession session;
    private ReplayPort port;

    @BeforeEach
    void setUp() {
        clock = playingClock(DURATION, MAX_ROUNDS);
        echoQueue = new EchoQueue(2);
        session = new RecordingSession(clock, echoQueue);
        port = new ReplayPort(clock, session);
    }

    private static RoundClock playingClock(int durationTicks, int maxRounds) {
        RoundClock c = new RoundClock(durationTicks, maxRounds);
        c.transition(GamePhase.MENU);
        c.transition(GamePhase.LEVEL_SELECT);
        c.transition(GamePhase.READY);
        c.transition(GamePhase.PLAYING);
        return c;
    }

    private static PlayerFrame frame(long tick, MovementState state) {
        return new PlayerFrame(tick, 100.0 + tick, 200.0, Direction.RIGHT, false,
                state, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
    }

    private void recordFullRound() {
        for (int i = 0; i < DURATION; i++) {
            session.recordFrame(frame(i, MovementState.CRUISING));
        }
    }

    @Test
    void currentPlayerFrame_emptyWhenNoBuffer() {
        assertTrue(port.currentPlayerFrame().isEmpty());
    }

    @Test
    void currentPlayerFrame_returnsFrameAfterBeginRound() {
        session.beginRound();
        session.recordFrame(frame(0, MovementState.CRUISING));

        Optional<PlayerFrame> result = port.currentPlayerFrame();
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().tick());
        assertEquals(100.0, result.get().x(), 1e-9);
    }

    @Test
    void activeEchoFrames_emptyWhenNoEchoes() {
        session.beginRound();
        assertTrue(port.activeEchoFrames().isEmpty());
    }

    @Test
    void activeEchoFrames_emptyWhenNoActiveEchoes() {
        // 第 1 轮结束生成 E1，但第 1 轮自己不算活跃
        session.beginRound();
        recordFullRound();
        session.completeNormalRound(() -> {});

        // 现在 currentRound=2，E1 活跃
        List<EchoFrameView> active = port.activeEchoFrames();
        assertEquals(1, active.size());
        assertEquals(1, active.get(0).sourceRound());
    }

    @Test
    void activeEchoFrames_unmodifiable() {
        session.beginRound();
        recordFullRound();
        session.completeNormalRound(() -> {});

        List<EchoFrameView> active = port.activeEchoFrames();
        assertThrows(UnsupportedOperationException.class, () -> active.clear());
    }

    @Test
    void activeEchoFrames_fieldValuesCorrect() {
        session.beginRound();
        recordFullRound();
        session.completeNormalRound(() -> {});

        // currentRound=2, E1 sourceRound=1, L=2, age=1
        // remainingRounds = 2, lifeProgress = ((1-1)*6 + 0)/(2*6) = 0
        List<EchoFrameView> active = port.activeEchoFrames();
        EchoFrameView view = active.get(0);
        assertEquals(1, view.sourceRound());
        assertEquals(2, view.remainingRounds());
        assertEquals(0.0, view.lifeProgress(), 1e-9);
        assertEquals(0.82, view.bodyAlpha(), 1e-9);
    }
}