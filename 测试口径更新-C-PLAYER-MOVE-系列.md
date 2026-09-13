# 测试口径更新通知 · C-PLAYER-MOVE 系列（致 测试 / 开发一）

> 发出：PM ｜ 日期：2026-09-10 ｜ 替代对象：`feature/player-move-01` `22e8f8e` 的测试报告
> 新被测对象：`feature/player-move-02` `b3ffd44` + PM 适配分支 `codex/player-move-02-app-adapt` `f20f1ff`

## 一、`22e8f8e` 的测试报告作废（结论事实正确，但对象已过期）

`feature/player-move-01` 的《测试报告》按当时提交读取的事实都对：

- 分支本体编译失败，根因 `app/Level01Assembly.java` 仍按**旧 2 参** `advance(tick, passability())` 调用 —— 属实；
- `PatrolController.advance(long tick, Optional<Direction> requestedDirection, ExitPassability passability)` —— 属实；
- 「需与 PM 的 app 适配一起合并」——当时正确。

但该分支**已被 C-PLAYER-MOVE-02 取代，不要再合**。原因：

1. `feature/player-move-02` `01d98da` 把签名再改一次为
   `advance(long tick, Set<Direction> heldDirections, Optional<Direction> newestEdge, ExitPassability passability)`；
   `feature/player-move-01` 的 3 参签名**已不存在**；
2. `feature/player-move-01` + 当时的 app 适配 `6a11772` 会把**两个阻塞缺陷**带进 develop
   （P0-D 出生点按反方向抛 `NoSuchElementException`；P0-E 直行穿节点死循环挂死），
   两者已在 `b3ffd44` 修掉；
3. 旧适配分支 `codex/player-move-app-adapt`（`6a11772` / merge `6220307`）已作废，
   **请勿合并**；替代分支是 `codex/player-move-02-app-adapt`（`f20f1ff`）。

## 二、报告里两处口径需要改（否则会把正确行为判成缺陷）

| 报告原文 | 更正 |
| --- | --- |
| 「反方向（180°）主动掉头被拒绝（返回 IDLE），符合 README『拐角≠死路，转弯 90°』规则」 | README 已按 `b44de66` 修订并新增**补充裁决记录**：普通路段仍禁止掉头，但**真死路**（当前朝向被内墙/边界墙/关闭门挡住，且该节点没有任何可通行 90° 方向）**允许原路返回**。若测试仍按“任何情况都不许掉头”写用例，会把正确行为判成缺陷；反之，若漏测真死路，会漏掉“玩家被永久锁死”的回归 |
| 「`codex/player-move-app-adapt` 已实测 252 测试全绿」 | 数字与分支都已过期：当前为 **265 条**（move-02 新增 19 条 entity 测试 + PM 新增 9 条 `app/Level01AssemblyMovementTest`），且最近一次全量**有 1 条红灯**，详情见 §四 |

## 三、新验收清单（回归用）

**输入与移动**
1. 无输入 → `IDLE`，位置与朝向不变；
2. 按住方向 → `baseSpeed = 2` 世界单位/刻；松开 → **同刻**停止；
3. 同刻按住相反方向（上+下 / 左+右）→ `IDLE`（不取末位胜出）；
4. 按住前进键 + 垂直方向 → **不冻住**，沿当前朝向继续推进；
5. 段中间按垂直方向 → 停在段中间、**不转向**；只有到达节点中心才提交转向；
6. 按住垂直方向走过节点 → 在**下一个可转向的节点中心**提交 90° 转向，且不需要刻级精确按键。

**死路与掉头**
7. 真死路（关闭门挡住的前方 / 驻留板这类单出口节点）→ 允许原路返回；
8. 普通直廊节点（前方仍可通行）→ 掉头**被拒**，保持 `IDLE`；
9. **出生点按反方向不得抛 `NoSuchElementException`**（P0-D 回归，必须有用例）。

**挂死与边界**
10. 按住方向连续直行穿过 ≥3 个节点 → 不挂死，且位移 = 刻数 × 2（P0-E 回归）；
    整套单测**任何一条都不得超过 1 秒**，超过即视为死循环回归。

**驻留机关**
11. 进入 autoDock 区域后，角色沿中心线走到**机关所在路径节点中心**才停驻（区域是边长 `tileSize` 的方格，边界处不能冻结）；
12. 离开驻留板：按合法出口即出发，占用随之释放（`tryLeave` 的最终语义以 R5 裁决 4 落地版为准），**不得出现“占用永不释放”**。

**残影与确定性**
13. 同一输入序列录制 → 下一轮残影逐 tick 位置/停驻长度一致；`IDLE` 段仍每刻写入 `PlayerFrame`（无空档）。

## 四、当前已知红灯（不属测试责任，等开发一修）

`PatrolControllerDeadEndTest.straightThroughNode_continuesMoving`：用例注释是 `start(0,0) → mid(0,5) → end(0,10)`，
`corridorGraph()` 间距确实是 5，而 10 刻 × 2 = 20 单位超过走廊长度，角色停在 `end` 中心 → 实得 `y=10`、断言 `20.0`。
**断言写错，不是实现问题**（该用例 `@Timeout(1s)` 0.005 秒返回，证明没挂死）。
修法：改用 `straightGraph()`（间距 10）或把断言改为「停在 `end` 中心 `y=10` 且 `IDLE`」。

## 五、合并门禁（更新版）

1. 被测对象：`feature/player-move-02` `b3ffd44`；
2. `feature/player-move-01` 及其旧适配分支 **明确标记为废弃**（PR 关闭/不再合并）；
3. 全量 `.\mvnw.cmd -o test` 退出码 0，且**无单测超过 1 秒**；
4. 测试须覆盖 §三 的 13 条（其中 4、6、7、9、10、12 是本次新增重点）；
5. app 侧适配由 PM 提供（`codex/player-move-02-app-adapt`），测试不需要改 `app/**`。
