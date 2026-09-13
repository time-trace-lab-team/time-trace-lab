# 任务卡 · 四方向受约束移动 补丁（开发一）【P0 · 待 PM 发卡】

> 任务 ID：**C-PLAYER-MOVE-02**
> 所属板块：**开发一**（`core/`、`entity/` + 对应测试）
> 状态：**已发卡，可开工**（依赖 C-PLAYER-MOVE-01 已推送的 `feature/player-move-01` / `22e8f8e`）
> 依据：`README.md` §三「四方向受约束移动与驻留」、`四方向受约束自由移动-任务卡-开发1.md`
> 日期：2026-09-10
> 提出人：PM（集成适配 C-PLAYER-MOVE-01 时实测）
> PM 已裁决：**选项 A（真死路掉头豁免）**；README 补充裁决记录已随 `develop` 提交 `b44de66`
> 集成适配分支：`codex/player-move-app-adapt` / `6a11772`（app 侧签名适配，252 条测试全绿）

---

## 〇、背景

C-PLAYER-MOVE-01 的行为（无输入静止、按住走、松开停、不自动寻路、遇阻停下）在 `entity` 单测中全部通过；
PM 在 `app/Level01Assembly` 完成签名适配后跑全量测试：**252 条全绿**，但集成层新增的 7 条测试暴露出
**两条会锁死玩家的 P0 行为**，按其现状在第一关**无法通关**。

复现命令：`.\mvnw.cmd -o test -Dtest=Level01AssemblyMovementTest`
证据测试：`src/test/java/org/example/timeloop/app/Level01AssemblyMovementTest.java`
（`softLockAtBlockedDoorNodeCannotBeLeft`、`softLockWhenLeavingDockRequiresReversal`）

---

## 一、P0-A：真死路没有掉头豁免 → 角色被永久锁死

### 实测现象

| 场景 | 结果 |
| --- | --- |
| 按住 `DOWN` 从出生点 (5,1) 直行 200 刻 | 停在 (5,4) 节点中心（前方是**关闭的门**节点 (5,5)），`IDLE`，位置正确 ✅ |
| 在 (5,4) 按 `UP` | 被 `opposite(direction)` 判定为“原地掉头”拒绝 → **位置永不再变** ❌ |
| 在 (5,4) 继续按 `DOWN` | 门未开 → `IDLE`，位置不变 ❌ |

节点 (5,4) 只有 `UP`/`DOWN` 两个出口：`DOWN` 被关闭门挡住、`UP` 是掉头 —— **任何输入都无法离开**。

### 连锁后果（同一根因，第二个场景）

到达左侧驻留板节点 (2,5)（该节点唯一出口是 `UP`）时角色朝 `DOWN`：

1. autoDock 正常进入 `DOCKED`，`L01_plate_left` 占用登记成功 ✅；
2. 按 `UP` → C3 判定 `UP` 是**合法离开方向**，返回 `departureDirection = UP` ✅；
3. `PatrolController.advance(tick, Optional.of(UP), …)` 把它当成掉头**拒绝**，位置不动 ❌；
4. 位置不出 region → `tryLeave` 一直 `NOT_OUTSIDE_REGION` → 占用永不释放 ❌。

玩家被永久钉在驻留板上，两块板都需要占用才能开门 → **第一关在当前规则下无解**。

### 裁决建议（PM 倾向 = 选项 A）

**A. 真死路掉头豁免（推荐）**：允许请求方向等于反方向，**仅当**
`isPassable(当前节点, 当前朝向) == false` **且**该节点没有任何可通行的 90° 方向。
语义与 C2 原 `PathExitSelector.select()` 的 “reversal is reserved for a genuine dead end” 一致：
开放路段仍然禁止原地掉头，只有真死路（含被关闭门挡住的“当前死路”）允许原路返回。

**B. 关卡数据兜底**：要求所有可达节点至少有一个非反向出口。
不推荐：驻留板天生在走廊末端，等于要求所有 key 机关节点都不能是走廊尽头，且关门瞬间仍会产生死路。

### 验收（P0-A）

