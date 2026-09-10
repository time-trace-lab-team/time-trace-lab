package org.example.timeloop.mechanism;

import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.Objects;

public class DockingPlate implements GameObserver {

    public enum State {
        UNOCCUPIED, OCCUPIED
    }

    private final String id;
    private final Vector2D position;
    private State state = State.UNOCCUPIED;
    private String occupantId = null;
    private int occupantSourceRound = 0;

    public DockingPlate(String id, Vector2D position) {
        this.id = Objects.requireNonNull(id);
        this.position = Objects.requireNonNull(position);
        EventDispatcher.getInstance().register(GameEvent.PLATE_ENTERED, this);
        EventDispatcher.getInstance().register(GameEvent.PLATE_EXITED, this);
        EventDispatcher.getInstance().register(GameEvent.ECHO_DISAPPEARED, this);
        DockingPlateRegistry.getInstance().register(this);
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
        EventDispatcher.getInstance().dispatch(GameEvent.plateEntered(id, tick, sourceRound));
        return true;
    }

    public boolean tryExit(String actorId, int sourceRound, long tick) {
        if (state != State.OCCUPIED || !occupantId.equals(actorId)) {
            return false;
        }
        state = State.UNOCCUPIED;
        occupantId = null;
        occupantSourceRound = 0;
        EventDispatcher.getInstance().dispatch(GameEvent.plateExited(id, tick, sourceRound));
        return true;
    }

    public void reset() {
        state = State.UNOCCUPIED;
        occupantId = null;
        occupantSourceRound = 0;
    }

    public void dispose() {
        EventDispatcher.getInstance().unregisterAll(this);
        DockingPlateRegistry.getInstance().unregister(id);
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
        DockingPlate.State getState();
        String getOccupantId();
        int getOccupantSourceRound();
    }

    public Snapshot createSnapshot() {
        return new Snapshot() {
            @Override
            public State getState() { return state; }

            @Override
            public String getOccupantId() { return occupantId; }

            @Override
            public int getOccupantSourceRound() { return occupantSourceRound; }
        };
    }

    public void restore(Snapshot snapshot) {
        this.state = snapshot.getState();
        this.occupantId = snapshot.getOccupantId();
        this.occupantSourceRound = snapshot.getOccupantSourceRound();
    }












}