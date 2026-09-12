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
    private State state = State.UNOCCUPIED;
    private String occupantId = null;
    private int occupantSourceRound = 0;

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
     * 同一实例内的板 ID 必须唯一，场景切换/重开时随装配一起丢弃。
     */
    public DockingPlate(String id,
                        Vector2D position,
                        DockingPlateOccupancyPort occupancy,
                        GameEventBus bus) {
        this.id = StableIdValidator.requireMechanismId(id, "plate", "dockingPlate.id");
        this.position = Objects.requireNonNull(position);
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.bus = Objects.requireNonNull(bus, "bus");
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

    public boolean isOccupied() {
        return state == State.OCCUPIED;
    }

    public boolean tryEnter(String actorId, int sourceRound, long tick) {
        if (state != State.UNOCCUPIED) {
            return false;
        }
        state = State.OCCUPIED;
        occupantId = actorId;
        occupantSourceRound = sourceRound;
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
    }

    /** 不可变的驻留板状态快照。 */
    public record StateSnapshot(String mechanismId,
                                State state,
                                String occupantId,
                                int occupantSourceRound) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "plate", "dockingPlate.snapshot.mechanismId");
            state = Objects.requireNonNull(state, "dockingPlate.snapshot.state");
            validateState(state, occupantId, occupantSourceRound);
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public State getState() { return state; }

        @Override
        public String getOccupantId() { return occupantId; }

        @Override
        public int getOccupantSourceRound() { return occupantSourceRound; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, state, occupantId, occupantSourceRound);
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
