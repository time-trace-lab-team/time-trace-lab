package org.example.timeloop.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.timeloop.replay.LevelResult;

import java.util.Objects;

/**
 * 通关 / 失败完成弹窗（TASK-DEV3-RESULT-DIALOG）。
 *
 * <p><b>只负责渲染</b>：不计算用时、不读时钟、不做胜负判定、不新增计时器、不做成绩系统
 * （卡 §1 / §5.4）。显示与隐藏由集成层在<b>进入终局的第一个帧</b>边沿触发调用（卡 §5.3），
 * 组件自身绝不会在玩法阶段出现。</p>
 *
 * <p><b>不抢焦点</b>（卡 §5.4.1）：构造与 {@link #show} 都不调用 {@code requestFocus()}，
 * 也不设置 {@code setFocusTraversable(true)} —— 否则 {@code Scene} 收不到按键、{@code R} 无反应，
 * 与 BUG-007 的现象会混淆。因此本类**不注册任何键盘处理**（卡 §6.7：不依赖键盘输入）。</p>
 *
 * <p>形态是覆盖层 {@link StackPane}（<b>不是</b>独立 {@code Stage}）：与暗调风格一致，
 * 不产生第二套窗口生命周期与焦点问题，背景压暗由自身提供，不改渲染图层（卡 §5.1）。</p>
 */
public final class ResultDialog extends StackPane {

    /**
     * 确认后的动作（卡 §5.2 的 D1/D4；具体取值由集成层按关卡传入）。
     *
     * <p><b>刻意不写死「进入第二关」</b>：第三关已经进 develop，等集成层接上 L2→L3 之后，
     * 第二关通关时的提示就该说「进入第三关」。所以默认文案保持与关卡无关，
     * 需要具体关卡名时由集成层调
     * {@link #show(LevelResult, String, NextAction, String)} 传进来。</p>
     */
    public enum NextAction {
        /** 还有下一关：确认后进入下一关。 */
        ENTER_NEXT_LEVEL("按 R 确认并进入下一关"),
        /** 最后一关（或失败）：从第一轮重开本关。 */
        RESTART_LEVEL("按 R 从第一轮重开");

        private final String hint;

        NextAction(String hint) {
            this.hint = hint;
        }

        /** 默认提示行文案（与具体关卡无关）。 */
        public String hint() {
            return hint;
        }
    }

    private static final String BACKGROUND = "-fx-background-color: rgba(12,16,24,0.88);";
    private static final String ACCENT = "#E6B85C";
    private static final String BODY = "#D9DFEA";

    private final Label titleLabel = new Label();
    private final Label detailLabel = new Label();
    private final Label hintLabel = new Label();

    public ResultDialog() {
        getStyleClass().add("result-dialog");
        setStyle(BACKGROUND);
        // 覆盖整块画布：StackPane 只在子节点 max 尺寸允许时才拉伸它。
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        titleLabel.getStyleClass().add("result-dialog-title");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + ACCENT + ";");
        detailLabel.getStyleClass().add("result-dialog-detail");
        detailLabel.setStyle("-fx-font-size: 17px; -fx-text-fill: " + BODY + ";");
        hintLabel.getStyleClass().add("result-dialog-hint");
        hintLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: " + BODY + "; -fx-opacity: 0.85;");

        VBox box = new VBox(titleLabel, detailLabel, hintLabel);
        box.setStyle("-fx-alignment: center; -fx-spacing: 14px;");
        getChildren().add(box);

