package org.example.timeloop.render;

import javafx.scene.canvas.GraphicsContext;
import org.example.timeloop.core.ActorPhase;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 残影精灵层；残影实体帧与资源均由 app 并行注入，不读取 EchoTrail。 */
public final class EchoSpriteLayer implements RenderLayer {

    private final Supplier<List<EchoSpriteVisual>> visualSource;
    private final Supplier<WorldTransform> transformSource;

    public EchoSpriteLayer(Supplier<List<EchoSpriteVisual>> visualSource,
                           Supplier<WorldTransform> transformSource) {
        this.visualSource = Objects.requireNonNull(visualSource, "visualSource");
        this.transformSource = Objects.requireNonNull(transformSource, "transformSource");
    }

    @Override
    public void render(GraphicsContext gc, double worldW, double worldH, double alpha) {
        List<EchoSpriteVisual> visuals = List.copyOf(Objects.requireNonNull(visualSource.get(),
                "EchoSpriteLayer visualSource 在 render 时返回 null"));
        WorldTransform transform = Objects.requireNonNull(transformSource.get(),
                "EchoSpriteLayer transformSource 在 render 时返回 null");
        for (EchoSpriteVisual visual : visuals) {
            if (!visual.enabled()) {
                continue;
            }
            drawEcho(gc, visual, transform);
        }
        gc.setGlobalAlpha(1.0);
        gc.setLineDashes();
    }

    private static void drawEcho(GraphicsContext gc, EchoSpriteVisual visual, WorldTransform transform) {
        EchoActor actor = visual.actor();
        double opacity = switch (actor.actorPhase()) {
            case AVAILABLE -> 0.78;
            case RECOVERING -> 0.68;
            case PHASED -> 0.48;
        };
        SpriteLayer.drawSprite(gc, visual.spriteSheet(), actor.x(), actor.y(), actor.direction(), actor.animation(),
                transform, opacity);

        double width = transform.scaled(visual.spriteSheet().worldWidth());
        double height = transform.scaled(visual.spriteSheet().worldHeight());
        double left = transform.toCanvasX(actor.x()) - width / 2.0;
        double top = transform.toCanvasY(actor.y()) - height / 2.0;
        boolean newer = actor.sourceRound() % 2 == 0;
        gc.setGlobalAlpha(1.0);
        gc.setStroke(newer ? RenderPalette.ECHO_NEW : RenderPalette.ECHO_OLD);
        gc.setLineWidth(1.5);
        gc.setLineDashes(newer ? new double[0] : new double[]{4.0, 3.0});
        gc.strokeRoundRect(left - 2.0, top - 2.0, width + 4.0, height + 4.0, 4.0, 4.0);
        gc.setLineDashes();
        gc.setFill(newer ? RenderPalette.ECHO_NEW : RenderPalette.ECHO_OLD);
        gc.fillText("E" + actor.sourceRound(), left, top - 4.0);
    }
}
