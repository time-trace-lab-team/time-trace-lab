package org.example.timeloop.level;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.GamePhase;
import org.example.timeloop.core.MovementState;
import org.example.timeloop.core.PlayerKinematics;
import org.example.timeloop.core.TickStepResult;
import org.example.timeloop.core.path.ExitPassability;
import org.example.timeloop.core.path.OrthogonalPathGraph;
import org.example.timeloop.core.path.PathExit;
import org.example.timeloop.core.path.PathPoint;
import org.example.timeloop.entity.PatrolConfig;
import org.example.timeloop.entity.PatrolController;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockResult;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.replay.EchoQueue;
import org.example.timeloop.replay.EchoState;
import org.example.timeloop.replay.PlayerFrame;
import org.example.timeloop.replay.RecordingSession;
import org.example.timeloop.replay.RoundClock;
import org.example.timeloop.replay.TimelineEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X-MOVE-COLLAPSE-01 L-2 · 第一关在「玩家驱动四方向移动」下的两轮通关模拟（headless）。
 *
 * <p>用冻结的移动参数（{@code baseSpeed = 2}、{@code tileSize = 48}、节点中心提交转向、真死路允许掉头）
 * 逐刻驱动 {@link PatrolController}，并按 spec 在模拟内提交 autoDock 边沿：进入只发生在
 * {@code outside → inside} 边沿；离开请求在按下合法出口方向的那一刻发出。</p>
 *
 * <p><b>本测试对 L-1（{@code ENT-2a}）的落地与否都成立</b>：离开刻若机制返回 {@code LEFT} 则同刻释放；
 * 若机制仍是旧语义（{@code NOT_OUTSIDE_REGION}）则继续沿该方向走出区域、在出界刻释放。
 * 因此它只断言<b>关卡可通关</b>这一结果，不断言释放刻——释放刻的契约由 L-1 自己的用例覆盖。</p>
 *
 * <p>路线全程只按显式方向输入推进，<b>不读取任何 {@code defaultExit}</b>；
 * 本分支基于 {@code develop}，因此不断言关卡数据的 defaultExit 现状（那是 L-4a 的范围）。</p>
 *
 * <p>第 1 轮：出生点 → 分叉 → 左驻留板（停留）→ 离开释放 → 回分叉 → 右驻留板（停留到轮末）；
 * 第 2 轮：E1 按第 1 轮记录占左板 + 当前玩家占右板 → 门解锁 → 玩家在右板按 E 结算。</p>
 */
class Level01TwoRoundSimulationTest {

    private static final double TILE = 48.0;
    private static final double EPS = 1e-6;
    private static final String PLAYER = "player";
    private static final int ROUTE_GUARD_TICKS = 3000;
    private static final int LEFT_PLATE_DWELL_TICKS = 200;

