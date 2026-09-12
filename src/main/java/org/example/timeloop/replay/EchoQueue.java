package org.example.timeloop.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 双残影滑动窗口容器（R4 基础，开发 2 主责）。
 *
 * <p>容量固定为 2（README「多残影的代际与寿命」）；每个残影参与它创建后的
 * {@code L} 个完整轮次，<b>只在轮次边界加入或淘汰</b>。令来源轮次为 {@code s}、
 * 当前轮次为 {@code r}：{@code active = 1 <= r - s <= L}。
 * 滑动窗口（L=2）：第 3 轮为 E1+E2，第 4 轮为 E2+E3。</p>
 *
 * <p>本类只做成员管理：不重算寿命公式（委托 {@link EchoLifetime}）、
 * 不驱动轮末事务（{@link RecordingSession} 的职责）、不处理消散视觉
 * （RESETTING 过渡属表现层）。关卡只配置 {@code L}，容量不可覆盖。</p>
 */
public final class EchoQueue {

    /** 残影容量，README 固定为 2，关卡不可覆盖。 */
    public static final int CAPACITY = 2;

    private final int lifetimeRounds;
    /** sourceRound -> 残影；TreeMap 保证按来源轮次稳定升序。 */
    private final Map<Integer, EchoState> echoes = new TreeMap<>();

    /**
     * @param lifetimeRounds 整轮寿命 L（第一/二关 1，第三至五关 2），进关冻结
     */
    public EchoQueue(int lifetimeRounds) {
        if (lifetimeRounds < 1) {
            throw new IllegalArgumentException("lifetimeRounds 必须 >= 1，实际 " + lifetimeRounds);
        }
        if (lifetimeRounds > CAPACITY) {
            throw new IllegalArgumentException(
                    "lifetimeRounds 不能超过容量 " + CAPACITY + "，实际 " + lifetimeRounds);
        }
        this.lifetimeRounds = lifetimeRounds;
    }

    /** 整轮寿命 L。 */
    public int lifetimeRounds() {
        return lifetimeRounds;
    }

    /**
     * 轮末事务调用：加入本轮生成的残影，并按整轮寿命淘汰在下一轮已超龄的残影。
     *
     * <p>淘汰条件：{@code nextRound - sourceRound > L}。例（L=2）：第 3 轮末
     * 加入 E3 且 nextRound=4 时，4-1=3&gt;2，E1 被淘汰 —— 与 README 表格一致。</p>
     *
     * <p><b>淘汰观察契约（X-MOVE-COLLAPSE-01 · E-4）</b>：返回本次被淘汰的残影
     * 列表（按来源轮次升序、只读），供调用方（app）在轮末边界以「残影消失」语义派发
     * 机关释放。淘汰发生刻 = <b>轮末边界刻</b>（刚结束那轮的最后一刻，与
     * {@code completeNormalRound} 的事务刻一致）。列表同时覆盖<b>寿命淘汰</b>（超龄）
     * 与<b>容量淘汰</b>（第 3 个残影入队时最旧的被挤出；L=CAPACITY=2 时二者等价，
     * 都表现为最旧的 sourceRound 被移除）。</p>
     *
     * @param echo      本轮生成的残影（来源轮次必须等于刚结束的这一轮）
     * @param nextRound 即将进入的轮次（刚结束轮次 + 1）
     * @return 本次被淘汰的残影（可能为空列表，只读）
     * @throws IllegalArgumentException 来源轮次重复
     * @throws IllegalStateException    容量越界（淘汰窗口失效）
     */
    public List<EchoState> addOnRoundEnd(EchoState echo, int nextRound) {
        Objects.requireNonNull(echo, "echo");
        int sourceRound = echo.sourceRound();
        if (echoes.containsKey(sourceRound)) {
            throw new IllegalArgumentException(
                    "来源轮次重复：" + sourceRound + "，一轮只能生成一条记录");
        }
        echoes.put(sourceRound, echo);
        List<EchoState> evicted = new ArrayList<>();
        echoes.entrySet().removeIf(e -> {
            if (nextRound - e.getKey() > lifetimeRounds) {
                evicted.add(e.getValue());
                return true;
            }
            return false;
        });
        if (echoes.size() > CAPACITY) {
            throw new IllegalStateException(
                    "残影数量 " + echoes.size() + " 超过容量 " + CAPACITY
                            + "：淘汰窗口失效，请检查 L 与轮次推进");
        }
        return List.copyOf(evicted);
    }

    /**
     * 当前轮参与行动的残影，按来源轮次升序（较旧在前）。
     * 活跃判定：{@code 1 <= currentRound - sourceRound <= L}，与公式一致。
     *
     * @param currentRound 当前轮次
     * @return 只读列表，长度不超过 {@link #CAPACITY}
     */
    public List<EchoState> activeEchoes(int currentRound) {
        List<EchoState> active = new ArrayList<>();
        for (Map.Entry<Integer, EchoState> e : echoes.entrySet()) {
            int age = currentRound - e.getKey();
            if (age >= 1 && age <= lifetimeRounds) {
                active.add(e.getValue());
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * 当前轮所有活跃残影的只读寿命视图，按来源轮次升序。
     * 轮次与刻取自共享 {@link TickContext}，不维护任何独立进度。
     *
     * @param context 共享时间上下文（来自 {@link RoundClock#toContext()}）
     * @return 与 {@link #activeEchoes(int)} 同序的寿命视图
     */
    public List<EchoLifetime> lifetimes(TickContext context) {
        Objects.requireNonNull(context, "context");
        List<EchoLifetime> views = new ArrayList<>();
        for (EchoState echo : activeEchoes(context.currentRound())) {
            views.add(EchoLifetime.of(echo.sourceRound(), lifetimeRounds, context));
        }
        return Collections.unmodifiableList(views);
    }

    /** 当前持有（含已过期待边界淘汰的）残影数量。 */
    public int size() {
        return echoes.size();
    }

    public boolean isEmpty() {
        return echoes.isEmpty();
    }

    /** 完整重开/退出关卡：清空全部残影（关卡会话状态的一种）。 */
    public void clear() {
        echoes.clear();
    }
}