1. 新增单测：真死路请求反方向 → 允许掉头并按 `baseSpeed` 移动；
2. 新增单测：开放路段请求反方向 → 仍被拒绝（`IDLE`，位置不变）；
3. 新增单测：前方被**关闭门**挡住且无 90° 出口 → 允许掉头；
4. `Level01AssemblyMovementTest` 两条 `softLock*` 测试改为断言“可离开”，并保留
   “离开后驻留板占用释放”的断言；
5. 集成验证：第一关可以完成一轮 —— 经分叉 → 左板驻留 → 离开 → 右板驻留 → 门开 → 出口终端。

---

## 二、P0-B：输入契约表达不了“按住前进键 + 改按垂直方向”

### 实测现象

`advance(tick, Optional<Direction>, passability)` 只能收**一个**方向，集成层必须自行降维，于是：

| 玩家动作 | 现状 |
| --- | --- |
| 按住 `DOWN` 行进中再按住 `RIGHT` | 段中间请求 90° → 停下；两个键都按住期间**一直不动**（集成层无法同时表达“继续沿 DOWN 走 + 到节点右转”） |
| 松开 `RIGHT` 后 | 才继续沿 `DOWN` 前进，但**已经错过了那个路口** |

后果：目标是“改按立即改向”（README §三），实际体验是“按住两个键就冻住 / 路口被走过”；
这正是本卡要消除的「按键延迟 / 不按预期转向」类问题的残留形态。§十六 原型门禁第 2 条会判不通过。

### 裁决建议

把**原始输入语义**交给 `PatrolController`，由它结合当前段位置决定，集成层不再降维：

```java
PlayerKinematics advance(long tick,
                         Set<Direction> heldDirections,   // 本刻刻末仍按住的方向
                         Optional<Direction> newestEdge,  // 本刻最后新按下的方向（无则空）
                         ExitPassability passability);
```

建议解析规则（可在实现中微调，但需写测试固定）：

1. `heldDirections` 包含当前朝向 → 沿当前朝向推进（**保证不会因垂直方向按住而冻住**）；
2. 到达节点中心时：`newestEdge`（仍按住且合法）→ 提交转向；否则当前朝向合法 → 直行；
3. 两者都不成立（含只按住垂直方向、或真死路）→ `IDLE`；真死路按 P0-A 豁免可掉头；
4. 同刻相反方向同时按住 → `IDLE`（PM 已裁决，保持）。

> 若判定该改动超出本补丁范围，可拆成 **C-PLAYER-MOVE-03**；但 P0-A 必须随本补丁先落地。

---

## 三、P1：README 措辞对齐（PM 自行修订，不需开发一动手）

README §三/§十六 现有措辞“方向输入**同刻生效**，不需要等到路口”“改按另一个方向立即改向”，
与「受路径中心线约束、只能在节点中心提交转向」的选项 2 模型不一致（段中间按 90° 在几何上不可能成立）。
PM 将把措辞改为：**输入同刻生效、无排队；转向在节点中心提交，段中间按垂直方向不转向**，
并同步 §十六 门禁第 2 条的表述。

---

## 四、允许 / 禁止修改路径

- **允许**：`src/main/java/org/example/timeloop/core/path/**`、`core/MovementState.java`、`entity/**`；
  对应 `src/test/java/.../{core,entity}/**`；`docs/development/开发1/**`。
- **禁止**：`pom.xml`、`app/**`、`replay/**`、`snapshot/**`、`mechanism/**`、`level/**`、`ui/**`、
  `persistence/**`、`audio/**`、`src/main/resources/**`。
  （`app/Level01Assembly` 的签名适配由 PM 负责，见分支 `codex/player-move-app-adapt` / `6a11772`。）

## 五、交付

1. 新分支（建议 `feature/player-move-02`），基于 `feature/player-move-01`；
2. 单元测试 + PM 集成测试全绿；
3. 交接说明：新的 `advance` 签名与解析规则，并在 PR 描述里给出「第一关可通关」的集成证据。

---

## 六、PM 集成验证回填（2026-09-10，`feature/player-move-02` / `01d98da` 已实测）

