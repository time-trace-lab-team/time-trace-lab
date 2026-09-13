package org.example.timeloop.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按 C1 约定执行一个完整逻辑 tick 的通用更新端口。
 *
 * <p>本类只编排已冻结的步骤顺序，不拥有玩家、机关、残影、记录或渲染模型。
 * 具体模型由接入方在各回调中提供，因此 {@code core} 不需要反向依赖
 * {@code replay}，也不会重复实现 replay 层的事件、记录或快照类型。</p>
 *
 * <p>每次 {@link #stepOnce()} 都按如下顺序执行：读取当前上下文、消费输入、
 * 更新玩家/机关/残影、发布并稳定排序事件、记录 tick 末状态、生成只读渲染快照，
 * 最后才推进共享时钟。上下文读取器必须提供不可变快照；事件列表在排序后会复制为
 * 只读列表，再交给记录和快照两个阶段。</p>
 *
 * @param <C> 当前 tick 的不可变上下文类型
 * @param <E> 当前 tick 的事件类型
 */
public final class OrderedTickUpdatePort<C, E> implements TickUpdatePort {

    private final Supplier<? extends C> contextReader;
    private final Consumer<? super C> inputConsumer;
    private final Consumer<? super C> worldUpdater;
    private final Function<? super C, ? extends List<? extends E>> eventPublisher;
    private final Comparator<? super E> eventOrder;
    private final BiConsumer<? super C, ? super List<E>> stateRecorder;
    private final BiConsumer<? super C, ? super List<E>> renderSnapshotPublisher;
    private final Supplier<TickStepResult> clockAdvance;

    /**
     * 创建一个按 C1 单 tick 顺序调度的更新端口。
     *
     * @param contextReader 每次 tick 开始时读取当前不可变上下文
     * @param inputConsumer 消费该上下文对应的输入
     * @param worldUpdater 更新玩家、机关和残影等纯逻辑状态
     * @param eventPublisher 发布本 tick 的事件；返回顺序是发布顺序
     * @param eventOrder 注入的正式事件排序键；重复事件的拒绝或去重由事件提供方负责
     * @param stateRecorder 记录 tick 末规范状态和已排序事件
     * @param renderSnapshotPublisher 基于 tick 末状态生成只读渲染快照
     * @param clockAdvance 在快照生成后推进一次共享时钟并返回其结果
     */
    public OrderedTickUpdatePort(
            Supplier<? extends C> contextReader,
            Consumer<? super C> inputConsumer,
            Consumer<? super C> worldUpdater,
            Function<? super C, ? extends List<? extends E>> eventPublisher,
            Comparator<? super E> eventOrder,
            BiConsumer<? super C, ? super List<E>> stateRecorder,
            BiConsumer<? super C, ? super List<E>> renderSnapshotPublisher,
            Supplier<TickStepResult> clockAdvance) {
        this.contextReader = Objects.requireNonNull(contextReader, "contextReader");
        this.inputConsumer = Objects.requireNonNull(inputConsumer, "inputConsumer");
        this.worldUpdater = Objects.requireNonNull(worldUpdater, "worldUpdater");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.eventOrder = Objects.requireNonNull(eventOrder, "eventOrder");
        this.stateRecorder = Objects.requireNonNull(stateRecorder, "stateRecorder");
        this.renderSnapshotPublisher = Objects.requireNonNull(renderSnapshotPublisher, "renderSnapshotPublisher");
        this.clockAdvance = Objects.requireNonNull(clockAdvance, "clockAdvance");
    }

    /**
     * 执行一个完整 tick，并原样返回共享时钟的推进结果。
     *
     * <p>本类只执行注入的排序器；对完整排序键相同的事件，事件提供方必须在此之前
     * 明确拒绝或去重，不能依赖稳定排序保留发布顺序。返回值不在此处重映射，
     * {@link FixedStepLoop} 通过 {@link TickStepResult#shouldContinueFrame()} 决定
     * 是否继续本帧补算。</p>
     *
     * @return 共享时钟本次推进的非空结果
     */
    @Override
    public TickStepResult stepOnce() {
        C context = Objects.requireNonNull(contextReader.get(), "contextReader returned null");

        inputConsumer.accept(context);
        worldUpdater.accept(context);

        List<? extends E> publishedEvents = Objects.requireNonNull(
                eventPublisher.apply(context),
                "eventPublisher returned null");
        List<E> orderedEvents = new ArrayList<>(publishedEvents);
        orderedEvents.sort(eventOrder);
        List<E> readOnlyEvents = List.copyOf(orderedEvents);

        stateRecorder.accept(context, readOnlyEvents);
        renderSnapshotPublisher.accept(context, readOnlyEvents);

        return Objects.requireNonNull(clockAdvance.get(), "clockAdvance returned null");
    }
}
