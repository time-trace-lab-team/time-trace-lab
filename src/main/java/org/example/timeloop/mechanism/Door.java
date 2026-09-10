package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
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
    private State state = State.LOCKED;

    public Door(String id, Vector2D position, Set<String> requiredPlateIds) {
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
        EventDispatcher.getInstance().register(GameEvent.PLATE_ENTERED, this);
        EventDispatcher.getInstance().register(GameEvent.PLATE_EXITED, this);
    }

    public String getId() { return id; }
    public Vector2D getPosition() { return position; }
    public State getState() { return state; }
    public boolean isUnlocked() { return state == State.UNLOCKED; }

    private boolean checkAllPlatesOccupied() {
        DockingPlateRegistry registry = DockingPlateRegistry.getInstance();
        for (String plateId : requiredPlateIds) {
            if (!registry.isOccupied(plateId)) return false;
        }
        return true;
    }

    private void updateDoorState(long tick) {
        boolean allOccupied = checkAllPlatesOccupied();
        State newState = allOccupied ? State.UNLOCKED : State.LOCKED;
        if (newState != state) {
            state = newState;
            if (state == State.UNLOCKED) {
                EventDispatcher.getInstance().dispatch(GameEvent.doorUnlocked(id, tick));
            }
        }
    }

    public void reset() { state = State.LOCKED; }

    public void dispose() {
        EventDispatcher.getInstance().unregisterAll(this);
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
        Door.State getState();
    }

    public Snapshot createSnapshot() {
        return () -> state;
    }

    public void restore(Snapshot snapshot) {
        this.state = snapshot.getState();
    }


}
