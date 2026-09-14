package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L2-A §二.3：两句必备提示 + 残影代际表达 + 校验。 */
class Level02ObjectiveViewModelTest {

    @Test
    void firstRoundTellsThePlayerToHoldTheOuterPlateLongEnough() {
        assertTrue(view(1, false, false, -1, false, -1, false)
                .text().contains("先踩门外板"));
        assertEquals("门外板踩得好：踩久一点再离开，为下一轮留门，最后停在主驻留板",
                view(1, true, false, -1, false, -1, false).text());
    }

    @Test
    void playerInsideTheRoomIsToldToStayUntilRoundEnd() {
        assertEquals("你已进房：需驻留到本轮结束（门外板一松，房门就回锁）",
                view(2, true, true, 0, false, -1, false).text());
    }

    @Test
    void thirdRoundNamesTheEchoGenerationHoldingEachPlate() {
        assertEquals("内板由E2压住、主驻留板由E1压住：闸门开的那一刻到旁边按 E",
                view(3, true, true, 2, true, 1, false).text());
        assertEquals("内板已由E2压住：你去踩门外板踩久一点，再停到主驻留板",
                view(3, true, true, 2, false, -1, false).text());
        assertEquals("主驻留板已由E1压住：还差内板（只有从第 5 列南侧借窗口进房）",
                view(3, true, false, -1, true, 1, false).text());
    }

    @Test
    void unlockedGateAlwaysPointsToTheInteractKey() {
        assertEquals("闸门已解锁：到闸门旁按 E 通关",
                view(3, true, true, 2, true, 1, true).text());
    }

    @Test
    void rejectsInconsistentOrOutOfRangeState() {
        assertThrows(IllegalArgumentException.class,
                () -> view(0, false, false, -1, false, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(5, false, false, -1, false, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(2, true, true, -1, false, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(2, true, false, -1, true, -1, false));
    }

    private static Level02ObjectiveViewModel view(int round, boolean outer, boolean inner, int innerRound,
                                                  boolean main, int mainRound, boolean unlocked) {
        return new Level02ObjectiveViewModel(round, 4, outer, inner, innerRound, main, mainRound, unlocked);
    }
}
