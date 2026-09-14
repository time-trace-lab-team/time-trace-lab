# L03-DEV1 · R4（Δt）PM 对接卡

> 发起方：开发一
>
> 分支：`codex/l03-dev1-r4`
>
> 代码提交：`502b6ab feat(render): render recorded L03 ray delay markers`
>
> 基线：`develop@cc1b174`
> 状态：render 侧完成，等待 PM/app 接线

## 一、已交付

开发一已完成 `RAY_DELAY` 的只读绘制：

- `TimelineEventKind.RAY_DELAY` 绘制为沙漏交叉标记；
- 文本显示 `Δt`；当 `delayTicks > 0` 时显示 `Δt+<ticks>`；
- 坐标仅使用 `TimelineVisualEvent.worldPosition`，由 `WorldTransform` 统一投影；
- 不解析 replay 的 `reason`，不重新判定射线命中，不自行计算延迟；
- 后续 `DOCK_ENTER` / `DOCK_LEAVE` 恢复交互色样式，不受射线标记污染。

涉及文件：

- `src/main/java/org/example/timeloop/render/TimelineEventLayer.java`
- `src/test/java/org/example/timeloop/render/TimelineEventLayerTest.java`

## 二、PM 已具备的上游事实

`develop` 已包含 PM 的 RAY_DELAY 录制提交：

```text
e680284 feat(replay+app): 射线命中写入 RAY_DELAY 记录事件
```

当前录制事件形态：

```java
new TimelineEvent(
    tick,
    PLAYER_ACTOR_ID,
    PLAYER_SOURCE_ROUND,
    rayId,
    TimelineEvent.EventType.RAY_DELAY,
    null,
    "delay=" + delayTicks)
```

回放仍可经 `EchoState.eventsAt(tick)` 按刻读取记录事件。

## 三、PM 需要完成的 app 接线

在 `app/**` 将当前轮与回放轮的 `TimelineEvent.EventType.RAY_DELAY` 映射为：

```java
new TimelineVisualEvent(
    sourceRound,
    tick,
    worldPosition,
    TimelineEventKind.RAY_DELAY,
    delayTicks)
```

约束：

- `sourceRound`、`tick`、`delayTicks` 必须来自记录事件；
- `worldPosition` 必须由事件/射线的权威世界坐标映射提供；
- 不得依据当前轨迹、速度或射线碰撞在 render/app 重算命中；
- 记录中的 `"delay=<ticks>"` 只可在 PM/app 投影层解析；
- 当前轮与 E₂ 回放必须消费同一条记录事实。

然后装配：

```java
new TimelineEventLayer(eventSource, transformSource)
```

建议层序：残影轨迹之后、玩家/HUD 之前；以实际 Canvas 层栈为准。

## 四、验收

自动化已通过：

```text
.\mvnw.cmd -o clean test
568 tests, 0 failures, 0 errors, 0 skipped
```

其中包括：

- RAY_DELAY 沙漏标记与 `Δt+ticks` 离屏绘制；
- 射线标记后驻留菱形样式不泄漏；
- 视口缩放后，标记仍投影到正确世界位置。

尚未完成：

1. PM/app Supplier 注入；
2. 真实第三关中当前轮与 E₂ 回放的 Δt 显示；
3. JavaFX 窗口截图、缩放人工核验；
4. R5 生命周期 `effectProgress` 消散接线与视觉验收。

## 五、开发一边界确认

本提交没有修改：

```text
app/**
level/**
mechanism/**
replay/**
snapshot/**
ui/**
RenderViews.Frame
RenderViews.EchoTrail
```

开发一下一步为 R5 render 消散表现；该项须等待开发二提供
`EchoLifecycleVisual.effectProgress`，并由 PM 注入 `EchoLifecycleLayer` 后再收口。
