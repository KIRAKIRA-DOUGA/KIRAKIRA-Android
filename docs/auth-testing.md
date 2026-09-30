# 认证离线测试

## 范围与隔离方式

本页记录用户明确要求的认证专项测试，不改变[贡献指南](../CONTRIBUTING.md#构建与检查)中默认只执行 Debug 构建的约定。

认证 JVM 测试使用现有 JUnit 4，新增 `kotlinx-coroutines-test` 1.10.2，与当前解析到的协程运行库版本一致。它仅位于 `testImplementation`，不进入 APK；继续使用构造参数注入，不添加 DI 或 Mock 框架。

- [ScriptedApi](../app/src/authTestShared/java/moe/kirakira/testing/ScriptedApi.kt) 直接实现 OkHttp `Call.Factory`，在内存中返回测试指定的响应或失败，**不建立 Socket、不进行 DNS 查询**。只接受 `auth.example.invalid`，未预设的请求立即失败，没有真实网络回退路径。
- 测试不读取 `BuildConfig.API_BASE_URL`，不运行应用或 `SessionViewModel` 的生产初始化，不读取本机真实账号、密码或 token。邮箱和 token 均为固定的虚构数据。
- [MemorySessionStore](../app/src/authTestShared/java/moe/kirakira/testing/AuthFixtures.kt) 实现 `SessionPersistence`，只使用内存。可模拟读取失败、写入失败及暂停写入，不访问 Android Keystore 或真实账号库。
- `SessionCipherTest` 直接调用标准 JCA AES-GCM，密钥每个用例临时生成；既验证篡改拒绝，也验证已有 v1 文件格式兼容。Android Keystore 与磁盘行为另由下述无网络权限的隔离设备包验证。
- 协程调度器与单调时钟可注入，验证请求取消、并发写入和验证码冷却，无须真实等待或连接设备。生产仍使用原来的 Android 时钟和后台调度器。
- ViewModel 测试注入固定邮箱校验函数，隔离 Android `Patterns`；生产继续使用 Android 邮箱格式校验。状态恢复用例重建 `SavedStateHandle` 和 ViewModel，不等同于设备上杀进程再恢复。

## 覆盖内容

| 测试 | 覆盖范围 |
| --- | --- |
| [ApiClientTest](../app/src/test/java/moe/kirakira/core/network/ApiClientTest.kt) | URL 参数编码、Cookie 不跨请求继承、HTTP 状态与网络错误映射、异常 JSON、响应大小上限、取消传递与非法路径拒绝 |
| [AuthApiTest](../app/src/test/java/moe/kirakira/data/auth/AuthApiTest.kt) | 2FA 类型与请求字段、验证码模板、业务限流、登录必要字段及 Cookie 字段检查、资料身份一致性 |
| [AuthRepositoryTest](../app/src/test/java/moe/kirakira/data/auth/AuthRepositoryTest.kt) | 密码摘要、先保存再发布、账号去重、存储重试、资料失败、切换失败与会话失效、离线游客／移除、损坏存储、验证码冷却和并发、密码重置清理、旧写入与新请求的竞态 |
| [AuthViewModelTest](../app/src/test/java/moe/kirakira/feature/auth/AuthViewModelTest.kt) | 密码／邮箱／TOTP 登录、四步注册、找回密码、逐步返回保留草稿、字段错误与确认密码重校验、同用途与跨用途冷却、重复提交、邮箱恢复与秘密清除、失败与保存重试、成功状态不可再次编辑 |
| [AuthNavigationTest](../app/src/test/java/moe/kirakira/feature/auth/AuthNavigationTest.kt) | 注册、找回密码和保存重试的返回路径，避免返回已消耗验证码的步骤 |
| [AuthCredentialOperationTest](../app/src/test/java/moe/kirakira/feature/auth/AuthCredentialOperationTest.kt) | 自动选择一次、取消／无提供者、重新登录邮箱筛选、TOTP 完成前不保存、会话存储与密码重置清理重试后才保存、系统原密码不重复保存、保存取消／失败不撤销认证、旋转与退出后的旧结果丢弃 |
| [SessionCipherTest](../app/src/test/java/moe/kirakira/data/auth/SessionCipherTest.kt) | AES-256-GCM 往返、128 次不同 IV、逐字节篡改 IV／密文／标签、错误密钥、截断／追加／超限、旧格式兼容、认证后数据结构检查与错误脱敏 |

这些用例使用真实的客户端请求构造、DTO 解码、Repository 和认证 ViewModel；只替换外部传输、存储及平台依赖。认证写操作在测试中消费内存响应，不会发送验证码、创建账号、重置密码、消耗真实恢复码或更改生产数据。

## 运行方式与结果

在依赖已缓存的环境执行：

```sh
./gradlew :app:testDebugUnitTest --offline
./gradlew :app:assembleDebug --offline
```

`--offline` 阻止 Gradle 下载依赖，测试代码自身由上述内存传输隔离；该参数本身不是测试进程的网络防火墙。新环境需要先同步 Maven 依赖，该过程不应访问业务后端。测试结果位于 `app/build/reports/tests/testDebugUnitTest/index.html`，JUnit XML 位于 `app/build/test-results/testDebugUnitTest/`，不提交这些生成文件。

2026-09-29 首轮存储加固记录：62 个 JVM 用例通过（认证相关 48 个、加密 9 个、已有 5 个），无失败或跳过；Debug 构建通过，生产 API 请求为零。测试复现并修正了以下问题：保存失败后重试仍请求资料、身份不一致时错误回退保存、旧提交清除新请求待保存会话、旧页面销毁时清除新页面待保存会话。待保存记录现在绑定认证流程身份，取消、重试和条目销毁只处理该流程的数据。另增加成功状态保护，防止页面关闭前的输入或切换操作重新开启认证流程。本次认证体验扩展后的结果见下方专项记录。

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

2026-09-29 结果：新建且无快照的 API 27（Android 8.1）与 API 37.2（Android 17、16 KiB 页）arm64 模拟器各通过 15 项，均无失败或跳过。执行后确认测试目录为空，并关闭临时模拟器；未安装或启动普通 `moe.kirakira` 包。原始输出位于本地生成目录 `app/build/reports/cryptoCheck/`，不提交。API 37.2 的 `KeyInfo.securityLevel` 为软件级，验证的是平台 API 行为，不能作为 TEE／StrongBox 硬件保护证明。

本轮加固保留 v1 密文格式，修正了缺失密钥时自动生成替代密钥、恢复 `.bak` 前误判为空账号库，以及读取失败后仍可覆盖等路径。读取大小限制适用于恢复后的文件，备份规则收紧为仅外观设置；完整设计和保护边界见[API 与会话](implementation.md#api-与会话)。

## 认证体验与系统密码专项

`authUiCheck` 使用独立包名 `moe.kirakira.authuicheck`，目标与测试 APK 均无 INTERNET 权限。Manifest 移除生产主 Activity、外部存储权限并关闭备份，API 地址固定为 `.invalid`，真实系统凭据网关由构建开关禁用。测试只启动专用 [AuthUiCheckActivity](../app/src/authUiCheck/java/moe/kirakira/testing/AuthUiCheckActivity.kt)，注入内存 API／Store 与 [FakePasswordCredentialGateway](../app/src/authTestShared/java/moe/kirakira/testing/FakePasswordCredentialGateway.kt)。共享替身目录只参与 JVM 测试和该隔离变体，不进入 Debug／Release APK。

[AuthExperienceTest](../app/src/authUiCheckAndroidTest/java/moe/kirakira/feature/auth/AuthExperienceTest.kt) 的 11 项设备用例覆盖：

- 四步注册的真实 Compose / Navigation 3 页面，前进聚焦与输入法可见、返回保留密码与确认密码并收起输入法、验证码页无确认密码、成功返回来源页。
- 自动密码请求一次、指定邮箱、取消后手动再次选择、Activity 重建不重放、已销毁 Activity 的延迟结果丢弃；无提供者仍可手动登录，保存取消不撤销成功。
- 账号切换目标行的边界与位置不变、保留原选中项、目标行显示加载和状态、等待时可返回；会话操作串行、阻止重复操作、失败绑定目标、移除存储重试、游客／移除后的提供者清理失败不阻断，以及启动校验失败后的目标账号重试。
- 英文／中文 320×640dp、2.0 倍字体；英文 740×360dp、1.0 倍字体；中文 740×360dp、1.5 倍字体。通过 `DeviceConfigurationOverride` 检查表单各步骤的主按钮可滚动到达、关闭入口可见。这是 Compose 尺寸／字体覆盖；Activity 重建另有独立用例，不把它等同于所有设备的物理旋转兼容性。

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

2026-09-29 本次认证体验检查：Debug 构建成功；75 个 JVM 用例通过，失败／错误／跳过均为零。新建、无快照且无账号的 API 37.2 arm64 临时模拟器上，11 个认证专项设备用例通过。`authUiCheck` 目标和测试包构建通过，已检查最终 Manifest 不含 INTERNET；保留的 `cryptoCheck` 目标和测试包再次构建通过，本次未重跑其 15 项设备用例。英文窄屏大字体、中文横屏大字体与邀请码键盘展开状态的 Compose 截图经过人工查看。设备输出及所选截图位于本地生成目录 `app/build/reports/authUiCheck/`，不提交生成文件。

冷却回归还确认：同一流程、同一邮箱、同一验证码用途可在返回后复用已发验证码；登录验证码的冷却不能被视作注册验证码已经发送。邀请码页仅在注册发码成功或存在同用途有效冷却时前进。

## 尚未验证的部分

系统 Credential Manager 接入已编译，选择、保存、取消与清理的应用逻辑使用替身验证。**没有读取真实密码管理器，也没有验证 Google Password Manager／其他提供者的真实选择、保存、更新弹窗或跨设备兼容性**。Compose Autofill 的语义与取消边界属于代码检查，未使用真实 Autofill 服务验证建议栏、保存提示及不同提供者行为；隔离设备关闭了 Autofill 服务。Passkey、网页与 App 的共享配置不在范围内。

本次验证实际 Compose 转场、输入法与 Activity 重建，但没有人工验证完整的预测性返回手势、物理设备硬件保护、真实 HTTP/TLS 和 Socket 超时，也未执行生产或预发布账号联调；本地用例通过不能代表这些部分已通过。对 HTTPS、系统证书校验、禁止重定向、无敏感日志及密码不进入应用磁盘的确认属于代码检查。用户确认后交由系统提供者保存密码的边界见[贡献指南](../CONTRIBUTING.md#api-与会话接入规范)。

严格要求不改变生产状态时，应继续使用隔离测试。Rosales 的邮箱和 TOTP 登录会更新验证尝试状态，恢复码还可能删除 TOTP；发送验证码、注册和密码重置均有写入。即使业务接口只读取资料，部署层仍可能记录访问日志或限流计数，因此本轮没有进行生产连通性探测。真实登录闭环应在允许产生测试数据的隔离环境中验证。
