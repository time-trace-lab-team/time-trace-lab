package org.example.timeloop.snapshot;

import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;

import java.util.HashMap;
import java.util.Map;

public class MechanismSnapshot {

    private final Map<String, DockingPlate.Snapshot> plateSnapshots;
    private final Map<String, Door.Snapshot> doorSnapshots;
    private final Map<String, ExitTerminal.Snapshot> exitSnapshots;

    private MechanismSnapshot(
            Map<String, DockingPlate.Snapshot> plateSnapshots,
            Map<String, Door.Snapshot> doorSnapshots,
            Map<String, ExitTerminal.Snapshot> exitSnapshots
    ) {
        this.plateSnapshots = plateSnapshots;
        this.doorSnapshots = doorSnapshots;
        this.exitSnapshots = exitSnapshots;
    }

    public Map<String, DockingPlate.Snapshot> getPlateSnapshots() {
        return plateSnapshots;
    }

    public Map<String, Door.Snapshot> getDoorSnapshots() {
        return doorSnapshots;
    }

    public Map<String, ExitTerminal.Snapshot> getExitSnapshots() {
        return exitSnapshots;
    }

    public static MechanismSnapshot capture(
            Map<String, DockingPlate> plates,
            Map<String, Door> doors,
            Map<String, ExitTerminal> exits
    ) {
        Map<String, DockingPlate.Snapshot> plateSnaps = new HashMap<>();
        for (Map.Entry<String, DockingPlate> entry : plates.entrySet()) {
            plateSnaps.put(entry.getKey(), entry.getValue().createSnapshot());
        }

        Map<String, Door.Snapshot> doorSnaps = new HashMap<>();
        for (Map.Entry<String, Door> entry : doors.entrySet()) {
            doorSnaps.put(entry.getKey(), entry.getValue().createSnapshot());
        }

        Map<String, ExitTerminal.Snapshot> exitSnaps = new HashMap<>();
        for (Map.Entry<String, ExitTerminal> entry : exits.entrySet()) {
            exitSnaps.put(entry.getKey(), entry.getValue().createSnapshot());
        }

        return new MechanismSnapshot(plateSnaps, doorSnaps, exitSnaps);
    }

    public void restore(
            Map<String, DockingPlate> plates,
            Map<String, Door> doors,
            Map<String, ExitTerminal> exits
    ) {
        for (Map.Entry<String, DockingPlate.Snapshot> entry : plateSnapshots.entrySet()) {
            DockingPlate plate = plates.get(entry.getKey());
            if (plate != null) {
                plate.restore(entry.getValue());
            }
        }

        for (Map.Entry<String, Door.Snapshot> entry : doorSnapshots.entrySet()) {
            Door door = doors.get(entry.getKey());
            if (door != null) {
                door.restore(entry.getValue());
            }
        }

        for (Map.Entry<String, ExitTerminal.Snapshot> entry : exitSnapshots.entrySet()) {
            ExitTerminal exit = exits.get(entry.getKey());
            if (exit != null) {
                exit.restore(entry.getValue());
            }
        }
    }
}