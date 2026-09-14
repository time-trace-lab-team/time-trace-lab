package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Objects;

public class DockingPlate implements GameObserver {

    public enum State {
        UNOCCUPIED, OCCUPIED
    }

    private final String id;
    private final Vector2D position;
    private final DockingPlateOccupancyPort occupancy;
    private final GameEventBus bus;
    /** 开关变体（关卡数据 {@code role=switch}）：被踩上后锁存，直到本轮 {@link #reset()}。 */
    private final boolean latching;
    private State state = State.UNOCCUPIED;
    private String occupantId = null;
    private int occupantSourceRound = 0;
    /** 锁存位：单向 {@code false -> true}，只由 {@link #reset()} / {@link #restore} 归零。 */
    private boolean latched = false;

    /** 兼容构造器：占用注册表与事件总线都取全局单例（Phase 2 删除单例后不再保留）。 */
    public DockingPlate(String id, Vector2D position) {
        this(id, position, DockingPlateRegistry.getInstance(), EventDispatcher.getInstance());
    }

    /** 注入占用注册表；事件总线仍取兼容单例。新代码请用四参构造器。 */
    public DockingPlate(String id, Vector2D position, DockingPlateOccupancyPort occupancy) {
        this(id, position, occupancy, EventDispatcher.getInstance());
    }

    /**
     * 完全注入（推荐，BUG-002-LIFECYCLE Phase 1）：占用注册表与事件总线都由关卡装配持有，
     * 同一实例内的板 ID 必须唯一，场景切换/重开时随装配一起丢弃。默认<b>非锁存</b>。
     */
    public DockingPlate(String id,
                        Vector2D position,
                        DockingPlateOccupancyPort occupancy,
                        GameEventBus bus) {
        this(id, position, occupancy, bus, false);
    }

    /**
     * 锁存开关变体（L01-GATE-MERGE-DEV3 §2.2）：关卡数据 {@code role=switch} 时由装配传
     * {@code latching = true}。该板被踩上即置锁存位，<b>离开不释放</b>，保持到本轮
     * {@link #reset()}；残影踩上同样触发（复用 {@code PLATE_ENTERED}，不新增事件类型）。
     *
     * @param latching 是否启用本轮内锁存；{@code false} 时 {@link #isLatched()} 恒为 false
     */
    public DockingPlate(String id,
                        Vector2D position,
                        DockingPlateOccupancyPort occupancy,
                        GameEventBus bus,
                        boolean latching) {
        this.id = StableIdValidator.requireMechanismId(id, "plate", "dockingPlate.id");
        this.position = Objects.requireNonNull(position);
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.latching = latching;
        occupancy.register(this);
        bus.register(GameEvent.PLATE_ENTERED, this);
        bus.register(GameEvent.PLATE_EXITED, this);
        bus.register(GameEvent.ECHO_DISAPPEARED, this);
    }

    public String getId() {
        return id;
    }

    public Vector2D getPosition() {
        return position;
    }

    public State getState() {
        return state;
    }

    public String getOccupantId() {
        return occupantId;
    }

    public int getOccupantSourceRound() {
        return occupantSourceRound;
    }

    /**
     * 门条件视角的判定：真实占用 <b>或</b>（开关变体）已锁存。
     *
     * <p>锁存的语义就是「即使人离开，条件依然成立」，因此必须并入本判定：否则
     * {@code Door} 依赖的 {@link DockingPlateOccupancyPort#isOccupied(String)} 会在玩家离开开关的
     * 瞬间把门重新锁上（{@code Door} 行为按卡 §三 不得修改）。想知道「此刻是否真有人站着」用
     * {@link #getState()}；想知道「开关是否已触发」用 {@link #isLatched()}；锁存<b>不</b>阻止再次
     * 踩上去，重复触发是幂等的。</p>
     */
    public boolean isOccupied() {
        return state == State.OCCUPIED || latched;
    }

    /** 开关变体：本轮是否已触发并锁存（与「此刻是否有人站着」无关）。非开关板恒为 {@code false}。 */
    public boolean isLatched() {
        return latched;
    }

    public boolean tryEnter(String actorId, int sourceRound, long tick) {
        if (state != State.UNOCCUPIED) {
            return false;
        }
        state = State.OCCUPIED;
        occupantId = actorId;
        occupantSourceRound = sourceRound;
        if (latching) {
            // 单向：本轮内只允许 false -> true；重复踩上是幂等 no-op。
            latched = true;
        }
        bus.dispatch(GameEvent.plateEntered(id, tick, sourceRound));
        return true;
    }

    public boolean tryExit(String actorId, int sourceRound, long tick) {
        if (state != State.OCCUPIED || !occupantId.equals(actorId)) {
            return false;
        }
        state = State.UNOCCUPIED;
        occupantId = null;
        occupantSourceRound = 0;
        bus.dispatch(GameEvent.plateExited(id, tick, sourceRound));
        return true;
    }

