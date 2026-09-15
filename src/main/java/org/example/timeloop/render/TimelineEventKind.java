package org.example.timeloop.render;

/** 只读时间线事件的视觉语义；事件事实由上游记录/回放系统提供。 */
public enum TimelineEventKind {
    DOCK_ENTER,
    DOCK_LEAVE,
    RAY_DELAY,
    ECHO_EXPIRE,
    TICK_MARK
}
