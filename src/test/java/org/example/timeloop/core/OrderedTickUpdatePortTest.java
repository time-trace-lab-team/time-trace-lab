package org.example.timeloop.core;

import org.example.timeloop.replay.TickContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderedTickUpdatePortTest {

    private static final long NS_PER_60FPS = 16_666_667L;

    @Test
    void runsSevenStepsInOrderAndBuildsSnapshotBeforeAdvance() {
        TestRoundDriver round = new TestRoundDriver(2, 2);
        List<String> trace = new ArrayList<>();
        List<List<TickEvent>> recordedEvents = new ArrayList<>();
        List<List<TickEvent>> snapshotEvents = new ArrayList<>();

        OrderedTickUpdatePort<TickContext, TickEvent> port = new OrderedTickUpdatePort<>(
                () -> {
                    TickContext context = round.toContext();
                    trace.add("context:" + context.roundTick());
                    return context;
                },
                context -> trace.add("input:" + context.roundTick()),
                context -> trace.add("update:" + context.roundTick()),
                context -> {
                    trace.add("events:" + context.roundTick());
                    return List.of(
                            new TickEvent("late", 2),
                            new TickEvent("first", 1),
                            new TickEvent("second", 1));
                },
                Comparator.comparingInt(TickEvent::priority),
                (context, events) -> {
                    trace.add("record:" + context.roundTick() + ":" + eventIds(events));
                    recordedEvents.add(events);
                },
                (context, events) -> {
                    trace.add("snapshot:" + context.roundTick() + ":clock="
                            + round.roundTick() + ":" + eventIds(events));
                    snapshotEvents.add(events);
                },
                () -> {
                    trace.add("advance:" + round.roundTick());
                    return round.advance();
                });

        assertEquals(TickStepResult.ADVANCED, port.stepOnce());

        assertEquals(List.of(
                "context:0",
                "input:0",
                "update:0",
                "events:0",
                "record:0:[first, second, late]",
                "snapshot:0:clock=0:[first, second, late]",
                "advance:0"), trace);
        assertEquals(List.of(List.of(
                new TickEvent("first", 1),
                new TickEvent("second", 1),
                new TickEvent("late", 2))), recordedEvents);
        assertEquals(recordedEvents, snapshotEvents);
        assertThrows(UnsupportedOperationException.class,
                () -> recordedEvents.get(0).add(new TickEvent("unexpected", 0)));
        assertEquals(1L, round.roundTick(), "共享时钟只能在快照完成后推进");
    }

    @Test
    void catchUpReadsFreshContextForEveryExecutedTickAndStopsAtRoundEnd() {
        TestRoundDriver round = new TestRoundDriver(2, 2);
        List<Long> contextTicks = new ArrayList<>();
        List<Long> inputTicks = new ArrayList<>();
        List<Long> updateTicks = new ArrayList<>();
        List<Long> recordedTicks = new ArrayList<>();
        List<Long> snapshotTicks = new ArrayList<>();
        List<Long> snapshotClockTicks = new ArrayList<>();

        OrderedTickUpdatePort<TickContext, TickEvent> port = new OrderedTickUpdatePort<>(
                () -> {
                    TickContext context = round.toContext();
                    contextTicks.add(context.roundTick());
                    return context;
                },
                context -> inputTicks.add(context.roundTick()),
                context -> updateTicks.add(context.roundTick()),
                context -> List.of(),
                Comparator.comparing(TickEvent::id),
                (context, events) -> recordedTicks.add(context.roundTick()),
                (context, events) -> {
                    snapshotTicks.add(context.roundTick());
                    snapshotClockTicks.add(round.roundTick());
                },
                () -> {
                    TickStepResult result = round.advance();
                    if (result == TickStepResult.ROUND_END) {
                        // 模拟上层轮次事务；FixedStepLoop 仍须丢弃本帧剩余补算。
                        round.startNextRound();
                    }
                    return result;
                });
        FixedStepLoop loop = new FixedStepLoop(port);

        loop.onAnimationFrame(0L, GamePhase.BOOT);
        loop.onAnimationFrame(50_000_000L, GamePhase.PLAYING);

        assertEquals(List.of(0L, 1L), contextTicks,
                "每个补算 tick 都必须重新读取当前 TickContext");
        assertEquals(contextTicks, inputTicks);
        assertEquals(contextTicks, updateTicks);
        assertEquals(contextTicks, recordedTicks);
        assertEquals(contextTicks, snapshotTicks);
        assertEquals(snapshotTicks, snapshotClockTicks,
                "每个快照必须对应 advance 前的当前 tick");
        assertEquals(2, round.currentRound());
        assertEquals(0L, round.roundTick(),
                "轮次事务完成后，新一轮仍应停在 tick 0，等待下一渲染帧");

        loop.onAnimationFrame(50_000_000L + NS_PER_60FPS, GamePhase.PLAYING);

        assertEquals(List.of(0L, 1L, 0L), contextTicks,
                "ROUND_END 后不得在同一帧偷跑；下一帧应从新一轮 tick 0 开始");
    }

    private static List<String> eventIds(List<TickEvent> events) {
        return events.stream().map(TickEvent::id).toList();
    }

    private record TickEvent(String id, int priority) {
    }

    private static final class TestRoundDriver {
        private final int durationTicks;
        private final int maxRounds;
        private int currentRound = 1;
        private long roundTick;

        private TestRoundDriver(int durationTicks, int maxRounds) {
            this.durationTicks = durationTicks;
            this.maxRounds = maxRounds;
        }

        private TickContext toContext() {
            return new TickContext(roundTick, durationTicks, currentRound, maxRounds);
        }

        private TickStepResult advance() {
            if (roundTick >= durationTicks - 1L) {
                return TickStepResult.ROUND_END;
            }
            roundTick++;
            return TickStepResult.ADVANCED;
        }

        private void startNextRound() {
            currentRound++;
            roundTick = 0;
        }

        private int currentRound() {
            return currentRound;
        }

        private long roundTick() {
            return roundTick;
        }
    }
}
