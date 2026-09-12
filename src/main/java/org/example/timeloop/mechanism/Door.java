package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class Door implements GameObserver {

    public enum State { LOCKED, UNLOCKED }

    private final String id;
    private final Vector2D position;
    private final Set<String> requiredPlateIds;
    private final DockingPlateOccupancyPort occupancy;
    private final GameEventBus bus;
    private State state = State.LOCKED;

    /** 兼容构造器：占用注册表与事件总线都取全局单例（Phase 2 删除单例后不再保留）。 */
    public Door(String id, Vector2D position, Set<String> requiredPlateIds) {
        this(id, position, requiredPlateIds,
                DockingPlateRegistry.getInstance(), EventDispatcher.getInstance());
    }

    /** 注入占用注册表；事件总线仍取兼容单例。新代码请用五参构造器。 */
    public Door(String id,
                Vector2D position,
                Set<String> requiredPlateIds,
                DockingPlateOccupancyPort occupancy) {
        this(id, position, requiredPlateIds, occupancy, EventDispatcher.getInstance());
    }

    /**
     * 完全注入（推荐，BUG-002-LIFECYCLE Phase 1）：门只通过窄端口查询板占用，
     * 不再直接依赖注册表具体类或全局单例。
     */
    public Door(String id,
                Vector2D position,
                Set<String> requiredPlateIds,
                DockingPlateOccupancyPort occupancy,
                GameEventBus bus) {
        this.id = StableIdValidator.requireMechanismId(id, "door", "door.id");
        this.position = Objects.requireNonNull(position);
        if (requiredPlateIds == null) {
            throw new IllegalArgumentException("door.requiredPlateIds 不能为 null");
        }
        TreeSet<String> sortedPlateIds = new TreeSet<>();
        for (String plateId : requiredPlateIds) {
            sortedPlateIds.add(StableIdValidator.requireMechanismId(
                    plateId, "plate", "door.requiredPlateIds"));
        }
        this.requiredPlateIds = Collections.unmodifiableSet(sortedPlateIds);
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.bus = Objects.requireNonNull(bus, "bus");
        bus.register(GameEvent.PLATE_ENTERED, this);
        bus.register(GameEvent.PLATE_EXITED, this);
    }

    public String getId() { return id; }
    public Vector2D getPosition() { return position; }
    public State getState() { return state; }
    public boolean isUnlocked() { return state == State.UNLOCKED; }

    private boolean checkAllPlatesOccupied() {
        for (String plateId : requiredPlateIds) {
            if (!occupancy.isOccupied(plateId)) return false;
        }
        return true;
    }

    private void updateDoorState(long tick) {
        boolean allOccupied = checkAllPlatesOccupied();
        State newState = allOccupied ? State.UNLOCKED : State.LOCKED;
        if (newState != state) {
            state = newState;
            if (state == State.UNLOCKED) {
                bus.dispatch(GameEvent.doorUnlocked(id, tick));
            }
        }
    }

    public void reset() { state = State.LOCKED; }

    public void dispose() {
        bus.unregisterAll(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event.isType(GameEvent.PLATE_ENTERED) || event.isType(GameEvent.PLATE_EXITED)) {
            if (requiredPlateIds.contains(event.sourceId())) {
                updateDoorState(event.tick());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Door{id='%s', state=%s, required=%s}", id, state, requiredPlateIds);
    }

    // ========== Snapshot 接口 ==========

    public interface Snapshot {
        /** 快照所属的稳定机制 ID。 */
        default String getMechanismId() { return null; }

        Door.State getState();
    }

    /** 不可变的门状态快照。 */
    public record StateSnapshot(String mechanismId, State state) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "door", "door.snapshot.mechanismId");
            state = Objects.requireNonNull(state, "door.snapshot.state");
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public State getState() { return state; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, state);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "door.snapshot");
        String snapshotId = snapshot.getMechanismId();
        if (!id.equals(snapshotId)) {
            throw new IllegalArgumentException(
                    "门快照 ID 不匹配: expected=" + id + ", actual=" + snapshotId);
        }
        this.state = Objects.requireNonNull(snapshot.getState(), "door.snapshot.state");
    }


}
