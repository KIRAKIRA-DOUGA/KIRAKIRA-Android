# Repository Guidelines

本文件是代码代理的执行摘要，冲突时以所指向的文档为准。

| 要找什么 | 文档 |
| --- | --- |
| 架构、代码风格、认证与 API 约束、检查和提交政策 | [CONTRIBUTING.md](CONTRIBUTING.md) |
| 必须遵守的界面规则 | [界面规范](docs/ui-guidelines.md) |
| 页面现在的行为、保存范围与限制 | [功能现状](docs/features.md) |
| 状态归属、取舍理由、平台限制、接口契约 | [实现说明](docs/implementation.md) |
| 环境、命令与目录 | [开发指南](docs/development.md) |

修改规范、功能或实现时，按[文档维护](CONTRIBUTING.md#文档维护)同步对应的文档，并遵守其中的写法：只写代码读不出来的内容，改写所属段落而不是在文末追加。

## 项目约束

- Kotlin + Jetpack Compose，单 Activity、单 `:app` 模块，包名 `moe.kirakira`，源码位于各源集的 `kotlin/` 目录。不新增 XML Layout、Fragment、ViewBinding 或 DataBinding 页面。
- 新功能放 `feature/<name>/`，共享 UI 放 `ui/components/<组件族>/`，路由与页面注册放 `navigation/`，按需增加 `data/` 与 `core/`，不预建空层。
- 依赖方向为 `navigation` → `feature` → `ui`／`data` → `core`。`ui` 不依赖 `feature`；`feature` 和 `ui` 不引用 `moe.kirakira.navigation`。
- 单向数据流：业务状态在 ViewModel，可复用 UI 接收状态与回调，第一个可选参数是 `modifier: Modifier = Modifier`。组合执行体中不请求网络、写存储或导航；取消异常继续传播。
- 导航使用 Navigation 3（`@Serializable` 的 `NavKey`、`ActivityNavDisplay`、`NavigationPage`），页面不重复配置宿主的转场。
- 依赖登记在 `gradle/libs.versions.toml`，使用现有的 Compose BOM，不顺带升级工具链。
- 代码风格遵循 `.editorconfig` 和 Kotlin 官方风格，见 [Kotlin 风格与命名](CONTRIBUTING.md#kotlin-风格与命名)。

## 界面变更

界面基于 Material 3 Expressive，配色为 Material Design 2 风格的经典强调色，阴影为高度投影加 KIRAKIRA 彩色阴影，统一复用 `KIRAKIRATheme`；动画曲线取自 `MaterialTheme.motionScheme`。

动手前阅读[界面规范](docs/ui-guidelines.md)中涉及的小节。优先复用已有的共享组件，不手绘替代：

| 涉及内容 | 复用入口 | 规范 |
| --- | --- | --- |
| 按钮、FAB、单选框、彩色卡片、危险操作 | `ShadowButtons`、`ShadowFloatingActionButton`、`FollowButton`、`ShadowRadioButton`、`cardColorShadow`、`ShadowButton(destructive = true)` | [按钮、单选框与阴影](docs/ui-guidelines.md#按钮单选框与阴影) |
| 带滑动指示块的分段单选 | `Segmented` | 同上 |
| 列表、分组、菜单、对话框、数量标题 | 官方 `ListItem`／`SegmentedListItem`、`ConnectedListGroup`、`ConnectedLazyColumn`、`MenuPopup`（组间用 `MenuDivider`）、`ActionDialog`、`DestructiveActionIcon` | [列表、开关与弹层](docs/ui-guidelines.md#列表开关与弹层) |
| 账号、认证、资料与隐私表单 | `AccountFlowScaffold`、`FilledTextField`、`PasswordTextField`、`OtpInputField` | [账号表单与保存](docs/ui-guidelines.md#账号表单与保存) |
| 加载、刷新与错误 | `IndeterminateCircularProgressIndicator`、`ContentPullToRefresh`、`ContentUnavailableView`、Snackbar（禁止 Toast）；按钮用 `loading` 参数显示进度 | [加载与刷新](docs/ui-guidelines.md#加载与刷新) |
| 应用栏与滚动 | `FrostedScaffold`、`PageTopAppBar` | [毛玻璃应用栏](docs/ui-guidelines.md#毛玻璃应用栏)、[固定页面顶栏](docs/ui-guidelines.md#固定页面顶栏) |
| 图标、用户名、文案 | 优先使用 Cerasus 图标；错误图标用主题 `error`；用户名显示为 `@username`；英语、简体中文、繁体中文和日语四套字符串同步 | [图标与用户名](docs/ui-guidelines.md#图标与用户名)、[文案、本地化与无障碍](docs/ui-guidelines.md#文案本地化与无障碍) |
| 配色、输入框与 Chip 的底色 | `rememberSeedColorScheme`、`MaterialTheme.colorScheme`、`ThemeColorDefaults.controlContainerColor`、`AppChipDefaults` | [主题配色约束](docs/ui-guidelines.md#主题配色约束) |

组件的接入方式和设计原因见[共享 UI 组件](docs/implementation/components.md)。

## 认证、API 与敏感材料

涉及登录、会话或网络时，完整阅读[认证状态约束](CONTRIBUTING.md#认证状态约束)和 [API 与会话接入规范](CONTRIBUTING.md#api-与会话接入规范)。不可违反的底线：

- 接口契约以 Rosales 为准；UI 不接触 DTO、令牌或密码摘要；认证写请求不自动重放。
- 只有邮箱可以进入 SavedState；密码、摘要、验证码、TOTP 材料和一次性代码不进入路由、磁盘或日志。
- 不得请求生产后端做测试，不得读取真实凭据；不提交 `local.properties`、凭据或签名密钥。
- 不改动已保存数据的格式和位置（偏好文件名与键名、`sessions.json`、`applicationId`、`MainActivity` 的组件名），除非用户明确同意迁移。

## 检查与交付

- 代码或构建变更默认只运行 `./gradlew :app:assembleDebug`（Windows 用 `gradlew.bat`）；`:app:installDebug` 只在用户要求安装时使用。纯文档修改只检查路径、链接与表述，不构建。
- 除非用户明确要求，不新增、修改或运行任何测试，不执行 `verify` 和额外的 Lint，不启动模拟器、截图或录屏。
- 测试的位置：JVM 测试和测试替身在 `src/test/`，设备测试在 `src/androidTest/`；包结构与被测源码一致。
- 构建成功只代表编译与打包通过。如实报告实际执行过的检查。
- 提交格式为 `type(scope): summary`，PR 使用[仓库模板](.github/pull_request_template.md)，细则见 [Git 与 Pull Request](CONTRIBUTING.md#git-与-pull-request)。保持变更聚焦，不覆盖他人的修改。