    public void reset() {
        state = State.UNOCCUPIED;
        occupantId = null;
        occupantSourceRound = 0;
        // 普通轮末 / FULL_RESTART / 场景退出共用本方法：锁存必须一并归零（OFF）。
        latched = false;
    }

    public void dispose() {
        bus.unregisterAll(this);
        occupancy.unregister(id);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event.isType(GameEvent.PLATE_ENTERED) || event.isType(GameEvent.PLATE_EXITED)) {
            // 驻留板自身事件由 tryEnter/tryExit 处理，此处仅保留扩展
        }

        if (event.isType(GameEvent.ECHO_DISAPPEARED)) {
            int sourceRound = event.sourceRound();
            String echoId = "echo_" + sourceRound;
            if (state == State.OCCUPIED && occupantId != null && occupantId.equals(echoId)) {
                tryExit(echoId, sourceRound, event.tick());
                System.out.println("DockingPlate " + id + " 释放残影 E" + sourceRound + " 的占用");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("DockingPlate{id='%s', state=%s, occupant=%s}",
                id, state, occupantId);
    }

    // ========== Snapshot 接口 ==========

    public interface Snapshot {
        /**
         * 快照所属的稳定机制 ID。旧的自定义快照实现若未提供该字段，恢复时会被拒绝。
         */
        default String getMechanismId() { return null; }

        DockingPlate.State getState();
        String getOccupantId();
        int getOccupantSourceRound();

        /**
         * 开关变体的锁存位（L01-GATE-MERGE-DEV3 §2.3）。默认 {@code false}，
         * 使既有自定义快照实现无需改动即可继续编译。
         */
        default boolean isLatched() { return false; }
    }

    /**
     * 不可变的驻留板状态快照。
     *
     * <p>{@code latched} 是 L01-GATE-MERGE 新增的<b>锁存位</b>，与 {@code state} 相互独立：
     * {@code (UNOCCUPIED, latched=true)} 是开关已触发但人已离开的<b>常态</b>组合。</p>
     */
    public record StateSnapshot(String mechanismId,
                                State state,
                                String occupantId,
                                int occupantSourceRound,
                                boolean latched) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "plate", "dockingPlate.snapshot.mechanismId");
            state = Objects.requireNonNull(state, "dockingPlate.snapshot.state");
            validateState(state, occupantId, occupantSourceRound);
        }

        /**
         * 兼容构造器（无锁存位）：保持既有调用方（如 {@code snapshot/MechanismSnapshot}）不改即可编译，
         * 语义等价于 {@code latched = false}。锁存位冻结后应改用五参构造器。
         */
        public StateSnapshot(String mechanismId,
                             State state,
                             String occupantId,
                             int occupantSourceRound) {
            this(mechanismId, state, occupantId, occupantSourceRound, false);
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public State getState() { return state; }

        @Override
        public String getOccupantId() { return occupantId; }

        @Override
        public int getOccupantSourceRound() { return occupantSourceRound; }

        @Override
        public boolean isLatched() { return latched; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, state, occupantId, occupantSourceRound, latched);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "dockingPlate.snapshot");
        String snapshotId = snapshot.getMechanismId();
        if (!id.equals(snapshotId)) {
            throw new IllegalArgumentException(
                    "驻留板快照 ID 不匹配: expected=" + id + ", actual=" + snapshotId);
        }

        State restoredState = Objects.requireNonNull(
                snapshot.getState(), "dockingPlate.snapshot.state");
        String restoredOccupantId = snapshot.getOccupantId();
        int restoredSourceRound = snapshot.getOccupantSourceRound();
        validateState(restoredState, restoredOccupantId, restoredSourceRound);

        // 直接恢复纯状态，不调用 tryEnter/tryExit，因而不会产生重复 gameplay 事件。
        this.state = restoredState;
        this.occupantId = restoredOccupantId;
        this.occupantSourceRound = restoredSourceRound;
        // 锁存位随快照恢复；无锁存位的旧快照（兼容构造器）等价于 false。
        this.latched = snapshot.isLatched();
    }

    private static void validateState(State state, String occupantId, int sourceRound) {
        if (state == State.UNOCCUPIED) {
            if (occupantId != null || sourceRound != 0) {
                throw new IllegalArgumentException("未占用驻留板快照不能带有占用者数据");
            }
            return;
        }
        if (occupantId == null || occupantId.isBlank()) {
            throw new IllegalArgumentException("已占用驻留板快照缺少 occupantId");
        }
        if (sourceRound < 0) {
            throw new IllegalArgumentException("驻留板快照的 sourceRound 不能为负数");
        }
    }












}
