package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.autodock.AutoDockOccupancyPort;
import org.example.timeloop.mechanism.autodock.AutoDockReadPort;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockResult;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.replay.TimelineEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * C3 停放/输入策略（开发一）。
 *
 * <p>职责边界：本类<b>不拥有位置、不推进运动</b>，只在每个逻辑刻依据当前世界坐标与
 * {@link InputIntent} 给出“冻结 / 巡行 / 本刻离开方向”，并发布 autoDock 边沿事件。
 * 运动由调用方（C2 {@code PatrolController} 或集成层）执行，因此本类不依赖
 * {@code core.path} 与 {@code level.model} 的桥接。</p>
 *
 * <p>关键规则（与 README §三、开发一指南 §3.4 一致）：</p>
 * <ul>
 *   <li>进入瞬间清空旧方向队列由调用方执行；本类只发出 {@code DOCK_ENTERED} 与冻结决策。</li>
 *   <li>只有<b>新发生的方向按下边沿</b>（{@code InputIntent.directionEdges}）才可能离开；
 *       进入前一直按住的旧键不会出现在边沿里，天然被拦截。</li>
 *   <li>离开方向必须是该 dock 的合法出口，否则保持驻留。</li>
 *   <li><b>已知偏差（待 PM 裁决）</b>：{@code AutoDockOccupancyPort.tryLeave} 要求位置已在区域外，
 *       因此 {@code DOCK_LEFT} 记在<b>出界刻</b>而非 README 所述“离开即释放”。本类按 API 实现，
 *       代码注释与交接文档均已标注。</li>
 * </ul>
 */
public final class C3DockController {

    /** E 交互缓冲长度（README §三 要求 6–10 tick）。 */
    public static final int INTERACT_BUFFER_TICKS = 8;

    /** DOCK_LEFT 的唯一正常原因（= {@link DockEventReason#NEW_DIRECTION}）。 */
    public static final String REASON_NEW_DIRECTION = DockEventReason.NEW_DIRECTION.name();

    private final AutoDockReadPort readPort;
    private final AutoDockOccupancyPort occupancyPort;
    private final String actorId;
    private final int sourceRound;

    private String dockedMechanismId;
    private Set<LogicalKey> heldAtEntry = Set.of();
    private Direction departureDirection;
    private int interactBufferTicks;

