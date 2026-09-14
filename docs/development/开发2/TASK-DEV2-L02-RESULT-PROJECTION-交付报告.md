# TASK-DEV2-L02-通关结果状态投影 · 交付报告

> 任务卡：TASK-DEV2-L02-RESULT-PROJECTION（郎森/测试拟稿）
> 主责：开发二（`replay/**`）　｜　日期：2026-09-14
> 基线：`origin/develop` = `765f461`

## 一、交付摘要

按 PM 冻结口径，为「通关提醒界面」提供只读结果数据 `LevelResult`，并完成定长录制 / 残影回放 / 轮次边界 / 寿命 / 整局重开的回归验证。

## 二、实现内容（`replay/**`）

**1. 新增 `LevelResult`（5 字段，严格按 PM 冻结口径）**

```java
public record LevelResult(
        String levelName,   // 关卡名（必须有）
        boolean cleared,    // 是否通关
        int clearedRound,   // 完成轮次
        int maxRounds,      // 总轮数
        long usedTicks      // 用时（刻）
) {}
```

- 含 `levelName`、**不含 `phase`**（GamePhase 是装配/时钟权威状态，不复制进结算 DTO，避免两个真相源）。
- 附 `usedSeconds()`（`usedTicks / 60`）供界面显示秒数。

**2. `RecordingSession` 固化结果**

- `completeGoal()` 固化通关结果（`cleared=true`，`clearedRound`=达成轮）。
- `failFinalRound()` 固化失败结果（`cleared=false`，`clearedRound`=maxRounds 轮）。
- `restartFromFirstRound()` 清空结果。
- 暴露只读 `result()`（`Optional<LevelResult>`，未终局为空）。

**3. 用时口径（与 HUD 唯一共享读秒同源）**

```java
usedTicks = (clearedRound - 1) × durationTicks + roundTickAtEnd
```

- `roundTickAtEnd` 取达成/失败那一刻的 `clock.roundTick()`（RESULT/FAILED 阶段均保留 roundTick）。
- **未**使用 `System.currentTimeMillis()` 或任何第二套计时器。

## 三、测试结果

| 项 | 结果 |
|---|---|
| `LevelResultTest` | 6 条（通关/失败固化、未终局空、重开清空、终局 roundTick 冻结、usedSeconds） |
| 全量 `mvn test` | **494 / 0 / 0 / 0**，BUILD SUCCESS |
| 回归 | 定长录制、残影回放、轮次边界、寿命、整局重开全部保持通过 |
| `git diff --check` | 干净 |

## 四、需 PM 接线（1 处，`app/**` 是 PM 路径）

`levelName` 的来源是 `app/LevelFlow.LevelId.title()`，但 `app/**` 不在开发二允许路径内。
replay 层已用**重载构造器**接收（旧二参构造器委托 null，兼容不破坏），请 PM 在两处装配接线：

```java
// Level01Assembly（约 L125）
this.recording = new RecordingSession(clock, echoQueue, LevelFlow.LevelId.LEVEL_01.title());

// Level02Assembly（约 L156）
this.recording = new RecordingSession(clock, echoQueue, LevelFlow.LevelId.LEVEL_02.title());
```

未接线前 `levelName` 为 `null`（不阻塞编译与测试），接线后结算界面即可显示真实关卡名。

## 五、回传字段

```
branch: codex/l02-result-projection
SHA:    <提交后回填>
通关投影: LevelResult（levelName/cleared/clearedRound/maxRounds/usedTicks）
用时口径: (clearedRound-1)*durationTicks + roundTickAtEnd（共享读秒同源）
测试: LevelResultTest 6 条
clean test: 494 / 0 / 0 / 0
```

## 六、停止条件核对

- 未改动 `TimelineRecording` 记录格式 ✅
- 未新增第二套计时器 ✅
- 未在 RESULT/FAILED 推进 roundTick ✅
- 未触及 core 时钟语义 / 机关事件类型 ✅
