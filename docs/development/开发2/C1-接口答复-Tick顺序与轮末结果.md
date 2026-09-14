# 开发 2 → 开发 1：C1 两个接口问题的正式答复

- 日期：2026-09-09
- 答复人：开发 2（时间与状态系统，`replay` 包）
- 对象：开发 1（`core` 包：FixedStepLoop / TickUpdatePort / 固定步长接入）
- 依据：`docs/decisions/C1-共享时钟与单刻顺序约定.md` §2、§3、§5.1
- 说明：**本答复不依赖 R1.5 的前置条件，可立即采纳。**

---

## A. TickContext 在一次 `stepOnce()` 内应在何时读取？

### 结论

你的四步「读取当前 tick → 玩家/机关更新 → 记录 tick 末状态 → `RoundClock.advance()`」**方向正确，但漏了三步，且漏掉的那三步里有硬约束**。请直接采用 C1 §3 已冻结的完整 7 步：

```text
1. 读取当前 TickContext（roundTick）
2. 消费该 tick 输入
3. 更新玩家、机关和残影
4. 发布并稳定排序该 tick 事件
5. 记录该 tick 结束后的规范状态
6. 生成只读渲染快照
7. 调用 RoundClock.advance()
```

### 对你那版的四处修正（按重要性排序）

1. **渲染快照必须在 `advance()` 之前生成（第 6 步在你版本里缺失，且位置不可挪后）**
   快照与当刻帧必须同源。若先 `advance()` 再生成快照，快照会带上 `roundTick+1` 的刻度却装着 `roundTick` 的内容，插值与残影渲染会出现半格错位。

2. **「消费输入」应在读取 TickContext 之后、更新之前（第 2 步）**
   输入要打上本 tick 的戳。若先更新再消费，输入会被记到错误的刻上。

3. **「发布并稳定排序事件」是独立一步（第 4 步）**
   排序不稳定会导致同一 tick 内事件顺序在回放与实战之间漂移，R3 残影回放会直接对不上。

4. **`advance()` 是 `stepOnce()` 的最后一步，且只调用一次**

### 给我这边的对应约束（开发 2 承诺）

- `RoundClock.toContext()` 返回**不可变快照**（`TickContext` 字段全 final），你可以在第 1 步取一次、放心传给所有 actor，不会被下游改坏。
- 权威值仍只在 `RoundClock` 内部；actor 只读，不回写。

### 一个容易踩的坑（补算多 tick 时）

一帧补算 N 个 tick，就要执行 N 次 `stepOnce()`，**每一次都必须重新读取 TickContext**（第 1 步在循环内，不是循环外）。因为上一次 `stepOnce()` 末尾的 `advance()` 已经把 `roundTick` 推到下一位了。

---

## B. `advance()` 返回 `ROUND_END` 后，`FixedStepLoop` 是否必须立刻停止本帧剩余补算？

### 结论：**必须立刻停止，且剩余补算额度直接丢弃，不能带入下一轮。**（C1 §2 已冻结）

### 为什么不能继续（两条理由）

1. **继续调用会重复处理同一刻。** `ROUND_END` 之后 `roundTick` 停在 `D-1` 不动。再 `stepOnce()` 一次，等于把第 `D-1` 刻的输入、更新、记录、快照**原样跑第二遍** —— 输入被消费两次、该刻被记录两条、残影寿命推进两格。这会直接破坏 C1 §3 的推论「有效记录恰好 D 条、`frame.tick` 等于列表索引」。

2. **你无法靠查询时钟状态来绕过这个判断。** 这点很关键：
   `advance()` 返回 `ROUND_END` 时，`RoundClock` 的内部状态**完全没变** ——
   `phase()` 仍是 `PLAYING`、`roundTick()` 仍是 `D-1`、`currentRound()` 不自增。
   也就是说**「已经到轮末」这件事，只能通过 `advance()` 的返回值观测，查任何 getter 都看不出来**。
   所以 `void` 接口是致命的，不是"暂时不方便"。

