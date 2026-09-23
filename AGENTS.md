# Repository Guidelines

## 适用范围与规范入口

本文件适用于本仓库的开发者和代码代理。开始修改前阅读 [README.md](README.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)。贡献指南是详细规范的唯一维护入口，本文件保留执行摘要；修改规范时同步相关文档、配置和模板。

## 技术栈与项目结构

- 使用 Kotlin 和 Jetpack Compose，设计语言统一为 **Material 3 Expressive**，通过 `KIRAKIRATheme` 使用 Expressive 主题。新页面不使用 XML Layout、Fragment、ViewBinding 或 DataBinding；平台和第三方 View 互操作例外须封装并在 PR 中说明原因。
- 当前仅有 `:app` 模块，包名为 `moe.kirakira`。源码在 `app/src/main/java/moe/kirakira/`，主题在 `ui/theme/`，资源在 `app/src/main/res/`。
- 新功能按 `feature/<name>/` 组织；按需增加 `data/` 和 `core/`，不要预建空层或无需求拆分模块。
- JVM 测试放 `app/src/test/`，设备测试放 `app/src/androidTest/`，测试包结构与被测代码一致。

## 代码与界面规则

- 遵循 `.editorconfig` 和 Kotlin official 风格：四空格、UTF-8、LF；禁用通配符导入，移除无用导入。
- 类型、文件和返回 `Unit` 的 UI composable 用 `PascalCase`；普通函数和属性用 `camelCase`；资源用 `snake_case`。
- 复用 `KIRAKIRATheme`；图标默认使用官方 **Material Symbols Rounded**，必要时可绘制相同风格的自定义矢量图标，记录来源或设计理由，不混用旧版 Material Icons、SF Symbols 或其他图标风格。
- 显示文案使用字符串资源，默认 `values/` 为英语，`values-zh/` 为中文；两套翻译同步维护。可复用 UI 接收状态、事件回调和 `modifier: Modifier = Modifier`。
- 分组菜单使用官方 `SegmentedListItem`，通过 `ListItemDefaults.segmentedShapes` 和 `SegmentedGap` 管理圆角与间距；纯布局使用无 `onClick` 的重载。
- 需要滚动展开大标题的二级页面可复用 `ui/components/CollapsibleTopAppBar.kt` 与 `rememberCollapsibleTopAppBarScrollBehavior`，每页独立创建状态并接入 `nestedScroll`；默认进入折叠。按场景选用，不要求所有页面使用，接入示例见贡献指南。
- 无确定进度的页面加载统一使用 `LoadingIndicator`，不得使用不确定进度的 `CircularProgressIndicator`；有可量化进度的加载可使用确定进度指示器。
- 保持单向数据流；业务状态交由 ViewModel，简单局部 UI 状态可保留在 composable。不得在组合执行体中发起网络请求、写存储或导航。
- 页面导航使用 Navigation 3：`@Serializable` 路由实现 `NavKey`，使用 `rememberNavBackStack`、封装 `NavDisplay` 的 `ui/navigation/ActivityNavDisplay.kt`；普通转场与 AOSP 两阶段预测性返回统一由该宿主管理，页面使用 `NavigationPage`，验证返回栈与页面状态恢复。
- 依赖统一登记在 `gradle/libs.versions.toml`；使用现有 Compose BOM。Material 3 为公开 Expressive API 显式使用 `1.5.0-alpha28`，其余版本例外需说明。

## 检查命令

从仓库根目录执行，Windows 使用 `gradlew.bat`：

- `./gradlew verify`：Debug 构建、JUnit 单元测试与 Android Lint。
- `./gradlew :app:connectedDebugAndroidTest`：设备或模拟器上的 AndroidJUnit4 / Compose UI 测试。
- `./gradlew :app:installDebug`：安装 Debug 应用。

业务逻辑变化补充行为测试；交互变化补充适用的 UI 测试。纯文档修改检查链接与内容即可。没有覆盖率数值门槛；不得将未运行的检查报告为通过。

## 提交与代理工作约定

采用 `type(scope): summary`，例如 `feat(home): add loading state`；这是本项目新增约定，不是从历史推断。PR 使用仓库模板，说明原因、验证结果、关联问题及 UI 截图。

保持变更聚焦，不覆盖他人修改，不顺带升级工具链。不提交生成文件、`local.properties`、凭据或签名密钥。遇到遗留规范差距时优先修正本次涉及的代码；跨模块架构或工具链变化应在 PR 中说明权衡。交付时明确变更、已执行检查及阻塞原因。
