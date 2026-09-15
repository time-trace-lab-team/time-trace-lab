package org.example.timeloop.render;

import java.util.Objects;

/** app 注入的残影精灵视觉投影；{@code enabled} 是与旧 EchoTrailLayer 的显式互斥开关。 */
public record EchoSpriteVisual(EchoActor actor, SpriteSheet spriteSheet, boolean enabled) {
    public EchoSpriteVisual {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(spriteSheet, "spriteSheet");
    }
}
