# Repository Guidelines

## 适用范围与规范入口

本文件适用于本仓库的开发者和代码代理。开始修改前阅读 [README.md](README.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)。贡献指南是详细规范的唯一维护入口，本文件保留执行摘要；修改规范时同步相关文档、配置和模板。

功能行为见[功能现状](docs/features.md)，实现细节与接入示例见[实现说明](docs/implementation.md)，环境、版本及命令见[开发指南](docs/development.md)；专题分工见[文档维护](CONTRIBUTING.md#文档维护)。

## 技术栈与项目结构

- 使用 Kotlin 和 Jetpack Compose，设计语言统一为 **Material 3 Expressive**，通过 `KIRAKIRATheme` 使用 Expressive 主题。新页面不使用 XML Layout、Fragment、ViewBinding 或 DataBinding；平台和第三方 View 互操作例外须封装并在 PR 中说明原因。
- 当前仅有 `:app` 模块，包名为 `moe.kirakira`。源码在 `app/src/main/java/moe/kirakira/`，主题在 `ui/theme/`，资源在 `app/src/main/res/`。
- 新功能按 `feature/<name>/` 组织；按需增加 `data/` 和 `core/`，不要预建空层或无需求拆分模块。
- 现有 JVM 测试位于 `app/src/test/`，设备测试位于 `app/src/androidTest/`；默认不新增或运行测试。

## 代码与界面规则

- 表达「添加」的 FAB 仅显示 Material Symbols Rounded `add` 加号，不附加「添加」文字；使用官方 `FloatingActionButton`，保留本地化无障碍描述与禁用语义，详见贡献指南。
- 遵循 `.editorconfig` 和 Kotlin official 风格：四空格、UTF-8、LF；禁用通配符导入，移除无用导入。
- 类型、文件和返回 `Unit` 的 UI composable 用 `PascalCase`；普通函数和属性用 `camelCase`；资源用 `snake_case`。
- 复用 `KIRAKIRATheme`；图标默认使用官方 **Material Symbols Rounded**，必要时可绘制相同风格的自定义矢量图标，记录来源或设计理由，不混用旧版 Material Icons、SF Symbols 或其他图标风格。
- 界面文案使用字符串资源，默认 `values/` 为英语，`values-zh/` 为中文；两套翻译同步维护。不在应用中内置 Demo 演示内容；设计预览优先使用空表单、游客账号与空列表。按钮、导航、提示和无障碍描述仍需翻译。可复用 UI 接收状态、事件回调和 `modifier: Modifier = Modifier`。
- 除非用户明确要求，界面中不添加解释功能如何运作的说明文字；功能机制与实现细节记录在文档中，界面保留必要的操作标签、状态、错误和无障碍提示。
- 分组菜单使用官方 `SegmentedListItem`，通过 `ListItemDefaults.segmentedShapes` 和 `SegmentedGap` 管理圆角与间距；纯布局使用无 `onClick` 的重载。
- 需要滚动展开大标题的二级页面可复用 `ui/components/CollapsibleTopAppBar.kt` 与 `rememberCollapsibleTopAppBarScrollBehavior`，每页独立创建状态并接入 `nestedScroll`；默认进入折叠。按场景选用，不要求所有页面使用，接入示例见[实现说明](docs/implementation.md#可选的可折叠大标题栏)。
- 滚动页面保留底部 edge-to-edge：底部系统内边距放入滚动内容（`Column` 的 `verticalScroll` 后或 `LazyColumn.contentPadding`），不要用容器外的完整 `innerPadding` 截短滚动区域；确保末项能滚动至导航栏上方。
- 无确定进度的页面加载统一使用 `LoadingIndicator`，不得使用不确定进度的 `CircularProgressIndicator`；有可量化进度的加载可使用确定进度指示器。
- 已有内容刷新时保留内容与布局，不在列表顶部额外插入 `LoadingIndicator`，避免跳动；下拉刷新使用 `ContentPullToRefresh` 的覆盖式指示器，不重复显示列表内加载状态。已加载的空状态在刷新期间也保持显示与占位。首次加载与相邻分页加载分别处理，详见贡献指南的 Compose 与界面规范。
- 保持单向数据流；业务状态交由 ViewModel，简单局部 UI 状态可保留在 composable。不得在组合执行体中发起网络请求、写存储或导航。
- API 接入遵循 [API 与会话规范](CONTRIBUTING.md#api-与会话接入规范)：契约对照 Rosales，UI 不接触 DTO／令牌；HTTPS、统一错误与取消、账号隔离、加密原子存储、禁止敏感日志和自动重放认证请求。仅邮箱可进入认证 SavedState；密码、摘要与验证码仅存流程内存。认证及本地保存完成后可经用户确认交给系统密码管理器；应用不落盘，非生产构建禁用真实凭据提供者。
- 页面导航使用 Navigation 3：`@Serializable` 路由实现 `NavKey`，使用 `rememberNavBackStack`、封装 `NavDisplay` 的 `ui/navigation/ActivityNavDisplay.kt`；普通转场与 AOSP 两阶段预测性返回统一由该宿主管理，页面使用 `NavigationPage`，保持返回栈与页面状态恢复逻辑正确。
- 依赖统一登记在 `gradle/libs.versions.toml`；使用现有 Compose BOM。Material 3 为公开 Expressive API 显式固定版本，具体值以版本目录为准，当前版本表见[开发指南](docs/development.md#开发环境)；其余版本例外需说明。

## 检查命令

从仓库根目录执行，Windows 使用 `gradlew.bat`：

- `./gradlew :app:assembleDebug`：代码或构建变更的默认检查，确认编译、资源处理和 Debug 打包成功，不运行测试。
- `./gradlew :app:installDebug`：用户要求安装时使用。

除非用户明确要求，否则不新增、修改或运行单元测试、回归测试、UI／设备测试、截图测试，不执行 `verify` 或额外的 Lint，也不主动启动模拟器进行人工测试或采集截图。业务逻辑和交互变化同样遵循此约定，不以补充测试作为交付条件。保留现有测试与可选检查命令；纯文档修改只检查链接与内容，不运行构建。构建成功仅表示编译和打包通过，不得将未运行的检查报告为通过。

## 提交与代理工作约定

采用 `type(scope): summary`，例如 `feat(home): add loading state`；这是本项目新增约定，不是从历史推断。PR 使用仓库模板，说明原因、构建结果及关联问题；测试结果、UI 截图或录屏仅在用户要求时提供。

保持变更聚焦，不覆盖他人修改，不顺带升级工具链。不提交生成文件、`local.properties`、凭据或签名密钥。遇到遗留规范差距时优先修正本次涉及的代码；跨模块架构或工具链变化应在 PR 中说明权衡。交付时明确变更、已执行检查及阻塞原因。
