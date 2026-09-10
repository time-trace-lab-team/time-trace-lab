package org.example.timeloop.replay;

import java.util.Objects;

/**
 * 单残影（R3，开发 2 主责）：一条已封装时间线在后续轮次中的只读播放体。
 *
 * <p>残影是<b>播放结果，不是第二个玩家</b>（技术指南 §3.3）。本类只按共享
 * {@code roundTick} 直接索引记录中的帧：</p>
 * <ul>
 *   <li>不重新执行输入、路径选择、碰撞、驻留、射线或机关逻辑；</li>
 *   <li>不被射线二次减速，不阻挡任何 actor；</li>
 *   <li>不持有独立播放时钟 —— 播放位置完全由调用方传入的 {@code roundTick} 决定，
 *       同一 {@code roundTick} 永远返回同一帧（不同渲染帧率下结果一致）；</li>
 *   <li>构造边界即校验：只接受<b>已封装的满长记录</b>，
 *       时间线长度不合法时明确失败，绝不用最后一帧补齐。</li>
 * </ul>
 *
 * <p>离散事件（残影触发机关）与整轮寿命（age / remainingRounds）分别依赖
 * 开发 3 的事件类型冻结与 R4，本类暂不包含。</p>
 */
public final class EchoState {

    private final TimelineRecording recording;

    private EchoState(TimelineRecording recording) {
        this.recording = recording;
    }

    /**
     * 由一条已封装的满长记录创建残影。
     *
     * @param recording 必须已 {@link TimelineRecording#seal()}
     * @return 只读残影播放体
     * @throws IllegalStateException 记录未封装或未满长
     */
    public static EchoState of(TimelineRecording recording) {
        Objects.requireNonNull(recording, "recording");
        if (!recording.isSealed()) {
            throw new IllegalStateException(
                    "残影只能播放已封装的满长记录：sourceRound=" + recording.sourceRound()
                            + "，当前 " + recording.size() + "/" + recording.durationTicks() + " 帧");
        }
        return new EchoState(recording);
    }

    /** 来源轮次（该记录是在第几轮产生的）。 */
    public int sourceRound() {
        return recording.sourceRound();
    }

    /** 本条时间线的固定长度（tick）。 */
    public int durationTicks() {
        return recording.durationTicks();
    }

    /**
     * 以共享 {@code roundTick} 直接索引本刻应呈现的帧。
     * 这是残影唯一的"移动"方式：不维护任何内部进度。
     *
     * @param roundTick 共享逻辑刻，范围 [0, durationTicks)
     * @return 该刻的不可变帧
     * @throws IndexOutOfBoundsException {@code roundTick} 越界
     */
    public PlayerFrame frameAt(long roundTick) {
        if (roundTick < 0 || roundTick >= recording.durationTicks()) {
            throw new IndexOutOfBoundsException(
                    "roundTick 越界：" + roundTick + "，时间线长度 " + recording.durationTicks()
                            + "（sourceRound=" + recording.sourceRound() + "）");
        }
        return recording.frameAt((int) roundTick);
    }
}
