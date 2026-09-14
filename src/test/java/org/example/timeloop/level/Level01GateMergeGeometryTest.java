package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L01-GATE-MERGE-DEV3 §2.1：闸门终点的几何不变量（约束 1–7）。
 *
 * <p>选定节点 <b>{@value #GATE}</b> = (18, 7) = (888, 360)。它是 8 个候选中唯一同时满足
 * 「与开关 1 格 + 位于第 7 行侧支（锁住时不切断第 8 行走廊）」的点；其余候选的筛选结果见
 * 交付文档（全部 8 个候选均满足硬约束 1–6，按软约束 7 取最小距离）。</p>
 */
class Level01GateMergeGeometryTest {

    private static final String GATE = "L01_node_c18_r7";
    private static final String SWITCH = "L01_node_plate_right";
    private static final String LEFT_PLATE = "L01_node_plate_left";
    private static final String SPAWN = "L01_node_spawn";
    /** 「不做任何忽略」的哨兵值：没有任何节点的 ID 是空串。 */
    private static final String NO_IGNORE = "";

    private static final double TILE = 48.0;
    /** 出口终端交互半径 = 1.5 × tileSize（第一关）。 */
    private static final double INTERACT_RADIUS = 72.0;

    private final LevelData level = Level01Footsteps.build();
    private final LevelGeometry geometry = new LevelGeometryImpl(level);

    /** 约束 2：是既有路径节点；且位置就是 (18, 7) 的格心。 */
    @Test
    void gateIsAnExistingPathNodeAtEighteenSeven() {
        assertTrue(geometry.hasNode(GATE), "闸门节点必须是既有路径节点");
        assertEquals(new Vector2D(18.5 * TILE, 7.5 * TILE), centerOf(GATE));
    }

    /** 约束 1 + 约束 3：≠ 原终点 (19,8)，≠ 开关格 (18,8)。 */
    @Test
    void gateIsNeitherTheOldExitNorTheSwitchCell() {
        assertNotEquals(centerOf("L01_node_exit_terminal"), centerOf(GATE), "约束 1：不得复用原终点");
        assertNotEquals(centerOf(SWITCH), centerOf(GATE), "约束 3：不得与开关同格");
        assertEquals(new Vector2D(19.5 * TILE, 8.5 * TILE), centerOf("L01_node_exit_terminal"),
                "原终点仍是 (19,8)，本卡不删节点");
    }

    /** 约束 4：与左驻留板中心距离 > 1.5 × tileSize —— 防「占着左板直接按 E」的单轮通关。 */
    @Test
    void gateIsOutsideInteractRangeOfTheLeftPlate() {
        double distance = distance(centerOf(GATE), centerOf(LEFT_PLATE));
        assertTrue(distance > INTERACT_RADIUS,
                "闸门到左板距离必须 > " + INTERACT_RADIUS + "，实测 " + distance);
        assertEquals(673.71, distance, 0.01);
    }

    /** §2.5-4 反例：站在左板上按 E 不能结算（终端不在交互半径内）。 */
    @Test
    void playerStandingOnLeftPlateCannotPressTheExit() {
        Vector2D gateTerminal = exitTerminalPosition();
        assertEquals(centerOf(GATE), gateTerminal, "出口终端必须与闸门同格");
        assertEquals(distance(gateTerminal, centerOf(LEFT_PLATE)) > INTERACT_RADIUS, true,
                "左板必须在闸门交互半径之外，否则单轮即可通关");
    }

    /** 约束 5：不是「出生点 → 左板」「出生点 → 开关」的割点。 */
    @Test
    void gateIsNotACutPointOfSpawnToLeftPlateOrSpawnToSwitch() {
        assertTrue(reachableWithout(SPAWN, LEFT_PLATE, GATE), "删掉闸门节点后仍须能走到左板");
        assertTrue(reachableWithout(SPAWN, SWITCH, GATE), "删掉闸门节点后仍须能走到开关");
    }

    /** 约束 6：第 1 轮可规划路线内可达（路径图可达性，而不是 1 跳相邻）。 */
    @Test
    void gateIsReachableFromSpawn() {
        assertTrue(reachableWithout(SPAWN, GATE, NO_IGNORE), "出生点必须能走到闸门节点");
    }

    /** 约束 7（软）：与开关相距 1 格，保证「闸门旁就是开关」的可读性。 */
    @Test
    void gateSitsExactlyOneTileFromTheSwitch() {
        assertEquals(TILE, distance(centerOf(GATE), centerOf(SWITCH)), 1e-9);
    }

    /** §2.1 数据一致性：门与出口终端同格同节点，门条件仍是两块板。 */
    @Test
    void doorAndExitTerminalShareTheGateNode() {
        DoorInfo door = level.getDoors().stream()
                .filter(d -> "L01_door_01".equals(d.getId()))
                .findFirst()
                .orElseThrow();
        EntitySpawnInfo exit = entity("L01_exit_00");

        assertEquals(centerOf(GATE), door.getPosition(), "门必须搬到闸门节点");
        assertEquals(centerOf(GATE), exit.getPos(), "出口终端必须与门同格");
        assertEquals(GATE, exit.getPathNodeId(), "出口终端必须引用闸门节点");
        assertEquals(Set.of("L01_plate_left", "L01_plate_right"), door.getRequiredPlateIds(),
                "门条件保持两块板不变");
    }

    /** §2.2 数据：右板是开关变体，ID 与 autoDock 不变。 */
    @Test
    void rightPlateCarriesSwitchRoleWithoutChangingIdOrAutoDock() {
        EntitySpawnInfo right = entity("L01_plate_right");
        assertEquals("switch", right.getProperties().get("role"));
        assertEquals(Boolean.TRUE, right.getProperties().get("autoDock"));
        assertEquals(centerOf(SWITCH), right.getPos(), "开关位置不变 (18,8)");
        assertEquals(SWITCH, right.getPathNodeId());
    }

    // ---------- 工具 ----------

    private Vector2D centerOf(String nodeId) {
        return level.getPathNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .map(PathNode::getWorldPos)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少节点: " + nodeId));
    }

    private Vector2D exitTerminalPosition() {
        return entity("L01_exit_00").getPos();
    }

    private EntitySpawnInfo entity(String id) {
        return level.getEntitySpawnList().stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少实体: " + id));
    }

    private static double distance(Vector2D a, Vector2D b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    /** 在「跳过 ignore 节点」的图上做 BFS，判断 from → to 是否仍连通。 */
    private boolean reachableWithout(String from, String to, String ignore) {
        if (ignore.equals(from) || ignore.equals(to)) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) {
                return true;
            }
            for (String next : new ArrayList<>(geometry.getNeighbors(current))) {
                if (!ignore.equals(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }
    @Test
    void geometryUsesOneTileGrid() {
        assertEquals(TILE, geometry.getTileSize(), 1e-9);
        assertFalse(level.getPathNodes().isEmpty());
    }
}
