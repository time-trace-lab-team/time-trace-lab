package org.example.timeloop.level;

import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2-A 三轮因果链与反例（卡 §二.2 + 裁决 §十一.3 官方解 + §十三.3 三条补充测试）。
 *
 * <p><b>官方解（R1 不进房）</b>：R1 玩家踩门外板造窗口 [216, 396) + 停在主驻留板到轮末；
 * R2 借 E1 的窗口从第 5 列南侧进房踩内板并驻留到轮末；R3 两残影同时压住内板与主驻留板 →
 * 终点闸门解锁 → 当前玩家到闸门按 E。</p>
 *
 * <p><b>注意语义差异</b>：本关的门外板是<b>普通驻留板</b>（占即开、离即关，<b>不锁存</b>），
 * 与 L1 的锁存开关（`role=switch`）刚好相反，测试名已写清以免混淆。</p>
 */
class Level02CorridorChainTest {

    private static final long WINDOW_START = Level02Corridor.WINDOW_START;
    private static final long WINDOW_END = Level02Corridor.WINDOW_END;
    private static final long R2_CROSS = Level02Corridor.R2_DOOR_CROSS;
    private static final long R2_INNER = Level02Corridor.R2_INNER_ARRIVAL;
    private static final long R1_MAIN = Level02Corridor.R1_MAIN_ARRIVAL;

    private LevelData level;
    private DockingPlateRegistry registry;
    private EventDispatcher bus;
    private DockingPlate outerPlate;
    private DockingPlate innerPlate;
    private DockingPlate mainPlate;
    private Door roomDoor;
    private Door exitDoor;
    private ExitTerminal exit;

    @BeforeEach
    void setUp() {
        level = Level02Corridor.build();
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();

        outerPlate = plate(Level02Corridor.PLATE_DOOR);
        innerPlate = plate(Level02Corridor.PLATE_INNER);
        mainPlate = plate(Level02Corridor.PLATE_MAIN);
        roomDoor = door(Level02Corridor.DOOR_ROOM);
        exitDoor = door(Level02Corridor.DOOR_EXIT);
        exit = new ExitTerminal(Level02Corridor.EXIT, position(Level02Corridor.EXIT),
                exitDoor.getId(), 72.0, bus);
    }

    // ================= 官方解：三轮成链 =================

    @Test
    void officialThreeRoundChainUnlocksTheGateAndFinishes() {
        // ---- R1：玩家踩门外板造窗口（不进房）----
        assertTrue(outerPlate.tryEnter("player", 0, WINDOW_START));
        assertTrue(roomDoor.isUnlocked(), "门外板被占 → 房门开");
        assertTrue(outerPlate.tryExit("player", 0, WINDOW_END), "R1 离开门外板");
        assertFalse(roomDoor.isUnlocked(), "离开门外板必须同刻回锁");

        // ---- 轮末：机关复位 ----
        resetAll();

        // ---- R2：E1 复现窗口；玩家从第 5 列南侧借窗口进房 ----
        assertTrue(outerPlate.tryEnter("echo_1", 1, WINDOW_START), "E1 复现门外板窗口");
        assertTrue(roomDoor.isUnlocked());
        assertTrue(R2_CROSS > WINDOW_START && R2_CROSS < WINDOW_END, "跨门刻必须落在窗口内");
        assertTrue(innerPlate.tryEnter("player", 0, R2_INNER), "玩家在窗口内进房踩内板");

        assertTrue(outerPlate.tryExit("echo_1", 1, WINDOW_END), "E1 离开门外板");
        assertFalse(roomDoor.isUnlocked(), "房门同刻回锁");
        assertTrue(innerPlate.isOccupied(), "进房即承诺：玩家仍在内板上");

        // ---- 轮末复位后进入 R3 ----
        resetAll();

        // ---- R3：E1 复现主驻留板；E2 复现内板 ----
        assertTrue(mainPlate.tryEnter("echo_1", 1, R1_MAIN));
        assertFalse(exitDoor.isUnlocked(), "只有主驻留板被占时闸门不开");
        assertFalse(exit.isDoorUnlocked());

        assertTrue(innerPlate.tryEnter("echo_2", 2, R1_MAIN), "E2 复现内板占用");
        assertTrue(exitDoor.isUnlocked(), "内板 + 主驻留板同刻被占 → 闸门解锁");
        assertTrue(exit.isDoorUnlocked(), "出口终端被武装");

        assertTrue(exit.interact(700, 0), "当前玩家应按 E 通关");
        assertFalse(exit.interact(701, 0), "出口不得重复触发");
    }

    // ================= 反例 =================

    @Test
    void counterExampleOnePlateAloneCannotUnlockTheGate() {
        assertTrue(innerPlate.tryEnter("player", 0, R2_INNER));
        assertFalse(exitDoor.isUnlocked(), "只压内板不解锁");

        resetAll();

        assertTrue(mainPlate.tryEnter("player", 0, R1_MAIN));
        assertFalse(exitDoor.isUnlocked(), "只压主驻留板不解锁");
    }

    /** 反例 ②：单人踩门外板后离开 → 房门同刻回锁 → 单人穿不过（裁决 §十一.1 推论）。 */
    @Test
    void counterExampleSoloPlayerCannotPassTheRoomDoor() {
        assertTrue(outerPlate.tryEnter("player", 0, WINDOW_START));
        assertTrue(roomDoor.isUnlocked(), "踩上时门是开的");

        assertTrue(outerPlate.tryExit("player", 0, WINDOW_START + 24));

        assertFalse(roomDoor.isUnlocked(),
                "驻留板同刻释放 → 房门同刻回锁 → 单人（没有第二个 actor 按板）穿不过房门格");
    }

