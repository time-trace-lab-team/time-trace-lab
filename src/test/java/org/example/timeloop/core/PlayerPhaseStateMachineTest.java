package org.example.timeloop.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerPhaseStateMachineTest {

    @Test
    void defaultPhaseAndRecoveryCoverExactly30And45Ticks() {
        PlayerPhaseStateMachine phase = new PlayerPhaseStateMachine();

        for (int tick = 0; tick < 30; tick++) {
            var state = phase.advance(tick < 10, tick);
            assertEquals(ActorPhase.PHASED, state.phase(), "PHASED tick=" + tick);
            assertEquals(30 - tick, state.ticksRemaining());
        }
        for (int tick = 30; tick < 75; tick++) {
            var state = phase.advance(false, tick);
            assertEquals(ActorPhase.RECOVERING, state.phase(), "RECOVERING tick=" + tick);
            assertEquals(75 - tick, state.ticksRemaining());
        }
        assertEquals(new PlayerPhaseStateMachine.Snapshot(ActorPhase.AVAILABLE, 0),
                phase.advance(false, 75));
    }

    @Test
    void heldInputAndRecoveryEdgesCannotRetrigger() {
        PlayerPhaseStateMachine phase = new PlayerPhaseStateMachine(2, 2);

        assertEquals(ActorPhase.PHASED, phase.advance(true, 0).phase());
        assertEquals(1, phase.advance(true, 1).ticksRemaining(), "按住不能刷新 PHASED");
        assertEquals(ActorPhase.RECOVERING, phase.advance(false, 2).phase());
        assertEquals(1, phase.advance(true, 3).ticksRemaining(), "恢复期新边沿不能触发");
        assertEquals(ActorPhase.AVAILABLE, phase.advance(true, 4).phase(), "持续按住进入 AVAILABLE 也不能触发");
        phase.advance(false, 5);
        assertEquals(ActorPhase.PHASED, phase.advance(true, 6).phase(), "释放后的新边沿可再次触发");
    }

    @Test
    void everyResetReasonClearsStateTickAndHeldEdgeMemory() {
        for (PlayerEffectResetReason reason : PlayerEffectResetReason.values()) {
            PlayerPhaseStateMachine phase = new PlayerPhaseStateMachine();
            phase.advance(true, 20);
            phase.reset(reason);

            assertEquals(new PlayerPhaseStateMachine.Snapshot(ActorPhase.AVAILABLE, 0), phase.snapshot());
            assertEquals(ActorPhase.PHASED, phase.advance(true, 0).phase(), reason.toString());
        }
    }

    @Test
    void validatesDurationsAndMonotonicRoundTick() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerPhaseStateMachine(0, 45));
        assertThrows(IllegalArgumentException.class, () -> new PlayerPhaseStateMachine(30, 0));

        PlayerPhaseStateMachine phase = new PlayerPhaseStateMachine();
        assertThrows(IllegalArgumentException.class, () -> phase.advance(false, -1));
        phase.advance(false, 0);
        assertThrows(IllegalArgumentException.class, () -> phase.advance(false, 0));
        assertThrows(IllegalArgumentException.class, () -> phase.advance(false, -1));
        assertThrows(NullPointerException.class, () -> phase.reset(null));
    }
}
