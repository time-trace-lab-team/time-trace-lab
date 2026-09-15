package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * C5 残影轨迹图层。
 *
 * <p>读取只读 {@link RenderViews.Frame} 的轨迹点；较新残影为实线、较旧为虚线，
 * 两条轨迹重叠时按代际做 2–4 像素平行错位，避免只靠颜色识别。</p>
 */
public final class EchoTrailLayer implements RenderLayer {

    private static final double NEW_ECHO_ALPHA = 0.82;
    private static final double OLD_ECHO_ALPHA = 0.50;

    private final Supplier<RenderViews.Frame> frameSource;
    private final Supplier<WorldTransform> transformSource;

    public EchoTrailLayer(Supplier<RenderViews.Frame> frameSource, WorldTransform transform) {
        this(frameSource, fixedTransform(transform));
    }

    public EchoTrailLayer(Supplier<RenderViews.Frame> frameSource, Supplier<WorldTransform> transformSource) {
        this.frameSource = frameSource;
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = currentTransform();
        gc.setLineWidth(2.0);
        List<RenderViews.EchoTrail> echoes = frameSource.get().echoes();

        for (RenderViews.EchoTrail echo : echoes) {
            List<Vector2D> points = echo.points();
            if (points.size() < 2) {
                continue;
            }
            double offset = echo.overlapOffsetPx();
            double[] xs = new double[points.size()];
            double[] ys = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                xs[i] = transform.toCanvasX(points.get(i).x()) + offset;
                ys[i] = transform.toCanvasY(points.get(i).y()) + offset;
            }

            gc.setGlobalAlpha(echo.newer() ? NEW_ECHO_ALPHA : OLD_ECHO_ALPHA);
            gc.setStroke(echo.newer() ? RenderPalette.ECHO_NEW : RenderPalette.ECHO_OLD);
            gc.setLineDashes(echo.newer() ? new double[0] : new double[]{6.0, 4.0});
            gc.strokePolyline(xs, ys, points.size());
            gc.setLineDashes();
        }
        gc.setGlobalAlpha(1.0);
    }

    private WorldTransform currentTransform() {
        return Objects.requireNonNull(transformSource.get(), "EchoTrailLayer transformSource 在 render 时返回 null");
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
