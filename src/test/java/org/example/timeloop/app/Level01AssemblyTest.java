package org.example.timeloop.app;

import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装配无头测试（不初始化 JavaFX）：验证第一关装配可构造、可开始、可逐刻推进、可出渲染视图。
 */
class Level01AssemblyTest {

    @AfterEach
    void clearGlobalMechanismState() {
        EventDispatcher.getInstance().clear();
        DockingPlateRegistry.getInstance().clear();
    }

    @Test
    void assemblyStartsStepsAndProducesRenderViews() {
        Level01Assembly assembly = new Level01Assembly();

        assembly.start();
        assertTrue(assembly.isPlaying());

        for (long tick = 0; tick < 180; tick++) {
            assembly.tick(InputIntent.empty(tick));
        }

        assertNotNull(assembly.renderViews());
        assertNotNull(assembly.hudContext());
        assertTrue(assembly.hudContext().roundTick() >= 0);

        assembly.cleanup();
        assertFalse(DockingPlateRegistry.getInstance().isOccupied("L01_plate_left"));
    }

    @Test
    void hudContextReflectsFrozenLevelParameters() {
        Level01Assembly assembly = new Level01Assembly();
        assembly.start();

        assertEquals(16 * 60, assembly.hudContext().durationTicks());
        assertEquals(3, assembly.hudContext().maxRounds());
        assertEquals(1, assembly.hudContext().currentRound());

        assembly.cleanup();
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
