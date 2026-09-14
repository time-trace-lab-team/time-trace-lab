package org.example.timeloop.render;

import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R-1 状态到形状/反馈的纯投影测试；不初始化 JavaFX Toolkit。 */
class PlayerVisualProjectionTest {

    @Test
    void idleUsesARoundedSquareWithoutAMovementMarker() {
        PlayerVisualProjection.Style style = PlayerVisualProjection.forMovementState(MovementState.IDLE);

        assertEquals(PlayerVisualProjection.BodyShape.ROUNDED_SQUARE, style.bodyShape());
        assertFalse(style.showsDirectionTick());
        assertFalse(style.showsSlowOutline());
        assertFalse(style.showsSlowTrail());
    }

    @Test
    void cruisingUsesTheNormalCircularBodyAndDirectionTick() {
        PlayerVisualProjection.Style style = PlayerVisualProjection.forMovementState(MovementState.CRUISING);

        assertEquals(PlayerVisualProjection.BodyShape.CIRCLE, style.bodyShape());
        assertTrue(style.showsDirectionTick());
        assertFalse(style.showsSlowOutline());
        assertFalse(style.showsSlowTrail());
    }

    @Test
    void slowedKeepsDirectionAndAddsTwoNonColorOnlyCues() {
        PlayerVisualProjection.Style style = PlayerVisualProjection.forMovementState(MovementState.SLOWED);

        assertEquals(PlayerVisualProjection.BodyShape.CIRCLE, style.bodyShape());
        assertTrue(style.showsDirectionTick());
        assertTrue(style.showsSlowOutline());
        assertTrue(style.showsSlowTrail());
    }

    @Test
    void dockedUsesTheStationaryShapeWithoutSlowFeedback() {
        PlayerVisualProjection.Style style = PlayerVisualProjection.forMovementState(MovementState.DOCKED);

        assertEquals(PlayerVisualProjection.BodyShape.ROUNDED_SQUARE, style.bodyShape());
        assertTrue(style.showsDirectionTick());
        assertFalse(style.showsSlowOutline());
        assertFalse(style.showsSlowTrail());
    }

    @Test
    void everyMovementStateAndPhaseCombinationHasAnExplicitProjection() {
        for (MovementState movementState : EnumSet.allOf(MovementState.class)) {
            for (boolean phased : List.of(false, true)) {
                PlayerVisualProjection.Style style = PlayerVisualProjection.forPlayer(movementState, phased);

                assertNotNull(style, () -> movementState + "/phased=" + phased);
                assertEquals(phased, style.showsPhaseRing(), () -> movementState + "/phased=" + phased);
                assertEquals(phased ? 0.62 : 1.0, style.bodyHeightScale(), 0.0001,
                        () -> movementState + "/phased=" + phased);
                assertEquals(phased ? 0.58 : 1.0, style.bodyAlpha(), 0.0001,
                        () -> movementState + "/phased=" + phased);
                switch (movementState) {
                    case IDLE -> {
                        assertEquals(PlayerVisualProjection.BodyShape.ROUNDED_SQUARE, style.bodyShape());
                        assertFalse(style.showsDirectionTick());
                        assertFalse(style.showsSlowOutline());
                        assertFalse(style.showsSlowTrail());
                    }
                    case CRUISING -> {
                        assertEquals(PlayerVisualProjection.BodyShape.CIRCLE, style.bodyShape());
                        assertTrue(style.showsDirectionTick());
                        assertFalse(style.showsSlowOutline());
                        assertFalse(style.showsSlowTrail());
                    }
                    case SLOWED -> {
                        assertEquals(PlayerVisualProjection.BodyShape.CIRCLE, style.bodyShape());
                        assertTrue(style.showsDirectionTick());
                        assertTrue(style.showsSlowOutline());
                        assertTrue(style.showsSlowTrail());
                    }
                    case DOCKED -> {
                        assertEquals(PlayerVisualProjection.BodyShape.ROUNDED_SQUARE, style.bodyShape());
                        assertTrue(style.showsDirectionTick());
                        assertFalse(style.showsSlowOutline());
                        assertFalse(style.showsSlowTrail());
                    }
                }
            }
        }
    }
}
