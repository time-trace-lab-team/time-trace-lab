package org.example.timeloop.replay;

import org.example.timeloop.core.ActorPhase;
import org.example.timeloop.core.AnimationState;
import org.example.timeloop.core.Direction;
import org.example.timeloop.core.MovementState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-DEV2-L03-ECHO-ACTOR-VIEW：残影角色只读取帧语义冻结（replay 层，不新增 DTO）。
 *
 * <p>把 {@link EchoState#frameAt(long)} 的四条契约钉进测试门禁：</p>
 * <ol>
 *   <li><b>入口唯一</b>：它是残影唯一的取帧通道（没有第二条返回 {@link PlayerFrame} 的公开方法）；</li>
 *   <li><b>逐字段保真</b>：任意刻返回的帧与录制帧在<b>全部字段</b>上一致
 *       （位置 x/y、朝向、交互边沿、移动状态、相位、相位剩余刻、动画姿态）；</li>
 *   <li><b>边界稳定</b>：刻 {@code 0} 与轮末 {@code D-1} 稳定可取；越界<b>严格拒绝</b>，
 *       绝不返回最后一帧补齐；</li>
 *   <li><b>纯只读</b>：乱序 / 重复读取结果不变 —— 不写减速、不刷新射线
 *       {@code (rayId, activeCycle)} 去重、不改变位置（无内部进度、无第二套状态）。</li>
 * </ol>
 *
 * <p><b>消散轮语义</b>：{@code EchoState} 本身不做寿命判断（寿命在
 * {@link EchoQueue} / {@code EchoLifetime}）。因此只要调用方仍在合法区间取帧，
 * {@code frameAt} 照常返回该刻录制帧；「是否继续绘制」由
 * {@link EchoQueue#activeEchoes(int)} 的活跃集合决定，见 {@code frameAt} 的 javadoc。</p>
 */
class EchoActorViewTest {

    /** 时间线长度：取小值，便于逐刻穷举。 */
    private static final int D = 8;

    /**
     * 每刻字段都在变的录制：逐字段比对时，任何「取错刻 / 补末帧 / 丢字段」都会立刻暴露。
     */
    private static TimelineRecording richRecording() {
        TimelineRecording r = new TimelineRecording(D, 1);
        for (int i = 0; i < D; i++) {
            r.record(new PlayerFrame(
                    i,
                    100.0 + i * 7.0,
                    200.0 - i * 3.0,
                    Direction.values()[i % Direction.values().length],
                    i % 2 == 0,
                    MovementState.values()[i % MovementState.values().length],
                    ActorPhase.values()[i % ActorPhase.values().length],
                    i * 2,
                    AnimationState.values()[i % AnimationState.values().length]));
        }
        r.seal();
        return r;
    }

    // ---------- 1. 取帧入口唯一 ----------

    @Test
    void frameAtIsTheOnlyPublicFrameAccessor() {
        Set<String> frameAccessors = Arrays.stream(EchoState.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> PlayerFrame.class.equals(m.getReturnType()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("frameAt"), frameAccessors,
                "只允许 frameAt 返回 PlayerFrame：残影取帧入口必须唯一，不得新增旁路");
    }

    // ---------- 2. 逐字段保真 ----------

    @Test
    void frameAtMatchesRecordedFrameFieldByFieldAtEveryTick() {
        TimelineRecording r = richRecording();
        EchoState echo = EchoState.of(r);

        for (int t = 0; t < D; t++) {
            PlayerFrame expected = r.frameAt(t);
            PlayerFrame actual = echo.frameAt(t);
            String at = " @tick " + t;

            assertEquals(expected.tick(), actual.tick(), "tick" + at);
            assertEquals(expected.x(), actual.x(), 1e-9, "x" + at);
            assertEquals(expected.y(), actual.y(), 1e-9, "y" + at);
            assertEquals(expected.direction(), actual.direction(), "direction" + at);
            assertEquals(expected.interacting(), actual.interacting(), "interacting" + at);
            assertEquals(expected.movementState(), actual.movementState(), "movementState" + at);
            assertEquals(expected.actorPhase(), actual.actorPhase(), "actorPhase" + at);
            assertEquals(expected.actorPhaseTicksRemaining(), actual.actorPhaseTicksRemaining(),
                    "actorPhaseTicksRemaining" + at);
            assertEquals(expected.animationState(), actual.animationState(), "animationState" + at);
        }
    }

    // ---------- 3. 边界 tick ----------

    @Test
    void frameAtIsStableAtFirstAndLastTick() {
        TimelineRecording r = richRecording();
        EchoState echo = EchoState.of(r);

        assertEquals(r.frameAt(0), echo.frameAt(0), "刻 0 必须与录制首帧一致");
        assertEquals(r.frameAt(D - 1), echo.frameAt(D - 1), "轮末边界刻必须与录制末帧一致");
        assertSame(r.frameAt(0), echo.frameAt(0), "直接回传录制帧：不复制、不重算");
        assertSame(r.frameAt(D - 1), echo.frameAt(D - 1));
    }

    @Test
    void frameAtRejectsOutOfRangeTick() {
        EchoState echo = EchoState.of(richRecording());

        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(D),
                "不存在索引 D 的帧");
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(D + 1L));
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(-1L));
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(Long.MAX_VALUE));
    }

    @Test
    void frameAtNeverSubstitutesLastFrameForOtherTicks() {
        TimelineRecording r = richRecording();
        EchoState echo = EchoState.of(r);
        PlayerFrame last = r.frameAt(D - 1);

        for (int t = 0; t < D - 1; t++) {
            assertNotEquals(last, echo.frameAt(t),
                    "刻 " + t + " 不得返回末帧：禁止用最后一帧补齐 / 兜底");
        }
    }

    // ---------- 4. 纯只读：回放不重算 ----------

    @Test
    void frameAtIsPureReadOnlyRegardlessOfReadOrder() {
        TimelineRecording r = richRecording();
        EchoState echo = EchoState.of(r);

        // 逆序读：结果仍逐刻正确（说明没有内部游标 / 播放进度）
        for (int t = D - 1; t >= 0; t--) {
            assertEquals(r.frameAt(t), echo.frameAt(t), "逆序读 @tick " + t);
        }
        // 重复读同一刻：返回同一个不可变对象
        for (int k = 0; k < 5; k++) {
            assertSame(echo.frameAt(3), echo.frameAt(3));
        }
        // 读取不得改变录制本身（不解封、不增删）
        assertEquals(D, r.size(), "取帧不得改变录制");
        assertTrue(r.isSealed(), "取帧不得解封录制");
        for (int t = 0; t < D; t++) {
            assertEquals(r.frameAt(t), echo.frameAt(t), "多轮读取后仍逐刻一致 @tick " + t);
        }
    }

    @Test
    void echoStateHoldsNoMutableStateBeyondTheRecording() {
        Field[] fields = EchoState.class.getDeclaredFields();

        assertEquals(1, fields.length,
                "EchoState 只允许持有 recording 一个字段：不得有第二套时钟 / 减速 / 射线去重状态");
        assertEquals(TimelineRecording.class, fields[0].getType());
        assertTrue(Modifier.isFinal(fields[0].getModifiers()),
                "recording 必须 final（播放体不可变）");
    }

    // ---------- 5. 消散轮 / 寿命无关 ----------

    @Test
    void frameAtRemainsReadableAcrossTheWholeTimelineRegardlessOfLifetime() {
        EchoState echo = EchoState.of(richRecording());

        // EchoState 不做寿命判断：整条时间线逐刻都必须有帧
        // （不会因「最后有效轮 / 已消散」出现空档、null 或异常）
        for (int t = 0; t < D; t++) {
            assertNotNull(echo.frameAt(t), "刻 " + t + " 必须可取帧（寿命判断不在 replay 层）");
        }
        // 但越界依旧严格拒绝 —— 不因「可能已消散」而放宽成「返回末帧」
        assertThrows(IndexOutOfBoundsException.class, () -> echo.frameAt(D),
                "越界必须拒绝，绝不因消散而返回最后一帧");
    }
}
