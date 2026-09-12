# X-MOVE-COLLAPSE-01-DEV1-v2 · 开发一交付

> 分支：`codex/dev1-move-v2`
> 基线：`ed5bcac`（PM `CORE-3 + APP-1` 配对验证头）
> 状态：开发一 v2-A / v2-B / v2-C provider 已完成；等待 PM `app/**` 静态节点数据接线与 JavaFX 画面验收。

## 任务链进度

| 顺序 | 子项 | 状态 | 交付效果 |
| --- | --- | --- | --- |
| v2-A | `CORE-2` 速度补测 | ✅ | 速度乘子在移动、节点转向、`resetTo` 后都保持生效；无输入仍为 `IDLE`。 |
| v2-B | `R-1` 玩家状态可见 | ✅ | `IDLE` 圆角方形，`CRUISING` 圆形+方向刻痕，`SLOWED` 额外描边+反向短拖尾；不靠颜色单独表达状态。 |
| v2-C | `R-2` 节点转向提示 | ✅（开发一） | 静态节点中心按距离绘制 8 px 菱形：≤48 world units 为亮、≤96 为暗、更远隐藏；不绘制全屏网格。 |
| 下一步 | PM app 接线 | ⏳ | 从 `levelData.getPathNodes()` 创建不可变 `PathNodeMarker` 列表，暴露只读访问器并注册 `PathNodeHintLayer`。 |

## 技术契约

- `RenderViews.PathNodeMarker(id, x, y)` 只承载稳定 ID 与有限的世界坐标，不加入逐帧 `RenderViews.Frame`。
- `PathNodeHintLayer` 只读取 `Frame.player()` 计算中心距；它不从玩家状态或地格反推节点，不读取门/开关/占用状态，也不修改玩法数据。
- `PathNodeHintLayer.levelFor(distanceWorld, tileSize)` 是纯函数：`d ≤ tileSize` 为 `BRIGHT`，`d ≤ 2 × tileSize` 为 `DIM`，其余 `HIDDEN`。
- `PlayerVisualProjection` 同样是纯投影：不增加计时器，`PlayerLayer` 只根据当前帧的 `movementState` 绘制。

## 自动化验证

| 命令 / 证据 | 结果 |
| --- | --- |
| `PatrolControllerSpeedModifierTest` | 5 / 0 / 0 |
| `PlayerVisualProjectionTest` | 4 / 0 / 0 |
| `PathNodeHintLayerTest` | 3 / 0 / 0 |
| `./mvnw.cmd -o test` | **303 tests / 0 failures / 0 errors / 0 skipped** |
| 最长单测 | `AppTest`，0.32 秒 |
| JavaFX 视觉验收 | 未运行；等待 PM 注册 `PathNodeHintLayer` 后截取三态画面。 |

## PM 接线清单（不属于开发一修改范围）

1. `Level01Assembly` 在构造阶段从 `levelData.getPathNodes()` 只读投影 `List<RenderViews.PathNodeMarker>`，并暴露不可变 `pathNodeMarkers()`。
2. `TimeTraceLabApplication` 在现有 render layers 后注册：
   `new PathNodeHintLayer(frames, assembly.pathNodeMarkers(), 48.0, transform)`。
3. 启动 JavaFX，验证静止、巡航、减速三态可辨识，且接近节点时菱形从隐藏/暗变亮；记录截图用于最终可读性门禁。
