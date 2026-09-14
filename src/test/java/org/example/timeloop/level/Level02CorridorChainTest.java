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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二关「闸链」三轮因果链与反例（设计说明 §二 / §四，通关流程 §二 / §三）。
 *
 * <p><b>官方解</b>：R1 踩外闸板造 D1 窗口 + 驻留主板；R2 借 E1 的窗口进东翼、踩中继板造 D2 窗口、
 * 再去右下角驻留内板；R3 E1 压「闸板窗口 + 主板」、E2 压「中继窗口 + 内板」，
 * 玩家在 D2 窗口内进右上角内室踩终结板 → 三块板同刻被占 → 终点闸解锁 → 按 E 通关。</p>
 *
 * <p><b>语义关键</b>：5 块板都是<b>普通驻留板</b>（占即开、离即关，<b>不锁存</b>）。
 * P2 曾经是 {@code role=switch} 锁存开关，本设计已改回普通板，因此「压板的人永远走不过自己开的门」，
 * D2 只能靠「窗口」借别人的身位过 —— 这正是本关必须 3 轮的原因。</p>
 */
class Level02CorridorChainTest {

    private static final long GATE_START = Level02Corridor.GATE_WINDOW_START;
    private static final long GATE_END = Level02Corridor.GATE_WINDOW_END;
    private static final long GATE_CROSS = Level02Corridor.GATE_DOOR_CROSS;
    private static final long MAIN_ARRIVAL = Level02Corridor.MAIN_ARRIVAL;
    private static final long RELAY_START = Level02Corridor.RELAY_WINDOW_START;
    private static final long RELAY_END = Level02Corridor.RELAY_WINDOW_END;
    private static final long INNER_ARRIVAL = Level02Corridor.INNER_ARRIVAL;
    private static final long RELAY_CROSS = Level02Corridor.RELAY_DOOR_CROSS;
    private static final long CORE_ARRIVAL = Level02Corridor.CORE_ARRIVAL;
    private static final long UNLOCK = Level02Corridor.EXIT_UNLOCK_TICK;

    private LevelData level;
    private DockingPlateRegistry registry;
    private EventDispatcher bus;
    private DockingPlate gatePlate;
    private DockingPlate relayPlate;
    private DockingPlate innerPlate;
    private DockingPlate mainPlate;
    private DockingPlate corePlate;
    private Door gateDoor;
    private Door relayDoor;
    private Door exitDoor;
    private ExitTerminal exit;

    @BeforeEach
    void setUp() {
        level = Level02Corridor.build();
        registry = new DockingPlateRegistry();
        bus = new EventDispatcher();

        gatePlate = plate(Level02Corridor.PLATE_GATE);
        relayPlate = plate(Level02Corridor.PLATE_RELAY);
        innerPlate = plate(Level02Corridor.PLATE_INNER);
        mainPlate = plate(Level02Corridor.PLATE_MAIN);
        corePlate = plate(Level02Corridor.PLATE_CORE);
        gateDoor = door(Level02Corridor.DOOR_GATE);
        relayDoor = door(Level02Corridor.DOOR_RELAY);
        exitDoor = door(Level02Corridor.DOOR_EXIT);
        exit = new ExitTerminal(Level02Corridor.EXIT, position(Level02Corridor.EXIT),
                exitDoor.getId(), ExitTerminal.interactRadiusForTileSize(Level02Corridor.TILE_SIZE), bus);
    }

    // ================= 官方解：三轮成链 =================

