# L-1 技术核查与开发一实施回复：开发三 → 开发一

> 回复方：开发一  
> 已核查文件：`entity/C3DockController`、`entity/C3DockControllerTest`、`mechanism/autodock/AutoDockService`、`AutoDockOccupancyPort`  
> 结论：问题成立；不需要新接口；等待 PM 确认 L-1 与 `ENT-2b` 的合入顺序后实施。

## 一、核查结论

开发三的 L-1 说明与当前代码一致，两个问题都已定位到明确的调用链。

### 1. 进入边沿缺失：成立

当前 `C3DockController.cruiseStep(...)` 只做本刻查询：

```java
readPort.findNearest(position, 0.0)
occupancyPort.tryEnter(..., tick, position)
```

它没有记录“上一采样是否位于同一 dock 区域内”。因此，当开发三把 `tryLeave(...)` 放宽为“合法出口按下刻即释放”后，角色虽然仍位于区域内，但 controller 已清除驻留状态；下一 tick 会再次调用 `tryEnter(...)`，从而产生错误的二次 `DOCK_ENTERED`。

### 2. 离开晚一 tick：成立

当前合法出口按下刻只设置 `departureDirection` 并返回 `CRUISE`。`tryLeave(...)` 在下一次 `dockedStep(...)` 才调用，因此 `DOCK_LEFT` 被记录为出界刻，而不是按键边沿刻。

现有 `C3DockControllerTest` 也将此旧行为写死为：tick 1 按 `UP`，tick 2 在区域外才产生 `DOCK_LEFT`。

## 二、开发一的实施方案

### A. 在 controller 内维护每个 dock 的区域采样状态

增加私有不可变集合，例如：

```java
Set<String> previousInsideMechanismIds
```

每次 `step(...)` 先通过 `readPort.snapshot()` 与 `view.region().contains(position)` 得到本刻 `currentInsideMechanismIds`。仅当某个机制满足以下条件时，`cruiseStep(...)` 才尝试进入：

```text
currentInsideMechanismIds 包含该 mechanismId
且 previousInsideMechanismIds 不包含该 mechanismId
```

即严格采用 `outside -> inside` 边沿。每个 `step(...)` 返回前将本刻集合保存为下一刻的 previous 集合。

这比只保存一个布尔值更稳健：即使地图中存在多个相邻或重叠驻留区域，也按 stable mechanism ID 分别判定边沿。

### B. 在合法出口边沿的同一 tick 调用 `tryLeave(...)`

`dockedStep(...)` 将改为：

1. 读取 `input.lastDirectionEdge()`；
2. 验证该方向属于 `AutoDockView.legalExitDirections()`；
3. 立即调用现有签名的 `occupancyPort.tryLeave(...)`，参数中的 `tick` 和 `position` 均为当前按键刻；
4. 当开发三的 L-1 返回 `LEFT` 时，在同一 tick：
   - 生成 `DOCK_LEFT`；
   - 清除 controller 的 dock 本地状态；
   - 返回 `CRUISE + departureDirection`，让调用方按该方向移动。

旧的跨 tick `departureDirection` 状态不再需要保留。若 `tryLeave(...)` 返回非 `LEFT`，controller 保持 `FREEZE`，避免在占用未释放时继续移动。

### C. 二次进入的防护由两层组成

1. **实体层跨 tick 防护**：上述 `previousInsideMechanismIds` 保证仍在区域内不会形成新的 `outside -> inside` 边沿；
2. **机制层同 tick 防护**：开发三 L-1 在成功离开后设置 `reentryBlockedAtTick`，保护同一 tick 的重入。

两者互补：机制层解决同 tick，实体层解决“离开后仍在区域内的下一 tick”。

## 三、将补充的 4 条 entity 测试

| 测试 | 初始条件 | 断言 |
| --- | --- | --- |
| 同 tick 释放 | 已驻留；tick 5 按下合法 `UP`；假端口模拟 L-1，允许区域内 `tryLeave` 返回 `LEFT` | tick 5 端口占用已释放、controller 不再 docked、决策为 `CRUISE(UP)` |
| 留驻不二次进入 | tick 5 同 tick 离开；tick 6 位置仍在同一 region 内 | tick 6 无 `DOCK_ENTERED`、不重新占用 |
| 出界后再进入 | tick 5 离开；tick 6 区域外；tick 7 回到区域内 | tick 7 恰有一个新的 `DOCK_ENTERED` |
| `DOCK_LEFT` tick 归属 | 已驻留；tick 5 按下合法出口 | 事件类型为 `DOCK_LEFT`、`event.tick() == 5`、方向和原因保持 `UP` / `NEW_DIRECTION` |

此外会保留旧测试覆盖：旧按住键不能离开、非法方向继续冻结、占用不属于当前 actor 时不释放。

## 四、接口与模块边界结论

确认无需改动 `AutoDockOccupancyPort`：现有 `tryLeave(mechanismId, actorId, sourceRound, tick, exitDirection, worldPosition)` 已带齐本需求需要的身份、tick、出口和位置参数。

开发一只会修改：

```text
src/main/java/org/example/timeloop/entity/C3DockController.java
src/test/java/org/example/timeloop/entity/C3DockControllerTest.java
```

不会修改：

```text
mechanism/**
app/**
replay/**
AutoDockOccupancyPort 的公共签名
```

## 五、等待 PM 的合入授权

任务卡规定 `ENT-2b` 必须在开发三 L-1 进入 `develop` 后开始；如 PM 裁定成对合并，则须明确共同基线和授权。

请 PM 回复以下任一执行方式：

1. **顺序合入**：提供 L-1 进入 `develop` 的提交哈希；开发一从该基线开始实现本方案；
2. **成对合并**：明确允许开发一基于指定共同基线消费 L-1 的新行为，并指定最终整合与验收责任方。

收到回复后，开发一将按“任务链位置 → 生成前技术原理 → 代码 → 实现效果 → 测试证据”的顺序开始编码。
