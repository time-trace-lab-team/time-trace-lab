package org.example.timeloop.app;

import org.example.timeloop.core.Direction;
import org.example.timeloop.core.input.InputIntent;
import org.example.timeloop.core.input.LogicalKey;
import org.example.timeloop.render.TimelineEventKind;
import org.example.timeloop.render.TimelineVisualEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R4 投影端：记录里的 RAY_DELAY 必须变成只读视觉事件（位置取权威录制帧、Δt 由 reason 解析）。 */
class Level02RayDelayProjectionTest {

    @Test
    void recordedRayDelayBecomesTimelineVisualEvent() {
        Level02Assembly assembly = new Level02Assembly();
        try {
            assembly.start();
            InputIntent held = new InputIntent(0, Set.of(LogicalKey.DIR_RIGHT), Set.of(),
                    Set.of(LogicalKey.DIR_RIGHT), List.of(Direction.RIGHT));
            assembly.tick(held);

            assembly.recordRayDelay("L02_ray_01", 0, 60);

            List<TimelineVisualEvent> visuals = assembly.timelineVisualEvents();
            TimelineVisualEvent delay = visuals.stream()
                    .filter(visual -> visual.kind() == TimelineEventKind.RAY_DELAY)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Δt 未被投影，实际 " + visuals));

            assertEquals(60, delay.delayTicks(), "Δt 数值必须由 reason 解析而来");
            assertTrue(delay.sourceRound() >= 1, "sourceRound 必须 >= 1（组件约束）");
            assertEquals(0L, delay.tick(), "刻必须与记录一致");
            assertTrue(Double.isFinite(delay.worldPosition().x())
                            && Double.isFinite(delay.worldPosition().y()),
                    "位置必须来自权威录制帧");
        } finally {
            assembly.cleanup();
        }
    }
}