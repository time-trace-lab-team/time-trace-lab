package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 时间线刻度与驻留事件图层。
 *
 * <p>每帧只读取一次动态事件 Supplier，并将该帧快照中的世界坐标统一交给
 * {@link WorldTransform} 投影。R3 绘制每秒刻度与驻留事件；R4 只消费上游已记录的
 * {@code RAY_DELAY} 投影来绘制 Δt，不推算射线命中或延迟。</p>
 */
public final class TimelineEventLayer implements RenderLayer {

    private static final double TICK_HALF_LENGTH_PX = 3.0;
    private static final double DIAMOND_HALF_SIZE_PX = 5.0;
    private static final double LEAVE_SLASH_HALF_PX = 2.5;
    private static final double DELAY_HALF_SIZE_PX = 6.0;
    private static final double DELAY_LABEL_OFFSET_Y_PX = 9.0;

    private final Supplier<List<TimelineVisualEvent>> eventSource;
    private final Supplier<WorldTransform> transformSource;

    public TimelineEventLayer(Supplier<List<TimelineVisualEvent>> eventSource, WorldTransform transform) {
        this(eventSource, fixedTransform(transform));
    }

    public TimelineEventLayer(Supplier<List<TimelineVisualEvent>> eventSource,
                              Supplier<WorldTransform> transformSource) {
        this.eventSource = Objects.requireNonNull(eventSource, "eventSource");
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = Objects.requireNonNull(
                transformSource.get(), "TimelineEventLayer transformSource 在 render 时返回 null");
        List<TimelineVisualEvent> events = List.copyOf(Objects.requireNonNull(
                eventSource.get(), "TimelineEventLayer eventSource 在 render 时返回 null"));

        for (TimelineVisualEvent event : events) {
            double x = transform.toCanvasX(event.worldPosition().x());
            double y = transform.toCanvasY(event.worldPosition().y());
            switch (event.kind()) {
                case TICK_MARK -> drawTickMark(gc, x, y);
                case DOCK_ENTER -> drawEnterDiamond(gc, x, y);
                case DOCK_LEAVE -> drawLeaveDiamond(gc, x, y);
                case RAY_DELAY -> drawRayDelay(gc, x, y, event.delayTicks());
                case ECHO_EXPIRE -> {
                    // 留给 R5 的消散表现；R4 不提前绘制。
                }
            }
        }
        gc.setGlobalAlpha(1.0);
    }

    private static void drawTickMark(GraphicsContext gc, double x, double y) {
        useInteractiveStyle(gc);
        gc.strokeLine(x, y - TICK_HALF_LENGTH_PX, x, y + TICK_HALF_LENGTH_PX);
    }

    private static void drawEnterDiamond(GraphicsContext gc, double x, double y) {
        useInteractiveStyle(gc);
        gc.fillPolygon(
                new double[]{x, x + DIAMOND_HALF_SIZE_PX, x, x - DIAMOND_HALF_SIZE_PX},
                new double[]{y - DIAMOND_HALF_SIZE_PX, y, y + DIAMOND_HALF_SIZE_PX, y},
                4);
    }

    private static void drawLeaveDiamond(GraphicsContext gc, double x, double y) {
        useInteractiveStyle(gc);
        gc.strokePolygon(
                new double[]{x, x + DIAMOND_HALF_SIZE_PX, x, x - DIAMOND_HALF_SIZE_PX},
                new double[]{y - DIAMOND_HALF_SIZE_PX, y, y + DIAMOND_HALF_SIZE_PX, y},
                4);
        gc.strokeLine(x - LEAVE_SLASH_HALF_PX, y + LEAVE_SLASH_HALF_PX,
                x + LEAVE_SLASH_HALF_PX, y - LEAVE_SLASH_HALF_PX);
    }

    /**
     * 以沙漏交叉几何标识一次已记录的时滞；文字仅复述上游提供的 delayTicks。
     * 即使用户无法辨色，交叉形与 Δt 文字也能区别于驻留菱形和每秒刻痕。
     */
    private static void drawRayDelay(GraphicsContext gc, double x, double y, int delayTicks) {
        gc.setStroke(RenderPalette.RAY_ACTIVE);
        gc.setFill(RenderPalette.RAY_ACTIVE);
        gc.setLineWidth(1.8);
        gc.strokePolygon(
                new double[]{x - DELAY_HALF_SIZE_PX, x + DELAY_HALF_SIZE_PX, x - DELAY_HALF_SIZE_PX,
                        x + DELAY_HALF_SIZE_PX},
                new double[]{y - DELAY_HALF_SIZE_PX, y - DELAY_HALF_SIZE_PX, y + DELAY_HALF_SIZE_PX,
                        y + DELAY_HALF_SIZE_PX},
                4);
        String suffix = delayTicks == 0 ? "" : "+" + delayTicks;
        gc.fillText("Δt" + suffix, x + DELAY_HALF_SIZE_PX + 2.0, y - DELAY_LABEL_OFFSET_Y_PX);
    }

    private static void useInteractiveStyle(GraphicsContext gc) {
        gc.setStroke(RenderPalette.INTERACTIVE);
        gc.setFill(RenderPalette.INTERACTIVE);
        gc.setLineWidth(1.5);
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