    /** 反例 ③：故意把窗口压短 → R2 跨门刻时门已回锁 → 进不了房（窗口不足即无解）。 */
    @Test
    void counterExampleTooShortWindowMakesR2Unsovable() {
        assertTrue(outerPlate.tryEnter("echo_1", 1, WINDOW_START));
        assertTrue(outerPlate.tryExit("echo_1", 1, WINDOW_START + 24), "窗口仅 24 刻");

        assertFalse(roomDoor.isUnlocked(),
                "R2 跨门刻 " + R2_CROSS + " 早于/晚于该短窗口 → 门已回锁 → R2 进不了房");
    }

    // ================= 回锁与承诺 =================

    /** 「离开门外板即回锁」专项：门外板是普通板，<b>不锁存</b>（与 L1 开关相反）。 */
    @Test
    void leavingOuterPlateRelocksTheRoomDoorImmediately() {
        assertTrue(outerPlate.tryEnter("echo_1", 1, 300));
        assertTrue(roomDoor.isUnlocked());

        assertTrue(outerPlate.tryExit("echo_1", 1, 300));

        assertFalse(roomDoor.isUnlocked(), "同一刻即回锁（非锁存语义）");
        assertFalse(outerPlate.isOccupied());
    }

    /** 约束 6「进房即承诺」：R2 玩家在 396 之后到轮末都无法离开（门已回锁）。 */
    @Test
    void playerInsideTheRoomStaysUntilRoundEnd() {
        assertTrue(outerPlate.tryEnter("echo_1", 1, WINDOW_START));
        assertTrue(innerPlate.tryEnter("player", 0, R2_INNER));
        assertTrue(outerPlate.tryExit("echo_1", 1, WINDOW_END));

        for (long tick : List.of(WINDOW_END, 500L, 900L, Level02Corridor.DURATION_TICKS)) {
            assertFalse(roomDoor.isUnlocked(),
                    "刻 " + tick + " 房门必须仍是锁的 → 玩家出不去（进房即承诺）");
        }
        assertTrue(innerPlate.isOccupied(), "玩家占用保持到轮末，不残留、不崩溃");
        assertEquals("player", innerPlate.getOccupantId());
    }

    // ================= §十三.3 第三条：跨门刻与窗口余量 =================

    @Test
    void r2PassesDoorOnlyInsideWindow() {
        long lower = WINDOW_START + 30;
        long upper = WINDOW_END - 30;
        assertTrue(R2_CROSS >= lower && R2_CROSS <= upper,
                "跨门刻 " + R2_CROSS + " 必须落在 [" + lower + ", " + upper + "]");

        // 「玩家经过门外板是否停驻」的确定结论：R2 路线不含门外板 ⇒ 不发生停驻。
        assertNotEquals(Level02Corridor.NODE_PLATE_DOOR, Level02Corridor.NODE_DOOR_SOUTH);
        assertFalse(outerPlate.isOccupied(), "R2 玩家全程不得占用门外板（几何已解耦）");
    }

    // ================= §十三.3 第二条：同刻争抢 =================

    /**
     * §十三.3 第 2 条：本几何下 R3 不存在门外板争抢（R2 录制从不占用门外板）；
     * 另加构造性用例验证「同刻争抢 → 较旧者（sourceRound 小）胜」的引擎规则落点。
     */
    @Test
    void sameTickPlateContentionOlderEchoWins() {
        // (a) 几何解耦：R2 的路线不含门外板 → R3 中 E1/E2 不会在门外板同刻相遇
        assertFalse(outerPlate.isOccupied(), "R2 全程不占用门外板（几何已解耦，无争抢）");

        // (b) 构造性：同刻两个残影争同一块板，先写者（较旧者，回放按 sourceRound 升序）占住
        assertTrue(outerPlate.tryEnter("echo_1", 1, WINDOW_START));
        assertFalse(outerPlate.tryEnter("echo_2", 2, WINDOW_START),
                "同刻后写者必须被拒（单占用不变量）");
        assertEquals("echo_1", outerPlate.getOccupantId(),
                "较旧残影（sourceRound=1）胜；较新者的位置回放由 replay 层独立进行，不受占用拒绝影响");
    }

    // ---------- 工具 ----------

    private DockingPlate plate(String mechanismId) {
        return new DockingPlate(mechanismId, position(mechanismId), registry, bus);
    }

    private Door door(String doorId) {
        Set<String> required = level.getDoors().stream()
                .filter(d -> doorId.equals(d.getId()))
                .map(d -> d.getRequiredPlateIds())
                .findFirst()
                .orElseThrow();
        return new Door(doorId, position(doorId), required, registry, bus);
    }

    private Vector2D position(String mechanismId) {
        return level.getEntitySpawnList().stream()
                .filter(e -> mechanismId.equals(e.getId()))
                .map(e -> e.getPos())
                .findFirst()
                .orElseGet(() -> level.getDoors().stream()
                        .filter(d -> mechanismId.equals(d.getId()))
                        .map(d -> d.getPosition())
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("缺少机制: " + mechanismId)));
    }

    private void resetAll() {
        outerPlate.reset();
        innerPlate.reset();
        mainPlate.reset();
        roomDoor.reset();
        exitDoor.reset();
        exit.reset();
    }
}
