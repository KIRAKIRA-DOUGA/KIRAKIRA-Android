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

Material 3 单独使用公开 Expressive 主题与分段列表 API 的版本，其余 Compose 库仍由 BOM 管理；原因和升级要求见[贡献指南](../CONTRIBUTING.md#依赖与配置)。

## 运行与安装

1. 使用 Android Studio 打开项目根目录，配置 JDK 25 并同步 Gradle。
2. 在 SDK Manager 中安装项目要求的 SDK；由 IDE 生成本机 `local.properties`，不要提交此文件。
3. 创建 API 27 或更高版本的模拟器，或连接启用 USB 调试的设备。
4. 选择 `app` 运行配置并运行。首次同步需要下载 Gradle 与依赖。

也可以在项目根目录执行以下命令；Windows 将 `./gradlew` 替换为 `gradlew.bat`。

| 命令 | 用途 |
| --- | --- |
| `./gradlew :app:assembleDebug` | 默认检查：编译并生成 `app/build/outputs/apk/debug/` 下的 APK，不运行测试 |
| `./gradlew :app:installDebug` | 安装到设备，随后从设备桌面打开应用 |
| `./gradlew :app:assemblePerformance` | 生成非调试、可采样且启用 R8 优化的本地性能 APK，不运行测试 |
| `./gradlew :app:installPerformance` | 安装本地性能 APK，使用默认 Debug 签名与相同应用 ID |
| `./gradlew verify` | 仅在用户明确要求时：构建 Debug、单元测试、Android Lint |
| `./gradlew :app:testDebugUnitTest` | 仅在用户明确要求时：运行本机 JUnit 4 测试 |
| `./gradlew :app:lintDebug` | 仅在用户明确要求时：运行 Android Lint |
| `./gradlew :app:connectedDebugAndroidTest` | 仅在用户明确要求时：在设备或模拟器上运行仪器测试 |

检查政策以[贡献指南](../CONTRIBUTING.md#构建与检查)为准：代码或构建变更默认只执行 Debug 构建，纯文档修改只检查内容和链接。测试、`verify`、额外 Lint 和设备／截图检查仅按用户明确要求执行，不因查阅上述命令而自动运行。

`verify` 在[根构建脚本](../build.gradle.kts)中聚合 Debug 构建、单元测试和 Android Lint，不包含设备测试，也不检查全部 Kotlin 格式规则。测试和 Lint 报告位于 `app/build/reports/`。已有布局测试覆盖中英文导航和选中状态恢复；设备测试需要模拟器或连接的设备。

明确要求认证测试时，可执行 `./gradlew :app:testDebugUnitTest --offline`。认证用例使用内存 HTTP 响应和账号存储，不访问生产服务；协程测试依赖只用于 JVM 测试。覆盖范围、隔离机制和未验证项见[认证离线测试](auth-testing.md)。

Keystore 专项设备测试通过 `-Pkirakira.cryptoCheck=true` 选择 `cryptoCheck` 变体：独立包名 `moe.kirakira.cryptocheck`、无网络权限、无主界面入口，API 地址固定为保留的 `.invalid` 域名。该开关只切换测试构建类型及测试源目录，不修改普通 Debug／Release 的生产环境配置。专项源码位于 `app/src/cryptoCheckAndroidTest/`，不参与默认 Debug 设备测试。构建、安装与运行命令见[认证离线测试](auth-testing.md#keystore-专项设备测试)。

仓库未配置 CI 工作流、ktlint、Detekt 或覆盖率门槛；远端 CI 和分支保护需维护者自行配置。PR 默认报告构建结果，纯文档修改报告内容与链接检查结果。

认证 UI 专项使用 `-Pkirakira.authUiCheck=true`，包名 `moe.kirakira.authuicheck`，无 INTERNET 权限、无生产入口，使用内存 HTTP / Store 和凭据替身。`authUiCheck` 与 `cryptoCheck` 两个开关不能同时启用；两者保留独立测试源目录。共享认证替身在 `app/src/authTestShared/`，不进入生产 APK。命令与结果见[认证专项验证](auth-testing.md#认证体验与系统密码专项)。

### 动画性能采样

Android Studio 的 Build Variants 可选择 `performance`，再以 Run 启动；命令行也可使用 `:app:installPerformance`。此变体沿用 Release 代码、依赖和配置，使用 `isDebuggable=false`、`isProfileable=true` 与 R8 优化；Debug 保留日常调试用途。Debug 执行开销会显著放大 Compose 页面首帧的组合、子组合和布局耗时，性能比较应分别记录构建类型，不能把非调试包的结果算作同配置 Debug 优化收益。依据见 [Compose 性能配置](https://developer.android.com/develop/ui/compose/performance#properly-configure)。

性能包包含依赖提供的 Baseline Profiles，不新增采集测试或依赖。Gradle 安装时同时提交 APK 和 `.dm` 编译元数据；此次 Android 17 实机安装后自动采用了 `speed-profile`，无需手动编译。每次采样仍应使用 `adb shell dumpsys package moe.kirakira` 核对实际 Dexopt 状态，并记录首次进入和重复进入。未预编译的设备也可在应用启动并等待 ProfileInstaller 写入后执行 `adb shell cmd package compile -m speed-profile -f moe.kirakira`，冷启动重录；预编译前后的样本分开报告。Debug 请求编译可能被设备限制为 `verify`，命令返回成功不代表代码已预编译。

`performance` 使用本机默认 Debug 密钥，仅供本地采样，禁用真实系统凭据提供者。它与 Debug 使用相同应用 ID 和签名，互相覆盖安装时保留同一后端的数据；不要将此 APK 作为正式发行包。采样完成后可通过 `:app:installDebug` 恢复调试包。默认检查入口仍是 `:app:assembleDebug`，性能采样只在用户要求时执行。

## API 环境配置

默认正式地址为 `https://rosales.kirakira.moe/`。通过 Gradle 属性覆盖地址，例如连接 Cerasus 开发代理使用的预发布服务：

```sh
./gradlew :app:assembleDebug -Pkirakira.apiBaseUrl=https://stg-rosales.kirafile.com/
```

也可在用户级 Gradle 属性配置 `kirakira.apiBaseUrl`；不要提交个人环境文件。地址经 HTTPS／host／无凭据／无 query 与 fragment 校验后生成 `BuildConfig.API_BASE_URL`，支持路径前缀，尾部斜杠自动补齐。模拟器访问开发机应使用设备可达、证书可信的 HTTPS 地址，不能把设备的 localhost 当作开发机，也不能关闭证书校验。

增加的 `INTERNET` 是普通权限，不弹运行时请求。Manifest 禁用明文流量；OkHttp API 客户端禁止重定向、自动连接重试、响应缓存和请求日志，Coil 头像请求使用独立客户端。OkHttp 用于 API 超时、取消、TLS 与 URL 编码，`coil-network-okhttp` 为已有 Coil 添加远程头像支持；复用现有 serialization，无新 DI／数据库框架。Media3 用于真实视频播放，按同一固定版本声明，不升级现有 Compose BOM。播放器使用无账号会话的独立媒体客户端。体积增量未测量，许可证由 AboutLibraries 收集。

非生产 API 根地址构建的 `SYSTEM_CREDENTIALS_ENABLED=false`，禁用真实系统密码提供者；仅完整的默认生产根地址启用。

账号库保存在不参与备份的 `noBackupFilesDir/sessions.v1.enc`，密码和验证码不持久保存。会话与完整 API 根地址绑定；切换构建环境时不加载另一后端的账号，首次保存新环境会话会替换本机旧环境账号库。可选的 userDataBootstrapHint 同样加密保存，仅首页过滤请求使用。图片分发默认固定生产 Cloudflare 地址，覆盖 API 地址不会自动切换图片环境。协议与已知服务端限制见[API 与会话](implementation.md#api-与会话)。

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
│   ├── core/network/        # HTTPS 客户端、响应解码、取消和统一失败类型
│   ├── data/                # auth 认证会话与 content 视频、资料、评论、弹幕
│   ├── feature/             # 主界面、视频、账户、认证与设置等功能
│   │   └── main/MainScreen.kt # 四栏切换、顶栏与底栏
│   └── ui/                  # Expressive 主题、共享组件与导航
│       └── navigation/
│           ├── AppRoutes.kt  # 页面路由定义
│           └── AppNavHost.kt # 页面注册与进入、返回逻辑
├── res/                     # 英文 / 中文字符串、Material Symbols、系统配置
└── AndroidManifest.xml
app/src/test/                 # 本机单元测试
app/src/androidTest/          # Android 与 Compose UI 测试
gradle/                      # Wrapper、JDK 配置和版本目录
```

后续功能继续按 `feature/<name>/` 组织，详细边界见[贡献指南](../CONTRIBUTING.md#技术选型与架构)。官方图标来源与许可见 [Material Symbols 记录](../third_party/material-symbols/README.md)。
