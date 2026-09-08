# 《时痕实验室：昨日的我》成员本地开发与 PR 操作指南

本文只说明成员如何将项目拉取到本地、在自己的分支上开发、提交 Pull Request（PR）以及进行审批。仓库、组织和成员权限已经由项目经理配置完成。

## 1. 开始前确认

1. 已接受 GitHub 组织邀请，并能打开私有仓库：<https://github.com/time-trace-lab-team/time-trace-lab>；
2. 已安装 Git、JDK 17、Maven 和 IntelliJ IDEA；
3. 已领取当天唯一的一张任务卡/Issue，明确所属板块、允许修改的文件和验收条件；
4. 开始业务代码前，已阅读 README 与 `TECHNICAL_GUIDE.md` 中自己板块的章节。

> 每位成员都在自己的电脑上保存一份项目副本。不要多人共用同一个本地项目文件夹，也不要直接编辑其他成员电脑或网盘中的工作副本。

## 2. 第一次拉取项目到本地

在 PowerShell 中进入你准备存放项目的目录，执行：

```powershell
git clone https://github.com/time-trace-lab-team/time-trace-lab.git
cd time-trace-lab
git switch develop
git pull --ff-only origin develop
```

之后用 IntelliJ IDEA 选择 **Open**，打开这个 `time-trace-lab` 文件夹即可。IDEA 会生成本机的 `.idea/` 配置；它只属于你的电脑，不应提交。

## 3. 每次领取任务后的开发流程

### 3.1 先同步 `develop`

每次开始新任务前，先回到日常集成分支并拉取最新版本：

```powershell
git switch develop
git pull --ff-only origin develop
```

如果该命令提示本地有未提交修改，不要强行覆盖。先完成、提交或与项目经理确认如何处理当前修改。

### 3.2 创建自己的功能分支

不要在 `main` 或 `develop` 上直接开发。根据任务板块创建分支：

```powershell
# 开发 1：基础引擎、输入、玩家、渲染
git switch -c feature/core-<任务号>-<简述>

# 开发 2：记录、回放、重置、快照
git switch -c feature/replay-<任务号>-<简述>

# 开发 3：机关、关卡、UI、存档、音频
git switch -c feature/content-<任务号>-<简述>

# 测试：测试用例、测试文档、复现材料
git switch -c test/<任务号>-<简述>
```

示例：`feature/replay-12-recording-frame`。分支名使用英文小写、数字和连字符，不使用空格或中文。

### 3.3 开发与本地验证

1. 只改任务卡允许的文件；一张任务卡只能属于一个板块；
2. 使用 AI 时，明确告诉 AI：已读章节、单一目标、允许修改路径、禁止修改路径和验证要求；
3. 不顺手重构其他板块，不擅自改公共接口、`pom.xml`、存档格式或关卡格式；
4. 按任务卡要求运行测试、编译或手工验证；
5. 查看改动：

```powershell
git status
git diff
```

## 4. 提交并推送自己的改动

确认只包含本任务相关内容后，逐个暂存相关文件或目录：

```powershell
git add src/main/java/org/example/timeloop/replay
git add src/test/java/org/example/timeloop/replay
git status
git commit -m "feat(replay): 添加单轮行为记录"
git push -u origin feature/replay-12-recording-frame
```

提交信息可参考：

| 类型 | 示例 |
| --- | --- |
| 新功能 | `feat(core): 添加方向输入队列` |
| 修复 | `fix(replay): 修复残影位置偏移` |
| 测试 | `test(core): 添加路口转向测试` |
| 文档 | `docs: 更新测试记录` |

### 不应提交的内容

- `.idea/`：个人 IntelliJ 配置；
- `target/`、`.class`、打包产物和日志；
- 密钥、Token、账户密码、个人路径或隐私数据；
- 与任务无关的格式化、重构或其他成员的未完成代码；
- 来源或许可证不明确的代码、图片、字体和音频。

如果 `git status` 里出现 `.idea/`，先确认根目录 `.gitignore` 有一行 `.idea/`。若它已被 Git 暂存：

```powershell
# 已经有提交记录的普通仓库
git restore --staged -- .idea

# 没有任何提交记录的仓库才使用这一条
git rm -r -f --cached -- .idea
```

这两条命令只将 `.idea/` 移出 Git 暂存区，不会删除电脑上的 IntelliJ 配置文件。

## 5. 在 GitHub 创建 PR

推送成功后，打开仓库页面。GitHub 通常会显示 **Compare & pull request**，点击它；若没有，进入 **Pull requests → New pull request**。

填写时确认：

| 字段 | 要求 |
| --- | --- |
| Base / 目标分支 | `develop` |
| Compare / 来源分支 | 你刚推送的 `feature/...` 或 `test/...` 分支 |
| Title | 与任务一致，例如 `feat(replay): 添加单轮行为记录` |
| Description | 关联任务、改动内容、验证结果、风险或待确认项 |
| Reviewer | 指定至少一名**非 PR 作者**的成员 |

PR 描述可复制：

```markdown
Closes #<Issue 编号>

## 所属板块
开发 1 / 开发 2 / 开发 3 / 测试（只能选一个）

## 改动内容
- 

## 验证结果
- 命令：
- 自动化测试：
- 手工验证：

## 风险或待确认项
- 无 / 
```

## 6. 审批、修改与合并

### 审批人

1. 阅读 PR 的任务说明、文件改动和验证结果；
2. 检查是否跨越任务边界、遗漏测试或提交无关文件；
3. 有问题时留下评论或 **Request changes**；
4. 确认通过后点击 **Approve**；
5. PR 作者不能审批自己的 PR；每个人都可轮流审批其他成员的 PR。

### PR 作者收到意见后

在原来的功能分支继续修改、测试和提交：

```powershell
git add <修改过的相关文件>
git commit -m "fix(replay): 修正评审发现的边界情况"
git push
```

不要重新创建 PR；再次 `git push` 后，原 PR 会自动更新。若改动使原审批结论失效，应请 reviewer 重新检查。

### 合并

审批通过、测试结论明确后，由当天轮值的项目经理或指定成员合并到 `develop`。合并后，PR 作者同步本地环境：

```powershell
git switch develop
git pull --ff-only origin develop
```

确认无误后，可在 GitHub 删除已合并的远端功能分支；本地分支可在确认不再需要后删除。

## 7. 私有仓库期间的强制团队约定

目前为 GitHub Free 组织的私有仓库，GitHub Ruleset 不会技术强制执行。因此全员必须遵守：

1. 不直接向 `main` 或 `develop` 推送；所有改动经 PR 合入 `develop`；
2. `main` 只接收稳定版本的 `develop → main` PR；
3. 每个 PR 至少由一名非作者成员审查；
4. 每天下班前，轮值项目经理检查 `develop` 提交历史、PR 和测试结果；
5. 出现误直接推送时，立即在 Issue 记录原因，并恢复之后的 PR 流程；
6. 项目完成并改为 Public 后，再为 `main` 和 `develop` 配置可执行的 Ruleset。

## 8. 每日结束核对

- [ ] 每位成员的当天任务有对应分支和 PR，或记录了未完成原因；
- [ ] 每个 PR 已填写验证结果并指定非作者 reviewer；
- [ ] 已合并 PR 的改动已在 `develop` 上完成测试、启动检查和试玩；
- [ ] `.idea/`、`target/`、密钥、日志和无关文件没有进入提交；
- [ ] 接口变更、缺陷和次日依赖已记录到 Issue 或项目看板；
- [ ] 没有人直接向 `main` 或 `develop` 推送。
