package org.example.timeloop.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 当前轮的定长录制缓冲（R2，开发 2 主责）。
 *
 * <p>一次有效录制<b>恰好包含 {@code durationTicks} 个连续逻辑刻</b>，
 * 且每个 {@link PlayerFrame#tick()} 必须等于其在记录中的索引：
 * 先完整处理并记录 tick {@code 0}，处理并记录完 {@code D-1} 后由上层发起轮次事务，
 * 因此不存在 tick {@code D}（C1 决策记录 §3）。</p>
 *
 * <p>生命周期为单向：{@code record}/{@code recordEvent} 逐刻收集 → 满 {@code D} 帧后
 * {@link #seal()} 一次封装（同时按 {@link TimelineEvent#STABLE_ORDER} 排序事件）→
 * 之后拒绝任何写入。封装前不允许读取为"有效记录"，
 * 通关、退出、重开产生的<b>未满缓冲必须由调用方直接丢弃</b>，不得封装。</p>
 *
 * <p>本类只负责帧的定长收集、事件收集与封装，不实现残影回放（R3）、
 * 寿命（R4）或快照（R5）。</p>
 */
public final class TimelineRecording {

    private final int durationTicks;
    private final int sourceRound;
    private final PlayerFrame[] frames;
    private final List<TimelineEvent> events;

    private int written;
    private boolean sealed;

    /**
     * 创建一个空缓冲。
     *
     * @param durationTicks 本轮固定时长（tick），范围 [1, ∞)
     * @param sourceRound   来源轮次，范围 [1, maxRounds]
     */
    public TimelineRecording(int durationTicks, int sourceRound) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks 必须 >= 1，实际 " + durationTicks);
        }
        if (sourceRound < 1) {
            throw new IllegalArgumentException("sourceRound 必须 >= 1，实际 " + sourceRound);
        }
        this.durationTicks = durationTicks;
        this.sourceRound = sourceRound;
        this.frames = new PlayerFrame[durationTicks];
        this.events = new ArrayList<>();
        this.written = 0;
        this.sealed = false;
    }

    /** 本轮固定时长（tick）。 */
    public int durationTicks() {
        return durationTicks;
    }

    /** 本条记录的来源轮次。 */
    public int sourceRound() {
        return sourceRound;
    }

    /** 已写入的帧数。 */
    public int size() {
        return written;
    }

    /** 是否已写满 {@code durationTicks} 帧（尚未封装也返回 true）。 */
    public boolean isComplete() {
        return written == durationTicks;
    }

    /** 是否已封装。 */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * 写入下一帧。帧的 {@code tick} 必须等于当前已写入帧数，
     * 因此缺 tick、重复 tick 与乱序都会明确失败，而不是静默错位。
     *
     * @param frame 待写入的不可变帧
     * @throws IllegalStateException    已封装后继续写入
     * @throws IllegalArgumentException {@code tick} 与期望索引不一致，或缓冲已满
     */
    public void record(PlayerFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (sealed) {
            throw new IllegalStateException(
                    "记录已封装，拒绝后续写入：sourceRound=" + sourceRound);
        }
        if (written >= durationTicks) {
            throw new IllegalStateException(
                    "缓冲已满（" + durationTicks + " 帧），应先封装：sourceRound=" + sourceRound);
        }
        long expectedTick = written;
        if (frame.tick() != expectedTick) {
            throw new IllegalArgumentException(
                    "帧 tick 必须等于下一个索引，期望 " + expectedTick + "，实际 " + frame.tick()
                            + "（sourceRound=" + sourceRound + "）");
        }
        frames[written] = frame;
        written++;
    }

    /**
     * 收集一条离散事件。
     * 事件可以在同刻的帧写入之前、之后、或任意时刻收集；
     * 不在收集时排序，只在 {@link #seal()} 时统一按稳定顺序排序一次。
     *
     * @param event 待收集的事件
     * @throws IllegalStateException 已封装后继续收集
     */
    public void recordEvent(TimelineEvent event) {
        Objects.requireNonNull(event, "event");
        if (sealed) {
            throw new IllegalStateException(
                    "记录已封装，拒绝后续事件：sourceRound=" + sourceRound);
        }
        events.add(event);
    }

    /**
     * 封装本条记录。仅当恰好写满 {@code durationTicks} 帧时允许，且只能成功一次。
     * 封装时按 {@link TimelineEvent#STABLE_ORDER} 排序事件列表。
     *
     * @throws IllegalStateException 未写满，或已经封装过
     */
    public void seal() {
        if (sealed) {
            throw new IllegalStateException(
                    "记录已封装，不能重复封装：sourceRound=" + sourceRound);
        }
        if (!isComplete()) {
            throw new IllegalStateException(
                    "只有满长记录才能封装，当前 " + written + "/" + durationTicks
                            + " 帧（sourceRound=" + sourceRound + "）；未满缓冲应直接丢弃");
        }
        events.sort(TimelineEvent.STABLE_ORDER);
        sealed = true;
    }

    /**
     * 按索引读取帧，即残影回放时"以共享 {@code roundTick} 直接索引"的方式（R3 使用）。
     *
     * @param tick 逻辑刻索引，范围 [0, 已写入帧数)
     * @return 该刻的不可变帧
     */
    public PlayerFrame frameAt(int tick) {
        if (tick < 0 || tick >= written) {
            throw new IndexOutOfBoundsException(
                    "tick 越界：" + tick + "，已写入 " + written + " 帧（sourceRound=" + sourceRound + "）");
        }
        return frames[tick];
    }

    /**
     * 已写入帧的只读副本。返回的列表不可修改，也不暴露内部数组。
     *
     * @return 长度等于 {@link #size()} 的不可修改列表
     */
    public List<PlayerFrame> frames() {
        List<PlayerFrame> snapshot = new ArrayList<>(written);
        Collections.addAll(snapshot, java.util.Arrays.copyOf(frames, written));
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * 已收集的事件（封装后按 {@link TimelineEvent#STABLE_ORDER} 排序）。
     * 未封装前返回当前已收集但未排序的事件视图；封装后返回排序后的事件。
     * 返回的列表不可修改。
     *
     * @return 不可修改的事件列表
     */
    public List<TimelineEvent> events() {
        return Collections.unmodifiableList(events);
    }

    /**
     * 返回指定 tick 的所有事件（按稳定顺序）。
     * 未封装前扫描未排序的事件列表，结果仍按稳定顺序返回，避免调用方看到不稳定顺序。
     *
     * @param tick 逻辑刻
     * @return 不可修改的事件列表（该 tick 的所有事件）
     */
    public List<TimelineEvent> eventsAt(long tick) {
        List<TimelineEvent> result = new ArrayList<>();
        for (TimelineEvent e : events) {
            if (e.tick() == tick) {
                result.add(e);
            }
        }
        result.sort(TimelineEvent.STABLE_ORDER);
        return Collections.unmodifiableList(result);
    }
}