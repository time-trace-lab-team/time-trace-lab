package org.example.timeloop.render;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderViewsRayProjectionTest {

    private static final RenderViews.Player PLAYER = new RenderViews.Player(
            0.0, 0.0, Direction.DOWN, MovementState.IDLE, false);

    @Test
    void compatibleFrameAndEmptyFrameHaveNoRays() {
        assertEquals(List.of(), new RenderViews.Frame(PLAYER, List.of(), List.of()).rays());
        assertEquals(List.of(), RenderViews.Frame.empty().rays());
    }

    @Test
    void frameCopiesAndSortsRaysById() {
        List<RenderViews.RayBeam> source = new ArrayList<>(List.of(
                beam("ray-z", RenderViews.RayVisualState.ACTIVE),
                beam("ray-a", RenderViews.RayVisualState.OFF)));

        RenderViews.Frame frame = new RenderViews.Frame(PLAYER, List.of(), List.of(), source);
        source.clear();

        assertEquals(List.of("ray-a", "ray-z"), frame.rays().stream().map(RenderViews.RayBeam::id).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> frame.rays().add(beam("ray-new", RenderViews.RayVisualState.WARNING)));
    }

    @Test
    void rayBeamRejectsInvalidProjectionData() {
        assertThrows(IllegalArgumentException.class,
                () -> new RenderViews.RayBeam(" ", 0, 0, 1, 1, RenderViews.RayVisualState.OFF));
        assertThrows(IllegalArgumentException.class,
                () -> new RenderViews.RayBeam("ray", Double.NaN, 0, 1, 1, RenderViews.RayVisualState.OFF));
        assertThrows(NullPointerException.class,
                () -> new RenderViews.RayBeam("ray", 0, 0, 1, 1, null));
        assertThrows(NullPointerException.class,
                () -> new RenderViews.Frame(PLAYER, List.of(), List.of(), null));
    }

    private static RenderViews.RayBeam beam(String id, RenderViews.RayVisualState state) {
        return new RenderViews.RayBeam(id, 10.0, 20.0, 70.0, 20.0, state);
    }
}
