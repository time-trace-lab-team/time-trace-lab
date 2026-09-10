package org.example.timeloop.replay;

import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EchoLifetimeManager {

    /** sourceRound 升序，保证同一 tick 的残影展示与事件顺序稳定。 */
    private final Map<Integer, EchoLifetime> echoes = new TreeMap<>();
    private int currentRound = 0;
    private long durationTicks = 60;
    private int maxRounds = 4;
    private int lifetimeRounds = 2;

    public void initialize(long durationTicks, int maxRounds, int lifetimeRounds) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks 必须 >= 1，实际 " + durationTicks);
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds 必须 >= 1，实际 " + maxRounds);
        }
        if (lifetimeRounds < 1) {
            throw new IllegalArgumentException("lifetimeRounds 必须 >= 1，实际 " + lifetimeRounds);
        }
        this.durationTicks = durationTicks;
        this.maxRounds = maxRounds;
        this.lifetimeRounds = lifetimeRounds;
        this.currentRound = 0;
        this.echoes.clear();
    }

    public void startRound() {
        advanceRound(0);
    }

    public void advanceRound(long roundTick) {
        validateRoundTick(roundTick);
        if (currentRound >= maxRounds) {
            throw new IllegalStateException("已处于最大轮次，不能继续开始新轮次");
        }
        currentRound++;
        updateAllEchoes(roundTick);
    }

    public void endRound(long roundTick) {
        validateRoundTick(roundTick);
        if (currentRound < 1) {
            throw new IllegalStateException("尚未开始轮次，不能结束");
        }
        if (currentRound >= maxRounds) {
            throw new IllegalStateException("最终轮不能生成无法使用的新残影");
        }
        List<Integer> toDisappear = getEchoesToDisappear();
        for (int sourceRound : toDisappear) {
            GameEvent event = GameEvent.echoDisappeared(
                    "echo_" + sourceRound, roundTick, sourceRound);
            EventDispatcher.getInstance().dispatch(event);
        }
        toDisappear.forEach(echoes::remove);
        addEcho(currentRound);
    }

    public void addEcho(int sourceRound) {
        EchoLifetime lifetime = new EchoLifetime(
                sourceRound, lifetimeRounds, durationTicks, currentRound, 0);
        echoes.put(sourceRound, lifetime);
    }

    public void updateRoundTick(long roundTick) {
        validateRoundTick(roundTick);
        updateAllEchoes(roundTick);
    }

    private void updateAllEchoes(long roundTick) {
        for (Map.Entry<Integer, EchoLifetime> entry : echoes.entrySet()) {
            int sourceRound = entry.getKey();
            EchoLifetime updated = new EchoLifetime(
                    sourceRound, lifetimeRounds, durationTicks, currentRound, roundTick);
            entry.setValue(updated);
        }
    }

    public EchoLifetime getEcho(int sourceRound) {
        return echoes.get(sourceRound);
    }

    public List<EchoLifetime> getAllActiveEchoes() {
        List<EchoLifetime> active = new ArrayList<>();
        for (EchoLifetime lifetime : echoes.values()) {
            if (lifetime.isActive()) {
                active.add(lifetime);
            }
        }
        return active;
    }

    public List<Integer> getEchoesToDisappear() {
        List<Integer> toDisappear = new ArrayList<>();
        for (Map.Entry<Integer, EchoLifetime> entry : echoes.entrySet()) {
            if (entry.getValue().shouldDisappearAtRoundEnd()) {
                toDisappear.add(entry.getKey());
            }
        }
        return toDisappear;
    }

    public int getCurrentRound() { return currentRound; }
    public int getMaxRounds() { return maxRounds; }

    public void reset() {
        echoes.clear();
        currentRound = 0;
    }

    private void validateRoundTick(long roundTick) {
        if (roundTick < 0 || roundTick >= durationTicks) {
            throw new IllegalArgumentException(
                    "roundTick 必须在 [0, " + (durationTicks - 1) + "]，实际 " + roundTick);
        }
    }
}
