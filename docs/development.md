# 开发指南

[返回项目首页](../README.md) · [贡献规范](../CONTRIBUTING.md) · [实现说明](implementation.md)

本文维护开发环境、运行方法与目录说明。

## 开发环境

| 配置 | 当前仓库值 |
| --- | --- |
| 应用 ID / namespace | `moe.kirakira` |
| Android 最低版本 | API 27 |
| compileSdk / targetSdk | 37（minor API 2）/ 37 |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin Compose 插件 | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Material 3 Expressive | 1.5.0-alpha29（显式版本例外） |
| Navigation 3 | 1.2.0（runtime / ui） |
| Lifecycle | 2.11.0 |
| AboutLibraries | 15.2.0 |
| MaterialKolor | 5.0.1 |
| colorpicker-compose | 1.3.0 |
| Gradle Daemon JDK | 25 |
| Java 源码 / 字节码兼容级别 | 11 |

以上记录仓库配置，不代表上游最新版本。版本来源为[版本目录](../gradle/libs.versions.toml)、[应用构建配置](../app/build.gradle.kts)、[Gradle Wrapper](../gradle/wrapper/gradle-wrapper.properties) 与 [Daemon JDK 配置](../gradle/gradle-daemon-jvm.properties)。使用支持这些版本的 Android Studio，并安装匹配 compileSdk 37、minor API 2 的 Android SDK Platform。JDK 25 用于运行 Gradle，与 Java 11 的编译兼容设置用途不同。

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
| `./gradlew verify` | 仅在用户明确要求时：构建 Debug、单元测试、Android Lint |
| `./gradlew :app:testDebugUnitTest` | 仅在用户明确要求时：运行本机 JUnit 4 测试 |
| `./gradlew :app:lintDebug` | 仅在用户明确要求时：运行 Android Lint |
| `./gradlew :app:connectedDebugAndroidTest` | 仅在用户明确要求时：在设备或模拟器上运行仪器测试 |

检查政策以[贡献指南](../CONTRIBUTING.md#构建与检查)为准：代码或构建变更默认只执行 Debug 构建，纯文档修改只检查内容和链接。测试、`verify`、额外 Lint 和设备／截图检查仅按用户明确要求执行，不因查阅上述命令而自动运行。

`verify` 在[根构建脚本](../build.gradle.kts)中聚合 Debug 构建、单元测试和 Android Lint，不包含设备测试，也不检查全部 Kotlin 格式规则。测试和 Lint 报告位于 `app/build/reports/`。已有布局测试覆盖中英文导航和选中状态恢复；设备测试需要模拟器或连接的设备。

仓库未配置 CI 工作流、ktlint、Detekt 或覆盖率门槛；远端 CI 和分支保护需维护者自行配置。PR 默认报告构建结果，纯文档修改报告内容与链接检查结果。

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

