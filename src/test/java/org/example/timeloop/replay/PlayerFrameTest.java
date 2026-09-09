package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2 自动测试：{@link PlayerFrame} 的字段契约与相位派生。
 */
class PlayerFrameTest {

    private static PlayerFrame frame(long tick, ActorPhase phase) {
        return new PlayerFrame(tick, 48.0, 96.0, Direction.RIGHT, false,
                MovementState.CRUISING, phase, 0, AnimationState.MOVING);
    }

    @Test
    void phaseDodging_onlyTrueWhenPhased() {
        // README 第九节的 boolean phaseDodging 与 ActorPhase 的等价关系。
        assertTrue(frame(0, ActorPhase.PHASED).isPhaseDodging());
        assertFalse(frame(0, ActorPhase.AVAILABLE).isPhaseDodging());
        assertFalse(frame(0, ActorPhase.RECOVERING).isPhaseDodging(),
                "冷却期不是相位下潜中，不能被错误回放为可免射线");
    }

    @Test
    void negativeTick_rejected() {
        assertThrows(IllegalArgumentException.class, () -> frame(-1, ActorPhase.AVAILABLE));
    }

    @Test
    void nullEnumFields_rejected() {
        assertThrows(NullPointerException.class, () ->
                new PlayerFrame(0, 0, 0, null, false,
                        MovementState.CRUISING, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        assertThrows(NullPointerException.class, () ->
                new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                        null, ActorPhase.AVAILABLE, 0, AnimationState.MOVING));
        assertThrows(NullPointerException.class, () ->
                new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                        MovementState.CRUISING, null, 0, AnimationState.MOVING));
        assertThrows(NullPointerException.class, () ->
                new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                        MovementState.CRUISING, ActorPhase.AVAILABLE, 0, null));
    }

    @Test
    void negativePhaseTicksRemaining_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerFrame(0, 0, 0, Direction.RIGHT, false,
                        MovementState.CRUISING, ActorPhase.PHASED, -1, AnimationState.MOVING));
    }

    @Test
    void recoveringKeepsRemainingTicks() {
        PlayerFrame f = new PlayerFrame(7, 1.0, 2.0, Direction.UP, true,
                MovementState.SLOWED, ActorPhase.RECOVERING, 31, AnimationState.MOVING);
        assertEquals(31, f.actorPhaseTicksRemaining());
        assertFalse(f.isPhaseDodging());
        assertTrue(f.interacting(), "interacting 为边沿语义，可由调用方置位");
    }
}
