package org.example.timeloop.mechanism.resonance;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.replay.EchoState;
import org.example.timeloop.replay.PlayerFrame;
import org.example.timeloop.replay.TickContext;
import org.example.timeloop.replay.TimelineRecording;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResonanceStateMachineTest {

    private static final int DURATION_TICKS = 100;
    private static final int MAX_ROUNDS = 4;

    @Test
    void secondRoundPlayerAndE1OnlyPreviewAndNeverLatch() {
        ResonanceStateMachine playerOnly = new ResonanceStateMachine(30);
        ResonanceTickResult playerOnlyResult = playerOnly.observe(context(10, 2), true, List.of());
        assertTrue(playerOnlyResult.currentPlayerPreviewed());
        assertEquals(ResonanceState.DORMANT, playerOnlyResult.state(),
                "当前玩家没有历史来源资格，单独进入只能预览");

        ResonanceStateMachine resonance = new ResonanceStateMachine(30);
        EchoState e1 = echo(1);

        ResonanceTickResult first = resonance.observe(context(10, 2), true, List.of(e1));
        ResonanceTickResult repeatedPlayerEntry = resonance.observe(context(11, 2), false, List.of(e1));
        ResonanceTickResult reentered = resonance.observe(context(12, 2), true, List.of(e1));

        assertTrue(first.currentPlayerPreviewed());
        assertEquals(ResonanceState.ARMED, first.state(), "E1 可以显示半环，但不构成正式锁存");
        assertFalse(first.latchedThisTick());
        assertTrue(reentered.currentPlayerPreviewed(), "玩家离开后再次进入仍仅产生预览");
        assertEquals(ResonanceState.ARMED, repeatedPlayerEntry.state());
        assertFalse(resonance.isLatched(), "第二轮的当前玩家不能替代第二个历史来源");
    }

    @Test
    void thirdRoundDifferentHistoricalSourcesLatchInWindow() {
        ResonanceStateMachine resonance = new ResonanceStateMachine(30);
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);

        resonance.observe(context(10, 3), false, List.of(e1));
        ResonanceTickResult result = resonance.observe(context(22, 3), false, List.of(e1, e2));

        assertTrue(result.latchedThisTick());
        assertEquals(ResonanceState.LATCHED, result.state());
        assertTrue(resonance.isLatched());
    }

    @Test
    void sameSourceReentryDoesNotRefreshWindowOrLatch() {
        ResonanceStateMachine resonance = new ResonanceStateMachine(3);
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);

        resonance.observe(context(10, 3), false, List.of(e1));
        resonance.observe(context(11, 3), false, List.of());
        ResonanceTickResult repeated = resonance.observe(context(12, 3), false, List.of(e1));
        ResonanceStateSnapshot afterRepeat = resonance.createSnapshot();
        ResonanceTickResult afterOriginalWindow = resonance.observe(context(14, 3), false, List.of(e1, e2));

        assertEquals(ResonanceState.ARMED, repeated.state());
        assertFalse(repeated.armedThisTick(), "同一来源再进入不能重置 armedAtRoundTick");
        assertFalse(repeated.latchedThisTick());
        assertEquals(10, afterRepeat.armedAtRoundTick());
        assertTrue(afterOriginalWindow.timedOutThisTick());
        assertFalse(afterOriginalWindow.latchedThisTick(), "窗口已过期，E2 不能与旧 E1 锁存");
        assertEquals(ResonanceState.ARMED, afterOriginalWindow.state(), "E2 可作为新的首个来源开始新窗口");
        assertEquals(2, resonance.createSnapshot().armedSourceRound());
    }

    @Test
    void windowIncludesFirstAndLastTickButNotTheFollowingTick() {
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);

        ResonanceStateMachine firstTick = new ResonanceStateMachine(30);
        ResonanceTickResult firstTickResult = firstTick.observe(context(10, 3), false, List.of(e2, e1));
        assertTrue(firstTickResult.latchedThisTick(), "同 tick 的两个历史来源相差 0，应在窗口首刻锁存");

        ResonanceStateMachine lastTick = new ResonanceStateMachine(30);
        lastTick.observe(context(10, 3), false, List.of(e1));
        ResonanceTickResult lastTickResult = lastTick.observe(context(40, 3), false, List.of(e1, e2));
        assertTrue(lastTickResult.latchedThisTick(), "elapsed == windowTicks 的末刻必须有效");

        ResonanceStateMachine afterTimeout = new ResonanceStateMachine(30);
        afterTimeout.observe(context(10, 3), false, List.of(e1));
        ResonanceTickResult afterTimeoutResult = afterTimeout.observe(context(41, 3), false, List.of(e1, e2));
        assertTrue(afterTimeoutResult.timedOutThisTick());
        assertFalse(afterTimeoutResult.latchedThisTick());
        assertEquals(ResonanceState.ARMED, afterTimeoutResult.state(),
                "超时后一刻先清除旧窗口，再把新进入的 E2 作为新的首个来源");
    }

    @Test
    void continuousStayDoesNotRefreshWindow() {
        ResonanceStateMachine resonance = new ResonanceStateMachine(3);
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);

        resonance.observe(context(10, 3), false, List.of(e1));
        resonance.observe(context(11, 3), false, List.of(e1));
        resonance.observe(context(12, 3), false, List.of(e1));
        resonance.observe(context(13, 3), false, List.of(e1));
        ResonanceTickResult result = resonance.observe(context(14, 3), false, List.of(e1, e2));

        assertTrue(result.timedOutThisTick());
        assertFalse(result.latchedThisTick());
        assertEquals(2, resonance.createSnapshot().armedSourceRound());
    }

    @Test
    void latchSurvivesAllActorsLeavingUntilRoundEnd() {
        ResonanceStateMachine resonance = latchedMachine();

        ResonanceTickResult afterLeave = resonance.observe(context(13, 3), false, List.of());

        assertEquals(ResonanceState.LATCHED, afterLeave.state());
        assertTrue(resonance.isLatched());
    }

    @Test
    void roundEndAndFullRestartClearStateAndEdgeMemory() {
        ResonanceStateMachine resonance = latchedMachine();

        resonance.reset(ResonanceResetReason.ROUND_END);
        assertCleared(resonance.createSnapshot());
        ResonanceTickResult afterRoundEnd = resonance.observe(context(0, 4), true, List.of(echo(2)));
        assertTrue(afterRoundEnd.currentPlayerPreviewed(), "ROUND_END 必须清除玩家边沿记忆");
        assertTrue(afterRoundEnd.armedThisTick(), "ROUND_END 必须清除残影边沿记忆");

        resonance.reset(ResonanceResetReason.FULL_RESTART);
        assertCleared(resonance.createSnapshot());
        ResonanceTickResult afterRestart = resonance.observe(context(0, 1), true, List.of());
        assertTrue(afterRestart.currentPlayerPreviewed(), "FULL_RESTART 后玩家重新进入仍是新预览");
        assertEquals(ResonanceState.DORMANT, afterRestart.state());
    }

    @Test
    void snapshotIsImmutableAndRestoreIsDeterministic() {
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);
        ResonanceStateMachine original = new ResonanceStateMachine(30);
        original.observe(context(10, 3), true, List.of(e1));
        ResonanceStateSnapshot snapshot = original.createSnapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.insideEchoSourceRounds().add(99));

        ResonanceStateMachine restored = new ResonanceStateMachine(30);
        restored.restore(snapshot);
        ResonanceTickResult originalResult = original.observe(context(12, 3), false, List.of(e1, e2));
        ResonanceTickResult restoredResult = restored.observe(context(12, 3), false, List.of(e1, e2));

        assertEquals(originalResult, restoredResult);
        assertEquals(original.createSnapshot(), restored.createSnapshot());
        assertTrue(restored.isLatched());
    }

    @Test
    void identicalInputsProduceIdenticalResultsRegardlessOfInputCollectionOrder() {
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);
        ResonanceStateMachine first = new ResonanceStateMachine(30);
        ResonanceStateMachine second = new ResonanceStateMachine(30);

        List<ResonanceTickResult> firstResults = new ArrayList<>();
        List<ResonanceTickResult> secondResults = new ArrayList<>();
        firstResults.add(first.observe(context(10, 3), false, List.of(e1)));
        secondResults.add(second.observe(context(10, 3), false, List.of(e1)));
        firstResults.add(first.observe(context(12, 3), false, List.of(e1, e2)));
        secondResults.add(second.observe(context(12, 3), false, List.of(e2, e1)));
        firstResults.add(first.observe(context(13, 3), true, List.of()));
        secondResults.add(second.observe(context(13, 3), true, List.of()));

        assertEquals(firstResults, secondResults);
        assertEquals(first.createSnapshot(), second.createSnapshot());
    }

    private static ResonanceStateMachine latchedMachine() {
        ResonanceStateMachine resonance = new ResonanceStateMachine(30);
        EchoState e1 = echo(1);
        EchoState e2 = echo(2);
        resonance.observe(context(10, 3), false, List.of(e1));
        resonance.observe(context(12, 3), false, List.of(e1, e2));
        return resonance;
    }

    private static void assertCleared(ResonanceStateSnapshot snapshot) {
        assertEquals(ResonanceState.DORMANT, snapshot.state());
        assertEquals(ResonanceStateSnapshot.NO_ARMED_TICK, snapshot.armedAtRoundTick());
        assertEquals(ResonanceStateSnapshot.NO_SOURCE_ROUND, snapshot.armedSourceRound());
        assertFalse(snapshot.currentPlayerInside());
        assertTrue(snapshot.insideEchoSourceRounds().isEmpty());
    }

    private static TickContext context(long roundTick, int currentRound) {
        return new TickContext(roundTick, DURATION_TICKS, currentRound, MAX_ROUNDS);
    }

    private static EchoState echo(int sourceRound) {
        TimelineRecording recording = new TimelineRecording(DURATION_TICKS, sourceRound);
        for (int tick = 0; tick < DURATION_TICKS; tick++) {
            recording.record(new PlayerFrame(
                    tick,
                    100.0 + tick,
                    200.0,
                    Direction.RIGHT,
                    false,
                    MovementState.CRUISING,
                    ActorPhase.AVAILABLE,
                    0,
                    AnimationState.MOVING
            ));
        }
        recording.seal();
        return EchoState.of(recording);
    }
}
