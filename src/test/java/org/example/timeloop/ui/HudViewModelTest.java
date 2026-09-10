package org.example.timeloop.ui;

import org.example.timeloop.replay.TickContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HudViewModelTest {

    @Test
    void derivesOneSharedCountdownAndRoundTextFromTickContext() {
        HudViewModel model = HudViewModel.from(new TickContext(0, 960, 2, 3));

        assertEquals(16, model.remainingSeconds());
        assertEquals(2, model.currentRound());
        assertEquals(3, model.maxRounds());
        assertEquals("剩余 16 秒", model.countdownText());
        assertEquals("第 2 / 3 轮", model.roundText());
    }

    @Test
    void countdownUsesCeilingAndNeverShowsZeroOnLastValidTick() {
        assertEquals(2,
                HudViewModel.from(new TickContext(0, 61, 1, 3)).remainingSeconds());
        assertEquals(1,
                HudViewModel.from(new TickContext(1, 61, 1, 3)).remainingSeconds());
        assertEquals(1,
                HudViewModel.from(new TickContext(60, 61, 1, 3)).remainingSeconds());
    }

    @Test
    void modelRejectsNullAndInvalidRoundValues() {
        assertThrows(NullPointerException.class, () -> HudViewModel.from(null));
        assertThrows(IllegalArgumentException.class, () -> new HudViewModel(0, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new HudViewModel(1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new HudViewModel(1, 4, 3));
    }

    @Test
    void modelHasOnlyFinalRecordFields() {
        for (Field field : HudViewModel.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertEquals(true, Modifier.isFinal(field.getModifiers()),
                        "HUD 投影字段必须为 final: " + field.getName());
            }
        }
    }
}
