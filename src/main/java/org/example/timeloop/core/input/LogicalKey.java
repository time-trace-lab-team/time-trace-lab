package org.example.timeloop.core.input;

/**
 * 逻辑输入键（C3）。
 *
 * <p>与物理键码解耦：JavaFX {@code KeyEvent} 只在边界层（{@code app/}）转成该逻辑键，
 * 逻辑层不保存 {@code KeyEvent}。{@code W/A/S/D} 与方向键映射到同一个 {@code DIR_*}，
 * 物理差异不进入逻辑层。</p>
 *
 * <p>{@link #PHASE} 属于相位下潜（C4 消费）；本类只定义键，不实现动作。</p>
 */
public enum LogicalKey {
    DIR_UP,
    DIR_DOWN,
    DIR_LEFT,
    DIR_RIGHT,
    /** E：终端交互。 */
    INTERACT,
    /** Space：相位下潜（C4 消费）。 */
    PHASE
}
