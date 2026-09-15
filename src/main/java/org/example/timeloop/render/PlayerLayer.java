package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.Objects;
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
    private final Supplier<WorldTransform> transformSource;

    public PlayerLayer(Supplier<RenderViews.Frame> frameSource, double tileSize, WorldTransform transform) {
        this(frameSource, tileSize, fixedTransform(transform));
    }

    public PlayerLayer(Supplier<RenderViews.Frame> frameSource,
                       double tileSize,
                       Supplier<WorldTransform> transformSource) {
        this.frameSource = frameSource;
        this.tileSize = tileSize;
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = currentTransform();
        RenderViews.Player player = frameSource.get().player();
        double cx = transform.toCanvasX(player.x());
        double cy = transform.toCanvasY(player.y());
        double radius = transform.scaled(tileSize * BODY_RADIUS_FACTOR);
        PlayerVisualProjection.Style visual = PlayerVisualProjection.forPlayer(
                player.movementState(), player.phased());
        double bodyRadiusY = radius * visual.bodyHeightScale();

        gc.setGlobalAlpha(1.0);

        if (visual.showsPhaseRing()) {
            gc.setStroke(RenderPalette.PHASE);
            gc.setLineWidth(1.5);
            double ring = transform.scaled(tileSize * PHASE_RING_FACTOR);
            gc.strokeOval(cx - ring / 2.0, cy - ring / 2.0, ring, ring);
        }

        gc.setFill(RenderPalette.PLAYER);
        gc.setGlobalAlpha(visual.bodyAlpha());
        switch (visual.bodyShape()) {
            case CIRCLE -> gc.fillOval(cx - radius, cy - bodyRadiusY,
                    radius * 2.0, bodyRadiusY * 2.0);
            case ROUNDED_SQUARE -> gc.fillRoundRect(cx - radius, cy - bodyRadiusY,
                    radius * 2.0, bodyRadiusY * 2.0, radius * 0.9, bodyRadiusY * 0.9);
        }
        gc.setGlobalAlpha(1.0);

        double tick = radius * 1.4;
        double dx = directionX(player.direction(), tick);
        double dy = directionY(player.direction(), tick);
        if (visual.showsSlowOutline()) {
            double outlineRadius = radius * 1.22;
            gc.setStroke(RenderPalette.INTERACTIVE);
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - outlineRadius, cy - outlineRadius, outlineRadius * 2.0, outlineRadius * 2.0);
        }
        if (visual.showsSlowTrail()) {
            gc.setStroke(RenderPalette.INTERACTIVE);
            gc.setLineWidth(1.5);
            gc.strokeLine(cx - dx * 0.45, cy - dy * 0.45, cx - dx * 1.15, cy - dy * 1.15);
        }

        if (visual.showsDirectionTick()) {
            // 朝向短刻痕：形状/方向提示，不只靠颜色
            gc.setStroke(RenderPalette.BACKGROUND);
            gc.setLineWidth(2.0);
            gc.strokeLine(cx, cy, cx + dx, cy + dy);
        }
    }

    private WorldTransform currentTransform() {
        return Objects.requireNonNull(transformSource.get(), "PlayerLayer transformSource 在 render 时返回 null");
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }

    private static double directionX(org.example.timeloop.core.Direction direction, double length) {
        return switch (direction) {
            case LEFT -> -length;
            case RIGHT -> length;
            case UP, DOWN -> 0.0;
        };
    }

    private static double directionY(org.example.timeloop.core.Direction direction, double length) {
        return switch (direction) {
            case UP -> -length;
            case DOWN -> length;
            case LEFT, RIGHT -> 0.0;
        };
    }
}
