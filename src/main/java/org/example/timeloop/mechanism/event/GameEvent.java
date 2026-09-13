package org.example.timeloop.mechanism.event;

/**
 * 机关事件的不可变记录。事件只描述"发生了什么"，**不承载 actor 归属的改写**。
 *
 * <p><b>{@code sourceRound} 的不变量（R5-B 冻结，PM 2026-09-13 裁决 §五）</b>：</p>
 * <ul>
 *   <li>{@code 0} = 活玩家（本轮的当前玩家）——该值**不得被残影替换**；</li>
 *   <li>{@code N >= 1} = 第 N 轮产生的残影，且**回放写入机关时**的 actor 归属必须与
 *       {@code "echo_" + N} 一致（见 {@code AutoDockService.requireActor} 与
 *       {@code DockingPlate.onEvent(ECHO_DISAPPEARED)}）。</li>
 * </ul>
 * <p>录制侧写入的事件记录的是录制当时的活玩家（恒为 {@code 0}）；只有**回放侧写机关**时
 * 才把归属改写成 {@code echo_<N>}，且该改写不改变事件本身的内容。</p>
 */
public record GameEvent(
        String eventType,
        String sourceId,
        long tick,
        int sourceRound,
        Object payload
) {

    public static final String PLATE_ENTERED = "PLATE_ENTERED";
    public static final String PLATE_EXITED = "PLATE_EXITED";
    public static final String DOOR_UNLOCKED = "DOOR_UNLOCKED";
    public static final String EXIT_TRIGGERED = "EXIT_TRIGGERED";
    public static final String ECHO_DISAPPEARED = "ECHO_DISAPPEARED";
    public static final String TICK_ADVANCED = "TICK_ADVANCED";

    public static GameEvent plateEntered(String sourceId, long tick, int sourceRound) {
        return new GameEvent(PLATE_ENTERED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent plateExited(String sourceId, long tick, int sourceRound) {
        return new GameEvent(PLATE_EXITED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent doorUnlocked(String sourceId, long tick) {
        return new GameEvent(DOOR_UNLOCKED, sourceId, tick, 0, null);
    }

    public static GameEvent exitTriggered(String sourceId, long tick, int sourceRound) {
        return new GameEvent(EXIT_TRIGGERED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent echoDisappeared(String sourceId, long tick, int sourceRound) {
        return new GameEvent(ECHO_DISAPPEARED, sourceId, tick, sourceRound, null);
    }

    public static GameEvent tickAdvanced(long tick) {
        return new GameEvent(TICK_ADVANCED, "system", tick, 0, null);
    }

    public boolean isType(String type) {
        return eventType.equals(type);
    }
}