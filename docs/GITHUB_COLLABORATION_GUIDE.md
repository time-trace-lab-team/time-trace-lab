# 《时痕实验室：昨日的我》GitHub 协作操作手册

本文用于团队每天同步进度、开发者提交 Pull Request（PR）、测试人员验证、项目经理集成代码。它补充 `README.md` 的协作约定，不替代其中的产品范围、模块边界和 AI 编码规则。

> **先决条件：** 在开始业务代码前，项目经理应先按 README 的附录 A 生成并组织评审 `TECHNICAL_GUIDE.md`。每个任务只能属于一个板块；跨模块需求先拆任务、定接口，不能顺手一起实现。

## 1. 推荐的组织与仓库

### 1.1 创建 GitHub Organization

在 GitHub 的 “Set up your organization” 页面填写：

| 页面字段 | 推荐填写 | 说明 |
| --- | --- | --- |
| Organization name | `time-trace-lab-team` | 这是组织的 URL 名称；若已被占用，可改为 `time-trace-lab-2026`。 |
| Contact email | 项目经理可长期接收邮件的邮箱 | 用于 GitHub 的组织通知，不要使用即将失效的临时邮箱。 |
| This organization belongs to | **My personal account**（`JiuYue-IT`） | 本项目是学生团队项目，应选择此项。除非学校/实验室明确要求接管，不选择 “A business or institution”。 |

创建组织不会把成员的个人账号合并在一起；每位成员仍使用自己的 GitHub 账号。它的作用是把仓库、成员权限和后续项目资料放到一个共同空间中。

### 1.2 创建仓库

在组织下创建：

| 设置项 | 推荐值 | 原因 |
| --- | --- | --- |
| Repository name | `time-trace-lab` | 简短，且与项目英文名一致。 |
| Description | `A JavaFX time-loop puzzle game where past actions become cooperative echoes.` | 说明技术和核心玩法。 |
| Visibility | **Private** | 开发过程、素材和成员信息只对团队开放。答辩或开源时再改为 Public。 |
| Add README | 不添加 | 本地已有 `README.md`。 |
| Add .gitignore | 不添加 | 本地已有 `.gitignore`。 |
| Add license | 不添加 | 私有开发阶段不需要；决定开源时再由全组选择许可证。 |

远端仓库要保持为空，避免第一次推送时出现 README 或 `.gitignore` 合并冲突。

### 1.3 邀请成员与权限

你们需要每日轮换职责，因此每位成员都应具有同等的仓库协作权限。在组织主页点击 **Customize members' permissions**，进入 `Organization Settings → Member privileges`，将 **Base permissions** 设为 **Write**。随后邀请每位成员加入组织。

这会让每名组织成员对组织内的所有仓库都拥有 Write 权限。当前只有这一个团队项目时，这个设置最直接；未来若组织新增不希望全员访问的仓库，应改回 `No permission`，并在单个仓库中通过 Team 单独授予 Write。

| 人员 | 建议仓库权限 | 主要能力 |
| --- | --- | --- |
| 项目经理 | Write | 建分支、推送、创建/评审/合并 PR；负责当天集成安排。 |
| 开发 1/2/3 | Write | 建分支、推送、创建/评审/合并 PR。 |
| 测试 | Write | 创建测试分支、提交测试和文档、评审/合并 PR。 |
| 组织 Owner | 仅创建组织的人，必要时另设一名可信备份人员 | 管理成员、组织和敏感设置；**不**作为每日轮换权限。 |

`Write` 权限已经足够让非仓库创建者审查、批准和合并符合分支规则的 PR。每个人都可以轮流担任当天 reviewer；但 PR 作者自己的批准不能满足“至少一位他人批准”的规则。

> 不要把所有成员设为 Organization Owner。`Write` 已足够完成日常角色轮换；Owner 可以管理所有成员、付款和组织安全设置，应只保留给创建者及最多一名可信备份人员。

