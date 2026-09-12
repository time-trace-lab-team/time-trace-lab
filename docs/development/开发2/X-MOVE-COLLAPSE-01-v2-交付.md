# X-MOVE-COLLAPSE-01-DEV2-v2 交付说明

> 任务卡：自由移动收口 v2（X-MOVE-COLLAPSE-01-DEV2-v2）
> 日期：2026-09-12
> 分支：`codex/dev2-x-move-collapse`（`0d28063` + 后续 E-3 契约提交）
> 基线：`origin/develop` = `202e7ad`（已同步），分支 diff 只含本卡提交

---

## 0. 结论速览

| 子项 | 结果 |
|---|---|
| A 交付并入 | 分支已 push，PR 待开/待确认标题（见 §1） |
| B E-3 actor 归属契约 | ✅ 已完成（javadoc + 测试，见 §2） |
| C sourceRound 语义 | ✅ 已给结论：**不需单独发卡**（见 §3） |

## 1. A 交付并入

- 分支 `codex/dev2-x-move-collapse` 的父提交正是 `origin/develop`（`202e7ad`），
  **PR diff 只含本卡提交**（此前「落后 98 提交」的问题已随 PM 同步 develop 消除）。
- PR 标题（v2 指定）：`X-MOVE-COLLAPSE-01 R1-R4 录制/回放与轮边界`。
- PR 描述要点：`replay/**` 行为改动只有 javadoc（`RecordingSession`/`RoundClock` 的
  轮末停靠契约 + `EchoState.eventsAt` 的 actor 归属契约），其余为新增测试。

## 2. B E-3 残影回放 actor 归属契约（已完成）

**背景**：`AutoDockService.requireActor` 只接受 `player` 或 `echo_<N>`（且 N == sourceRound），
`DockingPlate.onEvent(ECHO_DISAPPEARED)` 只在占用者等于 `"echo_" + sourceRound` 时才释放。
app 回放若直接传录制时的 `"player"`，会导致残影消失后驻留板永久占用。

**契约（只改注记，不改写行为）**：

- javadoc：`EchoState.eventsAt(long)` 新增「actor 归属契约」段 ——
  replay 层不负责改写 `actorId`、不把 `"player"` 当残影，由调用方（app）写入机关前
  改写为 `echo_<sourceRound>`。
- 测试：`EventRoundTripTest.eventsAt_doesNotRewriteActorId_callerMustRewriteToEchoSourceRound`
  —— 断言 `eventsAt` 取出的事件 `actorId` 仍是录制值 `"player"`，且调用方改写后
  `echo_<N>` 的 N 与 `sourceRound` 一致。

## 3. C 事件 sourceRound 语义（结论：不需单独发卡）

**结论：维持现状（活玩家事件 `sourceRound=0`），不单独发卡。**

理由：

1. B 的 actor 改写已消除占用冲突：残影事件 `actorId` 改写为 `echo_<N>`（N=sourceRound），
   活玩家保持 `"player"`，两者身份天然区分，占用侧不再互相顶掉。
2. 事件 `sourceRound` 目前只描述「活玩家」这一身份，残影回放靠 `echo_<N>` 区分，
   不依赖事件的 `sourceRound` 字段。
3. 若改为「当轮轮次」，需动 `app/entity`（`C3DockController.sourceRound` 是 final 构造固定），
   改动面大、对既有残影数据无收益。

## 4. 测试数字

- `.\mvnw.cmd -o test` = **295 全绿、0 失败、0 错误、0 跳过**。
- 最慢测试类 `MovementScriptFidelityTest` 0.067s，所有单测 < 0.1s。

## 5. B 契约条目落点

- javadoc：`src/main/java/org/example/timeloop/replay/EchoState.java` → `eventsAt(long)`。
- 测试：`src/test/java/org/example/timeloop/replay/EventRoundTripTest.java` →
  `eventsAt_doesNotRewriteActorId_callerMustRewriteToEchoSourceRound`。
