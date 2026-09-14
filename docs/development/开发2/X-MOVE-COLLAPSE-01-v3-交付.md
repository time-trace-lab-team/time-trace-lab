# X-MOVE-COLLAPSE-01-DEV2-v3 交付说明

> 任务卡：自由移动收口 v3（X-MOVE-COLLAPSE-01-DEV2-v3）
> 日期：2026-09-12
> 基线：`origin/develop` = `b76c14c`（PR #50 已并入 R1-R4 + E-3）
> 结论：E-4 已交付，全量 320 测试全绿。

---

## 0. 结论速览

| 子项 | 结果 |
|---|---|
| A 开 PR（R1-R4 + E-3） | 已由 PM 完成：PR #50 已并入 develop（merge `0e211c8`） |
| E-4 淘汰观察契约 | ✅ 已交付（`addOnRoundEnd` 返回被淘汰残影列表 + 契约 + 测试） |
| C sourceRound 语义 | 维持 v2 结论（不需单独发卡） |

## 1. E-4 的 API 形态

选定方案：**`EchoQueue.addOnRoundEnd(...)` 返回值由 `void` 改为 `List<EchoState>`**，
返回「本次被淘汰的残影」列表（按来源轮次升序、只读、可能为空）。
既有调用方忽略返回值即可，源码兼容（`RecordingSession` 无需改动）。

```java
public List<EchoState> addOnRoundEnd(EchoState echo, int nextRound)
```

契约要点（已写入该方法 javadoc）：

- **淘汰刻 = 轮末边界刻**（刚结束那轮的最后一刻，与 `completeNormalRound` 的事务刻一致）；
- 列表同时覆盖**寿命淘汰**（超龄）与**容量淘汰**（第 3 个残影入队时最旧的被挤出；
  L=CAPACITY=2 时二者等价，都表现为最旧的 sourceRound 被移除）；
- `replay/**` 只暴露数据、不派发、不持有机关状态（不 import `mechanism/event`）。

另在 `EchoState.eventsAt(long)` javadoc 补「残影消失契约」段：
残影消失的机关释放语义由调用方（app）派发，replay 不持有机关状态。

## 2. 给 PM 的调用样例（app 轮末如何派发残影消失）

```java
// app 侧轮末事务里（completeNormalRound 之后）：
List<EchoState> evicted = echoQueue.addOnRoundEnd(newEcho, nextRound);
long boundaryTick = clock.roundTick();               // 轮末边界刻
for (EchoState gone : evicted) {
    EventDispatcher.getInstance().dispatch(
            GameEvent.echoDisappeared(
                    "echo_" + gone.sourceRound(),     // sourceId 与机关侧 echoId 一致
                    boundaryTick,
                    gone.sourceRound()));
}
```

说明：`DockingPlate.onEvent(ECHO_DISAPPEARED)` 内部以 `"echo_" + sourceRound` 匹配占用者，
故 `sourceId` 必须传 `"echo_" + gone.sourceRound()`，二者才对齐。

## 3. 契约落点

- 主契约：`src/main/java/org/example/timeloop/replay/EchoQueue.java` → `addOnRoundEnd`。
- 补充说明：`src/main/java/org/example/timeloop/replay/EchoState.java` → `eventsAt(long)`。
- 测试：`src/test/java/org/example/timeloop/replay/EchoQueueTest.java`
  → `addOnRoundEnd_returnsEvictedEchoes`（L=2 第 3 轮末淘汰 E1，且不再 activeEchoes）、
  `lifetimeOne_evictsPreviousOnRoundEnd`（L=1 淘汰上一轮残影）。

## 4. 测试数字

- `.\mvnw.cmd -o test` = **320 全绿、0 失败、0 错误、0 跳过**（含 PM 并入的 PR #51~#59 测试）。
- 最慢测试类 < 0.1s。

## 5. 交付与遗留

- 本卡改动待提交、推送并新开 PR（base=develop）；PR 标题建议
  `X-MOVE-COLLAPSE-01-v3 E-4 残影淘汰观察契约`。
- 依赖：app 侧需按 §2 接线，在轮末用淘汰列表派发 `GameEvent.echoDisappeared`
  （app 属禁止路径，开发二不代改）。
