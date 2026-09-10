package org.example.timeloop.mechanism.autodock;

/** autoDock 状态操作的确定性结果。 */
public record AutoDockResult(Status status, long tick, AutoDockView view) {

    public enum Status {
        ENTERED,
        LEFT,
        ALREADY_OCCUPIED,
        OUTSIDE_REGION,
        NOT_OCCUPANT,
        INVALID_EXIT_DIRECTION,
        NOT_OUTSIDE_REGION,
        SAME_TICK_REENTRY_BLOCKED,
        UNKNOWN_DOCK
    }

    public AutoDockResult {
        if (status == null) {
            throw new IllegalArgumentException("autoDock result 缺少 status");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("autoDock result 的 tick 不能为负数");
        }
    }
}
