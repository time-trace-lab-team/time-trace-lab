package org.example.timeloop.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.replay.TickContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标提示只能有<b>一块</b> Label。
 *
 * <p>第一关写 {@link ObjectiveViewModel#text()}、第二关写 {@link Level02ObjectiveViewModel#text()}，
 * 两个关卡的目标投影形状不同，但必须写进<b>同一个</b>控件：否则切关后场景里会多出一块
 * 空 Label（或多个目标面板），并且 app 侧被迫复制本组件的样式类与配色。</p>
 *
 * <p>本测试锁的是"同一块 Label"这件事本身：控件身份在两次渲染之间不变、样式类只有一份、
 * 第二关文案覆盖第一关文案且不残留。</p>
 */
class SharedHudObjectiveLabelTest {

    private static final TickContext L1_CONTEXT = new TickContext(0, 960, 1, 3);
    private static final TickContext L2_CONTEXT = new TickContext(0, 1200, 1, 4);

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @Test
    void bothLevelsWriteTheSameSingleObjectiveLabel() throws Exception {
        onFxThread(() -> {
            SharedHud hud = new SharedHud();
            List<Node> before = objectiveLabels(hud);
            assertEquals(1, before.size(), "共享 HUD 只能有一块目标提示 Label");

            ObjectiveViewModel levelOne = new ObjectiveViewModel(1, 3, false, false, false, false, false);
            hud.render(L1_CONTEXT, GamePhase.PLAYING, levelOne);
            assertEquals(levelOne.text(), hud.getObjectiveText(), "第一关目标文本写进共享 HUD");

            Level02ObjectiveViewModel levelTwo =
                    new Level02ObjectiveViewModel(1, 4, false, false, -1, false, -1, false);
            hud.render(L2_CONTEXT, GamePhase.PLAYING, levelTwo.text());

            assertEquals(levelTwo.text(), hud.getObjectiveText(), "第二关目标文本写进共享 HUD");
            assertNotEquals(levelOne.text(), hud.getObjectiveText(), "第二关文案必须覆盖第一关文案");
            List<Node> after = objectiveLabels(hud);
            assertEquals(1, after.size(), "第二关文案不得新增第二块目标 Label");
            assertSame(before.get(0), after.get(0), "两次渲染必须落在同一个控件上");
        });
    }

    @Test
    void switchingLevelsLeavesNoFirstLevelTextOnAnyObjectiveLabel() throws Exception {
        onFxThread(() -> {
            SharedHud hud = new SharedHud();
            ObjectiveViewModel levelOne = new ObjectiveViewModel(2, 3, false, true, false, false, false);
            hud.render(L1_CONTEXT, GamePhase.PLAYING, levelOne);
            String levelOneText = levelOne.text();
            assertTrue(hud.getObjectiveText().contains("左板"), "夹具前提：第一关文案提到左板");

            Level02ObjectiveViewModel levelTwo =
                    new Level02ObjectiveViewModel(1, 4, false, false, -1, false, -1, false);
            hud.render(L2_CONTEXT, GamePhase.PLAYING, levelTwo.text());

            assertTrue(levelTwo.text().contains("门外板"), "夹具前提：第二关文案提到门外板");
            for (Node node : objectiveLabels(hud)) {
                String text = ((javafx.scene.control.Label) node).getText();
                assertEquals(levelTwo.text(), text, "任何目标 Label 上都不得残留第一关文本");
                assertNotEquals(levelOneText, text);
            }
        });
    }

    @Test
    void objectiveTextIsRequired() throws Exception {
        onFxThread(() -> {
            SharedHud hud = new SharedHud();
            hud.render(L1_CONTEXT, GamePhase.PLAYING, "第 1 轮：先踩开关（踩上即开启），再去左驻留板停住");
            assertThrows(NullPointerException.class,
                    () -> hud.render(L1_CONTEXT, GamePhase.PLAYING, (String) null),
                    "目标文本不允许为 null（会把 HUD 目标行清成空白）");
        });
    }

    private static List<Node> objectiveLabels(SharedHud hud) {
        return hud.getChildrenUnmodifiable().stream()
                .filter(node -> node.getStyleClass().contains("shared-hud-objective"))
                .toList();
    }

    private static void onFxThread(FxAssertion assertion) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                assertion.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 共享 HUD 控件操作超时");
        if (failure[0] != null) {
            throw new AssertionError("共享 HUD 目标提示断言失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
