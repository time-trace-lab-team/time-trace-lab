package org.example.timeloop.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedStepLoopTest {

    private static final long NS_PER_STEP = 1_000_000_000L / 60;
    private static final long NS_PER_60FPS = Math.round(1_000_000_000L / 60.0);

    @Test
    void firstFrameOnlyEstablishesTimeBaseline() {
        CountingUpdatePort port = new CountingUpdatePort();
        FixedStepLoop loop = new FixedStepLoop(port);

        loop.onAnimationFrame(0L, GamePhase.PLAYING);

        assertEquals(0, port.calls);
    }

    @Test
    void oneRenderFrameCanDispatchMultipleLogicalTicks() {
        CountingUpdatePort port = new CountingUpdatePort();
        FixedStepLoop loop = new FixedStepLoop(port);

        loop.onAnimationFrame(0L, GamePhase.BOOT);
        loop.onAnimationFrame(50_000_000L, GamePhase.PLAYING);

        assertEquals(3, port.calls);
    }

    @Test
    void nonPlayingPhaseDoesNotDispatchLogicOrAccumulatePauseTime() {
        CountingUpdatePort port = new CountingUpdatePort();
        FixedStepLoop loop = new FixedStepLoop(port);

        loop.onAnimationFrame(0L, GamePhase.BOOT);
        loop.onAnimationFrame(NS_PER_60FPS, GamePhase.PLAYING);
        loop.onAnimationFrame(NS_PER_60FPS + 2_000_000_000L, GamePhase.PAUSED);
        loop.onAnimationFrame(NS_PER_60FPS + 2_000_000_000L + NS_PER_60FPS, GamePhase.PLAYING);

        assertEquals(2, port.calls);
    }

    @Test
    void renderRateChangesDoNotChangeLogicalTickCount() {
        CountingUpdatePort at30Fps = new CountingUpdatePort();
        CountingUpdatePort at120Fps = new CountingUpdatePort();
        FixedStepLoop loop30 = new FixedStepLoop(at30Fps);
        FixedStepLoop loop120 = new FixedStepLoop(at120Fps);

        loop30.onAnimationFrame(0L, GamePhase.BOOT);
        loop120.onAnimationFrame(0L, GamePhase.BOOT);

        long time30 = 0L;
        long frame30 = Math.round(NS_PER_60FPS * 2.0);
        for (int i = 0; i < 30; i++) {
            time30 += frame30;
            loop30.onAnimationFrame(time30, GamePhase.PLAYING);
        }

        long time120 = 0L;
        long frame120 = Math.round(NS_PER_60FPS / 2.0);
        for (int i = 0; i < 120; i++) {
            time120 += frame120;
            loop120.onAnimationFrame(time120, GamePhase.PLAYING);
        }

        assertEquals(at30Fps.calls, at120Fps.calls);
        assertEquals(60, at30Fps.calls);
    }

    @Test
    void interpolationAlphaIsForwardedFromClock() {
        CountingUpdatePort port = new CountingUpdatePort();
        FixedStepLoop loop = new FixedStepLoop(port);

        loop.onAnimationFrame(0L, GamePhase.BOOT);
        loop.onAnimationFrame(NS_PER_STEP / 2, GamePhase.PLAYING);

        assertTrue(loop.interpolationAlpha() >= 0.0);
        assertTrue(loop.interpolationAlpha() < 1.0);
    }

    @Test
    void nullUpdatePortIsRejected() {
        assertThrows(NullPointerException.class, () -> new FixedStepLoop(null));
    }

    private static final class CountingUpdatePort implements TickUpdatePort {
        private int calls;

        @Override
        public void stepOnce() {
            calls++;
        }
    }
}