    public C3DockController(AutoDockReadPort readPort,
                            AutoDockOccupancyPort occupancyPort,
                            String actorId,
                            int sourceRound) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.occupancyPort = Objects.requireNonNull(occupancyPort, "occupancyPort");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.sourceRound = sourceRound;
        if (sourceRound < 0) {
            throw new IllegalArgumentException("sourceRound 不能为负数");
        }
    }

    public boolean isDocked() {
        return dockedMechanismId != null;
    }

    public Optional<String> dockedMechanismId() {
        return Optional.ofNullable(dockedMechanismId);
    }

    public boolean interactBuffered() {
        return interactBufferTicks > 0;
    }

    /** 进入驻留瞬间正在按住的方向键集合（只读，供诊断与回放解释）。 */
    public Set<LogicalKey> heldAtEntry() {
        return heldAtEntry;
    }

    /**
     * 推进一个逻辑刻的策略判定。
     *
     * @param tick     当前逻辑刻
     * @param input    本刻输入意图
     * @param position 本刻开始时的世界坐标
     */
    public C3DockDecision step(long tick, InputIntent input, Vector2D position) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(position, "position");
        if (tick < 0) {
            throw new IllegalArgumentException("tick 不能为负数");
        }

        updateInteractBuffer(input);
        List<TimelineEvent> events = new ArrayList<>();

        if (dockedMechanismId == null) {
            return cruiseStep(tick, input, position, events);
        }
        return dockedStep(tick, input, position, events);
    }

    private C3DockDecision cruiseStep(long tick, InputIntent input, Vector2D position, List<TimelineEvent> events) {
        Optional<AutoDockView> candidate = readPort.findNearest(position, 0.0);
        if (candidate.isEmpty()) {
            return cruise(events);
        }

        AutoDockView view = candidate.get();
        AutoDockResult result = occupancyPort.tryEnter(view.mechanismId(), actorId, sourceRound, tick, position);
        if (result.status() != AutoDockResult.Status.ENTERED) {
            // 已被其他 actor 占用或同 tick 防重入：本刻不驻留，继续按调用方运动。
            return cruise(events);
        }

        dockedMechanismId = view.mechanismId();
        heldAtEntry = Set.copyOf(input.held());
        departureDirection = null;
        events.add(new TimelineEvent(tick, actorId, sourceRound, view.mechanismId(),
                TimelineEvent.EventType.DOCK_ENTERED, null, null));
        return new C3DockDecision(C3DockDecision.Status.FREEZE, Optional.empty(), events, interactBuffered());
    }

    private C3DockDecision dockedStep(long tick, InputIntent input, Vector2D position, List<TimelineEvent> events) {
        if (departureDirection == null) {
            Optional<Direction> edge = input.lastDirectionEdge();
            if (edge.isEmpty()) {
                return freeze(events);
            }
            Direction requested = edge.get();
            if (!isLegalExit(dockedMechanismId, requested)) {
                return freeze(events);
            }
            // 离开边沿成立：本刻起按所选方向移动；占用在出界刻释放（见类注释的已知偏差）。
            departureDirection = requested;
            return new C3DockDecision(C3DockDecision.Status.CRUISE, Optional.of(requested), events, interactBuffered());
        }

        AutoDockResult leave = occupancyPort.tryLeave(
                dockedMechanismId, actorId, sourceRound, tick, toDir(departureDirection), position);
        if (leave.status() == AutoDockResult.Status.LEFT) {
            events.add(new TimelineEvent(tick, actorId, sourceRound, dockedMechanismId,
                    TimelineEvent.EventType.DOCK_LEFT, departureDirection, REASON_NEW_DIRECTION));
            clearDockState();
            return cruise(events);
        }
        // 仍在区域内：继续按离开方向移动，占用保留到出界刻。
        return new C3DockDecision(C3DockDecision.Status.CRUISE, Optional.of(departureDirection), events, interactBuffered());
    }

    /**
     * 释放本 actor 在所有 dock 上的占用（残影淘汰 / 整局重开 / 退出关卡）。
     * 仅对确实被本 actor 占用的 dock 产生 {@code OCCUPANCY_RELEASED}。
     */
    public List<TimelineEvent> releaseOccupancy(DockEventReason reason, long tick) {
        Objects.requireNonNull(reason, "reason");
        List<TimelineEvent> events = new ArrayList<>();
        for (AutoDockView view : readPort.snapshot()) {
            if (isOwnedByThisActor(view)) {
                events.add(new TimelineEvent(tick, actorId, sourceRound, view.mechanismId(),
                        TimelineEvent.EventType.OCCUPANCY_RELEASED, null, reason.name()));
            }
        }
        occupancyPort.releaseActor(actorId, sourceRound, tick);
        clearDockState();
        return List.copyOf(events);
    }

    /** 轮末 / 整局重开 / 场景退出的幂等清理，并清空本策略的本地状态。 */
    public List<TimelineEvent> reset(AutoDockResetReason reason, long tick) {
        Objects.requireNonNull(reason, "reason");
        List<TimelineEvent> events = new ArrayList<>();
        for (AutoDockView view : readPort.snapshot()) {
            if (isOwnedByThisActor(view)) {
                events.add(new TimelineEvent(tick, actorId, sourceRound, view.mechanismId(),
                        TimelineEvent.EventType.OCCUPANCY_RELEASED, null, toEventReason(reason).name()));
            }
        }
        occupancyPort.reset(reason, tick);
        clearDockState();
        interactBufferTicks = 0;
        return List.copyOf(events);
    }

    private boolean isOwnedByThisActor(AutoDockView view) {
        return view.occupancy().occupied()
                && actorId.equals(view.occupancy().occupantId())
                && sourceRound == view.occupancy().occupantSourceRound();
    }

    private boolean isLegalExit(String mechanismId, Direction direction) {
        return readPort.findById(mechanismId)
                .map(view -> view.legalExitDirections().contains(toDir(direction)))
                .orElse(false);
    }

    private void updateInteractBuffer(InputIntent input) {
        if (input.isPressed(LogicalKey.INTERACT)) {
            interactBufferTicks = INTERACT_BUFFER_TICKS;
        } else if (interactBufferTicks > 0) {
            interactBufferTicks--;
        }
    }

    private void clearDockState() {
        dockedMechanismId = null;
        departureDirection = null;
        heldAtEntry = Set.of();
    }

    private C3DockDecision cruise(List<TimelineEvent> events) {
        return new C3DockDecision(C3DockDecision.Status.CRUISE, Optional.empty(), events, interactBuffered());
    }

    private C3DockDecision freeze(List<TimelineEvent> events) {
        return new C3DockDecision(C3DockDecision.Status.FREEZE, Optional.empty(), events, interactBuffered());
    }

    /** {@code core.Direction} 与 {@code level.model.PathNode.Dir} 同名集合，按名称映射。 */
    private static PathNode.Dir toDir(Direction direction) {
        return PathNode.Dir.valueOf(direction.name());
    }

    /** 开发三清理原因 → 冻结事件原因（同名映射）。 */
    private static DockEventReason toEventReason(AutoDockResetReason reason) {
        return switch (reason) {
            case ROUND_END -> DockEventReason.ROUND_END;
            case FULL_RESTART -> DockEventReason.FULL_RESTART;
            case SCENE_EXIT -> DockEventReason.SCENE_EXIT;
        };
    }
}
