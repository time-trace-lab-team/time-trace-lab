package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.level.model.Vector2D;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 残影代际和最后有效轮标识图层。
 *
 * <p>每帧各读取一次已有帧投影和生命周期投影。R2 只表现 E 编号与“LAST”标记；
 * {@code effectProgress} 留给 R5 的淡出/粒子表现，本层不建立本地寿命计时。</p>
 */
public final class EchoLifecycleLayer implements RenderLayer {

    private static final double BADGE_WIDTH_PX = 25.0;
    private static final double BADGE_HEIGHT_PX = 15.0;
    private static final double BADGE_OFFSET_Y_PX = 16.0;
    private static final double LAST_RING_PADDING_PX = 3.0;

    private final Supplier<RenderViews.Frame> frameSource;
    private final Supplier<List<EchoLifecycleVisual>> lifecycleSource;
    private final Supplier<WorldTransform> transformSource;

    public EchoLifecycleLayer(Supplier<RenderViews.Frame> frameSource,
                              Supplier<List<EchoLifecycleVisual>> lifecycleSource,
                              WorldTransform transform) {
        this(frameSource, lifecycleSource, fixedTransform(transform));
    }

    public EchoLifecycleLayer(Supplier<RenderViews.Frame> frameSource,
                              Supplier<List<EchoLifecycleVisual>> lifecycleSource,
                              Supplier<WorldTransform> transformSource) {
        this.frameSource = Objects.requireNonNull(frameSource, "frameSource");
        this.lifecycleSource = Objects.requireNonNull(lifecycleSource, "lifecycleSource");
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = Objects.requireNonNull(
                transformSource.get(), "EchoLifecycleLayer transformSource 在 render 时返回 null");
        RenderViews.Frame frame = Objects.requireNonNull(
                frameSource.get(), "EchoLifecycleLayer frameSource 在 render 时返回 null");
        Map<Integer, EchoLifecycleVisual> lifecycles = indexBySourceRound(List.copyOf(Objects.requireNonNull(
                lifecycleSource.get(), "EchoLifecycleLayer lifecycleSource 在 render 时返回 null")));

        for (RenderViews.EchoTrail echo : frame.echoes()) {
            EchoLifecycleVisual lifecycle = lifecycles.get(echo.sourceRound());
            if (lifecycle != null && lifecycle.phase() == EchoVisualPhase.GONE) {
                continue;
            }
            if (echo.points().isEmpty()) {
                continue;
            }
            drawBadge(gc, echo, lifecycle, transform);
        }
        gc.setGlobalAlpha(1.0);
    }

    private static Map<Integer, EchoLifecycleVisual> indexBySourceRound(List<EchoLifecycleVisual> lifecycles) {
        Map<Integer, EchoLifecycleVisual> indexed = new HashMap<>();
        for (EchoLifecycleVisual lifecycle : lifecycles) {
            EchoLifecycleVisual prior = indexed.putIfAbsent(lifecycle.sourceRound(), lifecycle);
            if (prior != null) {
                throw new IllegalArgumentException("同一 sourceRound 只能有一个生命周期投影：" + lifecycle.sourceRound());
            }
        }
        return indexed;
    }

    private static void drawBadge(GraphicsContext gc,
                                  RenderViews.EchoTrail echo,
                                  EchoLifecycleVisual lifecycle,
                                  WorldTransform transform) {
        Vector2D anchor = echo.points().get(0);
        double x = transform.toCanvasX(anchor.x()) + echo.overlapOffsetPx();
        double y = transform.toCanvasY(anchor.y()) - BADGE_OFFSET_Y_PX;
        double left = x - BADGE_WIDTH_PX / 2.0;
        double top = y - BADGE_HEIGHT_PX / 2.0;
        boolean lastEffective = lifecycle != null && lifecycle.isLastEffectiveRound();

        gc.setGlobalAlpha(echo.newer() ? 0.90 : 0.72);
        gc.setFill(echo.newer() ? RenderPalette.ECHO_NEW : RenderPalette.ECHO_OLD);
        gc.fillRoundRect(left, top, BADGE_WIDTH_PX, BADGE_HEIGHT_PX, 5.0, 5.0);
        gc.setFill(RenderPalette.BACKGROUND);
        gc.fillText("E" + echo.sourceRound(), left + 5.0, top + 11.0);

        if (lastEffective) {
            gc.setGlobalAlpha(1.0);
            gc.setStroke(RenderPalette.INTERACTIVE);
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(left - LAST_RING_PADDING_PX, top - LAST_RING_PADDING_PX,
                    BADGE_WIDTH_PX + LAST_RING_PADDING_PX * 2.0,
                    BADGE_HEIGHT_PX + LAST_RING_PADDING_PX * 2.0, 7.0, 7.0);
            gc.setFill(RenderPalette.INTERACTIVE);
            gc.fillText("LAST", left, top - 5.0);
        }
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
