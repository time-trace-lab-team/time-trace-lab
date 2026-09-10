package org.example.timeloop.level;

import org.example.timeloop.level.model.DoorInfo;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.mechanism.DockingPlate;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.Door;
import org.example.timeloop.mechanism.ExitTerminal;
import org.example.timeloop.mechanism.autodock.AutoDockResult;
import org.example.timeloop.mechanism.autodock.AutoDockService;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一关最小 headless 因果链：E1 占左板、当前玩家占右板，门解锁后当前玩家触发出口。
 *
 * <p>AutoDock 结果到机关事件的转换代表移动层向开发三机关层提交的已批准边沿，
 * 不依赖 JavaFX、app 场景或第二套计时器。</p>
 */
class Level01CausalChainTest {

    @BeforeEach
    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void level01IsSolvableThroughEchoLeftPlayerRightDoorAndExit() {
        LevelData level = Level01Footsteps.build();
        assertEquals(16 * 60L, level.getDurationTicks());
        assertEquals(3, level.getMaxRounds());
        assertEquals(1, level.getEchoLifeL());

        EntitySpawnInfo leftInfo = entity(level, "L01_plate_left");
        EntitySpawnInfo rightInfo = entity(level, "L01_plate_right");
        EntitySpawnInfo exitInfo = entity(level, "L01_exit_00");
        DoorInfo doorInfo = level.getDoors().stream()
                .filter(door -> "L01_door_01".equals(door.getId()))
                .findFirst()
                .orElseThrow();
        List<String> eventTrace = registerEventTrace();

        DockingPlate leftPlate = new DockingPlate(leftInfo.getId(), leftInfo.getPos());
        DockingPlate rightPlate = new DockingPlate(rightInfo.getId(), rightInfo.getPos());
        Door door = new Door(doorInfo.getId(), doorInfo.getPosition(), doorInfo.getRequiredPlateIds());
        ExitTerminal exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), door.getId());
        AutoDockService autoDock = new AutoDockService(level);

        AutoDockResult echoDocked = autoDock.tryEnter(
                leftInfo.getId(), "echo_1", 1, 600, leftInfo.getPos());
        assertEquals(AutoDockResult.Status.ENTERED, echoDocked.status());
        assertTrue(leftPlate.tryEnter("echo_1", 1, 600));
        assertFalse(door.isUnlocked(), "只有 E1 占左板时门仍应关闭");
        assertFalse(exit.interact(601, 2), "门未开时当前玩家不能触发出口");

        AutoDockResult playerDocked = autoDock.tryEnter(
                rightInfo.getId(), "player", 2, 720, rightInfo.getPos());
        assertEquals(AutoDockResult.Status.ENTERED, playerDocked.status());
        assertTrue(rightPlate.tryEnter("player", 2, 720));

        assertTrue(door.isUnlocked());
        assertTrue(exit.isDoorUnlocked());
        assertTrue(exit.interact(721, 2), "当前玩家在门解锁后应能成功触发出口");
        assertFalse(exit.interact(722, 2), "出口触发后不得重复触发");

        assertEquals(List.of(
                        GameEvent.PLATE_ENTERED + ":L01_plate_left",
                        GameEvent.PLATE_ENTERED + ":L01_plate_right",
                        GameEvent.DOOR_UNLOCKED + ":L01_door_01",
                        GameEvent.EXIT_TRIGGERED + ":L01_exit_00"),
                eventTrace);
    }

    @Test
    void oneOccupiedPlateCannotBypassFirstLevelDoor() {
        LevelData level = Level01Footsteps.build();
        EntitySpawnInfo leftInfo = entity(level, "L01_plate_left");
        EntitySpawnInfo rightInfo = entity(level, "L01_plate_right");
        EntitySpawnInfo exitInfo = entity(level, "L01_exit_00");
        DoorInfo doorInfo = level.getDoors().get(0);

        DockingPlate leftPlate = new DockingPlate(leftInfo.getId(), leftInfo.getPos());
        new DockingPlate(rightInfo.getId(), rightInfo.getPos());
        Door door = new Door(doorInfo.getId(), doorInfo.getPosition(), doorInfo.getRequiredPlateIds());
        ExitTerminal exit = new ExitTerminal(exitInfo.getId(), exitInfo.getPos(), door.getId());

        assertTrue(leftPlate.tryEnter("echo_1", 1, 600));
        assertFalse(door.isUnlocked());
        assertFalse(exit.isDoorUnlocked());
        assertFalse(exit.interact(601, 2));
    }

    private static EntitySpawnInfo entity(LevelData level, String id) {
        return level.getEntitySpawnList().stream()
                .filter(entity -> id.equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少第一关实体: " + id));
    }

    private static List<String> registerEventTrace() {
        List<String> trace = new ArrayList<>();
        GameObserver observer = event -> trace.add(event.eventType() + ":" + event.sourceId());
        EventDispatcher dispatcher = EventDispatcher.getInstance();
        dispatcher.register(GameEvent.PLATE_ENTERED, observer);
        dispatcher.register(GameEvent.PLATE_EXITED, observer);
        dispatcher.register(GameEvent.DOOR_UNLOCKED, observer);
        dispatcher.register(GameEvent.EXIT_TRIGGERED, observer);
        return trace;
    }
}
