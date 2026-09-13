package org.example.timeloop.render;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canvas 边界适配器。
 * - 持有 Canvas 引用以获取尺寸和清屏；
 * - 管理图层顺序；
 * - 不持有 Scene，不管理窗口/舞台/页面切换。
 */
public final class CanvasAdapter {

    private final Canvas canvas;
    private final List<RenderLayer> layers = new ArrayList<>();

    public CanvasAdapter(Canvas canvas) {
        this.canvas = Objects.requireNonNull(canvas);
    }

    public void addLayer(RenderLayer layer) {
        layers.add(Objects.requireNonNull(layer));
    }

    public void removeLayer(RenderLayer layer) {
        layers.remove(layer);
    }

    public List<RenderLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public void renderFrame(double worldW, double worldH, double alpha) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();

        // 径向渐变背景：中心略亮，四周压黑
        gc.setFill(new RadialGradient(0, 0, w / 2.0, h / 2.0, Math.max(w, h) * 0.70,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, RenderPalette.BACKGROUND_CENTER),
                new Stop(1, RenderPalette.BACKGROUND)));
        gc.fillRect(0, 0, w, h);

        for (RenderLayer layer : layers) {
            gc.save();
            try {
                layer.render(gc, worldW, worldH, alpha);
            } finally {
                gc.restore();
            }
        }
    }

    public double getWidth() {
        return canvas.getWidth();
    }

    public double getHeight() {
        return canvas.getHeight();
    }
}
