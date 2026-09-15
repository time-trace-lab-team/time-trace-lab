package org.example.timeloop.replay;

import org.example.timeloop.core.Direction;

import java.util.Comparator;
import java.util.Objects;

/**
 * 时间线上的离散事件（不可变）。
 * 由开发二定义，开发一/三产生。
 *
 * <p>事件只在状态边沿写入：进入 autoDock、离开 autoDock、占用释放、机关状态变化、玩家出口请求。
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
     * <p>priority 显式声明同 tick 内的事件处理顺序，与开发三规格文档 §3.2 的类别表一致：</p>
     * <ul>
     *   <li>DOCK_LEFT(10)：actor 合法离开某 dock 的边沿事实</li>
     *   <li>OCCUPANCY_RELEASED(20)：该 dock 完成占用释放</li>
     *   <li>DOCK_ENTERED(30)：actor 从区域外进入某 dock</li>
     *   <li>MECHANISM_STATE_CHANGED(40)：由占用变化派生的机关状态变化</li>
     *   <li>EXIT_REQUESTED(50)：当前玩家在满足权限后发起出口请求（玩家专属，残影不能触发）</li>
     * </ul>
     *
     * <p>不使用 {@code ordinal()}：枚举声明顺序变化会导致排序静默改变，这是隐式陷阱。
     * 未列入的类别不得自行插入排序；需要扩展时必须先更新开发三规格契约。</p>
     */
    public enum EventType {
        DOCK_LEFT(10),
        OCCUPANCY_RELEASED(20),
        DOCK_ENTERED(30),
        MECHANISM_STATE_CHANGED(40),
        EXIT_REQUESTED(50),
    /** 时滞射线命中：玩家被减速（{@code reason} 携带 {@code delay=`<迟到刻>`}）。纯追加，不影响既有优先级顺序。 */
    RAY_DELAY(60);

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
     * 先按 tick 升序，再按事件类别优先级，再按 mechanismId、actorId、sourceRound 字典序。
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