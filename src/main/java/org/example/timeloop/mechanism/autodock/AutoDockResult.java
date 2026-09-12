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
        /**
         * @deprecated X-MOVE-COLLAPSE-01-DEV3 L-1（PM 2026-09-10 批准）起，合法出口在区域内也同刻释放，
         *     本状态<b>不再由 {@code AutoDockService.tryLeave} 产生</b>。
         *     保留仅为兼容既有调用方（含 {@code entity/C3DockControllerTest} 的假端口），
         *     不参与任何新的判定路径。
         */
        @Deprecated
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
