package org.example.timeloop.mechanism.ray;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Objects;

/**
 * 时滞射线。
 * 状态：OFF → WARNING → ACTIVE → OFF（循环）
 * 由共享 roundTick 驱动，不受暂停、教程或渲染帧率影响。
 */
public class Ray implements GameObserver {

    public enum State { OFF, WARNING, ACTIVE }

    private final String id;
    private final Vector2D start;
    private final Vector2D end;
    private final long warningStartTick;
    private final long warningDurationTicks;
    private final long activeStartTick;
    private final long activeDurationTicks;
    private final long cycleDurationTicks;
    /** 事件总线（注入；BUG-002 Phase 2 后不得使用任何全局单例）。 */
    private final GameEventBus bus;

    private State state = State.OFF;

    /**
     * @param bus 关卡装配持有的事件总线（注入，禁止全局单例）。
     *            L2-B 将在此之上完成「共享 roundTick 驱动 + 关卡接线 + 命中语义」的正式接线。
     */
    public Ray(String id, Vector2D start, Vector2D end,
               long warningStartTick, long warningDurationTicks,
               long activeStartTick, long activeDurationTicks,
               GameEventBus bus) {
        this.id = Objects.requireNonNull(id);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.warningStartTick = warningStartTick;
        this.warningDurationTicks = warningDurationTicks;
        this.activeStartTick = activeStartTick;
        this.activeDurationTicks = activeDurationTicks;
        this.cycleDurationTicks = warningStartTick + warningDurationTicks + activeDurationTicks;
        this.bus = Objects.requireNonNull(bus, "bus");

        bus.register(GameEvent.TICK_ADVANCED, this);
    }

    public String getId() { return id; }
    public Vector2D getStart() { return start; }
    public Vector2D getEnd() { return end; }
    public State getState() { return state; }

    /**
     * 每 tick 更新射线状态（由游戏循环调用）。
     */
    public void update(long roundTick) {
        long cycleTick = roundTick % cycleDurationTicks;

        if (cycleTick >= warningStartTick && cycleTick < warningStartTick + warningDurationTicks) {
            state = State.WARNING;
        } else if (cycleTick >= activeStartTick && cycleTick < activeStartTick + activeDurationTicks) {
            state = State.ACTIVE;
        } else {
            state = State.OFF;
        }
    }

    /**
     * 判断点是否在射线路径上（简化：点在线段上）。
     */
    public boolean containsPoint(Vector2D point, double width) {
        // 简化为点在线段上的距离判断
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return false;

        double t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / len2;
        if (t < 0 || t > 1) return false;

        double px = start.x() + t * dx;
        double py = start.y() + t * dy;
        double dist2 = (point.x() - px) * (point.x() - px) + (point.y() - py) * (point.y() - py);
        return dist2 < width * width;
    }

    public void reset() {
        state = State.OFF;
    }

    public void dispose() {
        bus.unregisterAll(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        // 监听 tick 更新；当前无生产调用方（RayManager 已删除），由调用方直接驱动 update(roundTick)
    }

    @Override
    public String toString() {
        return String.format("Ray{id='%s', state=%s}", id, state);
    }
}