package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标提示的文案与校验测试。
 *
 * <p>锁的是"玩家能不能看懂下一步"：每种板占用组合都必须给出一句可执行的话，
 * 且终局/无解态必须明确指向按 R 重开，不能只留一句模糊描述。</p>
 */
class ObjectiveViewModelTest {

    @Test
    void doorUnlockedAlwaysTellsThePlayerToPressInteract() {
        assertEquals("门已解锁：站到右驻留板上按 E 通关",
                view(2, false, true, true, false, true).text());
        // 即使两块板都空了，门一旦解锁（锁存）提示仍然是去按 E
        assertEquals("门已解锁：站到右驻留板上按 E 通关",
                view(2, false, false, false, false, true).text());
    }

    @Test
    void echoHoldingRightPlateMeansThisRoundIsUnwinnable() {
        assertTrue(view(3, false, false, false, true, false).text().contains("按 R 重开"));
        assertTrue(view(3, true, false, false, true, false).text().contains("本轮无解"));
    }

    @Test
    void guidesThePlayerToTheRightPlateWhenTheEchoHoldsTheLeftOne() {
        assertEquals("残影已压住左板：你去右驻留板站住，门一解锁就按 E",
                view(2, false, true, false, false, false).text());
        assertEquals("两块板都已压住，等门解锁后按 E",
                view(2, false, true, true, false, false).text());
    }

    @Test
    void firstRoundTeachesParkingOnTheLeftPlate() {
        assertEquals("第 1 轮：走到左驻留板并停住（松开方向键）",
                view(1, false, false, false, false, false).text());
        assertEquals("已压住左驻留板：保持不动，等本轮结束让残影记住这条路线",
                view(1, true, false, false, false, false).text());
    }

    @Test
    void laterRoundsWithAnEmptyLeftPlateTellThePlayerToRestart() {
        assertTrue(view(2, false, false, false, false, false).text().contains("第 1 轮务必站上左板"));
        assertTrue(view(3, true, false, false, false, false).text().contains("按 R 重开"));
    }

    @Test
    void rejectsImpossibleOrOutOfRangeStates() {
        assertThrows(IllegalArgumentException.class,
                () -> view(0, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(4, false, false, false, false, false));
        // 同一块板不可能同时被玩家与残影占用
        assertThrows(IllegalArgumentException.class,
                () -> view(2, true, true, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(2, false, false, true, true, false));
    }

    private static ObjectiveViewModel view(int round,
                                           boolean leftPlayer,
                                           boolean leftEcho,
                                           boolean rightPlayer,
                                           boolean rightEcho,
                                           boolean doorUnlocked) {
        return new ObjectiveViewModel(round, 3, leftPlayer, leftEcho, rightPlayer, rightEcho, doorUnlocked);
    }
}
