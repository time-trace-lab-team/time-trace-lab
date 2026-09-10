package org.example.timeloop.snapshot;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.replay.EchoFrameView;
import org.example.timeloop.replay.PlayerFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W4 自动测试：MvpRenderSnapshot 不可变性与字段正确性。
 */
class MvpRenderSnapshotTest {

    private static PlayerFrame playerFrame() {
        return new PlayerFrame(0L, 100.0, 200.0, Direction.RIGHT, false,
                MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING);
    }

    private static EchoFrameView echoView() {
        return new EchoFrameView(1, playerFrame(), 2, 0.0, 0.82);
    }

    @Test
    void validArgs_succeeds() {
        MvpRenderSnapshot snap = new MvpRenderSnapshot(
                GamePhase.PLAYING, 5L, 2, 4,
                playerFrame(), List.of(echoView()));
        assertEquals(GamePhase.PLAYING, snap.phase());
        assertEquals(5L, snap.roundTick());
        assertEquals(2, snap.currentRound());
        assertEquals(4, snap.maxRounds());
        assertNotNull(snap.currentPlayer());
        assertEquals(1, snap.activeEchoes().size());
    }

    @Test
    void nullCurrentPlayer_allowed() {
        MvpRenderSnapshot snap = new MvpRenderSnapshot(
                GamePhase.READY, 0L, 1, 4,
                null, List.of());
        assertNull(snap.currentPlayer());
    }

    @Test
    void activeEchoes_unmodifiable() {
        List<EchoFrameView> mutable = new ArrayList<>();
        mutable.add(echoView());
        MvpRenderSnapshot snap = new MvpRenderSnapshot(
                GamePhase.PLAYING, 0L, 2, 4,
                playerFrame(), mutable);
        assertThrows(UnsupportedOperationException.class,
                () -> snap.activeEchoes().clear());
    }

    @Test
    void activeEchoes_defensiveCopy() {
        List<EchoFrameView> mutable = new ArrayList<>();
        mutable.add(echoView());
        MvpRenderSnapshot snap = new MvpRenderSnapshot(
                GamePhase.PLAYING, 0L, 2, 4,
                playerFrame(), mutable);
        mutable.clear();
        assertEquals(1, snap.activeEchoes().size(),
                "构造时的防御性拷贝必须保留快照");
    }

    @Test
    void nullPhase_throws() {
        assertThrows(NullPointerException.class, () ->
                new MvpRenderSnapshot(null, 0L, 1, 4, null, List.of()));
    }

    @Test
    void negativeRoundTick_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new MvpRenderSnapshot(GamePhase.PLAYING, -1L, 1, 4, null, List.of()));
    }

    @Test
    void currentRoundExceedsMax_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new MvpRenderSnapshot(GamePhase.PLAYING, 0L, 5, 4, null, List.of()));
    }
}