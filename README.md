# KIRAKIRA Compose

KIRAKIRA 的 Android 客户端，使用 **Kotlin + Jetpack Compose**，设计语言为 **Material 3 Expressive**，图标使用官方 **Material Symbols Rounded**。

当前是基础布局原型：底部导航为 **首页（Home）、搜索（Search）、关注（Following）、我（Me）**。首页展示 KIRAKIRA 标题及头像占位，搜索页展示静态 SearchBar，关注页展示标题；“我”包含个人资料、统计、个人主页、历史、收藏和设置占位。支持导航切换及“我 → 设置”的进入和返回。设置页分为“我”“常规”与账户操作，子页面、切换账户和登出均为静态占位，未接入业务。

支持中文和英语，默认跟随系统语言；Android 13+ 可通过系统的应用语言设置切换。“我”页面当前使用占位资料和官方 `SegmentedListItem` 分组菜单，尚未接入真实账号数据。

## 开发环境

| 配置 | 当前仓库值 |
| --- | --- |
| 应用 ID / namespace | `moe.kirakira` |
| Android 最低版本 | API 32 |
| compileSdk / targetSdk | 37 / 37 |
| Gradle Wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin Compose 插件 | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Material 3 Expressive | 1.5.0-alpha28（显式版本例外） |
| Gradle Daemon JDK | 25 |
| Java 源码 / 字节码兼容级别 | 11 |

以上记录仓库配置；版本来源为 `gradle/`、`gradle/libs.versions.toml` 和 `app/build.gradle.kts`。使用支持这些版本的 Android Studio，并安装 Android SDK Platform 37。JDK 25 用于运行 Gradle，与 Java 11 的编译兼容设置用途不同。

Material 3 单独使用公开 Expressive 主题与分段列表 API 的版本，其余 Compose 库仍由 BOM 管理；原因和升级要求见贡献指南。

## 快速开始

1. 使用 Android Studio 打开项目根目录，配置 JDK 25 并同步 Gradle。
2. 在 SDK Manager 中安装项目要求的 SDK；由 IDE 生成本机 `local.properties`，不要提交此文件。
3. 创建 API 32 或更高版本的模拟器，或连接启用 USB 调试的设备。
4. 选择 `app` 运行配置并运行。首次同步需要下载 Gradle 与依赖。

也可以在项目根目录执行以下命令；Windows 将 `./gradlew` 替换为 `gradlew.bat`。

| 命令 | 用途 |
| --- | --- |
| `./gradlew verify` | 提交前统一检查：构建 Debug、单元测试、Android Lint |
| `./gradlew :app:assembleDebug` | 生成 `app/build/outputs/apk/debug/` 下的 APK |
| `./gradlew :app:installDebug` | 安装到设备，随后从设备桌面打开应用 |
| `./gradlew :app:testDebugUnitTest` | 运行本机 JUnit 4 测试 |
| `./gradlew :app:lintDebug` | 运行 Android Lint |
| `./gradlew :app:connectedDebugAndroidTest` | 在设备或模拟器上运行仪器测试 |

`verify` 不包含设备测试，也不检查全部 Kotlin 格式规则。测试和 Lint 报告位于 `app/build/reports/`。依赖解析失败时，先核对 SDK、JDK、仓库网络和错误日志，不要以任意降级依赖作为默认解决方案。

## 目录结构

```text
app/src/main/
├── java/moe/kirakira/
│   ├── MainActivity.kt       # Activity 宿主
│   ├── KIRAKIRAApp.kt        # 四栏导航与顶栏
│   ├── feature/             # 搜索、个人页、设置页布局
│   └── ui/                  # Expressive 主题与共享组件
├── res/                     # 英文 / 中文字符串、Material Symbols、系统配置
└── AndroidManifest.xml
app/src/test/                 # 本机单元测试
app/src/androidTest/          # Android 与 Compose UI 测试
gradle/                      # Wrapper、JDK 配置和版本目录
```

后续功能继续按 `feature/<name>/` 组织，详细边界见贡献指南。官方图标来源与许可见 [Material Symbols 记录](third_party/material-symbols/README.md)。

## 参与开发

- [CONTRIBUTING.md](CONTRIBUTING.md)：技术选型、架构、Compose、代码风格、测试与提交规范。
- [AGENTS.md](AGENTS.md)：开发者与代码代理的执行摘要。
- [.editorconfig](.editorconfig)：编辑器格式配置；在 Android Studio 中启用 EditorConfig 支持。
- [PR 模板](.github/pull_request_template.md)：提交变更时的说明与检查清单。

目前未配置远端 CI、ktlint、Detekt 或覆盖率门槛，检查由贡献者执行并在 PR 中报告。布局测试覆盖中英文导航和选中状态恢复，设备测试需启动模拟器或连接设备。
