package org.example.timeloop.app;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
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
import org.example.timeloop.level.Level01Footsteps;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 第一关「留下的脚步」装配（集成层，纯 Java，不依赖 JavaFX）。
 *
 * <p>按《第一关MVP-集成装配技术指南》5 步把零件串起来：加载关卡 → 创建玩家与机关 →
 * 每 tick 驱动（输入/巡行/autoDock/录制）→ 只读渲染视图 → HUD 上下文。
 * 渲染与窗口接线由 {@link TimeTraceLabApplication} 负责。</p>
 */
public final class Level01Assembly {

    private static final String PLAYER_ACTOR_ID = "player";
    private static final int PLAYER_SOURCE_ROUND = 0;
    /** 第一关玩家出生节点与初始朝向（轮初复位用，与 PatrolController 构造参数一致）。 */
    private static final String PLAYER_SPAWN_NODE_ID = "L01_node_spawn";
    private static final Direction PLAYER_SPAWN_DIRECTION = Direction.DOWN;
    /** 残影写入机关时的 actor 前缀：`echo_<sourceRound>`（机制侧冻结约定）。 */
    private static final String ECHO_ACTOR_ID_PREFIX = "echo_";

    /** 判定“是否已走到驻留机关中心”的容差（世界单位）。 */
    private static final double DOCK_CENTER_EPSILON = 1e-6;

    private final LevelData levelData;
    private final AutoDockService autoDock;
    private final PatrolController patrol;
    private final C3DockController dockController;
    private final RoundClock clock;
    private final EchoQueue echoQueue;
    private final RecordingSession recording;
    private final ReplayPort replayPort;

    private final DockingPlate leftPlate;
    private final DockingPlate rightPlate;
    private final Door door;
    private final ExitTerminal exit;

    /**
     * 路径节点静态几何（只读投影，供 R-2 节点提示图层消费）。
     *
     * <p>只承载关卡路径节点的稳定 ID 与世界坐标，不含玩家选择、门/开关/占用等玩法状态；
     * 由 {@code levelData.getPathNodes()} 一次性投影，不随帧变化。</p>
     */
    private final List<RenderViews.PathNodeMarker> pathNodeMarkers;

    /**
     * 本关装配自己持有的板占用注册表与事件总线（BUG-002-LIFECYCLE Phase 1 的注入形态）。
     *
     * <p>不再使用全局单例：同一 JVM 内可以有多个装配实例而互不干扰；场景切换/重开时随装配一起丢弃。</p>
     */
    private final DockingPlateOccupancyPort occupancy;
    private final GameEventBus eventBus;

    private final List<TimelineEvent> events = new ArrayList<>();
    private PlayerFrame lastFrame;
    private long lastTick = -1;
    private boolean firstMovementStarted;
    /** 已卸载：{@link #stop()} 之后不再推进、不再消费事件（切关时由 {@link LevelFlow} 调用）。 */
    private boolean stopped;

