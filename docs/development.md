# 开发指南

[返回项目首页](../README.md) · [贡献规范](../CONTRIBUTING.md) · [实现说明](implementation.md)

本文维护开发环境、运行方法与目录说明。

## 开发环境

| 配置 | 当前仓库值 |
| --- | --- |
| 应用 ID / namespace | `moe.kirakira` |
| Android 最低版本 | API 27 |
| compileSdk / targetSdk | 37（minor API 2）/ 37 |
| Gradle Daemon JDK | 25 |
| Java 源码 / 字节码兼容级别 | 11 |

依赖与插件版本以[版本目录](../gradle/libs.versions.toml)为准，文档不重复记录具体版本号。环境配置来源为[应用构建配置](../app/build.gradle.kts)、[Gradle Wrapper](../gradle/wrapper/gradle-wrapper.properties) 与 [Daemon JDK 配置](../gradle/gradle-daemon-jvm.properties)。使用兼容项目工具链的 Android Studio，并安装匹配 compileSdk 37、minor API 2 的 Android SDK Platform。JDK 25 用于运行 Gradle，与 Java 11 的编译兼容设置用途不同。

应用直接声明 `androidx.core:core` 和 `androidx.lifecycle:lifecycle-runtime`，分别提供 `SharedPreferences.edit` 等 Kotlin 扩展和 `repeatOnLifecycle`，无需再直接声明旧的 `-ktx` 模块。Compose 中的 `collectAsStateWithLifecycle` 仍由 `lifecycle-runtime-compose` 提供。

