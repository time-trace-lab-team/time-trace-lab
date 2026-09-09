package org.example.timeloop.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GamePhaseTest {

    private static final GamePhase[] APPROVED_PHASES = {
            GamePhase.BOOT,
            GamePhase.MENU,
            GamePhase.LEVEL_SELECT,
            GamePhase.TUTORIAL,
            GamePhase.READY,
            GamePhase.PLAYING,
            GamePhase.PAUSED,
            GamePhase.RESETTING,
            GamePhase.RESULT,
            GamePhase.FAILED
    };

    @Test
    void declaresApprovedPhasesInOrderAndOnlyPlayingAdvancesLogic() {
        assertArrayEquals(APPROVED_PHASES, GamePhase.values(),
                "GamePhase 的阶段集合或声明顺序不符合共享阶段契约");

        for (GamePhase phase : GamePhase.values()) {
            assertEquals(phase == GamePhase.PLAYING, phase.advancesLogic(),
                    phase + " 的 advancesLogic() 取值不符合共享阶段契约");
        }
    }
}
