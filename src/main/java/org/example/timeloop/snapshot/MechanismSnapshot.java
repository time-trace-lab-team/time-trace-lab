package org.example.timeloop.snapshot;

import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.level.StableIdValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class MechanismSnapshot {

    private final Map<String, DockingPlate.Snapshot> plateSnapshots;
    private final Map<String, Door.Snapshot> doorSnapshots;
    private final Map<String, ExitTerminal.Snapshot> exitSnapshots;

    private MechanismSnapshot(
            Map<String, DockingPlate.Snapshot> plateSnapshots,
            Map<String, Door.Snapshot> doorSnapshots,
            Map<String, ExitTerminal.Snapshot> exitSnapshots
    ) {
        this.plateSnapshots = Map.copyOf(plateSnapshots);
        this.doorSnapshots = Map.copyOf(doorSnapshots);
        this.exitSnapshots = Map.copyOf(exitSnapshots);
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
        Objects.requireNonNull(plates, "plates");
        Objects.requireNonNull(doors, "doors");
        Objects.requireNonNull(exits, "exits");

        Map<String, DockingPlate.Snapshot> plateSnaps = new HashMap<>();
        for (Map.Entry<String, DockingPlate> entry : plates.entrySet()) {
            String mechanismId = requireMatchingMechanismId(
                    entry.getKey(),
                    Objects.requireNonNull(entry.getValue(), "plates value").getId(),
                    "plate",
                    "plates"
            );
            DockingPlate.Snapshot snapshot = Objects.requireNonNull(
                    entry.getValue().createSnapshot(), "plates snapshot");
            requireMatchingMechanismId(
                    mechanismId, snapshot.getMechanismId(), "plate", "plates snapshot");
            plateSnaps.put(mechanismId, new DockingPlate.StateSnapshot(
                    mechanismId,
                    snapshot.getState(),
                    snapshot.getOccupantId(),
                    snapshot.getOccupantSourceRound(),
                    snapshot.isLatched()
            ));
        }

        Map<String, Door.Snapshot> doorSnaps = new HashMap<>();
        for (Map.Entry<String, Door> entry : doors.entrySet()) {
            String mechanismId = requireMatchingMechanismId(
                    entry.getKey(),
                    Objects.requireNonNull(entry.getValue(), "doors value").getId(),
                    "door",
                    "doors"
            );
            Door.Snapshot snapshot = Objects.requireNonNull(
                    entry.getValue().createSnapshot(), "doors snapshot");
            requireMatchingMechanismId(
                    mechanismId, snapshot.getMechanismId(), "door", "doors snapshot");
            doorSnaps.put(mechanismId, new Door.StateSnapshot(mechanismId, snapshot.getState()));
        }

        Map<String, ExitTerminal.Snapshot> exitSnaps = new HashMap<>();
        for (Map.Entry<String, ExitTerminal> entry : exits.entrySet()) {
            String mechanismId = requireMatchingMechanismId(
                    entry.getKey(),
                    Objects.requireNonNull(entry.getValue(), "exits value").getId(),
                    "exit",
                    "exits"
            );
            ExitTerminal.Snapshot snapshot = Objects.requireNonNull(
                    entry.getValue().createSnapshot(), "exits snapshot");
            requireMatchingMechanismId(
                    mechanismId, snapshot.getMechanismId(), "exit", "exits snapshot");
            exitSnaps.put(mechanismId, new ExitTerminal.StateSnapshot(
                    mechanismId,
                    snapshot.isDoorUnlocked(),
                    snapshot.isTriggered()
            ));
        }

        return new MechanismSnapshot(plateSnaps, doorSnaps, exitSnaps);
    }

    public void restore(
            Map<String, DockingPlate> plates,
            Map<String, Door> doors,
            Map<String, ExitTerminal> exits
    ) {
        validateSnapshotCollection(
                plateSnapshots, "plate", "plate snapshots", DockingPlate.Snapshot::getMechanismId);
        validateSnapshotCollection(
                doorSnapshots, "door", "door snapshots", Door.Snapshot::getMechanismId);
        validateSnapshotCollection(
                exitSnapshots, "exit", "exit snapshots", ExitTerminal.Snapshot::getMechanismId);

        validateMechanismCollection(
                plates, plateSnapshots, "plate", "plates", DockingPlate::getId);
        validateMechanismCollection(
                doors, doorSnapshots, "door", "doors", Door::getId);
        validateMechanismCollection(
                exits, exitSnapshots, "exit", "exits", ExitTerminal::getId);

        for (Map.Entry<String, DockingPlate.Snapshot> entry : plateSnapshots.entrySet()) {
            plates.get(entry.getKey()).restore(entry.getValue());
        }

        for (Map.Entry<String, Door.Snapshot> entry : doorSnapshots.entrySet()) {
            doors.get(entry.getKey()).restore(entry.getValue());
        }

        for (Map.Entry<String, ExitTerminal.Snapshot> entry : exitSnapshots.entrySet()) {
            exits.get(entry.getKey()).restore(entry.getValue());
        }
    }

    private static String requireMatchingMechanismId(
            String key,
            String mechanismId,
            String mechanismKind,
            String collectionName
    ) {
        String stableKey = requireStableMechanismId(key, mechanismKind, collectionName + " key");
        String stableMechanismId = requireStableMechanismId(
                mechanismId, mechanismKind, collectionName + " mechanism ID");
        if (!stableKey.equals(stableMechanismId)) {
            throw new IllegalArgumentException(
                    collectionName + " map key and mechanism ID do not match: key="
                            + stableKey + ", mechanismId=" + stableMechanismId);
        }
        return stableKey;
    }

    private static String requireStableMechanismId(
            String mechanismId,
            String mechanismKind,
            String fieldName
    ) {
        return StableIdValidator.requireMechanismId(
                Objects.requireNonNull(mechanismId, fieldName), mechanismKind, fieldName);
    }

    private static <S> void validateSnapshotCollection(
            Map<String, S> snapshots,
            String mechanismKind,
            String collectionName,
            Function<S, String> snapshotIdReader
    ) {
        Objects.requireNonNull(snapshots, collectionName);
        for (Map.Entry<String, S> entry : snapshots.entrySet()) {
            String key = requireStableMechanismId(
                    entry.getKey(), mechanismKind, collectionName + " key");
            S snapshot = Objects.requireNonNull(entry.getValue(), collectionName + " value");
            String snapshotId = requireStableMechanismId(
                    snapshotIdReader.apply(snapshot), mechanismKind, collectionName + " mechanism ID");
            if (!key.equals(snapshotId)) {
                throw new IllegalArgumentException(
                        collectionName + " map key and snapshot ID do not match: key="
                                + key + ", snapshotId=" + snapshotId);
            }
        }
    }

    private static <T> void validateMechanismCollection(
            Map<String, T> mechanisms,
            Map<String, ?> snapshots,
            String mechanismKind,
            String collectionName,
            Function<T, String> mechanismIdReader
    ) {
        Objects.requireNonNull(mechanisms, collectionName);
        for (Map.Entry<String, T> entry : mechanisms.entrySet()) {
            String key = requireStableMechanismId(
                    entry.getKey(), mechanismKind, collectionName + " key");
            T mechanism = Objects.requireNonNull(entry.getValue(), collectionName + " value");
            String mechanismId = requireStableMechanismId(
                    mechanismIdReader.apply(mechanism), mechanismKind, collectionName + " mechanism ID");
            if (!key.equals(mechanismId)) {
                throw new IllegalArgumentException(
                        collectionName + " map key and mechanism ID do not match: key="
                                + key + ", mechanismId=" + mechanismId);
            }
        }
        if (!snapshots.keySet().equals(mechanisms.keySet())) {
            throw new IllegalArgumentException(
                    collectionName + " IDs do not match the snapshot: expected="
                            + snapshots.keySet() + ", actual=" + mechanisms.keySet());
        }
    }
}
