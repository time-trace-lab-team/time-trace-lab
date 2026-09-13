package org.example.timeloop.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.replay.TickContext;

import java.util.Objects;

/**
 * 最小共享 HUD 组件。
 *
 * <p>组件不推进时间，也不创建第二套计时器；场景端每次拿到新的共享
 * {@link TickContext} 后显式调用 {@link #render(TickContext)} 即可刷新。</p>
 */
public final class SharedHud extends HBox {

    private final Label countdownLabel = new Label();
    private final Label roundLabel = new Label();
    private final Label phaseLabel = new Label();
    /** 常驻目标提示：哪块驻留板被谁压着、现在该做什么。 */
    private final Label objectiveLabel = new Label();

    private HudViewModel viewModel;
    private ObjectiveViewModel objectiveViewModel;

    public SharedHud() {
        getStyleClass().add("shared-hud");
        countdownLabel.getStyleClass().add("shared-hud-countdown");
        roundLabel.getStyleClass().add("shared-hud-round");
        phaseLabel.getStyleClass().add("shared-hud-phase");
        phaseLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d99b24;");
        objectiveLabel.getStyleClass().add("shared-hud-objective");
        objectiveLabel.setStyle("-fx-text-fill: #9fb4cc;");
        setSpacing(16.0);
        getChildren().addAll(countdownLabel, roundLabel, phaseLabel, objectiveLabel);
    }

    /**
     * 将共享 tick 的只读投影显示到两个 HUD 文本中。
     *
     * @param context 开发 2 的共享时间上下文
     */
    public void render(TickContext context) {
        render(context, GamePhase.PLAYING);
    }

    /** Refreshes the shared timer and any terminal phase feedback. */
    public void render(TickContext context, GamePhase phase) {
        HudViewModel next = HudViewModel.from(Objects.requireNonNull(context, "context"));
        viewModel = next;
        countdownLabel.setText(next.countdownText());
        roundLabel.setText(next.roundText());
        phaseLabel.setText(HudViewModel.phaseText(phase));
    }

    /**
     * 带常驻目标提示的刷新。
     *
     * <p>两块驻留板画得一模一样，玩家无法从画面判断"残影压着哪块、我该去哪块"，
     * 因此这里必须每次都把当前目标写成一句人话（见 {@link ObjectiveViewModel#text()}）。</p>
     */
    public void render(TickContext context, GamePhase phase, ObjectiveViewModel objective) {
        render(context, phase);
        objectiveViewModel = Objects.requireNonNull(objective, "objective");
        objectiveLabel.setText(objective.text());
    }

    /** 最近一次渲染的不可变投影；尚未渲染时返回 {@code null}。 */
    public HudViewModel getViewModel() {
        return viewModel;
    }

    public String getCountdownText() {
        return countdownLabel.getText();
    }

    public String getRoundText() {
        return roundLabel.getText();
    }

    public String getPhaseText() {
        return phaseLabel.getText();
    }
}
