package org.example.timeloop.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
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

    private HudViewModel viewModel;

    public SharedHud() {
        getStyleClass().add("shared-hud");
        countdownLabel.getStyleClass().add("shared-hud-countdown");
        roundLabel.getStyleClass().add("shared-hud-round");
        setSpacing(16.0);
        getChildren().addAll(countdownLabel, roundLabel);
    }

    /**
     * 将共享 tick 的只读投影显示到两个 HUD 文本中。
     *
     * @param context 开发 2 的共享时间上下文
     */
    public void render(TickContext context) {
        HudViewModel next = HudViewModel.from(Objects.requireNonNull(context, "context"));
        viewModel = next;
        countdownLabel.setText(next.countdownText());
        roundLabel.setText(next.roundText());
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
}
