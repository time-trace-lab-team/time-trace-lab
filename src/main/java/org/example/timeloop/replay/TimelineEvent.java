package org.example.timeloop.replay;

import org.example.timeloop.core.Direction;

import java.util.Comparator;
import java.util.Objects;

/**
 * 时间线上的离散事件（不可变）。
 * 由开发二定义，开发一/三产生。
 *
 * <p>事件只在状态边沿写入：进入 autoDock、离开 autoDock、占用释放。
 * 持续 DOCKED 不重复发进入事件，但静止位置仍由 PlayerFrame 逐 tick 记录。</p>
 *
 * <p>同 tick 多事件先批量收集，再按 STABLE_ORDER 排序，保证确定性。
 * 排序优先级通过 {@link EventType#priority()} 显式声明，不依赖枚举声明顺序（{@code ordinal()}）。</p>
 */
public record TimelineEvent(
        long tick,
        String actorId,
        int sourceRound,
        String mechanismId,
        EventType eventType,
        Direction leaveDirection,
        String reason
) {

    /**
     * 事件类型。
     *
     * <p>priority 显式声明同 tick 内的事件处理顺序：
     * 先释放（LEFT=10），再占用清理（RELEASED=20），最后进入（ENTERED=30）。
     * 这样同 tick 内"离开 A 板 + 进入 B 板"会先处理离开，避免 actor 占用冲突。</p>
     *
     * <p>不使用 {@code ordinal()}：枚举声明顺序变化会导致排序静默改变，这是隐式陷阱。</p>
     */
    public enum EventType {
        DOCK_LEFT(10),
        OCCUPANCY_RELEASED(20),
        DOCK_ENTERED(30);

        private final int priority;

        EventType(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    /**
     * 稳定排序键。
     * 先按 tick 升序，再按事件类别优先级（LEFT=10 < RELEASED=20 < ENTERED=30），
     * 再按 mechanismId、actorId、sourceRound 字典序。
     * 不依赖集合迭代顺序，也不依赖枚举声明顺序，保证同输入同输出。
     */
    public static final Comparator<TimelineEvent> STABLE_ORDER =
            Comparator.comparingLong(TimelineEvent::tick)
                    .thenComparingInt(e -> e.eventType().priority())
                    .thenComparing(TimelineEvent::mechanismId)
                    .thenComparing(TimelineEvent::actorId)
                    .thenComparingInt(TimelineEvent::sourceRound);

    public TimelineEvent {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(mechanismId, "mechanismId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0");
        }
        if (sourceRound < 0) {
            throw new IllegalArgumentException("sourceRound must be >= 0");
        }
        if (actorId.isEmpty()) {
            throw new IllegalArgumentException("actorId must not be empty");
        }
        if (mechanismId.isEmpty()) {
            throw new IllegalArgumentException("mechanismId must not be empty");
        }
    }
}