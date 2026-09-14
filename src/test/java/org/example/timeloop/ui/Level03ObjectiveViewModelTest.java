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
        assertTrue(vm.tierThreeRoute().contains("A(120) → C(288) → D(576)"), vm.tierThreeRoute());
        assertTrue(vm.tierThreeRoute().contains("门A(120) → J(192) → 射线(288) → B(384)"),
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
    void rejectsInconsistentState() {
        assertThrows(IllegalArgumentException.class, () -> view(0, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> view(4, false, false, false, false, false, false, false));
        // 「最后有效轮」只可能出现在最后一轮
        assertThrows(IllegalArgumentException.class, () -> view(2, true, false, false, false, false, false, false));
        // 门 A 没开却已经在 B 支路里（doorAOpen=false, inBranchB=true）
        assertThrows(IllegalArgumentException.class, () -> view(2, false, false, true, false, false, false, false));
        // 第一、二轮不可能出现「门 C 开着但门 B 没开」
        assertThrows(IllegalArgumentException.class, () -> view(2, false, false, false, false, false, true, false));
        // 第一轮不可能已供能
        assertThrows(IllegalArgumentException.class, () -> view(1, false, false, false, false, false, false, true));
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
