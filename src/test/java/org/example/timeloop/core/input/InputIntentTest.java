package org.example.timeloop.core.input;

import org.example.timeloop.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3 输入契约测试：不可变性、边沿/按住的区分、方向边沿末位胜出、tick 校验。
 */
class InputIntentTest {

    @Test
    void emptyIntentHasNoEdgesAndNoHeldKeys() {
        InputIntent intent = InputIntent.empty(7);
        assertEquals(7L, intent.tick());
        assertFalse(intent.hasDirectionEdge());
        assertTrue(intent.lastDirectionEdge().isEmpty());
        assertTrue(intent.pressedThisTick().isEmpty());
        assertTrue(intent.held().isEmpty());
    }

    @Test
    void edgeAndHeldAreDistinct() {
        InputIntent intent = new InputIntent(
                3,
                Set.of(LogicalKey.DIR_UP),
                Set.of(),
                Set.of(LogicalKey.DIR_UP, LogicalKey.INTERACT),
                List.of(Direction.UP));

        assertTrue(intent.isPressed(LogicalKey.DIR_UP));
        assertTrue(intent.isHeld(LogicalKey.DIR_UP));
        // 按住但非本刻新按下：不得产生边沿动作
        assertFalse(intent.isPressed(LogicalKey.INTERACT));
        assertTrue(intent.isHeld(LogicalKey.INTERACT));
    }

    @Test
    void lastDirectionEdgeWins() {
        InputIntent intent = new InputIntent(
                1,
                Set.of(LogicalKey.DIR_UP, LogicalKey.DIR_LEFT),
                Set.of(),
                Set.of(LogicalKey.DIR_UP, LogicalKey.DIR_LEFT),
                List.of(Direction.UP, Direction.LEFT));

        assertEquals(Direction.LEFT, intent.lastDirectionEdge().orElseThrow());
    }

    @Test
    void collectionsAreImmutableCopies() {
        List<Direction> edges = new ArrayList<>(List.of(Direction.UP));
        InputIntent intent = new InputIntent(0, Set.of(), Set.of(), Set.of(), edges);

        // 修改原集合不影响意图
        edges.add(Direction.DOWN);
        assertEquals(List.of(Direction.UP), intent.directionEdges());

        assertThrows(UnsupportedOperationException.class, () -> intent.directionEdges().add(Direction.DOWN));
        assertThrows(UnsupportedOperationException.class, () -> intent.held().add(LogicalKey.PHASE));
    }

    @Test
    void negativeTickIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> InputIntent.empty(-1));
    }

    @Test
    void nullCollectionsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new InputIntent(0, null, Set.of(), Set.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new InputIntent(0, Set.of(), Set.of(), Set.of(), null));
    }
}
