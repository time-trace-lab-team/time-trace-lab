package org.example.timeloop.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickStepResultTest {

    @Test
    void onlyAdvancedAllowsCurrentFrameCatchUpToContinue() {
        assertFalse(TickStepResult.NO_ADVANCE.shouldContinueFrame());
        assertTrue(TickStepResult.ADVANCED.shouldContinueFrame());
        assertFalse(TickStepResult.ROUND_END.shouldContinueFrame());
    }
}
