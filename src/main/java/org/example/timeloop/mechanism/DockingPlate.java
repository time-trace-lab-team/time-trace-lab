package org.example.timeloop.mechanism;

import org.example.timeloop.level.StableIdValidator;
import org.example.timeloop.level.model.Vector2D;
import org.example.timeloop.mechanism.event.EventDispatcher;
import org.example.timeloop.mechanism.event.GameEvent;
import org.example.timeloop.mechanism.event.GameEventBus;
import org.example.timeloop.mechanism.event.GameObserver;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DockingPlate implements GameObserver {

    public enum State {
        UNOCCUPIED, OCCUPIED
    }

    private final String id;
    private final Vector2D position;
    private final DockingPlateOccupancyPort occupancy;
    private final GameEventBus bus;
    /** 开关变体（关卡数据 {@code role=switch}）：被踩上后锁存，直到本轮 {@link #reset()}。 */
    private final boolean latching;
    private State state = State.UNOCCUPIED;
    private String occupantId = null;
    private int occupantSourceRound = 0;
    /**
     * 板上的<b>全部</b>占用者（按进入顺序）。一块板可以同时被多个 actor 占用：
     * 玩家与残影同踩一块板是<b>合法</b>状态。
     *
     * <p>L03-DEV3 裁决前的模型只有一个槽位，第二个踩上来的人被 {@code tryEnter} 静默丢弃，
     * 于是先占槽位者一离开 / 一消散，板就整体释放、门当刻回锁 —— 画面上海有人站着，门却关了。</p>
     */
    private final Set<String> occupants = new LinkedHashSet<>();
    /** 每个占用者的来源轮（与 {@link #occupants} 同序；主占用者换人时用它给出准确来源轮）。 */
    private final Map<String, Integer> occupantSourceRounds = new LinkedHashMap<>();
    /** 锁存位：单向 {@code false -> true}，只由 {@link #reset()} / {@link #restore} 归零。 */
    private boolean latched = false;

    /**
     * 完全注入（BUG-002-LIFECYCLE）：占用注册表与事件总线都由关卡装配持有，
     * 同一实例内的板 ID 必须唯一，场景切换/重开时随装配一起丢弃。默认<b>非锁存</b>。
     *
     * <p>Phase 2 起<b>不再提供</b>取全局单例的兼容构造器。</p>
     */
    public DockingPlate(String id,
                        Vector2D position,
                        DockingPlateOccupancyPort occupancy,
                        GameEventBus bus) {
        this(id, position, occupancy, bus, false);
    }

    /**
     * 锁存开关变体（L01-GATE-MERGE-DEV3 §2.2）：关卡数据 {@code role=switch} 时由装配传
     * {@code latching = true}。该板被踩上即置锁存位，<b>离开不释放</b>，保持到本轮
     * {@link #reset()}；残影踩上同样触发（复用 {@code PLATE_ENTERED}，不新增事件类型）。
     *
     * @param latching 是否启用本轮内锁存；{@code false} 时 {@link #isLatched()} 恒为 false
     */
    public DockingPlate(String id,
                        Vector2D position,
                        DockingPlateOccupancyPort occupancy,
                        GameEventBus bus,
                        boolean latching) {
        this.id = StableIdValidator.requireMechanismId(id, "plate", "dockingPlate.id");
        this.position = Objects.requireNonNull(position);
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.latching = latching;
        occupancy.register(this);
        bus.register(GameEvent.PLATE_ENTERED, this);
        bus.register(GameEvent.PLATE_EXITED, this);
        bus.register(GameEvent.ECHO_DISAPPEARED, this);
    }

    public String getId() {
        return id;
    }

    public Vector2D getPosition() {
        return position;
    }

    public State getState() {
        return state;
    }

    public String getOccupantId() {
        return occupantId;
    }

    public int getOccupantSourceRound() {
        return occupantSourceRound;
    }

    /**
     * 板上全部占用者（按进入顺序的只读副本）。
     *
     * <p>一块板可以被多个 actor 同时占用（L03-DEV3 裁决）：{@link #getOccupantId()} 只给<b>主占用者</b>
     * （最先进入且仍在板上的那一个），要判断「玩家与残影是否都在板上」请用本方法。</p>
     */
    public List<String> getOccupantIds() {
        return List.copyOf(occupants);
    }

    /** 该 actor 此刻是否在板上。 */
    public boolean isOccupiedBy(String actorId) {
        return actorId != null && occupants.contains(actorId);
    }

    /** 板上人数（不含锁存位；锁存开关即使无人站着也可能 {@link #isOccupied()} 为真）。 */
    public int getOccupantCount() {
        return occupants.size();
    }

    /**
     * 门条件视角的判定：真实占用 <b>或</b>（开关变体）已锁存。
     *
     * <p>锁存的语义就是「即使人离开，条件依然成立」，因此必须并入本判定：否则
     * {@code Door} 依赖的 {@link DockingPlateOccupancyPort#isOccupied(String)} 会在玩家离开开关的
     * 瞬间把门重新锁上（{@code Door} 行为按卡 §三 不得修改）。</p>
     *
     * <p><b>本次语义扩展已经 PM 认可（2026-09-14，L01-GATE-MERGE-DEV3）</b>，边界如下：</p>
     * <ul>
     *   <li>只在<b>开关变体</b>（{@code latching == true}）上可能出现 {@code true} 而 {@link #getState()}
     *       为 {@code UNOCCUPIED}；普通驻留板的行为与语义<b>完全不变</b>。</li>
     *   <li>«此刻是否真有人站着»请用 {@link #getState()} 或 {@link #getOccupantIds()}；
     *       «开关是否已触发»请用 {@link #isLatched()}。</li>
     *   <li>残影淘汰（{@code ECHO_DISAPPEARED}）只释放占用，<b>不清锁存</b>：开关是本轮已兑现的事实，
     *       不随触发者消散而回滚（见 {@code DockingPlateSwitchLatchTest.echoDisappearanceReleasesOccupancyButKeepsTheLatch}）。</li>
     *   <li>锁存<b>不</b>阻止再次踩上去，重复触发是幂等的。</li>
     *   <li><b>一块板可以被多个 actor 同时占用</b>（L03-DEV3）：{@code true} 只表示「板上有人或已锁存」，
     *       所以两个 actor 同踩时它是 {@code true}，而先占者离开后它<b>仍然</b>是 {@code true}。</li>
     * </ul>
     */
    public boolean isOccupied() {
        return !occupants.isEmpty() || latched;
    }

    /** 开关变体：本轮是否已触发并锁存（与「此刻是否有人站着」无关）。非开关板恒为 {@code false}。 */
    public boolean isLatched() {
        return latched;
    }

    /**
     * 登记一个 actor 的占用（L03-DEV3：<b>一块板可被多个 actor 同时占用</b>）。
     *
     * <p>四个语义要点（"四条加严"）：</p>
     * <ol>
     *   <li><b>第二人不再被丢弃</b>：板上已有别人时，本方法照样登记并返回 {@code true}
     *       —— 旧实现返回 {@code false} 且调用方不看返回值，第二个人的存在就这么没了；</li>
     *   <li><b>主占用者 = 最先进入且仍在板上的人</b>：{@link #getOccupantId()} 与
     *       {@link #getOccupantSourceRound()} 只描述这一个（HUD/快照沿用单占用者形态），
     *       想知道"都在不在"用 {@link #getOccupantIds()};</li>
     *   <li><b>同一个 actor 重复进入是幂等 no-op</b>（返回 {@code false}，不重复计数）；</li>
     *   <li><b>锁存开关</b>：任何一次成功进入都置锁存位，单向 {@code false -> true}。</li>
     * </ol>
     *
     * @return 本次是否真的新增了一个占用者
     */
    public boolean tryEnter(String actorId, int sourceRound, long tick) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("dockPlate.actorId 不能为空白");
        }
        if (!occupants.add(actorId)) {
            return false;
        }
        occupantSourceRounds.put(actorId, sourceRound);
        if (occupantId == null) {
            // 主占用者只在"空板"时确定；后续进入者不抢主位。
            occupantId = actorId;
            occupantSourceRound = sourceRound;
        }
        state = State.OCCUPIED;
        if (latching) {
            latched = true;
        }
        bus.dispatch(GameEvent.plateEntered(id, tick, sourceRound));
        return true;
    }

    /**
     * 只移除 {@code actorId} 自己的占用；<b>别人还在板上时板保持占用</b>。
     *
     * <p>旧实现只认「唯一占用者」：非占用者的退出被拒（这一点保留），但占用者一退出就把整块板清零，
     * 于是残影还站在板上、门却回锁。现在只有当最后一个人离开，板才真正释放。</p>
     *
     * @return 本次是否真的移除了一个占用者
     */
    public boolean tryExit(String actorId, int sourceRound, long tick) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("dockPlate.actorId 不能为空白");
        }
        if (!occupants.remove(actorId)) {
            return false;
        }
        occupantSourceRounds.remove(actorId);
        if (occupants.isEmpty()) {
            state = State.UNOCCUPIED;
            occupantId = null;
            occupantSourceRound = 0;
        } else if (actorId.equals(occupantId)) {
            // 主占用者退场：把主位交给剩下最早进入的那一个（锁存位不受影响）。
            String promoted = occupants.iterator().next();
            occupantId = promoted;
            occupantSourceRound = occupantSourceRounds.getOrDefault(promoted, 0);
        }
        bus.dispatch(GameEvent.plateExited(id, tick, sourceRound));
        return true;
    }

    public void reset() {
        state = State.UNOCCUPIED;
        occupantId = null;
        occupantSourceRound = 0;
        occupants.clear();
        occupantSourceRounds.clear();
        // 普通轮末 / FULL_RESTART / 场景退出共用本方法：锁存必须一并归零（OFF）。
        latched = false;
    }

    public void dispose() {
        bus.unregisterAll(this);
        occupancy.unregister(id);
    }

    @Override
    public void onEvent(GameEvent event) {
        if (event.isType(GameEvent.PLATE_ENTERED) || event.isType(GameEvent.PLATE_EXITED)) {
            // 驻留板自身事件由 tryEnter/tryExit 处理，此处仅保留扩展
        }

        if (event.isType(GameEvent.ECHO_DISAPPEARED)) {
            int sourceRound = event.sourceRound();
            String echoId = "echo_" + sourceRound;
            // 只释放这个残影自己的占用：玩家（或另一个残影）还在板上时板必须保持占用，
            // 否则残影一到寿命、门就会在玩家脚下回锁。
            if (occupants.contains(echoId) && tryExit(echoId, sourceRound, event.tick())) {
                System.out.println("DockingPlate " + id + " 释放残影 E" + sourceRound + " 的占用");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("DockingPlate{id='%s', state=%s, occupant=%s}",
                id, state, occupantId);
    }

    // ========== Snapshot 接口 ==========

    public interface Snapshot {
        /**
         * 快照所属的稳定机制 ID。旧的自定义快照实现若未提供该字段，恢复时会被拒绝。
         */
        default String getMechanismId() { return null; }

        DockingPlate.State getState();
        String getOccupantId();
        int getOccupantSourceRound();

        /**
         * 开关变体的锁存位（L01-GATE-MERGE-DEV3 §2.3）。默认 {@code false}，
         * 使既有自定义快照实现无需改动即可继续编译。
         */
        default boolean isLatched() { return false; }

        /**
         * 板上全部占用者（L03-DEV3）。<b>默认实现</b>把单占用者形态包装成单元素列表，
         * 因此既有自定义快照实现（含开发二聚合器）不改也能继续编译；
         * 采用本方法后，多人占用的板在快照往返里才不会退化成单占用。
         */
        default List<String> getOccupantIds() {
            String single = getOccupantId();
            return single == null ? List.of() : List.of(single);
        }
    }

    /**
     * 不可变的驻留板状态快照。
     *
     * <p>{@code latched} 是 L01-GATE-MERGE 新增的<b>锁存位</b>，与 {@code state} 相互独立：
     * {@code (UNOCCUPIED, latched=true)} 是开关已触发但人已离开的<b>常态</b>组合。</p>
     */
    public record StateSnapshot(String mechanismId,
                                State state,
                                String occupantId,
                                int occupantSourceRound,
                                boolean latched,
                                List<String> occupantIds) implements Snapshot {

        public StateSnapshot {
            mechanismId = StableIdValidator.requireMechanismId(
                    mechanismId, "plate", "dockingPlate.snapshot.mechanismId");
            state = Objects.requireNonNull(state, "dockingPlate.snapshot.state");
            occupantIds = occupantIds == null ? null : List.copyOf(occupantIds);
            validateState(state, occupantId, occupantSourceRound, occupantIds);
        }

        /**
         * 兼容构造器（无锁存位）：保持既有调用方（如 {@code snapshot/MechanismSnapshot}）不改即可编译，
         * 语义等价于 {@code latched = false} + 单占用者形态。
         */
        public StateSnapshot(String mechanismId,
                             State state,
                             String occupantId,
                             int occupantSourceRound) {
            this(mechanismId, state, occupantId, occupantSourceRound, false);
        }

        /**
         * 兼容构造器（有锁存位、单占用者形态）：{@code occupantIds} 由 {@code occupantId} 派生。
         * 多人占用请改用六参构造器。
         */
        public StateSnapshot(String mechanismId,
                             State state,
                             String occupantId,
                             int occupantSourceRound,
                             boolean latched) {
            this(mechanismId, state, occupantId, occupantSourceRound, latched,
                    occupantId == null ? List.of() : List.of(occupantId));
        }

        @Override
        public String getMechanismId() { return mechanismId; }

        @Override
        public State getState() { return state; }

        @Override
        public String getOccupantId() { return occupantId; }

        @Override
        public int getOccupantSourceRound() { return occupantSourceRound; }

        @Override
        public boolean isLatched() { return latched; }

        @Override
        public List<String> getOccupantIds() { return occupantIds; }
    }

    public Snapshot createSnapshot() {
        return new StateSnapshot(id, state, occupantId, occupantSourceRound, latched,
                List.copyOf(occupants));
    }

    /**
     * 用快照恢复纯状态（不调用 {@link #tryEnter}/{@link #tryExit}，因此不产生重复 gameplay 事件）。
     *
     * <p><b>锁存位校验（L01-GATE-MERGE-DEV2 请求，2026-09-14）</b>：非开关板
     * （{@code latching == false}）不允许被恢复成 {@code latched = true} —— 否则
     * {@link #isOccupied()} 会恒为真，门被错误解锁。校验在所有状态赋值<b>之前</b>执行，
     * 因此抛异常时世界状态零变化。</p>
     *
     * @throws IllegalArgumentException 快照 ID 不匹配、状态非法，或向非开关板恢复锁存位
     */
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "dockingPlate.snapshot");
        String snapshotId = snapshot.getMechanismId();
        if (!id.equals(snapshotId)) {
            throw new IllegalArgumentException(
                    "驻留板快照 ID 不匹配: expected=" + id + ", actual=" + snapshotId);
        }

        State restoredState = Objects.requireNonNull(
                snapshot.getState(), "dockingPlate.snapshot.state");
        String restoredOccupantId = snapshot.getOccupantId();
        int restoredSourceRound = snapshot.getOccupantSourceRound();
        List<String> restoredOccupants = snapshot.getOccupantIds();
        if (restoredOccupants == null) {
            // 自定义快照实现可能不提供多人字段：退回单占用者形态。
            restoredOccupants = restoredOccupantId == null ? List.of() : List.of(restoredOccupantId);
        }
        validateState(restoredState, restoredOccupantId, restoredSourceRound, restoredOccupants);

        // 非开关板不得携带锁存位：否则 isOccupied() 恒真，门会被错误解锁。
        if (!latching && snapshot.isLatched()) {
            throw new IllegalArgumentException(
                    "非开关驻留板不能恢复锁存位: id=" + id);
        }

        // 直接恢复纯状态，不调用 tryEnter/tryExit，因而不会产生重复 gameplay 事件。
        this.state = restoredState;
        this.occupantId = restoredOccupantId;
        this.occupantSourceRound = restoredSourceRound;
        occupants.clear();
        occupantSourceRounds.clear();
        for (String actor : restoredOccupants) {
            occupants.add(actor);
            // 快照只带主占用者的来源轮；其余占用者的来源轮按 0 恢复（不参与门判定）。
            occupantSourceRounds.put(actor,
                    actor.equals(restoredOccupantId) ? restoredSourceRound : 0);
        }
        // 锁存位随快照恢复；无锁存位的旧快照（兼容构造器）等价于 false。
        this.latched = snapshot.isLatched();
    }

    /**
     * 快照校验（L03-DEV3 后加严到<b>占用者集合</b>维度）。
     *
     * <p>不止校验「有没有 occupantId」，还校验集合本身自洽：元素不得空白、不得重复；
     * 未占用必须一个占用者都没有；已占用必须至少一个，且主占用者必须在集合里。
     * 校验在所有状态赋值<b>之前</b>执行，抛异常时世界状态零变化。</p>
     */
    private static void validateState(State state, String occupantId, int sourceRound,
                                      List<String> occupantIds) {
        List<String> ids = occupantIds == null ? List.of() : occupantIds;
        Set<String> seen = new LinkedHashSet<>();
        for (String actor : ids) {
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("驻留板快照的占用者 ID 不能为空白");
            }
            if (!seen.add(actor)) {
                throw new IllegalArgumentException("驻留板快照的占用者 ID 不得重复: " + actor);
            }
        }
        if (state == State.UNOCCUPIED) {
            if (occupantId != null || sourceRound != 0 || !ids.isEmpty()) {
                throw new IllegalArgumentException("未占用驻留板快照不能带有占用者数据");
            }
            return;
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("已占用驻留板快照必须至少给出一个占用者");
        }
        if (occupantId == null || occupantId.isBlank()) {
            throw new IllegalArgumentException("已占用驻留板快照缺少 occupantId");
        }
        if (!ids.contains(occupantId)) {
            throw new IllegalArgumentException(
                    "主占用者必须包含在占用者列表里: occupantId=" + occupantId + ", ids=" + ids);
        }
        if (sourceRound < 0) {
            throw new IllegalArgumentException("驻留板快照的 sourceRound 不能为负数");
        }
    }












}
