package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.entity.PatrolConfig;
import org.example.timeloop.entity.PatrolController;
import org.example.timeloop.level.Level02Corridor;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.ray.Ray;
import org.example.timeloop.mechanism.ray.RayFactory;
import org.example.timeloop.render.RenderViews;
import org.example.timeloop.replay.RoundClock;
import org.example.timeloop.replay.TickContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 第二关（低姿穿行 + 门房）的 app 集成装配 —— **L02 的唯一权威状态持有者**。
 *
 * <p><b>L02-B / B3 的职责边界</b>（交接书 §2 P3–P8）：</p>
 * <ol>
 *   <li><b>权威射线</b>：持有 {@code List<Ray>}（由 {@link RayFactory#buildFrom} 依据关卡数据构造），
 *       每个逻辑 tick **先**用<b>共享 {@code roundTick}</b> 调 {@link RayFactory#updateAll}，
 *       <b>再</b>推进时钟与生成该 tick 的 {@link RenderViews.Frame} —— 画面状态与判定状态同 tick，不提前/滞后。</li>
 *   <li><b>穷尽投影</b>：{@link #toRayBeam} 用**无 {@code default} 的穷尽 switch** 把
 *       {@link Ray.State} 映射为 {@link RenderViews.RayVisualState}；将来 mechanism 新增状态会在<b>编译期</b>暴露。</li>
 *   <li><b>不与 L1 共用任何状态</b>：本类自持关卡数据、时钟、占用注册表、事件总线、玩家与射线。</li>
 *   <li><b>多门</b>：L02 有两个门（房门 + 与出口同格的闸门），{@link #blockedByAnyDoor} 对**全部门**判定，
 *       两个门的解锁条件互不影响。</li>
 * </ol>
 *
 * <p><b>本切片范围</b>：B3 交付「射线生命周期 + 投影 + 同帧」与门的独立判定；
 * 录制 / 残影回放 / 驻留 / 轮末结算的完整玩法环（L1 装配里那一大段）属后续切片（B4/B6），
 * 本类当前只推进时钟与射线，不做玩家位移积分。</p>
 */
public final class Level02Assembly {

    /** 活玩家 actor 标识（与 L1 保持一致）。 */
    public static final String PLAYER_ACTOR_ID = "player";
    /** 活玩家 sourceRound 语义：0 = 活玩家。 */
    public static final int PLAYER_SOURCE_ROUND = 0;

    private final LevelData levelData;
    private final RoundClock clock;
    private final DockingPlateRegistry occupancy;
    private final EventDispatcher eventBus;
    private final List<Ray> rays;
    private final List<DockingPlate> plates = new ArrayList<>();
    private final List<Door> doors = new ArrayList<>();
    private final DockingPlate plateDoor;
    private final DockingPlate plateInner;
    private final DockingPlate plateMain;
    private final Door roomDoor;
    private final Door exitGate;
    private final ExitTerminal exit;
    private final PatrolController patrol;

    public Level02Assembly() {
        this.levelData = Level02Corridor.build();
        OrthogonalPathGraph graph =
                new PathGraphBridge(levelData.getPathNodes(), levelData.getTileSize()).toGraph();

        this.clock = new RoundClock(Math.toIntExact(levelData.getDurationTicks()),
                levelData.getMaxRounds());
        this.occupancy = new DockingPlateRegistry();
        this.eventBus = new EventDispatcher();

        // 三块驻留板：全部是普通驻留板（PLATE），不是锁存开关变体。
        this.plateDoor = buildPlate(entity("L02_plate_door"));
        this.plateInner = buildPlate(entity("L02_plate_inner"));
        this.plateMain = buildPlate(entity("L02_plate_main"));

        // 两个门：房门只受门外板控制；与出口同格的闸门受内板 + 主驻留板同时控制。
        this.roomDoor = buildDoor("L02_door_room");
        this.exitGate = buildDoor("L02_door_exit");

        this.exit = new ExitTerminal(entity("L02_exit_00").getId(), entity("L02_exit_00").getPos(),
                exitGate.getId(), ExitTerminal.interactRadiusForTileSize(levelData.getTileSize()),
                eventBus);

        this.rays = List.copyOf(RayFactory.buildFrom(levelData, eventBus));

        // 初始朝向从路径图推导（不硬编码关卡数据里的方向，避免与几何改动耦合）
        org.example.timeloop.core.path.PathNode spawnNode = graph.node(Level02Corridor.NODE_SPAWN);
        Direction initialDirection = java.util.Arrays.stream(Direction.values())
                .filter(candidate -> graph.neighbor(spawnNode, candidate).isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("L02 出生点没有可通行方向"));
        this.patrol = new PatrolController(graph, Level02Corridor.NODE_SPAWN, initialDirection,
                PatrolConfig.c2Greybox());
    }

    private DockingPlate buildPlate(EntitySpawnInfo info) {
        DockingPlate created = new DockingPlate(info.getId(), info.getPos(), occupancy, eventBus);
        plates.add(created);
        return created;
    }

    private Door buildDoor(String doorId) {
        DoorInfo info = levelData.getDoors().stream()
                .filter(candidate -> candidate.getId().equals(doorId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("关卡数据缺少门: " + doorId));
        Door created = new Door(info.getId(), info.getPosition(), info.getRequiredPlateIds(),
                occupancy, eventBus);
        doors.add(created);
        return created;
    }

    private EntitySpawnInfo entity(String entityId) {
        return levelData.getEntitySpawnList().stream()
                .filter(candidate -> candidate.getId().equals(entityId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("关卡数据缺少物件: " + entityId));
    }

    /** 进入第二关会话：BOOT → … → READY → PLAYING。 */
    public void start() {
        clock.transition(GamePhase.MENU);
        clock.transition(GamePhase.LEVEL_SELECT);
        clock.transition(GamePhase.READY);
        clock.transition(GamePhase.PLAYING);
    }

    /**
     * 推进一个逻辑 tick。
     *
     * <p><b>顺序冻结（P5）</b>：先用<b>当前</b>共享 {@code roundTick} 更新权威射线，再推进时钟；
     * 因此 {@link #renderViews()} 反映的永远是本 tick 结束后的权威状态（P6 同帧一致）。</p>
     */
    public void tick(InputIntent input) {
        Objects.requireNonNull(input, "input");
        RayFactory.updateAll(rays, clock.roundTick());
        if (clock.isPlaying()) {
            clock.advance();
        }
    }

    /** 本帧的只读渲染视图（含本 tick 的权威射线投影）。 */
    public RenderViews.Frame renderViews() {
        List<RenderViews.Mechanism> mechanisms = List.of(
                new RenderViews.Mechanism(plateDoor.getId(), plateDoor.getPosition().x(),
                        plateDoor.getPosition().y(), RenderViews.MechanismKind.PLATE,
                        plateDoor.isOccupied()),
                new RenderViews.Mechanism(plateInner.getId(), plateInner.getPosition().x(),
                        plateInner.getPosition().y(), RenderViews.MechanismKind.PLATE,
                        plateInner.isOccupied()),
                new RenderViews.Mechanism(plateMain.getId(), plateMain.getPosition().x(),
                        plateMain.getPosition().y(), RenderViews.MechanismKind.PLATE,
                        plateMain.isOccupied()),
                new RenderViews.Mechanism(roomDoor.getId(), roomDoor.getPosition().x(),
                        roomDoor.getPosition().y(), RenderViews.MechanismKind.DOOR,
                        roomDoor.isUnlocked()),
                new RenderViews.Mechanism(exit.getId(), exit.getPosition().x(),
                        exit.getPosition().y(), RenderViews.MechanismKind.EXIT,
                        exit.isDoorUnlocked()));

        List<RenderViews.RayBeam> rayBeams = rays.stream()
                .map(Level02Assembly::toRayBeam)
                .toList();

        RenderViews.Player playerView = new RenderViews.Player(
                patrol.position().x(), patrol.position().y(), patrol.direction(),
                MovementState.IDLE, false);

        return new RenderViews.Frame(playerView, mechanisms, List.of(), rayBeams);
    }

    /** {@code Ray.State -> RayVisualState} 的**穷尽**映射（禁止 {@code default}；新增状态会在编译期暴露）。 */
    private static RenderViews.RayBeam toRayBeam(Ray ray) {
        RenderViews.RayVisualState state = switch (ray.getState()) {
            case OFF -> RenderViews.RayVisualState.OFF;
            case WARNING -> RenderViews.RayVisualState.WARNING;
            case ACTIVE -> RenderViews.RayVisualState.ACTIVE;
        };
        return new RenderViews.RayBeam(ray.getId(),
                ray.getStart().x(), ray.getStart().y(),
                ray.getEnd().x(), ray.getEnd().y(), state);
    }

    /**
     * 目标节点是否被**任意**门挡着（多门判定；L02 有房门与出口闸门两个门）。
     *
     * <p>与 L1 的单门判定等价推广：门未解锁时挡住门自己那一格；任一门挡路即不可通行。</p>
     */
    public boolean blockedByAnyDoor(double targetX, double targetY) {
        for (Door candidate : doors) {
            if (candidate.isUnlocked()) {
                continue;
            }
            if (samePosition(targetX, targetY, candidate.getPosition().x(),
                    candidate.getPosition().y())) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePosition(double ax, double ay, double bx, double by) {
        return Math.abs(ax - bx) < 1e-9 && Math.abs(ay - by) < 1e-9;
    }

    /** 权威射线（只读；供集成测试与诊断）。 */
    public List<Ray> rays() {
        return rays;
    }

    public GamePhase phase() {
        return clock.phase();
    }

    public boolean isPlaying() {
        return clock.isPlaying();
    }

    public TickContext hudContext() {
        return clock.toContext();
    }

    public DockingPlate plate(String plateId) {
        return plates.stream()
                .filter(candidate -> candidate.getId().equals(plateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知驻留板: " + plateId));
    }

    public Door door(String doorId) {
        return doors.stream()
                .filter(candidate -> candidate.getId().equals(doorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知门: " + doorId));
    }

    public ExitTerminal exit() {
        return exit;
    }

    public GameEventBus eventBus() {
        return eventBus;
    }

    /** 释放机关与事件订阅（避免观察者残留）。 */
    public void cleanup() {
        plates.forEach(DockingPlate::dispose);
        doors.forEach(Door::dispose);
        exit.dispose();
        RayFactory.disposeAll(rays);
    }
}
