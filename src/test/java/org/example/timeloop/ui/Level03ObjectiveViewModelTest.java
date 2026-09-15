package org.example.timeloop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三关 HUD 提示（设定书 §9.2 / §9.4，卡 {@code L03-DEV3} §三）。
 *
 * <p>锁的是「玩家能不能看懂下一步」：一级/二级/三级提示各有固定文案，J 分岔必须说清上下两条路，
 * 第一残影进入最后有效轮时必须显式提示，且提示只讲信息、不改任何时钟（纯函数）。</p>
 */
class Level03ObjectiveViewModelTest {

    @Test
    void firstRoundTeachesTheOuterControlRoute() {
        assertTrue(view(1, false, false, false, false, false, false, false)
                .text().contains("A → C → D"));
        assertTrue(view(1, false, false, false, false, false, false, false)
                .text().contains("踩够久"));
        assertTrue(view(1, false, false, false, false, false, false, false)
                .text().contains("D 压到轮末"));
    }

    @Test
    void secondRoundTellsThePlayerToWaitForDoorAThenTakeBranchB() {
        assertTrue(view(2, false, false, false, false, false, false, false)
                .text().contains("等残影 E₁ 压住 A 板开门"));
        String inside = view(2, false, true, false, false, false, false, false).text();
        assertTrue(inside.contains("J 处向上进 B 支路"), inside);
        assertTrue(inside.contains("下潜"), inside);
    }

    @Test
    void rayActiveInBranchBTellsThePlayerToPhaseNow() {
        String active = view(2, false, true, true, false, false, false, false).text();
        assertTrue(active.contains("Space"), active);
        assertTrue(active.contains("下潜"), active);
        String warning = view(2, false, true, false, false, false, false, false).text();
        assertFalse(warning.contains("射线激活"), warning);
    }

    @Test
    void thirdRoundNamesTheEchoThatOpensEachDoor() {
        String waiting = view(3, true, true, false, false, false, false, false).text();
        assertTrue(waiting.contains("和 E₂ 一起进内区"), waiting);
        assertTrue(waiting.contains("等 E₂ 压住 B"), waiting);

        String doorBOpen = view(3, true, true, false, false, true, false, false).text();
        assertTrue(doorBOpen.contains("门 B 开了"), doorBOpen);
        assertTrue(doorBOpen.contains("E₁ 的 C 窗口"), doorBOpen);

        String bothOpen = view(3, true, true, false, false, true, true, false).text();
        assertTrue(bothOpen.contains("门 B 与门 C 同时开着"), bothOpen);
    }

    @Test
    void poweredExitAlwaysPointsToTheInteractKey() {
        String powered = view(3, true, false, false, false, false, false, true).text();
        assertEquals("出口已供能：到出口旁按 E 通关", powered);
        // 已供能时其它状态一律让位给「按 E」。
        String poweredWithEverything = view(3, true, true, true, true, true, true, true).text();
        assertEquals("出口已供能：到出口旁按 E 通关", poweredWithEverything);
    }

    @Test
    void threeHintTiersAndForkHintMatchTheSettingBook() {
        Level03ObjectiveViewModel vm = view(2, false, false, false, false, false, false, false);
        assertEquals("出口需要 D 板供能；两道中间门由过去开启。", vm.tierOne());
        assertEquals("第二轮到达 B 的时刻，会决定第三轮门 B 何时打开。", vm.tierTwo());
        assertTrue(vm.tierThreeRoute().contains("A(336) → C(600) → D(1056)"), vm.tierThreeRoute());
        assertTrue(vm.tierThreeRoute().contains("门A(336) → J(432) → 射线(552) → B(624)"),
                vm.tierThreeRoute());
        assertTrue(vm.forkHint().contains("上方通向 B 板"), vm.forkHint());
        assertTrue(vm.forkHint().contains("右侧是主通道"), vm.forkHint());
    }

    @Test
    void finalValidRoundBadgeOnlyAppearsInTheLastRound() {
        assertEquals("", view(2, false, false, false, false, false, false, false).finalRoundBadge());
        assertEquals("", view(3, false, false, false, false, false, false, false).finalRoundBadge());
        assertEquals("E₁：最后有效轮",
                view(3, true, false, false, false, false, false, false).finalRoundBadge());
    }

    @Test
    void textIsPureAndDoesNotChangeWithRepeatedCalls() {
        Level03ObjectiveViewModel vm = view(3, true, true, false, false, true, false, false);
        String first = vm.text();
        String second = vm.text();
        assertEquals(first, second, "提示必须是纯函数");
        assertEquals(vm.tierOne(), vm.tierOne());
        assertEquals(vm.tierTwo(), vm.tierTwo());
        assertEquals(vm.tierThreeRoute(), vm.tierThreeRoute());
    }

