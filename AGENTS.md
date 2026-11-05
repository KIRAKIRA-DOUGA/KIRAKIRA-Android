# Repository Guidelines

本文件是代码代理的执行摘要。详细规范只在 [CONTRIBUTING.md](CONTRIBUTING.md) 维护，冲突时以贡献指南为准；功能行为见[功能现状](docs/features.md)，实现细节见[实现索引](docs/implementation.md)，环境、命令与目录见[开发指南](docs/development.md)。修改规范、功能或实现时，按[文档维护分工](CONTRIBUTING.md#文档维护)同步对应文档。

## 项目约束

- Kotlin + Jetpack Compose，单 Activity、单 `:app` 模块，包名 `moe.kirakira`。界面基于 Material 3 Expressive，配 Material Design 2 风格经典强调色、高度阴影与 KIRAKIRA 彩色阴影，复用 `KIRAKIRATheme`。不新增 XML Layout、Fragment、ViewBinding 或 DataBinding 页面。
- 新功能放 `feature/<name>/`，共享 UI 放 `ui/components/`，按需增加 `data/` 与 `core/`，不预建空层。
- 单向数据流：业务状态在 ViewModel，可复用 UI 接收状态与回调，首个可选参数为 `modifier: Modifier = Modifier`；组合执行体中不请求网络、写存储或导航，取消异常继续传播。
- 导航使用 Navigation 3（`@Serializable` + `NavKey`、`ActivityNavDisplay`、`NavigationPage`），页面不重复配置宿主转场。
- 依赖登记在 `gradle/libs.versions.toml`，使用现有 Compose BOM，不顺带升级工具链。
- 代码风格遵循 `.editorconfig` 与 Kotlin official，详见 [Kotlin 风格与命名](CONTRIBUTING.md#kotlin-风格与命名)。

## 界面变更

动手前阅读[Compose 与界面规范](CONTRIBUTING.md#compose-与界面规范)中涉及的小节，优先复用已有共享组件，不手绘替代：

| 涉及内容 | 复用入口 | 详细规范 |
| --- | --- | --- |
| 按钮、FAB、单选框、彩色卡片、危险操作 | `ShadowButtons`、`ShadowFloatingActionButton`、`FollowButton`、`ShadowRadioButton`、`cardColorShadow`、`ShadowButton(destructive = true)` | [按钮、单选框与阴影](CONTRIBUTING.md#按钮单选框与阴影) |
| 列表、分组、菜单、Dialog、数量标题 | 官方 `ListItem`／`SegmentedListItem`、`ConnectedListGroup`、`ConnectedLazyColumn`、`ExpressiveMenuPopup`、`ActionDialog` | [列表、开关与弹层](CONTRIBUTING.md#列表开关与弹层) |
| 账号、认证、资料与隐私表单 | `AccountFlowScaffold`、`FilledTextField`、`PasswordTextField`、`OtpInputField` | [账号表单与保存](CONTRIBUTING.md#账号表单与保存) |
| 加载、刷新与错误 | `IndeterminateCircularProgressIndicator`、`ContentPullToRefresh`、`ContentUnavailableView`、Snackbar（禁止 Toast） | [加载与刷新](CONTRIBUTING.md#加载与刷新) |
| 应用栏与滚动 | `FrostedScaffold` | [毛玻璃应用栏](CONTRIBUTING.md#毛玻璃应用栏) |
| 图标、用户名、文案 | Material Symbols Rounded、`@username`；中英文字符串同步 | [图标与用户名](CONTRIBUTING.md#图标与用户名)、[文案、本地化与无障碍](CONTRIBUTING.md#文案本地化与无障碍) |
| 配色 | `rememberSeedColorScheme`、`MaterialTheme.colorScheme` | [主题配色约束](CONTRIBUTING.md#主题配色约束) |

## 认证、API 与敏感材料

涉及登录、会话或网络时完整阅读[认证状态约束](CONTRIBUTING.md#认证状态约束)和 [API 与会话接入规范](CONTRIBUTING.md#api-与会话接入规范)。不可违反的底线：

- 契约以 Rosales 为准；UI 不接触 DTO、令牌或密码摘要；不自动重放认证写请求。
- 仅邮箱可进入 SavedState；密码、摘要、验证码、TOTP 材料与一次性代码不进入路由、磁盘或日志。
- 不得请求生产后端测试或读取真实凭据；不提交 `local.properties`、凭据或签名密钥。

## 检查与交付

- 代码或构建变更默认只运行 `./gradlew :app:assembleDebug`（Windows 用 `gradlew.bat`）；`:app:installDebug` 仅在用户要求安装时使用。纯文档修改只检查路径、链接与表述，不构建。
- 除非用户明确要求，不新增、修改或运行任何测试，不执行 `verify`、额外 Lint，不启动模拟器、截图或录屏。构建成功只代表编译与打包通过，如实报告已执行的检查。
- 提交格式 `type(scope): summary`，PR 使用[仓库模板](.github/pull_request_template.md)，细则见 [Git 与 Pull Request](CONTRIBUTING.md#git-与-pull-request)。保持变更聚焦，不覆盖他人修改。
