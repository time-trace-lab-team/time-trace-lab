# 任务卡 · 残影回放的 actor 归属（自由移动收口配套）

> 任务 ID：**X-MOVE-COLLAPSE-01-ECHO-ACTOR**
> 主责：**PM（`app/**`，E-1）** ｜ 配合：**开发三（E-2，机制约定确认 + 补测）** ＋ **开发二（E-3，R-2 契约注记）**
> 状态：**已结案（2026-09-13）** —— E-1/E-2/E-3/E-4 全部合入 develop，淘汰派发已接通并有契约测试；
> 残留一条见 §五（`restartFromFirstRound` 清空残影时的占用释放，L≥2 关卡前处理）
> 触发：开发三 L-1 确认请求第 6 项（“回放沿用录制时的 `player`/`0`，与规格冻结 §2.1 不符”）
> 日期：2026-09-10

## 结案记录（2026-09-13）

| 环节 | 交付 | 状态 |
| --- | --- | --- |
| E-1 回放 actor 改写为 `echo_<sourceRound>` | PR #57（`788820c`） | ✅ 合入 |
| E-2 机制侧确认 + 释放补测 | PR #58（`8cc8f08`） | ✅ 合入 |
| E-3 replay 契约注记 | `fe1f0d0`（PR #62 链路） | ✅ 合入 |
| E-4 淘汰观察数据面（`addOnRoundEnd` 返回淘汰列表） | PR #62 | ✅ 合入 |
| **淘汰派发接通（app 侧 diff + 事件契约测试）** | **PR #68（`d7189ab`）** | ✅ 合入（`29aa416` 合并 sha）；合并结果树本地 clean **362/0/0** |
| `sourceRound` 语义结论（开发二 B 部分） | 一页结论，**PM 采纳：维持二值语义、不发卡** | ✅ 结案（见开发二 v4 卡 §八） |


## 一、这不是“规格不符”的文书问题，是**真 bug**

机制侧早就冻结了 actor 约定，`AutoDockService.requireActor(...)`（`AutoDockService.java:336-357`）强制：

```java
if ("player".equals(actorId)) return;                       // 活玩家
if (!actorId.matches("echo_[1-9][0-9]*")) throw ...;         // 残影必须是 echo_<round>
if (Integer.parseInt(actorId.substring(5)) != sourceRound) throw ...;  // 且与 sourceRound 一致
```

而 `DockingPlate.onEvent(...)`（`DockingPlate.java:95-99`）**只在**占用者等于 `"echo_" + sourceRound` 时才在残影消失时释放：

```java
String echoId = "echo_" + sourceRound;
if (state == State.OCCUPIED && occupantId != null && occupantId.equals(echoId)) {
    tryExit(echoId, sourceRound, event.tick());
}
```

当前 `app/Level01Assembly.replayEchoEvents(...)` 用**录制时的 actorId**（恒为 `"player"`）调用 `plate.tryEnter(...)`，于是：

1. 残影在驻留板上留下的占用，`ECHO_DISAPPEARED` 时**永远不会被释放** → 驻留板**永久占用**（第一关的门会被永久顶开，或把该板对后续轮次锁死）；
2. 残影占用与活玩家占用在 `DockingPlateRegistry` 里无法区分（同一个 `(player, 0)`）；
3. 与机制层已冻结的 `echo_<round>` 约定直接冲突（虽然 `requireActor` 对 `"player"` 放行，所以现在不会抛异常，问题被静默吞掉）。

## 二、任务

### E-1（PM，`app/Level01Assembly.replayEchoEvents`）

回放残影事件时改写 actor 归属：

```java
String echoActorId = "echo_" + echo.sourceRound();   // 与 AutoDockService.requireActor / DockingPlate.onEvent 约定一致
plate.tryEnter(echoActorId, echo.sourceRound(), event.tick());
// 退出同理：plate.tryExit(echoActorId, echo.sourceRound(), event.tick());
```

要求：只改“回放时写入机关的 actor 归属”，**不改** `TimelineEvent` 里记录的内容（录制仍是 `player`），也不改 `replay/**` 的类型。

### E-2（开发三，确认 + 补测）

1. 确认 `echo_<sourceRound>`（1 起算）是机制层**冻结**约定，且 `DockingPlate.onEvent(ECHO_DISAPPEARED)` 的释放路径就是靠它；
2. 补一条机制侧测试：`tryEnter("echo_1", 1, t)` → 派发 `ECHO_DISAPPEARED(sourceRound=1)` → 板释放；
3. 若 `ECOH_DISAPPEARED` 的 `sourceRound` 字段与 actor 名不一致时应报错（防呆），在文档里写明。

### E-3（开发二，R-2 契约注记）

在 `EchoState.eventsAt` 的契约里写明：**回放时的 actor 归属由调用方改写为 `echo_<sourceRound>`**（`replay/**` 不负责改写，也不应把 `player` 当作残影）。
另：活玩家事件的 `sourceRound` 目前恒为 `0`（`Level01Assembly.PLAYER_SOURCE_ROUND`）。有了 E-1 之后，占用侧已不会冲突（`player` ≠ `echo_N`），故**该常量可暂不改**；若开发二认为回放语义需要“事件带当轮轮次”，另开小卡。

## 三、验收

1. **app 集成测试（PM）**：第 1 轮把玩家送到左驻留板停驻 → 第 2 轮残影占住左板（已有 `Level01AssemblyRoundLoopTest.echoOccupiesLeftPlateInSecondRound`）→ 让第 1 轮残影消失（`ECHO_LIFE_L=1`，推进到第 3 轮）→ 断言左板**已被释放**（`isOccupied == false`）。这条现在必然失败，E-1 落地后转绿；
2. 残影占用期间活玩家去占**同一**块板时按机制既有语义处理（不得互相顶掉后留下幽灵占用）；
3. 全量 `.\mvnw.cmd -o test` 退出码 0，单测任何一条 ≤ 1 秒；
4. `git grep -n "\"player\"" src/main/java/org/example/timeloop/app` 中不再出现“回放残影时传 player”的调用点。

## 四、边界

- PM：`app/**`；开发三：`mechanism/**`（仅确认与补测）；开发二：`replay/**`（仅契约注记与测试）。
- 禁止任何一方用“第二套 actor 命名”或“在 replay 里硬编码 `player`”绕过本卡。
