# X-MOVE-COLLAPSE-01-DEV2 交付说明（开发二）

> 任务卡：自由移动收口（X-MOVE-COLLAPSE-01-DEV2）
> 收件：PM（唯一对外交付对象；含 app 调用点清单与 sourceRound 裁决请求）
> 日期：2026-09-11
> 分支：develop（本地，未提交未推送）
> 结论：R-1 / R-2 / R-3 / R-4 全部完成，全量 294 测试全绿。

---

## 0. 交付概览

| 项 | 内容 |
|---|---|
| 新增测试 | 3 个测试类、11 个用例（R-1 四个 + R-2 四个 + R-3 三个） |
| 生产代码改动 | 仅 2 处 javadoc（`RecordingSession`、`RoundClock`，共 +11 行），零逻辑改动 |
| 禁止路径 | 未触碰 `app/core/entity/mechanism/level/render/ui/persistence/audio/resources/pom` |
| 测试 | `.\mvnw.cmd -o test` = **294 全绿**，单测均 < 0.1s |

## 1. 验收标准逐项核对

1. ✅ `mvnw -o test` 退出码 0，任何单测 ≤ 1 秒；
2. ✅ R-1 / R-2 / R-3 新增测试全部落地（11 个用例）；
3. ✅ `replay/**` 内不存在按 `MovementState` 值改变行为的分支（`MovementState` 仅出现在
   `PlayerFrame` 的 import 与字段声明处）；
4. ✅ 交付文档含 app 调用点清单（见 §4）。

## 2. 子任务交付详情

### 2.1 R-1 移动脚本逐刻保真（吸收 R2.7-IDLE-FRAMES）

一段 60 帧的混合移动脚本（按住走 → IDLE 松开停 → 段中间按垂直方向停住 → 真死路掉头 →
驻留 12 刻 → 离开），第 1 轮录制、第 2 轮残影回放，逐 tick 六字段全等（x/y/direction/
movementState/actorPhase/animationState），无空档、帧索引严格 == tick。

「索引严格 == tick」由 `TimelineRecording.record` 写入时强制（跳刻/重复/乱序抛异常），
测试只需证明回放链路逐刻保真。

### 2.2 R-2 事件录制闭环（本卡最重要）

锁定契约：`recordEvent → TimelineRecording → EchoState.eventsAt(tick)` 逐字段 round-trip 相等；
同 tick 多事件按 `STABLE_ORDER` 稳定排序；`DOCK_ENTERED(t=40) / DOCK_LEFT(t=210)` 序列
在下一轮重放出「占用 / 释放」语义。

| 契约点 | 测试 |
|---|---|
| round-trip 逐字段相等（7 字段，含 leaveDirection/reason） | `recordEvent_roundTripsFieldByField` |
| 占用/释放边沿语义（t=41..209 无事件，t=210 释放） | `dockEnteredThenLeft_replaysOccupancyAndReleaseSemantics` |
| 同 tick 稳定排序（与写入顺序无关） | `sameTickEvents_sortedByStableOrder` |
| RESETTING / RESULT 拒绝写事件 | `recordEvent_rejectedOutsidePlaying` |

### 2.3 R-3 轮边界契约

**根因链条**（「第二轮动不了」）：

```
tick() 首句 if (!clock.isPlaying()) return;          （Level01Assembly 第 144 行）
    ↓
onRoundEnd() 调 completeNormalRound → 停在 READY     （第 363–374 行）
    ↓
onRoundEnd() 之后没有 transition(PLAYING)
    ↓
下一帧 tick() 进来，READY 不是 PLAYING → 直接 return → 玩家再也动不了
```

契约（已写入 javadoc + 测试锁定）：

| 契约 | 测试 |
|---|---|
| `completeNormalRound` 后 READY、roundTick==0、currentRound+1 | `completeNormalRound_endsAtReadyAndCallerMustResumePlaying` |
| READY 下 recordFrame / recordEvent 抛异常 | `recordFrameAndEvent_rejectedInReady` |
| transition(PLAYING) 后新缓冲可用、帧索引从 0、durationTicks 不变 | `resumePlayingAfterReady_newBufferReusableAndTickRestartsFromZero` |

