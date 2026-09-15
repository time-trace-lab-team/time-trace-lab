# L01 门与终点合并 · 开发一交付给 PM

> 任务：`L01-GATE-MERGE-DEV1` ｜ 日期：2026-09-14
>
> **远端 render 交付**：`codex/l01-gate-merge-dev1`
>
> **须配对的提交**：`0afbfd79be7fe710f7fb2a9133eb858563927c50`
>
> 基线：`origin/develop @ 7079d03`。

## 开发一已交付

1. `RenderViews.MechanismKind.SWITCH`：附带 javadoc，明确它只是 `dock_plate` 的表现变体；
2. `MechanismLayer`：
   - `SWITCH` OFF / 本轮锁存 ON：横向踏板、按键高度与光晕不同；
   - `EXIT` 关闭 / 解锁：分别画关闭门栅、打开门框；解锁态保留 `E` 提示；
   - `DOOR` 绘制分支保留给第二关起使用；
3. `MechanismLayerGateMergeVisualTest`：开关两态、开关与驻留板区分、闸门两态共三项离屏回归。

自动化证据：`./mvnw.cmd -o clean test` 为 **385 / 0 / 0 / 0**；
`git diff --check` 通过。新测试逐项最长 0.221 秒。

## PM 配对所需接线

在开发三已交付第一关数据与机关状态后，PM 仅在 app 投影层完成：

```text
left plate  -> kind=PLATE,  active=左板占用
right plate -> kind=SWITCH, active=右开关本轮锁存 ON
L01 door   -> 不投影单独的 DOOR 条目
exit       -> kind=EXIT,   active=exit.isDoorUnlocked()
```

特别约束：右开关的 `active` 不是“当前是否占用”。离开后仍应保持 ON，直到轮末机关 reset；
不得更改 `RenderViews.Mechanism` 记录结构、事件类型、快照格式或 `ExitTerminal` 契约。

## 当前尚未具备的整批前置

当前 `develop` 仅含 PM 的接口/任务卡确认，尚未见开发三实际关卡与机关提交。配对前请取得：

- `role=switch` 的第一关关卡数据；
- 门与出口同一闸门节点的坐标及可解性证据；
- 右板本轮锁存 ON 的只读值；
- 轮末、重开、场景退出 reset 的开发三/开发二验证 SHA。

缺任一项时，禁止以 `isOccupied()` 猜测锁存状态，禁止在开发一分支补 app/level/mechanism 代码。

## 合入与验收

本 render 分支**不得单边合入 `develop`**。PM 应建立含开发三、开发一和 app 接线的配对分支，完成：

1. 全量自动化与 `git diff --check`；
2. JavaFX 人工验证：开关 OFF/ON、闸门关闭/打开、离开开关仍 ON、轮末/重开复位；
3. 第一轮残影压左板、第二轮开关开门并按 `E` 结算的完整流程。

完成后再由你选择建立最终面向 `develop` 的 PR。
