# 贡献指南

本规范适用于人工开发与代码代理。文中的“必须 / 不得”是提交要求，“建议 / 优先”允许结合需求判断；例外应在 PR 中记录原因、影响及验证方式。配置变更应同步文档，分工见[文档维护](#文档维护)。既有模板不作为新代码的风格标准，也不要求每次提交清理整个项目。

## 技术选型与架构

- 新应用代码使用 Kotlin，Gradle 使用 Kotlin DSL。新界面统一使用 Jetpack Compose，设计语言为 **Material 3 Expressive**；复用 `KIRAKIRATheme` 中的 `MaterialExpressiveTheme`，包括颜色、形状、排版和动效体系。
- 不新增以 XML Layout、Fragment、ViewBinding 或 DataBinding 为核心的页面。只有平台能力或第三方 SDK 需要时使用 `AndroidView` 等互操作，并封装生命周期与资源释放。Manifest、图标、字符串和系统主题 XML 仍正常使用。
- 保持单 Activity 入口；`MainActivity` 负责宿主和顶层组合，不承载网络、数据库或业务规则。
- 保留当前 `:app` 模块。新功能放在 `moe.kirakira.feature.<name>`；跨功能 UI 放 `ui/components/`，主题放 `ui/theme/`，数据访问按需放 `data/`，真正跨功能的基础设施放 `core/`。包名全小写，不将无关代码堆入 `Utils.kt`。
- 页面展示状态并上报事件；涉及业务逻辑的屏幕由 ViewModel 管理状态，通过 Repository 隔离数据访问。复杂且可复用的业务规则才抽取 use case；不强制为简单操作增加层次。
- 页面导航使用 Navigation 3，路由实现 `NavKey` 并标注 `@Serializable`，通过 `rememberNavBackStack` 和封装 `NavDisplay` 的 `ui/navigation/ActivityNavDisplay.kt` 管理返回栈及状态恢复，页面使用 `NavigationPage`。普通转场复用 `NavigationMotion.kt`，预测性返回由宿主统一管理，不在页面重复配置或叠加 NavDisplay 默认的预测性返回补间。预览与收尾保留双页面内容，完成后仅出栈一次，取消时复原；手势期间页面生命周期不高于 STARTED。导航由页面事件回调触发，保留根页面，避免连续点击重复入栈或出栈。底栏作为主界面内的局部状态，不预建多返回栈架构。接入关系见[实现说明](docs/implementation.md#导航与状态管理)，参数来源与公开 API 适配差异见 [Android 转场说明](third_party/android-motion/README.md)。
- 主题、会话与认证状态使用 ViewModel；当前数据层为 `data/auth`，网络基础设施为 `core/network`，通过构造参数显式传递依赖，暂不引入 DI 框架。新增库时说明具体需求，先复用已有能力，避免为了符合目录示意而添加空实现。

## Kotlin 风格与命名

以仓库 `.editorconfig` 和 [Kotlin 官方风格](https://kotlinlang.org/docs/coding-conventions.html) 为基础。提交前在 Android Studio 对修改的代码执行 Reformat Code 和 Optimize Imports。

| 对象 | 约定与示例 |
| --- | --- |
| 类、接口、文件 | `PascalCase`，例如 `HomeScreen.kt`、`HomeViewModel.kt` |
| 返回 `Unit` 的 UI composable | `PascalCase`，例如 `FavoriteButton` |
| 普通函数、属性、返回值的 composable | `camelCase`，例如 `loadFavorites`、`rememberHomeState` |
| 常量、枚举项 | `UPPER_SNAKE_CASE`，例如 `PAGE_SIZE`、`HOME` |
| Android 资源 | `snake_case`，例如 `home_title`、`ic_favorite` |
| 测试 | `<Subject>Test`；方法用 `action_condition_expectedResult` |

Kotlin 和 XML 使用四空格、UTF-8、LF 和文件末尾换行；建议行宽不超过 120。Markdown、YAML、JSON 和 TOML 使用两空格缩进，Windows `.bat` 保留 CRLF。禁用通配符导入，删除无用导入，多行参数使用尾随逗号。优先 `val`、不可变数据和明确的空值处理，不用 `!!` 掩盖状态或接口问题。公共 API 的约束、复杂逻辑及原因用 KDoc 或注释说明，避免复述代码。代码标识符使用英文，文档和解释性注释可使用中文。

## Compose 与界面规范

- 表达「添加」的悬浮操作按钮（FAB）通过共享 `ShadowFloatingActionButton` 封装官方 `FloatingActionButton`，默认使用 `CircleShape`、`MaterialTheme.colorScheme.primary` 背景与配套 `onPrimary` 前景；允许显式覆盖形状和颜色，前景默认通过 `contentColorFor(containerColor)` 匹配。仅显示 Material Symbols Rounded `add` 加号，不在图标右侧重复显示「添加」文字，不使用带文字的扩展 FAB。保留官方尺寸、本地化的「添加」无障碍描述、标准触摸目标与操作期间的禁用语义。
- 使用 Material 3 Expressive 的组件、色彩层次和圆角分组；保持浅色、深色及动态颜色兼容，不用手绘控件替代已有标准组件。参考 [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)。
- 实心按钮统一使用 `ShadowButtons` 中的共享封装，内部保留官方填充／浅色填充按钮、实心图标按钮与 FAB。普通按钮静止／按压／悬停或聚焦的阴影等效高度为 2dp／8dp／4dp，FAB 为 6dp／12dp／8dp；按下 120ms、恢复 180ms，禁用时无阴影。使用双层 `dropShadow`，模糊、偏移和扩张采用 Material 官方双层阴影参数，按现有交互高度在官方 elevation level 间插值。彩色投影取实际容器色，不透明度为显式例外：所有彩色按钮与 FAB 的环境层／主层固定为 25%／50%，不随交互状态改变；中性色使用黑色及官方 15%／30%，两类均乘以容器透明度。形变与阴影共用动画形状及交互源，不叠加原生投影或外层投影，不改变触摸区域、布局尺寸和业务状态。文字、描边与裸图标按钮保持平面，不提供阴影开关或持久化偏好。发送按钮遵循实心按钮规则，输入框仍保留固定阴影；参数来源及接入方式见[按钮动态阴影](docs/implementation.md#按钮动态阴影)。
- 单选框统一使用 `ShadowRadioButton` 封装官方 `RadioButton`，仅选中且可用时绘制双层彩色阴影。静止／悬停或聚焦／按压高度为 1dp／2dp／4dp，按压优先，按下 120ms、恢复 180ms；禁用时立即移除阴影。几何参数与实心按钮共用 Material 官方映射和插值，1dp 的环境层／主层「垂直偏移／模糊／扩张」分别为 1／3／1dp 与 1／2／0dp。阴影取实际选中颜色，强度为按钮的一半：彩色环境层／主层为 12.5%／25%，中性色使用黑色及 7.5%／15%，均乘以选中颜色透明度。投影仅位于居中的 20dp 可见圆圈外侧，不染色内部空白，不改变官方布局、触摸目标、涟漪、选中动画及语义；整行处理点击时，行与单选框共用显式交互源，单选框 `onClick = null`。不增加阴影开关或持久化偏好，接入方式见[单选框动态阴影](docs/implementation.md#单选框动态阴影)。
- 指定 Expressive 按钮尺寸时，使用 `ButtonDefaults` 的官方尺寸常量（如 `MediumContainerHeight`），将同一尺寸传给 `shapesFor`、`contentPaddingFor` 与 `textStyleFor`；有图标时同步使用 `iconSizeFor` 和 `iconSpacingFor`，并向内边距 API 传入对应的前后图标标记。不得手写标准尺寸的高度、圆角、内边距或字号，也不得只放大容器而保留默认小字号；最小高度使用 `heightIn(min = …)`，允许内容随字体缩放增高。
- 资料保存、隐私应用及账号安全流程的主操作使用固定底部的 `SettingsActionBar`，通过 `SettingsScaffold.bottomBar` 接入，只显示一个靠内容区域尾侧对齐的普通实心按钮，不使用 FAB 或悬浮工具栏；参考 [Material 按钮指南](https://m3.material.io/components/buttons/guidelines)。操作区使用 `surface`，沿用设置页 640dp 最大内容宽度及 16dp 间距；按钮使用官方 Medium 尺寸配套 API，默认仅显示单行文字，加载时复用圆形进度指示器，保留本项目双层彩色阴影这一相对官方平面实心样式的定制。底栏负责系统安全区域，宿主负责 IME 避让；底栏实测高度随滚动内容底部 padding 保留，Snackbar 交由 Scaffold 避让，不硬编码额外留白。隐私重置放在顶栏，取消沿用返回及舍弃保护；头像裁剪保留底部旋转与完成操作栏。接入说明见[设置表单操作区](docs/implementation.md#设置表单操作区)。
- 个人主页与视频页统一使用共享 `FollowButton`，内部保留官方 `ToggleButton`。未关注时使用 `primary/onPrimary` 与共享按钮的双层投影；已关注时使用灰色 `surfaceContainerHigh/onSurfaceVariant`，作为实心按钮阴影规则的例外不绘制投影，悬停、聚焦和按压也不额外添加原生阴影。保留官方尺寸、选中语义、禁用配色及选中／按压形变，表面与投影共用动画轮廓和交互源；禁用时无阴影，关注状态仍由原有业务流程拥有。
- 邀请码顶部统计块的生成按钮作为实心按钮阴影规则的例外，直接使用官方 `Button` 并设置 `elevation = null`，静止、按压、悬停、聚焦及禁用时均不绘制阴影。浅深主题中可用状态均固定白色容器与主题 `primary` 文字、图标，禁用配色沿用统计块的低透明度前景色；保留官方 Expressive 尺寸配套 API、按压形变、点击反馈和禁用语义，不增加阴影开关。
- 分组菜单直接使用官方 `SegmentedListItem`，通过共享 `connectedListItemShapes(index, count)` 处理首尾与单项主题圆角：组内无间隙、无分隔线，中间连接边为直角；选中、按压、聚焦、悬停与拖动沿用基础形状，保留官方配色、点击反馈及内容内边距。普通 `Column` 分组复用 `ConnectedListGroup`，它提供满宽、零间距布局及固定的 1dp 整组阴影，不额外添加背景或内边距，默认不裁剪内容；独立条目在调用处显式包裹单项分组，`SettingsSection` 内部复用该容器，账号侧滑组通过 `clipContent` 按整体圆角裁剪，裁剪不截断外侧阴影。懒列表使用 `ConnectedLazyColumn`，通过 `connectedItemsIndexed` 同步声明稳定分组标识及逐项内容，由宿主根据可见行统一绘制每组的 1dp 平台 elevation 阴影；条目只负责内容、形状与交互，不添加行级投影、不延伸和拼接逐行阴影、不通过组合局部上下文判断阴影归属。固定少量菜单可作为普通分组放入一个懒列表条目；可能增长的数据列表保留逐项懒加载、稳定 key、contentType 与分页。宿主仅支持正向垂直列表，连接行必须满宽，不加外部纵向 padding 或位移动画；在标题、分组边界及独立状态区域设置间距，不使用 `SegmentedGap` 分隔组内条目，不新增阴影开关或持久化偏好。侧滑删除按钮的横向间距不受此规则影响。不以整组 `Surface` 模拟列表项。静态占位使用无 `onClick` 的重载；无副标题时传入空的 `supportingContent`，不要提供空内容 lambda。
- 设置页中，普通开关仅通过 Switch 表达开启状态，整行背景和形状不随开启状态变化；使用 `SegmentedListItem` 的 `onClick` 重载切换状态，并提供 `Role.Switch` 与 `toggleableState` 语义，尾部 Switch 的 `onCheckedChange` 为 `null`，保留禁用状态和官方按压反馈。仅功能总开关（如「显示弹幕」）使用 `checked` 重载保留整行强调样式；「应用内小窗播放」「应用外小窗播放」「自动播放」和各弹幕模式均属于普通开关。
- 普通对话框、底部 Sheet、下拉菜单、日期选择和开源组件详情弹层的背景按当前内容决定：包含至少一个连接列表条目时保留现有灰色，业务 Sheet 使用 `surfaceContainerLow`；没有连接条目时使用 `surface`，浅色为白色，深色跟随主题。调用处根据实际条目和当前子页直接选择颜色，不探测组件树，不新增配色包装、动画或持久化设置。空列表、首次无数据加载与错误占位使用 `surface`；刷新保留已有条目时仍用灰色，滚动出可视区域不改变背景。日期选择器内外容器同步设置背景，第三方详情使用公开颜色参数；列表项、输入框、选中态与媒体查看器保留各自语义配色，接入说明见[弹层背景](docs/implementation.md#弹层背景)。
- 应用栏、刷新指示器及评论／弹幕输入框的阴影固定开启，不提供关闭入口，不传递可变阴影状态或保存阴影偏好。应用栏复用 `ThemeShadows`，保留底边裁剪与全屏／画中画的绘制边界；高度与承载方式见[栏面阴影](docs/implementation.md#栏面阴影)。经典强调色继续关闭色调高度叠加，阴影不改变语义配色。
- 图标采用官方 **Material Symbols**，默认统一为 **Rounded、24dp 资源画布、wght 400、GRAD 0、FILL 0**；实际显示尺寸按组件规范设置，Expressive 列表前后图标使用 24dp，按钮、工具栏、FAB 和输入框遵循各自组件尺寸。选中状态如使用填充图标应保持其他参数一致。按需导入 Android VectorDrawable，不打包完整字体或旧版 `material-icons-extended`。
- 普通设置列表前后图标使用裸露的 24dp 图标（包括尾部箭头与列表内操作图标），继承官方列表内容颜色，不默认添加花形背景或裁切容器；危险操作使用主题错误色，禁用样式遵循列表组件。MaterialShapes 图标容器仅用于随状态切换形状的表达（如隐私可见性、邀请码使用状态）与独立大图标展示（如安全状态横幅、流程图标和邀请码统计）。
- 必要时可自行绘制相同风格的图标：保持 24dp 画布、相近视觉重量、圆角和光学对齐，检查浅深色与小尺寸可读性，在 PR 中说明缺少合适标准图标的原因。官方资源使用 `ic_symbol_<name>`，自绘资源使用 `ic_custom_<name>`，保留来源及许可证记录于 `third_party/`。不得混用 SF Symbols、旧版 Material Icons 或不一致的描边风格。参见 [Material Symbols 指南](https://developers.google.com/fonts/docs/material_symbols)。
- 使用单向数据流：状态向下传递，事件通过回调向上传递。可复用组件接收所需状态和回调，不直接获取 ViewModel、Repository 或导航控制器。
- 可复用 UI 的第一个可选参数使用 `modifier: Modifier = Modifier`，作用于组件根节点；内容插槽放最后。[参数示例](docs/implementation.md#可复用-ui-参数示例)见实现说明。
- 按[状态提升原则](https://developer.android.com/develop/ui/compose/state-hoisting)选择状态所有者：局部临时状态用 `remember`；需要恢复且可保存的 UI 状态用 `rememberSaveable`；业务状态由 ViewModel 暴露只读状态。采用 Flow 时，在屏幕入口通过 `collectAsStateWithLifecycle` 收集，并先在版本目录声明所需依赖。
- 不在组合执行体中直接请求网络、写存储或执行导航。用户操作在事件回调中触发；与组合生命周期相关的工作使用具有正确 key 的 `LaunchedEffect` / `DisposableEffect`，并清理监听器。参见[副作用指南](https://developer.android.com/develop/ui/compose/side-effects)。
- 网络和磁盘工作不得阻塞主线程；使用结构化并发，禁止 `GlobalScope`。捕获取消异常时必须继续传播取消。
- 通过 `KIRAKIRATheme` 和 `MaterialTheme` 复用颜色、字体及形状；避免在业务页面散落品牌颜色。布局使用 `dp`，字体使用 `sp`，通用设计值按实际复用需求提取。
- 独立展示用户名（username）时统一使用 `@username`、`FontFamily.Monospace` 等宽字体和 `MaterialTheme.colorScheme.onSurfaceVariant` 次要文字颜色，字号沿用所在组件的排版层级；适用于个人主页、“我”、视频页上传者、评论作者、历史记录作者、关注／粉丝列表及资料预览。空白 username 不展示文字行。`@` 仅在展示层添加，输入框、存储及 API 参数保留原始 username；昵称或显示名称回退使用 username 时仍沿用名称样式，不套用独立 username 样式。
- 新界面文案必须放入字符串资源，包含错误、导航标题和无障碍描述；使用格式化资源和 plurals，不拼接可翻译句子。
- 除非用户明确要求，界面中不添加解释功能如何运作的说明文字；功能机制与实现细节记录在相应文档中。界面保留必要的操作标签、状态反馈、错误信息和无障碍提示，不主动加入机制说明或实现细节。
- 当前支持英语与中文：`res/values/strings.xml` 是完整英语界面回退资源，`res/values-zh/strings.xml` 提供中文；新增可翻译 key 必须同时补齐两套文案。品牌名标记 `translatable="false"`。跟随系统语言，Android 13+ 通过 `res/xml/locales_config.xml` 声明应用语言；新增语言同步配置并检查长文案和字体缩放。
- 不在应用中内置 Demo 账号、评论、弹幕、视频或邀请码等演示内容。设计预览优先使用空表单、游客账号与空列表，不依赖真实服务。按钮、导航标题、输入提示、功能待接入提示、数量标签和无障碍描述仍须维护中英文翻译。
- 交互控件提供语义与可读标签，纯装饰图标使用空描述；保证触摸目标、字体缩放和 TalkBack 可用。实现时兼顾浅色、深色、窄屏和宽屏，使用 `start/end` 方向及系统 Insets，避免用固定屏幕尺寸布局；不因此默认增加设备或截图测试。
- 列表项目使用稳定的业务 key；不要为了压制重组而随意添加 `@Stable` / `@Immutable`。可复用组件提供使用假数据的 Preview，不依赖真实服务或运行中的 ViewModel。
- 异步页面明确表达加载、成功、空内容与失败状态；重试入口应与操作语义一致。
- 无确定进度的加载统一复用 `IndeterminateCircularProgressIndicator`，封装官方 Material 2 `CircularProgressIndicator` 的不确定进度重载；这是 Material 3 Expressive 界面中的加载器例外，不引入 Material 2 主题，不使用 Expressive 形变加载器或 Material 3 圆形加载动画。圆弧端点固定使用 `StrokeCap.Round`，轨道透明，默认颜色来自当前 Material 3 主题 `primary`，媒体区域保留白色。
- 加载器尺寸按场景适配：普通页面、图片及分页加载和播放器中央缓冲为 40dp、官方默认 4dp 线宽；文字按钮内使用 Material 2 `ButtonDefaults.IconSize`、2dp 线宽；图标按钮、账号行与小窗播放按钮为 24dp、2dp 线宽；下拉刷新内部采用 Material 2 原始刷新尺寸 20dp、2.5dp 线宽，保留 40dp 白色圆形容器。保留按钮触摸目标、账号行固定占位及原有无障碍语义。下拉刷新保留官方手势、白色圆形容器与固定阴影；拉动阶段按 Material 2 Android swipe-to-refresh 的旋转、圆弧增长、透明度阈值与超拉阻尼反馈，箭头复用 Material Symbols Rounded，不使用线性填满整圈的确定进度加载器。刷新阶段复用不确定进度组件，结束时保留旋转图形缩小淡出，不重新显示拉动箭头；两阶段均为圆角端点。有可量化进度的其他加载可使用确定进度指示器。
- 区分首次加载与刷新：已有内容刷新时保留内容与布局，不在列表顶部额外插入占位的加载指示器，避免条目位移；下拉刷新统一由 `ContentPullToRefresh` 的覆盖式指示器反馈，不与列表内加载指示器重复显示。已加载的空结果也属于已有状态，刷新时保留空状态及其占位，不临时隐藏或替换为列表内加载指示器。首次无数据加载及相邻分页加载仍可在对应区域显示加载状态。检查调用 `ContentStatus` 时传入的 `loading`，不要直接把刷新状态映射为列表内加载状态。

### 毛玻璃应用栏

- 栏面复用 `ThemeShadows`，连接列表使用容器 `shadow` 或宿主 `GraphicsLayer.shadowElevation` 的平台 elevation 投影，栏面与列表固定使用 4dp／1dp 高度；输入框、刷新容器和按钮保持各自既有投影规则，详见[栏面阴影](docs/implementation.md#栏面阴影)。
- 普通顶部栏（含认证、设置、历史、标签和头像裁剪页面）、主界面底部胶囊导航栏及视频页 Tab 栏固定开启毛玻璃，不添加开关或持久化偏好。视频画面与播放控件、图片查看器和系统导航栏不在此范围。
- 普通顶栏导航与操作图标统一使用 `onSurfaceVariant`，标题保留 `primary`，复用共享顶栏配色；填充图标按钮同步覆盖内容色，禁用状态保留透明度。认证页返回与关闭使用平面官方 `IconButton`。品牌 Logo 与流程标题图标保留强调色，媒体顶栏保留白色控件，文字操作沿用原组件默认配色。
- 普通页面使用 `FrostedScaffold` 管理独立采样状态和顶部背景；顶栏容器及滚动后容器均透明，不对整栏设置 alpha 或模糊。顶栏阴影由宿主统一复用 `ThemeShadows`，页面不重复添加；底栏仅在胶囊内部绘制效果，保留外部阴影。
- Android 12+ 使用 Haze 背景采样；Android 8.1–11 使用 90% 不透明度的栏面，不采样或模糊。原生 backdrop 暂不启用，规避已在 Android 17 模拟器上复现的转场 alpha 与 elevation 合成异常，排查证据见实现说明。
- 模糊使用主题 `surface`、20dp 模糊和 80% 不透明度的底色遮罩，不增加噪点或折射，不改变前景色。依赖版本以版本目录为准。
- 顶部安全区域放入滚动内容，刷新指示器通过 `indicatorTopPadding` 保持在栏下；非滚动表单保留安全避让。作者资料页保留既有吸顶标签布局，并将滚动封面单独接入该页采样状态。视频页 Tab 栏复用共享毛玻璃背景并独立采样分页内容，实测栏高放入列表顶部 `contentPadding`，刷新指示器、评论分页工具栏和输入面板高度同步避让。接入与限制见[实现说明](docs/implementation.md#毛玻璃应用栏)。

### 可选的可折叠大标题栏

共享 `CollapsibleTopAppBar` 适合有返回入口、希望通过纵向滚动展开大标题的二级页面，不要求所有页面使用；固定小标题、搜索页、播放器等按场景选择合适的官方顶栏。

- 每页独立创建 `rememberCollapsibleTopAppBarScrollBehavior()`，将同一状态传给顶栏并接入父容器的 `nestedScroll`；默认首次进入折叠，状态恢复后保留展开程度。
- 顶栏接收资源解析后的标题与事件，页面保留内容、滚动状态及 Snackbar 等职责。
- 普通毛玻璃页面的顶部与底部系统内边距必须随内容滚动：`Column` 放在 `verticalScroll()` 后，`LazyColumn` 使用 `contentPadding`。不要在滚动容器外应用完整 `innerPadding` 截短底部 edge-to-edge 区域；消费 Insets 防止重复避让，确保末项能滚动至导航栏上方。
- 短内容页也让滚动容器占满可用高度，使空白区域能接收下拉手势。

参数、首次展开设置和完整接入示例见[实现说明](docs/implementation.md#可选的可折叠大标题栏)。

### 认证状态约束

认证 ViewModel 必须绑定 Navigation 3 导航条目，旋转时保留、出栈后释放，不使用 Activity 级认证 ViewModel。仅邮箱写入 `SavedStateHandle`；密码、密码摘要、验证码、备用码与恢复码不得写入可保存状态、路由、应用磁盘或日志。系统密码保存是明确的例外边界：只有服务端认证与本机会话保存完成（密码重置则服务端确认与本地清理完成）后，才能经 `CreatePasswordRequest` 请求用户确认，将原密码交由用户选择的系统密码管理器保存；应用本身不落盘，取消或失败不撤销认证。待保存密码仅保留于条目流程内存，完成、取消或退出时释放。密码在进程重建后为空，认证流程回到登录入口，界面重建和切换步骤后默认隐藏。

系统凭据获取、保存及清理通过可替换网关；弹窗必须由当前 Activity 承载，ViewModel 以请求编号及宿主身份处理一次性结果，拒绝旋转、退出或旧流程的回调。自动选择器每次进入登录流程只请求一次，禁用自动选中，指定邮箱重新登录时筛选账号。非生产根地址构建禁用真实提供者，离线测试使用凭据替身；不得读取真实密码管理器凭据。Compose 保留邮箱／用户名及密码 Autofill 语义，切换、取消流程和调用凭据保存前取消旧 Autofill 会话，不使用 View 专用凭据联动接口。退出登录、切换游客和移除当前账号后只尽力清理提供者会话，不删除其密码。

业务接入放在 ViewModel 的统一提交入口，通过 Repository 隔离 API，服务端 DTO 不进入 UI。层次职责与条目装饰器顺序见[认证状态实现](docs/implementation.md#认证状态)。

### API 与会话接入规范

- 接口契约以 Rosales 的路由、Controller DTO 和 Service 实现为依据，Cerasus 用于对照交互、参数及邮件模板。不得根据 DTO 注释臆造散列方式、Bearer 鉴权、刷新令牌或登出撤销能力；当前已对照的端点见[实现说明](docs/implementation.md#api-与会话)。接口改动同步契约表与失败语义。
- UI → ViewModel → Repository → API / Store 单向分层。服务端 DTO 只在数据层解码并转为领域模型；UI 不接触 token、Cookie 或密码摘要。应用只持有一份会话 Repository，认证表单仍按导航条目隔离。
- API 地址只能来自 `BuildConfig.API_BASE_URL`，使用 HTTPS；构建配置不得含凭据、查询串或片段。禁止放宽证书校验、自动降级 HTTP、跟随鉴权请求重定向，禁止将会话发往图片或其他域名。环境切换不得复用另一后端的会话。
- 使用共享 OkHttp API 客户端及 kotlinx.serialization；设置连接、读写与整个调用的超时，限制响应大小，查询值通过 URL builder 编码。网络、散列和磁盘操作离开主线程，请求取消必须传递到 HTTP Call。
- HTTP 成功不等于业务成功：检查 `success`、必要字段及账号身份一致性；未知字段允许向前兼容，缺失关键字段不能产生已登录状态。禁止原样展示服务端错误或输出响应、邮箱、密码、摘要、验证码、Cookie、token 日志。
- 统一区分网络、超时、服务不可用、响应异常、业务拒绝、限流、失效与本地存储错误，映射中英文提示。网络失败不删除账号；当前 Rosales 的 `success=false` 也可能表示数据库故障，不能一律当作令牌已撤销。校验拒绝保留加密凭据供重试，同时撤下受影响的当前身份；明确 HTTP 401 才清除对应 token。
- 登录、注册、发送验证码、找回密码不自动重放请求，不自动重试。按钮禁用重复提交，重试由用户触发；验证码冷却按邮箱统一管理，尊重服务端冷却及每日上限，客户端倒计时不代替服务端校验。
- 会话以服务端 UUID 去重，token 与最小账号资料用 Android Keystore AES-256-GCM 加密，IV 由平台每次随机生成，原子写入 `noBackupFilesDir`，不进入备份、SavedState、预览或普通偏好设置。解密不得生成替代密钥，须先验证完整认证标签及数据结构；缺失密钥、损坏文件或恢复失败时保留原文件并禁止覆盖。写入成功后才能发布新的当前账号；只能在用户明确确认后清除本机账号库及密钥。系统备份仅允许已明确列出的非敏感偏好。
- 切换、移除和会话保存串行化；请求绑定发起时的账号，结果只能更新所属账号。后续增加账号私有缓存时必须按 UUID 隔离，并在切换／移除时清理对应页面状态，不能将前一个账号的数据交给新账号。
- 游客不携带认证信息。切换到游客保留其他会话；登出／移除仅清除指定本机会话，不谎称已撤销服务器 token。禁止添加不存在的 refresh-token 流程；失效账号通过认证页重新登录。
- 用户明确要求认证测试时，优先使用内存响应、内存账号库、虚构身份和可控时钟；测试不得请求生产后端或读取真实凭据。实际 Keystore 测试使用独立包名、无网络权限的专项变体及临时文件／密钥，不安装到普通应用包名。测试替身只放在测试源码中，不增加绕过 TLS 或改用假会话的产品入口。现有隔离方式与命令见[认证离线测试](docs/auth-testing.md)。

### 主题配色约束

- 所有颜色统一使用经典强调色，默认项目粉色，壁纸取色默认关闭；不提供配色算法选择。主题、色板与选色器统一使用 `rememberSeedColorScheme` 生成完整语义颜色，不能只替换 `primary`；错误等语义颜色保留必要区分，形状、排版与动效沿用 Material 3 Expressive。
- 壁纸颜色作为主题色板中的一个选项，仅在 Android 12+ 显示，与应用主题共用 `wallpaperAccentColor` 读取系统强调色；只提供原色来源，浅深模式均使用经典方案。选择壁纸不覆盖手动或自定义色值，系统颜色资源变化后重新读取。
- 经典强调色在浅深模式均保留不透明原色为 `primary`，完整生成主色容器、反色与固定色角色，次要容器与主色容器共用强调色调及配套前景，使默认 Slider 的未经过轨道、选中 Chip 和导航指示器统一着色，其余次要／第三色角色维持灰阶；NavigationBar 选中图标与文字使用 `primary`，选中指示器使用 `primaryContainer`；栏面底色浅色纯白、深色深灰，普通应用栏叠加共享毛玻璃背景，关闭色调高度叠加以免重新染色。
- 经典方案的 `onPrimary` 在主题生成阶段采用白色优先规则：白色与原色的对比达到 2.5:1 时使用白色，否则使用黑色。Switch 滑块与填充按钮文字直接继承该角色；此视觉取舍不保证按钮文字达到 4.5:1。
- TextButton、RadioButton、OutlinedTextField 等使用官方默认配色，主色文字、选中标签与边框直接引用 `MaterialTheme.colorScheme.primary`，不逐组件计算对比度或调整明度，接受极浅／极深选色时前景对比不足。
- `ThemeColorDefaults` 只提供栏面颜色、普通页面背景和设置页面背景三个接口；对比度与 HCT 工具仅作为主题生成器的私有实现。底栏选中指示器及第三方组件需映射不同角色时直接使用 `ColorScheme`，不新增组件专用配色函数或包装组件。
- 自定义颜色草稿仅在确认后持久保存，取消不改变主题。自定义色值与当前生效色值独立存储，并保存预设／自定义选择状态，不能仅按色值相等判断选中项。

配色生成、颜色保存与选色组件实现见[主题实现](docs/implementation.md#主题实现)。

## 依赖与配置

依赖和插件版本集中到 [gradle/libs.versions.toml](gradle/libs.versions.toml)，通过 `libs.*` 引用。Compose 库优先由现有 BOM 管理；禁止动态版本（如 `1.+`）和无需求的工具链升级。新增依赖说明用途、维护状况及体积影响，不为接入单项功能更换现有 BOM 或 Material 3 版本。实际 APK 增量需通过同构建配置比较，不能以依赖包大小代替或将迁移默认视为体积优化。

Material 3 采用显式固定版本例外，以使用公开的 Expressive 主题与分段列表 API；其余 Compose 依赖由 BOM 管理。`androidx.compose.material:material` 仅用于复用官方 Material 2 圆形加载器及按钮图标尺寸，不将页面或主题迁移到 Material 2；未进行同配置 APK 对比时，不宣称体积变化。依赖与插件的具体版本号只维护在版本目录，文档不重复记录；开发环境与工具链配置来源见[开发环境](docs/development.md#开发环境)。实验性 opt-in 限于实际调用点；升级或转为稳定版时检查主题、导航、搜索栏及中英文布局相关 API 的兼容性，检查范围遵循下方默认构建约定，不使用编译器抑制绕过内部 API 可见性。

导航依赖采用 AndroidX 官方 Navigation 3，显式引入 `navigation3-runtime` 和 `navigation3-ui`，不使用 Navigation 2 的 `navigation-compose`；Navigation 3 不由 Compose BOM 管理。认证的 `lifecycle-viewmodel-compose`、`lifecycle-viewmodel-navigation3` 与主题的 `lifecycle-runtime-compose` 均与现有 Lifecycle 版本保持一致。暂不添加 adaptive 等无当前需求的扩展库。接入背景见[实现说明](docs/implementation.md)，Navigation 3 另见[官方入门指南](https://developer.android.com/guide/navigation/navigation-3/get-started)。

不得提交 `local.properties`、访问令牌、签名密钥或带个人信息的日志。客户端内置值不能视为秘密；服务端凭据不得写入客户端代码。权限按功能最小需要声明。

## 构建与检查

- 代码或构建变更默认只执行 `./gradlew :app:assembleDebug`，修复本次改动引起的编译、资源处理和打包错误。构建成功仅表示编译和打包通过，不代表运行时行为已经测试。
- 除非用户明确要求，否则不新增、修改或运行单元测试、回归测试、UI／设备测试或截图测试。业务逻辑变化、缺陷修复及交互变化同样遵循此约定，不以测试覆盖率或补充测试作为交付条件。
- 默认不执行 `verify`、额外的 Android Lint，也不主动启动模拟器进行人工测试或采集截图、录屏。文中关于布局、颜色、导航和无障碍的要求是实现约束，不自动构成开展测试的要求。
- 保留现有测试、依赖和检查任务。`verify` 仍聚合 Debug 构建、单元测试和 Android Lint，仅在用户明确要求该检查时运行；设备测试同样按用户要求运行，不改变这些命令的含义。
- 用户明确要求测试时，JVM 测试使用 JUnit 4，放入 `app/src/test/`；设备测试使用 AndroidJUnit4 / Compose UI Test，放入 `app/src/androidTest/`，包结构与被测源码一致。仅覆盖用户要求的行为，避免固定等待或依赖真实网络。
- 纯文档修改只检查路径、链接、命令及表述一致性，不运行构建或测试。交付时如实记录已执行检查及失败或阻塞原因，不得将未运行的检查报告为通过；按默认约定未运行测试无需作为规范例外处理。

## 文档维护

本指南是详细规范的唯一维护入口，AGENTS 保留执行摘要。修改规范时同步相关摘要、配置和模板；功能或实现变化更新对应专题，不在 README 追加逐次变更记录。

| 内容变化 | 维护位置 |
| --- | --- |
| 项目定位、主要能力、最低运行要求或最短启动步骤 | [README](README.md) |
| 页面行为、演示范围、保存行为与限制 | [功能现状](docs/features.md) |
| 数据流、实现理由、参数与接入示例 | [实现说明](docs/implementation.md) |
| 开发环境、工具链配置来源、命令与目录 | [开发指南](docs/development.md)；依赖与插件版本只维护在 [版本目录](gradle/libs.versions.toml)，Wrapper 版本以实际配置为准 |
| 技术选型、架构边界、代码与 UI 约束、检查和提交政策 | 本指南；同步 [AGENTS](AGENTS.md) 与 [PR 模板](.github/pull_request_template.md) 的适用摘要 |
| 第三方来源、许可、移植差异与开源声明维护 | [third_party](third_party/) 下对应文档 |

同一项详细信息只在所属文档维护，其他位置用概览和链接引用。移动章节时同步相对路径与锚点；保留仍被引用的规范入口。依赖升级无需同步版本号到文档；若影响开发环境、运行要求、接入方式或快速开始，则更新对应说明。

## Git 与 Pull Request

以下为项目统一约定。

- 分支建议使用 `feat/<topic>`、`fix/<topic>`、`docs/<topic>` 或 `chore/<topic>`。
- 提交格式为 `type(scope): summary`，scope 可省略；type 使用 `feat`、`fix`、`refactor`、`docs`、`test`、`style`、`build`、`ci` 或 `chore`。例如 `feat(home): add empty state`、`docs: 完善开发环境说明`。summary 简洁说明动作，不用“更新代码”一类泛泛描述。
- 一次提交聚焦一个目的；不要混入无关重排、依赖升级或生成目录。破坏兼容性的变更在正文中解释影响及迁移方式。
- 使用 [PR 模板](.github/pull_request_template.md)，说明问题、方案、关联 issue（如有）、构建结果及风险；测试结果、界面截图或录屏仅在用户要求时提供，规范例外写明原因。
- 合并前由其他开发者审核适用规范与验证证据。当前项目未提供 CI 工作流；远端分支保护需维护者另行设置，不能把本地检查入口视为服务器已强制执行的规则。