javadoc 改动：`RecordingSession` 类 javadoc、`completeNormalRound` 方法 javadoc、
`RoundClock` 类 javadoc 各新增「轮末停靠契约」段。

## 3. sourceRound 裁决（待 PM 确认）

**裁决建议：事件的 `sourceRound` = 产生时的 `currentRound`（≥1），废弃「活玩家用 0」。**

- 现状：app 硬编码 `PLAYER_SOURCE_ROUND=0`，且 `C3DockController.sourceRound` 为 final 构造固定，
  第 2 轮活玩家事件仍写 0，与上一轮残影（sourceRound≥1）在占用上共用 `(actorId="player", 0)`，
  互相顶掉占用；`L=1` 不暴露，寿命拉大即现。
- 裁决后身份唯一：第 N 轮活玩家 sourceRound=N，残影 E1=1、E2=2。
- 落地分两边：app 侧（PM 改，开发二不代改）传 `clock.currentRound()`；
  replay 侧可选把 `TimelineEvent` 校验从 `≥0` 收紧为 `≥1`（等 app 改完再做）。

## 4. app 调用点清单（给 PM，开发二不改 app）

### 4.1 recordEvent 三个事件（R-2）

`C3DockController` 产生的三类事件目前只攒在 `Level01Assembly.events` 列表里，
**从未调用 `RecordingSession.recordEvent`**，导致残影 `eventsAt(t)` 恒为空、回放占不了板。
需在 PLAYING 中把这三类事件转调 `recording.recordEvent`：

- `DOCK_ENTERED` —— 进入驻留边沿；
- `DOCK_LEFT` —— 离开驻留边沿（带 `leaveDirection` + `reason="NEW_DIRECTION"`）；
- `OCCUPANCY_RELEASED` —— 占用释放（残影淘汰 / 整局重开 / 退出）。

同 tick 写入顺序不影响回放（封装时按 `STABLE_ORDER` 统一排序）。

### 4.2 轮末回 PLAYING（R-3）

`app/Level01Assembly.onRoundEnd()` 里 `completeNormalRound(...)` 之后补一行：

```java
recording.completeNormalRound(
        () -> autoDock.reset(AutoDockResetReason.ROUND_END, clock.roundTick()));
clock.transition(GamePhase.PLAYING);   // ← 补这一行
```

### 4.3 轮初 resetTo（R-3，占位）

开发一 `ENT-3 resetTo` 落地前只写占位，不引用未存在 API：

```java
// 轮初（transition(PLAYING) 之后）：调用开发一的 resetTo 复位玩家位置/朝向/排队方向
// player.resetTo(...)   // 占位 —— 待 ENT-3 落地后补
```

## 5. 新增测试清单

| 文件 | 覆盖 | 用例数 |
|---|---|---|
| `MovementScriptFidelityTest` | R-1 混合形态逐刻保真、IDLE 无空档、DOCKED≥12 刻、索引==tick | 4 |
| `EventRoundTripTest` | R-2 round-trip、占用/释放边沿、稳定排序、非 PLAYING 拒绝 | 4 |
| `RoundBoundaryContractTest` | R-3 停 READY、READY 拒绝写、PLAYING 后新缓冲复用 | 3 |

## 6. 遗留与依赖

- **sourceRound 裁决**：待 PM 确认后由 app 侧按 §3 落地。
- **ENT-3 resetTo**（开发一）：落地后 R-3 的「轮初复位」从占位转为实际接线。
- **ENT-2a tryLeave 同刻释放**（开发三）：落地后 `DOCK_LEFT` 的 tick 语义前移，
  R-2 的 `LEAVE_TICK=210` 示例序列需按最终语义微调一次（PM 会通知）。
