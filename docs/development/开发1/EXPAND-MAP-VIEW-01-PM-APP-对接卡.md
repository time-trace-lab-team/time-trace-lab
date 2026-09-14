# EXPAND-MAP-VIEW-01 · PM app 对接卡

> 责任人：PM / app 所有者。开发一只交付 render 契约，不修改 `app/**`。
>
> 本卡生成时基线：`origin/develop @ 418d0ca`。开发一工作分支：`codex/expand-map-view-dev1-v4`。
> 注意：当前开发一改动仍在该分支工作区，尚无专门的交付 commit / push / PR；PM 配对前必须取得开发一回传的提交 SHA，不能从未提交工作区制作可合入分支。
> PM 于 2026-09-13 的最新裁决：默认窗口为 `1280×720`；`960×576` 仅是人工 resize 验收档位。

## 1. PM 唯一建议修改的生产文件

```text
src/main/java/org/example/timeloop/app/TimeTraceLabApplication.java
```

当前该文件在 `start(...)` 中创建固定 `WorldTransform.identity()` 并传给六层。PM 应改为持有一个当前不可变变换的引用和一个共享 supplier；不要修改 `CanvasAdapter`、图层、关卡数据或任何玩法代码。

## 2. 开发一交付的精确 API

```java
public static WorldTransform WorldTransform.fit(
        double worldWidth,
        double worldHeight,
        double viewWidth,
        double viewHeight)
```

- `worldWidth` / `worldHeight`：世界单位；由当前 `LevelData` 网格列数/行数乘以 `tileSize` 推导。
- `viewWidth` / `viewHeight`：实际可绘制 Canvas/容器的有效像素尺寸。
- 四个参数均必须是正有限数；`0`、负数、`NaN`、正负无穷抛出 `IllegalArgumentException`。
- 返回不可变投影：`scale = min(viewW / worldW, viewH / worldH)`，`originX/Y` 为剩余空间的一半；它不回写任何世界坐标。
- 初始化/布局阶段有效宽高为 `0` 时，**不得调用** `fit`；等待有效尺寸或暂保留一个已知正尺寸的变换。

以下 supplier 构造器已提供；原有接收固定 `WorldTransform` 的构造器仍兼容，PM 必须改用 supplier 版本。

```java
new GroundWallLayer(TileType[][] grid, double tileSize,
                    Supplier<WorldTransform> transformSource)

new SpawnLayer(double worldX, double worldY, double tileSize,
               Supplier<WorldTransform> transformSource)

new PathNodeHintLayer(Supplier<RenderViews.Frame> frameSource,
                      List<RenderViews.PathNodeMarker> nodes,
                      double tileSize,
                      Supplier<WorldTransform> transformSource)

new MechanismLayer(Supplier<RenderViews.Frame> frameSource, double tileSize,
                   Supplier<WorldTransform> transformSource)

new PlayerLayer(Supplier<RenderViews.Frame> frameSource, double tileSize,
                Supplier<WorldTransform> transformSource)

new EchoTrailLayer(Supplier<RenderViews.Frame> frameSource,
                   Supplier<WorldTransform> transformSource)
```

每层每帧仅读取 supplier 一次。supplier 或其返回值为 `null` 会带图层上下文失败，不会静默回退 `identity()`。

## 3. PM 接线步骤

1. 从 `418d0ca` 建立 PM app 分支；不要直接在 `develop` 修改。
2. 从 `LevelData` 推导 `worldWidth = columns * tileSize`、`worldHeight = rows * tileSize`；不得把 `28`、`16`、`48` 写成通用关卡常量。
3. 持有一个当前不可变 `WorldTransform`。监听实际 Canvas 或其有效容器宽/高；尺寸为正时调用 `WorldTransform.fit(...)` 替换当前值。
4. 建立**同一个** `Supplier<WorldTransform>`，传给地面/墙体、出生点、节点提示、机关、玩家、残影轨迹六层。
5. 保持 HUD 与 Canvas 的布局边界清晰：`fit` 的 view 尺寸只使用实际可绘制区域，不把 HUD 高度混入世界投影。
6. resize 回调只能更新投影；不得调用 `assembly.tick(...)`、不得改变 `roundTick`、世界坐标、机关状态、录制帧或输入状态。

## 4. 配对顺序与停止条件

1. 开发一回传 render 提交 SHA 和推送分支后，PM 在独立配对分支按约定合入开发一 render 提交与 PM app 提交。
2. 配对分支必须同时包含两侧变更；开发一单边分支不得直接向 `develop` 提 PR 或合入。
3. PM 回传：PM app SHA、配对分支名/配对 SHA、全量测试结果，以及任何新增生产路径。
4. 只有配对分支的全量自动化、`git diff --check` 和 JavaFX 人工 resize 验收均通过，才可进入 `V4-INTEGRATION-GATE` / PR。

## 5. 已有开发一证据与配对后验收

开发一侧当前已验证：

- `WorldTransform.fit` 的合法/非法输入、等比居中、极端比例和坐标往返；
- 六层 supplier 构造器兼容、空值防御、每帧单次读取；
- 静态地图/出生点、机关/玩家、残影/节点在离屏 Canvas 中随新变换移动；
- render 范围 33 项自动化均通过，最慢单项 0.409 秒；
- `PatrolControllerHeldDirectionTest` 12 项通过，新增松开旧方向后的侧向转弯规则已锁定。

PM 与测试负责人必须在有显示器的目标机共同执行（默认窗口为 `1280×720`，`960×576` 为验收档位，不是默认窗口）：

1. 默认 `1280×720` 与 `960×576` 验收档位均完整显示地图，格子仍为正方形。
2. 明显更宽、明显更高的视口均对称留边、无拉伸。
3. 六层连续 resize 时始终重合：地面/墙、出生点、节点、机关、玩家、残影。
4. resize 不改变玩家世界位置、碰撞、驻留、门/出口、`roundTick`、录制或残影行为。
5. 至少连续 10 次侧向转向、段中间掉头、静止路口吸附；完成第一关一次。
6. 保存 960×576、明显更宽、明显更高三张完整窗口截图（含 HUD）。
