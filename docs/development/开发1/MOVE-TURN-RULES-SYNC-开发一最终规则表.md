# MOVE-TURN-RULES-SYNC · 开发一最终移动规则表

> 用途：供 PM 同步 `README.md` 的玩家操作说明。本表只记录当前现场实现与测试证据；开发一不修改 README。
>
> 现场基线：`origin/develop @ 418d0ca`；开发一分支：`codex/expand-map-view-dev1-v4`。
> 方法行号以本表生成时的工作区为准，后续提交发生行号变化时应重新核对。

## 规则与证据

| # | 最终规则 | 当前实现位置 | 最低测试证据 | 游戏内表现 | README 待 PM 同步点 |
| --- | --- | --- | --- | --- | --- |
| 1 | 本 tick 最后发生、且仍按住的非当前方向边沿进入单槽 `pendingDirection`；它在节点中心优先于直行。 | `PatrolController.refreshPendingDirection`（209–219）；`decideDirection`（181–206）。 | `PatrolControllerHeldDirectionTest.earlySideEdgeIsRetainedUntilTheNodeCenter`；`newestOppositeEdgeReversesTheCurrentSegmentImmediately`。 | 玩家最后新按的方向有确定优先级，不依赖集合遍历顺序。 | README 77、90 只描述“仍按住当前方向”时的段中间行为；应补充最新边沿的单槽语义。 |
| 2 | 段中间松开当前方向、但持续按住可在下一节点通行的已缓存 90° 方向时，角色继续沿旧中心线到节点。 | `PatrolController.advance`（123–142）；`hasUpcomingTurn`（326–332）。 | `PatrolControllerHeldDirectionTest.releasingForwardWhileHoldingLegalSideContinuesToNodeAndUsesRemainingBudgetAfterTurning`；只读集成证据 `Level01AssemblyMovementTest.releasingForwardThenHoldingSideTurnsAtTheNextJunction`。 | 可提前按转弯键并松开旧方向；不会段中间斜切、卡住或掉线。 | README 77、90 的“当前方向仍按住时继续”需改为包含本规则，避免误导玩家持续按住旧方向。 |
| 3 | 90° 转向仅在节点中心提交；距离预算循环在到中心后立即按新方向消费同 tick 剩余预算。 | `PatrolController.advance`（106–142）；`decideDirection`（181–206）。 | `PatrolControllerHeldDirectionTest.earlySideEdgeIsRetainedUntilTheNodeCenter`；`releasingForwardWhileHoldingLegalSideContinuesToNodeAndUsesRemainingBudgetAfterTurning`。 | 角色沿正交中心线顺滑拐弯，不斜切，也不会在路口损失一 tick 的移动距离。 | README 77、90 的“节点中心提交”保留；补充“同 tick 剩余预算继续消费”。 |
| 4 | 段中间新按反方向且反向边通行时，立即交换当前有向段并在同 tick 原路掉头。 | `PatrolController.reverseCurrentSegmentImmediately`（227–250）；调用点 `advance`（88–90）。 | `PatrolControllerHeldDirectionTest.newestOppositeEdgeReversesTheCurrentSegmentImmediately`。 | 玩家在走廊中按反方向会立即返回，不必先走到端点。 | README 73、78–79、91 仅允许真死路原路返回，与当前 BUG-001 裁决冲突；PM 必须改为区分“新反向边沿的主动掉头”和无输入自动掉头。 |
| 5 | 静止且靠近节点时，新的合法 90° 边沿可在固定半径内吸附到最近可通行节点；范围外、移动中或阻挡出口不得吸附。 | `PatrolController.snapStationaryTurnToNearbyNode`（262–289）；`nearerPassableNodeWithinTurnSnapDistance`（291–318）。 | `idleNearJunction_newSideEdgeSnapsAndTurnsInTheSameTick`、`idleBeyondSnapDistance_newSideEdgeDoesNotPullBackToJunction`、`idleExactlyAtSnapDistance_isInsideTheClosedBoundary`、`movingNearJunction_doesNotUseStationaryPullBack`、`idleNearJunction_blockedSideExitDoesNotSnapThroughObstacle`。 | 玩家在路口附近停住后可转向，不需要像素级对齐；不会穿过关闭门。 | README 100 的“自动吸附中心线”需明确为仅静止、仅新按合法 90°、仅有限半径的输入辅助。 |
| 6 | 同时按住当前方向与其反方向、且本 tick 没有新方向边沿时，保持 `IDLE`，不猜测输入集合顺序。 | `PatrolController.advance`（92–98）。 | `PatrolControllerHeldDirectionTest.holdingOppositeDirectionsWithoutANewEdge_staysIdle`。 | 玩家不会因同时按相反键而随机向任一方向移动。 | README 77、90 应补充相反方向无新边沿时静止的确定性规则。 |
| 7 | 第一轮在首个方向输入前不推进共享计时或录制；第一次有效方向输入后才开始本轮。 | `Level01Assembly.tick`（237–246）；该路径归 PM/app，只读。 | `Level01AssemblyMovementTest.countdownWaitsForTheFirstMovementKey`（只读集成证据）。 | 进入第一关后可先观察地图和轨迹；第一次移动才开始倒计时。 | README 的 READY/读秒说明应补充“首个方向输入启动”，并由 PM 修改。 |

## 开发一边界与交接

- 本表只说明开发一的 `PatrolController` 行为和测试证据；`Level01Assembly` 的首键计时属于 PM/app，只读列出。
- 本表不授权修改 `README.md`、`app/**`、`level/**`、`mechanism/**`、`replay/**`、`snapshot/**`、`ui/**` 或 `pom.xml`。
- README 同步后，PM 应在配对分支执行：段中间提前侧向、段中间掉头、静止路口吸附、首键启动计时的完整人工回归。
