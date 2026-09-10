package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.function.Supplier;

/**
 * C5 机关图层：驻留板、门、出口终端。
 *
 * <p>状态来自只读 {@link RenderViews.Frame}；渲染不写回机关状态。
 * 关键状态用形状/明度区分，不只靠颜色。</p>
 */
public final class MechanismLayer implements RenderLayer {

    private static final double MARKER_SIZE_FACTOR = 0.8;

    private final Supplier<RenderViews.Frame> frameSource;
    private final double tileSize;
    private final WorldTransform transform;

    public MechanismLayer(Supplier<RenderViews.Frame> frameSource, double tileSize, WorldTransform transform) {
        this.frameSource = frameSource;
        this.tileSize = tileSize;
        this.transform = transform;
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(2.0);

        double size = transform.scaled(tileSize * MARKER_SIZE_FACTOR);
        for (RenderViews.Mechanism mechanism : frameSource.get().mechanisms()) {
            double cx = transform.toCanvasX(mechanism.x());
            double cy = transform.toCanvasY(mechanism.y());
            double left = cx - size / 2.0;
            double top = cy - size / 2.0;

            Color accent = mechanism.active() ? RenderPalette.INTERACTIVE : RenderPalette.INACTIVE_OUTLINE;
            gc.setStroke(accent);
            gc.setFill(mechanism.active()
                    ? RenderPalette.INTERACTIVE.deriveColor(0, 1, 1, 0.20)
                    : Color.TRANSPARENT);

            switch (mechanism.kind()) {
                case PLATE -> {
                    gc.fillRoundRect(left, top, size, size, size / 3.0, size / 3.0);
                    gc.strokeRoundRect(left, top, size, size, size / 3.0, size / 3.0);
                }
                case DOOR -> {
                    gc.fillRect(left, top + size / 3.0, size, size / 3.0);
                    gc.strokeRect(left, top + size / 3.0, size, size / 3.0);
                }
                case EXIT -> {
                    gc.fillRect(left, top, size, size);
                    gc.strokeRect(left, top, size, size);
                    // 出口中心菱形标记，形状区分而非仅颜色
                    double half = size / 4.0;
                    gc.strokePolygon(
                            new double[]{cx, cx + half, cx, cx - half},
                            new double[]{cy - half, cy, cy + half, cy}, 4);
                }
            }
        }
    }
}
