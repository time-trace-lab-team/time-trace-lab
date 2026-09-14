# L01 门与终点合并 · 开发一 render 对接卡

> 任务：`L01-GATE-MERGE-DEV1` ｜ render 分支：`codex/l01-gate-merge-dev1`
>
> 本卡只定义开发一 render 交付与 PM 的 `app/**` 接线边界；不授权开发一修改 `app/**`。

## 1. 开发一提供的契约

`RenderViews.MechanismKind` 新增 `SWITCH`：它是 `dock_plate` 的表现变体（关卡数据
`role=switch`），不是新的机关类型。`RenderViews.Mechanism` 的记录结构保持不变：

```java
new RenderViews.Mechanism(id, x, y, kind, active)
```

- `kind = SWITCH`、`active = false`：未触发的弹起踏板；
- `kind = SWITCH`、`active = true`：本轮已锁存的压下踏板；该状态不表示当前是否有人站在上面；
- `kind = EXIT`、`active = false`：关闭的实体闸门；
- `kind = EXIT`、`active = true`：打开的门框与可通行状态，图层保留既有 `E` 提示；
- `kind = DOOR`：绘制分支保留给第二关起使用，但第一关不再投影该条目。

## 2. PM 唯一需要完成的 app 投影

待开发三将第一关的门与出口改到同一闸门节点、并交付开关的锁存状态后，PM 在
`Level01Assembly.renderViews()`（或其等价投影处）完成：

1. 左驻留板：保持 `PLATE`，`active = leftPlate.isOccupied()`；
2. 右驻留板：改投影为 `SWITCH`，`active = rightPlate` 的本轮锁存 ON 状态；
3. 第一关不再投影单独的 `DOOR`；
4. 终点：投影为 `EXIT`，`active = exit.isDoorUnlocked()`；
5. `Door` 和 `ExitTerminal` 的同坐标、物理阻挡、解锁来源、事件及快照契约均不由本卡修改。

禁止以“当前占用”替代右开关的锁存 ON；玩家/残影离开后开关仍应保持 ON 至轮末 reset。

## 3. 开发三须先回传的输入

- 新闸门节点的稳定 ID、世界坐标与满足几何约束的证据；
- 第一关关卡数据的 `role=switch`；
- 右驻留板锁存 ON 的只读查询来源；
- 门与出口已同坐标、以及轮末 / 重开 reset 的验证提交 SHA。

若缺少锁存 ON 的只读值，PM 不得猜测用 `isOccupied()` 代替；应停止并要求开发三补接口。

## 4. 配对验收

1. 第一关画面只有一个闸门终点，不出现远处的第二个 `DOOR` 图标；
2. 左驻留板与右开关一眼可区分；开关 OFF / ON、闸门关闭 / 打开四态均可辨认；
3. 开关触发后，离开该格仍显示 ON，轮末和整局重开后回到 OFF；
4. 闸门锁定时不可通过自身格；解锁后显示打开并可按 `E` 结算；
5. 配对分支执行全量自动化、`git diff --check` 与 JavaFX 人工验收后，才能向 `develop` 建最终 PR。
