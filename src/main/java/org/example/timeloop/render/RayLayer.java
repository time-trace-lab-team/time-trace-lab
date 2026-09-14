package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.Objects;
import java.util.function.Supplier;

/** L02-B 射线图层；只消费只读世界坐标投影，不依赖机制层。 */
public final class RayLayer implements RenderLayer {

    private final Supplier<RenderViews.Frame> frameSource;
    private final Supplier<WorldTransform> transformSource;

    public RayLayer(Supplier<RenderViews.Frame> frameSource, WorldTransform transform) {
        this(frameSource, fixedTransform(transform));
    }

    public RayLayer(Supplier<RenderViews.Frame> frameSource, Supplier<WorldTransform> transformSource) {
        this.frameSource = Objects.requireNonNull(frameSource, "frameSource");
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = Objects.requireNonNull(
                transformSource.get(), "RayLayer transformSource 在 render 时返回 null");

        for (RenderViews.RayBeam ray : frameSource.get().rays()) {
            double startX = transform.toCanvasX(ray.startX());
            double startY = transform.toCanvasY(ray.startY());
            double endX = transform.toCanvasX(ray.endX());
            double endY = transform.toCanvasY(ray.endY());

            switch (ray.state()) {
                case OFF -> drawOff(gc, startX, startY, endX, endY);
                case WARNING -> drawWarning(gc, startX, startY, endX, endY);
                case ACTIVE -> drawActive(gc, startX, startY, endX, endY);
            }
        }
        restore(gc);
    }

    private static void drawOff(GraphicsContext gc, double x1, double y1, double x2, double y2) {
        gc.setGlobalAlpha(0.28);
        gc.setStroke(RenderPalette.RAY_OFF);
        gc.setLineWidth(1.0);
        gc.setLineDashes(2.0, 7.0);
        gc.strokeLine(x1, y1, x2, y2);
    }

    private static void drawWarning(GraphicsContext gc, double x1, double y1, double x2, double y2) {
        gc.setGlobalAlpha(0.78);
        gc.setStroke(RenderPalette.RAY_WARNING);
        gc.setLineWidth(2.0);
        gc.setLineDashes(8.0, 5.0);
        gc.strokeLine(x1, y1, x2, y2);
    }

    private static void drawActive(GraphicsContext gc, double x1, double y1, double x2, double y2) {
        gc.setLineDashes();
        gc.setGlobalAlpha(0.22);
        gc.setStroke(RenderPalette.RAY_ACTIVE);
        gc.setLineWidth(9.0);
        gc.strokeLine(x1, y1, x2, y2);

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(3.0);
        gc.strokeLine(x1, y1, x2, y2);
    }

    private static void restore(GraphicsContext gc) {
        gc.setGlobalAlpha(1.0);
        gc.setLineDashes();
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
