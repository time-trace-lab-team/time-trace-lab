package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 路径节点的只读转向提示图层。
 *
 * <p>节点来自关卡路径几何，玩家坐标只用于计算亮度；本图层绝不反推节点、编码机关状态或回写玩法。</p>
 */
public final class PathNodeHintLayer implements RenderLayer {

    private static final double DIAMOND_SIZE_PX = 8.0;
    private static final double DIM_ALPHA = 0.45;

    /** 节点提示亮度档位。 */
    public enum HintLevel {
        HIDDEN,
        DIM,
        BRIGHT
    }

    private final Supplier<RenderViews.Frame> frameSource;
    private final List<RenderViews.PathNodeMarker> nodes;
    private final double tileSize;
    private final WorldTransform transform;

    public PathNodeHintLayer(Supplier<RenderViews.Frame> frameSource,
                             List<RenderViews.PathNodeMarker> nodes,
                             double tileSize,
                             WorldTransform transform) {
        this.frameSource = Objects.requireNonNull(frameSource, "frameSource");
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (!Double.isFinite(tileSize) || tileSize <= 0.0) {
            throw new IllegalArgumentException("tileSize 必须为正有限数");
        }
        this.tileSize = tileSize;
        this.transform = Objects.requireNonNull(transform, "transform");
    }

    /**
     * 按玩家与节点的世界中心距确定显示档位。
     *
     * <p>距离不超过一格时提亮；一至两格之间以低亮度显示；更远则隐藏。</p>
     */
    public static HintLevel levelFor(double distanceWorld, double tileSize) {
        if (!Double.isFinite(distanceWorld) || distanceWorld < 0.0) {
            throw new IllegalArgumentException("distanceWorld 必须为非负有限数");
        }
        if (!Double.isFinite(tileSize) || tileSize <= 0.0) {
            throw new IllegalArgumentException("tileSize 必须为正有限数");
        }
        if (distanceWorld <= tileSize) {
            return HintLevel.BRIGHT;
        }
        if (distanceWorld <= tileSize * 2.0) {
            return HintLevel.DIM;
        }
        return HintLevel.HIDDEN;
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        RenderViews.Player player = frameSource.get().player();
        double half = DIAMOND_SIZE_PX / 2.0;
        gc.setStroke(RenderPalette.INTERACTIVE);
        gc.setLineWidth(1.5);

        for (RenderViews.PathNodeMarker node : nodes) {
            double distance = Math.hypot(player.x() - node.x(), player.y() - node.y());
            HintLevel level = levelFor(distance, tileSize);
            if (level == HintLevel.HIDDEN) {
                continue;
            }

            double cx = transform.toCanvasX(node.x());
            double cy = transform.toCanvasY(node.y());
            gc.setGlobalAlpha(level == HintLevel.BRIGHT ? 1.0 : DIM_ALPHA);
            gc.strokePolygon(
                    new double[]{cx, cx + half, cx, cx - half},
                    new double[]{cy - half, cy, cy + half, cy},
                    4);
        }
        gc.setGlobalAlpha(1.0);
    }
}
