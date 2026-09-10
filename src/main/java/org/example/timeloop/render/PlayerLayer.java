package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.function.Supplier;

/**
 * C5 当前玩家图层。
 *
 * <p>玩家数据来自只读 {@link RenderViews.Frame}；渲染不改变玩法坐标。
 * 相位下潜以脚下圆环表达，不建立真实高度轴。</p>
 */
public final class PlayerLayer implements RenderLayer {

    private static final double BODY_RADIUS_FACTOR = 0.30;
    private static final double PHASE_RING_FACTOR = 0.52;

    private final Supplier<RenderViews.Frame> frameSource;
    private final double tileSize;
    private final WorldTransform transform;

    public PlayerLayer(Supplier<RenderViews.Frame> frameSource, double tileSize, WorldTransform transform) {
        this.frameSource = frameSource;
        this.tileSize = tileSize;
        this.transform = transform;
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        RenderViews.Player player = frameSource.get().player();
        double cx = transform.toCanvasX(player.x());
        double cy = transform.toCanvasY(player.y());
        double radius = transform.scaled(tileSize * BODY_RADIUS_FACTOR);

        gc.setGlobalAlpha(1.0);

        if (player.phased()) {
            gc.setStroke(RenderPalette.PHASE);
            gc.setLineWidth(1.5);
            double ring = transform.scaled(tileSize * PHASE_RING_FACTOR);
            gc.strokeOval(cx - ring / 2.0, cy - ring / 2.0, ring, ring);
        }

        gc.setFill(RenderPalette.PLAYER);
        gc.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);

        // 朝向短刻痕：形状/方向提示，不只靠颜色
        double tick = radius * 1.4;
        double dx = 0.0;
        double dy = 0.0;
        switch (player.direction()) {
            case UP -> dy = -tick;
            case DOWN -> dy = tick;
            case LEFT -> dx = -tick;
            case RIGHT -> dx = tick;
        }
        gc.setStroke(RenderPalette.BACKGROUND);
        gc.setLineWidth(2.0);
        gc.strokeLine(cx, cy, cx + dx, cy + dy);
    }
}
