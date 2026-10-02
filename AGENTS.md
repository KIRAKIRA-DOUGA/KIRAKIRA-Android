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
- 实心按钮通过 `ShadowButtons` 封装官方组件，统一使用按压变化的双层 `dropShadow`；彩色投影取实际容器色，中性色使用黑色，禁用时无阴影。普通按钮静止／按压／悬停或聚焦为 2dp／8dp／4dp，FAB 为 6dp／12dp／8dp，按下 120ms、恢复 180ms；形变与阴影共用形状和交互源，不重复投影。文字、描边与裸图标按钮保持平面，发送按钮遵循实心按钮规则，不增加开关，详见贡献指南。
- 指定 Expressive 按钮尺寸时，使用 `ButtonDefaults` 官方尺寸常量及同一尺寸的形状、内边距、文字和图标 API，不手写标准尺寸参数或只放大容器；使用最小高度适应字体缩放，详见贡献指南。
- 遵循 `.editorconfig` 和 Kotlin official 风格：四空格、UTF-8、LF；禁用通配符导入，移除无用导入。
- 类型、文件和返回 `Unit` 的 UI composable 用 `PascalCase`；普通函数和属性用 `camelCase`；资源用 `snake_case`。
- 复用 `KIRAKIRATheme`，统一经典强调色，默认项目粉色，壁纸取色作为可选颜色来源；图标默认使用官方 **Material Symbols Rounded**，必要时可绘制相同风格的自定义矢量图标，记录来源或设计理由，不混用旧版 Material Icons、SF Symbols 或其他图标风格。
- 应用栏、刷新指示器及评论／弹幕输入框的阴影固定开启，不保留开关、状态参数或持久化偏好；应用栏与连接列表复用 `ThemeShadows` 的 elevation 投影，具体规则见贡献指南。
- 普通设置列表前后图标使用裸露的 24dp 图标，不默认添加花形背景；形状图标容器仅用于状态形状切换和独立大图标展示，详见贡献指南。
- 界面文案使用字符串资源，默认 `values/` 为英语，`values-zh/` 为中文；两套翻译同步维护。不在应用中内置 Demo 演示内容；设计预览优先使用空表单、游客账号与空列表。按钮、导航、提示和无障碍描述仍需翻译。可复用 UI 接收状态、事件回调和 `modifier: Modifier = Modifier`。
- 除非用户明确要求，界面中不添加解释功能如何运作的说明文字；功能机制与实现细节记录在文档中，界面保留必要的操作标签、状态、错误和无障碍提示。
- 分组菜单使用官方 `SegmentedListItem`，通过共享 `connectedListItemShapes(index, count)` 统一形状：组内无间隙、无分隔线，仅整组外侧四角保留主题圆角，交互状态不改变形状；普通分组复用 `ConnectedListGroup`，整组固定使用 1dp 阴影；独立条目与懒列表使用 `connectedListItemShadow`，不重复投影或在组内产生阴影接缝。懒列表保留逐项加载，在分组边界设置间距。纯布局使用无 `onClick` 的重载。
- 设置页普通开关仅改变 Switch 状态，不随开启状态改变整行背景与形状；使用 `onClick` 重载并保留开关状态和禁用语义。仅「显示弹幕」等功能总开关使用 `checked` 重载保留整行强调，具体分类见贡献指南。
- 普通顶部栏与主界面胶囊底栏固定使用毛玻璃，复用 `FrostedScaffold` 的独立采样状态与背景，顶栏容器透明、阴影由宿主管理；Android 12+ 使用 Haze 采样，Android 8.1–11 半透明降级。原生 backdrop 暂不启用，规避已复现的转场 alpha 与 elevation 合成异常。特殊媒体与裁剪栏不变，详见贡献指南。
- 需要滚动展开大标题的二级页面可复用 `ui/components/CollapsibleTopAppBar.kt` 与 `rememberCollapsibleTopAppBarScrollBehavior`，每页独立创建状态并接入 `nestedScroll`；默认进入折叠。按场景选用，不要求所有页面使用，接入示例见[实现说明](docs/implementation.md#可选的可折叠大标题栏)。
- 滚动页面保留底部 edge-to-edge：底部系统内边距放入滚动内容（`Column` 的 `verticalScroll` 后或 `LazyColumn.contentPadding`），不要用容器外的完整 `innerPadding` 截短滚动区域；确保末项能滚动至导航栏上方。
- 无确定进度的加载统一复用 `IndeterminateCircularProgressIndicator`，采用官方 Material 2 `CircularProgressIndicator` 的不确定进度动画与 `StrokeCap.Round` 圆角端点，不使用 Material 3 Expressive 形变加载器；普通加载及播放器中央缓冲为 40dp／4dp，文字按钮为 Material 2 官方按钮图标尺寸／2dp，图标按钮、账号行与小窗按钮为 24dp／2dp，下拉刷新内部采用 Material 2 原始比例 20dp／2.5dp。有可量化进度的加载可使用确定进度指示器。
- 已有内容刷新时保留内容与布局，不在列表顶部额外插入加载指示器，避免跳动；下拉刷新使用 `ContentPullToRefresh` 的覆盖式指示器，拉动反馈遵循 Material 2 的圆弧旋转、阈值透明度与超拉阻尼，箭头复用 Material Symbols Rounded，不重复显示列表内加载状态。已加载的空状态在刷新期间也保持显示与占位。首次加载与相邻分页加载分别处理，详见贡献指南的 Compose 与界面规范。
- 保持单向数据流；业务状态交由 ViewModel，简单局部 UI 状态可保留在 composable。不得在组合执行体中发起网络请求、写存储或导航。
- API 接入遵循 [API 与会话规范](CONTRIBUTING.md#api-与会话接入规范)：契约对照 Rosales，UI 不接触 DTO／令牌；HTTPS、统一错误与取消、账号隔离、加密原子存储、禁止敏感日志和自动重放认证请求。仅邮箱可进入认证 SavedState；密码、摘要与验证码仅存流程内存。认证及本地保存完成后可经用户确认交给系统密码管理器；应用不落盘，非生产构建禁用真实凭据提供者。
- 页面导航使用 Navigation 3：`@Serializable` 路由实现 `NavKey`，使用 `rememberNavBackStack`、封装 `NavDisplay` 的 `ui/navigation/ActivityNavDisplay.kt`；普通转场与 AOSP 两阶段预测性返回统一由该宿主管理，页面使用 `NavigationPage`，保持返回栈与页面状态恢复逻辑正确。
- 依赖统一登记在 `gradle/libs.versions.toml`，文档不重复记录依赖与插件的具体版本号；使用现有 Compose BOM。Material 3 为公开 Expressive API 显式固定版本，具体值以版本目录为准；其余版本例外需说明。

## 检查命令

从仓库根目录执行，Windows 使用 `gradlew.bat`：

- `./gradlew :app:assembleDebug`：代码或构建变更的默认检查，确认编译、资源处理和 Debug 打包成功，不运行测试。
- `./gradlew :app:installDebug`：用户要求安装时使用。

除非用户明确要求，否则不新增、修改或运行单元测试、回归测试、UI／设备测试、截图测试，不执行 `verify` 或额外的 Lint，也不主动启动模拟器进行人工测试或采集截图。业务逻辑和交互变化同样遵循此约定，不以补充测试作为交付条件。保留现有测试与可选检查命令；纯文档修改只检查链接与内容，不运行构建。构建成功仅表示编译和打包通过，不得将未运行的检查报告为通过。

## 提交与代理工作约定

采用 `type(scope): summary`，例如 `feat(home): add loading state`；这是本项目新增约定，不是从历史推断。PR 使用仓库模板，说明原因、构建结果及关联问题；测试结果、UI 截图或录屏仅在用户要求时提供。

保持变更聚焦，不覆盖他人修改，不顺带升级工具链。不提交生成文件、`local.properties`、凭据或签名密钥。遇到遗留规范差距时优先修正本次涉及的代码；跨模块架构或工具链变化应在 PR 中说明权衡。交付时明确变更、已执行检查及阻塞原因。
