package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 输入累加器（集成层，纯 Java，不依赖 JavaFX）。
 *
 * <p>JavaFX 的 {@code KeyEvent} 在 {@code app/} 的监听器里转成 {@link LogicalKey} 后喂入本类；
 * 每个逻辑刻开始时由 {@code stepOnce()} 调用 {@link #drain(long)} 取走本刻意图。
 * 严格区分"新按下边沿"与"持续按住"：只有 {@code onKeyPressed} 的首次进入产生边沿。</p>
 */
final class InputAccumulator {

    private final Set<LogicalKey> held = new LinkedHashSet<>();
    private final Set<LogicalKey> pressedThisTick = new LinkedHashSet<>();
    private final Set<LogicalKey> releasedThisTick = new LinkedHashSet<>();
    private final List<Direction> directionEdges = new ArrayList<>();

    void onKeyPressed(LogicalKey key) {
        if (held.add(key)) {
            pressedThisTick.add(key);
            toDirection(key).ifPresent(directionEdges::add);
        }
    }

    void onKeyReleased(LogicalKey key) {
        if (held.remove(key)) {
            releasedThisTick.add(key);
        }
    }

    /** 失焦：把仍按住的键视为已释放，避免"卡键"（裁决草案 E）。 */
    void releaseAll() {
        releasedThisTick.addAll(held);
        held.clear();
    }

    /** 取走本刻意图并清空边沿。 */
    InputIntent drain(long tick) {
        InputIntent intent = new InputIntent(
                tick,
                Set.copyOf(pressedThisTick),
                Set.copyOf(releasedThisTick),
                Set.copyOf(held),
                List.copyOf(directionEdges));
        pressedThisTick.clear();
        releasedThisTick.clear();
        directionEdges.clear();
        return intent;
    }

    static Optional<Direction> toDirection(LogicalKey key) {
        return switch (key) {
            case DIR_UP -> Optional.of(Direction.UP);
            case DIR_DOWN -> Optional.of(Direction.DOWN);
            case DIR_LEFT -> Optional.of(Direction.LEFT);
            case DIR_RIGHT -> Optional.of(Direction.RIGHT);
            case INTERACT, PHASE -> Optional.empty();
        };
    }
}
