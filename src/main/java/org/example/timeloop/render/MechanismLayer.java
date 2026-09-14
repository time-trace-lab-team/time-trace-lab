package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * C5 机关图层：驻留板、开关表现变体、门、出口终端。
 *
 * <p>状态来自只读 {@link RenderViews.Frame}；渲染不写回机关状态。
 * 每个机关有自己的底色（板蓝、门红、终端琥珀），激活时满色并加光晕，
 * 未激活时退成灰蓝轮廓 —— 状态不只靠颜色区分。</p>
 */
public final class MechanismLayer implements RenderLayer {

    private static final double MARKER_SIZE_FACTOR = 0.8;

    private final Supplier<RenderViews.Frame> frameSource;
    private final double tileSize;
    private final Supplier<WorldTransform> transformSource;

    public MechanismLayer(Supplier<RenderViews.Frame> frameSource, double tileSize, WorldTransform transform) {
        this(frameSource, tileSize, fixedTransform(transform));
    }

    public MechanismLayer(Supplier<RenderViews.Frame> frameSource,
                          double tileSize,
                          Supplier<WorldTransform> transformSource) {
        this.frameSource = frameSource;
        this.tileSize = tileSize;
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        WorldTransform transform = currentTransform();
        gc.setGlobalAlpha(1.0);

        double size = transform.scaled(tileSize * MARKER_SIZE_FACTOR);
        for (RenderViews.Mechanism mechanism : frameSource.get().mechanisms()) {
            double cx = transform.toCanvasX(mechanism.x());
            double cy = transform.toCanvasY(mechanism.y());

            switch (mechanism.kind()) {
                case PLATE -> drawPlate(gc, cx, cy, size, mechanism.active());
                case SWITCH -> drawSwitch(gc, cx, cy, size, mechanism.active());
                case DOOR -> drawDoor(gc, cx, cy, size, mechanism.active());
                case EXIT -> drawExit(gc, cx, cy, size, mechanism.active());
            }
        }
    }

    private WorldTransform currentTransform() {
        return Objects.requireNonNull(transformSource.get(), "MechanismLayer transformSource 在 render 时返回 null");
    }

