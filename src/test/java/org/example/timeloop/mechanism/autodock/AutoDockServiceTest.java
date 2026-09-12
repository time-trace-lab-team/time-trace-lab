package org.example.timeloop.mechanism.autodock;

import org.example.timeloop.level.Level01Footsteps;
import org.example.timeloop.level.model.EntitySpawnInfo;
import org.example.timeloop.level.model.LevelData;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.level.model.TileType;
import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDockServiceTest {

    @Test
    void level01ExposesImmutableDockViews() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());

        List<AutoDockView> views = service.snapshot();
        assertEquals(List.of("L01_plate_left", "L01_plate_right"),
                views.stream().map(AutoDockView::mechanismId).toList());

        AutoDockView left = service.findById("L01_plate_left").orElseThrow();
        assertEquals("L01_node_left_end", left.pathNodeId());
        assertEquals(new Vector2D(120.0, 264.0), left.center());
        assertEquals(Set.of(PathNode.Dir.UP), left.legalExitDirections());
        assertFalse(left.occupancy().occupied());
        assertTrue(left.region().contains(left.center()));
        assertThrows(UnsupportedOperationException.class, () -> views.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> left.legalExitDirections().clear());
    }

    @Test
    void regionBoundaryIsInclusiveAndOutsideIsRejected() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        DockRegionView region = service.findById("L01_plate_left").orElseThrow().region();

        assertTrue(region.contains(new Vector2D(region.minX(), region.minY())));
        assertTrue(region.contains(new Vector2D(region.maxX(), region.maxY())));
        assertFalse(region.contains(new Vector2D(region.minX() - 2.0 * region.epsilon(), region.minY())));

        AutoDockResult result = service.tryEnter(
                "L01_plate_left", "player", 1, 0, new Vector2D(region.minX() - 1.0, region.minY()));
        assertEquals(AutoDockResult.Status.OUTSIDE_REGION, result.status());
    }

    @Test
    void nearestQueryUsesDistanceThenStableIdTieBreak() {
        AutoDockService service = serviceWithDocks(
                dock("L01_plate_z", "L01_node_z", 48.0, 48.0),
                dock("L01_plate_a", "L01_node_a", 144.0, 48.0));

        assertEquals("L01_plate_a", service.findNearest(new Vector2D(96.0, 48.0), 24.0)
                .orElseThrow().mechanismId());
        assertTrue(service.findNearest(new Vector2D(96.0, 48.0), 23.0).isEmpty());
        assertTrue(service.findNearest(new Vector2D(0.0, 0.0), 1.0).isEmpty());
    }

    @Test
    void oneDockAcceptsOnlyOneActor() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);

        AutoDockResult first = service.tryEnter("L01_plate_left", "echo_1", 1, 10, center);
        AutoDockResult second = service.tryEnter("L01_plate_left", "player", 1, 10, center);

        assertEquals(AutoDockResult.Status.ENTERED, first.status());
        assertEquals(AutoDockResult.Status.ALREADY_OCCUPIED, second.status());
        assertEquals("echo_1", service.findById("L01_plate_left").orElseThrow()
                .occupancy().occupantId());
    }

    @Test
    void nonOwnerCannotReleaseDock() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        service.tryEnter("L01_plate_left", "echo_1", 1, 10, new Vector2D(120.0, 264.0));

        AutoDockResult result = service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP, new Vector2D(120.0, 239.0));

        assertEquals(AutoDockResult.Status.NOT_OCCUPANT, result.status());
        assertTrue(service.findById("L01_plate_left").orElseThrow().occupancy().occupied());
    }

    @Test
    void legalLeaveReleasesAtSameTickAndBlocksReentryUntilNextTick() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);
        service.tryEnter("L01_plate_left", "player", 1, 10, center);

        AutoDockResult left = service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP, new Vector2D(120.0, 239.0));
        AutoDockResult sameTick = service.tryEnter("L01_plate_left", "echo_1", 1, 11, center);
        AutoDockResult nextTick = service.tryEnter("L01_plate_left", "echo_1", 1, 12, center);

        assertEquals(AutoDockResult.Status.LEFT, left.status());
        assertFalse(left.view().occupancy().occupied());
        assertEquals(AutoDockResult.Status.SAME_TICK_REENTRY_BLOCKED, sameTick.status());
        assertEquals(AutoDockResult.Status.ENTERED, nextTick.status());
    }

    @Test
    void invalidExitDirectionKeepsOccupancy() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);
        service.tryEnter("L01_plate_left", "player", 1, 10, center);

        AutoDockResult invalidDirection = service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.DOWN, new Vector2D(120.0, 239.0));

        assertEquals(AutoDockResult.Status.INVALID_EXIT_DIRECTION, invalidDirection.status());
        assertTrue(service.findById("L01_plate_left").orElseThrow().occupancy().occupied());
    }

    @Test
    void legalExitInsideRegionReleasesAtSameTick() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);
        service.tryEnter("L01_plate_left", "player", 1, 10, center);

        AutoDockResult left = service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP, center);

        assertEquals(AutoDockResult.Status.LEFT, left.status(), "区域内按合法出口必须同刻释放");
        assertFalse(left.view().occupancy().occupied());
        AutoDockView view = service.findById("L01_plate_left").orElseThrow();
        assertFalse(view.occupancy().occupied());
        assertEquals(11, view.reentryBlockedAtTick());
    }

    @Test
    void nonOwnerCannotLeaveInsideRegion() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);
        service.tryEnter("L01_plate_left", "echo_1", 1, 10, center);

        AutoDockResult result = service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP, center);

        assertEquals(AutoDockResult.Status.NOT_OCCUPANT, result.status());
        assertTrue(service.findById("L01_plate_left").orElseThrow().occupancy().occupied());
    }

    @Test
    void sameTickReentryIsBlockedAfterInsideRelease() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        Vector2D center = new Vector2D(120.0, 264.0);
        service.tryEnter("L01_plate_left", "player", 1, 10, center);
        assertEquals(AutoDockResult.Status.LEFT, service.tryLeave(
                "L01_plate_left", "player", 1, 11, PathNode.Dir.UP, center).status());

        AutoDockResult sameTick = service.tryEnter("L01_plate_left", "echo_1", 1, 11, center);
        AutoDockResult nextTick = service.tryEnter("L01_plate_left", "echo_1", 1, 12, center);

        assertEquals(AutoDockResult.Status.SAME_TICK_REENTRY_BLOCKED, sameTick.status());
        assertEquals(AutoDockResult.Status.ENTERED, nextTick.status());
    }

    @Test
    void echoReleaseAndRoundResetClearOccupancy() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());
        service.tryEnter("L01_plate_left", "echo_1", 1, 10, new Vector2D(120.0, 264.0));

        assertEquals(1, service.releaseActor("echo_1", 1, 11));
        assertFalse(service.findById("L01_plate_left").orElseThrow().occupancy().occupied());

        service.tryEnter("L01_plate_left", "player", 1, 12, new Vector2D(120.0, 264.0));
        service.reset(AutoDockResetReason.ROUND_END, 13);
        AutoDockView view = service.findById("L01_plate_left").orElseThrow();
        assertFalse(view.occupancy().occupied());
        assertEquals(-1, view.reentryBlockedAtTick());
    }

    @Test
    void unknownDockReturnsEmptyOrExplicitStatus() {
        AutoDockService service = new AutoDockService(Level01Footsteps.build());

        assertTrue(service.findById("L01_plate_missing").isEmpty());
        AutoDockResult result = service.tryEnter(
                "L01_plate_missing", "player", 1, 0, new Vector2D(0.0, 0.0));
        assertEquals(AutoDockResult.Status.UNKNOWN_DOCK, result.status());
        assertNotNull(result);
    }

    @Test
    void overlappingDockRegionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> serviceWithDocks(
                dock("L01_plate_left", "L01_node_left", 48.0, 48.0),
                dock("L01_plate_right", "L01_node_right", 60.0, 48.0)));
    }

    private static EntitySpawnInfo dock(String id, String pathNodeId, double x, double y) {
        return new EntitySpawnInfo(id, "dock_plate", new Vector2D(x, y), pathNodeId)
                .putProp("autoDock", true);
    }

    private static AutoDockService serviceWithDocks(EntitySpawnInfo... docks) {
        List<PathNode> nodes = List.of(
                new PathNode("L01_node_z", new Vector2D(48.0, 48.0), EnumSet.of(PathNode.Dir.UP)),
                new PathNode("L01_node_a", new Vector2D(144.0, 48.0), EnumSet.of(PathNode.Dir.UP)),
                new PathNode("L01_node_left", new Vector2D(48.0, 48.0), EnumSet.of(PathNode.Dir.UP)),
                new PathNode("L01_node_right", new Vector2D(60.0, 48.0), EnumSet.of(PathNode.Dir.UP))
        );
        return new AutoDockService(new LevelData(
                48.0,
                new TileType[0][0],
                nodes,
                List.of(docks),
                List.of(),
                new Vector2D(0.0, 0.0),
                960,
                3,
                1
        ));
    }
}
