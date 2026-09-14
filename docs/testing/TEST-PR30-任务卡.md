# 任务卡：EchoLifetime 残影整轮寿命权威计算

## 1. 基本信息

| 项目 | 内容 |
|---|---|
| 任务编号 | TTL-R4-ECHO-LIFETIME |
| 任务名称 | EchoLifetime 残影整轮寿命权威计算 |
| 所属模块 | `org.example.timeloop.replay` |
| 主责 | 开发 2 |
| 阶段 | R4 基础 |
| 优先级 | P0 |
| 依赖 | `TickContext`、`RoundClock`、README「统一生命周期公式」、开发 2 技术指南 §3.6 |
| 前置条件 | 项目可编译；共享 `TickContext` / `RoundClock` 已就绪 |

> 备注：当前 `mvnw.cmd test` 编译失败，根因是 `Ray.java` 缺少 `org.example.timeloop.mechanism.event` 包。该问题与本任务卡无直接关系，但会阻塞整体测试，需要先恢复 `GameObserver`、`GameEvent`、`EventDispatcher` 或修正 `Ray.java` 的 import。

---

## 2. 背景

残影的整轮寿命必须有唯一权威计算来源，避免各模块自建轮次、秒数或透明度计时器。  
本任务要求实现并验证 `EchoLifetime`，使其成为 README「统一生命周期公式」的唯一实现。

核心公式：

```text
age             = r - s
active          = 1 <= age <= L
remainingRounds = L - age + 1
lifeProgress    = clamp(((age - 1) * D + roundTick) / (L * D), 0, 1)
bodyAlpha       = 0.82 - 0.32 * lifeProgress
```

其中：

- `s`：来源轮次 `sourceRound`
- `r`：当前轮次 `currentRound`
- `L`：整轮寿命 `lifetimeRounds`
- `D`：轮长 `durationTicks`
- `roundTick`：当前轮内刻，来自共享 `TickContext`

---

## 3. 目标

1. 实现 `EchoLifetime`，所有轮次与刻必须从共享 `TickContext` 派生。
2. 保证 `active` 与 `remainingRounds` 只随轮次变化，决定玩法状态。
3. 保证 `lifeProgress` 只用于透明度、轨迹与粒子表现，不是新的游戏计时器。
4. 禁止 `EchoLifetime` 持有任何轮次/秒数计数器。
5. 提供完整单元测试，覆盖公式、边界、异常与共享时钟集成。

---

## 4. 交付物

| 交付物 | 路径 / 说明 |
|---|---|
| `EchoLifetime.java` | `src/main/java/org/example/timeloop/replay/EchoLifetime.java` |
| `EchoLifetimeTest.java` | `src/test/java/org/example/timeloop/replay/EchoLifetimeTest.java` |
| 文档更新 | README 或技术指南中确认「统一生命周期公式」由本类实现 |
| 测试报告 | `mvnw.cmd test` 通过，且本类相关测试全部通过 |

---

## 5. 接口与行为

| 方法 | 行为 |
|---|---|
| `EchoLifetime(int sourceRound, int lifetimeRounds, long durationTicks, int currentRound, long roundTick)` | 直接构造，参数必须满足范围，非法抛 `IllegalArgumentException` |
| `static EchoLifetime of(int sourceRound, int lifetimeRounds, TickContext context)` | 推荐构造方式，从共享 `TickContext` 取 `durationTicks()`、`currentRound()`、`roundTick()` |
| `getAge()` | `currentRound - sourceRound` |
| `isActive()` | `1 <= age <= lifetimeRounds` |
| `getRemainingRounds()` | 未生效返回 `0`，否则 `L - age + 1` |
| `getLifeProgress()` | 按公式计算并 clamp 到 `[0, 1]`；`age < 1` 为 `0`，`age > L` 为 `1` |
| `getBodyAlpha()` | `0.82 - 0.32 * lifeProgress` |
| `isLastEffectiveRound()` | `getRemainingRounds() == 1` |
| `shouldDisappearAtRoundEnd()` | `isActive() && getRemainingRounds() == 1` |
| `getSourceRound()` | 返回来源轮次 |
| `getLifetimeRounds()` | 返回整轮寿命 `L` |

