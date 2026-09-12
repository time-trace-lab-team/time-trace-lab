package org.example.timeloop.app;

import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.mechanism.DockingPlateRegistry;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.render.RenderViews;
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

    /**
     * R-2 数据源：装配层把关卡路径节点投影成只读 {@link RenderViews.PathNodeMarker}，
     * 只含稳定 ID 与世界坐标（不含任何玩法状态），且与关卡数据逐个一致。
     */
    @Test
    void projectsStaticPathNodeMarkersFromLevelData() {
        Level01Assembly assembly = new Level01Assembly();

        java.util.Map<String, org.example.timeloop.level.model.Vector2D> expected = new java.util.HashMap<>();
        for (org.example.timeloop.level.model.PathNode node
                : org.example.timeloop.level.Level01Footsteps.build().getPathNodes()) {
            expected.put(node.getId(), node.getWorldPos());
        }

        java.util.List<RenderViews.PathNodeMarker> markers = assembly.pathNodeMarkers();
        org.junit.jupiter.api.Assertions.assertEquals(expected.size(), markers.size(),
                "路径节点数量应与关卡数据一致");
        for (RenderViews.PathNodeMarker marker : markers) {
            org.example.timeloop.level.model.Vector2D position = expected.get(marker.id());
            org.junit.jupiter.api.Assertions.assertNotNull(position, marker.id());
            org.junit.jupiter.api.Assertions.assertEquals(position.x(), marker.x(), 1e-9);
            org.junit.jupiter.api.Assertions.assertEquals(position.y(), marker.y(), 1e-9);
        }
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> markers.add(new RenderViews.PathNodeMarker("L01_node_extra", 0.0, 0.0)),
                "投影结果应为不可变列表");

        assembly.cleanup();
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
