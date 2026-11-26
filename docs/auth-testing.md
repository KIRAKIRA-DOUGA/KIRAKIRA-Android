# 认证离线测试

[开发指南](development.md) · [认证与会话实现](implementation/auth.md) · [历史验证记录](validation/auth-2026-09-29.md)

本文说明认证相关的 JVM 测试如何与真实后端和真实账号隔离、现有用例覆盖什么，以及怎样运行。测试只在用户明确要求时执行，政策见[构建与检查](../CONTRIBUTING.md#构建与检查)。

带日期的通过数量与设备结果保存在 [2026-09-29 验证记录](validation/auth-2026-09-29.md)，它不代表当前版本的检查结论。

## 隔离方式

测试使用 JUnit 4 和 `kotlinx-coroutines-test`，两者只在 `testImplementation` 里，不进入 APK。依赖通过构造参数注入，不使用 DI 或 Mock 框架。替身位于 [src/test 的 `moe.kirakira.testing`](../app/src/test/kotlin/moe/kirakira/testing)。

- **内存传输**。[ScriptedApi](../app/src/test/kotlin/moe/kirakira/testing/ScriptedApi.kt) 直接实现 OkHttp 的 `Call.Factory`，在内存里返回测试指定的响应或失败，不建立 Socket，也不做 DNS 查询。它只接受 `auth.example.invalid`；没有预设的请求立即失败，不存在回退到真实网络的路径。
- **内存账号库**。[MemorySessionStore](../app/src/test/kotlin/moe/kirakira/testing/AuthFixtures.kt) 实现 `SessionPersistence`，可以模拟读取失败、写入失败和暂停写入，不访问 Android Keystore 或真实的账号库。
- **凭据替身**。[FakePasswordCredentialGateway](../app/src/test/kotlin/moe/kirakira/testing/FakePasswordCredentialGateway.kt) 代替系统密码管理器。
- **不读生产配置**。测试不读取 `BuildConfig.API_BASE_URL`，不运行应用或 `SessionViewModel` 的生产初始化，也不读取本机真实的账号、密码或 token。邮箱和 token 都是固定的虚构数据。
- **可控的时间与调度**。协程调度器和单调时钟可以注入，用来验证请求取消、并发写入和验证码冷却，不需要真实等待。
- **隔离 Android 依赖**。ViewModel 测试注入固定的邮箱校验函数，以隔离 Android 的 `Patterns`。状态恢复的用例重建 `SavedStateHandle` 和 ViewModel，这不等同于设备上杀进程后再恢复。

认证写操作在测试里消费的是内存响应：不会发送验证码、创建账号、重置密码、消耗真实的恢复码，也不会更改生产数据。

## 覆盖内容

下表列出现有用例的目标范围。它不表示这些用例已经在当前版本运行或通过；界面与业务变化之后，要按用户要求的验证范围核对用例是否仍然适用。

| 测试 | 覆盖范围 |
| --- | --- |
| [ApiClientTest](../app/src/test/kotlin/moe/kirakira/core/network/ApiClientTest.kt) | URL 参数编码、Cookie 不跨请求继承、HTTP 状态与网络错误的映射、异常 JSON、响应大小上限、取消传递、非法路径拒绝 |
| [AuthApiTest](../app/src/test/kotlin/moe/kirakira/data/auth/AuthApiTest.kt) | 二步验证的类型与请求字段、验证码模板、业务限流、登录必要字段与 Cookie 字段检查、资料身份一致性 |
| [AuthRepositoryTest](../app/src/test/kotlin/moe/kirakira/data/auth/AuthRepositoryTest.kt) | 密码摘要、先保存再发布、账号去重、存储重试、资料失败、切换失败与会话失效、离线游客与移除、损坏的存储、验证码冷却与并发、密码重置清理、旧写入与新请求的竞态 |
| [AuthViewModelTest](../app/src/test/kotlin/moe/kirakira/feature/auth/AuthViewModelTest.kt) | 密码／邮箱／TOTP 登录、四步注册、找回密码、逐步返回保留草稿、字段错误与确认密码重校验、冷却、重复提交、邮箱恢复与秘密清除、失败与保存重试、成功后不可再编辑 |
| [AuthNavigationTest](../app/src/test/kotlin/moe/kirakira/feature/auth/AuthNavigationTest.kt) | 注册、找回密码和保存重试的返回路径，避免回到已经消耗验证码的步骤 |
| [AuthCredentialOperationTest](../app/src/test/kotlin/moe/kirakira/feature/auth/AuthCredentialOperationTest.kt) | 凭据操作的一次性结果、取消与无提供者、TOTP 完成前不保存、存储与清理重试后才保存、系统原密码不重复保存、保存失败不撤销认证、旋转与退出后丢弃旧结果 |

内容、历史、关注列表和评论的“读取失败保留内容”用例也在 `src/test`（`*ResilienceTest`），使用同一套替身。

## 运行

依赖已缓存时：

```sh
./gradlew :app:testDebugUnitTest --offline
```

`--offline` 阻止 Gradle 下载依赖；测试代码自身的隔离靠上面的内存传输。这个参数不是测试进程的网络防火墙。新环境需要先同步 Maven 依赖，这个过程不访问业务后端。

报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`，JUnit XML 位于 `app/build/test-results/testDebugUnitTest/`；这些生成文件不提交。

记录结果时注明执行日期、代码版本、命令、环境、失败或跳过的项目，以及验证的限制。没有执行的项目不能标为通过。

## 设备测试

普通的设备测试位于 `app/src/androidTest/`，用 `./gradlew :app:connectedDebugAndroidTest` 在设备或模拟器上运行。它们使用普通的 debug 包和包名，所以不要用它们操作真实账号。

仓库曾经有两套运行在独立包名、无网络权限安装包里的隔离专项：Keystore 存储测试（`cryptoCheck`）和认证界面测试（`authUiCheck`）。两套都针对已经不存在的实现——前者假设账号库仍然加密写入，后者断言登录页的关闭按钮和自动密码选择器——因此连同对应的构建类型一起移除了。它们当时的结果保留在[验证记录](validation/auth-2026-09-29.md)里。

以后如果需要在设备上验证账号存储或认证界面，应当重新建立隔离：使用独立的包名、去掉网络权限、注入内存 API 与账号库，并且不安装到普通应用的包名下。

## 尚未验证的部分

认证界面的实际交互、系统 Autofill，以及 Google 密码管理器的选择、保存和更新已在真实设备上手动验收，但没有自动化覆盖。以下内容既没有自动化覆盖，也不能由历史的替身结果证明：

- 当前的私有 JSON 账号库在真实设备文件系统上的读写与原子替换。
- Google 密码管理器以外的密码提供者的兼容性。
- 完整的预测性返回手势。
- 真实的 HTTP／TLS、Socket 超时，以及与生产或预发布后端的联调。

真实的登录、发送验证码、注册、密码重置和恢复码的使用都会改变后端状态。需要严格不改变生产状态时，只能使用替身和虚构数据；真实的闭环应当在允许产生测试数据的隔离环境里验证。