    @BeforeEach
    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void level01IsSolvableInTwoRoundsUnderFreeMove() {
        Sim sim = new Sim(Level01Footsteps.build());

        // ---------- 第 1 轮：为第 2 轮铺一块板 ----------
        sim.advanceUntil("fork", Direction.DOWN, () -> sim.py() >= 168.0 - EPS);
        sim.advanceUntil("left_turn", Direction.LEFT, () -> sim.px() <= 120.0 + EPS);
        sim.advanceUntil("left_plate_settled", Direction.DOWN, () -> sim.py() >= 264.0 - EPS);
        assertTrue(sim.docked, "到达左驻留板后应处于驻留状态");
        assertTrue(sim.leftPlate.isOccupied(), "左驻留板应被当前玩家占用");
        assertFalse(sim.door.isUnlocked(), "只有一块板被占用时门必须仍关闭");

        sim.dwellUntilTick(sim.lastTick + LEFT_PLATE_DWELL_TICKS, "left_plate_dwell_end");

        // 离开驻留板：按下合法出口（左端节点唯一合法出口为 UP）
        sim.advanceUntil("left_plate_release", Direction.UP, () -> !sim.leftPlate.isOccupied());
        assertFalse(sim.docked, "离开后不应仍处于驻留状态");
        assertFalse(sim.leftPlate.isOccupied(), "离开驻留板必须释放占用");
        assertFalse(sim.door.isUnlocked(), "释放后没有任何板被占用，门必须回到关闭");

        sim.advanceUntil("left_turn_back", Direction.UP, () -> sim.py() <= 168.0 + EPS);
        sim.advanceUntil("fork_back", Direction.RIGHT, () -> sim.px() >= 264.0 - EPS);
        sim.advanceUntil("right_turn", Direction.RIGHT, () -> sim.px() >= 408.0 - EPS);
        sim.advanceUntil("right_plate_settled", Direction.DOWN, () -> sim.py() >= 264.0 - EPS);
        assertTrue(sim.rightPlate.isOccupied(), "第 1 轮结束时右驻留板应由当前玩家占用");

        long roundOneEnd = sim.runToRoundEnd("round1_end");
        assertEquals(959L, roundOneEnd, "第 1 轮应在 roundTick = D-1 = 959 结束");
        assertEquals(2, sim.clock.currentRound(), "普通轮末事务后应进入第 2 轮");

        // ---------- 第 2 轮：E1 占左板 + 当前玩家占右板 → 门开 → 出口结算 ----------
        sim.advanceUntil("r2_fork", Direction.DOWN, () -> sim.py() >= 168.0 - EPS);
        sim.advanceUntil("r2_right_turn", Direction.RIGHT, () -> sim.px() >= 408.0 - EPS);
        sim.advanceUntil("r2_right_plate_settled", Direction.DOWN, () -> sim.py() >= 264.0 - EPS);

        assertTrue(sim.rightPlate.isOccupied(), "第 2 轮当前玩家应占用右驻留板");
        assertEquals(PLAYER, sim.rightPlate.getOccupantId(), "右板占用者必须是当前玩家");
        assertTrue(sim.leftPlate.isOccupied(), "左板应由第 1 轮残影 E1 复现占用");
        assertEquals(1, sim.leftPlate.getOccupantSourceRound(), "左板占用者必须是 sourceRound = 1 的残影");
        assertTrue(sim.door.isUnlocked(), "两板同时占用时门必须解锁");
        assertTrue(sim.exit.isDoorUnlocked(), "门解锁后出口终端应获得权限");

        // 出口终端与右驻留板中心恰好相距 1 格（L-3 半径硬下限的几何依据）
        double distance = Math.hypot(sim.exit.getPosition().x() - sim.rightPlate.getPosition().x(),
                sim.exit.getPosition().y() - sim.rightPlate.getPosition().y());
        assertEquals(TILE, distance, 1e-9, "右驻留板中心与出口终端必须相距 1 格");

        long interactTick = sim.lastTick;
        sim.arrivals.put("r2_exit_settled", interactTick);
        assertTrue(sim.exit.interact(interactTick, 0), "门解锁后当前玩家应能触发出口");
        assertFalse(sim.exit.interact(interactTick + 1, 0), "出口不得重复触发");
        sim.recording.completeGoal();
        sim.arrivals.put("r2_exit_triggered", interactTick);

        assertEquals(GamePhase.RESULT, sim.clock.phase(), "触发出口后应进入 RESULT");
        assertEquals(2, sim.clock.currentRound(), "通关发生在第 2 轮");

        sim.printArrivalTable();
    }

    // ======================================================================
    // 模拟器：逐刻驱动「输入 → autoDock 边沿 → 运动 → 记录」
    // ======================================================================

    private static final class Sim {

        final LevelData levelData;
        final AutoDockService autoDock;
        final OrthogonalPathGraph graph;
        PatrolController patrol;
        final DockingPlate leftPlate;
        final DockingPlate rightPlate;
        final Door door;
        final ExitTerminal exit;
        final RoundClock clock;
        final EchoQueue echoQueue;
        final RecordingSession recording;

        final Map<String, Long> arrivals = new LinkedHashMap<>();

        long lastTick;
        boolean docked;
        private String dockedId;
        private Direction departure;
        /** 前一 tick 的采样是否位于某个 autoDock 区域内（进入必须满足 outside → inside 边沿）。 */
        private boolean insidePreviousSample;