    @Test
    void officialThreeRoundChainUnlocksTheGateAndFinishes() {
        // ---------- R1：踩外闸板造 D1 窗口（刻 240），站到 384，再走 10 格到主板（624）驻留 ----------
        assertTrue(gatePlate.tryEnter("player", 0, GATE_START), "R1 在 240 踩上外闸板");
        assertTrue(gateDoor.isUnlocked(), "外闸板被占 → D1 开");

        assertTrue(gatePlate.tryExit("player", 0, GATE_END), "R1 在 384 松开外闸板");
        assertFalse(gateDoor.isUnlocked(), "普通板离即关：松手同刻 D1 回锁（无锁存）");

        assertTrue(mainPlate.tryEnter("player", 0, MAIN_ARRIVAL), "R1 在 624 踩上主板并驻留到轮末");
        assertFalse(exitDoor.isUnlocked(), "只有主板被占时终点闸不开");

        // ---------- 轮末复位 ----------
        resetAll();

        // ---------- R2：E1 复现闸板窗口；玩家借窗跨 D1（312）；踩中继板造 D2 窗口（456→744）；去内板（960）----------
        assertTrue(gatePlate.tryEnter("echo_1", 1, GATE_START), "E1 复现外闸板窗口起点");
        assertTrue(GATE_CROSS > GATE_START && GATE_CROSS < GATE_END,
                "跨 D1 刻 " + GATE_CROSS + " 必须落在窗口 [" + GATE_START + ", " + GATE_END + ") 内");
        assertTrue(gateDoor.isUnlocked(), "刻 312 跨门时 D1 必须开着");

        assertTrue(gatePlate.tryExit("echo_1", 1, GATE_END), "E1 在 384 松开外闸板");
        assertFalse(gateDoor.isUnlocked(), "D1 回锁 —— 玩家已经在东翼里");

        assertTrue(relayPlate.tryEnter("player", 0, RELAY_START), "R2 在 456 踩上中继板造 D2 窗口");
        assertTrue(relayDoor.isUnlocked(), "中继板被占 → D2 开");
        assertTrue(relayPlate.tryExit("player", 0, RELAY_END), "R2 在 744 松手中继板");
        assertFalse(relayDoor.isUnlocked(), "松手同刻 D2 回锁（普通板，不锁存）");

        assertTrue(innerPlate.tryEnter("player", 0, INNER_ARRIVAL), "R2 在 960 踩上内板并驻留到轮末");

        // ---------- 轮末复位 ----------
        resetAll();

        // ---------- R3：E1 复现窗口+主板；E2 复现窗口+内板；玩家跨 D2（696）进内室踩终结板（840）----------
        assertTrue(gatePlate.tryEnter("echo_1", 1, GATE_START));
        assertTrue(gateDoor.isUnlocked());
        assertTrue(gatePlate.tryExit("echo_1", 1, GATE_END));
        assertFalse(gateDoor.isUnlocked());
        assertTrue(mainPlate.tryEnter("echo_1", 1, MAIN_ARRIVAL), "E1 从 624 起压住主板");

        assertTrue(relayPlate.tryEnter("echo_2", 2, RELAY_START), "E2 从 456 起压住中继板");
        assertTrue(relayDoor.isUnlocked(), "D2 窗口打开");
        assertTrue(RELAY_CROSS > RELAY_START && RELAY_CROSS < RELAY_END,
                "跨 D2 刻 " + RELAY_CROSS + " 必须落在窗口 [" + RELAY_START + ", " + RELAY_END + ") 内");

        assertTrue(relayPlate.tryExit("echo_2", 2, RELAY_END), "E2 在 744 松手中继板");
        assertFalse(relayDoor.isUnlocked(), "D2 回锁 —— 玩家已经在内室里");
        assertFalse(relayPlate.isOccupied(), "解锁那一刻 P2 必须已经释放（终局只占 3 块板）");

        assertTrue(corePlate.tryEnter("player", 0, CORE_ARRIVAL), "玩家在 840 踩上终结板 P5");
        assertFalse(exitDoor.isUnlocked(), "此刻只有主板 + 终结板，终点闸仍锁");

        assertTrue(innerPlate.tryEnter("echo_2", 2, UNLOCK), "E2 在 960 踩上内板 P3");
        assertTrue(exitDoor.isUnlocked(), "内板 + 主板 + 终结板同刻被占 → 终点闸解锁");
        assertTrue(exit.isDoorUnlocked(), "出口终端被武装（E 提示可见）");

        assertTrue(exit.interact(UNLOCK, 0), "当前玩家在 " + UNLOCK + " 刻按 E 通关");
        assertFalse(exit.interact(UNLOCK + 1, 0), "出口不得重复触发");

        // 三块板同刻被占，且各自由不同 actor 压住（2 残影 + 1 玩家 = 上限）。
        assertEquals("echo_1", mainPlate.getOccupantId());
        assertEquals("echo_2", innerPlate.getOccupantId());
        assertEquals("player", corePlate.getOccupantId());
        assertTrue(innerPlate.isOccupied() && mainPlate.isOccupied() && corePlate.isOccupied());
    }

