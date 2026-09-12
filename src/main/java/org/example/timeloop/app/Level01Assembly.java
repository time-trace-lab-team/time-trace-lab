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
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.mechanism.event.EventDispatcher;
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

    private final List<TimelineEvent> events = new ArrayList<>();
    private PlayerFrame lastFrame;
    private long lastTick = -1;

    public Level01Assembly() {
        this.levelData = Level01Footsteps.build();
        new LevelGeometryImpl(levelData); // 构造即校验关卡几何（W2 后应通过）
        this.autoDock = new AutoDockService(levelData);

        OrthogonalPathGraph graph =
                new PathGraphBridge(levelData.getPathNodes(), levelData.getTileSize()).toGraph();
        this.patrol = new PatrolController(graph, "L01_node_spawn", Direction.DOWN, PatrolConfig.c2Greybox());
        this.dockController = new C3DockController(autoDock, autoDock, PLAYER_ACTOR_ID, PLAYER_SOURCE_ROUND);

        this.clock = new RoundClock(Math.toIntExact(levelData.getDurationTicks()), levelData.getMaxRounds());
        this.echoQueue = new EchoQueue(levelData.getEchoLifeL());
        this.recording = new RecordingSession(clock, echoQueue);
        this.replayPort = new ReplayPort(clock, recording);

        EntitySpawnInfo leftInfo = entity("L01_plate_left");
        EntitySpawnInfo rightInfo = entity("L01_plate_right");
        EntitySpawnInfo exitInfo = entity("L01_exit_00");
        DoorInfo doorInfo = levelData.getDoors().get(0);

        this.leftPlate = new DockingPlate(leftInfo.getId(), leftInfo.getPos());
        this.rightPlate = new DockingPlate(rightInfo.getId(), rightInfo.getPos());
        this.door = new Door(doorInfo.getId(), doorInfo.getPosition(), doorInfo.getRequiredPlateIds());
        this.exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), door.getId(),
                ExitTerminal.interactRadiusForTileSize(levelData.getTileSize()));
    }

    /** 进入第一关会话：BOOT → … → READY → 复位玩家 → 开本轮缓冲 → PLAYING。 */
    public void start() {
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        resetPlayerForNewRound();
        recording.beginRound();
        clock.transition(GamePhase.PLAYING);
    }

    public boolean isPlaying() {
        return clock.isPlaying();
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

    /** 推进一个逻辑刻：输入 → autoDock 决策 → 巡行/驻留 → 记录帧 → 事件 → 时钟推进。 */
    public void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        if (!clock.isPlaying()) {
            return;
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
                        patrol.direction(), MovementState.CRUISING, false)
                : new RenderViews.Player(player.x(), player.y(), player.direction(),
                        player.movementState(), player.isPhaseDodging());

        List<RenderViews.Mechanism> mechanisms = List.of(
                new RenderViews.Mechanism(leftPlate.getId(), leftPlate.getPosition().x(),
                        leftPlate.getPosition().y(), RenderViews.MechanismKind.PLATE, leftPlate.isOccupied()),
                new RenderViews.Mechanism(rightPlate.getId(), rightPlate.getPosition().x(),
                        rightPlate.getPosition().y(), RenderViews.MechanismKind.PLATE, rightPlate.isOccupied()),
                new RenderViews.Mechanism(door.getId(), door.getPosition().x(),
                        door.getPosition().y(), RenderViews.MechanismKind.DOOR, door.isUnlocked()),
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

    /** 关闭/退出清理：释放机关占用并清空全局注册表，避免旧会话泄漏。 */
    public void cleanup() {
        leftPlate.dispose();
        rightPlate.dispose();
        door.dispose();
        exit.dispose();
        DockingPlateRegistry.getInstance().clear();
        EventDispatcher.getInstance().clear();
    }

    // ---------- 内部 ----------

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
        leftPlate.reset();
        rightPlate.reset();
        door.reset();
        exit.reset();
        // 轮末事务：封装满长记录、生成残影、清空 autoDock 占用（走 C3 的 reset 以便同时清掉它的本地驻留状态），
        // 事务结束时时钟停在 READY（见 docs/development 的开发二 R-3 契约）。
        recording.completeNormalRound(
                () -> dockController.reset(AutoDockResetReason.ROUND_END, clock.roundTick()));
        // READY -> PLAYING：不接回 PLAYING 的话 tick() 会直接 return，第 2 轮起角色完全无法移动。
        // README §三 要求的“短暂 READY 冻结”（玩家确认起始朝向）尚未实现，登记在 app 轮转待办里。
        clock.transition(GamePhase.PLAYING);
        // 轮初复位：恰好一次，把玩家放回出生节点中心与初始朝向（ENT-3 resetTo）
        resetPlayerForNewRound();
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
