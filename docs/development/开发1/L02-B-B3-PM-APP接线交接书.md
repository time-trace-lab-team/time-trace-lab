# L02-B · B3 PM APP 接线交接书

> 任务链：B0-1 ✅ → B0-2 ✅ → B0-3 ✅ → B1 ✅ → B2 ✅ → **B3（PM）** → B4 → B5 → B6
>
> 开发一分支：`codex/l02-b-dev1`
>
> 当前基线：`develop @ 31920dc02cc5555d73a3ba5691fdc846e7b22b2d`
>
> 日期：2026-09-14
>
> 交出方：开发一
>
> 接收方：PM / app 集成所有者

## 1. 本次交付结论

开发一已在 `render/**` 完成：

1. `RenderViews.RayVisualState { OFF, WARNING, ACTIVE }`；
2. `RenderViews.RayBeam` 世界坐标只读 DTO；
3. `RenderViews.Frame` 第四分量 `List<RayBeam> rays`；
4. 兼容的三参数 `Frame` 构造器，既有 L1 调用点无需修改；
5. `RayLayer` 三态绘制及动态 `WorldTransform` 支持；
6. B2 `PHASED` 玩家压低、半透明和相位圆环视觉。

开发一没有修改 `app/**`、`mechanism/**` 或 `level/**`。B3 必须由 PM/app 所有者完成。

## 2. B3 开工前置条件

以下条件全部满足后才能把 B3 标记为“已开工”：

- [ ] **P1：开发一交付可被消费。** `RayBeam`、`RayVisualState`、`Frame.rays()`、`RayLayer` 和 B2 已形成明确提交或 PR；PM 不应从开发一未提交工作区复制代码。
- [ ] **P2：PM 集成分支包含开发一交付。** 合并后编译可直接引用 `RenderViews.RayBeam` 和 `RayLayer`。
- [ ] **P3：L02 权威 assembly 已确定。** 明确哪个 `app/**` 对象持有 L02 的 `List<Ray>`、共享 `roundTick`、玩家状态和 render frame；不得临时复用 L1 状态冒充 L02。
- [ ] **P4：射线权威 API 可用。** `Ray.getId()`、`getStart()`、`getEnd()`、`getState()` 以及 `RayFactory.updateAll(...)` 均来自开发三现有公开面；不要求开发一或开发三新增接口。
- [ ] **P5：tick 顺序冻结。** 每个逻辑 tick 先以当前共享 `roundTick` 调用 `RayFactory.updateAll(rays, roundTick)`，再生成该 tick 的 `RenderViews.Frame`；禁止画面状态比碰撞状态提前或滞后一 tick。
- [ ] **P6：同帧一致性。** 玩家 `phased`、射线状态、射线端点和命中判断必须来自同一个 tick 结束后的权威状态快照。
- [ ] **P7：L1 隔离。** L1 继续使用兼容构造器或显式传 `List.of()`，其 `Frame.rays()` 恒为空，L1 画面不得出现射线。
- [ ] **P8：变换源一致。** `RayLayer` 使用与地图、机关、玩家相同的 `Supplier<WorldTransform>`，不得在 app 或 render 中额外添加射线屏幕偏移。

若 P1–P8 任一项不满足，B3 保持阻塞，不进入 B4 联调。

## 3. 冻结接口

```java
public enum RayVisualState {
    OFF,
    WARNING,
    ACTIVE
}

public record RayBeam(
        String id,
        double startX,
        double startY,
        double endX,
        double endY,
        RayVisualState state
) {}

public record Frame(
        Player player,
        List<Mechanism> mechanisms,
        List<EchoTrail> echoes,
        List<RayBeam> rays
) {}
```

实际实现包含构造校验、不可变复制和按 `id` 升序排序。PM 不要在 app 侧重复排序或创建另一套 DTO。

## 4. PM 的 B3 实现任务

### B3-1 · 权威射线生命周期接线

在 L02 assembly 中：

1. 使用 `RayFactory.buildFrom(level, eventBus)` 构造并持有射线；
2. 每个逻辑 tick 使用共享 `roundTick` 调用 `RayFactory.updateAll(rays, roundTick)`；
3. assembly 销毁或切关时调用既有释放路径，避免观察者残留；
4. 不在 render 层更新或缓存权威 `Ray`。

### B3-2 · `Ray` 到 `RayBeam` 的穷尽投影

映射必须位于 `app/**`。状态映射使用无 `default` 的穷尽 `switch`，让开发三未来新增状态时产生编译期提示：

```java
private static RenderViews.RayBeam toRayBeam(Ray ray) {
    RenderViews.RayVisualState state = switch (ray.getState()) {
        case OFF -> RenderViews.RayVisualState.OFF;
        case WARNING -> RenderViews.RayVisualState.WARNING;
        case ACTIVE -> RenderViews.RayVisualState.ACTIVE;
    };

    return new RenderViews.RayBeam(
            ray.getId(),
            ray.getStart().x(),
            ray.getStart().y(),
            ray.getEnd().x(),
            ray.getEnd().y(),
            state);
}
```

将所有投影放入 `new RenderViews.Frame(player, mechanisms, echoes, rayBeams)`；`Frame` 会做不可变复制并按 ID 排序。

