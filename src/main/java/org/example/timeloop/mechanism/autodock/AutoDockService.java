package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 开发三的 autoDock 只读查询和占用端口。
 *
 * <p>移动系统只提交 tick 末位置和离开方向；本类不推进玩家、不处理输入，也不写入 replay。</p>
 */
public final class AutoDockService implements AutoDockReadPort,
        AutoDockOccupancyPort, AutoDockSnapshotPort {

    private final NavigableMap<String, DockState> docksById;

    public AutoDockService(LevelData levelData) {
        Objects.requireNonNull(levelData, "levelData");
        this.docksById = buildDocks(levelData);
        rejectOverlappingRegions(docksById);
    }

    @Override
    public synchronized Optional<AutoDockView> findById(String mechanismId) {
        DockState state = docksById.get(mechanismId);
        return state == null ? Optional.empty() : Optional.of(viewOf(state));
    }

    @Override
    public synchronized Optional<AutoDockView> findNearest(Vector2D worldPosition, double maxDistance) {
        requirePosition(worldPosition);
        requireFinite(maxDistance, "maxDistance");
        if (maxDistance < 0.0) {
            throw new IllegalArgumentException("maxDistance 不能为负数");
        }

        DockState nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (DockState candidate : docksById.values()) {
            double distance = Math.sqrt(candidate.definition.region().distanceSquared(worldPosition));
            if (distance > maxDistance + candidate.definition.region().epsilon()) {
                continue;
            }
            if (nearest == null || isCloser(candidate, distance, nearest, nearestDistance)) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(viewOf(nearest));
    }

    @Override
    public synchronized List<AutoDockView> snapshot() {
        List<AutoDockView> views = new ArrayList<>(docksById.size());
        for (DockState state : docksById.values()) {
            views.add(viewOf(state));
        }
        return Collections.unmodifiableList(views);
    }

    @Override
    public synchronized AutoDockStateSnapshot createSnapshot() {
        List<AutoDockStateSnapshot.DockSnapshot> snapshots = new ArrayList<>(docksById.size());
        for (DockState state : docksById.values()) {
            snapshots.add(new AutoDockStateSnapshot.DockSnapshot(
                    state.definition.mechanismId(),
                    occupancyOf(state),
                    state.reentryBlockedAtTick));
        }
        return new AutoDockStateSnapshot(snapshots);
    }

    @Override
    public synchronized void restore(AutoDockStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "autoDock.snapshot");

        Map<String, AutoDockStateSnapshot.DockSnapshot> snapshotsById = new HashMap<>();
        Set<String> snapshotIds = new java.util.HashSet<>();
        for (AutoDockStateSnapshot.DockSnapshot dockSnapshot : snapshot.docks()) {
            String mechanismId = dockSnapshot.mechanismId();
            if (!snapshotIds.add(mechanismId)) {
                throw new IllegalArgumentException(
                        "autoDock 快照包含重复机制 ID: " + mechanismId);
            }
            if (!docksById.containsKey(mechanismId)) {
                throw new IllegalArgumentException(
                        "autoDock 快照包含未知机制 ID: " + mechanismId);
            }
            snapshotsById.put(mechanismId, dockSnapshot);
            validateOccupancySnapshot(dockSnapshot.occupancy());
        }
        if (!snapshotIds.equals(docksById.keySet())) {
            throw new IllegalArgumentException("autoDock 快照与当前关卡机制集合不匹配");
        }

        // 所有 ID、占用者和 tick 先完成校验，再修改任何内部状态，避免半恢复。
        for (DockState state : docksById.values()) {
            AutoDockStateSnapshot.DockSnapshot dockSnapshot =
                    snapshotsById.get(state.definition.mechanismId());
            DockOccupancyView occupancy = dockSnapshot.occupancy();
            if (occupancy.occupied()) {
                state.occupantId = occupancy.occupantId();
                state.occupantSourceRound = occupancy.occupantSourceRound();
                state.occupiedAtTick = occupancy.occupiedAtTick();
            } else {
                clearOccupancy(state);
            }
            state.reentryBlockedAtTick = dockSnapshot.reentryBlockedAtTick();
        }
    }

    @Override
    public synchronized AutoDockResult tryEnter(String mechanismId,
                                                 String actorId,
                                                 int sourceRound,
                                                 long tick,
                                                 Vector2D worldPosition) {
        requireActor(actorId, sourceRound);
        requireTick(tick);
        requirePosition(worldPosition);

        DockState state = docksById.get(mechanismId);
        if (state == null) {
            return new AutoDockResult(AutoDockResult.Status.UNKNOWN_DOCK, tick, null);
        }
        if (!state.definition.region().contains(worldPosition)) {
            return result(state, AutoDockResult.Status.OUTSIDE_REGION, tick);
        }
        if (state.reentryBlockedAtTick == tick) {
            return result(state, AutoDockResult.Status.SAME_TICK_REENTRY_BLOCKED, tick);
        }
        if (state.occupantId != null) {
            return result(state, AutoDockResult.Status.ALREADY_OCCUPIED, tick);
        }

        state.occupantId = actorId;
        state.occupantSourceRound = sourceRound;
        state.occupiedAtTick = tick;
        return result(state, AutoDockResult.Status.ENTERED, tick);
    }

    @Override
    public synchronized AutoDockResult tryLeave(String mechanismId,
                                                 String actorId,
                                                 int sourceRound,
                                                 long tick,
                                                 PathNode.Dir exitDirection,
                                                 Vector2D worldPosition) {
        requireActor(actorId, sourceRound);
        requireTick(tick);
        requirePosition(worldPosition);

        DockState state = docksById.get(mechanismId);
        if (state == null) {
            return new AutoDockResult(AutoDockResult.Status.UNKNOWN_DOCK, tick, null);
        }
        if (!isOwner(state, actorId, sourceRound)) {
            return result(state, AutoDockResult.Status.NOT_OCCUPANT, tick);
        }
        if (exitDirection == null || !state.definition.legalExitDirections().contains(exitDirection)) {
            return result(state, AutoDockResult.Status.INVALID_EXIT_DIRECTION, tick);
        }
        if (state.definition.region().contains(worldPosition)) {
            return result(state, AutoDockResult.Status.NOT_OUTSIDE_REGION, tick);
        }

        clearOccupancy(state);
        state.reentryBlockedAtTick = tick;
        return result(state, AutoDockResult.Status.LEFT, tick);
    }

    @Override
    public synchronized int releaseActor(String actorId, int sourceRound, long tick) {
        requireActor(actorId, sourceRound);
        requireTick(tick);
        int released = 0;
        for (DockState state : docksById.values()) {
            if (isOwner(state, actorId, sourceRound)) {
                clearOccupancy(state);
                state.reentryBlockedAtTick = tick;
                released++;
            }
        }
        return released;
    }

    @Override
    public synchronized void reset(AutoDockResetReason reason, long tick) {
        Objects.requireNonNull(reason, "reason");
        requireTick(tick);
        for (DockState state : docksById.values()) {
            clearOccupancy(state);
            state.reentryBlockedAtTick = -1;
        }
    }

    private static NavigableMap<String, DockState> buildDocks(LevelData levelData) {
        requireFinite(levelData.getTileSize(), "tileSize");
        if (levelData.getTileSize() <= 0.0) {
            throw new IllegalArgumentException("tileSize 必须为正数");
        }

        Map<String, PathNode> nodesById = new HashMap<>();
        for (PathNode node : levelData.getPathNodes()) {
            if (node == null || node.getId() == null) {
                throw new IllegalArgumentException("autoDock 路径节点不能为空");
            }
            StableIdValidator.requirePathNodeId(node.getId(), "autoDock.pathNodeId");
            if (nodesById.putIfAbsent(node.getId(), node) != null) {
                throw new IllegalArgumentException("重复的 autoDock 路径节点 ID: " + node.getId());
            }
        }

        NavigableMap<String, DockState> docks = new TreeMap<>();
        for (EntitySpawnInfo entity : levelData.getEntitySpawnList()) {
            if (!"dock_plate".equals(entity.getEntityType())
                    || !Boolean.TRUE.equals(entity.getProperties().get("autoDock"))) {
                continue;
            }

            String mechanismId = StableIdValidator.requireMechanismId(
                    entity.getId(), "plate", "autoDock.mechanismId");
            String pathNodeId = StableIdValidator.requirePathNodeId(
                    entity.getPathNodeId(), "autoDock.pathNodeId");
            PathNode node = nodesById.get(pathNodeId);
            if (node == null) {
                throw new IllegalArgumentException("autoDock 引用了不存在的路径节点: " + pathNodeId);
            }
            Vector2D center = Objects.requireNonNull(entity.getPos(), "autoDock.center");
            if (!samePosition(center, node.getWorldPos())) {
                throw new IllegalArgumentException(
                        "autoDock " + mechanismId + " 的中心与路径节点不一致");
            }
            if (node.getAllowDirs() == null || node.getAllowDirs().isEmpty()) {
                throw new IllegalArgumentException("autoDock " + mechanismId + " 没有合法出口");
            }
            if (docks.containsKey(mechanismId)) {
                throw new IllegalArgumentException("重复的 autoDock 机制 ID: " + mechanismId);
            }

            DockDefinition definition = new DockDefinition(
                    mechanismId,
                    pathNodeId,
                    DockRegionView.around(center, levelData.getTileSize()),
                    center,
                    Set.copyOf(node.getAllowDirs())
            );
            docks.put(mechanismId, new DockState(definition));
        }
        return docks;
    }

    private static void rejectOverlappingRegions(NavigableMap<String, DockState> docks) {
        List<DockState> states = new ArrayList<>(docks.values());
        for (int i = 0; i < states.size(); i++) {
            for (int j = i + 1; j < states.size(); j++) {
                DockDefinition left = states.get(i).definition;
                DockDefinition right = states.get(j).definition;
                if (left.region().overlaps(right.region())) {
                    throw new IllegalArgumentException(
                            "autoDock 区域重叠: " + left.mechanismId() + " 与 " + right.mechanismId());
                }
            }
        }
    }

    private static boolean isCloser(DockState candidate,
                                    double candidateDistance,
                                    DockState current,
                                    double currentDistance) {
        double epsilon = Math.max(candidate.definition.region().epsilon(),
                current.definition.region().epsilon());
        if (candidateDistance < currentDistance - epsilon) {
            return true;
        }
        return Math.abs(candidateDistance - currentDistance) <= epsilon
                && candidate.definition.mechanismId().compareTo(current.definition.mechanismId()) < 0;
    }

    private AutoDockResult result(DockState state, AutoDockResult.Status status, long tick) {
        return new AutoDockResult(status, tick, viewOf(state));
    }

    private static AutoDockView viewOf(DockState state) {
        DockOccupancyView occupancy = occupancyOf(state);
        return new AutoDockView(
                state.definition.mechanismId(),
                state.definition.pathNodeId(),
                state.definition.region(),
                state.definition.center(),
                state.definition.legalExitDirections(),
                occupancy,
                state.reentryBlockedAtTick);
    }

    private static DockOccupancyView occupancyOf(DockState state) {
        return state.occupantId == null
                ? DockOccupancyView.empty()
                : new DockOccupancyView(
                true,
                state.occupantId,
                state.occupantSourceRound,
                state.occupiedAtTick);
    }

    private static void validateOccupancySnapshot(DockOccupancyView occupancy) {
        Objects.requireNonNull(occupancy, "autoDock.snapshot.occupancy");
        if (occupancy.occupied()) {
            requireActor(occupancy.occupantId(), occupancy.occupantSourceRound());
        }
    }

    private static boolean isOwner(DockState state, String actorId, int sourceRound) {
        return actorId.equals(state.occupantId) && sourceRound == state.occupantSourceRound;
    }

    private static void clearOccupancy(DockState state) {
        state.occupantId = null;
        state.occupantSourceRound = 0;
        state.occupiedAtTick = -1;
    }

    private static void requireActor(String actorId, int sourceRound) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId 不能为空白");
        }
        if (sourceRound < 0) {
            throw new IllegalArgumentException("sourceRound 不能为负数");
        }
        if ("player".equals(actorId)) {
            return;
        }
        if (!actorId.matches("echo_[1-9][0-9]*")) {
            throw new IllegalArgumentException("非法 actorId: " + actorId);
        }
        try {
            int encodedRound = Integer.parseInt(actorId.substring("echo_".length()));
            if (encodedRound != sourceRound) {
                throw new IllegalArgumentException("actorId 与 sourceRound 不一致: " + actorId);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("非法 actorId: " + actorId, exception);
        }
    }

    private static void requireTick(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick 不能为负数");
        }
    }

    private static void requirePosition(Vector2D position) {
        Objects.requireNonNull(position, "worldPosition");
        requireFinite(position.x(), "worldPosition.x");
        requireFinite(position.y(), "worldPosition.y");
    }

    private static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " 必须为有限数");
        }
    }

    private static boolean samePosition(Vector2D left, Vector2D right) {
        return left != null && right != null
                && Math.abs(left.x() - right.x()) <= 1e-9
                && Math.abs(left.y() - right.y()) <= 1e-9;
    }

    private record DockDefinition(String mechanismId,
                                  String pathNodeId,
                                  DockRegionView region,
                                  Vector2D center,
                                  Set<PathNode.Dir> legalExitDirections) {
    }

    private static final class DockState {
        private final DockDefinition definition;
        private String occupantId;
        private int occupantSourceRound;
        private long occupiedAtTick = -1;
        private long reentryBlockedAtTick = -1;

        private DockState(DockDefinition definition) {
            this.definition = definition;
        }
    }
}