    // ================= 反例 =================

    /** 反例 ①：1 块板、任意 2 块板的组合都不解锁终点闸（必须 3 块同刻）。 */
    @Test
    void counterExampleFewerThanThreePlatesNeverUnlockTheGate() {
        assertTrue(innerPlate.tryEnter("player", 0, INNER_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "只压内板不解锁");

        resetAll();

        assertTrue(mainPlate.tryEnter("player", 0, MAIN_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "只压主板不解锁");

        resetAll();

        assertTrue(corePlate.tryEnter("player", 0, CORE_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "只压终结板不解锁");

        resetAll();

        assertTrue(innerPlate.tryEnter("echo_2", 2, INNER_ARRIVAL));
        assertTrue(mainPlate.tryEnter("echo_1", 1, MAIN_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "内板 + 主板仍不解锁");

        resetAll();

        assertTrue(innerPlate.tryEnter("echo_2", 2, INNER_ARRIVAL));
        assertTrue(corePlate.tryEnter("player", 0, CORE_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "内板 + 终结板仍不解锁");

        resetAll();

        assertTrue(mainPlate.tryEnter("echo_1", 1, MAIN_ARRIVAL));
        assertTrue(corePlate.tryEnter("player", 0, CORE_ARRIVAL));
        assertFalse(exitDoor.isUnlocked(), "主板 + 终结板仍不解锁");

        // 补上第三块 → 才解锁。
        assertTrue(innerPlate.tryEnter("echo_2", 2, UNLOCK));
        assertTrue(exitDoor.isUnlocked(), "补齐第三块板才解锁");
    }

    /** 反例 ②：单人压板后离开 → 门同刻回锁 → 单人永远穿不过自己开的门。 */
    @Test
    void counterExampleLoneActorCanNeverPassAPlateGatedDoor() {
        // D1：单人踩外闸板，门开；他要走到门格就必须先离开板 → 门已回锁。
        assertTrue(gatePlate.tryEnter("player", 0, GATE_START));
        assertTrue(gateDoor.isUnlocked(), "踩上时 D1 是开的");
        assertTrue(gatePlate.tryExit("player", 0, GATE_START + Level02Corridor.TICKS_PER_TILE));
        assertFalse(gateDoor.isUnlocked(),
                "外闸板同刻释放 → D1 同刻回锁 → 单人（没有第二个 actor 压板）穿不过 D1");

        // D2 同理：P2 不再是锁存开关，压板的人自己走不过自己开的 D2。
        assertTrue(relayPlate.tryEnter("player", 0, RELAY_START));
        assertTrue(relayDoor.isUnlocked(), "踩上时 D2 是开的");
        assertTrue(relayPlate.tryExit("player", 0, RELAY_START + Level02Corridor.TICKS_PER_TILE));
        assertFalse(relayDoor.isUnlocked(),
                "中继板同刻释放 → D2 同刻回锁 → 单人无法自行绕过「窗口」进内室");
    }

    /** 反例 ③：把窗口压短（只压 24 刻）→ 跨门刻时门早已回锁 → 该轮作废。 */
    @Test
    void counterExampleTooShortWindowMissesTheCrossingTick() {
        assertTrue(gatePlate.tryEnter("echo_1", 1, GATE_START));
        assertTrue(gatePlate.tryExit("echo_1", 1, GATE_START + Level02Corridor.TICKS_PER_TILE),
                "窗口仅 24 刻");
        assertFalse(gateDoor.isUnlocked(),
                "跨门刻 " + GATE_CROSS + " 远在窗口之后 → D1 已回锁 → R2 进不了东翼");

        assertTrue(relayPlate.tryEnter("echo_2", 2, RELAY_START));
        assertTrue(relayPlate.tryExit("echo_2", 2, RELAY_START + Level02Corridor.TICKS_PER_TILE),
                "D2 窗口仅 24 刻");
        assertFalse(relayDoor.isUnlocked(),
                "跨门刻 " + RELAY_CROSS + " 远在窗口之后 → D2 已回锁 → R3 进不了内室");
    }

    // ================= 回锁 / 承诺 / 争抢 =================

    /** 「离即关」专项：同一刻进入再离开，门立刻回锁，板上不残留占用。 */
    @Test
    void leavingAPlateRelocksItsDoorOnTheVerySameTick() {
        assertTrue(gatePlate.tryEnter("echo_1", 1, 300));
        assertTrue(gateDoor.isUnlocked());

        assertTrue(gatePlate.tryExit("echo_1", 1, 300));

        assertFalse(gateDoor.isUnlocked(), "同一刻即回锁（非锁存语义）");
        assertFalse(gatePlate.isOccupied());
        assertFalse(gatePlate.isLatched(), "普通板不得有锁存位");

        // P2 同样不得锁存 —— 它是本关唯一被改造过的板。
        assertTrue(relayPlate.tryEnter("echo_2", 2, 300));
        assertTrue(relayDoor.isUnlocked());
        assertTrue(relayPlate.tryExit("echo_2", 2, 300));
        assertFalse(relayPlate.isLatched(), "P2 必须是普通驻留板（role=switch 已去掉）");
        assertFalse(relayDoor.isUnlocked());
    }

    /** 「进内室即承诺」：744 之后 D2 一直回锁，玩家只能留在内室里等 E2 去压内板。 */
    @Test
    void playerInsideTheInnerRoomStaysUntilRoundEnd() {
        assertTrue(relayPlate.tryEnter("echo_2", 2, RELAY_START));
        assertTrue(relayDoor.isUnlocked());
        assertTrue(corePlate.tryEnter("player", 0, CORE_ARRIVAL));
        assertTrue(relayPlate.tryExit("echo_2", 2, RELAY_END));

        for (long tick : List.of(RELAY_END, 800L, 900L, Level02Corridor.DURATION_TICKS)) {
            assertFalse(relayDoor.isUnlocked(), "刻 " + tick + " D2 必须仍是锁的（玩家出不去）");
        }
        assertTrue(corePlate.isOccupied(), "玩家占用保持到轮末");
        assertEquals("player", corePlate.getOccupantId());
    }

    /** 同刻争抢：两个残影抢同一块板时，先写者（较旧残影，回放按 sourceRound 升序）占住。 */
    @Test
    void sameTickPlateContentionOlderEchoWins() {
        assertTrue(gatePlate.tryEnter("echo_1", 1, GATE_START));
        assertFalse(gatePlate.tryEnter("echo_2", 2, GATE_START),
                "同刻后写者必须被拒（单占用不变量）");
        assertEquals("echo_1", gatePlate.getOccupantId(), "较旧残影（sourceRound=1）胜");
    }

    /** 数据面复核：3 扇门引用正确的板，5 块板都是普通板。 */
    @Test
    void doorWiringMatchesTheDesignTable() {
        assertEquals(3, level.getDoors().size());
        assertEquals(Set.of(Level02Corridor.PLATE_GATE),
                requiredPlates(Level02Corridor.DOOR_GATE));
        assertEquals(Set.of(Level02Corridor.PLATE_RELAY),
                requiredPlates(Level02Corridor.DOOR_RELAY));
        assertEquals(Set.of(Level02Corridor.PLATE_INNER, Level02Corridor.PLATE_MAIN, Level02Corridor.PLATE_CORE),
                requiredPlates(Level02Corridor.DOOR_EXIT));
        assertEquals(position(Level02Corridor.DOOR_GATE), gateDoor.getPosition());
        assertEquals(position(Level02Corridor.DOOR_RELAY), relayDoor.getPosition());
        assertEquals(position(Level02Corridor.DOOR_EXIT), exitDoor.getPosition());
    }

    // ---------- 工具 ----------

    private Set<String> requiredPlates(String doorId) {
        return level.getDoors().stream()
                .filter(d -> doorId.equals(d.getId()))
                .map(org.example.timeloop.level.model.DoorInfo::getRequiredPlateIds)
                .findFirst()
                .orElseThrow();
    }

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
        registry.resetAll();
        gateDoor.reset();
        relayDoor.reset();
        exitDoor.reset();
        exit.reset();
    }
}
