package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.core.Direction;

import java.util.Objects;
import java.util.function.Supplier;

/** 当前玩家精灵层；只消费 app 注入的玩家投影、图集和世界变换。 */
public final class SpriteLayer implements RenderLayer {

    private final Supplier<PlayerSpriteVisual> visualSource;
    private final Supplier<WorldTransform> transformSource;

    public SpriteLayer(Supplier<PlayerSpriteVisual> visualSource,
                       Supplier<WorldTransform> transformSource) {
        this.visualSource = Objects.requireNonNull(visualSource, "visualSource");
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        PlayerSpriteVisual visual = Objects.requireNonNull(visualSource.get(),
                "SpriteLayer visualSource 在 render 时返回 null");
        if (!visual.enabled()) {
            return;
        }
        WorldTransform transform = Objects.requireNonNull(transformSource.get(),
                "SpriteLayer transformSource 在 render 时返回 null");
        RenderViews.Player player = visual.player();
        drawSprite(gc, visual.spriteSheet(), player.x(), player.y(), player.direction(), player.animation(), transform,
                player.phased() ? 0.62 : 1.0);
        gc.setGlobalAlpha(1.0);
    }

    static void drawSprite(GraphicsContext gc,
                           SpriteSheet sheet,
                           double worldX,
                           double worldY,
                           Direction direction,
                           org.example.timeloop.core.AnimationState animation,
                           WorldTransform transform,
                           double opacity) {
        SpriteSheet.SourceFrame source = sheet.sourceFrame(direction, animation);
        double width = transform.scaled(sheet.worldWidth());
        double height = transform.scaled(sheet.worldHeight());
        double x = transform.toCanvasX(worldX) - width / 2.0;
        double y = transform.toCanvasY(worldY) - height / 2.0;
        gc.setGlobalAlpha(opacity);
        gc.drawImage(sheet.image(), source.x(), source.y(), source.width(), source.height(), x, y, width, height);
    }
}
