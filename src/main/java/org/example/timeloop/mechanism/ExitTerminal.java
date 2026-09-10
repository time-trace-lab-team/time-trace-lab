package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Objects;

public class ExitTerminal implements GameObserver {

    private final String id;
    private final Vector2D position;
    private final String associatedDoorId;
    private boolean doorUnlocked = false;
    private boolean triggered = false;

    public ExitTerminal(String id, Vector2D position, String associatedDoorId) {
        this.id = StableIdValidator.requireMechanismId(id, "exit", "exitTerminal.id");
        this.position = Objects.requireNonNull(position);
        this.associatedDoorId = StableIdValidator.requireMechanismId(
                associatedDoorId, "door", "exitTerminal.associatedDoorId");
        EventDispatcher.getInstance().register(GameEvent.DOOR_UNLOCKED, this);
    }

    public String getId() { return id; }
    public Vector2D getPosition() { return position; }
    public boolean isDoorUnlocked() { return doorUnlocked; }
    public boolean isTriggered() { return triggered; }

    public boolean interact(long tick, int sourceRound) {
        if (!doorUnlocked || triggered) return false;
        triggered = true;
        EventDispatcher.getInstance().dispatch(GameEvent.exitTriggered(id, tick, sourceRound));
        return true;
    }

    public void reset() {
        doorUnlocked = false;
        triggered = false;
    }

    public void dispose() {
        EventDispatcher.getInstance().unregisterAll(this);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event.isType(GameEvent.DOOR_UNLOCKED) && associatedDoorId.equals(event.sourceId())) {
            doorUnlocked = true;
        }
    }

    @Override
    public String toString() {
        return String.format("ExitTerminal{id='%s', doorUnlocked=%s, triggered=%s}", id, doorUnlocked, triggered);
    }

    // ========== Snapshot 接口 ==========

    public interface Snapshot {
        /** 快照所属的稳定机制 ID。 */
        default String getMechanismId() { return null; }

        boolean isDoorUnlocked();
        boolean isTriggered();
    }

    /** 不可变的出口终端状态快照。 */
    public record StateSnapshot(String mechanismId,
                                boolean doorUnlocked,
                                boolean triggered) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "exit", "exitTerminal.snapshot.mechanismId");
            validateState(doorUnlocked, triggered);
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public boolean isDoorUnlocked() { return doorUnlocked; }

        @Override
        public boolean isTriggered() { return triggered; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, doorUnlocked, triggered);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "exitTerminal.snapshot");
        String snapshotId = snapshot.getMechanismId();
        if (!id.equals(snapshotId)) {
            throw new IllegalArgumentException(
                    "出口终端快照 ID 不匹配: expected=" + id + ", actual=" + snapshotId);
        }
        boolean restoredDoorUnlocked = snapshot.isDoorUnlocked();
        boolean restoredTriggered = snapshot.isTriggered();
        validateState(restoredDoorUnlocked, restoredTriggered);
        this.doorUnlocked = restoredDoorUnlocked;
        this.triggered = restoredTriggered;
    }

    private static void validateState(boolean doorUnlocked, boolean triggered) {
        if (triggered && !doorUnlocked) {
            throw new IllegalArgumentException("已触发出口终端的门必须已解锁");
        }
    }



}
