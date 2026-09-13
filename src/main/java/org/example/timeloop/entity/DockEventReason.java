package org.example.timeloop.entity;

/**
 * C3 dock 事件的 {@code reason} 取值冻结集合（开发一）。
 *
 * <p>与开发二 {@code replay.TimelineEvent.reason}（String）对齐：本枚举的 {@link #name()}
 * 即写入事件的稳定字符串。跨模块规范，新增取值须 PM 批准。</p>
 *
 * <p>约束：{@code DOCK_ENTERED} 的 reason 恒为 {@code null}；{@code DOCK_LEFT} 只用
 * {@link #NEW_DIRECTION}；{@link #ROUND_END}/{@link #FULL_RESTART}/{@link #SCENE_EXIT}
 * 复用开发三 {@code AutoDockResetReason} 的同名值，{@link #ECHO_EXPIRED} 用于残影淘汰释放。</p>
 */
public enum DockEventReason {

    /** DOCK_LEFT：占用者按下合法出口方向，正常离开。 */
    NEW_DIRECTION,

    /** OCCUPANCY_RELEASED：普通轮末清理。 */
    ROUND_END,

    /** OCCUPANCY_RELEASED：残影淘汰导致的占用释放。 */
    ECHO_EXPIRED,

    /** OCCUPANCY_RELEASED：整局重开。 */
    FULL_RESTART,

    /** OCCUPANCY_RELEASED：退出关卡 / 场景销毁。 */
    SCENE_EXIT
}
