package org.example.timeloop.render;

import org.example.timeloop.level.model.Vector2D;

import java.util.Objects;

/**
 * 时间线事件的不可变只读渲染投影。
 *
 * <p>位置一律为世界坐标。{@code tick}、事件类型和延迟量均由上游权威状态投影，
 * render 不从轨迹速度或当前轮次反推事件。</p>
 */
public record TimelineVisualEvent(
        int sourceRound,
        long tick,
        Vector2D worldPosition,
        TimelineEventKind kind,
        int delayTicks) {

    public TimelineVisualEvent {
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick 必须 >= 0");
        }
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(worldPosition.x()) || !Double.isFinite(worldPosition.y())) {
            throw new IllegalArgumentException("worldPosition 坐标必须为有限数");
        }
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks 必须 >= 0");
        }
        if (kind != TimelineEventKind.RAY_DELAY && delayTicks != 0) {
            throw new IllegalArgumentException("只有 RAY_DELAY 可以携带非零 delayTicks");
        }
    }
}
