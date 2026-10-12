# 认证离线测试

[开发指南](development.md) · [认证与会话实现](implementation/auth.md) · [历史验证记录](validation/auth-2026-09-29.md)

本页维护测试隔离方式、既有用例入口与专项命令。测试仅在用户明确要求时执行，默认检查政策以[贡献指南](../CONTRIBUTING.md#构建与检查)为准。带日期的通过数量与设备结果维护在[2026-09-29 验证记录](validation/auth-2026-09-29.md)，不作为当前版本的检查结论。

## 范围与隔离方式

认证 JVM 测试使用现有 JUnit 4 和 `kotlinx-coroutines-test`，协程测试库与协程运行库共用版本目录中的版本。它仅位于 `testImplementation`，不进入 APK；继续使用构造参数注入，不添加 DI 或 Mock 框架。

- [ScriptedApi](../app/src/authTestShared/java/moe/kirakira/testing/ScriptedApi.kt) 直接实现 OkHttp `Call.Factory`，在内存中返回测试指定的响应或失败，**不建立 Socket、不进行 DNS 查询**。只接受 `auth.example.invalid`，未预设的请求立即失败，没有真实网络回退路径。
- 测试不读取 `BuildConfig.API_BASE_URL`，不运行应用或 `SessionViewModel` 的生产初始化，不读取本机真实账号、密码或 token。邮箱和 token 均为固定的虚构数据。
- [MemorySessionStore](../app/src/authTestShared/java/moe/kirakira/testing/AuthFixtures.kt) 实现 `SessionPersistence`，只使用内存。可模拟读取失败、写入失败及暂停写入，不访问 Android Keystore 或真实账号库。
- `SessionCipherTest` 直接调用标准 JCA AES-GCM，密钥每个用例临时生成；既验证篡改拒绝，也验证已有 v1 文件格式兼容。Android Keystore 与磁盘行为另由下述无网络权限的隔离设备包验证。
- 协程调度器与单调时钟可注入，验证请求取消、并发写入和验证码冷却，无须真实等待或连接设备。生产仍使用原来的 Android 时钟和后台调度器。
- ViewModel 测试注入固定邮箱校验函数，隔离 Android `Patterns`；生产继续使用 Android 邮箱格式校验。状态恢复用例重建 `SavedStateHandle` 和 ViewModel，不等同于设备上杀进程再恢复。

## 覆盖内容

下表列出既有用例源码及目标范围，不表示这些用例已在当前版本运行或通过。UI 与业务变化后，需按用户要求的验证范围核对用例是否仍适用。

| 测试 | 覆盖范围 |
| --- | --- |
| [ApiClientTest](../app/src/test/java/moe/kirakira/core/network/ApiClientTest.kt) | URL 参数编码、Cookie 不跨请求继承、HTTP 状态与网络错误映射、异常 JSON、响应大小上限、取消传递与非法路径拒绝 |
| [AuthApiTest](../app/src/test/java/moe/kirakira/data/auth/AuthApiTest.kt) | 2FA 类型与请求字段、验证码模板、业务限流、登录必要字段及 Cookie 字段检查、资料身份一致性 |
| [AuthRepositoryTest](../app/src/test/java/moe/kirakira/data/auth/AuthRepositoryTest.kt) | 密码摘要、先保存再发布、账号去重、存储重试、资料失败、切换失败与会话失效、离线游客／移除、损坏存储、验证码冷却和并发、密码重置清理、旧写入与新请求的竞态 |
| [AuthViewModelTest](../app/src/test/java/moe/kirakira/feature/auth/AuthViewModelTest.kt) | 密码／邮箱／TOTP 登录、四步注册、找回密码、逐步返回保留草稿、字段错误与确认密码重校验、同用途与跨用途冷却、重复提交、邮箱恢复与秘密清除、失败与保存重试、成功状态不可再次编辑 |
| [AuthNavigationTest](../app/src/test/java/moe/kirakira/feature/auth/AuthNavigationTest.kt) | 注册、找回密码和保存重试的返回路径，避免返回已消耗验证码的步骤 |
| [AuthCredentialOperationTest](../app/src/test/java/moe/kirakira/feature/auth/AuthCredentialOperationTest.kt) | 自动选择一次、取消／无提供者、重新登录邮箱筛选、TOTP 完成前不保存、会话存储与密码重置清理重试后才保存、系统原密码不重复保存、保存取消／失败不撤销认证、旋转与退出后的旧结果丢弃 |
| [SessionCipherTest](../app/src/test/java/moe/kirakira/data/auth/SessionCipherTest.kt) | AES-256-GCM 往返、128 次不同 IV、逐字节篡改 IV／密文／标签、错误密钥、截断／追加／超限、旧格式兼容、认证后数据结构检查与错误脱敏 |

上述用例使用真实的客户端请求构造、DTO 解码、Repository 和认证 ViewModel；只替换外部传输、存储及平台依赖。认证写操作在测试中消费内存响应，不会发送验证码、创建账号、重置密码、消耗真实恢复码或更改生产数据。

## 运行方式与结果

在依赖已缓存的环境执行：

```sh
./gradlew :app:testDebugUnitTest --offline
./gradlew :app:assembleDebug --offline
```

`--offline` 阻止 Gradle 下载依赖，测试代码自身由上述内存传输隔离；该参数本身不是测试进程的网络防火墙。新环境需要先同步 Maven 依赖，该过程不应访问业务后端。测试结果位于 `app/build/reports/tests/testDebugUnitTest/index.html`，JUnit XML 位于 `app/build/test-results/testDebugUnitTest/`，不提交这些生成文件。

结果记录须注明执行日期、代码版本、命令、环境、失败或跳过项及验证限制；没有执行的项目不能标记通过。历史结果见[验证记录](validation/auth-2026-09-29.md#首轮存储加固)。

## Keystore 专项设备测试

[SessionStoreInstrumentedTest](../app/src/cryptoCheckAndroidTest/java/moe/kirakira/data/auth/SessionStoreInstrumentedTest.kt) 使用真实 Android Keystore 和 `AtomicFile`，不替换加密提供者。`cryptoCheck` 构建有独立包名 `moe.kirakira.cryptocheck`，其 Manifest 移除 INTERNET、外部存储权限和主 Activity，关闭备份，并将 API 根地址固定为 `https://auth.example.invalid/`；测试 APK 同样没有网络权限。测试开头再次检查包名与权限，防止误操作普通应用。

每个用例生成随机测试密钥别名和私有临时目录；结束时删除测试文件、临时文件和对应密钥。设备测试目录仅在传入 `-Pkirakira.cryptoCheck=true` 时参与构建，替换默认设备测试源目录，测试 APK 不包含普通 UI 测试。它不启动主界面、不执行会话 Repository 的生产初始化、不使用原模拟器快照或现有账号库。

15 项覆盖：密钥不可导出与 AES-256／GCM 用途限制、平台拒绝指定加密 IV、重新打开解密、32 次不同随机 IV、密文篡改后阻止覆盖、读取／写入时密钥丢失、仅剩 `.bak` 时恢复、中断写入恢复、首次写入遗留 `.new`、文件大小上限、环境隔离、未读取前禁止写入、并发完整性及备份白名单。

在专用临时模拟器上复现（示例序列号需替换为该临时设备）：

```sh
./gradlew :app:assembleCryptoCheck :app:assembleCryptoCheckAndroidTest -Pkirakira.cryptoCheck=true --offline
TEST_DEVICE_SERIAL=emulator-5582
adb -s "$TEST_DEVICE_SERIAL" install -r -t app/build/outputs/apk/cryptoCheck/app-cryptoCheck.apk
adb -s "$TEST_DEVICE_SERIAL" install -r -t app/build/outputs/apk/androidTest/cryptoCheck/app-cryptoCheck-androidTest.apk
adb -s "$TEST_DEVICE_SERIAL" shell am instrument -w -e class moe.kirakira.data.auth.SessionStoreInstrumentedTest moe.kirakira.cryptocheck.test/androidx.test.runner.AndroidJUnitRunner
```

该开关会使当前测试构建类型变为 `cryptoCheck`；普通 JVM 测试命令应不带该开关单独执行。普通 Debug／Release 配置仍使用用户选择的生产根地址。

既有平台测试范围和结果见[Keystore 历史记录](validation/auth-2026-09-29.md#keystore-专项)。模拟器的平台 API 验证不能证明 TEE／StrongBox 硬件保护，当前保护边界见[会话存储实现](implementation/auth.md#会话存储与切换)。

## 认证体验与系统密码专项

`authUiCheck` 使用独立包名 `moe.kirakira.authuicheck`，目标与测试 APK 均无 INTERNET 权限。Manifest 移除生产主 Activity、外部存储权限并关闭备份，API 地址固定为 `.invalid`，真实系统凭据网关由构建开关禁用。测试只启动专用 [AuthUiCheckActivity](../app/src/authUiCheck/java/moe/kirakira/testing/AuthUiCheckActivity.kt)，注入内存 API／Store 与 [FakePasswordCredentialGateway](../app/src/authTestShared/java/moe/kirakira/testing/FakePasswordCredentialGateway.kt)。共享替身目录只参与 JVM 测试和该隔离变体，不进入 Debug／Release APK。

[AuthExperienceTest](../app/src/authUiCheckAndroidTest/java/moe/kirakira/feature/auth/AuthExperienceTest.kt) 仍有自动密码选择器、`auth_saved_password` 和 `auth_close` 入口断言，以及把主按钮作为滚动内容的检查。当前登录页使用系统 Autofill、不提供关闭按钮，主操作固定在底栏；这些旧 UI 假设需要在用户要求专项测试时更新。重新验证前应核对这些假设；历史 11 项通过记录不能用于证明当前界面行为。

构建与运行（序列号替换为专用临时设备）：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --offline
./gradlew :app:assembleAuthUiCheck :app:assembleAuthUiCheckAndroidTest -Pkirakira.authUiCheck=true --offline
TEST_DEVICE_SERIAL=emulator-5584
adb -s "$TEST_DEVICE_SERIAL" install -r -t app/build/outputs/apk/authUiCheck/app-authUiCheck.apk
adb -s "$TEST_DEVICE_SERIAL" install -r -t app/build/outputs/apk/androidTest/authUiCheck/app-authUiCheck-androidTest.apk
adb -s "$TEST_DEVICE_SERIAL" shell am instrument -w -e class moe.kirakira.feature.auth.AuthExperienceTest moe.kirakira.authuicheck.test/androidx.test.runner.AndroidJUnitRunner
```

`-Pkirakira.authUiCheck=true` 与 `-Pkirakira.cryptoCheck=true` 互斥，普通 JVM 命令不带这两个开关。加上 instrumentation 参数 `-e capture true` 可把仅包含虚构身份的 Compose 根节点截图写入隔离包 cache；截图不包含系统密码选择器或系统键盘，键盘状态通过窗口 insets 验证。不得对普通应用或用户已有账号做这类采集。

## 尚未验证的部分

当前界面的实际系统 Autofill、真实密码管理器选择／保存／更新、跨设备提供者兼容性及完整预测性返回手势，不能由历史替身结果或预览配置证明。物理设备硬件保护、真实 HTTP/TLS、Socket 超时和生产／预发布联调也不在本次文档检查范围。原专项的完整限制见[历史记录](validation/auth-2026-09-29.md#当时未验证的部分)。

真实登录、发码、注册、密码重置及恢复码使用可能改变后端状态。严格要求不改变生产状态时使用隔离变体和虚构数据；真实闭环应在允许产生测试数据的隔离环境中验证，不能将 `--offline` 当作测试进程的网络防火墙。
