package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.level.model.Vector2D;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 第三关三路线与 J 分岔的静态可读性图层。
 *
 * <p>路线在构造时作为不可变世界坐标列表注入。控制路线使用虚线，B 支路使用方形刻痕，
 * 最终主通道使用双轨；因而路线职责不只依赖颜色。图层只投影和绘制，绝不读取关卡文件、
 * 推算路线或回写玩法状态。</p>
 */
public final class RouteReadabilityLayer implements RenderLayer {

    private static final double CONTROL_LINE_WIDTH_PX = 2.5;
    private static final double BRANCH_LINE_WIDTH_PX = 3.0;
    private static final double FINAL_OUTER_WIDTH_PX = 6.0;
    private static final double FINAL_INNER_WIDTH_PX = 2.0;
    private static final double BRANCH_MARK_SIZE_PX = 6.0;
    private static final double JUNCTION_DIAMOND_HALF_PX = 5.0;
    private static final double JUNCTION_ARROW_LENGTH_PX = 14.0;
    private static final double JUNCTION_ARROW_HALF_WIDTH_PX = 4.0;

    private final List<RouteVisual> routes;
    private final List<RouteJunctionVisual> junctions;
    private final Supplier<WorldTransform> transformSource;

    public RouteReadabilityLayer(List<RouteVisual> routes,
                                 List<RouteJunctionVisual> junctions,
                                 WorldTransform transform) {
        this(routes, junctions, fixedTransform(transform));
    }

    public RouteReadabilityLayer(List<RouteVisual> routes,
                                 List<RouteJunctionVisual> junctions,
                                 Supplier<WorldTransform> transformSource) {
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes"));
        this.junctions = List.copyOf(Objects.requireNonNull(junctions, "junctions"));
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = Objects.requireNonNull(
                transformSource.get(), "RouteReadabilityLayer transformSource 在 render 时返回 null");

        for (RouteVisual route : routes) {
            switch (route.kind()) {
                case CONTROL_E1 -> drawControlRoute(gc, route.worldPoints(), transform);
                case BRANCH_E2 -> drawBranchRoute(gc, route.worldPoints(), transform);
                case FINAL_PLAYER -> drawFinalRoute(gc, route.worldPoints(), transform);
            }
        }
        for (RouteJunctionVisual junction : junctions) {
            drawJunction(gc, junction.worldPosition(), transform);
        }
        gc.setLineDashes();
        gc.setGlobalAlpha(1.0);
    }

    private static void drawControlRoute(GraphicsContext gc, List<Vector2D> points, WorldTransform transform) {
        gc.setGlobalAlpha(0.82);
        gc.setStroke(RenderPalette.ECHO_OLD);
        gc.setLineWidth(CONTROL_LINE_WIDTH_PX);
        gc.setLineDashes(8.0, 5.0);
        strokePolyline(gc, points, transform);
        gc.setLineDashes();
    }

    private static void drawBranchRoute(GraphicsContext gc, List<Vector2D> points, WorldTransform transform) {
        gc.setGlobalAlpha(0.88);
        gc.setStroke(RenderPalette.ECHO_NEW);
        gc.setLineWidth(BRANCH_LINE_WIDTH_PX);
        strokePolyline(gc, points, transform);

        double half = BRANCH_MARK_SIZE_PX / 2.0;
        for (Vector2D point : points) {
            double x = transform.toCanvasX(point.x());
            double y = transform.toCanvasY(point.y());
            gc.strokeRect(x - half, y - half, BRANCH_MARK_SIZE_PX, BRANCH_MARK_SIZE_PX);
        }
    }

    private static void drawFinalRoute(GraphicsContext gc, List<Vector2D> points, WorldTransform transform) {
        gc.setGlobalAlpha(0.92);
        gc.setStroke(RenderPalette.INTERACTIVE);
        gc.setLineWidth(FINAL_OUTER_WIDTH_PX);
        strokePolyline(gc, points, transform);
        gc.setStroke(RenderPalette.BACKGROUND);
        gc.setLineWidth(FINAL_INNER_WIDTH_PX);
        strokePolyline(gc, points, transform);
    }

    private static void drawJunction(GraphicsContext gc, Vector2D worldPosition, WorldTransform transform) {
        double x = transform.toCanvasX(worldPosition.x());
        double y = transform.toCanvasY(worldPosition.y());

        gc.setGlobalAlpha(1.0);
        gc.setFill(RenderPalette.TEXT);
        gc.fillPolygon(
                new double[]{x, x + JUNCTION_DIAMOND_HALF_PX, x, x - JUNCTION_DIAMOND_HALF_PX},
                new double[]{y - JUNCTION_DIAMOND_HALF_PX, y, y + JUNCTION_DIAMOND_HALF_PX, y},
                4);

        gc.setFill(RenderPalette.ECHO_NEW);
        gc.fillPolygon(
                new double[]{x, x - JUNCTION_ARROW_HALF_WIDTH_PX, x + JUNCTION_ARROW_HALF_WIDTH_PX},
                new double[]{y - JUNCTION_ARROW_LENGTH_PX, y - JUNCTION_ARROW_LENGTH_PX + JUNCTION_ARROW_HALF_WIDTH_PX,
                        y - JUNCTION_ARROW_LENGTH_PX + JUNCTION_ARROW_HALF_WIDTH_PX},
                3);
        gc.fillText("B", x - 4.0, y - JUNCTION_ARROW_LENGTH_PX - 4.0);

        gc.setFill(RenderPalette.INTERACTIVE);
        gc.fillPolygon(
                new double[]{x + JUNCTION_ARROW_LENGTH_PX, x + JUNCTION_ARROW_LENGTH_PX - JUNCTION_ARROW_HALF_WIDTH_PX,
                        x + JUNCTION_ARROW_LENGTH_PX - JUNCTION_ARROW_HALF_WIDTH_PX},
                new double[]{y, y - JUNCTION_ARROW_HALF_WIDTH_PX, y + JUNCTION_ARROW_HALF_WIDTH_PX},
                3);
        gc.fillText("MAIN", x + JUNCTION_ARROW_LENGTH_PX + 4.0, y + 4.0);
    }

    private static void strokePolyline(GraphicsContext gc, List<Vector2D> points, WorldTransform transform) {
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            Vector2D point = points.get(index);
            xs[index] = transform.toCanvasX(point.x());
            ys[index] = transform.toCanvasY(point.y());
        }
        gc.strokePolyline(xs, ys, points.size());
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
    }
}