### 最小结果类型（我的建议）

**三值枚举，不要回调。**

```java
public enum TickStepResult { NO_ADVANCE, ADVANCED, ROUND_END }
```

`FixedStepLoop` 侧的用法就一行判断：

```java
for (int i = 0; i < steps; i++) {
    if (port.stepOnce() != TickStepResult.ADVANCED) {
        break;   // NO_ADVANCE 或 ROUND_END：立即停止剩余步数
    }
}
// 剩余 steps 直接丢弃，不回填 accumulator、不带入下一帧
```

### 三个细节

1. **`NO_ADVANCE` 也必须返回，不能只区分推进/轮末。**
   暂停、教学、准备等冻结阶段下，`stepOnce()` 仍会被调用，此时必须让循环**立刻停止补算**，否则会在暂停帧里空转掉本帧的补算额度。

2. **返回值应原样透传，不要二次映射。**
   `TickUpdatePort.stepOnce()` 的返回值应当就是 `RoundClock.advance()` 的返回值。`RoundClock` 是唯一权威，中间加映射层就会出现语义漂移（C1 §1 明确反对映射层）。

3. **不要用回调 / listener 形式。**
   `FixedStepLoop` 需要的是一个**同步的"继续还是停止"决策**，回调表达不了返回值语义，且控制流反转后难以单测。三值枚举是最简形式。

### ⚠️ 放置位置属未裁决项，请等 PM 拍板再动

C1 §5.1 已标记：你要建的 `TickStepResult` 与**既有的** `replay.AdvanceResult` 三个值**逐字相同**（`NO_ADVANCE / ADVANCED / ROUND_END`，21 条测试在用）。

| 方案 | 放置 | 依赖方向 | 代价 |
| --- | --- | --- | --- |
| **A（开发 2 推荐）** | `core.TickStepResult`，删除 `replay.AdvanceResult`，`RoundClock.advance()` 改返回它 | 与 C1 §1 一致，全项目单向 `replay → core` | 开发 2 需改 `RoundClock` 签名与 12 条测试（**属独立卡，不在 R1.5 范围**） |
| B | 保留 `replay.AdvanceResult`，`core.TickUpdatePort` import 它 | 引入 `core → replay`，与 C1 §1 方向相反 | 依赖双向，后期易循环 |

**请你先不要擅自新建 `core.TickStepResult`** —— 若 PM 选 B，那个类就得删掉。可以先按三值语义写 `FixedStepLoop` 的分支逻辑，类型名留占位。

---

## 我这边（开发 2）对外承诺的 `RoundClock` 接口（供你接入）

R1.5 迁移完成后保持以下签名不变（仅 `phase` 相关类型由 `RoundPhase` 改为 `core.GamePhase`）：

```java
public RoundClock(int durationTicks, int maxRounds)   // 两参均为 final，进关冻结
public void transition(GamePhase to)                  // 非法转移抛 IllegalStateException
public AdvanceResult advance()                        // NO_ADVANCE / ADVANCED / ROUND_END
public GamePhase phase()
public long roundTick()                               // 0 .. durationTicks-1，不存在索引 D
public int currentRound()
public int durationTicks()
public int maxRounds()
public boolean isPlaying()                            // phase == GamePhase.PLAYING
public TickContext toContext()                        // 不可变快照
```

行为保证：

- 仅 `PLAYING` 推进 `roundTick`；冻结阶段返回 `NO_ADVANCE`
- 到达 `D-1` 后再推进恒返回 `ROUND_END`，**不递增、不切轮、不存在索引 D**
- `ROUND_END` 后**不会自动切到** `RESETTING` / `RESULT` / `FAILED` —— 轮次事务由上层（level 逻辑）发起，请 `FixedStepLoop` 收到 `ROUND_END` 后停止补算并把控制权交回上层，不要继续 `stepOnce()`
- 非法转移抛 `IllegalStateException`（10 组 `LEGAL` 边，R1.5 后逐条保持不变）
