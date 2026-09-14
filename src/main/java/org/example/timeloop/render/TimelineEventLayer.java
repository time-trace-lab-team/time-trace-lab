package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 时间线刻度与驻留事件图层。
 *
 * <p>每帧只读取一次动态事件 Supplier，并将该帧快照中的世界坐标统一交给
 * {@link WorldTransform} 投影。R3 仅绘制每秒刻度、驻留和离开节点；其他已冻结事件类型
 * 留给后续任务，不在本层推算或提前表现。</p>
 */
public final class TimelineEventLayer implements RenderLayer {

    private static final double TICK_HALF_LENGTH_PX = 3.0;
    private static final double DIAMOND_HALF_SIZE_PX = 5.0;
    private static final double LEAVE_SLASH_HALF_PX = 2.5;

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

        gc.setStroke(RenderPalette.INTERACTIVE);
        gc.setFill(RenderPalette.INTERACTIVE);
        gc.setLineWidth(1.5);

        for (TimelineVisualEvent event : events) {
            double x = transform.toCanvasX(event.worldPosition().x());
            double y = transform.toCanvasY(event.worldPosition().y());
            switch (event.kind()) {
                case TICK_MARK -> drawTickMark(gc, x, y);
                case DOCK_ENTER -> drawEnterDiamond(gc, x, y);
                case DOCK_LEAVE -> drawLeaveDiamond(gc, x, y);
                case RAY_DELAY, ECHO_EXPIRE -> {
                    // 已冻结的后续任务事件；R3 不提前绘制。
                }
            }
        }
    }

    private static void drawTickMark(GraphicsContext gc, double x, double y) {
        gc.strokeLine(x, y - TICK_HALF_LENGTH_PX, x, y + TICK_HALF_LENGTH_PX);
    }

    private static void drawEnterDiamond(GraphicsContext gc, double x, double y) {
        gc.fillPolygon(
                new double[]{x, x + DIAMOND_HALF_SIZE_PX, x, x - DIAMOND_HALF_SIZE_PX},
                new double[]{y - DIAMOND_HALF_SIZE_PX, y, y + DIAMOND_HALF_SIZE_PX, y},
                4);
    }

    private static void drawLeaveDiamond(GraphicsContext gc, double x, double y) {
        gc.strokePolygon(
                new double[]{x, x + DIAMOND_HALF_SIZE_PX, x, x - DIAMOND_HALF_SIZE_PX},
                new double[]{y - DIAMOND_HALF_SIZE_PX, y, y + DIAMOND_HALF_SIZE_PX, y},
                4);
        gc.strokeLine(x - LEAVE_SLASH_HALF_PX, y + LEAVE_SLASH_HALF_PX,
                x + LEAVE_SLASH_HALF_PX, y - LEAVE_SLASH_HALF_PX);
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
