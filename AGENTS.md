# Repository Guidelines

## 适用范围与规范入口

本文件适用于本仓库的开发者和代码代理。开始修改前阅读 [README.md](README.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)。贡献指南是详细规范的唯一维护入口；本文件保留执行摘要与专题入口，组件参数和例外以贡献指南为准。

功能行为见[功能现状](docs/features.md)，实现细节见[实现索引](docs/implementation.md)，环境、命令与目录见[开发指南](docs/development.md)。修改规范、功能或实现时，按[文档维护分工](CONTRIBUTING.md#文档维护)同步所属专题、摘要、配置和模板。

## 技术栈与代码组织

- Kotlin + Jetpack Compose，设计语言为 **Material 3 Expressive**，复用 `KIRAKIRATheme`。新页面不使用 XML Layout、Fragment、ViewBinding 或 DataBinding；平台／第三方 View 互操作须封装生命周期与资源释放，并在 PR 中说明原因。
- 保留单 Activity 与 `:app` 模块，包名为 `moe.kirakira`。源码在 `app/src/main/java/moe/kirakira/`，主题在 `ui/theme/`，资源在 `app/src/main/res/`。
- 新功能按 `feature/<name>/` 组织，共享 UI 放 `ui/components/`；按需增加 `data/` 与 `core/`，不预建空层或无需求拆分模块。
- 遵循 `.editorconfig` 与 Kotlin official 风格：Kotlin／XML 四空格、UTF-8、LF，禁用通配符导入，移除无用导入。类型、文件和返回 `Unit` 的 UI composable 用 `PascalCase`，普通函数和属性用 `camelCase`，资源用 `snake_case`。
- 保持单向数据流：业务状态交给 ViewModel，局部临时 UI 状态可放 composable。可复用 UI 接收状态、事件回调及第一个可选参数 `modifier: Modifier = Modifier`；不在组合执行体中请求网络、写存储或导航，取消异常继续传播。
- 页面使用 Navigation 3：`@Serializable` 路由实现 `NavKey`，通过 `rememberNavBackStack`、`ActivityNavDisplay` 与 `NavigationPage` 管理普通转场、预测性返回和状态恢复，不在页面重复配置宿主转场。
- 依赖与插件统一登记在 `gradle/libs.versions.toml`，使用现有 Compose BOM；Material 3 的公开 Expressive API 使用显式固定版本例外。文档不重复记录依赖版本，不顺带升级工具链。

## 界面规则入口

实施 UI 变更前，阅读贡献指南中涉及的类别；具体尺寸、时序、颜色、阴影强度及场景例外均在对应章节维护。

| 涉及内容 | 必须遵循的摘要 | 详细规范 |
| --- | --- | --- |
| 按钮、FAB、关注和单选框 | 复用 `ShadowButtons`、`ShadowFloatingActionButton`、`FollowButton` 与 `ShadowRadioButton`；添加 FAB 仅显示加号，保留本地化描述；指定 Expressive 尺寸使用同尺寸官方配套 API；不重复投影或新增阴影开关 | [按钮、单选框与阴影](CONTRIBUTING.md#按钮单选框与阴影) |
| 分组、开关和弹层 | 使用官方 `SegmentedListItem` 与共享连接形状；普通组用 `ConnectedListGroup`，数据列表用 `ConnectedLazyColumn`；普通开关只改变 Switch；弹层背景按实际连接条目决定，空内容用 `surface` | [列表、开关与弹层](CONTRIBUTING.md#列表开关与弹层) |
| 资料、隐私和账号流程 | 资料在顶栏用平面 `TextButton` 保存，保留同步重试与未保存返回确认；隐私选择立即提交，明确失败恢复确认值，结果不确定时暂停编辑并只读核对；重置五项公开须确认；认证与安全流程复用 `AccountFlowScaffold` 和固定底部主按钮；验证步骤直接展示邮箱，保留任务标题及输入标签；六位代码复用 `OtpInputField`，保留字母数字备用码与完整恢复码入口；错误反馈短过渡且不留占位；二步验证采用浅色状态卡与绿色启用标记，代码展示区的辅助操作使用描边按钮；Auth 无关闭按钮 | [账号表单与保存](CONTRIBUTING.md#账号表单与保存) |
| 图标和用户名 | 默认 Material Symbols Rounded，不混用图标风格；普通设置图标裸露显示；独立 username 使用 `@username`、等宽字体和次要文字色，空白不显示，输入与 API 保留原始值 | [图标与用户名](CONTRIBUTING.md#图标与用户名) |
| 文案和预览 | 英语与中文字符串同步维护；短单句标签不加句末标点，保留 `Alpha!` 例外；不内置 Demo，不主动增加功能机制说明；预览优先空表单、游客账号与空列表 | [文案、本地化与无障碍](CONTRIBUTING.md#文案本地化与无障碍) |
| 加载和刷新 | 不确定加载统一使用 `IndeterminateCircularProgressIndicator` 的官方 Material 2 动画与圆角端点；刷新保留已有内容和空状态，复用 `ContentPullToRefresh` 覆盖式指示器；首次加载与分页分别处理 | [加载与刷新](CONTRIBUTING.md#加载与刷新) |
| 栏面和滚动 | 普通栏面固定毛玻璃与阴影，复用 `FrostedScaffold`；栏面背景、alpha 和阴影同层，前景独立；普通栏面不使用原生 backdrop；滚动内容承载系统 Insets，底栏实测高度放入内容 padding，避免重复避让 | [毛玻璃应用栏](CONTRIBUTING.md#毛玻璃应用栏)、[可折叠大标题栏](CONTRIBUTING.md#可选的可折叠大标题栏) |
| 主题配色 | 统一经典强调色，默认项目粉色，壁纸取色可选；选中的可用 Chip 使用 `primary/onPrimary`，其他状态保留官方默认；保留完整语义配色 | [主题配色约束](CONTRIBUTING.md#主题配色约束) |

## API 与敏感材料

遵循[认证状态约束](CONTRIBUTING.md#认证状态约束)和 [API 与会话规范](CONTRIBUTING.md#api-与会话接入规范)：

- 契约对照 Rosales，UI 不接触 DTO、令牌或密码摘要；使用共享 HTTPS 客户端、统一错误与取消处理，请求和结果绑定发起账号，不自动重放认证写请求。
- 会话使用加密原子存储，写入成功后才发布身份；读取失败、密钥缺失或数据损坏时保留原文件并禁止覆盖，不自动创建替代密钥。清除本机账号库须用户明确确认。
- 仅邮箱可进入认证 SavedState；密码、摘要、验证码、TOTP 材料与一次性代码只存流程内存，不进入路由、应用磁盘或日志。
- 认证及本地保存完成后，可经用户确认将原密码交给系统密码管理器；应用不落盘。登录页使用系统自动填充，不主动弹出密码选择器；非生产构建禁用真实凭据网关。

## 检查命令

从仓库根目录执行，Windows 使用 `gradlew.bat`：

- `./gradlew :app:assembleDebug`：代码或构建变更的默认检查，确认编译、资源处理和 Debug 打包成功，不运行测试。
- `./gradlew :app:installDebug`：仅在用户要求安装时使用。
- 纯文档修改只检查路径、链接、命令及表述一致性，不运行构建。

除非用户明确要求，否则不新增、修改或运行单元、回归、UI／设备或截图测试，不执行 `verify`、额外 Lint，不主动启动模拟器人工测试或采集截图、录屏。业务逻辑与交互变化同样遵循此约定，不以补充测试作为交付条件。保留现有测试、依赖和可选检查任务；构建成功仅代表编译与打包通过，不得将未运行检查或历史记录报告为本次通过。

## 提交与交付

采用 `type(scope): summary`，例如 `feat(home): add loading state`；PR 使用[仓库模板](.github/pull_request_template.md)，说明原因、实际检查结果、风险和关联问题。测试结果、UI 截图或录屏仅在用户要求时提供。

保持变更聚焦，不覆盖他人修改，不提交生成文件、`local.properties`、凭据或签名密钥。遗留规范差距优先修正本次涉及的代码；跨模块架构或工具链变化在 PR 中说明权衡。交付明确变更、已执行检查及阻塞原因。
