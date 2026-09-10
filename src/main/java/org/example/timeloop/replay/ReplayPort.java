package org.example.timeloop.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 回放端口（R3，开发 2 主责）：供集成层每 tick 读取当前玩家帧与所有活跃残影帧。
 *
 * <p>残影只读共享 {@code roundTick} 的第 N 帧（技术指南 §3.3），
 * <b>不重新执行</b>输入、路径选择、碰撞、驻留、射线或机关逻辑。
 * 端口只读，调用方不得通过返回对象修改时间系统。</p>
 */
public final class ReplayPort {

    private final RoundClock clock;
    private final RecordingSession session;

    /**
     * @param clock   权威共享时钟（提供 roundTick / currentRound / phase）
     * @param session 当前关卡会话（提供缓冲与残影队列）
     */
    public ReplayPort(RoundClock clock, RecordingSession session) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * 当前玩家本刻的帧。
     * 若尚未 {@link RecordingSession#beginRound()}（无缓冲），返回 {@link Optional#empty()}。
     *
     * @return 当前玩家帧，或空
     */
    public Optional<PlayerFrame> currentPlayerFrame() {
        TickContext ctx = clock.toContext();
        int tick = (int) ctx.roundTick();
        return session.currentBuffer().map(buf -> buf.frameAt(tick));
    }

    /**
     * 当前轮所有活跃残影的本刻帧视图，按来源轮次升序（较旧在前）。
     * 无活跃残影时返回空列表。
     *
     * @return 不可修改的列表
     */
    public List<EchoFrameView> activeEchoFrames() {
        TickContext ctx = clock.toContext();
        int tick = (int) ctx.roundTick();
        int currentRound = ctx.currentRound();

        List<EchoState> active = session.echoQueue().activeEchoes(currentRound);
        if (active.isEmpty()) {
            return Collections.emptyList();
        }

        int lifetimeRounds = session.echoQueue().lifetimeRounds();
        List<EchoFrameView> result = new ArrayList<>(active.size());
        for (EchoState echo : active) {
            EchoLifetime life = EchoLifetime.of(echo.sourceRound(), lifetimeRounds, ctx);
            result.add(new EchoFrameView(
                    echo.sourceRound(),
                    echo.frameAt(tick),
                    life.getRemainingRounds(),
                    life.getLifeProgress(),
                    life.getBodyAlpha()
            ));
        }
        return Collections.unmodifiableList(result);
    }
}