package org.example.timeloop.app;

import org.example.timeloop.core.GamePhase;
import org.example.timeloop.replay.LevelResult;
import org.example.timeloop.ui.ResultDialog;

import java.util.Optional;

/**
 * 终局弹窗的 app 侧接线（把 {@link ResultDialog} 这个**只读组件**接到关卡流上）。
 *
 * <p>约定（TASK-PM-L02-RESULT-DIALOG 的 app 部分，PM 实现）：</p>
 * <ul>
 *   <li><b>边沿触发</b>：{@link #onFrame(LevelFlow)} 只在"刚进入终局且弹窗尚未显示"时显示一次；
 *       绝不每帧调用 {@code show}（会重复写文本并可能吞键）。</li>
 *   <li><b>确认驱动切关</b>：切关仍只认 {@link GamePhase#RESULT}（四守卫不变），但**由玩家按确认键触发**，
 *       避免"每帧自动切关"把结算界面瞬间顶掉；{@link #confirmEntersNextLevel()} 告诉调用方
 *       这次确认是"进入下一关"还是"本关重开"。</li>
 *   <li><b>失败复用同一组件</b>：{@code FAILED} 用同一弹窗、失败文案（组件内部按 {@code cleared} 决定）。</li>
 *   <li>本类不读时钟、不新增计时器、不做胜负判定、不改玩法状态。</li>
 * </ul>
 */
final class ResultDialogPresenter {

    /** 有下一关的关卡 → 确认即进入下一关；末关（第三关）/ 失败 → 本关重开。 */
    private static final String LEVEL_ONE_NEXT_TITLE = "第二关";
    private static final String LEVEL_TWO_NEXT_TITLE = "第三关";

    private final ResultDialog dialog = new ResultDialog();
    private boolean shown;
    private boolean confirmEntersNextLevel;

    ResultDialog node() {
        return dialog;
    }

    boolean isShown() {
        return shown;
    }

    boolean confirmEntersNextLevel() {
        return confirmEntersNextLevel;
    }

    /** 每帧调用一次：只在终局边沿显示，之后保持到 {@link #hide()}。 */
    void onFrame(LevelFlow flow) {
        if (shown || !flow.isFinalPhase()) {
            return;
        }
        Optional<LevelResult> result = flow.activeResult();
        if (result.isEmpty()) {
            return;
        }
        String title = flow.activeLevel().title();
        if (flow.phase() == GamePhase.RESULT) {
            // 通关：有下一关就「进入下一关」（关卡名由集成层传入，组件不写死关卡号），
            // 第三关是最后一关 → 本关重开。
            String nextTitle = nextLevelTitle(flow.activeLevel());
            if (nextTitle != null) {
                confirmEntersNextLevel = true;
                dialog.show(result.get(), title, ResultDialog.NextAction.ENTER_NEXT_LEVEL, nextTitle);
            } else {
                confirmEntersNextLevel = false;
                dialog.show(result.get(), title, ResultDialog.NextAction.RESTART_LEVEL);
            }
        } else {
            confirmEntersNextLevel = false;
            dialog.show(result.get(), title, ResultDialog.NextAction.RESTART_LEVEL);
        }
        shown = true;
    }

    /**
     * 下一关的显示名（关卡号），最后一关返回 {@code null}。
     *
     * <p>刻意写成穷尽的 {@code switch} 表达式：新增关卡时这里会直接编译失败，
     * 而不是悄悄把新关当作「最后一关」——那样玩家通关后会被卡在结算界面。</p>
     */
    private static String nextLevelTitle(LevelFlow.LevelId level) {
        return switch (level) {
            case LEVEL_01 -> LEVEL_ONE_NEXT_TITLE;
            case LEVEL_02 -> LEVEL_TWO_NEXT_TITLE;
            case LEVEL_03 -> null;
        };
    }

    /** 隐藏并把边沿守卫复位（确认、重开、切关后都必须调用）。 */
    void hide() {
        dialog.hide();
        shown = false;
        confirmEntersNextLevel = false;
    }
}
