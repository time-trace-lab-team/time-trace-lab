package org.example.timeloop.app;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.TickStepResult;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathPoint;
import org.example.timeloop.entity.C3DockController;
import org.example.timeloop.entity.C3DockDecision;
import org.example.timeloop.entity.PatrolConfig;
import org.example.timeloop.entity.PatrolController;
import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.LevelGeometry;
import org.example.timeloop.level.LevelGeometryImpl;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.replay.EchoFrameView;
import org.example.timeloop.replay.EchoQueue;
import org.example.timeloop.replay.PlayerFrame;
import org.example.timeloop.replay.RecordingSession;
import org.example.timeloop.replay.ReplayPort;
import org.example.timeloop.replay.RoundClock;
import org.example.timeloop.replay.TickContext;
import org.example.timeloop.replay.TimelineEvent;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.snapshot.MechanismSnapshot;
import org.example.timeloop.snapshot.MvpRenderSnapshot;
import org.example.timeloop.ui.HudViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 第一关的无头装配根。
 *
 * <p>本类只把已经冻结的模块接在一起：关卡数据和几何、C2 路径图、玩家巡行、
 * C3 停驻策略、开发三机关/autoDock，以及开发二的录制和只读回放端口。它不创建
 * JavaFX 场景，也不拥有第二套时钟；窗口层可以消费本类产生的只读快照。</p>
 *
 * <p>旧的 {@code Level01Integration} 曾经直接硬编码机关和坐标，已被删除。本类所有
 * 机关实例都来自 {@link Level01Footsteps#build()}，所有路径边都由稳定节点 ID 和
 * 明确方向桥接得到。</p>
 */
public final class Level01Assembly implements AutoCloseable {

    public static final String PLAYER_ID = "player";
    public static final int PLAYER_SOURCE_ROUND = 0;
    public static final String START_NODE_ID = "L01_node_spawn";
    public static final String DOOR_ID = "L01_door_01";
    public static final Direction START_DIRECTION = Direction.DOWN;

    /**
     * 语义固定的方向遍历顺序。这里显式列出方向，不使用 enum ordinal 或集合顺序
     * 决定路径图的内容。
     */
    private static final List<PathNode.Dir> MODEL_DIRECTION_ORDER = List.of(
            PathNode.Dir.UP,
            PathNode.Dir.RIGHT,
            PathNode.Dir.DOWN,
            PathNode.Dir.LEFT
    );

    private final LevelData levelData;
    private final LevelGeometry geometry;
    private final OrthogonalPathGraph pathGraph;
    private final PatrolController patrolController;
    private final AutoDockService autoDockService;
    private final C3DockController dockController;
    private final Map<String, DockingPlate> platesById;
    private final Map<String, Door> doorsById;
    private final Map<String, ExitTerminal> exitsById;
    private final RoundClock clock;
    private final RecordingSession recordingSession;
    private final ReplayPort replayPort;
    private final MechanismSnapshot initialMechanismSnapshot;
    private final org.example.timeloop.mechanism.autodock.AutoDockStateSnapshot initialAutoDockSnapshot;

    private PlayerKinematics lastPlayerKinematics;
    private MvpRenderSnapshot lastRenderSnapshot;
    private boolean closed;

    private Level01Assembly() {
        this.levelData = Level01Footsteps.build();
        this.geometry = new LevelGeometryImpl(levelData);
        this.pathGraph = buildPathGraph(levelData, geometry);

        // 机关使用全局注册表/事件总线；一个新场景必须先切断旧场景的注册。
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();

        this.platesById = buildPlates(levelData);
        this.doorsById = buildDoors(levelData);
        this.exitsById = buildExits(levelData, doorsById);
        this.autoDockService = new AutoDockService(levelData);
        this.dockController = new C3DockController(
                autoDockService,
                autoDockService,
                PLAYER_ID,
                PLAYER_SOURCE_ROUND);

        this.patrolController = new PatrolController(
                pathGraph,
                START_NODE_ID,
                START_DIRECTION,
                PatrolConfig.c2Greybox());

        this.clock = new RoundClock(requireIntDuration(levelData), levelData.getMaxRounds());
        enterPlaying(clock);
        this.recordingSession = new RecordingSession(
                clock,
                new EchoQueue(levelData.getEchoLifeL()));
        recordingSession.beginRound();
        this.replayPort = new ReplayPort(clock, recordingSession);

        this.initialMechanismSnapshot = MechanismSnapshot.capture(
                platesById,
                doorsById,
                exitsById);
        this.initialAutoDockSnapshot = autoDockService.createSnapshot();
        this.lastPlayerKinematics = initialPlayerKinematics(pathGraph);
        this.lastRenderSnapshot = new MvpRenderSnapshot(
                clock.phase(),
                clock.roundTick(),
                clock.currentRound(),
                clock.maxRounds(),
                null,
                List.of());
    }

    /** Creates a fresh first-level assembly in PLAYING with an empty round buffer. */
    public static Level01Assembly create() {
        return new Level01Assembly();
    }

    /** Alias used by integration callers that describe construction as assembly. */
    public static Level01Assembly assemble() {
        return create();
    }

    public LevelData levelData() {
        return levelData;
    }

    public LevelGeometry geometry() {
        return geometry;
    }

    public OrthogonalPathGraph pathGraph() {
        return pathGraph;
    }

    public PatrolController patrolController() {
        return patrolController;
    }

    public AutoDockService autoDockService() {
        return autoDockService;
    }

    public C3DockController dockController() {
        return dockController;
    }

    /** Returns a stable-ID-sorted, unmodifiable view of the plate instances. */
    public Map<String, DockingPlate> platesById() {
        return platesById;
    }

    /** Returns a stable-ID-sorted, unmodifiable view of the door instances. */
    public Map<String, Door> doorsById() {
        return doorsById;
    }

    /** Returns a stable-ID-sorted, unmodifiable view of the exit instances. */
    public Map<String, ExitTerminal> exitsById() {
        return exitsById;
    }

    public RoundClock clock() {
        return clock;
    }

    public RecordingSession recordingSession() {
        return recordingSession;
    }

    public ReplayPort replayPort() {
        return replayPort;
    }

    /**
     * Processes one player tick through C3, C2 and the recording port.
     *
     * <p>The returned snapshots describe the tick just processed. The clock is advanced
     * after the frame and edge events have been recorded; at the final tick it returns
     * {@link TickStepResult#ROUND_END} and waits for the upper layer's round transaction.</p>
     */
    public TickResult tick(InputIntent input) {
        ensureOpen();
        Objects.requireNonNull(input, "input");
        if (!clock.isPlaying()) {
            throw new IllegalStateException("第一关装配当前不在 PLAYING: " + clock.phase());
        }
        long tick = clock.roundTick();
        if (input.tick() != tick) {
            throw new IllegalArgumentException(
                    "输入 tick 必须等于共享时钟当前刻: expected=" + tick + ", actual=" + input.tick());
        }
        if (recordingSession.currentBuffer().orElseThrow().isComplete()) {
            throw new IllegalStateException("当前轮记录已满，必须先完成轮末事务");
        }

        Vector2D currentPosition = toVector(patrolController.position());
        C3DockDecision dockingDecision = dockController.step(tick, input, currentPosition);
        PlayerKinematics nextKinematics;
        if (dockingDecision.isFreeze()) {
            nextKinematics = dockedKinematics(tick, patrolController.position(), patrolController.direction());
        } else {
            dockingDecision.departureDirection().ifPresent(patrolController::queueDirection);
            nextKinematics = patrolController.advance(tick, ExitPassability.allOpen());
        }

        for (TimelineEvent event : dockingDecision.events()) {
            applyMechanismEvent(event);
            recordingSession.recordEvent(event);
        }

        PlayerFrame playerFrame = toPlayerFrame(nextKinematics);
        recordingSession.recordFrame(playerFrame);
        TickContext processedContext = clock.toContext();
        MvpRenderSnapshot renderSnapshot = new MvpRenderSnapshot(
                clock.phase(),
                processedContext.roundTick(),
                processedContext.currentRound(),
                processedContext.maxRounds(),
                replayPort.currentPlayerFrame().orElse(playerFrame),
                replayPort.activeEchoFrames());
        RenderViews.Frame renderViews = buildRenderViews(nextKinematics);
        HudViewModel hud = HudViewModel.from(processedContext);

        lastPlayerKinematics = nextKinematics;
        lastRenderSnapshot = renderSnapshot;
        TickStepResult clockResult = clock.advance();
        return new TickResult(
                tick,
                nextKinematics,
                dockingDecision,
                dockingDecision.events(),
                clockResult,
                renderSnapshot,
                renderViews,
                hud);
    }

    /** Most recent fully processed tick's read-only replay projection. */
    public MvpRenderSnapshot mvpRenderSnapshot() {
        ensureOpen();
        return lastRenderSnapshot;
    }

    /**
     * Builds the C5 read-only render projection from the assembled mechanism state and
     * the last player state. Echoes contain their current point; the full frame history
     * remains available through {@link MvpRenderSnapshot} and {@link ReplayPort}.
     */
    public RenderViews.Frame renderViews() {
        ensureOpen();
        return buildRenderViews(lastPlayerKinematics);
    }

    /** HUD projection from the one shared clock context; this method creates no timer. */
    public HudViewModel hudViewModel() {
        ensureOpen();
        return HudViewModel.from(clock.toContext());
    }

    /**
     * Restores the first-level mechanism state for a round boundary or full restart.
     * The returned events are diagnostics only and are deliberately not added to the
     * gameplay recording batch.
     */
    public List<TimelineEvent> resetMechanisms(AutoDockResetReason reason, long tick) {
        ensureOpen();
        Objects.requireNonNull(reason, "reason");
        List<TimelineEvent> events = dockController.reset(reason, tick);
        for (TimelineEvent event : events) {
            applyMechanismEvent(event);
        }
        initialMechanismSnapshot.restore(platesById, doorsById, exitsById);
        autoDockService.restore(initialAutoDockSnapshot);
        return List.copyOf(events);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            List<TimelineEvent> events = dockController.reset(
                    AutoDockResetReason.SCENE_EXIT,
                    clock.roundTick());
            for (TimelineEvent event : events) {
                applyMechanismEvent(event);
            }
        } finally {
            exitsById.values().forEach(ExitTerminal::dispose);
            doorsById.values().forEach(Door::dispose);
            platesById.values().forEach(DockingPlate::dispose);
            DockingPlateRegistry.getInstance().clear();
            EventDispatcher.getInstance().clear();
            closed = true;
        }
    }

    private void applyMechanismEvent(TimelineEvent event) {
        DockingPlate plate = platesById.get(event.mechanismId());
        if (plate == null) {
            return;
        }
        switch (event.eventType()) {
            case DOCK_ENTERED -> {
                if (!plate.tryEnter(event.actorId(), event.sourceRound(), event.tick())) {
                    throw new IllegalStateException(
                            "autoDock 与驻留板状态不一致，进入失败: " + event.mechanismId());
                }
            }
            case DOCK_LEFT, OCCUPANCY_RELEASED -> {
                if (!plate.tryExit(event.actorId(), event.sourceRound(), event.tick())
                        && plate.isOccupied()) {
                    throw new IllegalStateException(
                            "autoDock 与驻留板状态不一致，释放失败: " + event.mechanismId());
                }
            }
            case MECHANISM_STATE_CHANGED, EXIT_REQUESTED -> {
                // 第一关 W2 没有额外的状态变更或出口请求转发来源。
            }
        }
    }

    private RenderViews.Frame buildRenderViews(PlayerKinematics playerState) {
        RenderViews.Player player = new RenderViews.Player(
                playerState.x(),
                playerState.y(),
                playerState.direction(),
                playerState.movementState(),
                playerState.actorPhase() == ActorPhase.PHASED);

        List<RenderViews.Mechanism> mechanisms = new ArrayList<>();
        platesById.forEach((id, plate) -> mechanisms.add(new RenderViews.Mechanism(
                id,
                plate.getPosition().x(),
                plate.getPosition().y(),
                RenderViews.MechanismKind.PLATE,
                plate.isOccupied())));
        doorsById.forEach((id, door) -> mechanisms.add(new RenderViews.Mechanism(
                id,
                door.getPosition().x(),
                door.getPosition().y(),
                RenderViews.MechanismKind.DOOR,
                door.isUnlocked())));
        exitsById.forEach((id, exit) -> mechanisms.add(new RenderViews.Mechanism(
                id,
                exit.getPosition().x(),
                exit.getPosition().y(),
                RenderViews.MechanismKind.EXIT,
                exit.isDoorUnlocked())));

        List<EchoFrameView> echoFrames = replayPort.activeEchoFrames();
        List<RenderViews.EchoTrail> echoes = new ArrayList<>(echoFrames.size());
        for (int index = 0; index < echoFrames.size(); index++) {
            EchoFrameView echo = echoFrames.get(index);
            echoes.add(new RenderViews.EchoTrail(
                    echo.sourceRound(),
                    List.of(new Vector2D(echo.frame().x(), echo.frame().y())),
                    index == echoFrames.size() - 1));
        }
        return new RenderViews.Frame(player, mechanisms, echoes);
    }

    private static OrthogonalPathGraph buildPathGraph(LevelData data, LevelGeometry geometry) {
        Map<String, PathNode> nodesById = new LinkedHashMap<>();
        for (PathNode node : data.getPathNodes()) {
            nodesById.put(node.getId(), node);
        }

        List<org.example.timeloop.core.path.PathNode> coreNodes = new ArrayList<>();
        for (PathNode source : data.getPathNodes()) {
            List<PathExit> exits = new ArrayList<>();
            for (PathNode.Dir modelDirection : MODEL_DIRECTION_ORDER) {
                if (!geometry.getValidExits(source.getId()).contains(modelDirection)) {
                    continue;
                }
                Vector2D targetPosition = neighborPosition(
                        source.getWorldPos(),
                        modelDirection,
                        data.getTileSize());
                PathNode target = findNodeAt(data.getPathNodes(), targetPosition)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "第一关路径桥接缺少相邻节点: " + source.getId()
                                        + " --" + modelDirection));
                if (!nodesById.containsKey(target.getId())) {
                    throw new IllegalArgumentException("第一关路径桥接引用未知节点: " + target.getId());
                }
                exits.add(new PathExit(toCoreDirection(modelDirection), target.getId()));
            }

            Optional<Direction> defaultExit = Optional.ofNullable(
                    geometry.getDefaultExit(source.getId())).map(Level01Assembly::toCoreDirection);
            coreNodes.add(new org.example.timeloop.core.path.PathNode(
                    source.getId(),
                    new PathPoint(source.getWorldPos().x(), source.getWorldPos().y()),
                    exits,
                    defaultExit));
        }
        return new OrthogonalPathGraph(coreNodes);
    }

    private static Map<String, DockingPlate> buildPlates(LevelData data) {
        Map<String, DockingPlate> result = new TreeMap<>();
        for (EntitySpawnInfo entity : data.getEntitySpawnList()) {
            if (!"dock_plate".equals(entity.getEntityType())) {
                continue;
            }
            DockingPlate plate = new DockingPlate(entity.getId(), entity.getPos());
            if (result.put(entity.getId(), plate) != null) {
                throw new IllegalArgumentException("重复的第一关驻留板 ID: " + entity.getId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Door> buildDoors(LevelData data) {
        Map<String, Door> result = new TreeMap<>();
        for (DoorInfo info : data.getDoors()) {
            Door door = new Door(info.getId(), info.getPosition(), info.getRequiredPlateIds());
            if (result.put(info.getId(), door) != null) {
                throw new IllegalArgumentException("重复的第一关门 ID: " + info.getId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, ExitTerminal> buildExits(
            LevelData data,
            Map<String, Door> doors) {
        if (doors.isEmpty()) {
            throw new IllegalArgumentException("第一关至少需要一扇门才能装配出口");
        }
        if (!doors.containsKey(DOOR_ID)) {
            throw new IllegalArgumentException("第一关缺少出口所需的稳定门 ID: " + DOOR_ID);
        }
        String associatedDoorId = DOOR_ID;
        Map<String, ExitTerminal> result = new TreeMap<>();
        for (EntitySpawnInfo entity : data.getEntitySpawnList()) {
            if (!"exit_terminal".equals(entity.getEntityType())) {
                continue;
            }
            ExitTerminal exit = new ExitTerminal(entity.getId(), entity.getPos(), associatedDoorId);
            if (result.put(entity.getId(), exit) != null) {
                throw new IllegalArgumentException("重复的第一关出口 ID: " + entity.getId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Optional<PathNode> findNodeAt(List<PathNode> nodes, Vector2D position) {
        for (PathNode node : nodes) {
            if (Math.abs(node.getWorldPos().x() - position.x()) <= 1.0e-9
                    && Math.abs(node.getWorldPos().y() - position.y()) <= 1.0e-9) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static Vector2D neighborPosition(Vector2D position, PathNode.Dir direction, double tileSize) {
        return switch (direction) {
            case UP -> new Vector2D(position.x(), position.y() - tileSize);
            case RIGHT -> new Vector2D(position.x() + tileSize, position.y());
            case DOWN -> new Vector2D(position.x(), position.y() + tileSize);
            case LEFT -> new Vector2D(position.x() - tileSize, position.y());
        };
    }

    private static Direction toCoreDirection(PathNode.Dir direction) {
        return switch (direction) {
            case UP -> Direction.UP;
            case RIGHT -> Direction.RIGHT;
            case DOWN -> Direction.DOWN;
            case LEFT -> Direction.LEFT;
        };
    }

    private static PlayerKinematics initialPlayerKinematics(OrthogonalPathGraph graph) {
        PathPoint spawn = graph.node(START_NODE_ID).center();
        return new PlayerKinematics(
                0,
                spawn.x(),
                spawn.y(),
                START_DIRECTION,
                MovementState.CRUISING,
                ActorPhase.AVAILABLE,
                0,
                false,
                AnimationState.MOVING);
    }

    private static PlayerKinematics dockedKinematics(long tick, PathPoint position, Direction direction) {
        return new PlayerKinematics(
                tick,
                position.x(),
                position.y(),
                direction,
                MovementState.DOCKED,
                ActorPhase.AVAILABLE,
                0,
                false,
                AnimationState.DOCKED);
    }

    private static PlayerFrame toPlayerFrame(PlayerKinematics kinematics) {
        return new PlayerFrame(
                kinematics.tick(),
                kinematics.x(),
                kinematics.y(),
                kinematics.direction(),
                kinematics.interactionTriggered(),
                kinematics.movementState(),
                kinematics.actorPhase(),
                kinematics.actorPhaseTicksRemaining(),
                kinematics.animationState());
    }

    private static Vector2D toVector(PathPoint point) {
        return new Vector2D(point.x(), point.y());
    }

    private static int requireIntDuration(LevelData data) {
        if (data.getDurationTicks() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("第一关 durationTicks 超出共享时钟 int 范围");
        }
        return Math.toIntExact(data.getDurationTicks());
    }

    private static void enterPlaying(RoundClock clock) {
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        clock.transition(GamePhase.PLAYING);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("第一关装配已关闭");
        }
    }

    /** Result of one assembled headless tick, including the read-only projections. */
    public record TickResult(
            long tick,
            PlayerKinematics player,
            C3DockDecision dockingDecision,
            List<TimelineEvent> events,
            TickStepResult clockResult,
            MvpRenderSnapshot mvpRenderSnapshot,
            RenderViews.Frame renderViews,
            HudViewModel hud) {

        public TickResult {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(dockingDecision, "dockingDecision");
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            Objects.requireNonNull(clockResult, "clockResult");
            Objects.requireNonNull(mvpRenderSnapshot, "mvpRenderSnapshot");
            Objects.requireNonNull(renderViews, "renderViews");
            Objects.requireNonNull(hud, "hud");
        }
    }
}