## 2. 首次把当前项目目录推送到 GitHub

当前 `D:\IntelliJ IDEA 2026.1\Java-project` 已经是 Git 工作目录，因此**直接使用它**。不要在它里面创建第二层同名仓库，也不要让多人共用这一份本地目录。

首次提交前，项目经理先检查：

1. 不提交 `target/`、个人路径、密钥、日志或打包产物；
2. 不提交个人 IntelliJ 设置 `.idea/`。按下一节将 `.idea/` 加入 `.gitignore`，并移出暂存区；
3. 确认 `README.md`、`pom.xml`、`src/` 和必要的 Maven Wrapper 文件是本次提交的内容；
4. 提交前先让全组确认 README 的需求冻结状态。

### 2.1 IntelliJ `.idea/`：忽略、移出暂存区与恢复

`.idea/` 保存的是个人 IntelliJ 配置，例如本地 JDK 路径、窗口布局和插件状态。把它提交到团队仓库容易造成无意义冲突；它不是 Java 源码、Maven 依赖或游戏资源，忽略它**不会影响项目编译、运行或其他成员打开项目**。

#### 第一步：加入忽略规则

在根目录 `.gitignore` 中单独增加一行：

```gitignore
.idea/
```

然后暂存这项规则本身：

```powershell
git add .gitignore
```

#### 第二步：移出暂存区，但保留本地文件

先执行 `git status --short`。若列表中出现 `A`、`AM` 或 `M` 开头的 `.idea/...` 文件，表示它们在暂存区或被 Git 跟踪。

**已有至少一个 Git 提交的普通仓库：**

```powershell
git restore --staged -- .idea
git status
```

**像本项目这样、尚未产生首个提交的仓库：** `git restore --staged` 会因没有 `HEAD` 而失败，应改用：

```powershell
git rm -r -f --cached -- .idea
git status
```

其中 `--cached` 的含义是“只从 Git 暂存区移除”，不会删除电脑上的 `.idea/` 文件夹。`-f` 只用于允许移除“暂存版本与本地版本不同”的配置文件，仍不会删除本地文件。

成功后，`.idea/` 应不再显示在 `git status` 的待提交列表中；若仍显示，确认 `.gitignore` 的 `.idea/` 行已保存，再重新执行相应命令。

#### 恢复方法与影响边界

| 情况 | 恢复方式 | 对项目的影响 |
| --- | --- | --- |
| 只是执行了 `git restore --staged` 或 `git rm --cached` | 不需要恢复；本地 `.idea/` 仍在，直接继续用 IntelliJ 开发。 | 只改变 Git 的待提交清单，不改任何代码。 |
| 误删了磁盘上的 `.idea/` | 关闭并重新打开 IntelliJ 项目，再重新加载 Maven；IDE 会重新生成大多数配置。 | 不影响源码或 Maven 项目；只需重新选择本机 JDK、窗口布局等个人选项。 |
| 团队明确决定重新跟踪某个 `.idea` 文件 | 由项目经理确认后使用 `git add -f .idea/<文件名>`，单独开 PR 说明理由。 | 会重新引入 IDE 配置冲突；默认不建议。 |
| 误把 `.idea/` 忽略规则删掉 | 将 `.idea/` 这一行重新写入根目录 `.gitignore`，执行 `git add .gitignore`。 | 只影响未来是否被 Git 发现，不改变现有源码。 |

不要使用 `Remove-Item`、`rm -rf` 或 `git clean` 来处理 `.idea/`，这些命令可能真的删除本地 IDE 配置。

创建空仓库后，在当前目录执行（将 URL 替换为创建页面提供的 SSH 或 HTTPS 地址）：

```powershell
git branch -M main
git add README.md pom.xml .gitignore src .mvn
git commit -m "docs: 初始化时痕实验室项目"
git remote add origin <仓库地址>
git push -u origin main

git switch -c develop
git push -u origin develop
```