        Sim(LevelData levelData) {
            this.levelData = levelData;
            this.autoDock = new AutoDockService(levelData);
            this.graph = buildGraph(levelData);
            this.patrol = new PatrolController(graph, "L01_node_spawn", Direction.DOWN, PatrolConfig.c2Greybox());

            EntitySpawnInfo leftInfo = entity("L01_plate_left");
            EntitySpawnInfo rightInfo = entity("L01_plate_right");
            EntitySpawnInfo exitInfo = entity("L01_exit_00");
            DoorInfo doorInfo = levelData.getDoors().get(0);

            this.leftPlate = new DockingPlate(leftInfo.getId(), leftInfo.getPos());
            this.rightPlate = new DockingPlate(rightInfo.getId(), rightInfo.getPos());
            this.door = new Door(doorInfo.getId(), doorInfo.getPosition(), doorInfo.getRequiredPlateIds());
            this.exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), door.getId());

            this.clock = new RoundClock((int) levelData.getDurationTicks(), levelData.getMaxRounds());
            this.echoQueue = new EchoQueue(levelData.getEchoLifeL());
            this.recording = new RecordingSession(clock, echoQueue);

            // BOOT → MENU → LEVEL_SELECT → READY → PLAYING（与集成层同序）
            clock.transition(GamePhase.MENU);
            clock.transition(GamePhase.LEVEL_SELECT);
            clock.transition(GamePhase.READY);
            recording.beginRound();
            clock.transition(GamePhase.PLAYING);
        }

        double px() {
            return patrol.position().x();
        }

        double py() {
            return patrol.position().y();
        }

        /** 按住 {@code dir} 直到条件满足；第一次调用产生一个新的方向边沿。 */
        void advanceUntil(String label, Direction dir, Condition condition) {
            int guard = 0;
            boolean first = true;
            while (!condition.met() && guard++ < ROUTE_GUARD_TICKS) {
                tick(dir, first);
                first = false;
            }
            if (guard >= ROUTE_GUARD_TICKS) {
                throw new AssertionError("路线超时：" + label + " 在 " + ROUTE_GUARD_TICKS
                        + " 刻内未达成，当前位置 " + patrol.position() + "，docked=" + docked);
            }
            arrivals.put(label, lastTick);
        }

        /** 无输入停留到指定刻（用于驻留段）。 */
        void dwellUntilTick(long targetTick, String label) {
            int guard = 0;
            while (clock.roundTick() < targetTick && guard++ < ROUTE_GUARD_TICKS) {
                tick(null, false);
            }
            arrivals.put(label, lastTick);
        }

        /** 一直空转到本轮轮末（驻留中），返回末刻 tick。 */
        long runToRoundEnd(String label) {
            int round = clock.currentRound();
            int guard = 0;
            while (clock.currentRound() == round && guard++ < ROUTE_GUARD_TICKS) {
                tick(null, false);
            }
            arrivals.put(label, lastTick);
            return lastTick;
        }

        private void tick(Direction desired, boolean newDirectionEdge) {
            long tick = clock.roundTick();
            Vector2D before = new Vector2D(px(), py());

            replayEchoEvents(tick);

            boolean insideNow = autoDock.findNearest(before, 0.0).isPresent();

            // 1) 进入边沿：前一采样在区域外、本刻采样在区域内
            if (!docked && !insidePreviousSample && insideNow) {
                Optional<AutoDockView> candidate = autoDock.findNearest(before, 0.0);
                if (candidate.isPresent()) {
                    String id = candidate.get().mechanismId();
                    AutoDockResult entered = autoDock.tryEnter(id, PLAYER, 0, tick, before);
                    if (entered.status() == AutoDockResult.Status.ENTERED) {
                        docked = true;
                        dockedId = id;
                        departure = null;
                        assertTrue(plateById(id).tryEnter(PLAYER, 0, tick), "机关侧应接受本次进入");
                        record(new TimelineEvent(tick, PLAYER, 0, id,
                                TimelineEvent.EventType.DOCK_ENTERED, null, null));
                    }
                }
            }

            // 2) 离开请求：按下合法出口方向的边沿
            if (docked && departure == null && desired != null && newDirectionEdge
                    && isLegalExit(dockedId, desired)) {
                departure = desired;
            }

            // 3) 离开结算：新语义同刻 LEFT；旧语义在出界刻 LEFT，期间继续沿该方向移动
            Direction moveDirection;
            if (docked && departure != null) {
                moveDirection = departure;
                AutoDockResult leave = autoDock.tryLeave(
                        dockedId, PLAYER, 0, tick, toDir(departure), before);
                if (leave.status() == AutoDockResult.Status.LEFT) {
                    assertTrue(plateById(dockedId).tryExit(PLAYER, 0, tick), "机关侧应接受本次离开");
                    record(new TimelineEvent(tick, PLAYER, 0, dockedId,
                            TimelineEvent.EventType.DOCK_LEFT, departure, "NEW_DIRECTION"));
                    docked = false;
                    dockedId = null;
                    departure = null;
                }
            } else if (docked) {
                // 驻留中：先沿中心线走到机关中心，之后冻结
                Vector2D center = autoDock.findById(dockedId).orElseThrow().center();
                moveDirection = (Math.abs(center.x() - before.x()) <= EPS
                        && Math.abs(center.y() - before.y()) <= EPS)
                        ? null
                        : directionToward(before, center);
            } else {
                moveDirection = desired;
            }

            // 4) 运动
            PlayerKinematics kinematics = moveDirection == null
                    ? patrol.advance(tick, Set.of(), Optional.empty(), passability())
                    : patrol.advance(tick, Set.of(moveDirection),
                            Optional.of(moveDirection), passability());

            // 5) 记录本刻规范帧
            AnimationState animation = kinematics.movementState() == MovementState.DOCKED
                    ? AnimationState.DOCKED
                    : AnimationState.MOVING;
            recording.recordFrame(new PlayerFrame(
                    tick, kinematics.x(), kinematics.y(), kinematics.direction(), false,
                    kinematics.movementState(), kinematics.actorPhase(),
                    kinematics.actorPhaseTicksRemaining(), animation));

            lastTick = tick;
            insidePreviousSample = insideNow;

            // 6) 时钟推进与轮末事务
            TickStepResult step = clock.advance();
            if (step == TickStepResult.ROUND_END) {
                leftPlate.reset();
                rightPlate.reset();
                door.reset();
                exit.reset();
                recording.completeNormalRound(
                        () -> autoDock.reset(AutoDockResetReason.ROUND_END, clock.roundTick()));
                // READY → PLAYING（集成层轮转接线）
                clock.transition(GamePhase.PLAYING);
                // 轮内玩家状态由开发一各自重置（README §九）
                patrol = new PatrolController(
                        graph, "L01_node_spawn", Direction.DOWN, PatrolConfig.c2Greybox());
                docked = false;
                dockedId = null;
                departure = null;
                insidePreviousSample = false;
            }
        }

        /**
         * 残影按记录事件复现驻留：只写机关侧占用，不重新执行输入或移动。
         *
         * <p>回放时 actor 身份按 autoDock 规格冻结 §2.1 归属为 {@code echo_<sourceRound>}，
         * 而不是沿用录制时当前玩家的 {@code player}/{@code 0}。</p>
         */
        private void replayEchoEvents(long tick) {
            for (EchoState echo : echoQueue.activeEchoes(clock.currentRound())) {
                String actorId = "echo_" + echo.sourceRound();
                int sourceRound = echo.sourceRound();
                for (TimelineEvent event : echo.eventsAt(tick)) {
                    DockingPlate plate = plateById(event.mechanismId());
                    if (plate == null) {
                        continue;
                    }
                    if (event.eventType() == TimelineEvent.EventType.DOCK_ENTERED) {
                        plate.tryEnter(actorId, sourceRound, event.tick());
                    } else {
                        plate.tryExit(actorId, sourceRound, event.tick());
                    }
                }
            }
        }

        private void record(TimelineEvent event) {
            recording.recordEvent(event);
        }

        private ExitPassability passability() {
            return this::isPassable;
        }

        private boolean isPassable(org.example.timeloop.core.path.PathNode from,
                                   PathExit exitEdge,
                                   org.example.timeloop.core.path.PathNode target) {
            if (door.isUnlocked()) {
                return true;
            }
            // 关闭的门挡在门所在节点：目标节点世界坐标等于门位置时不可通行
            return !samePosition(target.center().x(), target.center().y(),
                    door.getPosition().x(), door.getPosition().y());
        }

        private boolean isLegalExit(String mechanismId, Direction direction) {
            return autoDock.findById(mechanismId)
                    .map(view -> view.legalExitDirections().contains(toDir(direction)))
                    .orElse(false);
        }

        private DockingPlate plateById(String mechanismId) {
            if (leftPlate.getId().equals(mechanismId)) {
                return leftPlate;
            }
            if (rightPlate.getId().equals(mechanismId)) {
                return rightPlate;
            }
            throw new AssertionError("未知机关: " + mechanismId);
        }

        private EntitySpawnInfo entity(String id) {
            return levelData.getEntitySpawnList().stream()
                    .filter(candidate -> id.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("缺少第一关实体: " + id));
        }

        void printArrivalTable() {
            System.out.println("[X-MOVE-COLLAPSE-01 L-2] 第一关两轮到达刻表（roundTick 0..959）");
            arrivals.forEach((label, tick) ->
                    System.out.printf("  %-26s tick=%-4d%n", label, tick));
        }
    }

    // ======================================================================
    // 关卡数据 → 运动图（等价于集成层 PathGraphBridge 的 1 格邻接规则）
    // ======================================================================

    private static OrthogonalPathGraph buildGraph(LevelData level) {
        List<org.example.timeloop.level.model.PathNode> levelNodes = level.getPathNodes();
        List<org.example.timeloop.core.path.PathNode> nodes = new ArrayList<>(levelNodes.size());
        for (org.example.timeloop.level.model.PathNode node : levelNodes) {
            List<PathExit> exits = new ArrayList<>();
            for (org.example.timeloop.level.model.PathNode.Dir dir : node.getAllowDirs()) {
                String neighborId = findNeighborId(levelNodes, node, dir, level.getTileSize());
                if (neighborId != null) {
                    exits.add(new PathExit(Direction.valueOf(dir.name()), neighborId));
                }
            }
            nodes.add(new org.example.timeloop.core.path.PathNode(
                    node.getId(),
                    new PathPoint(node.getWorldPos().x(), node.getWorldPos().y()),
                    exits,
                    Optional.empty()));
        }
        return new OrthogonalPathGraph(nodes);
    }

    private static String findNeighborId(List<org.example.timeloop.level.model.PathNode> nodes,
                                        org.example.timeloop.level.model.PathNode node,
                                        org.example.timeloop.level.model.PathNode.Dir dir,
                                        double tileSize) {
        double targetX = node.getWorldPos().x();
        double targetY = node.getWorldPos().y();
        switch (dir) {
            case UP -> targetY -= tileSize;
            case DOWN -> targetY += tileSize;
            case LEFT -> targetX -= tileSize;
            case RIGHT -> targetX += tileSize;
        }
        double threshold = tileSize * 0.1;
        for (org.example.timeloop.level.model.PathNode candidate : nodes) {
            if (candidate.getId().equals(node.getId())) {
                continue;
            }
            double dx = candidate.getWorldPos().x() - targetX;
            double dy = candidate.getWorldPos().y() - targetY;
            if (dx * dx + dy * dy < threshold * threshold) {
                return candidate.getId();
            }
        }
        return null;
    }

    private static Direction directionToward(Vector2D from, Vector2D to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        }
        return dy > 0 ? Direction.DOWN : Direction.UP;
    }

    private static org.example.timeloop.level.model.PathNode.Dir toDir(Direction direction) {
        return org.example.timeloop.level.model.PathNode.Dir.valueOf(direction.name());
    }

    private static boolean samePosition(double x1, double y1, double x2, double y2) {
        return Math.abs(x1 - x2) <= 1e-6 && Math.abs(y1 - y2) <= 1e-6;
    }

    @FunctionalInterface
    private interface Condition {
        boolean met();
    }
}
