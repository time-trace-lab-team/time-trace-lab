package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关目标提示投影（重排 v3）的单元测试。
 *
 * <p><b>最重要的一条是穷尽扫描</b>：轮次 1..3 × 9 个布尔字段的全部组合都必须能算出一句人话、不得抛异常。
 * v2 曾经在紧凑构造器里写了三条「板与门的因果组合」校验，结果第 1 轮玩家按官方解踩 C 板时
 * <b>每帧抛异常</b>、JavaFX 直接崩 —— 教训就是「某个时刻的因果」不是「状态组合的合法性」。</p>
 *
 * <p>夹具用链式 setter（{@link Fixture}），字段名与判定链一一对应，避免 12 个位置参数写串。</p>
 */
class Level03ObjectiveViewModelTest {

    @Test
    void textSaysExitIsPoweredWhenTheGateIsUnlocked() {
        Fixture view = new Fixture().exit(true).s2(true).s3(true).k(true);
        assertEquals("出口已供能：到出口旁按 E 通关", view.build().text());
        assertEquals("出口闸 3/3：S₂✓ S₃✓ K✓", view.build().gateProgress());
    }

    @Test
    void textTellsThePlayerToDiveWhenTheRayIsActiveInTheBranch() {
        Fixture diving = new Fixture().round(2).branch(true).ray(true);
        assertTrue(diving.build().text().contains("Space"), diving.build().text());
        assertTrue(diving.build().text().contains("下潜"), diving.build().text());

        Fixture approaching = new Fixture().round(2).branch(true);
        assertTrue(approaching.build().text().contains("S₂"), approaching.build().text());
        assertTrue(approaching.build().text().contains("预警"), approaching.build().text());
    }

    @Test
    void roundOneTextWalksTheControlRoute() {
        String text = new Fixture().round(1).build().text();
        assertTrue(text.contains("第 1 轮"), text);
        assertTrue(text.contains("A → C → K"), text);
        assertTrue(text.contains("18"), "C 板要驻留 18 格（v3 窗口）: " + text);
    }

    @Test
    void thirdRoundTextFollowsTheE3MainLine() {
        String waitingForA = new Fixture().round(3).build().text();
        assertTrue(waitingForA.contains("门 A"), waitingForA);

        String inInnerRegion = new Fixture().round(3).doorA(true).build().text();
        assertTrue(inInnerRegion.contains("门 B"), inInnerRegion);
        assertTrue(inInnerRegion.contains("等 E₂"), inInnerRegion);

        String atDoorB = new Fixture().round(3).doorA(true).doorB(true).build().text();
        assertTrue(atDoorB.contains("S₃"), atDoorB);

        String afterS3 = new Fixture().round(3).doorA(true).doorB(true).s3(true).build().text();
        assertTrue(afterS3.contains("S₃ 已锁存"), afterS3);
        assertTrue(afterS3.contains("出口"), afterS3);

        // 门 C 开着而 S₃ 还没踩：提示应当指向「趁窗口穿门 C」
        String atDoorC = new Fixture().round(3).doorC(true).build().text();
        assertTrue(atDoorC.contains("窗口"), atDoorC);
        // S₃ 一旦锁存，提示就换成「折回门 B → 门 C → 出口」（这是第三轮的主线）
        String afterS3AtDoorC = new Fixture().round(3).doorC(true).s3(true).build().text();
        assertTrue(afterS3AtDoorC.contains("S₃ 已锁存"), afterS3AtDoorC);
    }

    @Test
    void secondRoundTextTellsThePlayerToWaitForEchoOneThenTakeTheBranch() {
        String waiting = new Fixture().round(2).build().text();
        assertTrue(waiting.contains("E₁"), waiting);
        String crossing = new Fixture().round(2).doorA(true).build().text();
        assertTrue(crossing.contains("S₂"), crossing);
        assertTrue(crossing.contains("B 板"), crossing);
    }

    @Test
    void gateProgressCountsTheThreeConditions() {
        assertEquals("出口闸 0/3：S₂✗ S₃✗ K✗", new Fixture().build().gateProgress());
        assertEquals("出口闸 1/3：S₂✓ S₃✗ K✗", new Fixture().s2(true).build().gateProgress());
        assertEquals("出口闸 2/3：S₂✗ S₃✓ K✓", new Fixture().s3(true).k(true).build().gateProgress());
    }

