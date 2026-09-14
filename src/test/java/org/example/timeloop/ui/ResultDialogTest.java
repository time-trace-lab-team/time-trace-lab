package org.example.timeloop.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.timeloop.replay.LevelResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通关 / 失败完成弹窗（TASK-DEV3-RESULT-DIALOG，卡 §6 验收条件）。
 *
 * <p>锁四件事：① 默认隐藏、只有显式 {@code show} 才出现（组件不会自己弹）；② 通关与失败两套文案
 * 各自正确，且**失败文案整块不含「通关」**；③ **绝不抢焦点**（BUG-007 场景：抢焦点会让 {@code Scene}
 * 收不到按键、按 {@code R} 无反应）；④ 只渲染 —— 重复 {@code show} 只改文本、不重建节点、
 * 不引入任何计时。</p>
 */
class ResultDialogTest {

    /** 通关：第 2 / 3 轮达成，用时 684 刻 = 11.4 秒（口径与 HUD 唯一共享读秒同源）。 */
    private static final LevelResult CLEARED =
            new LevelResult("第二关：闸链", true, 2, 3, 684L);
    /** 失败：第 3 / 3 轮读秒归零。 */
    private static final LevelResult FAILED =
            new LevelResult("第二关：闸链", false, 3, 3, 3600L);

    @BeforeAll
    static void initializeJavafx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // 同一测试 JVM 中已初始化 Toolkit，直接复用。
        }
    }

    @Test
    void dialogStartsHiddenAndOnlyAppearsWhenExplicitlyShown() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            assertFalse(dialog.isVisible(), "构造后必须不可见（组件不得自己弹）");
            assertEquals("", dialog.titleText(), "未 show 之前不应有文案");

            dialog.show(CLEARED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);
            assertTrue(dialog.isVisible(), "show 之后必须可见");

            dialog.hide();
            assertFalse(dialog.isVisible(), "hide 之后必须不可见");
        });
    }

    @Test
    void clearedCopyCarriesLevelTitleRoundAndSeconds() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            dialog.show(CLEARED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);

            assertEquals("第二关：闸链 · 通关完成", dialog.titleText());
            assertEquals("第 2 / 3 轮完成，用时 11.4 秒", dialog.detailText());
            assertEquals("按 R 从第一轮重开", dialog.hintText());
        });
    }

    @Test
    void firstLevelCopyPointsToTheNextLevel() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            LevelResult firstLevel = new LevelResult("第一关：留下的脚步", true, 2, 2, 1200L);
            dialog.show(firstLevel, "第一关：留下的脚步",
                    ResultDialog.NextAction.ENTER_NEXT_LEVEL);

            assertEquals("第一关：留下的脚步 · 通关完成", dialog.titleText());
            assertEquals("第 2 / 2 轮完成，用时 20.0 秒", dialog.detailText());
            assertEquals("按 R 确认并进入第二关", dialog.hintText());
        });
    }

    @Test
    void failedCopyNeverSaysCleared() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            dialog.show(FAILED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);

            assertEquals("时间耗尽", dialog.titleText());
            assertEquals("第 3 / 3 轮读秒归零时仍未达成", dialog.detailText());
            assertEquals("按 R 从第一轮重开", dialog.hintText());

            for (String text : allLabelTexts(dialog)) {
                assertFalse(text.contains("通关"), "失败文案不得出现「通关」字样（卡 §6.6）：" + text);
            }
        });
    }

    /** 卡 §5.4.1 / §6.7：弹窗绝不抢焦点（否则 Scene 收不到按键，按 R 无反应，会被误当成 BUG-007）。 */
    @Test
    void dialogNeverStealsFocusFromTheScene() throws Exception {
        onFxThread(() -> {
            Button focusable = new Button("focus me");
            focusable.setFocusTraversable(true);
            ResultDialog dialog = new ResultDialog();
            StackPane root = new StackPane(focusable, dialog);
            Scene scene = new Scene(root, 800.0, 600.0);

            focusable.requestFocus();
            // 头less 环境（无 Stage）下焦点所有者可能仍是 null —— 关键是「弹窗不得改变它」。
            javafx.scene.Node focusBeforeShow = scene.getFocusOwner();

            dialog.show(CLEARED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);
            assertSame(focusBeforeShow, scene.getFocusOwner(),
                    "弹窗显示后焦点必须原地不动（不得抢焦点）");
            assertFalse(dialog.isFocusTraversable(), "弹窗自身不得可聚焦");
            assertFalse(dialog.isFocused());

            dialog.hide();
            assertSame(focusBeforeShow, scene.getFocusOwner(), "隐藏后焦点同样不得改变");
        });
    }

    /** 卡 §5.3 / §5.4.2：只渲染 —— 重复 show 幂等，且不重建节点。 */
    @Test
    void repeatedShowOnlyRewritesTextAndDoesNotRebuildNodes() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            VBox box = (VBox) dialog.getChildren().get(0);
            List<javafx.scene.Node> childrenBefore = List.copyOf(box.getChildren());

            dialog.show(CLEARED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);
            String firstTitle = dialog.titleText();
            String firstDetail = dialog.detailText();

            for (int i = 0; i < 30; i++) {
                dialog.show(CLEARED, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL);
            }

            assertEquals(firstTitle, dialog.titleText(), "重复 show 必须得到同样的标题");
            assertEquals(firstDetail, dialog.detailText(), "重复 show 必须得到同样的副文案");
            assertEquals(childrenBefore, List.copyOf(box.getChildren()),
                    "不得逐帧重建节点（同一批 Node 实例）");
            assertEquals(3, box.getChildren().size(), "面板固定三行：标题 / 副文案 / 提示");
        });
    }

    @Test
    void levelTitleFallsBackToResultNameThenToPlaceholder() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();

            dialog.show(CLEARED, null, ResultDialog.NextAction.RESTART_LEVEL);
            assertEquals("第二关：闸链 · 通关完成", dialog.titleText(),
                    "关卡名为空时回退到 LevelResult.levelName()");

            dialog.show(CLEARED, "   ", ResultDialog.NextAction.RESTART_LEVEL);
            assertEquals("第二关：闸链 · 通关完成", dialog.titleText(), "空白同样视为未提供");

            LevelResult anonymous = new LevelResult(null, true, 1, 2, 60L);
            dialog.show(anonymous, null, ResultDialog.NextAction.RESTART_LEVEL);
            assertEquals("本关 · 通关完成", dialog.titleText(), "两处都没有时用占位名");
        });
    }

    @Test
    void rejectsMissingInputs() throws Exception {
        onFxThread(() -> {
            ResultDialog dialog = new ResultDialog();
            assertThrows(NullPointerException.class,
                    () -> dialog.show(null, "第二关：闸链", ResultDialog.NextAction.RESTART_LEVEL));
            assertThrows(NullPointerException.class,
                    () -> dialog.show(CLEARED, "第二关：闸链", null));
        });
    }

    /** 面板上所有 Label 的文案（失败文案的「不含通关」断言用）。 */
    private static List<String> allLabelTexts(ResultDialog dialog) {
        List<String> texts = new ArrayList<>();
        for (javafx.scene.Node node : ((VBox) dialog.getChildren().get(0)).getChildren()) {
            if (node instanceof javafx.scene.control.Label label && label.getText() != null) {
                texts.add(label.getText());
            }
        }
        return texts;
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

        assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX 弹窗操作超时");
        if (failure[0] != null) {
            throw new AssertionError("通关弹窗断言失败", failure[0]);
        }
    }

    @FunctionalInterface
    private interface FxAssertion {
        void run() throws Exception;
    }
}