    @Test
    void rejectsOnlyTheGenuinelyImpossibleStates() {
        assertThrows(IllegalArgumentException.class, () -> view(0, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> view(4, false, false, false, false, false, false, false));
        // 「最后有效轮」只可能出现在最后一轮
        assertThrows(IllegalArgumentException.class, () -> view(2, true, false, false, false, false, false, false));
    }

    /**
     * <b>穷举输入空间</b>：任意合法轮次 × 任意布尔组合，{@code text()} 与四个分层 accessor 都必须
     * 给出一句人话、<b>绝不抛异常</b>。
     *
     * <p>这条用例是被一次真实崩溃逼出来的：本类曾校验「门 C 开着时门 B 必须也开着且轮次 ≥3」
     * 与「供能只可能来自残影」，而第 1 轮玩家踩 C 板（门 C 开、门 B 无人、轮次 1）正是官方解的
     * 第一步、踩 D 板供能时门 C 早已松开 —— 于是 HUD 每帧抛异常，游戏刷屏报错。
     * 手挑几个组合的用例扫不到这种状态，所以这里改成<b>全枚举</b>：3 轮 × 64 种布尔组合。</p>
     */
    @Test
    void everyLegalRoundAndFlagCombinationProducesTextWithoutThrowing() {
        int checked = 0;
        for (int round = 1; round <= 3; round++) {
            for (int mask = 0; mask < 64; mask++) {
                boolean doorAOpen = (mask & 1) != 0;
                boolean inBranchB = (mask & 2) != 0;
                boolean rayActive = (mask & 4) != 0;
                boolean doorBOpen = (mask & 8) != 0;
                boolean doorCOpen = (mask & 16) != 0;
                boolean exitPowered = (mask & 32) != 0;
                boolean finalRound = round == 3;

                Level03ObjectiveViewModel vm = new Level03ObjectiveViewModel(round, 3, finalRound,
                        doorAOpen, inBranchB, rayActive, doorBOpen, doorCOpen, exitPowered);

                assertFalse(vm.text().isBlank(),
                        "组合 round=" + round + " mask=" + mask + " 必须给出提示");
                assertFalse(vm.tierOne().isBlank());
                assertFalse(vm.tierTwo().isBlank());
                assertFalse(vm.tierThreeRoute().isBlank());
                assertFalse(vm.forkHint().isBlank());
                checked++;
            }
        }
        assertEquals(192, checked, "3 轮 × 64 组合全都要扫过");
    }

    /** 官方解第一轮的三个状态：踩 A → 踩 C（门 C 开、门 B 无人）→ 踩 D（供能开、门 C 已松）。 */
    @Test
    void firstRoundOfficialRouteStatesAreAllLegal() {
        // 站在 A 板上（门 A 开）
        assertFalse(view(1, false, true, false, false, false, false, false).text().isBlank());
        // 站在 C 板上：这正是曾经抛「门 C 只能由第一残影在第三轮打开」的那一格
        assertFalse(view(1, false, false, false, false, false, true, false).text().isBlank());
        // 站在 D 板上：这正是曾经抛「出口供能只可能来自第一残影驻留 D」的那一格
        assertFalse(view(1, false, false, false, false, false, false, true).text().isBlank());
    }

    /**
     * 反例保护：{@code doorAOpen=false && inBranchB=true} 是<b>合法</b>状态，不得再抛异常。
     *
     * <p>真实玩法必然出现这一组合：E₁ 在刻 384 离开 A 板（门 A 回锁），而第二轮 / 第三轮的玩家
     * 此刻正在 B 支路里跑到轮末。曾经的校验把「门此刻是否开着」当成「能不能已经在里面」，
     * 会让这一格的 HUD 投影直接抛 {@link IllegalArgumentException}。</p>
     */
    @Test
    void doorAClosedWhileAlreadyInBranchBIsLegalAndStillGuidesThePlayer() {
        Level03ObjectiveViewModel vm = view(2, false, false, true, false, false, false, false);
        assertFalse(vm.doorAOpen());
        assertTrue(vm.inBranchB());
        assertFalse(vm.rayActive());
        assertTrue(vm.text().contains("沿 B 支路往上走"), vm.text());
        assertTrue(vm.text().contains("Space"), vm.text());
    }

    private static Level03ObjectiveViewModel view(int round,
                                                  boolean firstEchoFinalRound,
                                                  boolean doorAOpen,
                                                  boolean inBranchB,
                                                  boolean rayActive,
                                                  boolean doorBOpen,
                                                  boolean doorCOpen,
                                                  boolean exitPowered) {
        return new Level03ObjectiveViewModel(round, 3, firstEchoFinalRound, doorAOpen, inBranchB,
                rayActive, doorBOpen, doorCOpen, exitPowered);
    }
}
