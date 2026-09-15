package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L2-A §二.3：两句必备提示 + 残影代际表达 + 锁存开关语义 + 校验。 */
class Level02ObjectiveViewModelTest {

    @Test
    void firstRoundTellsThePlayerToHoldTheOuterPlateLongEnough() {
        assertTrue(view(1, false, false, -1, false, -1, false, false)
                .text().contains("先踩门外板"));
        assertEquals("门外板踩得好：踩久一点再离开，为下一轮留门，最后停在主板",
                view(1, true, false, -1, false, -1, false, false).text());
    }

    @Test
    void playerInsideTheRoomIsToldToStayUntilRoundEnd() {
        assertEquals("你已进房：需驻留到本轮结束（门外板一松，房门就回锁）",
                view(2, true, true, 0, false, -1, false, false).text());
    }

    /** 第三块是锁存开关：没踩之前「还差开关」；踩过之后不再要求有人压着。 */
    @Test
    void thirdPlateIsALatchingSwitchThatNobodyHasToStandOn() {
        assertEquals("内板由E2压住、主板由E1压住：还差开关 —— 去踩一脚，踩上即锁存，不用压着",
                view(3, true, true, 2, true, 1, false, false).text());
        assertEquals("开关已锁存、内板由E2压住、主板由E1压住：闸门开的那一刻到旁边按 E",
                view(3, true, true, 2, true, 1, true, false).text());
    }

    @Test
    void thirdRoundNamesTheEchoGenerationHoldingEachPlate() {
        assertEquals("内板已由E2压住：你去踩门外板踩久一点，再停到主板",
                view(3, true, true, 2, false, -1, false, false).text());
        assertEquals("主板已由E1压住：还差内板（等中继板窗口，借窗进内室踩内板）",
                view(3, true, false, -1, true, 1, false, false).text());
        // 改版后「只有从第 5 列南侧借窗口进房」已失效，不得再出现在提示里。
        assertFalse(view(3, true, false, -1, true, 1, false, false).text().contains("第 5 列"),
                "旧链路说法必须删除");
    }

    /** 开关已锁存 → 只差哪块板就说哪块，且明确「不用再有人压着」（锁存不占 actor）。 */
    @Test
    void latchedSwitchNeverAsksAnyoneToStandOnIt() {
        assertEquals("开关已锁存（不用再有人压着）：内板由E2压住，还差主板 —— 你去踩门外板留窗口，再停在主板上",
                view(3, true, true, 2, false, -1, true, false).text());
        assertEquals("开关已锁存（不用再有人压着）：主板由E1压住，还差内板（等中继板窗口，借窗进内室踩内板）",
                view(3, true, false, -1, true, 1, true, false).text());
        assertEquals("开关已锁存（不用再有人压着）：等两条残影分别压住内板与主板",
                view(3, true, false, -1, false, -1, true, false).text());
    }

    /** 第 3 轮空场提示与桌面运行图 HUD 同义：踩下开关（锁存）→ 等两条残影 → 走到终点按 E。 */
    @Test
    void thirdRoundTellsThePlayerToLatchTheSwitchThenWaitForTheEchoes() {
        assertEquals("第 3 轮：踩下开关（锁存），等两条残影压住内板与主板 —— 然后走到终点按 E",
                view(3, false, false, -1, false, -1, false, false).text());
    }

    @Test
    void unlockedGateAlwaysPointsToTheInteractKey() {
        assertEquals("闸门已解锁：到闸门旁按 E 通关",
                view(3, true, true, 2, true, 1, true, true).text());
    }

    @Test
    void rejectsInconsistentOrOutOfRangeState() {
        assertThrows(IllegalArgumentException.class,
                () -> view(0, false, false, -1, false, -1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(5, false, false, -1, false, -1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(2, true, true, -1, false, -1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(2, true, false, -1, true, -1, false, false));
    }

    private static Level02ObjectiveViewModel view(int round, boolean outer, boolean inner, int innerRound,
                                                  boolean main, int mainRound, boolean switchLatched,
                                                  boolean unlocked) {
        return new Level02ObjectiveViewModel(round, 4, outer, inner, innerRound, main, mainRound,
                switchLatched, unlocked);
    }
}