    private static Supplier<WorldTransform> fixedTransform(WorldTransform transform) {
        WorldTransform fixed = Objects.requireNonNull(transform, "transform");
        return () -> fixed;
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

    /**
     * 开关：横向踏板 + 下沉按键。{@code active} 表示本轮锁存 ON，而不是当前是否有人站在上面。
     * 因此 ON 态保留压下位置与亮起指示，直到机关在轮末 reset。
     */
    private void drawSwitch(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double width = size * 0.88;
        double baseHeight = size * 0.34;
        double buttonWidth = size * 0.56;
        double buttonHeight = size * 0.22;
        double buttonY = cy + (active ? size * 0.10 : -size * 0.10);

        if (active) {
            glow(gc, cx, cy, size * 1.55, RenderPalette.INTERACTIVE, 0.45);
        }

        gc.setFill(fade(RenderPalette.INACTIVE_OUTLINE, 0.85));
        gc.fillRoundRect(cx - width / 2.0, cy - baseHeight / 2.0, width, baseHeight, 10, 10);
        gc.setStroke(active ? Color.web("#ffe9b8") : fade(RenderPalette.INTERACTIVE, 0.48));
        gc.setLineWidth(3.0);
        gc.strokeRoundRect(cx - width / 2.0, cy - baseHeight / 2.0, width, baseHeight, 10, 10);

        gc.setFill(active ? RenderPalette.INTERACTIVE : fade(RenderPalette.INTERACTIVE, 0.30));
        gc.fillRoundRect(cx - buttonWidth / 2.0, buttonY - buttonHeight / 2.0,
                buttonWidth, buttonHeight, 8, 8);
        gc.setStroke(active ? Color.web("#fff0c8") : fade(RenderPalette.INTERACTIVE, 0.58));
        gc.setLineWidth(2.0);
        gc.strokeRoundRect(cx - buttonWidth / 2.0, buttonY - buttonHeight / 2.0,
                buttonWidth, buttonHeight, 8, 8);

        gc.setStroke(active ? Color.web("#7a5c17") : fade(RenderPalette.TEXT, 0.55));
        gc.setLineWidth(2.0);
        double indicatorY = buttonY + (active ? 0.0 : -size * 0.025);
        gc.strokeLine(cx - size * 0.12, indicatorY, cx + size * 0.12, indicatorY);
    }

    /**
     * 独立房门：关闭时门扇和竖栅封住入口，打开时只保留两侧门框和顶梁。
     * 与 {@link #drawExit} 的终点闸门分开：房门不绘制 {@code E} 提示，也不使用终点门栅样式。
     */
    private void drawDoor(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double halfWidth = size * 0.50;
        double halfHeight = size * 0.58;
        double frameWidth = size * 0.16;

        gc.setFill(active ? fade(RenderPalette.DOOR_EDGE, 0.82) : fade(RenderPalette.DOOR, 0.88));
        gc.setStroke(active ? RenderPalette.PLATE_EDGE : RenderPalette.DOOR_EDGE);
        gc.setLineWidth(3.0);

        if (active) {
            // 中央保留从上到下的空白通道，只画门框与顶梁。
            gc.fillRoundRect(cx - halfWidth, cy - halfHeight, frameWidth, halfHeight * 2.0, 5, 5);
            gc.fillRoundRect(cx + halfWidth - frameWidth, cy - halfHeight,
                    frameWidth, halfHeight * 2.0, 5, 5);
            gc.fillRoundRect(cx - halfWidth, cy - halfHeight,
                    halfWidth * 2.0, frameWidth, 5, 5);
            gc.strokeRoundRect(cx - halfWidth, cy - halfHeight,
                    halfWidth * 2.0, halfHeight * 2.0, 6, 6);

            gc.setStroke(Color.web("#c8e1ff", 0.72));
            gc.setLineWidth(2.0);
            gc.strokeLine(cx - halfWidth + frameWidth, cy + halfHeight * 0.72,
                    cx + halfWidth - frameWidth, cy + halfHeight * 0.72);
        } else {
            // 高不透明门扇覆盖入口中心，竖栅进一步强化“不可通行”结构。
            gc.fillRoundRect(cx - halfWidth, cy - halfHeight,
                    halfWidth * 2.0, halfHeight * 2.0, 6, 6);
            gc.strokeRoundRect(cx - halfWidth, cy - halfHeight,
                    halfWidth * 2.0, halfHeight * 2.0, 6, 6);

            gc.setStroke(Color.web("#781414", 0.82));
            gc.setLineWidth(2.0);
            for (double factor : new double[]{-0.50, 0.0, 0.50}) {
                double barX = cx + halfWidth * factor;
                gc.strokeLine(barX, cy - halfHeight * 0.72, barX, cy + halfHeight * 0.72);
            }
        }
    }

    /**
     * 闸门终点：关闭时画出封闭门栅，解锁后画出两侧门框与通行缺口。
     * 可交互时保留既有 {@code E} 提示。
     */
    private void drawExit(GraphicsContext gc, double cx, double cy, double size, boolean active) {
        double s = size * 0.48;
        if (active) {
            glow(gc, cx, cy, size * 1.8, RenderPalette.INTERACTIVE, 0.50);
        }
        gc.setStroke(active ? Color.web("#ffe9b8") : fade(RenderPalette.INTERACTIVE, 0.48));
        gc.setLineWidth(4.0);

        if (active) {
            double pillarWidth = s * 0.34;
            gc.setFill(Color.web("#7a5c17"));
            gc.fillRoundRect(cx - s, cy - s, pillarWidth, s * 2, 6, 6);
            gc.fillRoundRect(cx + s - pillarWidth, cy - s, pillarWidth, s * 2, 6, 6);
            gc.strokeRoundRect(cx - s, cy - s, s * 2, s * 2, 6, 6);
            gc.setStroke(Color.web("#fff0c8", 0.82));
            gc.setLineWidth(2.0);
            gc.strokeLine(cx - s + pillarWidth, cy - s * 0.62, cx + s - pillarWidth, cy - s * 0.62);
            // 交互提示：旧图层在终端可交互时会在正上方画 "E"（沿用默认字体与旧版位置，
            // 不引入新的字体依赖）。设计稿没有这个元素，但它是玩家判断
            // "现在能按 E 通关"的唯一视觉反馈，属于功能而非装饰，必须保留。
            gc.setFill(RenderPalette.INTERACTIVE);
            gc.fillText("E", cx - size * 0.08, cy - s - 7.0);
        } else {
            gc.setFill(fade(RenderPalette.DOOR, 0.34));
            gc.fillRoundRect(cx - s, cy - s, s * 2, s * 2, 6, 6);
            gc.strokeRoundRect(cx - s, cy - s, s * 2, s * 2, 6, 6);
            gc.setStroke(fade(RenderPalette.DOOR_EDGE, 0.55));
            gc.setLineWidth(2.0);
            gc.strokeLine(cx - s * 0.42, cy - s * 0.72, cx - s * 0.42, cy + s * 0.72);
            gc.strokeLine(cx, cy - s * 0.72, cx, cy + s * 0.72);
            gc.strokeLine(cx + s * 0.42, cy - s * 0.72, cx + s * 0.42, cy + s * 0.72);
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