如果团队使用 HTTPS，GitHub 登录时应使用浏览器授权或 Personal Access Token，不能把账户密码写进命令、代码或文档。

## 3. 分支和保护规则

### 3.1 分支用途

| 分支 | 用途 | 谁可以直接推送 |
| --- | --- | --- |
| `main` | 已验收、可演示或发布的稳定版本 | 无；只能通过 PR。 |
| `develop` | 每天集成后的开发版本 | 无；只能通过 PR。 |
| `feature/core-*` | 开发 1：基础引擎、输入、玩家、渲染 | 对应任务负责人。 |
| `feature/replay-*` | 开发 2：记录、回放、重置、快照 | 对应任务负责人。 |
| `feature/content-*` | 开发 3：机关、关卡、UI、存档、音频 | 对应任务负责人。 |
| `test/*` | 测试用例、测试文档、复现材料 | 测试负责人。 |

### 3.2 分支保护设置

在 `Settings → Rules → Rulesets`（或旧版 `Branches`）分别对 `main`、`develop` 建规则：

1. Require a pull request before merging；
2. Require **1 approval**；
3. Require conversation resolution before merging；
4. Dismiss stale approvals when new commits are pushed；
5. Block direct pushes / Do not allow force pushes。

等 Maven 测试能够稳定运行后，再创建 GitHub Actions 工作流，并把 `mvn test` 对应的检查设为必需。不要在检查尚未存在时强制它，否则所有 PR 都会被无意义地卡住。

## 4. 每日工作流程

```text
任务卡 / Issue
      ↓
个人功能分支 → 开发与本地验证 → PR 到 develop
                                        ↓
                            开发主责 + 测试审查
                                        ↓
                              项目经理合并 develop
                                        ↓
                         构建、测试、启动、试玩记录
                                        ↓
                     稳定里程碑 PR：develop → main
```

### 4.1 每天的固定节奏

| 时间 | 负责人 | 要做什么 |
| --- | --- | --- |
| 09:00 | 全员 / 项目经理 | 10–15 分钟站会；确认当天唯一里程碑和每人的 Issue。 |
| 09:15 | 全员 | 前 5 天可进行 60–90 分钟影子轮岗；它不转移模块最终责任。 |
| 开发时段 | 开发 / 测试 | 从 `develop` 建个人分支，完成一个范围明确的任务卡。 |
| 16:30 | 项目经理、相关主责 | 对当日真实发生的接口变更进行同步和代码评审。 |
| 下班前 | 全员 | PR、测试、合并；在 `develop` 上完成 `mvn test`、启动检查和 10 分钟试玩。 |

## 5. 项目经理操作指南

### 每天开始

1. 建立或更新 GitHub Issue：写明所属板块、目标、允许/禁止修改路径、验收条件和验证步骤；一张 Issue 只对应一个板块。
2. 将 Issue 分配给一人；若属于影子轮岗，注明模块主责人和最终 reviewer。
3. 在 GitHub Project 看板中将任务移到 `Today` 或 `In progress`。
4. 对跨模块需求，先记录待提供的接口，联系模块主责人拆为新的 Issue；不要要求同一个 PR 跨模块完成。

### 审查与合并 PR

1. 确认 PR 目标分支为 `develop`，且关联一个 Issue。
2. 检查改动是否超出任务卡允许路径；如果越界，要求拆分或撤出无关改动。
3. 确认作者提供了构建/测试/手工验证证据，测试人员已给出结论。
4. 检查至少有一名非作者 reviewer 批准，且没有未解决评论。
5. 合并到 `develop` 后执行或确认 `mvn test`、启动检查和试玩记录；失败则回滚或马上开修复 PR。
6. 只有稳定里程碑才创建 `develop → main` 的发布 PR。

## 6. 开发人员操作指南

### 首次克隆

```powershell
git clone <仓库地址>
cd time-trace-lab
git switch develop
git pull --ff-only origin develop
```