Material 3 单独使用公开 Expressive 主题与分段列表 API 的版本，其余 Compose 库仍由 BOM 管理；原因和升级要求见[贡献指南](../CONTRIBUTING.md#依赖与配置)。

## 运行与安装

1. 使用 Android Studio 打开项目根目录，配置 JDK 25 并同步 Gradle。
2. 在 SDK Manager 中安装项目要求的 SDK；由 IDE 生成本机 `local.properties`，不要提交此文件。
3. 创建 API 27 或更高版本的模拟器，或连接启用 USB 调试的设备。
4. 选择 `app` 运行配置并运行。首次同步需要下载 Gradle 与依赖。Wrapper 下载 Gradle 分发包时按配置中的 `distributionSha256Sum` 校验文件。

也可以在项目根目录执行以下命令；Windows 将 `./gradlew` 替换为 `gradlew.bat`。

| 命令 | 用途 |
| --- | --- |
| `./gradlew :app:assembleDebug` | 默认检查：编译并生成 `app/build/outputs/apk/debug/` 下的 APK，不运行测试 |
| `./gradlew :app:installDebug` | 安装到设备，随后从设备桌面打开应用 |
| `./gradlew :app:assembleRelease` | 在 `app/build/outputs/apk/release/` 生成未签名通用包 `KIRAKIRA-<versionName>-release.apk` |
| `./gradlew :app:assemblePerformance` | 生成非调试、可采样且启用 R8 优化的本地性能 APK，不运行测试 |
| `./gradlew :app:installPerformance` | 安装本地性能 APK，使用默认 Debug 签名与相同应用 ID |
| `./gradlew verify` | 仅在用户明确要求时：构建 Debug、单元测试、Android Lint |
| `./gradlew :app:testDebugUnitTest` | 仅在用户明确要求时：运行本机 JUnit 4 测试 |
| `./gradlew :app:lintDebug` | 仅在用户明确要求时：运行 Android Lint |
| `./gradlew :app:connectedDebugAndroidTest` | 仅在用户明确要求时：在设备或模拟器上运行仪器测试 |

检查政策以[贡献指南](../CONTRIBUTING.md#构建与检查)为准：代码或构建变更默认只执行 Debug 构建，纯文档修改只检查内容和链接。测试、`verify`、额外 Lint 和设备／截图检查仅按用户明确要求执行，不因查阅上述命令而自动运行。

Release 在[应用构建配置](../app/build.gradle.kts)中启用 R8 代码和资源优化。发布时保留同次构建生成的 `app/build/outputs/mapping/release/mapping.txt`，用于还原该版本的混淆堆栈。

`verify` 在[根构建脚本](../build.gradle.kts)中聚合 Debug 构建、单元测试和 Android Lint，不包含设备测试，也不检查全部 Kotlin 格式规则。测试和 Lint 报告位于 `app/build/reports/`。设备测试需要模拟器或连接的设备；已有用例和历史结果不代表当前版本已经验证。

认证专项使用内存传输、账号存储与虚构身份；各变体的隔离方式、完整命令和历史结果统一维护在[认证离线测试](auth-testing.md)。

| 专项入口 | 用途 |
| --- | --- |
| 不带专项属性的 `:app:testDebugUnitTest` | JVM 认证、会话与网络用例 |
| `-Pkirakira.cryptoCheck=true` | 无网络权限的隔离 Keystore 设备验证 |
| `-Pkirakira.authUiCheck=true` | 无网络权限、使用凭据替身的隔离认证 UI 验证 |

两个专项属性互斥，普通 JVM 命令不带这些属性。认证 UI 中的部分旧断言已不对应当前界面，适用范围见[专项说明](auth-testing.md#认证体验与系统密码专项)。

PR 默认报告构建结果，纯文档修改报告内容与链接检查结果。远端分支保护由维护者配置。

### Compose 布局预览

需要按用户要求检查预览时，优先用已安装的 [Android CLI](https://developer.android.com/tools/agents/android-cli) 在宿主机独立渲染 PNG，不需要运行 Android Studio、Computer Use、设备或模拟器，也不需要新增截图测试插件。从仓库根目录执行：

```sh
android preview render :app \
  --fqn=moe.kirakira.ui.components.HistorySearchLogoKt.HistorySearchLogoPreview \
  --fqn=moe.kirakira.ui.components.HistorySearchLogoKt.HistorySearchLogoDarkPreview \
  --format=json \
  --output=/tmp/history-clock-previews \
  -- --console=plain --no-scan
```

命令编译目标并返回生成图片的绝对路径；具体参数见 [Preview render](https://developer.android.com/tools/agents/android-cli/commands/preview_render)。实际打开浅深色 PNG 核对外观，命令成功不代替视觉确认。

较旧的 CLI 可借用运行中的 Android Studio 渲染器：

```sh
android studio render-compose-preview \
  app/src/main/java/moe/kirakira/ui/components/HistorySearchLogo.kt \
  HistorySearchLogoPreview \
  --output-image-file=/tmp/history-clock-light.png
```

兼容入口中，将 composable 名改为 `HistorySearchLogoDarkPreview` 可导出深色预览；存在多个 Studio 项目时，用 `--project` 指定项目绝对路径。该入口可能返回 IDE 旧缓存；代码发生可见变化而图片仍相同时，等待 IDE 完成更新后重新导出，并实际查看生成图片。两个入口只渲染指定预览，不新增基线比较或自动测试；预览检查仍须遵循[构建与检查](../CONTRIBUTING.md#构建与检查)的用户授权约定。

也可在 Android Studio 打开预览文件，切换到 **Design** 或 **Split** 并刷新 Preview：

- [AuthPreview.kt](../app/src/main/java/moe/kirakira/feature/auth/AuthPreview.kt) 的 `AuthStepPreview` 覆盖认证各步骤。
- [SecuritySettingsPreview.kt](../app/src/main/java/moe/kirakira/feature/settings/security/SecuritySettingsPreview.kt) 的 `SecurityStepPreview` 覆盖修改邮箱、密码及二步验证各步骤；`TwoFactorEnabledPreview` 分别展示邮箱验证和身份验证器已开启的管理页，`SecuritySettingsPreview` 保留游客态预览。
- [VideoCard.kt](../app/src/main/java/moe/kirakira/ui/components/video/VideoCard.kt) 的 `Video cards` 预览组展示双列网格和单列列表，`Home` 版本省略上传时间。

预览直接向界面传入固定状态，不创建 ViewModel 或访问 API。账号安全预览提供中英文版本；邮箱、绑定时间、设置密钥与一次性代码均为预览专用虚构值，密码和验证码输入框保持为空，流程回调不会发送验证码或提交账号修改。

### 动画性能采样

Android Studio 的 Build Variants 可选择 `performance`，再以 Run 启动；命令行也可使用 `:app:installPerformance`。此变体沿用 Release 代码、依赖和配置，使用 `isDebuggable=false`、`isProfileable=true` 与 R8 优化；Debug 保留日常调试用途。Debug 执行开销会显著放大 Compose 页面首帧的组合、子组合和布局耗时，性能比较应分别记录构建类型，不能把非调试包的结果算作同配置 Debug 优化收益。依据见 [Compose 性能配置](https://developer.android.com/develop/ui/compose/performance#properly-configure)。

性能包包含依赖提供的 Baseline Profiles，不新增采集测试或依赖。Gradle 安装时同时提交 APK 和 `.dm` 编译元数据；安装后的实际 ART 编译状态由设备决定。每次采样仍应使用 `adb shell dumpsys package moe.kirakira` 核对实际 Dexopt 状态，并记录首次进入和重复进入。未预编译的设备也可在应用启动并等待 ProfileInstaller 写入后执行 `adb shell cmd package compile -m speed-profile -f moe.kirakira`，冷启动重录；预编译前后的样本分开报告。Debug 请求编译可能被设备限制为 `verify`，命令返回成功不代表代码已预编译。

`performance` 使用本机默认 Debug 密钥，仅供本地采样，禁用真实系统凭据提供者。它与 Debug 使用相同应用 ID 和签名，互相覆盖安装时保留同一后端的数据；不要将此 APK 作为正式发行包。采样完成后可通过 `:app:installDebug` 恢复调试包。默认检查入口仍是 `:app:assembleDebug`，性能采样只在用户要求时执行。

## GitHub Actions 发版

[Android distribution](../.github/workflows/android-distribution.yml) 使用同一套 Release 构建和签名流程。每次 `main` 推送触发的构建成功后，Releases 中的 [CI build](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/releases/tag/ci) 预发行会更新到最新成功构建。CI 的 APK 文件名包含版本号、运行序号和提交 SHA 的前 7 位，例如 `KIRAKIRA-0.4-ci-12-a659b8b.apk`。每次运行的 APK 和同次生成的 `mapping.txt` 可从 Actions 的 **Artifacts** 下载，保留 90 天。

正式版与 CI 版共享应用 ID 和签名。分发包使用 `3 + GITHUB_RUN_NUMBER` 作为 `versionCode`，其中 3 是已发布的 `0.3` APK 的版本号。CI 的版本名包含运行序号，例如 `0.4-ci.12`。如果手机已安装构建序号更高的 CI，需要等序号更高的正式版才能覆盖更新。两个渠道使用同一个工作流计数。迁移工作流前应确保新计数生成的版本号高于已分发的 APK。

### 首次配置

1. 保护 `main`，要求通过审核的 PR 才能合并。在仓库 **Settings → Rules → Rulesets** 启用独立的 tag 规则集，匹配 `[0-9]*.[0-9]*`，限制创建、更新和删除，仅允许发布维护者绕过。`ci` 不属于正式版本规则；不要给发布工作流绕过正式版本规则的权限。
2. 在仓库 **Settings → Environments** 新建 `android-signing`。选择 **Selected branches and tags**，仅允许 `main` 分支和 `[0-9]*.[0-9]*` 标签。tag 规则集必须覆盖环境允许的全部标签，而不只是当前工作流匹配的两段或三段数字版本。工作流从触发它的 tag 读取，仅限制名称不能阻止未经审核的工作流访问签名密钥。
3. 确认前两项已生效，并核对所有已存在的匹配 tag 都指向经审核进入 `main` 的提交。然后使用发布 `0.3` 的原密钥，仅在该 Environment 中设置以下 Secrets，不要设为仓库级 Secrets。Base64 编码应为一行。

   | Secret | 值 |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | 原 keystore 文件的 Base64 |
   | `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
   | `ANDROID_KEY_ALIAS` | 签名密钥别名 |
   | `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

4. 下载 [0.3 APK](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/releases/tag/0.3)，用 `apksigner verify --print-certs` 核对证书指纹，用 `aapt2 dump badging` 核对应用 ID 和版本号。工作流已固定该 APK 的公开证书 SHA-256 指纹，签名不匹配时该次构建会失败。
5. 确认仓库与组织的 Release 设置允许替换 `ci` 附件，tag 规则允许工作流移动 `ci` tag。发布任务使用内置 `GITHUB_TOKEN`。首次推送 `main` 后，核对 Actions 产物和 CI 预发行中的 APK，再确认预发行记录的提交与 `ci` tag 一致。

### 发布正式版

1. 把 [应用构建配置](../app/build.gradle.kts) 的 `appVersionName` 更新为版本号，例如 `0.4`。推送到 `main` 并等该提交的 CI 完成。
2. 在该提交创建并推送同名 tag。

   ```sh
   git tag -a 0.4 -m "0.4"
   git push origin refs/tags/0.4
   ```

3. 在 Releases 打开生成的草稿，核对 `KIRAKIRA-0.4-release.apk`，补充中文说明并点击 **Publish release**。从对应 Actions 产物长期保存同一 APK 的 `mapping.txt`，可用 APK 的 SHA-256 对照 Release 附件和构建产物。重跑会补齐缺失的草稿附件，保留已有 APK 和说明。

## API 环境配置

默认 API 地址为 `https://rosales.kirakira.moe/`。API 地址和图片环境分别通过 Gradle 属性配置。连接 Rainyun staging 时使用以下命令。

```sh
./gradlew :app:assembleDebug \
  -Pkirakira.apiBaseUrl=https://stg-rainyun-rosales.kirafile.com/ \
  -Pkirakira.imageProvider=cloudflare-stg
```

两项也可在用户级 Gradle 属性中配置。个人环境文件仅在本机使用。`kirakira.apiBaseUrl` 经 HTTPS／host／无凭据／无 query 与 fragment 校验后生成 `BuildConfig.API_BASE_URL`，支持路径前缀，尾部斜杠自动补齐。模拟器访问开发机应使用设备可达、证书可信的 HTTPS 地址，不能把设备的 localhost 当作开发机，也不能关闭证书校验。

`kirakira.imageProvider` 默认为 `cloudflare-prod`，可设为 `cloudflare-stg` 以使用 Cerasus 的 staging 图片分发地址。其他值会在 Gradle 配置阶段报错。图片环境由该属性独立决定。

增加的 `INTERNET` 与播放器媒体音量使用的 `MODIFY_AUDIO_SETTINGS` 均为普通权限，不弹运行时请求。Manifest 禁用明文流量；OkHttp API 客户端禁止重定向、自动连接重试、响应缓存和请求日志，Coil 头像请求使用独立客户端。OkHttp 用于 API 超时、取消、TLS 与 URL 编码，`coil-network-okhttp` 为已有 Coil 添加远程头像支持；复用现有 serialization，无新 DI／数据库框架。Media3 用于真实视频播放，按同一固定版本声明，不升级现有 Compose BOM。播放器使用无账号会话的独立媒体客户端。体积增量未测量，许可证由 AboutLibraries 收集。

非生产 API 根地址构建的 `SYSTEM_CREDENTIALS_ENABLED=false`，禁用真实系统密码提供者；仅完整的默认生产根地址启用。

账号库以 JSON 保存在不参与备份的 `noBackupFilesDir/sessions.json`，正常读写不依赖 Keystore。只有新库主文件及备份都不存在时才尝试迁移旧 `sessions.v1.enc`，首次写入遗留的临时文件清理后可重试；恢复失败保留旧文件，成功写入空新库后提示重新登录。密码和验证码不持久保存。会话与完整 API 根地址绑定；迁移保留原后端的账号数据，切换构建环境时不加载另一后端的账号，首次保存新环境会话才会替换本机旧环境账号库。可选的 userDataBootstrapHint 同样保存在私有账号文件中，仅首页过滤请求使用。协议与已知服务端限制见[API 与会话](implementation/auth.md#api-与会话)。

## 环境排障

依赖解析失败时，先核对 SDK、JDK、仓库网络和错误日志，不要以任意降级依赖作为默认解决方案。

- SDK 缺失：按应用构建配置检查 Platform 及 minor API 是否匹配，并确认本机 `local.properties` 指向正确 SDK。
- Gradle 启动失败：核对 Android Studio 的 Gradle JDK 与 Daemon JDK 配置；Java 11 是源码／字节码兼容级别，不是此项目运行 Gradle 的 JDK 要求。
- 首次同步或下载失败：根据日志区分 Gradle 分发包、Maven 依赖和本机网络问题，修复对应配置后重试。

## 目录结构

```text
app/src/main/
├── java/moe/kirakira/
│   ├── MainActivity.kt       # Activity 宿主
│   ├── KIRAKIRAApp.kt        # 应用入口、应用级状态与导航连接
│   ├── AppDependenciesViewModel.kt # 宿主依赖、共享 API 客户端与历史任务作用域
│   ├── core/network/        # HTTPS 客户端、响应解码、取消和统一失败类型
│   ├── data/                # 认证、内容、历史、资料、安全、图片导出与设置的数据访问
│   ├── feature/             # 主界面、视频、账户、认证与设置等功能
│   │   └── main/MainScreen.kt # 四栏切换、顶栏与底栏
│   └── ui/                  # Expressive 主题、共享组件与导航
│       ├── content/         # 跨功能加载状态与账号请求协调
│       ├── components/video/ # 共享视频卡片、封面、布局类型与格式化
│       └── navigation/
│           ├── AppRoutes.kt  # 页面路由定义
│           ├── AppNavHost.kt # 统一转场、返回保护与播放协调
│           ├── AppNavigationState.kt # 组合内导航协调状态
│           └── *Entries.kt   # 按功能拆分的页面注册
├── res/                     # 英文 / 中文字符串、图标、系统配置
└── AndroidManifest.xml
app/src/test/                 # 本机单元测试
app/src/androidTest/          # Android 与 Compose UI 测试
gradle/                      # Wrapper、JDK 配置和版本目录
docs/                        # 功能、开发、实现索引和认证专项指南
├── implementation/          # 按专题维护的实现说明
└── validation/              # 带日期与验证范围的历史记录
```

后续功能继续按 `feature/<name>/` 组织，详细边界见[贡献指南](../CONTRIBUTING.md#技术选型与架构)。图标来源与许可见 [Cerasus 记录](../third_party/cerasus-icons/README.md)与 [Material Symbols 保留资源](../third_party/material-symbols/README.md)。
