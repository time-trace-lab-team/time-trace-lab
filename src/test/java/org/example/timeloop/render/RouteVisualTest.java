package org.example.timeloop.render;

import org.example.timeloop.level.model.Vector2D;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteVisualTest {

    @Test
    void copiesStaticWorldPointsAndNormalizesNoJunctionRoute() {
        List<Vector2D> source = new ArrayList<>(List.of(new Vector2D(0.0, 0.0), new Vector2D(48.0, 0.0)));
        RouteVisual route = new RouteVisual("control", RouteKind.CONTROL_E1, source, null);
        source.clear();

        assertEquals(2, route.worldPoints().size());
        assertEquals("", route.branchNodeId());
    }

    @Test
    void rejectsIncompleteOrInvalidStaticGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteVisual(" ", RouteKind.CONTROL_E1,
                        List.of(new Vector2D(0.0, 0.0), new Vector2D(1.0, 1.0)), "J"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteVisual("route", RouteKind.CONTROL_E1, List.of(new Vector2D(0.0, 0.0)), "J"));
        assertThrows(NullPointerException.class,
                () -> new RouteVisual("route", null,
                        List.of(new Vector2D(0.0, 0.0), new Vector2D(1.0, 1.0)), "J"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteJunctionVisual("J", new Vector2D(Double.NaN, 0.0)));
    }
}
