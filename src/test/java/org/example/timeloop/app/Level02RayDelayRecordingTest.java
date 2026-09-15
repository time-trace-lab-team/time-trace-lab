package org.example.timeloop.app;

import org.example.timeloop.replay.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R4 写入端：一次射线命中必须作为 {@code RAY_DELAY} 事件进入本轮记录（Δt 靠录制、不靠回放重算）。 */
class Level02RayDelayRecordingTest {

    @Test
    void hitIsWrittenIntoTheCurrentRecordingAsRayDelay() {
        Level02Assembly assembly = new Level02Assembly();
        try {
            assembly.start();
            assembly.recordRayDelay("L02_ray_01", 0, 60);

            List<TimelineEvent> events = assembly.currentRecording().orElseThrow().eventsAt(0);
            TimelineEvent delay = events.stream()
                    .filter(event -> event.eventType() == TimelineEvent.EventType.RAY_DELAY)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("命中未写入 RAY_DELAY，实际 " + events));

            assertEquals("L02_ray_01", delay.mechanismId(), "事件必须带射线 id");
            assertEquals("player", delay.actorId(), "只对当前玩家结算");
            assertEquals(0, delay.sourceRound(), "来源轮次必须是活玩家(0)");
            assertEquals("delay=60", delay.reason(), "Δt 数值放在 reason，不新增记录字段");
            assertTrue(delay.tick() == 0, "事件刻必须等于写入刻");
        } finally {
            assembly.cleanup();
        }
    }
}