分支：`codex/player-move-02-app-adapt`（`e17bf4a` 热修 + `cd9aede` app 适配），全量 **261 条通过**，其中包含
`app/Level01AssemblyMovementTest`（9 条）与开发一的 19 条 entity 测试。**在补上热修前，下列 entity 测试挂死。**

### 已修好、复验通过的

| 项 | 证据 |
| --- | --- |
| P0-A 真死路掉头（含关闭门挡住） | `Level01AssemblyMovementTest.closedDoorDeadEndAllowsReversal` 通过：在 (5,4) 面朝 DOWN 松键改按 UP → 原路返回 |
| P0-B 多键按住不冻住 | `holdingForwardAndSideKeepsMovingForward` 通过：按住 DOWN+RIGHT 仍以 baseSpeed 沿 DOWN 推进 |
| 驻留板可离开 | `dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy` 通过：走到机关中心停驻 → 按 UP 离开 → 占用释放 |

### P0-E（阻塞级，必须修）：`advance()` 死循环

**现象**：站在“段终点节点中心”且直行合法时，`decideDirection` 返回 `nextDir == direction`，
旧代码不推进 `segment`（`if (nextDir != direction)` 才更新），于是
`destination` 仍是脚下节点 → `dist == 0` → `budget -= 0` → `while (budget > 0)` **死循环**。
任何「按住方向键直行穿过节点」都会挂死。

**实测证据**：`PatrolControllerDeadEndTest#junctionWithSideExit_stillRejectsReversal` 单独运行时
`mvn -o test -Dtest=...` **超过 100 秒无输出**（正常 <1s），必须强杀。开发一自己的图里
`jct` 有 UP/DOWN/RIGHT，第 3 刻正落在 jct 中心直行 DOWN，命中该分支。

**PM 临时热修（`e17bf4a`，请在 `feature/player-move-02` 用正式修复替换）**：

```java
if (nextDir != direction || center.id().equals(segmentEndNodeId)) {
    direction = nextDir;
    segmentStartNodeId = center.id();
    segmentEndNodeId = graph.neighbor(center, nextDir).get().id();
}
```

**验收**：新增单测「直行穿过节点后继续推进」+ 上面那条 entity 测试必须在 1 秒内结束；
「按住方向连续穿过 ≥3 个节点直行」不得挂死。

### P0-D（阻塞级，必须修）：真死路豁免缺少两个前置判定

`decideDirection` 第 3 条只检查「没有可通行的 90° 出口」，缺两项判定，造成 1 个崩溃 + 1 个规则偏差：

1. **崩溃**：`held` 含反方向就直接返回反方向，但**没检查反方向在该节点是否有出口**。
   出生点只有 `DOWN` 出口，开局按 `UP` → `graph.neighbor(center, UP).get()` 抛
   `NoSuchElementException`（`Level01AssemblyMovementTest.currentBehaviourReverseAtSpawnThrows` 已固化）。
2. **规则偏差**：`isPassable(当前节点, 当前朝向) == false` 这个前置条件没写进代码。
   普通直廊节点 (5,2)（只有 UP/DOWN，前方 DOWN 仍可通行）按 `UP` 也能掉头，
   与 README「仅真死路可掉头」不符（`currentBehaviourReversalAllowedAtPlainCorridorNode` 已固化）。

**建议修复**（与 §一 P0-A 的裁决文字一致）：

```java
if (held.contains(DirectionGeometry.opposite(direction))
        && !PathExitSelector.isPassable(graph, center, direction, passability)   // 前方不可通行
        && PathExitSelector.isPassable(graph, center,
                DirectionGeometry.opposite(direction), passability)             // 反方向确有出口
        && !hasAny90Exit(center, passability)) {
    return DirectionGeometry.opposite(direction);
}
```

修复后请把上面两条 `currentBehaviour*` 测试改为断言「不崩且静止 / 普通直廊节点不允许掉头」。

### P1（建议）：单槽方向意图的归属

