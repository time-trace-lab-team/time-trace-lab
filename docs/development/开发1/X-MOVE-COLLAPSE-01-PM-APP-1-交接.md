# X-MOVE-COLLAPSE-01：PM `APP-1` 配对接线交接

> 交接方：开发一  
> 任务链位置：`CORE-3 -> PM APP-1 -> 开发三 ENT-2a -> 开发一 ENT-2b`  
> 当前分支：`codex/dev1-next-module`  
> 交接状态：等待 PM 接线；开发一在本交接完成前停止后续编码。

## 一、当前阻断

开发一已完成并提交 `CORE-1`、`CORE-2`、`ENT-1`、`ENT-3`、`CORE-3`。

`CORE-3` 删除了 core 路径节点的自动选路数据：

- `PathNode` 现在只接受 `(id, center, exits)`；
- 已删除 `PathNode.defaultExit`；
- 已删除 `PathExitDecision`；
- 已删除 `PathExitSelector.select(...)`；
- 保留 `PathExitSelector.isPassable(...)` 作为“指定方向是否可通行”的纯判断。

因此当前全量构建预期在下列位置失败：

```text
src/main/java/org/example/timeloop/app/PathGraphBridge.java:47
```

该文件仍以四参形式构造 core `PathNode`，并传入旧的 `Optional<Direction> defaultExit`。这是 PM `APP-1` 的配对接线工作，不能由开发一修改 `app/**` 解决。

## 二、开发一已交付的接口

| 子项 | 提交 | PM 可调用接口 / 行为 |
| --- | --- | --- |
| `CORE-1` | `c3b8464` | `LogicalKey.direction()`；`InputIntent.heldDirections()` |
| `CORE-2` | `f92cec9` | `SpeedModifierPort.speedMultiplier()`；五参 `PatrolController` 构造器接收速度端口，四参构造器仍默认 1.0 倍速 |
| `ENT-1` | `ac9637f` | `PatrolController` 内部保存尚未提交的 90° 转向意图；`advance(...)` 签名不变 |
| `ENT-3` | `b171b0c` | `PatrolController.resetTo(String startNodeId, Direction initialDirection)` |
| `CORE-3` | `82fe73d` | core 路径不再提供默认出口或自动选路 |

## 三、PM 需要完成的工作

### 1. 输入接线

在 `InputAccumulator` 与 `Level01Assembly` 中消费：

```java
inputIntent.heldDirections()
inputIntent.lastDirectionEdge()
```

不要在 app 层重新维护“逻辑键到方向”的映射，也不要自行保留下一次转向意图。方向映射属于 `CORE-1`，单槽意图属于 `ENT-1` 的 `PatrolController`。

### 2. 删除 app 层重复转向状态

删除 `Level01Assembly.pendingTurn` 及其写入、清除和传递逻辑。

调用 `PatrolController.advance(...)` 时仍传入：

- 本刻按住方向集合；
- 本刻最后方向边沿；
- 本刻出口通行性。

控制器会自行保留有效侧向意图，直到节点中心、松手或成功提交。

### 3. 轮初复位接线

在每轮真正开始移动前调用：

```java
patrol.resetTo("L01_node_spawn", Direction.DOWN);
```

调用点应在轮初状态迁移处，而不是每一刻调用。该 API 不生成帧、不发事件、不清空回放或残影；PM 只需保证它恰好对应“新轮玩家状态初始化”。

### 4. 修复 `PathGraphBridge`

将桥接层改成纯几何换算：根据关卡节点位置和可用方向构造 core `PathExit` 与三参 `PathNode`。

删除 bridge 中对以下旧自动选路信息的转换：

- `levelNode.getDefaultExit()`；
- `Optional<Direction> defaultExit`；
- core `PathNode` 的第四个构造参数。

不要修改 `core/path/**` 以恢复旧构造器，也不要把默认出口作为另一种 app 自动选路规则保留。

## 四、验收条件

PM 接线完成后，在项目根目录执行：

```powershell
.\mvnw.cmd -o test
```

验收应同时满足：

1. 全量编译和测试退出码为 0；
2. `PathGraphBridge` 不再引用 `defaultExit` 或四参 core `PathNode` 构造器；
3. `Level01Assembly` 不再有 `pendingTurn`；
4. 按住方向可连续移动；提前按住侧向键可在节点转向；松手后不转向；
5. 新轮开始时调用一次 `resetTo(...)`，角色回到出生点并可立即移动；
6. 路口不会在没有玩家有效转向意图时自动选择侧路。

## 五、完成后交回开发一所需信息

请回报：

- PM 接线提交哈希；
- `.\mvnw.cmd -o test` 的测试数量和退出码；
- `resetTo(...)` 的实际调用点；
- `PathGraphBridge` 已删除默认出口转换的确认；
- 是否已有开发三 `ENT-2a`（`AutoDockOccupancyPort.tryLeave` 同刻离开）可消费的确认。

收到以上信息后，开发一才可评估是否进入 `ENT-2b`。
