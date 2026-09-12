package org.example.timeloop.render;

import org.example.timeloop.core.MovementState;

import java.util.Objects;

/**
 * 把玩家的权威移动状态投影为无 JavaFX 依赖的视觉语义。
 *
 * <p>本类不计时、不插值，也不修改玩法状态；{@link PlayerLayer} 仅把该投影画到 Canvas。
 * 因此静止和减速的可见性始终由当前帧 {@link MovementState} 决定。</p>
 */
final class PlayerVisualProjection {

    enum BodyShape {
        CIRCLE,
        ROUNDED_SQUARE
    }

    record Style(BodyShape bodyShape,
                 boolean showsDirectionTick,
                 boolean showsSlowOutline,
                 boolean showsSlowTrail) {
        Style {
            Objects.requireNonNull(bodyShape, "bodyShape");
        }
    }

    private PlayerVisualProjection() {
    }

    static Style forMovementState(MovementState movementState) {
        Objects.requireNonNull(movementState, "movementState");
        return switch (movementState) {
            case IDLE -> new Style(BodyShape.ROUNDED_SQUARE, false, false, false);
            case CRUISING -> new Style(BodyShape.CIRCLE, true, false, false);
            case SLOWED -> new Style(BodyShape.CIRCLE, true, true, true);
            case DOCKED -> new Style(BodyShape.ROUNDED_SQUARE, true, false, false);
        };
    }
}
