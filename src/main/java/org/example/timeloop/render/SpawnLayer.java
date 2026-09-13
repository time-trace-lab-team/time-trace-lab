package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * C5 出生点标记图层（新增）。
 *
 * <p>原生 {@code SPAWN_POINT} 瓦片在画面上与普通地板没有区别，
 * 这一层把出生格画成金色同心圆，让玩家一眼看到本轮的起点。
 * 坐标由装配层注入，逐帧不变；本层不读任何玩法状态、不回写。</p>
 */
public final class SpawnLayer implements RenderLayer {

    private final double worldX;
    private final double worldY;
    private final double tileSize;
    private final WorldTransform transform;

    public SpawnLayer(double worldX, double worldY, double tileSize, WorldTransform transform) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.tileSize = tileSize;
        this.transform = transform;
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        double cx = transform.toCanvasX(worldX);
        double cy = transform.toCanvasY(worldY);
        double t = transform.scaled(tileSize);

        gc.setGlobalAlpha(1.0);
        gc.setFill(new RadialGradient(0, 0, cx, cy, t * 2.2, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f2c53d", 0.45)),
                new Stop(1, Color.web("#f2c53d", 0.0))));
        gc.fillOval(cx - t * 2.2, cy - t * 2.2, t * 4.4, t * 4.4);

        gc.setFill(RenderPalette.SPAWN);
        gc.fillOval(cx - t * 0.36, cy - t * 0.36, t * 0.72, t * 0.72);
        gc.setStroke(RenderPalette.SPAWN_RING);
        gc.setLineWidth(4.0);
        gc.strokeOval(cx - t * 0.36, cy - t * 0.36, t * 0.72, t * 0.72);

        gc.setFill(RenderPalette.SPAWN_CORE);
        gc.fillOval(cx - t * 0.17, cy - t * 0.17, t * 0.34, t * 0.34);
    }
}