### B3-3 · Canvas 图层接线

使用和其他世界图层相同的对象：

```java
Supplier<RenderViews.Frame> frames = assembly::renderViews;
Supplier<WorldTransform> transformSource = transform::get;

canvasAdapter.addLayer(new RayLayer(frames, transformSource));
```

图层顺序要求：

- `RayLayer` 必须位于地图层之上，确保光束可见；
- `RayLayer` 必须位于 `PlayerLayer` 之前，确保玩家和 `PHASED` 外观不会被 ACTIVE 光晕覆盖；
- PM 可结合 L02 机关遮挡关系决定其位于 `MechanismLayer` 前或后，但必须在 JavaFX 人工验收中确认端点清晰。

建议顺序：

```text
GroundWallLayer
SpawnLayer / PathNodeHintLayer
MechanismLayer
RayLayer
PlayerLayer
EchoTrailLayer
```

## 5. B3 验收门禁

### 5.1 纯逻辑 / DTO

- [ ] `Frame.rays()` 按 ID 升序且不可修改；
- [ ] 旧三参数 `Frame` 构造仍得到空射线列表；
- [ ] L1 的 `Frame.rays()` 恒为空；
- [ ] `Ray.State` 三态无 `default` 地映射到 `RayVisualState`。

### 5.2 Headless app 接线

- [ ] L02 assembly 测试证明 `updateAll` 使用共享 `roundTick`；
- [ ] OFF → WARNING → ACTIVE 的权威状态能进入同 tick 的 `Frame.rays()`；
- [ ] L02 frame 中端点与 `Ray.getStart()/getEnd()` 一致；
- [ ] PHASED 玩家经过 ACTIVE 射线时，画面投影与权威相位状态同帧；
- [ ] L1 既有测试无回退。

### 5.3 JavaFX 人工验收

- [ ] OFF：低亮、细点线导轨可辨；
- [ ] WARNING：预警虚线明显，不只靠颜色区分；
- [ ] ACTIVE：实线核心与光晕明显；
- [ ] 三态切换无一 tick 画面错位；
- [ ] 玩家始终绘制在射线之上；
- [ ] `PHASED` 压低、半透明、脚下圆环仍清楚；
- [ ] 缩放或窗口尺寸变化后，光束端点仍与世界地图对齐；
- [ ] L1 画面零变化。

自动化测试通过不能代替本节人工验收。

## 6. 建议验证命令

```powershell
.\mvnw.cmd -o "-Dtest=RenderViewsRayProjectionTest,RayLayerVisualTest,DynamicWorldTransformLayerTest,PlayerVisualProjectionTest,PlayerLayerPhaseVisualTest" test
.\mvnw.cmd -o clean test
git diff --check
git status --short --branch
```

开发一交付现场证据：

- 定向测试：`14 / 0 / 0 / 0`；
- 全量测试：`459 / 0 / 0 / 0`；
- `git diff --check`：通过；
- JavaFX 真实游戏窗口：尚未人工验收；
- 当前代码：尚未提交、尚未推送，P1 仍未满足。

## 7. B3 停止条件

出现以下任一情况，PM 应停止接线并回传，不要在 `render/**` 或 `mechanism/**` 临时绕过：

1. PM 集成分支不存在 `Frame.rays()`、`RayBeam` 或 `RayLayer`；
2. L02 没有唯一权威 assembly，或射线与玩家使用不同 `roundTick`；
3. 必须修改 `Ray` 才能取得 ID、端点或状态；
4. 旧三参数 `Frame` 调用无法编译；
5. L1 frame 出现非空射线；
6. 状态映射只能通过字符串、ordinal 或带 `default` 的 switch 完成；
7. 射线端点需要手工屏幕偏移才能对齐地图；
8. app 自动化通过但 JavaFX 中看不到射线，或射线遮住玩家。

## 8. B3 完成后的回传格式

```markdown
### L02-B / B3 PM APP 接线回传

- 分支：
- 提交 SHA：
- L02 assembly / 权威 Frame 生产位置：
- `Ray.State -> RayVisualState` 映射位置：
- `RayLayer` 图层注册位置与顺序：
- 定向测试：x / 0 / 0 / 0
- 全量测试：x / 0 / 0 / 0
- L1 空射线证据：
- L02 三态进入 Frame 的 headless 证据：
- JavaFX 人工验收：已完成 / 未完成
- 未解决阻塞：无 / 说明
```

## 9. 后续任务链

| 阶段 | 所有者 | 进入条件 | 完成条件 |
| --- | --- | --- | --- |
| B3 app 投影与图层接线 | PM | P1–P8 全部满足 | headless 接线通过，真实画面可进入验收 |
| B4 命中 / SLOWED / PHASED 豁免联调 | PM + 开发三，开发一只处理 render 缺陷 | B3 完成 | 权威判定与同帧视觉一致 |
| B5 JavaFX 人工验收 | PM + 开发一 | B4 可运行 | 三态、相位、缩放和 L1 隔离全部通过 |
| B6 提交、推送、合并 | 各自所有者 / PM | 自动化与人工验收通过 | SHA 与测试证据回传、合入 develop |
