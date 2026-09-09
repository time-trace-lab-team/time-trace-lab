package org.example.timeloop.mechanism;

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
        this.id = Objects.requireNonNull(id);
        this.position = Objects.requireNonNull(position);
        this.associatedDoorId = Objects.requireNonNull(associatedDoorId);
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
}