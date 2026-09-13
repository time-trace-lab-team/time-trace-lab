package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.function.Supplier;

/**
 * C5 机关图层：驻留板、门、出口终端。
 *
 * <p>状态来自只读 {@link RenderViews.Frame}；渲染不写回机关状态。
 * 每个机关有自己的底色（板蓝、门红、终端琥珀），激活时满色并加光晕，
 * 未激活时退成灰蓝轮廓 —— 状态不只靠颜色区分。</p>
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

        double size = transform.scaled(tileSize * MARKER_SIZE_FACTOR);
        for (RenderViews.Mechanism mechanism : frameSource.get().mechanisms()) {
            double cx = transform.toCanvasX(mechanism.x());
            double cy = transform.toCanvasY(mechanism.y());

            switch (mechanism.kind()) {
                case PLATE -> drawPlate(gc, cx, cy, size, mechanism.active());
                case DOOR -> drawDoor(gc, cx, cy, size, mechanism.active());
                case EXIT -> drawExit(gc, cx, cy, size, mechanism.active());
            }
        }
    }

    /** 驻留板：圆角方 + 内框；激活时蓝色实心带光晕。 */
    private void drawPlate(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double s = size * 0.42;
        if (active) {
            glow(gc, cx, cy, size * 1.6, RenderPalette.PLATE, 0.45);
        }
        gc.setFill(active ? RenderPalette.PLATE : fade(RenderPalette.PLATE, 0.18));
        gc.fillRoundRect(cx - s, cy - s, s * 2, s * 2, 8, 8);
        gc.setStroke(active ? RenderPalette.PLATE_EDGE : fade(RenderPalette.PLATE, 0.45));
        gc.setLineWidth(4.0);
        gc.strokeRoundRect(cx - s, cy - s, s * 2, s * 2, 8, 8);
        if (active) {
            gc.setStroke(Color.web("#c8e1ff", 0.55));
            gc.setLineWidth(2.0);
            gc.strokeRoundRect(cx - s * 0.5, cy - s * 0.5, s, s, 4, 4);
        }
    }

    /** 门：菱形；解锁时红色实心带光晕，锁着时只留灰蓝轮廓。 */
    private void drawDoor(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double s = size * 0.50;
        if (active) {
            glow(gc, cx, cy, size * 2.1, RenderPalette.DOOR, 0.45);
        }
        double[] xs = {cx, cx + s, cx, cx - s};
        double[] ys = {cy - s, cy, cy + s, cy};
        gc.setFill(active ? RenderPalette.DOOR : fade(RenderPalette.DOOR, 0.18));
        gc.fillPolygon(xs, ys, 4);
        gc.setStroke(active ? RenderPalette.DOOR_EDGE : fade(RenderPalette.DOOR, 0.45));
        gc.setLineWidth(4.0);
        gc.strokePolygon(xs, ys, 4);
        if (active) {
            gc.setStroke(Color.web("#781414", 0.80));
            gc.setLineWidth(2.0);
            gc.strokeLine(cx - s, cy, cx + s, cy);
            gc.strokeLine(cx, cy - s, cx, cy + s);
        }
    }

    /** 终点终端：方框 + 内嵌 X；可交互时琥珀实心带光晕，并在正上方给出 `E` 按键提示。 */
    private void drawExit(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double s = size * 0.43;
        if (active) {
            glow(gc, cx, cy, size * 1.8, RenderPalette.INTERACTIVE, 0.50);
        }
        gc.setFill(active ? Color.web("#7a5c17") : fade(RenderPalette.INTERACTIVE, 0.18));
        gc.fillRoundRect(cx - s, cy - s, s * 2, s * 2, 6, 6);
        gc.setStroke(active ? Color.web("#ffe9b8") : fade(RenderPalette.INTERACTIVE, 0.45));
        gc.setLineWidth(4.0);
        gc.strokeRoundRect(cx - s, cy - s, s * 2, s * 2, 6, 6);
        if (active) {
            gc.setStroke(Color.web("#fff0c8", 0.80));
            gc.setLineWidth(3.0);
            gc.strokeLine(cx - s * 0.45, cy - s * 0.45, cx + s * 0.45, cy + s * 0.45);
            gc.strokeLine(cx + s * 0.45, cy - s * 0.45, cx - s * 0.45, cy + s * 0.45);
            // 交互提示：旧图层在终端可交互时会在正上方画 "E"（沿用默认字体与旧版位置，
            // 不引入新的字体依赖）。设计稿没有这个元素，但它是玩家判断
            // "现在能按 E 通关"的唯一视觉反馈，属于功能而非装饰，必须保留。
            gc.setFill(RenderPalette.INTERACTIVE);
            gc.fillText("E", cx - size * 0.08, cy - s - 7.0);
        }
    }

    /** 同色系的暗色，用于未激活态：保持色相、只降亮度，状态靠明度和光晕区分。 */
    private static Color fade(Color c, double a) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static void glow(GraphicsContext gc, double cx, double cy, double radius, Color base, double strength) {
        gc.setFill(new RadialGradient(0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(base.getRed(), base.getGreen(), base.getBlue(), strength)),
                new Stop(1, Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.0))));
        gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }
}
