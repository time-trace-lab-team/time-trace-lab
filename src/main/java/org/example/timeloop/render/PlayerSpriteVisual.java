package org.example.timeloop.render;

import java.util.Objects;

/** app 注入的当前玩家精灵视觉投影；{@code enabled} 是与旧 PlayerLayer 的显式互斥开关。 */
public record PlayerSpriteVisual(RenderViews.Player player, SpriteSheet spriteSheet, boolean enabled) {
    public PlayerSpriteVisual {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(spriteSheet, "spriteSheet");
    }
}