    @Test
    void tierTextsDescribeTheV3Chain() {
        Level03ObjectiveViewModel view = new Fixture().round(2).build();
        assertTrue(view.tierOne().contains("S₂ + S₃ + K"), view.tierOne());
        assertTrue(view.tierTwo().contains("S₂"), view.tierTwo());
        assertTrue(view.tierThreeRoute().contains("K(1512)"), view.tierThreeRoute());
        assertTrue(view.tierThreeRoute().contains("S₂(456)"), view.tierThreeRoute());
        assertTrue(view.tierThreeRoute().contains("S₃(816)"), view.tierThreeRoute());
        assertTrue(view.tierThreeRoute().contains("门C(1032)"), view.tierThreeRoute());
        assertTrue(view.forkHint().contains("向南"), view.forkHint());
        assertTrue(view.forkHint().contains("向北"), view.forkHint());
    }

    @Test
    void finalRoundBadgeOnlyShowsOnTheLastRound() {
        assertEquals("E₁：最后有效轮", new Fixture().round(3).build().finalRoundBadge());
        assertEquals("", new Fixture().round(2).build().finalRoundBadge());
    }

    @Test
    void roundRangeIsValidatedButNothingElse() {
        assertThrows(IllegalArgumentException.class, () -> new Level03ObjectiveViewModel(
                0, 3, false, false, false, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new Level03ObjectiveViewModel(
                4, 3, false, false, false, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new Level03ObjectiveViewModel(
                2, 3, true, false, false, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new Level03ObjectiveViewModel(
                1, 0, false, false, false, false, false, false, false, false, false, false));
        // 任意「板与门」组合都不得被当成非法：这是 v2 崩过的坑，此处正向钉住。
        assertNotNull(new Fixture().round(1).doorC(true).build().text());
        assertNotNull(new Fixture().round(1).k(true).build().text());
        assertNotNull(new Fixture().round(1).doorC(true).doorB(true).build().text());
    }

    /**
     * 穷尽扫描：轮次 1..3 × 9 个布尔字段的全部组合（1536 种）都必须算得出提示。
     *
     * <p>尤其是 v2 崩掉过的两种真实局面：第 1 轮玩家踩 C 板（门 C 开、门 B 无人压、轮次 1）、
     * 第 1 轮玩家踩 K 板（K 板被压但轮次 1）。它们必须是<b>合法</b>输入。</p>
     */
    @Test
    void everyStateCombinationProducesTextWithoutThrowing() {
        List<String> failures = new ArrayList<>();
        int combinations = 0;
        for (int round = 1; round <= 3; round++) {
            for (int mask = 0; mask < (1 << 9); mask++) {
                Level03ObjectiveViewModel view = new Level03ObjectiveViewModel(
                        round, 3, round == 3,
                        bit(mask, 0),   // doorAOpen
                        bit(mask, 1),   // inEcho2Branch
                        bit(mask, 2),   // rayActive
                        bit(mask, 3),   // switchS2On
                        bit(mask, 4),   // switchS3On
                        bit(mask, 5),   // plateKHeld
                        bit(mask, 6),   // doorBOpen
                        bit(mask, 7),   // doorCOpen
                        bit(mask, 8));  // exitUnlocked
                combinations++;
                String text = view.text();
                if (text == null || text.isBlank()) {
                    failures.add("轮次 " + round + " mask=" + mask + " 提示为空");
                }
                assertNotNull(view.gateProgress());
                assertFalse(view.gateProgress().isBlank());
            }
        }
        assertEquals(3 * 512, combinations);
        assertTrue(failures.isEmpty(), String.join("；", failures));
    }

    private static boolean bit(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    /** 链式夹具：默认「第三轮开局、什么条件都没满足」。 */
    private static final class Fixture {
        private int round = 3;
        private boolean doorA;
        private boolean branch;
        private boolean ray;
        private boolean s2;
        private boolean s3;
        private boolean k;
        private boolean doorB;
        private boolean doorC;
        private boolean exit;

        Fixture round(int value) {
            this.round = value;
            return this;
        }

        Fixture doorA(boolean value) {
            this.doorA = value;
            return this;
        }

        Fixture branch(boolean value) {
            this.branch = value;
            return this;
        }

        Fixture ray(boolean value) {
            this.ray = value;
            return this;
        }

        Fixture s2(boolean value) {
            this.s2 = value;
            return this;
        }

        Fixture s3(boolean value) {
            this.s3 = value;
            return this;
        }

        Fixture k(boolean value) {
            this.k = value;
            return this;
        }

        Fixture doorB(boolean value) {
            this.doorB = value;
            return this;
        }

        Fixture doorC(boolean value) {
            this.doorC = value;
            return this;
        }

        Fixture exit(boolean value) {
            this.exit = value;
            return this;
        }

        Level03ObjectiveViewModel build() {
            return new Level03ObjectiveViewModel(round, 3, round == 3, doorA, branch, ray,
                    s2, s3, k, doorB, doorC, exit);
        }
    }
}
