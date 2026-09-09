package org.example.timeloop.level.model;

public class DoorInfo {

    private final String id;
    private final Vector2D position;
    private final boolean initiallyOpen;

    public DoorInfo(String id, Vector2D position, boolean initiallyOpen) {
        this.id = id;
        this.position = position;
        this.initiallyOpen = initiallyOpen;
    }

    public String getId() {
        return id;
    }

    public Vector2D getPosition() {
        return position;
    }

    public boolean isInitiallyOpen() {
        return initiallyOpen;
    }
}