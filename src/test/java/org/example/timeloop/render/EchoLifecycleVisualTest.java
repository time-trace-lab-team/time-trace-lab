package org.example.timeloop.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoLifecycleVisualTest {

    @Test
    void lastEffectiveRoundMayBeSuppliedByFlagOrDedicatedPhase() {
        assertTrue(new EchoLifecycleVisual(1, true, EchoVisualPhase.ACTIVE, 0.0).isLastEffectiveRound());
        assertTrue(new EchoLifecycleVisual(1, true, EchoVisualPhase.LAST_EFFECTIVE_ROUND, 0.0)
                .isLastEffectiveRound());
        assertFalse(new EchoLifecycleVisual(2, false, EchoVisualPhase.ACTIVE, 0.0).isLastEffectiveRound());
    }

    @Test
    void rejectsInvalidLifecycleProjection() {
        assertThrows(IllegalArgumentException.class,
                () -> new EchoLifecycleVisual(0, false, EchoVisualPhase.ACTIVE, 0.0));
        assertThrows(NullPointerException.class,
                () -> new EchoLifecycleVisual(1, false, null, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EchoLifecycleVisual(1, false, EchoVisualPhase.ACTIVE, -0.01));
        assertThrows(IllegalArgumentException.class,
                () -> new EchoLifecycleVisual(1, false, EchoVisualPhase.ACTIVE, 1.01));
        assertThrows(IllegalArgumentException.class,
                () -> new EchoLifecycleVisual(1, false, EchoVisualPhase.LAST_EFFECTIVE_ROUND, 0.0));
    }
}