        // 默认隐藏：只有集成层显式 show(...) 才会出现。
        setVisible(false);
    }

    /**
     * 渲染一次结局面板并显示。**幂等且无副作用**：同一个入参重复调用只会重写同一批 Label 的文本，
     * 不重建节点、不注册监听、不改变焦点（卡 §5.3 / §5.4.1）。
     *
     * @param result     开发 2 的只读结算数据（{@code LevelResult}）
     * @param levelTitle 关卡名（来源 {@code LevelFlow.LevelId.title()}）；为 {@code null} / 空白时
     *                   依次回退到 {@link LevelResult#levelName()} 与 {@code "本关"}
     * @param nextAction 确认后的动作，决定提示行文案
     * @return 本次渲染的提示行文案（便于集成层与测试断言）
     */
    public String show(LevelResult result, String levelTitle, NextAction nextAction) {
        return show(result, levelTitle, nextAction, null);
    }

    /**
     * 带「下一关关卡名」的渲染：{@code nextAction == ENTER_NEXT_LEVEL} 且给出了关卡名时，
     * 提示行会写成「按 R 确认并进入&lt;关卡名&gt;」；否则退回与关卡无关的默认文案。
     *
     * @param nextLevelTitle 下一关的显示名（如「第二关」或「第三关：追赶过去」——原样拼进提示行）；
     *                       无下一关或未知时传 {@code null}
     */
    public String show(LevelResult result, String levelTitle, NextAction nextAction,
                       String nextLevelTitle) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(nextAction, "nextAction");

        String name = resolveLevelTitle(result, levelTitle);
        titleLabel.setText(result.cleared() ? name + " · 通关完成" : "时间耗尽");
        detailLabel.setText(result.cleared() ? clearedDetail(result) : failedDetail(result));
        hintLabel.setText(resolveHint(nextAction, nextLevelTitle));
        setVisible(true);
        return hintLabel.getText();
    }

    /** 便捷重载：不经裁决时按「重开本关」渲染（卡 §5.2 里第二关的默认文案）。 */
    public String show(LevelResult result, String levelTitle) {
        return show(result, levelTitle, NextAction.RESTART_LEVEL);
    }

    /** 隐藏弹窗（集成层在按 R 确认后调用）。 */
    public void hide() {
        setVisible(false);
    }

    /** 当前标题文案（只读；测试与诊断用）。 */
    public String titleText() {
        return titleLabel.getText();
    }

    /** 当前副文案（只读）。 */
    public String detailText() {
        return detailLabel.getText();
    }

    /** 当前提示行文案（只读）。 */
    public String hintText() {
        return hintLabel.getText();
    }

    /**
     * 通关副文案：轮次与用时。用时不在这里算 —— 秒数直接取开发 2 的只读投影
     * （{@code usedTicks / 60}，与 HUD 的唯一共享读秒同源），本组件不引入第二套计时。
     */
    private static String clearedDetail(LevelResult result) {
        double seconds = result.usedTicks() / 60.0;
        return String.format("第 %d / %d 轮完成，用时 %.1f 秒",
                result.clearedRound(), result.maxRounds(), seconds);
    }

    /**
     * 失败副文案：<b>不出现「通关」字样</b>（卡 §6.6 验收条件）。
     *
     * <p>注：卡 §5.2 的建议文案是「第 M / M 轮未能在读秒归零前通关」，其中恰好含「通关」二字，
     * 与 §6.6 冲突。这里以可判定的验收条件为准，改为「读秒归零时仍未达成」，
     * 语义相同且整块面板不含「通关」。</p>
     */
    private static String failedDetail(LevelResult result) {
        return String.format("第 %d / %d 轮读秒归零时仍未达成",
                result.clearedRound(), result.maxRounds());
    }

    private static String resolveLevelTitle(LevelResult result, String levelTitle) {
        if (levelTitle != null && !levelTitle.isBlank()) {
            return levelTitle;
        }
        String fromResult = result.levelName();
        if (fromResult != null && !fromResult.isBlank()) {
            return fromResult;
        }
        return "本关";
    }

    /** 提示行：能给出下一关名时写具体关卡名，否则用与关卡无关的默认文案。 */
    private static String resolveHint(NextAction nextAction, String nextLevelTitle) {
        if (nextAction == NextAction.ENTER_NEXT_LEVEL
                && nextLevelTitle != null && !nextLevelTitle.isBlank()) {
            return "按 R 确认并进入" + nextLevelTitle;
        }
        return nextAction.hint();
    }
}
