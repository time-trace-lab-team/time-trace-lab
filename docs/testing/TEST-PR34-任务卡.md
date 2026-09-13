# Maven 测试报告

## 基本信息

| 项目 | 内容 |
|---|---|
| 项目名称 | java-project |
| GroupId | org.example |
| ArtifactId | java-project |
| 版本 | 1.0-SNAPSHOT |
| 打包类型 | jar |
| 项目路径 | C:\Users\Eason\IdeaProjects\time-trace-lab1 |
| Maven 版本 | Apache Maven 3.9.11 |
| Java 编译版本 | release 17 |
| 测试框架 | JUnit Platform |
| Surefire 版本 | 3.5.3 |
| 完成时间 | 2026-09-10T15:07:22+08:00 |
| 总构建耗时 | 1.854 s |

## 构建结果

| 状态 | 结果 |
|---|---|
| 主源码编译 | 无需编译，所有类均为最新 |
| 测试源码编译 | 无需编译，所有类均为最新 |
| 测试执行 | 成功 |
| 构建结果 | BUILD SUCCESS |

## 测试执行结果

| 测试类 | 运行数 | 失败数 | 错误数 | 跳过数 | 耗时 | 结果 |
|---|---:|---:|---:|---:|---:|---|
| org.example.AppTest | 1 | 0 | 0 | 0 | 0.057 s | 通过 |

## 汇总

| 指标 | 数量 |
|---|---:|
| Tests run | 1 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

## 编译信息

| 阶段 | 内容 |
|---|---|
| 主源码编译 | Nothing to compile - all classes are up to date. |
| 测试源码编译 | Nothing to compile - all classes are up to date. |
| 资源目录 | 主资源目录和测试资源目录不存在，已跳过 |

## 警告信息

构建过程中出现以下警告，但不影响本次测试结果：

- `sun.misc.Unsafe` 中一个即将废弃的方法被调用。
- `sun.misc.Unsafe::staticFieldBase` 被 `com.google.inject.internal.aop.HiddenClassDefiner` 调用。
- `sun.misc.Unsafe::staticFieldBase` 将在未来版本中移除。
- 类 `org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher` 的 final 字段 `_cipher` 被 `org.eclipse.sisu.bean.BeanPropertyField` 反射修改。
- 可通过 `--enable-final-field-mutation=ALL-UNNAMED` 临时避免 final 字段修改警告。
- 未来版本可能默认阻止 final 字段反射修改。

## 结论

本次 Maven 测试执行成功。

- 共运行测试：1 个
- 失败：0 个
- 错误：0 个
- 跳过：0 个
- 最终结果：**BUILD SUCCESS**