---

## 6. 约束

- 残影只能在轮次边界生成或淘汰，不得因透明度降低在轮中移除。
- `bodyAlpha` 从约 `0.82` 平滑降至 `0.50`。
- 最后一轮结束后的 `0.50 → 0` 淡出属于 `RESETTING` 视觉过渡，不由本公式表达。
- `EchoLifetime` 不持有任何计数器；轮次与进度由调用方通过 `of(int, int, TickContext)` 从共享 `TickContext` 派生。
- 禁止自建第二套轮次/秒数。
- 参数范围：
  - `sourceRound >= 1`
  - `lifetimeRounds >= 1`
  - `durationTicks >= 1`
  - `currentRound >= 1`
  - `roundTick ∈ [0, durationTicks)`

---

## 7. 验收标准

- [ ] 公式实现与 README「统一生命周期公式」完全一致。
- [ ] `of(...)` 使用共享 `TickContext`，调用方无需传入自报轮次。
- [ ] `isActive()` 与 `getRemainingRounds()` 只随轮次变化。
- [ ] `getLifeProgress()` 正确 clamp，且不引入新计时器。
- [ ] `getBodyAlpha()` 输出范围在 `0.50 ~ 0.82` 之间。
- [ ] `shouldDisappearAtRoundEnd()` 仅在最后一个有效轮返回 `true`。
- [ ] 所有非法参数均抛出 `IllegalArgumentException`。
- [ ] 单元测试覆盖：`age < 1`、`age = 1`、`age = L`、`age > L`、`roundTick = 0`、`roundTick = D - 1`。
- [ ] `mvnw.cmd test` 通过，无编译错误。

---

## 8. 测试要点

建议至少覆盖以下用例：

| 用例 | 输入 | 期望 |
|---|---|---|
| 未生效 | `s=1, L=3, D=100, r=1, tick=0` | `age=0, active=false, remaining=0, lifeProgress=0, bodyAlpha=0.82` |
| 第一有效轮开始 | `s=1, L=3, D=100, r=2, tick=0` | `age=1, active=true, remaining=3, lifeProgress=0, bodyAlpha=0.82` |
| 第一有效轮中段 | `s=1, L=3, D=100, r=2, tick=50` | `lifeProgress=50/300≈0.1667, bodyAlpha≈0.7667` |
| 最后有效轮末刻 | `s=1, L=3, D=100, r=4, tick=99` | `age=3, active=true, remaining=1, lifeProgress=299/300≈0.9967, bodyAlpha≈0.501` |
| 过期 | `s=1, L=3, D=100, r=5, tick=0` | `age=4, active=false, remaining=0, lifeProgress=1, bodyAlpha=0.50` |
| 非法参数 | `sourceRound=0`、`lifetimeRounds=0`、`durationTicks=0`、`currentRound=0`、`roundTick=-1`、`roundTick=D` | 均抛 `IllegalArgumentException` |

---

## 9. 完成定义 DoD

- [ ] 代码已合并到主分支。
- [ ] `EchoLifetime` 无编译警告与错误。
- [ ] 单元测试全部通过。
- [ ] 相关文档或注释与实现一致。
- [ ] 调用方使用 `EchoLifetime.of(...)` + 共享 `TickContext`，未出现自建轮次计数器。
- [ ] 整体 `mvnw.cmd clean test` 通过。

---

## 10. 风险与注意事项

1. **共享时钟未接通**：若 `TickContext` 尚未提供 `durationTicks()`、`currentRound()`、`roundTick()`，本任务会被阻塞。
2. **调用方绕过 `of(...)`**：直接使用构造函数并自报轮次，会破坏「唯一权威」原则，需要在代码审查中拦截。
3. **透明度与玩法状态混淆**：不得因 `bodyAlpha` 降低而在轮中移除残影；淘汰只能发生在轮次边界。
4. **整体编译失败**：当前 `Ray.java` 缺少 `event` 包，会阻塞 `mvnw test`；需先修复 `org.example.timeloop.mechanism.event` 下的 `GameObserver`、`GameEvent`、`EventDispatcher`。