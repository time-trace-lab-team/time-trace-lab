package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标提示的文案与校验测试（L01-GATE-MERGE 后更新）。
 *
 * <p>锁的是"玩家能不能看懂下一步"：右板已改称<b>开关</b>（踩上即开启、离开不关闭、保持到本轮结束），
 * 因此 {@code rightHeldBy*} 表示"开关是否已开启"，不再表示"此刻有人站着"。</p>
 */
class ObjectiveViewModelTest {

    @Test
    void doorUnlockedAlwaysTellsThePlayerToPressInteract() {
        assertEquals("门已解锁：到闸门（开关正上方一格）按 E 通关",
                view(2, false, true, true, false, true).text());
        // 即使两块板都空了，门一旦解锁（锁存）提示仍然是去按 E
        assertEquals("门已解锁：到闸门（开关正上方一格）按 E 通关",
                view(2, false, false, false, false, true).text());
    }

    /** 开关是锁存的：残影开启的开关不再意味着"本轮无解"，而是条件已满足的一半。 */
    @Test
    void echoOpeningTheSwitchIsProgressNotADeadEnd() {
        assertEquals("开关已开启、左板已被残影压住：去闸门按 E",
                view(3, false, true, false, true, false).text());
        assertEquals("残影已压住左板：你去踩一下开关（踩上即开启，离开也不会关）",
                view(3, false, true, false, false, false).text());
    }

    @Test
    void switchOnGuidesThePlayerToParkOnTheLeftPlate() {
        assertEquals("开关已开启：去左驻留板停住，把「压住左板」留给下一轮",
                view(2, false, false, true, false, false).text());
        assertEquals("开关已开启：保持不动，等本轮结束让残影记住这条路线",
                view(2, true, false, true, false, false).text());
    }

    @Test
    void firstRoundTeachesSwitchThenLeftPlate() {
        assertEquals("第 1 轮：先踩开关（踩上即开启），再去左驻留板停住",
                view(1, false, false, false, false, false).text());
        assertEquals("已压住左驻留板：保持不动，等本轮结束让残影记住这条路线",
                view(1, true, false, false, false, false).text());
    }

    @Test
    void laterRoundsWithNothingDoneTellThePlayerToRestart() {
        assertTrue(view(2, false, false, false, false, false).text().contains("第 1 轮先踩开关再压左板"));
        assertTrue(view(3, true, false, false, false, false).text().contains("按 R 重开"));
    }

    /**
     * 一块驻留板可以<b>同时</b>被玩家与残影占用（L03-DEV3 裁决）：
     * 开关锁存的两个标志可以同时为真；左板的两个标志<b>同样</b>可以同时为真。
     *
     * <p>旧版本在这里断言 {@code leftPlayer && leftEcho} 必须抛异常，把「底层模型只有一个占用槽位」
     * 错当成了「玩法规则不允许」—— 底层当时是静默丢弃第二人，两边一起把缺陷藏住了。
     * 该"不变量"已按工作令删除，改为断言它在两种情形下都合法且文案正确。</p>
     */
    @Test
    void bothActorsMayHoldEitherPlateAtTheSameTime() {
        // 开关：两人先后踩上 → 两个标志同时为真
        assertEquals("开关已开启、左板已被残影压住：去闸门按 E",
                view(2, false, true, true, true, false).text());

        // 左板：玩家与残影同踩 → 合法，且提示仍然是「去踩开关」（残影会继续压着左板）
        assertEquals("残影已压住左板：你去踩一下开关（踩上即开启，离开也不会关）",
                view(2, true, true, false, false, false).text());

        // 左板两人同踩 + 开关已开 → 门条件已满足，提示去按 E
        assertEquals("开关已开启、左板已被残影压住：去闸门按 E",
                view(2, true, true, true, false, false).text());
    }

    @Test
    void rejectsOutOfRangeRounds() {
        assertThrows(IllegalArgumentException.class,
                () -> view(0, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> view(4, false, false, false, false, false));
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