转向只能在节点中心提交，而一格 24 刻，若 `newestEdge` 只在本刻有效，玩家几乎没有转向窗口。
开发一的测试 `PatrolControllerHeldDirectionTest#holdingDirectionAndSide_thenEdgeCommittedAtNode`
在**连续两刻都传 `Optional.of(RIGHT)`**，说明设计意图就是「意图保留到节点」。
PM 已在 `app/Level01Assembly.pendingTurn` 里实现这一保留（按住期间转交、松手或提交后作废），
但**移动语义属于 entity**，建议后续把该单槽状态移进 `PatrolController`（签名不变），
`app/` 只做键位到 `Set<Direction>` 的转换；README 里「没有单槽排队」一句也会同步改成
「不排队、但按住的方向意图保留到下一个可提交的节点中心」。

---

## 七、复验结果（`5f633e0`，2026-09-10 PM 实测 — 已闭合）

分支 `codex/player-move-02-app-adapt`（重建到 `5f633e0`，PM 临时热修 `e17bf4a` 已按约定丢弃）：
**Tests run 265, Failures 0, Errors 0, BUILD SUCCESS**，全部 entity 测试类均 ≤ 0.012s（无挂死）。

| 项 | 证据（`5f633e0`） |
| --- | --- |
| **P0-E 死循环** | `PatrolControllerDeadEndTest` 8/8 通过（0.012s）；`straightThroughNode_continuesMoving` 已改用 `straightGraph()`，`y=20 CRUISING` 成立；`straightThroughManyNodes_doesNotHang` 通过 |
| **P0-D 崩溃** | `app.Level01AssemblyMovementTest.reverseAtSpawnStaysIdleWithoutCrash` 通过：出生点按 `UP` 不崩、`IDLE`、朝向仍 `DOWN` |
| **P0-D 规则偏差** | `reversalRejectedAtPlainCorridorNode` 通过：普通直廊节点 (5,2) 掉头被拒 |
| **P0-A 未被误伤** | `closedDoorDeadEndAllowsReversal`、`dockingSnapsToMechanismCenterAndLeavingReleasesOccupancy` 通过 |
| **P0-B/P0-C** | `holdingForwardAndSideKeepsMovingForward`、`heldSideIntentTurnsAtTheNextJunction` 通过 |
| **javadoc 回退** | `advance` 的四个 `@param`（含「`newestEdge` 仅在节点中心用于提交转向，段中间忽略」）与 `position/direction/config` 说明已补回，ENT-1 的 javadoc 要求满足 |

**验收闭合**：P0-A/P0-B/P0-C/P0-D/P0-E 全部有正断言测试；合并门禁满足，可进入合并流程
（`feature/player-move-02` → `codex/player-move-02-app-adapt` → `develop`）。
**遗留（不阻塞本卡）**：单槽方向意图仍在 `app/Level01Assembly.pendingTurn`，按 ENT-1 在
`X-MOVE-COLLAPSE-01` 中移进 `PatrolController`；`feature/player-move-01` 及旧适配分支
`codex/player-move-app-adapt` 已废弃，不得合并。

---

## 八、历史记录（`b3ffd44` 复验，2026-09-10）

第一次复验时全量 **265 条、1 条失败**：`PatrolControllerDeadEndTest.straightThroughNode_continuesMoving`
断言写错（`corridorGraph()` 间距 5，10 刻 × 2 = 20 单位超过走廊长度 10 → 实得 `y=10`、断言 `20.0`），
且 `b3ffd44` 误删了 `advance(...)` 的 `@param` javadoc。两项均已在 `5f633e0` 处理完毕。

已确认修复 ✅（当时证据）：

| 项 | 证据 |
| --- | --- |
| **P0-E 死循环** | `PatrolControllerDeadEndTest` 整类 **0.051 秒**结束（修复前 >100 秒挂死）；`straightThroughManyNodes_doesNotHang` 通过 |
| **P0-D 崩溃** | 出生点按 `UP` 不崩、`IDLE`、朝向仍 `DOWN` |
| **P0-D 规则偏差** | 普通直廊节点 (5,2) 掉头被拒 |
| **P0-A 未被误伤** | 门前死路可掉头；驻留板可离开并释放占用 |


