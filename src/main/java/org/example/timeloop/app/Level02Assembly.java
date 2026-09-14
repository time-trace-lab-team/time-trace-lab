package org.example.timeloop.app;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerEffectResetReason;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.PlayerPhaseStateMachine;
import org.example.timeloop.core.TickStepResult;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathNode;
import org.example.timeloop.entity.C3DockController;
import org.example.timeloop.entity.C3DockDecision;
import org.example.timeloop.entity.PatrolConfig;
import org.example.timeloop.entity.PatrolController;
import org.example.timeloop.entity.PlayerSlowdownController;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.LevelGeometryImpl;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateOccupancyPort;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.mechanism.ray.RayFactory;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.EchoQueue;
import org.example.timeloop.replay.EchoState;
import org.example.timeloop.replay.PlayerFrame;
import org.example.timeloop.replay.RecordingSession;
import org.example.timeloop.replay.ReplayPort;
import org.example.timeloop.replay.RoundClock;
import org.example.timeloop.replay.TickContext;
import org.example.timeloop.replay.TimelineEvent;
import org.example.timeloop.replay.TimelineRecording;
import org.example.timeloop.ui.Level02ObjectiveViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 第二关「闸链」装配（集成层，纯 Java，不依赖 JavaFX）。
 *
 * <p>与 {@link Level01Assembly} 同一套生命周期与只读投影（{@code start / tick / restart / stop /
 * cleanup / phase / isFinalPhase / hudContext / drainEvents / currentRecording / pathNodeMarkers /
 * renderViews / objectiveView / dockingPlate / isPlateOccupied}），因此应用层与测试可以把两关当成
 * 同一个「可推进、可投影的关卡」来接线；差别只在关卡数据与机关数量：</p>
 *
 * <ul>
 *   <li><b>5 块普通驻留板</b>（{@code L02_plate_*}）：占即开、离即关，<b>不锁存</b>、无开关变体；</li>
 *   <li><b>3 扇门</b>：{@code L02_door_gate} 需闸板、{@code L02_door_relay} 需中继板、
 *       {@code L02_door_exit} 需内板 + 主板 + 终结板同刻被占；</li>
 *   <li><b>多门阻挡</b>：{@link #isPassable} 遍历本关<b>全部</b>门 —— 任一未解锁且挡在目标节点即不可通行
 *       （{@link Level01Assembly} 只有一扇门，故那边只需查一扇）；</li>
 *   <li><b>1 束射线</b>（{@code L02_ray_01}）：由 {@link RayFactory} 装配，每逻辑刻用共享
 *       {@code roundTick} 驱动，不自建计时器。</li>
 * </ul>
 *
 * <p>渲染投影契约：5 个 {@code PLATE}、{@code L02_door_gate} / {@code L02_door_relay} 两个
 * {@code DOOR}、终点格<b>只投影一个</b> {@code EXIT}（终点闸 {@code L02_door_exit} 与出口终端同格，
 * 该格的开关状态由 {@code EXIT.active = exit.isDoorUnlocked()} 表达，避免同格叠画门与终点）。</p>
 */
public final class Level02Assembly {

    private static final String PLAYER_ACTOR_ID = "player";
    private static final int PLAYER_SOURCE_ROUND = 0;
    /** 第二关玩家出生节点与初始朝向（轮初复位用，与 PatrolController 构造参数一致）。 */
    private static final String PLAYER_SPAWN_NODE_ID = Level02Corridor.NODE_SPAWN;
    private static final Direction PLAYER_SPAWN_DIRECTION = Direction.UP;
    /** 残影写入机关时的 actor 前缀：`echo_<sourceRound>`（机制侧冻结约定）。 */
    private static final String ECHO_ACTOR_ID_PREFIX = "echo_";

    /** 判定「是否已走到驻留机关中心」的容差（世界单位）。 */
    private static final double DOCK_CENTER_EPSILON = 1e-6;

    private final LevelData levelData;
    private final AutoDockService autoDock;
    private final PatrolController patrol;

    /** B4：相位状态机与减速控制器（core/entity 交付，app 只做接线与投影）。 */
    private final PlayerPhaseStateMachine phaseState;
    private final PlayerSlowdownController slowdown;
    private final C3DockController dockController;
    private final RoundClock clock;
    private final EchoQueue echoQueue;
    private final RecordingSession recording;
    private final ReplayPort replayPort;

    /** 五块普通驻留板（顺序 = 渲染投影顺序）。 */
    private final List<DockingPlate> plates;
    private final DockingPlate gatePlate;
    private final DockingPlate relayPlate;
    private final DockingPlate innerPlate;
    private final DockingPlate mainPlate;
    private final DockingPlate corePlate;

    /** 三扇门（顺序 = 渲染投影顺序：闸门、内室门、终点闸）。 */
    private final List<Door> doors;
    private final Door gateDoor;
    private final Door relayDoor;
    private final Door exitDoor;
    private final ExitTerminal exit;

    /** 本关射线（由共享 roundTick 驱动；无独立计时器）。 */
    private final List<Ray> rays;

    /** 路径节点静态几何（只读投影，供节点提示图层消费）。 */
    private final List<RenderViews.PathNodeMarker> pathNodeMarkers;

    private final DockingPlateOccupancyPort occupancy;
    private final GameEventBus eventBus;

    private final List<TimelineEvent> events = new ArrayList<>();
    private PlayerFrame lastFrame;
    private long lastTick = -1;
    private boolean firstMovementStarted;
    /** 已卸载：{@code stop()} 之后不再推进、不再消费事件（见 {@link #stop()}）。 */
    private boolean stopped;

    public Level02Assembly() {
        this.levelData = Level02Corridor.build();
        new LevelGeometryImpl(levelData); // 构造即校验关卡几何（无孤点 / 无悬空出口）
        this.autoDock = new AutoDockService(levelData);

        OrthogonalPathGraph graph =
                new PathGraphBridge(levelData.getPathNodes(), levelData.getTileSize()).toGraph();
        this.pathNodeMarkers = projectPathNodeMarkers(levelData);
        this.phaseState = new PlayerPhaseStateMachine();
        this.slowdown = new PlayerSlowdownController();
        this.patrol = new PatrolController(graph, PLAYER_SPAWN_NODE_ID, PLAYER_SPAWN_DIRECTION,
                PatrolConfig.c2Greybox(), slowdown);
        this.dockController = new C3DockController(autoDock, autoDock, PLAYER_ACTOR_ID, PLAYER_SOURCE_ROUND);

        this.clock = new RoundClock(Math.toIntExact(levelData.getDurationTicks()), levelData.getMaxRounds());
        this.echoQueue = new EchoQueue(levelData.getEchoLifeL());
        this.recording = new RecordingSession(clock, echoQueue);
        this.replayPort = new ReplayPort(clock, recording);

        // 与 L1 相同：板 / 门 / 出口 / 射线全部注入本装配自己的注册表与事件总线实例。
        this.occupancy = new DockingPlateRegistry();
        this.eventBus = new EventDispatcher();

        // 五块板都是普通驻留板（dock_plate + autoDock=true，无 role=switch）→ 不传 latching。
        this.gatePlate = createPlate(Level02Corridor.PLATE_GATE);
        this.relayPlate = createPlate(Level02Corridor.PLATE_RELAY);
        this.innerPlate = createPlate(Level02Corridor.PLATE_INNER);
        this.mainPlate = createPlate(Level02Corridor.PLATE_MAIN);
        this.corePlate = createPlate(Level02Corridor.PLATE_CORE);
        this.plates = List.of(gatePlate, relayPlate, innerPlate, mainPlate, corePlate);

        this.gateDoor = createDoor(Level02Corridor.DOOR_GATE);
        this.relayDoor = createDoor(Level02Corridor.DOOR_RELAY);
        this.exitDoor = createDoor(Level02Corridor.DOOR_EXIT);
        this.doors = List.of(gateDoor, relayDoor, exitDoor);

        EntitySpawnInfo exitInfo = entity(Level02Corridor.EXIT);
        this.exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), exitDoor.getId(),
                ExitTerminal.interactRadiusForTileSize(levelData.getTileSize()), eventBus);

        this.rays = RayFactory.buildFrom(levelData, eventBus);
    }

    /** 进入第二关会话：BOOT → … → READY → 复位玩家 → 开本轮缓冲 → PLAYING。 */
    public void start() {
        if (stopped) {
            return;
        }
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        resetPlayerForNewRound();
        recording.beginRound();
        firstMovementStarted = false;
        clock.transition(GamePhase.PLAYING);
    }

    /**
     * 是否处于终局阶段（{@link GamePhase#FAILED} / {@link GamePhase#RESULT}）。
     *
     * <p>与 L1 同义：这两个阶段没有 gameplay 输入，集成层必须据此显示「按 R 重开」并接受重开按键。</p>
     */
    public boolean isFinalPhase() {
        GamePhase phase = clock.phase();
        return phase == GamePhase.FAILED || phase == GamePhase.RESULT;
    }

    /**
     * 整局重开：放弃本次会话（清空残影、恢复初始机关状态），回到第 1 轮并立即恢复操作。
     *
     * <p>与 {@link Level01Assembly#restart()} 同构；已卸载（{@link #stop()}）后为重开 no-op。</p>
     */
    public void restart() {
        if (stopped) {
            return;
        }
        if (clock.phase() == GamePhase.RESULT) {
            // RESULT 只允许转移去 MENU / LEVEL_SELECT，不能直接回 READY。
            clock.transition(GamePhase.MENU);
            clock.transition(GamePhase.LEVEL_SELECT);
        }
        recording.restartFromFirstRound(() -> {
            for (DockingPlate p : plates) {
                p.reset();
            }
            for (Door d : doors) {
                d.reset();
            }
            exit.reset();
            for (Ray ray : rays) {
                ray.reset();
            }
            dockController.reset(AutoDockResetReason.FULL_RESTART, clock.roundTick());
            phaseState.reset(PlayerEffectResetReason.FULL_RESTART);
            slowdown.reset(PlayerEffectResetReason.FULL_RESTART);
        });
        clock.transition(GamePhase.PLAYING);
        resetPlayerForNewRound();
        firstMovementStarted = false;
        events.clear();
    }

    public boolean isPlaying() {
        return clock.isPlaying();
    }

    /**
     * 屏幕目标提示的只读投影（第二关原生形态 {@link Level02ObjectiveViewModel}）。
     *
     * <p>第二关有五块板，本关用得到的字段是：外闸板（{@code L02_plate_gate}）对应
     * {@code outerPlateHeld}、{@code L02_plate_inner} 对应 {@code innerPlateHeld}、
     * {@code L02_plate_main} 对应 {@code mainPlateHeld}，闸门状态对应 {@code gateUnlocked}；
     * 中继板 / 终结板的进度已经体现在「闸门是否解锁」与残影代际（{@code E<n>}）上。
     * 本方法只做只读投影，不推进任何状态，也不在 L1 的 VM 里做关卡分支。</p>
     */
    public Level02ObjectiveViewModel objectiveView() {
        return new Level02ObjectiveViewModel(
                clock.currentRound(),
                clock.maxRounds(),
                gatePlate.isOccupied(),
                innerPlate.isOccupied(),
                sourceRoundOrNone(innerPlate),
                mainPlate.isOccupied(),
                sourceRoundOrNone(mainPlate),
                exitDoor.isUnlocked());
    }

    public GamePhase phase() {
        return clock.phase();
    }

    /** HUD 只读上下文。 */
    public TickContext hudContext() {
        return clock.toContext();
    }

    /** 本 tick 起已产生、尚未被消费的事件（供记录层/诊断）。 */
    public List<TimelineEvent> drainEvents() {
        List<TimelineEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
    }

    /** 本轮录制缓冲（只读；供诊断与集成测试核对帧/事件是否真的写进了记录）。 */
    public Optional<TimelineRecording> currentRecording() {
        return recording.currentBuffer();
    }

    /** 路径节点静态几何（只读）：供节点提示图层一次性注入。 */
    public List<RenderViews.PathNodeMarker> pathNodeMarkers() {
        return pathNodeMarkers;
    }

    /** 本装配持有的驻留板（只读；供 HUD 与集成测试，不暴露注册表实现）。 */
    public Optional<DockingPlate> dockingPlate(String plateId) {
        Objects.requireNonNull(plateId, "plateId");
        for (DockingPlate p : plates) {
            if (p.getId().equals(plateId)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** 本装配持有的门（只读；供 HUD 与集成测试核对开/关状态）。 */
    public Optional<Door> door(String doorId) {
        Objects.requireNonNull(doorId, "doorId");
        for (Door d : doors) {
            if (d.getId().equals(doorId)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** 出口终端（只读；供集成测试核对终点闸解锁与结算）。 */
    public ExitTerminal exitTerminal() {
        return exit;
    }

    /**
     * 本关持有的权威射线（只读副本）。
     *
     * <p>存在理由：B3 要求 {@code Ray -> RenderViews.RayBeam} 的投影可被测试逐刻比对到权威状态，
     * 否则「画面比碰撞早/晚一 tick」这类缺陷无法在 headless 下发现。</p>
     */
    public List<Ray> rays() {
        return List.copyOf(rays);
    }

    /**
     * 该驻留板当前是否被占用（只读；走本装配自己的占用端口，与全局单例无关）。
     *
     * <p>第二关五块板都是普通驻留板（不锁存），因此本方法就是「此刻是否有人（玩家 / 残影）站在上面」。</p>
     */
    public boolean isPlateOccupied(String plateId) {
        Objects.requireNonNull(plateId, "plateId");
        return occupancy.isOccupied(plateId);
    }

    /** 推进一个逻辑刻：射线 → 输入 → autoDock 决策 → 巡行/驻留 → 记录帧 → 事件 → 时钟推进。 */
    public void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        if (stopped || !clock.isPlaying()) {
            return;
        }
        // 第一轮先显示完整时间并接收输入；首个方向输入出现前不写帧、不推进 roundTick。
        if (!firstMovementStarted) {
            if (input.heldDirections().isEmpty()) {
                return;
            }
            firstMovementStarted = true;
        }
        long tick = clock.roundTick();
        // 射线没有持久状态，行为完全由共享 roundTick 决定：每逻辑刻驱动一次即可（无独立计时器）。
        RayFactory.updateAll(rays, tick);
        slowdown.beginTick(tick);
        PlayerPhaseStateMachine.Snapshot phaseSnapshot =
                phaseState.advance(input.isHeld(LogicalKey.PHASE), tick);
        Vector2D position = new Vector2D(patrol.position().x(), patrol.position().y());

        C3DockDecision decision = dockController.step(tick, input, position);
        PlayerKinematics kinematics;
        // 同 L1：驻留机关的停驻中心 = 机关所在路径节点中心；进入区域边界后沿中心线补走完这一段，
        // 否则玩家会停在离中心半格处（离开方向被掉头规则拒绝 → 占用永不释放）。
        Optional<Direction> toDockCenter = dockCenterApproach();
        if (toDockCenter.isPresent() && toDockCenter.get() == patrol.direction()) {
            Direction approach = toDockCenter.get();
            kinematics = patrol.advance(tick, Set.of(approach), Optional.of(approach), passability());
        } else if (decision.isFreeze()) {
            kinematics = dockedKinematics(tick);
        } else {
            Set<Direction> heldDirections = input.heldDirections();
            Optional<Direction> newestEdge;
            if (decision.departureDirection().isPresent()) {
                Direction departure = decision.departureDirection().get();
                heldDirections = Set.of(departure);
                newestEdge = Optional.of(departure);
            } else {
                newestEdge = input.lastDirectionEdge();
            }
            kinematics = patrol.advance(tick, heldDirections, newestEdge, passability());
        }

        resolveRayHits(tick);
        lastFrame = toFrame(kinematics, input, phaseSnapshot, slowdown.isSlowed());
        lastTick = tick;
        recording.recordFrame(lastFrame);
        events.addAll(decision.events());
        for (TimelineEvent event : decision.events()) {
            recording.recordEvent(event);
        }
        mirrorDockEvents(decision.events());
        replayEchoEvents(tick);
        interactIfRequested(tick, input);

        TickStepResult result = clock.advance();
        if (result == TickStepResult.ROUND_END) {
            onRoundEnd();
        }
    }

    /**
     * 只读渲染视图（零回写）。
     *
     * <p>投影契约：5 个 {@code PLATE} + {@code L02_door_gate} / {@code L02_door_relay} 两个
     * {@code DOOR} + 终点格一个 {@code EXIT}。终点闸 {@code L02_door_exit} 与出口终端同格，
     * 该格<b>不</b>再投影 {@code DOOR}（同格叠画会互相遮挡），开/关由 {@code EXIT.active} 表达。</p>
     */
    public RenderViews.Frame renderViews() {
        PlayerFrame player = lastFrame;
        RenderViews.Player playerView = player == null
                ? new RenderViews.Player(patrol.position().x(), patrol.position().y(),
                        patrol.direction(), MovementState.IDLE, false)
                : new RenderViews.Player(player.x(), player.y(), player.direction(),
                        player.movementState(), player.isPhaseDodging());

        List<RenderViews.Mechanism> mechanisms = new ArrayList<>();
        for (DockingPlate p : plates) {
            mechanisms.add(new RenderViews.Mechanism(p.getId(), p.getPosition().x(),
                    p.getPosition().y(), RenderViews.MechanismKind.PLATE, p.isOccupied()));
        }
        mechanisms.add(new RenderViews.Mechanism(gateDoor.getId(), gateDoor.getPosition().x(),
                gateDoor.getPosition().y(), RenderViews.MechanismKind.DOOR, gateDoor.isUnlocked()));
        mechanisms.add(new RenderViews.Mechanism(relayDoor.getId(), relayDoor.getPosition().x(),
                relayDoor.getPosition().y(), RenderViews.MechanismKind.DOOR, relayDoor.isUnlocked()));
        mechanisms.add(new RenderViews.Mechanism(exit.getId(), exit.getPosition().x(),
                exit.getPosition().y(), RenderViews.MechanismKind.EXIT, exit.isDoorUnlocked()));

        List<RenderViews.EchoTrail> echoes = new ArrayList<>();
        for (EchoState echo : echoQueue.activeEchoes(clock.currentRound())) {
            List<Vector2D> trail = new ArrayList<>();
            int maxTick = (int) Math.min(Math.max(lastTick, 0), echo.durationTicks() - 1);
            for (int t = 0; t <= maxTick; t += 4) {
                PlayerFrame frame = echo.frameAt(t);
                trail.add(new Vector2D(frame.x(), frame.y()));
            }
            boolean newer = echo.sourceRound() == clock.currentRound() - 1;
            echoes.add(new RenderViews.EchoTrail(echo.sourceRound(), trail, newer));
        }

        // B3-2：射线 → 只读渲染投影（映射只允许出现在 app 侧，render/** 不得反向取机制层状态）。
        List<RenderViews.RayBeam> rayBeams = new ArrayList<>(rays.size());
        for (Ray ray : rays) {
            rayBeams.add(toRayBeam(ray));
        }

        return new RenderViews.Frame(playerView, mechanisms, echoes, rayBeams);
    }

    /**
     * {@link Ray} → {@link RenderViews.RayBeam} 的穷尽投影。
     *
     * <p>状态映射刻意<b>不写 default</b>：将来 {@link Ray.State} 新增状态时这里会直接编译失败，
     * 而不是把新状态悄悄画成 OFF。</p>
     */
    private static RenderViews.RayBeam toRayBeam(Ray ray) {
        RenderViews.RayVisualState state = switch (ray.getState()) {
            case OFF -> RenderViews.RayVisualState.OFF;
            case WARNING -> RenderViews.RayVisualState.WARNING;
            case ACTIVE -> RenderViews.RayVisualState.ACTIVE;
        };
        return new RenderViews.RayBeam(ray.getId(),
                ray.getStart().x(), ray.getStart().y(),
                ray.getEnd().x(), ray.getEnd().y(),
                state);
    }

    /**
     * 关闭/退出清理：释放本装配的机关（板 / 门 / 出口 / 射线）并丢弃自己持有的注册表与事件总线。
     *
     * <p>幂等：可重复调用（切换关卡与场景退出都会走到这里）。</p>
     */
    public void cleanup() {
        for (DockingPlate p : plates) {
            p.dispose();
        }
        for (Door d : doors) {
            d.dispose();
        }
        exit.dispose();
        RayFactory.disposeAll(rays);
    }

    /**
     * 卸载本关（{@link LevelFlow} 在切换到下一关前调用）。
     *
     * <p>两件事必须同时发生，缺一就会出现「两关同时推进」：</p>
     * <ol>
     *   <li>停止逻辑推进：{@code stopped = true} → {@link #tick} / {@link #restart} 立即返回，
     *       即使还有残留引用（旧图层、旧回调）本关也不会再推进一个逻辑刻；</li>
     *   <li>释放事件消费：{@link #cleanup()} 把板/门/出口/射线从本装配的事件总线注销，
     *       卸载后的事件不会再改动本关状态。</li>
     * </ol>
     */
    public void stop() {
        stopped = true;
        cleanup();
    }

    /** 本关是否已卸载（{@link #stop()} 之后恒为 true）。 */
    public boolean isStopped() {
        return stopped;
    }

    // ---------- 内部 ----------

    /** 把关卡路径节点投影成只读的 {@link RenderViews.PathNodeMarker} 列表（节点提示数据源）。 */
    private static List<RenderViews.PathNodeMarker> projectPathNodeMarkers(LevelData levelData) {
        List<RenderViews.PathNodeMarker> markers = new ArrayList<>();
        for (org.example.timeloop.level.model.PathNode node : levelData.getPathNodes()) {
            markers.add(new RenderViews.PathNodeMarker(
                    node.getId(), node.getWorldPos().x(), node.getWorldPos().y()));
        }
        return List.copyOf(markers);
    }

    /** 轮初复位：把玩家放回出生节点中心与初始朝向（每轮只调用一次）。 */
    private void resetPlayerForNewRound() {
        patrol.resetTo(PLAYER_SPAWN_NODE_ID, PLAYER_SPAWN_DIRECTION);
        phaseState.reset(PlayerEffectResetReason.ROUND_END);
        slowdown.reset(PlayerEffectResetReason.ROUND_END);
        lastFrame = null;
    }

    /** 驻留机关停驻中心方向：尚未走到机关中心时返回「走过去」的方向，已在中心返回空。 */
    private Optional<Direction> dockCenterApproach() {
        Optional<AutoDockView> docked = dockController.dockedMechanismId().flatMap(autoDock::findById);
        if (docked.isEmpty()) {
            return Optional.empty();
        }
        Vector2D center = docked.get().center();
        double dx = center.x() - patrol.position().x();
        double dy = center.y() - patrol.position().y();
        if (Math.abs(dx) <= DOCK_CENTER_EPSILON && Math.abs(dy) <= DOCK_CENTER_EPSILON) {
            return Optional.empty();
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            return Optional.of(dx > 0 ? Direction.RIGHT : Direction.LEFT);
        }
        return Optional.of(dy > 0 ? Direction.DOWN : Direction.UP);
    }

    private ExitPassability passability() {
        return this::isPassable;
    }

    /**
     * 多门通行判定：<b>遍历本关全部门</b> —— 只要有一扇门未解锁且挡在目标节点，该出口就不可通行。
     *
     * <p>{@link Level01Assembly#isPassable} 只查一扇门（第一关只有一扇）；第二关三扇门互不影响：
     * 闸板只开外闸、中继板只开内室门、三块板同刻被占才开终点闸。</p>
     */
    private boolean isPassable(PathNode from, PathExit exitEdge, PathNode target) {
        Vector2D targetCenter = new Vector2D(target.center().x(), target.center().y());
        for (Door d : doors) {
            if (d.isUnlocked()) {
                continue;
            }
            if (samePosition(targetCenter, d.getPosition())) {
                return false;
            }
        }
        return true;
    }

    private PlayerKinematics dockedKinematics(long tick) {
        return new PlayerKinematics(tick, patrol.position().x(), patrol.position().y(),
                patrol.direction(), MovementState.DOCKED, ActorPhase.AVAILABLE, 0, false,
                AnimationState.DOCKED);
    }

    private PlayerFrame toFrame(PlayerKinematics k, InputIntent input,
                                PlayerPhaseStateMachine.Snapshot phaseSnapshot, boolean slowed) {
        boolean interacting = input.isPressed(LogicalKey.INTERACT);
        AnimationState animation = interacting
                ? AnimationState.INTERACTING
                : k.movementState() == MovementState.DOCKED ? AnimationState.DOCKED : AnimationState.MOVING;
        return new PlayerFrame(k.tick(), k.x(), k.y(), k.direction(), interacting,
                k.movementState() == MovementState.DOCKED || !slowed ? k.movementState() : MovementState.SLOWED,
                phaseSnapshot.phase(), phaseSnapshot.ticksRemaining(), animation);
    }

    /**
     * 命中结算：只对**当前玩家**、用**移动后**位置与权威端点判定；PHASED 直接豁免（不写减速）。
     *
     * <p>周期编号来自关卡数据（{@code RAY_CYCLE_TICKS}），同 (rayId, activeCycle) 只结算一次；
     * 残影不参与判定。命中只写减速，不进入 FAILED，也不截断录制。</p>
     */
    private void resolveRayHits(long tick) {
        if (phaseState.snapshot().phase() == ActorPhase.PHASED) {
            return;
        }
        Vector2D position = new Vector2D(patrol.position().x(), patrol.position().y());
        long activeCycle = tick / Level02Corridor.RAY_CYCLE_TICKS;
        for (Ray ray : rays) {
            if (ray.getState() != Ray.State.ACTIVE) {
                continue;
            }
            if (ray.containsPoint(position, Level02Corridor.RAY_HIT_WIDTH)) {
                slowdown.noteHit(ray.getId(), activeCycle);
            }
        }
    }

    /** 把当前玩家的 autoDock 边沿镜像到机关侧（驱动门）。 */
    private void mirrorDockEvents(List<TimelineEvent> tickEvents) {
        for (TimelineEvent event : tickEvents) {
            DockingPlate plate = plateById(event.mechanismId());
            if (plate == null) {
                continue;
            }
            switch (event.eventType()) {
                case DOCK_ENTERED -> plate.tryEnter(event.actorId(), event.sourceRound(), event.tick());
                case DOCK_LEFT, OCCUPANCY_RELEASED ->
                        plate.tryExit(event.actorId(), event.sourceRound(), event.tick());
            }
        }
    }

    /**
     * 回放活跃残影在本刻的机关事件（残影可占板，按记录复现）。
     *
     * <p>actor 归属与 L1 完全一致：写进机关时必须改写为 {@code echo_<sourceRound>}，
     * 否则残影占用与活玩家无法区分、残影消失时驻留板不会被释放。</p>
     */
    private void replayEchoEvents(long tick) {
        for (EchoState echo : echoQueue.activeEchoes(clock.currentRound())) {
            String echoActorId = ECHO_ACTOR_ID_PREFIX + echo.sourceRound();
            for (TimelineEvent event : echo.eventsAt(tick)) {
                DockingPlate plate = plateById(event.mechanismId());
                if (plate == null) {
                    continue;
                }
                if (event.eventType() == TimelineEvent.EventType.DOCK_ENTERED) {
                    plate.tryEnter(echoActorId, echo.sourceRound(), event.tick());
                } else {
                    plate.tryExit(echoActorId, echo.sourceRound(), event.tick());
                }
            }
        }
    }

    /** `E` 交互：宽容半径内 + 终点闸已解锁 + 未触发（半径来自机制侧，app 不硬编码）。 */
    private void interactIfRequested(long tick, InputIntent input) {
        if (!input.isPressed(LogicalKey.INTERACT) || exit.isTriggered() || !exit.isDoorUnlocked()) {
            return;
        }
        if (!exit.isInInteractRange(new Vector2D(patrol.position().x(), patrol.position().y()))) {
            return;
        }
        exit.interact(tick, PLAYER_SOURCE_ROUND);
        recording.completeGoal();
    }

    private void onRoundEnd() {
        if (clock.currentRound() >= clock.maxRounds()) {
            recording.failFinalRound();
            return;
        }
        long boundaryTick = clock.roundTick();
        Set<Integer> echoesBefore = activeEchoRounds();
        recording.completeNormalRound(
                () -> dockController.reset(AutoDockResetReason.ROUND_END, boundaryTick));
        for (int goneRound : evictedEchoRounds(echoesBefore)) {
            eventBus.dispatch(GameEvent.echoDisappeared(
                    ECHO_ACTOR_ID_PREFIX + goneRound, boundaryTick, goneRound));
        }
        for (DockingPlate p : plates) {
            p.reset();
        }
        for (Door d : doors) {
            d.reset();
        }
        exit.reset();
        clock.transition(GamePhase.PLAYING);
        resetPlayerForNewRound();
    }

    /** 当前轮仍活跃的残影来源轮次集合（轮末 diff 用）。 */
    private Set<Integer> activeEchoRounds() {
        Set<Integer> rounds = new TreeSet<>();
        for (EchoState echo : echoQueue.activeEchoes(clock.currentRound())) {
            rounds.add(echo.sourceRound());
        }
        return rounds;
    }

    /** 轮末事务前活跃、事务后不再活跃的残影来源轮次（即本轮被寿命淘汰的残影）。 */
    private Set<Integer> evictedEchoRounds(Set<Integer> beforeRounds) {
        Set<Integer> evicted = new TreeSet<>(beforeRounds);
        evicted.removeAll(activeEchoRounds());
        return evicted;
    }

    /** 本装配的事件总线（包内可见：集成测试用它捕获残影淘汰等事件）。 */
    GameEventBus eventBus() {
        return eventBus;
    }

    private DockingPlate plateById(String mechanismId) {
        for (DockingPlate p : plates) {
            if (p.getId().equals(mechanismId)) {
                return p;
            }
        }
        return null;
    }

    private DockingPlate createPlate(String plateId) {
        EntitySpawnInfo info = entity(plateId);
        // 第二关五块板都是普通驻留板（关卡数据无 role=switch）→ 一律不锁存。
        return new DockingPlate(info.getId(), info.getPos(), occupancy, eventBus);
    }

    private Door createDoor(String doorId) {
        DoorInfo info = levelData.getDoors().stream()
                .filter(d -> doorId.equals(d.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少第二关门数据: " + doorId));
        return new Door(info.getId(), info.getPosition(), info.getRequiredPlateIds(), occupancy, eventBus);
    }

    /** 占用者来源轮 → VM 字段：{@code 0}=当前玩家、{@code >=1}=该代残影、{@code -1}=无人（未占用）。 */
    private static int sourceRoundOrNone(DockingPlate plate) {
        return plate.isOccupied() ? plate.getOccupantSourceRound() : -1;
    }

    private EntitySpawnInfo entity(String id) {
        return levelData.getEntitySpawnList().stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少第二关实体: " + id));
    }

    private static boolean samePosition(Vector2D left, Vector2D right) {
        return Math.abs(left.x() - right.x()) <= 1e-6 && Math.abs(left.y() - right.y()) <= 1e-6;
    }
}