    public Level01Assembly() {
        this.levelData = Level01Footsteps.build();
        new LevelGeometryImpl(levelData); // 构造即校验关卡几何（W2 后应通过）
        this.autoDock = new AutoDockService(levelData);

        OrthogonalPathGraph graph =
                new PathGraphBridge(levelData.getPathNodes(), levelData.getTileSize()).toGraph();
        this.pathNodeMarkers = projectPathNodeMarkers(levelData);
        this.patrol = new PatrolController(graph, "L01_node_spawn", Direction.DOWN, PatrolConfig.c2Greybox());
        this.dockController = new C3DockController(autoDock, autoDock, PLAYER_ACTOR_ID, PLAYER_SOURCE_ROUND);

        this.clock = new RoundClock(Math.toIntExact(levelData.getDurationTicks()), levelData.getMaxRounds());
        this.echoQueue = new EchoQueue(levelData.getEchoLifeL());
        this.recording = new RecordingSession(clock, echoQueue, LevelFlow.LevelId.LEVEL_01.title());
        this.replayPort = new ReplayPort(clock, recording);

        EntitySpawnInfo leftInfo = entity("L01_plate_left");
        EntitySpawnInfo rightInfo = entity("L01_plate_right");
        EntitySpawnInfo exitInfo = entity("L01_exit_00");
        DoorInfo doorInfo = levelData.getDoors().get(0);

        // BUG-002-LIFECYCLE Phase 1：板/门/出口全部通过窄端口注入本装配自己的实例，
        // 不再读写 DockingPlateRegistry / EventDispatcher 的兼容单例。
        this.occupancy = new DockingPlateRegistry();
        this.eventBus = new EventDispatcher();
        this.leftPlate = new DockingPlate(leftInfo.getId(), leftInfo.getPos(), occupancy, eventBus);
        // L01-GATE-MERGE：右板是「开关」表现变体（关卡数据 role=switch）→ 启用本轮内锁存。
        // 不接线的话游戏里开关离开就弹起，与 render 契约「active = 本轮锁存」不符。
        boolean rightLatching = "switch".equals(rightInfo.getProperties().get("role"));
        this.rightPlate = new DockingPlate(rightInfo.getId(), rightInfo.getPos(), occupancy, eventBus,
                rightLatching);
        this.door = new Door(doorInfo.getId(), doorInfo.getPosition(), doorInfo.getRequiredPlateIds(),
                occupancy, eventBus);
        this.exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), door.getId(),
                ExitTerminal.interactRadiusForTileSize(levelData.getTileSize()), eventBus);
    }

    /** 进入第一关会话：BOOT → … → READY → 复位玩家 → 开本轮缓冲 → PLAYING。 */
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
     * <p>这两个阶段没有 gameplay 输入：{@link #tick} 会直接返回。集成层必须据此显示
     * "按 R 重开"提示并接受重开按键，否则玩家会停在"角色不能动、也没有下一步"的死画面里。</p>
     */
    public boolean isFinalPhase() {
        GamePhase phase = clock.phase();
        return phase == GamePhase.FAILED || phase == GamePhase.RESULT;
    }

    /**
     * 整局重开：放弃本次会话（清空残影、恢复初始机关状态），回到第 1 轮并立即恢复操作。
     *
     * <p>存在的理由：FAILED / RESULT 是终局阶段，{@code tick()} 直接 return —— 若不给重开入口，
     * 失败之后玩家就永远动不了。重开复用 replay 的会话重置契约
     * （{@link RecordingSession#restartFromFirstRound}，收尾停在 READY），本方法补上
     * READY → PLAYING 与轮初复位，语义与 {@link #start()} 的末段保持一致。</p>
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
            leftPlate.reset();
            rightPlate.reset();
            door.reset();
            exit.reset();
            dockController.reset(AutoDockResetReason.FULL_RESTART, clock.roundTick());
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
     * 屏幕目标提示的只读投影：谁压着哪块驻留板 + 门是否已解锁。
     *
     * <p>存在的理由：两块驻留板外观完全相同，玩家站在<b>被残影占用</b>的板上时既不会驻留、
     * 也没有任何反馈，于是会以为"我明明站在板上却按不了 E"。把占用者身份投影出去，
     * HUD 才能直接写出"残影压着左板，你去右板"。</p>
     */
    public org.example.timeloop.ui.ObjectiveViewModel objectiveView() {
        String leftOccupant = leftPlate.isOccupied() ? leftPlate.getOccupantId() : null;
        String rightOccupant = rightPlate.isOccupied() ? rightPlate.getOccupantId() : null;
        return new org.example.timeloop.ui.ObjectiveViewModel(
                clock.currentRound(),
                clock.maxRounds(),
                PLAYER_ACTOR_ID.equals(leftOccupant),
                leftOccupant != null && leftOccupant.startsWith(ECHO_ACTOR_ID_PREFIX),
                PLAYER_ACTOR_ID.equals(rightOccupant),
                rightOccupant != null && rightOccupant.startsWith(ECHO_ACTOR_ID_PREFIX),
                door.isUnlocked());
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

    /**
     * 路径节点静态几何（只读）：供节点提示图层（{@code PathNodeHintLayer}）一次性注入。
     *
     * <p>返回不可变列表；内容只来自关卡路径节点，与玩家状态、门/开关/占用无关。</p>
     */
    public List<RenderViews.PathNodeMarker> pathNodeMarkers() {
        return pathNodeMarkers;
    }

    /** 本装配持有的驻留板（只读；供 HUD 与集成测试，不暴露注册表实现）。 */
    public Optional<DockingPlate> dockingPlate(String plateId) {
        Objects.requireNonNull(plateId, "plateId");
        if (leftPlate.getId().equals(plateId)) {
            return Optional.of(leftPlate);
        }
        if (rightPlate.getId().equals(plateId)) {
            return Optional.of(rightPlate);
        }
        return Optional.empty();
    }

    /**
     * 该驻留板当前是否被占用（只读；走本装配自己的占用端口，与全局单例无关）。
     *
     * <p><b>开关变体（{@code role=switch}）语义扩展</b>：L01-GATE-MERGE 后右板是锁存开关，
     * 本方法对它返回「**占用 ∨ 本轮已锁存**」——人离开后仍为 {@code true}，直到轮末 {@code reset()}。
     * 要问「现在是否有人站在上面」请用 {@code DockingPlate.getState()/getOccupantId()}，
     * 要问「开关是否已开启」请用 {@code DockingPlate.isLatched()}。</p>
     */
    public boolean isPlateOccupied(String plateId) {
        Objects.requireNonNull(plateId, "plateId");
        return occupancy.isOccupied(plateId);
    }

    /** 推进一个逻辑刻：输入 → autoDock 决策 → 巡行/驻留 → 记录帧 → 事件 → 时钟推进。 */
    public void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        if (stopped || !clock.isPlaying()) {
            return;
        }
        // 第一轮先显示完整时间并接收输入；首个方向输入出现前不写帧、不推进 roundTick。
        // 后续轮次必须立即推进，才能让当前玩家与残影继续共享同一逻辑时钟。
        if (!firstMovementStarted) {
            if (input.heldDirections().isEmpty()) {
                return;
            }
            firstMovementStarted = true;
        }
        long tick = clock.roundTick();
        Vector2D position = new Vector2D(patrol.position().x(), patrol.position().y());

        C3DockDecision decision = dockController.step(tick, input, position);
        PlayerKinematics kinematics;
        // 驻留机关的停驻中心 = 机关所在路径节点中心，而 autoDock 区域是边长 tileSize 的方格：
        // 玩家在区域边界就被判定进入，此刻若直接冻结会停在离中心半格处，离开时又只能用反方向
        // （被掉头规则拒绝）→ 位置出不了区域、占用永不释放。因此先把这一段沿中心线走完。
        // 只在“机关中心还在前进方向上”时补走：离开驻留板时中心在身后，不能反向走回去。
        Optional<Direction> toDockCenter = dockCenterApproach();
        if (toDockCenter.isPresent() && toDockCenter.get() == patrol.direction()) {
            Direction approach = toDockCenter.get();
            kinematics = patrol.advance(tick, Set.of(approach), Optional.of(approach), passability());
        } else if (decision.isFreeze()) {
            kinematics = dockedKinematics(tick);
        } else {
            // CORE-1：held 投影与方向边沿都由 core 提供，app 不再维护第二份映射；
            // ENT-1：单槽转向意图由 PatrolController 自己保留到节点中心/松手/提交。
            Set<Direction> heldDirections = input.heldDirections();
            Optional<Direction> newestEdge;
            if (decision.departureDirection().isPresent()) {
                // 驻留离开（ENT-2a/2b 同刻释放语义）：C3 在离开边沿刻已调用 tryLeave 并释放占用，
                // 本刻把该方向作为唯一请求方向传给移动层，让角色立即按所选方向出发。
                Direction departure = decision.departureDirection().get();
                heldDirections = Set.of(departure);
                newestEdge = Optional.of(departure);
            } else {
                newestEdge = input.lastDirectionEdge();
            }
            kinematics = patrol.advance(tick, heldDirections, newestEdge, passability());
        }

        lastFrame = toFrame(kinematics, input);
        lastTick = tick;
        recording.recordFrame(lastFrame);
        events.addAll(decision.events());
        // 把本刻机关边沿写进本轮记录：残影回放只认记录里的事件（echo.eventsAt），
        // 不写就会出现“残影路过驻留板却不占板”，第一关双板门永远打不开。
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

    /** 只读渲染视图（开发一图层消费；零回写）。 */
    public RenderViews.Frame renderViews() {
        PlayerFrame player = lastFrame;
        RenderViews.Player playerView = player == null
                ? new RenderViews.Player(patrol.position().x(), patrol.position().y(),
                        patrol.direction(), MovementState.IDLE, false)
                : new RenderViews.Player(player.x(), player.y(), player.direction(),
                        player.movementState(), player.isPhaseDodging());

        // L01-GATE-MERGE 投影契约（开发一 render 对接卡）：
        // 左板 = PLATE/占用；右板 = SWITCH/**本轮锁存**（不是「当前是否有人站着」）；
        // 第一关不再投影独立 DOOR —— 闸门与终点同格，开/关由 EXIT 的 active（exit.isDoorUnlocked()）表达；
        // MechanismKind.DOOR 绘制分支保留给第二关起使用。
        List<RenderViews.Mechanism> mechanisms = List.of(
                new RenderViews.Mechanism(leftPlate.getId(), leftPlate.getPosition().x(),
                        leftPlate.getPosition().y(), RenderViews.MechanismKind.PLATE, leftPlate.isOccupied()),
                new RenderViews.Mechanism(rightPlate.getId(), rightPlate.getPosition().x(),
                        rightPlate.getPosition().y(), RenderViews.MechanismKind.SWITCH, rightPlate.isLatched()),
                new RenderViews.Mechanism(exit.getId(), exit.getPosition().x(),
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

        return new RenderViews.Frame(playerView, mechanisms, echoes);
    }

    /**
     * 关闭/退出清理：释放本装配的机关并丢弃自己持有的注册表与事件总线实例。
     *
     * <p>BUG-002-LIFECYCLE Phase 1：不再触碰全局单例 —— 实例随装配一起被 GC，
     * 因此同一 JVM 内可以有多个装配，互不干扰，测试也不需要再手工清理全局状态。</p>
     */
    public void cleanup() {
        leftPlate.dispose();
        rightPlate.dispose();
        door.dispose();
        exit.dispose();
    }

    /**
     * 卸载本关（{@link LevelFlow} 在第一关通关、切换到第二关之前调用）。
     *
     * <p>两件事必须同时发生，缺一就会出现「两关同时推进」：</p>
     * <ol>
     *   <li>停止逻辑推进：{@code stopped = true} → {@link #tick} / {@link #restart} 立即返回，
     *       即使还有残留引用（旧图层、旧回调）本关也不会再推进一个逻辑刻；</li>
     *   <li>释放事件消费：{@link #cleanup()} 把板/门/出口从本装配的事件总线注销，
     *       卸载后的事件不会再改动本关状态。</li>
     * </ol>
     *
     * <p>默认（未调用本方法）行为与本方法引入前完全一致：既有第一关测试不受影响。</p>
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

    /**
     * 把关卡路径节点投影成只读的 {@link RenderViews.PathNodeMarker} 列表（R-2 节点提示数据源）。
     *
     * <p>只取稳定 ID 与世界坐标；<b>不</b>读取玩家状态、门的运行状态或占用信息。</p>
     */
    private static List<RenderViews.PathNodeMarker> projectPathNodeMarkers(LevelData levelData) {
        List<RenderViews.PathNodeMarker> markers = new ArrayList<>();
        for (org.example.timeloop.level.model.PathNode node : levelData.getPathNodes()) {
            markers.add(new RenderViews.PathNodeMarker(
                    node.getId(), node.getWorldPos().x(), node.getWorldPos().y()));
        }
        return List.copyOf(markers);
    }

    /**
     * 轮初复位：把玩家放回出生节点中心与初始朝向（ENT-3 的 `PatrolController.resetTo`）。
     *
     * <p>恰好对应“新轮玩家状态初始化”，每轮只调用一次（`READY` 之后、`PLAYING` 之前）：
     * 不写帧、不发事件、不清残影；本轮缓冲与时钟由 `RecordingSession` 负责。</p>
     */
    private void resetPlayerForNewRound() {
        patrol.resetTo(PLAYER_SPAWN_NODE_ID, PLAYER_SPAWN_DIRECTION);
        // 本轮还没有任何帧：清掉上一轮末刻的缓存帧，避免轮初这一小段渲染出上一轮的落点
        // （lastTick 保留，残影轨迹仍按上一轮整轮采样）。
        lastFrame = null;
    }

    /**
     * 驻留机关停驻中心方向：尚未走到机关中心时返回“走过去”的方向，已在中心返回空。
     *
     * <p>只在确实被本 actor 占用驻留时计算；未驻留（或占用已被离开边沿释放）返回空。</p>
     */
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

    private boolean isPassable(PathNode from, PathExit exitEdge, PathNode target) {
        if (door.isUnlocked()) {
            return true;
        }
        // 关闭门挡在门所在节点：目标节点世界坐标等于门位置时不可通行。
        return !samePosition(new Vector2D(target.center().x(), target.center().y()), door.getPosition());
    }

    private PlayerKinematics dockedKinematics(long tick) {
        return new PlayerKinematics(tick, patrol.position().x(), patrol.position().y(),
                patrol.direction(), MovementState.DOCKED, ActorPhase.AVAILABLE, 0, false,
                AnimationState.DOCKED);
    }

    private PlayerFrame toFrame(PlayerKinematics k, InputIntent input) {
        boolean interacting = input.isPressed(LogicalKey.INTERACT);
        AnimationState animation = interacting
                ? AnimationState.INTERACTING
                : k.movementState() == MovementState.DOCKED ? AnimationState.DOCKED : AnimationState.MOVING;
        return new PlayerFrame(k.tick(), k.x(), k.y(), k.direction(), interacting,
                k.movementState(), k.actorPhase(), k.actorPhaseTicksRemaining(), animation);
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
     * <p>actor 归属：记录里写的是录制当时的活玩家（`player`），但**回放写入机关时必须改写为
     * `echo_&lt;sourceRound&gt;`** —— `AutoDockService.requireActor` 与
     * `DockingPlate.onEvent(ECHO_DISAPPEARED)` 都以该约定区分「活玩家 / 第 N 轮残影」；
     * 若继续传 `player`，残影占用会与活玩家无法区分，且残影消失时驻留板不会被释放。
     * 只改写写入机关的归属，**不改** `TimelineEvent` 记录内容，也不改 `replay/**` 类型。</p>
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

    /**
     * `E` 交互：需要宽容半径内 + 门已解锁 + 未触发。
     *
     * <p>半径来自机制侧（`ExitTerminal.interactRadiusForTileSize`，第一关 = 1.5 × 48 = 72），
     * app 不硬编码数值；6–10 tick 输入缓冲仍由 `C3DockController` 负责。</p>
     */
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
        // 边界刻 = 刚结束那轮的最后一刻；残影淘汰事件记在这一刻（与 TimelineEvent.tick 口径一致）
        long boundaryTick = clock.roundTick();
        Set<Integer> echoesBefore = activeEchoRounds();
        // 轮末事务：封装满长记录、生成残影、淘汰超龄残影、清空 autoDock 占用（注入的 C3 reset）。
        recording.completeNormalRound(
                () -> dockController.reset(AutoDockResetReason.ROUND_END, boundaryTick));
        // 残影淘汰派发（开发二 v4 卡 §七裁决）：replay 侧只暴露数据，派发在装配层做。
        // 必须用本装配自己的 eventBus —— Phase 1 之后板/门/出口只注册在它的实例上；
        // 顺序上放在机关重置之前，让残影占用经 DockingPlate.onEvent(ECHO_DISAPPEARED) 正规释放，
        // 而不是被随后的一揽子 reset 覆盖。
        for (int goneRound : evictedEchoRounds(echoesBefore)) {
            eventBus.dispatch(GameEvent.echoDisappeared(
                    ECHO_ACTOR_ID_PREFIX + goneRound, boundaryTick, goneRound));
        }
        leftPlate.reset();
        rightPlate.reset();
        door.reset();
        exit.reset();
        // READY -> PLAYING：不接回 PLAYING 的话 tick() 会直接 return，第 2 轮起角色完全无法移动。
        // README §三 要求的“短暂 READY 冻结”（玩家确认起始朝向）尚未实现，登记在 app 轮转待办里。
        clock.transition(GamePhase.PLAYING);
        // 轮初复位：恰好一次，把玩家放回出生节点中心与初始朝向（ENT-3 resetTo）
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
        if (leftPlate.getId().equals(mechanismId)) {
            return leftPlate;
        }
        if (rightPlate.getId().equals(mechanismId)) {
            return rightPlate;
        }
        return null;
    }

    private EntitySpawnInfo entity(String id) {
        return levelData.getEntitySpawnList().stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少第一关实体: " + id));
    }

    private static boolean samePosition(Vector2D left, Vector2D right) {
        return Math.abs(left.x() - right.x()) <= 1e-6 && Math.abs(left.y() - right.y()) <= 1e-6;
    }
}