每位成员都必须 clone 到自己的本地目录；不要在网盘共享目录、他人电脑或项目经理的工作目录里直接修改。

### 领取任务后开发

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/core-123-input-queue
```

将示例分支名按自己任务替换，例如开发 2 使用 `feature/replay-123-*`，开发 3 使用 `feature/content-123-*`。然后：

1. 阅读 README、`TECHNICAL_GUIDE.md` 的公共约定与自己板块章节、对应 Issue；
2. 只修改任务卡允许的文件；使用 AI 时在提示中写明已读章节、允许路径、禁止路径和验收条件；
3. 在本地编译、运行测试，并完成任务卡要求的手工验证；
4. 查看差异，确认没有把 `.idea/`、`target/`、密钥或无关重构带进去；
5. 提交并推送：

```powershell
git status
git add <本次任务的文件或目录>
git commit -m "feat(core): 添加方向输入队列"
git push -u origin feature/core-123-input-queue
```

6. 在 GitHub 开 PR：目标分支选 `develop`；填写关联 Issue、改动摘要、验证命令/结果、手工验证和风险；
7. 根据 review 意见继续在**同一分支**提交和推送，PR 会自动更新。合并后更新本地 `develop`，再开始下一张任务。

提交信息可使用：`feat(core): ...`、`feat(replay): ...`、`feat(content): ...`、`fix(replay): ...`、`test(core): ...`、`docs: ...`。

## 7. 测试人员操作指南

1. 每天从 `develop` 拉取最新代码，依据 README、技术指南和 Issue 的验收条件建立测试清单。
2. 对每个 PR 执行适用的自动化测试、启动检查和手工试玩；不只接受“AI 说可以运行”。
3. 在 PR 留下明确结论：`通过`、`有条件通过` 或 `不通过`，并附上执行命令、实际结果和环境。
4. 发现问题时建立 Issue：复现步骤、预期结果、实际结果、严重级别（阻塞/高/中/低）、截图或必要日志、关联 PR。
5. 测试人员可以提交测试、文档和复现材料的 PR；不要为了让测试通过而直接修改别人的业务代码。
6. 合并后在 `develop` 做回归；第 6 天后优先处理阻塞和高优先级缺陷。

## 8. 轮岗与 PR 审批规则

README 采用“固定主责 + 前 5 天短时影子轮岗”。因此：

- GitHub 上的 `Write` 成员均可审查和批准他人的 PR，仓库创建者不是唯一审批者；
- 轮岗成员可以承担当天低风险任务和一次 review；
- 影响公共接口、`pom.xml`、存档格式、关卡格式或跨模块依赖的 PR，必须由模块主责人与项目经理共同确认；
- 任何 PR 作者都不能用自己的批准替代他人验收；
- Organization Owner 不是每日轮换岗位，至少保留项目经理与一位可信备份人员。

## 9. 每日 PR 模板

将以下内容复制到 PR 描述中：

```markdown
## 关联任务
Closes #<Issue 编号>

## 所属板块
开发 1 / 开发 2 / 开发 3 / 测试（只能选一个）

## 改动内容
- 

## 已阅读
- README.md：第 __ 节
- TECHNICAL_GUIDE.md：第 __ 节

## 允许修改的范围
- 

## 验证结果
- 命令：
- 自动化测试：
- 手工验证：

## 风险或待确认项
- 无 / 
```

## 10. 每日结束核对清单

- [ ] 每个成员的当天进度已更新到 Issue 或项目看板；
- [ ] 每个开发任务都有对应 PR，或明确记录未完成原因；
- [ ] 所有合并到 `develop` 的 PR 均有至少一名非作者批准；
- [ ] `develop` 已完成构建、测试、启动检查和短时试玩；
- [ ] 缺陷已按严重级别记录；
- [ ] 接口变更、风险和次日依赖已由项目经理记录；
- [ ] 没有将 `.idea/`、`target/`、密钥、个人路径或无许可证资源提交到仓库。
