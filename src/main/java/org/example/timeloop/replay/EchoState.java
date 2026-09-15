package org.example.timeloop.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * <p>离散事件：残影可以触发 README 允许的机关（驻留板/门/中继/共振），
 * 但不能操作只允许当前玩家使用的核心/出口。{@link TimelineEvent.EventType#EXIT_REQUESTED}
 * 是玩家专属事件，会被 {@link #filterEchoAllowed} 排除。</p>
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
     * <p><b>取帧语义（TASK-DEV2-L03-ECHO-ACTOR-VIEW 冻结）</b>：本方法是残影
     * <b>唯一</b>的取帧入口，只服务「残影当前该画什么姿态」，且与寿命 / 消散状态
     * <b>无关</b>：</p>
     * <ul>
     *   <li>合法 {@code roundTick}（{@code [0, durationTicks)}）<b>恒返回该刻录制帧</b>
     *       —— 不因处于「最后有效轮」或已被淘汰而改变，也不返回 {@code null}；</li>
     *   <li>越界 {@code roundTick}（{@code < 0} 或 {@code >= durationTicks}）<b>一律拒绝</b>
     *       （抛 {@link IndexOutOfBoundsException}），<b>绝不返回最后一帧补齐</b>；</li>
     *   <li><b>消散轮 / GONE 语义</b>：{@code EchoState} 自身<b>不做寿命判断、不感知「消散」</b>。
     *       被淘汰（GONE）的残影不再出现在 {@link EchoQueue#activeEchoes(int)}，
     *       由调用方据此停止取帧；只要调用方仍在合法区间内取帧，本方法照常返回该刻录制帧。</li>
     * </ul>
     *
     * <p>回放<b>不重算射线</b>：本方法只做只读索引 —— 不写减速、不刷新
     * {@code (rayId, activeCycle)} 去重、不改变任何位置或状态。</p>
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

    /**
     * 本刻残影触发的事件（已过滤到只允许残影触发的类型）。
     * 顺序由 {@link TimelineEvent#STABLE_ORDER} 保证稳定。
     *
     * <p><b>actor 归属契约（X-MOVE-COLLAPSE-01 · E-3）</b>：本方法返回的事件的
     * {@code actorId} 仍是<b>录制时的原值</b>（例如活玩家录制时写入的 {@code "player"}）；
     * replay 层<b>不负责改写</b>，也不把 {@code "player"} 当作残影身份。
     * 调用方（app）把事件写入机关前，需把 actor 改写为 {@code "echo_" + sourceRound}，
     * 以满足机关侧「{@code echo_<N>} 且 N == sourceRound」的占用约定；
     * 否则残影消失后其占用的机关不会被释放（占用永久泄漏）。</p>
     *
     * <p><b>残影消失契约（X-MOVE-COLLAPSE-01 · E-4）</b>：残影消失（寿命到期被淘汰）
     * 的机关释放语义由调用方（app）负责派发，replay 层<b>不持有机关状态</b>、
     * 也不派发任何「消失」事件——被淘汰的残影由 {@link EchoQueue#addOnRoundEnd}
     * 返回给调用方。</p>
     *
     * @param roundTick 共享逻辑刻
     * @return 不可修改的事件列表（该 tick 的残影允许事件）
     */
    public List<TimelineEvent> eventsAt(long roundTick) {
        List<TimelineEvent> raw = recording.eventsAt(roundTick);
        return filterEchoAllowed(raw);
    }

    /**
     * 整条时间线上残影触发的事件（已过滤）。
     * 顺序由 {@link TimelineEvent#STABLE_ORDER} 保证稳定。
     *
     * @return 不可修改的事件列表
     */
    public List<TimelineEvent> allEvents() {
        List<TimelineEvent> raw = recording.events();
        return filterEchoAllowed(raw);
    }

    /**
     * 过滤掉残影不允许触发的事件类型。
     *
     * <p>残影允许：{@link TimelineEvent.EventType#DOCK_LEFT}、
     * {@link TimelineEvent.EventType#OCCUPANCY_RELEASED}、
     * {@link TimelineEvent.EventType#DOCK_ENTERED}、
     * {@link TimelineEvent.EventType#MECHANISM_STATE_CHANGED}。</p>
     *
     * <p>残影禁止：{@link TimelineEvent.EventType#EXIT_REQUESTED}
     * —— 出口请求是当前玩家专属，残影不能操作核心或出口完成结算（README「时间残影」）。</p>
     */
    private static List<TimelineEvent> filterEchoAllowed(List<TimelineEvent> events) {
        List<TimelineEvent> allowed = new ArrayList<>(events.size());
        for (TimelineEvent e : events) {
            if (e.eventType() != TimelineEvent.EventType.EXIT_REQUESTED) {
                allowed.add(e);
            }
        }
        return Collections.unmodifiableList(allowed);
    }
}