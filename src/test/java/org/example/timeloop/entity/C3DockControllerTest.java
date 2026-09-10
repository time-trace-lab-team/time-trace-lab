package org.example.timeloop.entity;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.autodock.AutoDockOccupancyPort;
import org.example.timeloop.mechanism.autodock.AutoDockReadPort;
import org.example.timeloop.mechanism.autodock.AutoDockResetReason;
import org.example.timeloop.mechanism.autodock.AutoDockResult;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.mechanism.autodock.DockOccupancyView;
import org.example.timeloop.mechanism.autodock.DockRegionView;
import org.example.timeloop.replay.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3 停放/输入策略测试（假 autoDock 端口，运动无关）。
 */
class C3DockControllerTest {

    private static final Vector2D CENTER = new Vector2D(0.0, 0.0);
    private static final Vector2D OUTSIDE = new Vector2D(0.0, 5.0);

    private static C3DockController controller(FakeDockPort port) {
        return new C3DockController(port, port, "player", 0);
    }

    private static InputIntent withDirectionEdge(long tick, Direction direction, LogicalKey heldKey) {
        return new InputIntent(tick, Set.of(heldKey), Set.of(), Set.of(heldKey), List.of(direction));
    }

    @Test
    void cruisingOutsideRegionKeepsCruisingWithoutEvents() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);

        C3DockDecision decision = controller.step(0, InputIntent.empty(0), OUTSIDE);

        assertEquals(C3DockDecision.Status.CRUISE, decision.status());
        assertTrue(decision.departureDirection().isEmpty());
        assertTrue(decision.events().isEmpty());
        assertFalse(controller.isDocked());
    }

    @Test
    void enteringRegionFreezesAndEmitsDockEntered() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);

        C3DockDecision decision = controller.step(10, InputIntent.empty(10), CENTER);

        assertEquals(C3DockDecision.Status.FREEZE, decision.status());
        assertEquals(1, decision.events().size());
        TimelineEvent event = decision.events().get(0);
        assertEquals(TimelineEvent.EventType.DOCK_ENTERED, event.eventType());
        assertEquals(10L, event.tick());
        assertEquals("player", event.actorId());
        assertEquals(0, event.sourceRound());
        assertEquals("L01_plate_left", event.mechanismId());
        assertTrue(controller.isDocked());
        assertEquals("L01_plate_left", controller.dockedMechanismId().orElseThrow());
    }

    @Test
    void heldOldKeyDoesNotLeaveDock() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);

        // 一直按住 UP，但没有新的按下边沿 -> 不得离开
        InputIntent heldOnly = new InputIntent(1, Set.of(), Set.of(),
                Set.of(LogicalKey.DIR_UP), List.of());
        C3DockDecision decision = controller.step(1, heldOnly, CENTER);

        assertEquals(C3DockDecision.Status.FREEZE, decision.status());
        assertTrue(decision.events().isEmpty());
        assertTrue(controller.isDocked());
    }

    @Test
    void illegalExitDirectionKeepsDocked() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);

        // LEFT 不是合法出口
        C3DockDecision decision = controller.step(1, withDirectionEdge(1, Direction.LEFT, LogicalKey.DIR_LEFT), CENTER);

        assertEquals(C3DockDecision.Status.FREEZE, decision.status());
        assertTrue(controller.isDocked());
    }

    @Test
    void legalLeaveEdgeStartsDepartureAndKeepsOccupancyUntilOutside() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);

        C3DockDecision departure = controller.step(1,
                withDirectionEdge(1, Direction.UP, LogicalKey.DIR_UP), CENTER);
        assertEquals(C3DockDecision.Status.CRUISE, departure.status());
        assertEquals(Direction.UP, departure.departureDirection().orElseThrow());
        assertTrue(controller.isDocked(), "仍在区域内，占用未释放");

        // 仍在区域内：不释放、不发 DOCK_LEFT
        C3DockDecision stillInside = controller.step(2, InputIntent.empty(2), new Vector2D(0.0, 1.0));
        assertEquals(C3DockDecision.Status.CRUISE, stillInside.status());
        assertEquals(Direction.UP, stillInside.departureDirection().orElseThrow());
        assertTrue(stillInside.events().isEmpty());
        assertTrue(controller.isDocked());
    }

    @Test
    void exitingRegionReleasesAndEmitsDockLeftWithDirectionAndReason() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);
        controller.step(1, withDirectionEdge(1, Direction.UP, LogicalKey.DIR_UP), CENTER);

        C3DockDecision decision = controller.step(2, InputIntent.empty(2), OUTSIDE);

        assertEquals(C3DockDecision.Status.CRUISE, decision.status());
        assertEquals(1, decision.events().size());
        TimelineEvent event = decision.events().get(0);
        assertEquals(TimelineEvent.EventType.DOCK_LEFT, event.eventType());
        assertEquals(2L, event.tick());
        assertEquals(Direction.UP, event.leaveDirection());
        assertEquals(C3DockController.REASON_NEW_DIRECTION, event.reason());
        assertFalse(controller.isDocked());
    }

    @Test
    void interactBufferHoldsForWindowThenExpires() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);

        InputIntent press = new InputIntent(0, Set.of(LogicalKey.INTERACT), Set.of(),
                Set.of(LogicalKey.INTERACT), List.of());
        assertTrue(controller.step(0, press, OUTSIDE).interactBuffered());

        for (long tick = 1; tick <= C3DockController.INTERACT_BUFFER_TICKS; tick++) {
            controller.step(tick, InputIntent.empty(tick), OUTSIDE);
        }
        assertFalse(controller.interactBuffered(), "缓冲窗口过后应失效");
    }

    @Test
    void releaseOccupancyEmitsReleasedOnlyForOwnedDock() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);

        List<TimelineEvent> events = controller.releaseOccupancy(DockEventReason.ECHO_EXPIRED, 5);

        assertEquals(1, events.size());
        assertEquals(TimelineEvent.EventType.OCCUPANCY_RELEASED, events.get(0).eventType());
        assertEquals("ECHO_EXPIRED", events.get(0).reason());
        assertFalse(controller.isDocked());
        assertFalse(port.view().occupancy().occupied());

        // 再次释放不产生事件（幂等）
        assertTrue(controller.releaseOccupancy(DockEventReason.ECHO_EXPIRED, 6).isEmpty());
    }

    @Test
    void resetClearsOccupancyAndLocalDockState() {
        FakeDockPort port = new FakeDockPort();
        C3DockController controller = controller(port);
        controller.step(0, InputIntent.empty(0), CENTER);

        List<TimelineEvent> events = controller.reset(AutoDockResetReason.FULL_RESTART, 7);

        assertEquals(1, events.size());
        assertEquals(TimelineEvent.EventType.OCCUPANCY_RELEASED, events.get(0).eventType());
        assertEquals(AutoDockResetReason.FULL_RESTART.name(), events.get(0).reason());
        assertFalse(controller.isDocked());
    }

    /** 假端口：单 dock，区域 [-2,2]²，合法出口 UP/DOWN。 */
    private static final class FakeDockPort implements AutoDockReadPort, AutoDockOccupancyPort {

        private final String id = "L01_plate_left";
        private final DockRegionView region = new DockRegionView(-2.0, -2.0, 2.0, 2.0, 0.0001);
        private final Set<PathNode.Dir> exits = Set.of(PathNode.Dir.UP, PathNode.Dir.DOWN);

        private String occupantId;
        private int occupantRound;
        private long occupiedAt = -1;

        AutoDockView view() {
            DockOccupancyView occupancy = occupantId == null
                    ? DockOccupancyView.empty()
                    : new DockOccupancyView(true, occupantId, occupantRound, occupiedAt);
            return new AutoDockView(id, "L01_node_left_end", region, CENTER, exits, occupancy, -1);
        }

        @Override
        public Optional<AutoDockView> findById(String mechanismId) {
            return id.equals(mechanismId) ? Optional.of(view()) : Optional.empty();
        }

        @Override
        public Optional<AutoDockView> findNearest(Vector2D worldPosition, double maxDistance) {
            return Math.sqrt(region.distanceSquared(worldPosition)) <= maxDistance + region.epsilon()
                    ? Optional.of(view())
                    : Optional.empty();
        }

        @Override
        public List<AutoDockView> snapshot() {
            return List.of(view());
        }

        @Override
        public AutoDockResult tryEnter(String mechanismId, String actorId, int sourceRound,
                                       long tick, Vector2D worldPosition) {
            if (!id.equals(mechanismId)) {
                return new AutoDockResult(AutoDockResult.Status.UNKNOWN_DOCK, tick, null);
            }
            if (!region.contains(worldPosition)) {
                return new AutoDockResult(AutoDockResult.Status.OUTSIDE_REGION, tick, view());
            }
            if (occupantId != null) {
                return new AutoDockResult(AutoDockResult.Status.ALREADY_OCCUPIED, tick, view());
            }
            occupantId = actorId;
            occupantRound = sourceRound;
            occupiedAt = tick;
            return new AutoDockResult(AutoDockResult.Status.ENTERED, tick, view());
        }

        @Override
        public AutoDockResult tryLeave(String mechanismId, String actorId, int sourceRound,
                                       long tick, PathNode.Dir exitDirection, Vector2D worldPosition) {
            if (occupantId == null || !occupantId.equals(actorId) || occupantRound != sourceRound) {
                return new AutoDockResult(AutoDockResult.Status.NOT_OCCUPANT, tick, view());
            }
            if (exitDirection == null || !exits.contains(exitDirection)) {
                return new AutoDockResult(AutoDockResult.Status.INVALID_EXIT_DIRECTION, tick, view());
            }
            if (region.contains(worldPosition)) {
                return new AutoDockResult(AutoDockResult.Status.NOT_OUTSIDE_REGION, tick, view());
            }
            occupantId = null;
            occupantRound = 0;
            occupiedAt = -1;
            return new AutoDockResult(AutoDockResult.Status.LEFT, tick, view());
        }

        @Override
        public int releaseActor(String actorId, int sourceRound, long tick) {
            if (occupantId != null && occupantId.equals(actorId) && occupantRound == sourceRound) {
                occupantId = null;
                occupantRound = 0;
                occupiedAt = -1;
                return 1;
            }
            return 0;
        }

        @Override
        public void reset(AutoDockResetReason reason, long tick) {
            occupantId = null;
            occupantRound = 0;
            occupiedAt = -1;
        }
    }
}
