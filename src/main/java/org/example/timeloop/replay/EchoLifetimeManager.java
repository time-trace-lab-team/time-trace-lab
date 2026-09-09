package org.example.timeloop.replay;

import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EchoLifetimeManager {

    private final Map<Integer, EchoLifetime> echoes = new ConcurrentHashMap<>();
    private int currentRound = 0;
    private long durationTicks = 60;
    private int maxRounds = 4;
    private int lifetimeRounds = 2;

    public void initialize(long durationTicks, int maxRounds, int lifetimeRounds) {
        this.durationTicks = durationTicks;
        this.maxRounds = maxRounds;
        this.lifetimeRounds = lifetimeRounds;
        this.currentRound = 0;
        this.echoes.clear();
        EventDispatcher.getInstance().clear();
    }

    public void startRound() {
        currentRound++;
        System.out.println("=== 第 " + currentRound + " 轮开始 ===");

        for (Map.Entry<Integer, EchoLifetime> entry : echoes.entrySet()) {
            int sourceRound = entry.getKey();
            EchoLifetime updated = new EchoLifetime(
                    sourceRound, lifetimeRounds, durationTicks, currentRound, 0
            );
            entry.setValue(updated);
        }

        echoes.entrySet().removeIf(entry -> !entry.getValue().isActive());
    }

    public void endRound(long roundTick) {
        System.out.println("第 " + currentRound + " 轮结束");

        // 发布消散事件
        List<Integer> toDisappear = getEchoesToDisappear();
        for (int sourceRound : toDisappear) {
            GameEvent event = GameEvent.echoDisappeared(
                    "echo_" + sourceRound,
                    roundTick,
                    sourceRound
            );
            EventDispatcher.getInstance().dispatch(event);
            System.out.println("发布消散事件: E" + sourceRound);
        }

        addEcho(currentRound);
    }

    public void addEcho(int sourceRound) {
        EchoLifetime lifetime = new EchoLifetime(
                sourceRound, lifetimeRounds, durationTicks, currentRound, 0
        );
        echoes.put(sourceRound, lifetime);
        System.out.println("生成 E" + sourceRound);
    }

    public void updateRoundTick(long roundTick) {
        for (Map.Entry<Integer, EchoLifetime> entry : echoes.entrySet()) {
            int sourceRound = entry.getKey();
            EchoLifetime updated = new EchoLifetime(
                    sourceRound, lifetimeRounds, durationTicks, currentRound, roundTick
            );
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

    public int getCurrentRound() {
        return currentRound;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void reset() {
        echoes.clear();
        currentRound = 0;
    }
}