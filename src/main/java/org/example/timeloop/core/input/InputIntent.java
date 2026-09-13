package org.example.timeloop.core.input;

import org.example.timeloop.core.Direction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一个逻辑刻的不可变输入意图（C3）。
 *
 * <p>严格区分“新按下边沿”与“持续按住”，供 C3 的 autoDock 防误离开与单槽方向队列使用：
 * 只有 {@link #pressedThisTick} 产生动作；{@link #held} 只用于进入驻留型机关瞬间的旧键快照。</p>
 *
 * <p>tick 归属：一批按键边沿在<b>本刻逻辑更新之前</b>采集为本对象；逻辑层不得跨刻补算。
 * 该类型是纯 Java，可在无 JavaFX Toolkit 的 JUnit 中构造。</p>
 *
 * @param tick              该输入归属的逻辑刻
 * @param pressedThisTick   本刻新按下的逻辑键（边沿）
 * @param releasedThisTick  本刻释放的逻辑键（用于失焦/漏 release 对账）
 * @param held              本刻结束时仍按住的逻辑键
 * @param directionEdges    本刻方向类新按下，<b>按发生顺序</b>；末位即单槽队列应保留者
 */
public record InputIntent(
        long tick,
        Set<LogicalKey> pressedThisTick,
        Set<LogicalKey> releasedThisTick,
        Set<LogicalKey> held,
        List<Direction> directionEdges
) {

    public InputIntent {
        if (tick < 0) {
            throw new IllegalArgumentException("tick 必须 >= 0，实际 " + tick);
        }
        pressedThisTick = Set.copyOf(Objects.requireNonNull(pressedThisTick, "pressedThisTick"));
        releasedThisTick = Set.copyOf(Objects.requireNonNull(releasedThisTick, "releasedThisTick"));
        held = Set.copyOf(Objects.requireNonNull(held, "held"));
        directionEdges = List.copyOf(Objects.requireNonNull(directionEdges, "directionEdges"));
    }

    /** 本刻无任何输入的意图。 */
    public static InputIntent empty(long tick) {
        return new InputIntent(tick, Set.of(), Set.of(), Set.of(), List.of());
    }

    /** 是否为本刻新按下的边沿。 */
    public boolean isPressed(LogicalKey key) {
        return pressedThisTick.contains(key);
    }

    /** 是否在本刻结束时仍按住。 */
    public boolean isHeld(LogicalKey key) {
        return held.contains(key);
    }

    /**
     * 将本刻仍按住的方向键投影为不可变方向集合。
     * 非方向键不参与移动语义。
     */
    public Set<Direction> heldDirections() {
        return held.stream()
                .map(LogicalKey::direction)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 本刻最后一个方向新按下（末位胜出）。
     * 无方向边沿时返回空。
     */
    public Optional<Direction> lastDirectionEdge() {
        return directionEdges.isEmpty()
                ? Optional.empty()
                : Optional.of(directionEdges.get(directionEdges.size() - 1));
    }

    /** 本刻是否有方向新按下。 */
    public boolean hasDirectionEdge() {
        return !directionEdges.isEmpty();
    }
}
