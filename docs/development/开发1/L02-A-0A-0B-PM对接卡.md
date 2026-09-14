# L02-A 开发一 0A/0B · PM 对接卡

> 任务：`L02-A-DEV1`
> 分支：`codex/l02-a-dev1`
> 实际基线：`origin/develop @ 450c9719afb6f4fa1e682215f5feeb11ecd54b73`
> 日期：2026-09-14
> 性质：开发一提案/异常回报，不代替 PM 最终裁决

> PM 回复：已于 `origin/develop @ 85060e2` 合入 PR #86，正式裁决见
> `L02-门房与双残影-PM裁决.md` §十二。本卡下方复选项已按裁决回填。

## 1. 0A：双残影代际表达提案

### 1.1 live source 事实

`RenderViews.EchoTrail` 当前签名为：

```java
EchoTrail(int sourceRound, List<Vector2D> points, boolean newer)
```

`EchoTrailLayer` 已使用 `newer` 选择实线/虚线和透明度，并使用 `+2 px / -2 px`
的屏幕空间平行错位。`sourceRound` 保留录制来源轮次，`newer` 表达当前两代中的新/旧关系。

### 1.2 开发一建议

L02 当前最多同时存在两条残影，因此建议：

1. **不新增 `EchoTrail` 字段**，沿用 `sourceRound + newer`；
2. 轨迹上的视觉语义固定为：较新残影实线/+2 px，较旧残影虚线/-2 px；
3. `/E1 / E2` HUD 文案由开发三在 `ui/**` 定义，PM 在 `app/**` 使用当前轮次与
   `sourceRound` 生成相对代际；不将 HUD 文案塞入 Canvas 轨迹数据；
4. `H` 增强需要 app 输入和只读显示状态。在 PM 冻结字段及构造方向前，开发一不修改
   `app/**`，也不自造第二套输入状态。

### 1.3 请 PM 确认

- [x] 接受沿用 `EchoTrail(sourceRound, points, newer)`，本批不改签名。
- [x] HUD 相对代际由开发三/PM 在 `ui/app` 配对，开发一只保证轨迹非纯颜色可辨。
- [x] `H` 增强从本批移出，等待 PM 另发 `RENDER-H-ECHO-EMPHASIS`。

## 2. 0B：基线慢测试例外回报

### 2.1 实际复跑

```text
command: .\mvnw.cmd -o clean test
result:  410 / 0 / 0 / 0, BUILD SUCCESS
total:   33.649 s
slowest: org.example.timeloop.level.Level01MapRenderSnapshotTest = 3.027 s
```

A2 完成后使用同一命令复跑：`411 / 0 / 0 / 0`，该基线慢测试仍为 `2.956 s`；
新增房门用例为 `0.012 s`，所在 render 测试类合计 `0.083 s`。

### 2.2 边界判断

`Level01MapRenderSnapshotTest` 位于 `src/test/java/org/example/timeloop/level/**`，是开发一任务卡的禁止路径。
本次超时在 L02 代码修改前已存在，不应通过开发一越界修改 `level/**` 或放宽断言来隐藏。

### 2.3 请 PM/测试负责人裁决

- [x] L02-A 记为基线已知例外，开发一门禁改为“不较基线中位数恶化 > 50%”。
- [x] 由开发三按 `TECH-DEBT-SNAPSHOT-RUNTIME-v2` 另行收口，不阻塞 L02-A 合入。

PM 同时冻结测量口径：无 JavaFX/构建/重负载并发，至少 3 次复跑并报中位数；
单次超过 1 s 不单独构成回归。

在裁决前，开发一可继续不涉及该路径的独立 `render/**` 小块，但不声称已满足整批
“任何单测 <= 1 s”门禁。

## 3. 开发一当前停止线

- 不修改 `app/**`、`ui/**`、`level/**`、`mechanism/**`、`replay/**`、`snapshot/**`；
- 不修改 `RenderViews.Mechanism` 结构；
- 若 PM 要求新增 `EchoTrail` 字段，必须先确认新签名与 `app/**` 构造点的配对方案；
- 本卡不改变机关、回放、时间或关卡语义。
