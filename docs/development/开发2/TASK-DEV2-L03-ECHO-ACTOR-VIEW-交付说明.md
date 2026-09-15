# TASK-DEV2-L03-ECHO-ACTOR-VIEW · 交付说明（残影角色只读取帧语义）

- 任务卡：`L03-残影角色取帧-任务卡-开发2.md`
- 主责：开发二（`replay/**`、`snapshot/**`）
- 分支：`codex/l03-dev2-echo-actor`
- 依据：PM 裁决 —— app 直接从 `EchoState.frameAt` 投影，**不新增 DTO**

---

## 一、交付物对照

| 任务卡要求 | 状态 | 落点 |
|---|---|---|
| 1. 确认 `EchoState.frameAt(long tick)` 为**唯一取帧入口** | ✅ | 测试 `frameAtIsTheOnlyPublicFrameAccessor`（反射锁定：全类只有它返回 `PlayerFrame`） |
| 2. 语义冻结 + 测试（任意 tick 稳定 / 逐字段一致 / 回放不重算射线） | ✅ | `EchoActorViewTest` 共 8 条；`EchoState.frameAt` javadoc 冻结契约 |
| 3. **消散轮语义写明**（GONE 后是返回末帧还是拒绝） | ✅ | `EchoState.frameAt` javadoc「消散轮 / GONE 语义」条 + 测试锁定 |
| 4. 对 P3 给出结论（是否含回放侧因素） | ✅ | 本文 §四 |

---

## 二、取帧语义（本次冻结）

`EchoState.frameAt(long roundTick)` —— 残影**唯一**的取帧入口，只服务「残影当前该画什么姿态」：

1. **与寿命 / 消散状态无关**：合法 `roundTick`（`[0, durationTicks)`）**恒返回该刻录制帧**，
   不因处于「最后有效轮」或已被淘汰而改变，也不返回 `null`。
2. **越界严格拒绝**：`< 0` 或 `>= durationTicks` 一律抛 `IndexOutOfBoundsException`，
   **绝不返回最后一帧补齐**（不许"兜底"）。
3. **GONE 之后的语义**：`EchoState` **不做寿命判断、不感知「消散」**。
   被淘汰（GONE）的残影不再出现在 `EchoQueue.activeEchoes(int)`，**由调用方据此停止取帧**；
   只要调用方仍在合法区间取帧，`frameAt` 照常返回该刻录制帧。
   → 即：**"是否继续绘制"是调用方的决定，不是 `frameAt` 的分支**。
4. **回放不重算射线**：只做只读索引 —— 不写减速、不刷新 `(rayId, activeCycle)` 去重、
   不改变任何位置或状态。

---

## 三、测试清单（`EchoActorViewTest`，8 条）

| # | 测试 | 钉住的契约 |
|---|---|---|
| 1 | `frameAtIsTheOnlyPublicFrameAccessor` | 入口唯一（反射：仅 `frameAt` 返回 `PlayerFrame`） |
| 2 | `frameAtMatchesRecordedFrameFieldByFieldAtEveryTick` | 逐字段保真（tick/x/y/朝向/交互/移动状态/相位/相位剩余刻/动画，9 字段逐刻全比） |
| 3 | `frameAtIsStableAtFirstAndLastTick` | 刻 `0` 与轮末 `D-1` 稳定（且 `assertSame`：直接回传录制帧，不复制不重算） |
| 4 | `frameAtRejectsOutOfRangeTick` | 越界拒绝（`D` / `D+1` / `-1` / `Long.MAX_VALUE`） |
| 5 | `frameAtNeverSubstitutesLastFrameForOtherTicks` | **绝不补末帧**（除 `D-1` 外任何刻都不得等于末帧） |
| 6 | `frameAtIsPureReadOnlyRegardlessOfReadOrder` | 纯只读（逆序读 / 重复读结果不变；录制不被改变、不被解封） |
| 7 | `echoStateHoldsNoMutableStateBeyondTheRecording` | 无第二套状态（反射：仅 `final recording` 一个字段） |
| 8 | `frameAtRemainsReadableAcrossTheWholeTimelineRegardlessOfLifetime` | 消散轮无关（整条时间线逐刻可取；越界仍拒绝） |

> 刻意用「每刻字段都在变」的录制做夹具，使任何「取错刻 / 补末帧 / 丢字段」都会立刻变红。

---

## 四、P3（残影重合时板占用中途失效）· 是否含回放侧因素

**结论：不含。** 回放侧不存在「事件被丢」或「重复消费」的机制，三条依据：

1. **事件读取是纯查询，没有"消费"语义。**
   `EchoState.eventsAt(tick)` → `TimelineRecording.eventsAt(tick)` 每次都**从事件列表重新过滤**该 tick
   （`for (e : events) if (e.tick() == tick)`），不维护游标、不清空、不 drain。
   ⇒ 同一 tick 重复调用结果**完全相同**；既不会"读一次就没了"，也不会"读两次算两次"。

2. **`EchoState` 无任何可变状态。**
   只有 `private final TimelineRecording recording` 一个字段；本卡的测试 #7 把这一点钉进了门禁。

3. **回放层不持有机关状态、不派发事件。**
   残影对机关（驻留板/门）的写入完全由调用方（app 的 `replayEchoEvents`）按 `eventsAt`
   返回的事件驱动；replay 侧只回答"该刻残影做过什么"。

**P3 的实际来源（已定位，非回放侧）**：第三关 C 板是**窗口板** ——
`Level03Pursuit.HOLD_C_TILES = 6`（= 144 刻 = 门 C 窗口宽度），E₁ 的录制里**如实存在**
`DOCK_LEFT`（刻 744），回放照做即释放。即「有限帧数后失效」= **刻表设计**，不是事件丢失或重复消费。

> 详见 `docs/development/开发2/TASK-DEV2-L03-ECHO-OCCUPANCY-排查报告.md`（§二 现象 vs 刻表、§五 释放路径枚举）。

---

## 五、停止条件核对（任务卡 §五）

| 停止条件 | 是否触发 |
|---|---|
| 需要改录制格式 | ❌ 未触发（`TimelineRecording` 一行未动） |
| 需要改 R5-B 冻结契约 | ❌ 未触发（`snapshot/**` 未动） |

**改动范围**：仅 `replay/EchoState.java`（javadoc）、`replay` 测试目录新增 `EchoActorViewTest.java`、本文档。
**未新增任何 DTO**（符合 PM 裁决）。
