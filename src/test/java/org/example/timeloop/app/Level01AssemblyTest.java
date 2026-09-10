package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.TickStepResult;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.level.model.PathNode;
import org.example.timeloop.mechanism.autodock.AutoDockView;
import org.example.timeloop.snapshot.MvpRenderSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Level01AssemblyTest {

    @Test
    void assemblesLevelGeometryCorePathAndStableMechanisms() {
        try (Level01Assembly assembly = Level01Assembly.create()) {
            assertEquals(17, assembly.levelData().getPathNodes().size());
            assertEquals(17, assembly.geometry().getPathNodes().size());
            assertEquals(48.0, assembly.geometry().getTileSize());
            assertEquals(48.0, assembly.pathGraph().shortestSegmentLength());
            assertEquals(Direction.DOWN, assembly.patrolController().direction());
            assertEquals("L01_node_corridor_01",
                    assembly.pathGraph()
                            .neighbor(assembly.pathGraph().node(Level01Assembly.START_NODE_ID), Direction.DOWN)
                            .orElseThrow()
                            .id());

            assertEquals(List.of("L01_plate_left", "L01_plate_right"),
                    assembly.platesById().keySet().stream().toList());
            assertEquals(List.of("L01_door_01"), assembly.doorsById().keySet().stream().toList());
            assertEquals(List.of("L01_exit_00"), assembly.exitsById().keySet().stream().toList());

            List<AutoDockView> docks = assembly.autoDockService().snapshot();
            assertEquals(List.of("L01_plate_left", "L01_plate_right"),
                    docks.stream().map(AutoDockView::mechanismId).toList());
            assertEquals("L01_node_left_end", docks.get(0).pathNodeId());
            assertEquals(java.util.Set.of(PathNode.Dir.UP), docks.get(0).legalExitDirections());
        }
    }

    @Test
    void firstTickFeedsC3RecordingReplayRenderAndHudWithoutJavaFx() {
        try (Level01Assembly assembly = Level01Assembly.create()) {
            Level01Assembly.TickResult result = assembly.tick(InputIntent.empty(0));

            assertEquals(0, result.tick());
            assertEquals(TickStepResult.ADVANCED, result.clockResult());
            assertEquals(1, assembly.recordingSession().currentBuffer().orElseThrow().size());
            assertNotNull(result.mvpRenderSnapshot().currentPlayer());
            assertEquals(0, result.mvpRenderSnapshot().currentPlayer().tick());
            assertEquals(0, result.mvpRenderSnapshot().roundTick());
            assertEquals("剩余 16 秒", result.hud().countdownText());
            assertEquals("第 1 / 3 轮", result.hud().roundText());
            assertEquals(4, result.renderViews().mechanisms().size());
            assertFalse(result.dockingDecision().isFreeze());
        }
    }

    @Test
    void assembledReadOnlyOutputsExposeCurrentSharedState() {
        try (Level01Assembly assembly = Level01Assembly.assemble()) {
            MvpRenderSnapshot initial = assembly.mvpRenderSnapshot();
            assertEquals(0, initial.roundTick());
            assertEquals(1, initial.currentRound());
            assertTrue(initial.activeEchoes().isEmpty());

            assertEquals("剩余 16 秒", assembly.hudViewModel().countdownText());
            assertEquals("第 1 / 3 轮", assembly.hudViewModel().roundText());
            assertEquals(4, assembly.renderViews().mechanisms().size());
        }
    }
}
