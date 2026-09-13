# 第一关 MVP 集成装配技术指南（给 PM）

> 目标：让第一关「留下的脚步」在窗口里可玩。
> 责任板块：PM / 集成（`app/**`）。
> 依据：`README.md` 第十节（模块归属）、三份岗位技术指南（路径边界）。

## 一、缺什么

一句话：**什么都不缺零件，只缺装配。**

三个开发的模块全部合并进 develop（`a862fc8`），240 条测试全绿。但没有任何代码把这些零件接进 `TimeTraceLabApplication`——它还是 C1 空壳（tick 回调为空）。

## 二、要做什么（装配 5 步）

在 `TimeTraceLabApplication` 里，把下面的链路串起来：

1. **加载关卡**：`Level01Footsteps.build()` → `LevelData` → `new LevelGeometryImpl(levelData)`（得到路径节点/墙/门/出生点）。
2. **创建玩家与机关**：从 `LevelData` 的 `entitySpawnList`/`doors` 创建 `DockingPlate`/`Door`/`ExitTerminal` 实例，并用 `PatrolController` 创建玩家（出生点 `getSpawnPosition()`）。
3. **每 tick 驱动**：`FixedStepLoop` 回调里依次 —— 输入(`InputIntent`) → 巡行/autoDock(`PatrolController.advance` + `C3DockController`) → 录制(`RecordingSession.recordFrame`) → 读残影(`ReplayPort.activeEchoFrames`) → 更新门/出口状态。
4. **渲染**：`CanvasAdapter.addLayer(...)` 依次加入 `GroundWallLayer`(grid,tileSize,transform) → 机关层 → `PlayerLayer` → `EchoTrailLayer`；坐标用 `WorldTransform`。
5. **HUD**：`SharedHud` 加入场景，每 tick 调 `render(TickContext)` 显示读秒 + 轮数。

## 三、可直接调用的现有接口（已核实）

| 环节 | 类 / 方法 |
| --- | --- |
| 关卡数据 | `Level01Footsteps.build()` → `LevelData` |
| 关卡几何 | `new LevelGeometryImpl(LevelData)`；`getTileSize()`/`getSpawnPosition()`/`getPathNodes()`/`isWall()`/`isDoorClosed()` |
| 玩家巡行 | `PatrolController.advance(long tick, ExitPassability)` → `PlayerKinematics` |
| autoDock 查询 | `AutoDockService`（开发三的只读查询） |
| 录制 | `RecordingSession(RoundClock, EchoQueue)`；`beginRound()`/`recordFrame(PlayerFrame)`/`completeNormalRound(Runnable)`/`completeGoal()`/`failFinalRound()` |
| 回放读取 | `ReplayPort(RoundClock, RecordingSession)`；`currentPlayerFrame()`/`activeEchoFrames()` |
| 只读视图 | `MvpRenderSnapshot` |
| 渲染层 | `RenderLayer.render(gc, worldW, worldH, alpha)`；`GroundWallLayer(TileType[][], tileSize, WorldTransform)` 等 |
| HUD | `SharedHud`（JavaFX HBox）`render(TickContext)`；`HudViewModel.from(TickContext)` |

## 四、允许与禁止路径

- **允许修改**：`src/main/java/org/example/timeloop/app/**`、对应测试。
- **禁止修改**：`core/**`、`entity/**`、`render/**`、`replay/**`、`snapshot/**`、`mechanism/**`、`level/**`、`ui/**`、`pom.xml` —— 只调用它们的公开接口，不进去改实现。

## 五、注意点（装配时需自己处理）

1. **机关实例创建**：`LevelData` 只有 `EntitySpawnInfo`（类型标记 `dock_plate`/`exit_terminal`）和 `DoorInfo`，**没有现成的机关工厂**。PM 需在 `app/` 写一个简单工厂：按 `EntitySpawnInfo` 类型 `new DockingPlate(...)`/`new ExitTerminal(...)`，按 `DoorInfo` `new Door(...)`。（若嫌麻烦，也可请开发三补一个工厂，但那属跨模块，建议先自己在 app 里写。）
2. **输入适配**：JavaFX `KeyEvent` 在 `app/` 的 `Scene` 监听器里转成 `InputIntent`/`LogicalKey`，再喂给 `core` 的纯逻辑输入端口（开发一默认不改 `app/`，所以这块适配是 PM 的）。
3. **线程**：`AnimationTimer` 每帧累加固定步长，逻辑更新与渲染分离；不要让 JavaFX 线程直接改逻辑状态。

## 六、验收标准

1. 窗口出现第一关地图：墙、地面、两块驻留板、门、出口、玩家、HUD（读秒 + 轮数）。
2. 玩家松键仍恒速巡行、在路口转向；进入驻留板自动停驻，新方向键离开。
3. 第一轮读秒归零自动切轮；残影在下一轮从 tick 0 复现。
4. 双驻留板同时激活 → 门开 → 出口交互通关。
5. 全量测试仍 **240 条全绿**。

## 七、验证命令

```bash
export JAVA_HOME="D:/1/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw.cmd -o test     # 全量测试
# 启动窗口（离线环境，绕开 mvn javafx:run）：
/d/1/jdk/bin/java.exe --module-path "<javafx-base/controls/graphics-win.jar>" \
  --add-modules javafx.controls,javafx.graphics,javafx.base \
  -cp target/classes org.example.timeloop.app.TimeTraceLabApplication
```
