package org.example.timeloop.mechanism;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.entity.C3DockController;
import org.example.timeloop.entity.C3DockDecision;
import org.example.timeloop.level.Level03Pursuit;
import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.autodock.DockRegionView;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.replay.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务卡 {@code TASK-DEV3-L03-PLATE-C-NO-RESPONSE} 验收 §6.2 / §6.3 的必加回归用例。
 *
 * <p><b>任务卡现象</b>：第三关 C 板 {@code L03_plate_c (10,2)}「玩家站上去没有任何反应
 * （不停驻、不占板、门 C 不开），但残影走上去有反应（能占板、门 C 会开）」。</p>
 *
 * <p><b>根因（实测结论，与任务卡 §2 的猜测方向不同）</b>：玩家侧的位置判定链路本身是好的 ——
 * C 板的 {@code pos} 与路径节点 {@code L03_node_c10_r2} 严格同格、判定区域就是整格
 * （边长 {@code tileSize}）、四块板用的是同一套规则。真正的缺陷在<b>机关侧的占用模型</b>：
 * {@link DockingPlate} 只有一个占用槽位，第二个踩上来的人被 {@code tryEnter} 返回 {@code false}
 * 静默丢弃，此后「先占槽位者」一离开 / 一消散，整块板被清空、门当刻回锁 ——
 * 于是「残影能占、门 C 会开」（残影就是先占槽位者），而「玩家站上去没反应」（玩家是被丢弃的第二个）。
 * 该缺陷已在 L03-DEV3 多占用改造中修掉，本用例把它钉死。</p>
 *
 * <p><b>为什么两条路径必须逐字段对账</b>：玩家走的是
 * {@code C3DockController → AutoDockService.region().contains(实时位置)} 的位置链路，
 * 残影走的是回放里已固化的 {@code DOCK_ENTERED} 事件链路。两条链路的判定输入完全不同
 * （连续世界坐标 vs 离散事件刻），任一环节不匹配都会表现成「同一块板、两种结果」。</p>
 *
 * <p><b>范围</b>：本用例只依赖 {@code mechanism/**}（{@link DockingPlate} / {@link Door} /
 * {@link AutoDockService}）、{@code level/**}（{@link Level03Pursuit} 真实关卡数据）与只读的
 * {@code entity.C3DockController}（玩家侧位置判定链路的真身；不改它），符合任务卡 §4 的允许范围。
 * {@link #mirrorDockEvents} 与装配层 {@code Level03Assembly.mirrorDockEvents} 同构，
 * 这里复制这几行是为了让用例不越界到 {@code app/**}。</p>
 */
class PlatePathConsistencyTest {

    /**
     * 第三关重排 v3 的「板 → 它作用的那扇门」对照表。
     *
     * <p>开门的 A/B/C 板各自对应一扇门；终点组的 K 板与 S₂/S₃ 两个开关共用终点供能闸，
     * 因此检验其中一块时，要先在两条路径上把另外两块<b>同样</b>满足掉，否则门条件本来就差一块，
     * 分不出「路径不一致」还是「条件没凑齐」。</p>
     */
    private static final List<PlatePair> PLATE_DOOR_PAIRS = List.of(
            new PlatePair(Level03Pursuit.PLATE_A, Level03Pursuit.DOOR_A, List.of()),
            new PlatePair(Level03Pursuit.PLATE_C, Level03Pursuit.DOOR_C, List.of()),
            new PlatePair(Level03Pursuit.PLATE_B, Level03Pursuit.DOOR_B, List.of()),
            new PlatePair(Level03Pursuit.PLATE_K, Level03Pursuit.DOOR_EXIT,
                    List.of(Level03Pursuit.SWITCH_S2, Level03Pursuit.SWITCH_S3)),
            new PlatePair(Level03Pursuit.SWITCH_S2, Level03Pursuit.DOOR_EXIT,
                    List.of(Level03Pursuit.SWITCH_S3, Level03Pursuit.PLATE_K)),
            new PlatePair(Level03Pursuit.SWITCH_S3, Level03Pursuit.DOOR_EXIT,
                    List.of(Level03Pursuit.SWITCH_S2, Level03Pursuit.PLATE_K)));

    /** 测试用的一条对照：待测板 + 它作用的那扇门 + 检验门之前要先满足掉的其它板。 */
    private record PlatePair(String plateId, String doorId, List<String> prepare) {
    }

    private static final Map<String, int[]> CELL_OF_PLATE = Map.of(
            Level03Pursuit.PLATE_A, Level03Pursuit.CELL_PLATE_A,
            Level03Pursuit.PLATE_B, Level03Pursuit.CELL_PLATE_B,
            Level03Pursuit.PLATE_C, Level03Pursuit.CELL_PLATE_C,
            Level03Pursuit.PLATE_K, Level03Pursuit.CELL_PLATE_K,
            Level03Pursuit.SWITCH_S2, Level03Pursuit.CELL_SWITCH_S2,
            Level03Pursuit.SWITCH_S3, Level03Pursuit.CELL_SWITCH_S3);

    // ---------- §6.2 主用例：两条路径同一块板必须同结果 ----------

    private static final String PLAYER = "player";
    private static final String ECHO = "echo_1";

    /**
     * 四块板逐一走两条路径：**玩家真走到**（位置链路）与**残影回放**（事件链路），
     * 并且把四种到达顺序都跑一遍（只玩家 / 只残影 / 残影先占再玩家走到 / 玩家站定再残影回放），
     * 占用结果与门结果必须完全一致。
     *
     * <p>后两种顺序才是任务卡的现场。旧单槽位模型下，第二个踩上来的人被静默丢弃，
     * 于是先占者一离开整块板就被清空、门当刻回锁 —— 本用例在
     * {@link #assertRemainingActorKeepsThePlateOccupied} 这一步会红。</p>
     */
    @Test
    void playerWalkAndEchoReplayAgreeOnEveryPlateInEveryArrivalOrder() {
        for (PlatePair pair : PLATE_DOOR_PAIRS) {
            String plateId = pair.plateId();
            String doorId = pair.doorId();
            List<Vector2D> walk = walkPath(Level03Pursuit.SPAWN_CELL, CELL_OF_PLATE.get(plateId),
                    otherPlateCells(plateId));

            // ---- ① 只玩家：真走到板格（真实 2px/刻 位置流 → C3DockController → AutoDockService）----
            Fixture playerRun = new Fixture();
            prepareGate(playerRun, pair.prepare());
            assertEquals(plateId, walkAndDock(playerRun, walk),
                    plateId + "：玩家沿真实路线走到板格必须停在它上面（位置判定链路）");
            Outcome playerAlone = playerRun.outcome(plateId, doorId);
            assertTrue(playerAlone.occupied(), plateId + "：玩家踩上去必须占板（任务卡 §6.1）");
            assertTrue(playerAlone.doorUnlocked(),
                    plateId + "：玩家踩上去必须让它作用的那扇门解锁（任务卡 §6.1）");
            assertEquals(List.of(PLAYER), playerRun.plate(plateId).getOccupantIds());
            assertEquals(1, playerAlone.occupantCount());
            // 玩家按合法出口方向离开
            leave(playerRun, plateId, walk.size() + 1L);
            Outcome playerReleased = playerRun.outcome(plateId, doorId);
            boolean playerLatched = playerRun.plate(plateId).isLatched();
            if (playerLatched) {
                assertTrue(playerReleased.occupied(),
                        plateId + "：锁存开关人走条件仍成立");
            } else {
                assertFalse(playerReleased.occupied(), plateId + "：普通板人走即释放");
                assertFalse(playerReleased.doorUnlocked(), plateId + "：普通板释放后门回锁");
            }
            playerRun.close();

            // ---- ② 只残影：回放一条 DOCK_ENTERED（事件链路）----
            Fixture echoRun = new Fixture();
            prepareGate(echoRun, pair.prepare());
            assertTrue(echoRun.plate(plateId).tryEnter(ECHO, 1, 600L),
                    plateId + "：残影事件必须能占板");
            Outcome echoAlone = echoRun.outcome(plateId, doorId);
            assertEquals(1, echoAlone.occupantCount());
            assertEquals(List.of(ECHO), echoRun.plate(plateId).getOccupantIds());

            // ---- 对账：两条路径的每一个可观察字段都必须相同（占用者身份除外）----
            assertEquals(playerAlone.occupied(), echoAlone.occupied(),
                    plateId + "：玩家路径与残影路径的占用结果必须一致");
            assertEquals(playerAlone.doorUnlocked(), echoAlone.doorUnlocked(),
                    plateId + "：玩家路径与残影路径的门结果必须一致");
            assertEquals(playerAlone.registryOccupied(), echoAlone.registryOccupied(),
                    plateId + "：占用注册表看到的门条件必须一致");
            assertEquals(playerAlone.state(), echoAlone.state(),
                    plateId + "：板状态枚举必须一致");

            // 残影也离开：两条路径的释放/锁存态必须逐字段相同
            assertTrue(echoRun.plate(plateId).tryExit(ECHO, 1, 700L), "残影离开");
            Outcome echoReleased = echoRun.outcome(plateId, doorId);
            assertEquals(playerReleased, echoReleased,
                    plateId + "：两条路径离开之后的每一个字段都必须相同");
            assertEquals(playerLatched, echoRun.plate(plateId).isLatched(),
                    plateId + "：锁存态也必须一致");
            echoRun.close();

            // ---- ③ 残影先占 → 玩家真走到（任务卡现场：残影能占、玩家站上去也得有反应）----
            Fixture echoFirst = new Fixture();
            prepareGate(echoFirst, pair.prepare());
            assertTrue(echoFirst.plate(plateId).tryEnter(ECHO, 1, 600L), "夹具前提：残影先压住板");
            assertEquals(plateId, walkAndDock(echoFirst, walk),
                    plateId + "：残影持板时玩家走到板格仍必须停驻在它上面");
            assertEquals(List.of(ECHO, PLAYER), echoFirst.plate(plateId).getOccupantIds(),
                    plateId + "：残影持板时玩家也必须登记进去（旧单槽位模型会静默丢弃玩家）");
            assertEquals(2, echoFirst.plate(plateId).getOccupantCount(), plateId + "：板上两人");
            assertEquals(ECHO, echoFirst.plate(plateId).getOccupantId(),
                    plateId + "：主占用者仍是最先进入的残影");
            assertTrue(echoFirst.plate(plateId).isOccupiedBy(PLAYER));
            assertTrue(echoFirst.outcome(plateId, doorId).occupied());
            assertTrue(echoFirst.outcome(plateId, doorId).doorUnlocked(),
                    plateId + "：两人同踩时门必须保持开");
            // 残影让出（回放里的 DOCK_LEFT / 轮末释放）→ 玩家还站着，门绝不回锁。
            assertTrue(echoFirst.plate(plateId).tryExit(ECHO, 1, 744L), "残影让出板");
            assertRemainingActorKeepsThePlateOccupied(echoFirst, plateId, doorId, PLAYER);
            // 玩家也离开后才释放 —— 与单路径终态的每一个字段一致
            assertTrue(echoFirst.plate(plateId).tryExit(PLAYER, 0, 800L), "玩家离开");
            assertEquals(playerReleased, echoFirst.outcome(plateId, doorId),
                    plateId + "：最后一人离开后，与单路径终态一致");
            echoFirst.close();

            // ---- ④ 玩家先站定 → 残影回放压上来（顺序反过来，结果必须对称）----
            Fixture playerFirst = new Fixture();
            prepareGate(playerFirst, pair.prepare());
            assertEquals(plateId, walkAndDock(playerFirst, walk));
            assertTrue(playerFirst.plate(plateId).tryEnter(ECHO, 1, 700L), "残影后到");
            assertEquals(List.of(PLAYER, ECHO), playerFirst.plate(plateId).getOccupantIds(),
                    plateId + "：玩家先到 → 残影后到同样必须双占用");
            assertEquals(2, playerFirst.plate(plateId).getOccupantCount());
            assertEquals(PLAYER, playerFirst.plate(plateId).getOccupantId());
            assertTrue(playerFirst.outcome(plateId, doorId).occupied());
            assertTrue(playerFirst.outcome(plateId, doorId).doorUnlocked());
            // 玩家离开 → 残影还在，门绝不回锁。
            leave(playerFirst, plateId, 800L);
            assertRemainingActorKeepsThePlateOccupied(playerFirst, plateId, doorId, ECHO);
            assertTrue(playerFirst.plate(plateId).tryExit(ECHO, 1, 900L), "残影也离开");
            assertEquals(playerReleased, playerFirst.outcome(plateId, doorId),
                    plateId + "：与「只残影」路径的终态一致（两条路径的终态已在上面对账）");
            playerFirst.close();
        }
    }

    /** 检验终点组某一块之前，先在<b>两条路径</b>上把同组的另外两块同样满足掉。 */
    private static void prepareGate(Fixture fixture, List<String> plateIds) {
        long tick = 100L;
        for (String plateId : plateIds) {
            assertTrue(fixture.plate(plateId).tryEnter("gate_prep", 0, tick++),
                    "夹具前提：预满足出口闸条件 " + plateId);
        }
    }

    /** 先占者离开后：剩下那个人必须仍在板上，板仍占用，门绝不回锁。 */
    private static void assertRemainingActorKeepsThePlateOccupied(Fixture fixture, String plateId,
                                                                 String doorId, String remaining) {
        DockingPlate plate = fixture.plate(plateId);
        assertTrue(plate.isOccupiedBy(remaining),
                plateId + "：剩下的 " + remaining + " 必须还在板上");
        assertTrue(plate.isOccupied(),
                plateId + "：板上还有人 → 必须仍为占用（旧单槽位模型在这里整体释放）");
        assertTrue(fixture.door(doorId).isUnlocked(),
                plateId + "：还有人压着板，门绝不能回锁");
        assertEquals(List.of(remaining), plate.getOccupantIds(), plateId + "：板上只剩剩下的那个人");
        assertEquals(remaining, plate.getOccupantId(), plateId + "：主占用者交给剩下的人");
        assertEquals(1, plate.getOccupantCount());
    }

    // ---------- 任务卡现象 1:1 复现 ----------

    /**
     * 任务卡的原始场景：**C 板先被残影压住**（第二/三轮 {@code E₁} 的回放），
     * 玩家随后真走到 C 板，再让残影让出 —— 门 C <b>绝不能</b>回锁。
     *
     * <p>这正是「残影能占、玩家站上去没反应」的现场：旧模型里玩家这一步被静默丢弃，
     * 残影一走板就整体释放，画面上人还站着、门却关了。</p>
     */
    @Test
    void playerArrivingOnCPlateHeldByAnEchoKeepsDoorCOpenAfterTheEchoLeaves() {
        Fixture f = new Fixture();
        DockingPlate plateC = f.plate(Level03Pursuit.PLATE_C);
        Door doorC = f.door(Level03Pursuit.DOOR_C);

        assertTrue(plateC.tryEnter("echo_1", 1, 600L), "夹具前提：E₁ 在刻 600 压住 C 板");
        assertTrue(doorC.isUnlocked(), "C 板被压 → 门 C 开（残影路径正常，与任务卡一致）");

        List<Vector2D> walk = walkPath(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_C,
                otherPlateCells(Level03Pursuit.PLATE_C));
        assertEquals(Level03Pursuit.PLATE_C, walkAndDock(f, walk),
                "玩家真走到 C 板必须停驻在 C 上（任务卡 §7 手工步骤）");

        assertEquals(List.of("echo_1", "player"), plateC.getOccupantIds(),
                "残影持板时玩家也必须登记进去（旧单槽位模型会静默丢弃玩家）");
        assertTrue(plateC.isOccupiedBy("player"));
        assertEquals("echo_1", plateC.getOccupantId(), "主占用者仍是最先进入的残影");
        assertTrue(doorC.isUnlocked());

        // 残影正常让出（回放里的 DOCK_LEFT / 轮末释放）
        assertTrue(plateC.tryExit("echo_1", 1, 744L), "E₁ 在刻 744 离开 C 板");
        assertTrue(plateC.isOccupiedBy("player"), "玩家还站着");
        assertTrue(plateC.isOccupied(), "板必须仍为占用");
        assertTrue(doorC.isUnlocked(), "玩家脚下的门 C 绝不能回锁（任务卡现象的反面）");
        assertEquals(List.of("player"), plateC.getOccupantIds());
        assertEquals("player", plateC.getOccupantId(), "主占用者交给玩家");
        assertEquals(0, plateC.getOccupantSourceRound());

        f.close();
    }

    /** 同一场景的第二种「残影让出」：残影寿命用尽（{@code ECHO_DISAPPEARED}），门 C 同样不得回锁。 */
    @Test
    void echoDisappearingUnderThePlayerOnCPlateKeepsDoorCOpen() {
        Fixture f = new Fixture();
        DockingPlate plateC = f.plate(Level03Pursuit.PLATE_C);
        Door doorC = f.door(Level03Pursuit.DOOR_C);

        plateC.tryEnter("echo_1", 1, 600L);
        assertEquals(Level03Pursuit.PLATE_C, walkAndDock(f,
                walkPath(Level03Pursuit.SPAWN_CELL, Level03Pursuit.CELL_PLATE_C,
                        otherPlateCells(Level03Pursuit.PLATE_C))));

        f.bus.dispatch(GameEvent.echoDisappeared("echo_1", 1056L, 1));

        assertFalse(plateC.isOccupiedBy("echo_1"), "消散的残影必须被移除");
        assertTrue(plateC.isOccupiedBy("player"), "玩家仍在板上");
        assertTrue(plateC.isOccupied());
        assertTrue(doorC.isUnlocked(), "残影消散不得把玩家脚下的门 C 带走");
        assertEquals(List.of("player"), plateC.getOccupantIds());

        f.close();
    }

    // ---------- §6.3 四块板同一套规则 ----------

    /**
     * 六块板/开关的判定配置必须逐字段同构（任务卡 §6.3「除位置外无差异」）：
     * 区域都是「以板为中心、边长 = 一格」的正方形，区域互不重叠，中心就是路径节点中心。
     *
     * <p>这一条同时排掉任务卡 §3 清单里的 1/2/4 项：中心-节点不一致、区域不覆盖玩家停下的那一格、
     * 同列导致区域重叠或 ID 串用 —— 任一项成立，这里都会红。</p>
     */
    @Test
    void allPlatesAndSwitchesShareTheSameDockRegionRule() {
        AutoDockService autoDock = new AutoDockService(Level03Pursuit.build());
        double tile = Level03Pursuit.TILE_SIZE;
        List<DockRegionView> regions = new ArrayList<>();

        for (String plateId : CELL_OF_PLATE.keySet()) {
            int[] cell = CELL_OF_PLATE.get(plateId);
            Vector2D center = Level03Pursuit.cellCenter(cell[0], cell[1]);

            var view = autoDock.findById(plateId).orElseThrow(
                    () -> new AssertionError("autoDock 里没有这块板: " + plateId));
            assertEquals(center, view.center(), plateId + "：板中心必须是本格中心");
            assertEquals(Level03Pursuit.nodeId(cell[0], cell[1]), view.pathNodeId(),
                    plateId + "：必须关联本格的路径节点");

            DockRegionView region = view.region();
            assertEquals(center.x() - tile / 2.0, region.minX(), 1e-9, plateId + "：区域左边 = 中心 − 半格");
            assertEquals(center.x() + tile / 2.0, region.maxX(), 1e-9, plateId + "：区域右边 = 中心 + 半格");
            assertEquals(center.y() - tile / 2.0, region.minY(), 1e-9, plateId + "：区域上边 = 中心 − 半格");
            assertEquals(center.y() + tile / 2.0, region.maxY(), 1e-9, plateId + "：区域下边 = 中心 + 半格");

            // 区域必须真的覆盖玩家停下的那一格：四条边界内侧都在区域内，边界外一格都不在。
            assertTrue(region.contains(center), plateId + "：中心在区域内");
            assertTrue(region.contains(new Vector2D(region.minX(), center.y())), plateId + "：左边界含");
            assertTrue(region.contains(new Vector2D(region.maxX(), center.y())), plateId + "：右边界含");
            assertTrue(region.contains(new Vector2D(center.x(), region.minY())), plateId + "：上边界含");
            assertTrue(region.contains(new Vector2D(center.x(), region.maxY())), plateId + "：下边界含");
            assertFalse(region.contains(new Vector2D(center.x(), region.maxY() + 2.0)),
                    plateId + "：格外的点不得落在区域内（相邻格不能误触）");
            assertFalse(region.contains(new Vector2D(region.minX() - 2.0, center.y())),
                    plateId + "：格外的点不得落在区域内");

            // §3 清单第 1 项：玩家实际停下的位置（节点中心）必须正好是 findNearest 命中的那一块。
            assertEquals(plateId, autoDock.findNearest(center, 0.0).orElseThrow().mechanismId(),
                    plateId + "：站在板心必须就近命中本板（区域不得与别的板串味）");

            regions.add(region);
        }

        assertEquals(CELL_OF_PLATE.size(), regions.size(), "六块板/开关都要检查到");
        for (int i = 0; i < regions.size(); i++) {
            for (int j = i + 1; j < regions.size(); j++) {
                assertFalse(regions.get(i).overlaps(regions.get(j)),
                        "判定区域不得两两重叠（C(10,2) 与 K(10,13) 同列也不得串用）");
            }
        }
    }

    // ---------- 夹具 ----------

    /** 一次「关卡会话」里的真实机关：四块板 + 四扇门 + 位置判定用的 autoDock。 */
    private static final class Fixture {

        private final LevelData level = Level03Pursuit.build();
        private final DockingPlateRegistry registry = new DockingPlateRegistry();
        private final EventDispatcher bus = new EventDispatcher();
        private final AutoDockService autoDock = new AutoDockService(level);
        private final C3DockController dock =
                new C3DockController(autoDock, autoDock, "player", 0);
        private final Map<String, DockingPlate> plates = new LinkedHashMap<>();
        private final Map<String, Door> doors = new LinkedHashMap<>();

        private Fixture() {
            for (EntitySpawnInfo entity : level.getEntitySpawnList()) {
                if ("dock_plate".equals(entity.getEntityType())) {
                    // 与装配层同构：第三关四块板都没有 role=switch ⇒ latching=false。
                    boolean latching = "switch".equals(entity.getProperties().get("role"));
                    plates.put(entity.getId(),
                            new DockingPlate(entity.getId(), entity.getPos(), registry, bus, latching));
                }
            }
            for (DoorInfo info : level.getDoors()) {
                doors.put(info.getId(), new Door(info.getId(), info.getPosition(),
                        info.getRequiredPlateIds(), registry, bus));
            }
        }

        private DockingPlate plate(String plateId) {
            return Objects.requireNonNull(plates.get(plateId), "没有这块板: " + plateId);
        }

        private Door door(String doorId) {
            return Objects.requireNonNull(doors.get(doorId), "没有这扇门: " + doorId);
        }

        /** 只读对账快照：板占用 / 门开 / 注册表占用 / 板状态 / 板上人数。 */
        private Outcome outcome(String plateId, String doorId) {
            DockingPlate plate = plate(plateId);
            return new Outcome(plate.isOccupied(), door(doorId).isUnlocked(),
                    registry.isOccupied(plateId), plate.getState(), plate.getOccupantCount());
        }

        private void close() {
            plates.values().forEach(DockingPlate::dispose);
            doors.values().forEach(Door::dispose);
        }
    }

    private record Outcome(boolean occupied,
                           boolean doorUnlocked,
                           boolean registryOccupied,
                           DockingPlate.State state,
                           int occupantCount) {
    }

    /**
     * 把玩家沿真实位置流从 {@code fromCell} 走到 {@code toCell}，并像装配层那样把 autoDock 的边沿
     * 镜像到机关侧；返回玩家最终停驻的机关 ID。
     *
     * <p>位置流完全按引擎口径生成：沿正交中心线、每刻 {@code C2_BASE_SPEED} = 2 世界单位 ⇒
     * 一格 48 单位 = 24 刻。玩家侧的驻留判定只吃位置（{@code C3DockController.cruiseStep} 不看输入），
     * 因此这条路径与真实玩法一致。</p>
     */
    private static String walkAndDock(Fixture fixture, List<Vector2D> positions) {
        for (int i = 0; i < positions.size(); i++) {
            long tick = i;
            C3DockDecision decision =
                    fixture.dock.step(tick, InputIntent.empty(tick), positions.get(i));
            mirrorDockEvents(fixture, decision);
            if (decision.isFreeze()) {
                return fixture.dock.dockedMechanismId().orElse(null);
            }
        }
        return null;
    }

    /** 玩家按该板所在节点的第一个合法出口方向离开；返回是否真的离开了。 */
    private static boolean leave(Fixture fixture, String plateId, long tick) {
        int[] cell = CELL_OF_PLATE.get(plateId);
        PathNode node = Level03Pursuit.build().getPathNodes().stream()
                .filter(n -> n.getId().equals(Level03Pursuit.nodeId(cell[0], cell[1])))
                .findFirst()
                .orElseThrow();
        assertFalse(node.getAllowDirs().isEmpty(), plateId + "：板所在节点必须有合法出口");
        Direction exit = Direction.valueOf(node.getAllowDirs().iterator().next().name());
        LogicalKey key = switch (exit) {
            case UP -> LogicalKey.DIR_UP;
            case DOWN -> LogicalKey.DIR_DOWN;
            case LEFT -> LogicalKey.DIR_LEFT;
            case RIGHT -> LogicalKey.DIR_RIGHT;
        };
        InputIntent input = new InputIntent(tick, Set.of(key), Set.of(), Set.of(key), List.of(exit));
        C3DockDecision decision = fixture.dock.step(
                tick, input, Level03Pursuit.cellCenter(cell[0], cell[1]));
        mirrorDockEvents(fixture, decision);
        assertFalse(fixture.dock.isDocked(), plateId + "：按合法出口方向必须能离开驻留");
        return true;
    }

    /** 与 {@code Level03Assembly.mirrorDockEvents} 同构：把 autoDock 边沿镜像到机关侧（驱动门）。 */
    private static void mirrorDockEvents(Fixture fixture, C3DockDecision decision) {
        for (TimelineEvent event : decision.events()) {
            DockingPlate plate = fixture.plates.get(event.mechanismId());
            if (plate == null) {
                continue;
            }
            switch (event.eventType()) {
                case DOCK_ENTERED -> plate.tryEnter(event.actorId(), event.sourceRound(), event.tick());
                case DOCK_LEFT, OCCUPANCY_RELEASED ->
                        plate.tryExit(event.actorId(), event.sourceRound(), event.tick());
                default -> {
                    // 其它事件与板占用无关。
                }
            }
        }
    }

    // ---------- 路线与位置流 ----------

    /** 出生点 → 目标板的真实位置流（每刻一个世界坐标），寻路避开其它三块板格。 */
    private static List<Vector2D> walkPath(int[] fromCell, int[] toCell, Set<String> avoidNodes) {
        List<int[]> cells = route(fromCell, toCell, avoidNodes);
        List<Vector2D> positions = new ArrayList<>();
        Vector2D current = Level03Pursuit.cellCenter(fromCell[0], fromCell[1]);
        positions.add(current);
        for (int i = 1; i < cells.size(); i++) {
            int dc = cells.get(i)[0] - cells.get(i - 1)[0];
            int dr = cells.get(i)[1] - cells.get(i - 1)[1];
            for (int step = 1; step <= Level03Pursuit.TICKS_PER_TILE; step++) {
                positions.add(new Vector2D(current.x() + dc * 2.0 * step,
                        current.y() + dr * 2.0 * step));
            }
            current = Level03Pursuit.cellCenter(cells.get(i)[0], cells.get(i)[1]);
        }
        return positions;
    }

    /** 除目标板之外的其它三块板格（寻路避开，免得半路被别的板驻留下来）。 */
    private static Set<String> otherPlateCells(String plateId) {
        Set<String> avoid = new LinkedHashSet<>();
        for (Map.Entry<String, int[]> entry : CELL_OF_PLATE.entrySet()) {
            if (!entry.getKey().equals(plateId)) {
                avoid.add(Level03Pursuit.nodeId(entry.getValue()[0], entry.getValue()[1]));
            }
        }
        return avoid;
    }

    /** BFS 最短格子路线（只用关卡数据的 {@link Level03Pursuit#isOpen}，不手抄地图）。 */
    private static List<int[]> route(int[] from, int[] to, Set<String> avoidNodes) {
        Map<String, int[]> cells = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        String start = key(from);
        String goal = key(to);
        assertNotEquals(start, goal, "起点与终点不得相同");
        cells.put(start, from);
        cameFrom.put(start, null);
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(goal)) {
                break;
            }
            int[] cell = cells.get(current);
            for (int[] delta : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                int col = cell[0] + delta[0];
                int row = cell[1] + delta[1];
                if (!Level03Pursuit.isOpen(col, row)) {
                    continue;
                }
                String next = col + "," + row;
                if (avoidNodes.contains(Level03Pursuit.nodeId(col, row)) || cameFrom.containsKey(next)) {
                    continue;
                }
                cells.put(next, new int[] {col, row});
                cameFrom.put(next, current);
                queue.add(next);
            }
        }
        assertTrue(cameFrom.containsKey(goal),
                "测试路线不可达: " + start + " -> " + goal + " avoid=" + avoidNodes);
        List<int[]> path = new ArrayList<>();
        for (String node = goal; node != null; node = cameFrom.get(node)) {
            path.add(0, cells.get(node));
        }
        return path;
    }

    private static String key(int[] cell) {
        return cell[0] + "," + cell[1];
    }
}
