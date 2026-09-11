# R5-B / P-MOVE-ALIGN：开发二交接文档

> 用途：交给下一位开发者继续完成开发二的 R5-B 与移动模型变更配套。
> 日期：2026-09-11
> 状态：前置条件未全部满足；本次只完成核对和交接，不新增业务代码。

## 0. 核心结论

这次有两张外部任务卡，范围不同：

1. 用户目标是把固定速度改为四方向受约束自由移动。移动推进、MovementState 枚举和玩家行为属于开发一的 core/entity 范围。
2. PM 给开发二的卡是 R2.7-IDLE-FRAMES：开发一先加入 MovementState.IDLE，开发二再确认 replay/**、snapshot/** 能逐刻记录和回放静止帧，不新增第二套移动逻辑。
3. 开发三给出的 R5-B 是共振快照端口配套：开发二后续只能通过 typed port 聚合，不复制共振状态机、时钟或残影队列。

在 MovementState.IDLE 尚未进入当前集成分支、且 R5-B 端口尚未合入当前分支前，不应写依赖代码，也不应修改 core/**、entity/**、mechanism/** 或 app/**。

## 1. 当前已验证事实

核对工作树：C:\Users\dhls\.codex\worktrees\323a\Java-project

| 项 | 当前事实 |
| --- | --- |
| 分支 | codex/dev2-1 |
| HEAD | 91bf8a039d4cc873b6ce3e911966a7a7c805e378，R5-A 快照加固提交 |
| 工作树 | 写本文前 git status --short 为空；本文会成为新的未跟踪文档 |
| R5-A PR | #47：https://github.com/time-trace-lab-team/time-trace-lab/pull/47；创建时 base=develop、head=codex/dev2-1 |
| 当前 MovementState | 只有 CRUISING / SLOWED / DOCKED，没有 IDLE |
| origin/develop | 当前本地跟踪引用中没有 MovementState.IDLE |
| codex/dev1-c3 | 当前本地引用中没有 MovementState.IDLE |
| 开发三提交 | ab1a766b84de84f83a27b4cd89026194eaf03b1d 存在于对象库，但不是当前 HEAD 的祖先 |
| 当前共振源码 | 当前工作树没有 mechanism/resonance/**；不能当作已合入 |
| 当前基线测试 | .\mvnw.cmd test：252 条通过 |

上表的分支、PR 和测试数字是时间点事实；接手时必须重新核对。

## 2. 已完成的开发二内容

R5-A 只覆盖三种既有机关：DockingPlate、Door、ExitTerminal。

- MechanismSnapshot 的三个 Map 是防御性不可变副本。
- 捕获时校验 Map key、对象稳定 ID 和快照稳定 ID。
- 恢复前先校验 null、稳定 ID 和完整 ID 集合；缺失或多余对象会失败，不静默跳过。
- 测试覆盖空集合、null、ID 不一致、缺失、多余、输入 Map 后续变化、集合不可变和正常恢复。
- 不使用反射扫描未知机关。

不要把 R5-A 的 MechanismSnapshot 当成已经包含 AutoDock、共振、射线、中继或核心的总聚合器。

## 3. 开发三提供的 R5-B 契约

开发三提交 ab1a766... 中的模块内端口为：

~~~
public interface ResonanceSnapshotPort {
    ResonanceStateSnapshot createSnapshot();
    void restore(ResonanceStateSnapshot snapshot);
}
~~~

快照记录形状为：

~~~
public record ResonanceStateSnapshot(
        ResonanceState state,
        long armedAtRoundTick,
        int armedSourceRound,
        boolean currentPlayerInside,
        List<Integer> insideEchoSourceRounds
) {}
~~~

契约边界：

- insideEchoSourceRounds 必须用 List.copyOf 暴露。
- armedAtRoundTick 是共享 TickContext 的历史刻标记，不是新时钟。
- armedSourceRound 是首个有效历史来源；当前玩家不充当历史来源。
- 不复制 TickContext、EchoQueue、EchoState 或任何独立计时器。
- ResonanceStateMachine 自己负责 observe(...)、状态校验和 reset(ResonanceResetReason)。
- 聚合器只能依赖公开的 typed port，不得引用状态机私有字段或复制其集合。

## 4. P-MOVE-ALIGN 的开发二边界

PM 卡任务 ID：R2.7-IDLE-FRAMES。

### 前置条件

- 开发一先在 core/MovementState 增加 IDLE。
- 移动推进、松键停止和 PlayerKinematics 语义由开发一完成。
- 开发二不得自行向 core 添加 IDLE 来帮助编译。

### 前置满足后的开发二工作

只在 replay/**、snapshot/** 及对应测试中确认：

1. 连续 IDLE 帧可以写入缓冲，size() 与 frameAt() 无空档。
2. CRUISING → IDLE → DOCKED → IDLE → CRUISING 中每帧 tick 严格等于列表索引。
3. EchoState.frameAt(roundTick) 逐 tick 返回原帧，不跳帧、不补帧、不维护独立播放时间。
4. IDLE 不产生 TimelineEvent。
5. 满长封装、未满丢弃、轮末事务和 DOCKED 静止帧不回归。

replay/**、snapshot/** 不得按 MovementState 值分支来改变索引、时钟或回放规则。

## 5. 建议接手顺序

### Gate A：同一集成基线

1. 重新核对工作树、分支和 origin/develop。
2. 等开发一的 MovementState.IDLE 通过其自身测试并进入 PM 指定基线。
3. 等 PM 确认如何把开发三 ab1a766... 接入当前基线；不要从另一 worktree 手工复制文件。
4. 对照 live source 确认四个端口/快照的真实包名、字段和构造校验。

### Gate B：冻结 R5-B 聚合接口

实现 snapshot/** 聚合器前，由 PM/调用方确认：

- 聚合器类型名和字段清单；
- 初始快照捕获时机；
- ROUND_END 是调用端口 reset(ROUND_END)，还是恢复轮内快照；
- FULL_RESTART 的完整恢复顺序；
- 恢复失败时如何保证没有半恢复状态。

未裁决前只能提交接口需求或测试设计，不能猜测顺序。

### Gate C：实现与验证

- 只读调用 ResonanceSnapshotPort、AutoDockSnapshotPort；不反射、不复制第二套 replay 类型。
- 聚合快照中的 Map/List 使用不可变副本。
- 所有 ID 集合、null 和状态组合先校验，再调用任何恢复端口。
- 为 ROUND_END、FULL_RESTART、正常恢复、缺失/多余对象和失败注入补测试。
- 运行：

    .\mvnw.cmd "-Dtest=TimelineRecordingTest,EchoStateTest,RecordingSessionTest" test
    .\mvnw.cmd test
    git diff --check
    git status --short

自动化测试不等同于 JavaFX 画面或页面行为；视觉验收要另列手工证据。

## 6. 允许与禁止路径

允许：

~~~
src/main/java/org/example/timeloop/replay/**
src/main/java/org/example/timeloop/snapshot/**
src/test/java/org/example/timeloop/replay/**
src/test/java/org/example/timeloop/snapshot/**
~~~

禁止：

~~~
pom.xml
src/main/java/org/example/timeloop/app/**
src/main/java/org/example/timeloop/core/**
src/main/java/org/example/timeloop/entity/**
src/main/java/org/example/timeloop/mechanism/**
src/main/java/org/example/timeloop/level/**
src/main/java/org/example/timeloop/render/**
src/main/java/org/example/timeloop/ui/**
src/main/resources/**
~~~

特别保留、不修改、不暂存：

~~~
docs/development/开发1/任务卡-C2-恒速巡行与路径图.md
~~~

## 7. Git 交接建议

codex/dev2-1 是 R5-A 分支。继续 R5-B 时：

1. 不要在旧 R5-A 分支上混入未裁决的开发一/三代码。
2. 按 PM 指定的已合入基线创建新的 codex/ 分支。
3. 使用仓库 Maven Wrapper，不使用全局 Maven 或历史测试数字。
4. 提交前显式检查 git diff --cached --name-only，排除其他角色的未跟踪文件。
5. 提交、推送、创建 PR 是三个独立动作；PR base 必须为 develop。

## 8. 停止条件

遇到以下任一情况立即停止并回传证据：

- MovementState.IDLE 尚未进入当前基线，却要求写依赖代码；
- 共振端口字段或包名与契约不一致，且没有提供方/PM确认；
- 需要修改 core/**、entity/**、mechanism/**、app/** 或开发一任务卡；
- 需要反射发现未知机关，或复制 TickContext、EchoQueue、独立计时器；
- 恢复顺序未裁决，或测试只能证明部分对象恢复；
- 暂存区出现任务之外文件，或测试失败。

## 9. 接手人回传模板

~~~
任务：R5-B / P-MOVE-ALIGN
当前分支与 HEAD：
依赖核对：MovementState.IDLE；Dev3 resonance commit/PR：
实际修改文件：
禁止路径审计：
聚合接口与字段：
ROUND_END / FULL_RESTART 顺序：
测试命令与实际结果：
git diff --check：
git diff --cached --name-only：
提交 SHA / PR URL（base=develop）：
未决问题与停止条件：
~~~

**一句话交接**：先让开发一把 IDLE、开发三端口带入同一基线，再由开发二做 typed、不可变、事务前置校验的 R5-B 聚合及 IDLE 逐 tick 回放测试；此前不要越权改移动或机关实现。
