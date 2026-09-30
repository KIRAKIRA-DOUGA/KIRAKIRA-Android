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

- 表达「添加」的悬浮操作按钮（FAB）使用官方 `FloatingActionButton`，仅显示 Material Symbols Rounded `add` 加号，不在图标右侧重复显示「添加」文字，不使用带文字的扩展 FAB。保留本地化的「添加」无障碍描述、标准触摸目标与操作期间的禁用语义。
- 使用 Material 3 Expressive 的组件、色彩层次和圆角分组；保持浅色、深色及动态颜色兼容，不用手绘控件替代已有标准组件。参考 [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)。
- 分组菜单直接使用官方 `SegmentedListItem`，以 `ListItemDefaults.segmentedShapes(index, count)` 处理首尾与单项圆角，以 `SegmentedGap` 设置组内间距，并保留默认分段配色及内容内边距。不再以整组大圆角 `Surface` 模拟此样式。静态占位使用无 `onClick` 的重载；无副标题时传入空的 `supportingContent`，不要提供空内容 lambda。
- 图标采用官方 **Material Symbols**，默认统一为 **Rounded、24dp、wght 400、GRAD 0、FILL 0**；选中状态如使用填充图标应保持其他参数一致。按需导入 Android VectorDrawable，不打包完整字体或旧版 `material-icons-extended`。
- 必要时可自行绘制相同风格的图标：保持 24dp 画布、相近视觉重量、圆角和光学对齐，检查浅深色与小尺寸可读性，在 PR 中说明缺少合适标准图标的原因。官方资源使用 `ic_symbol_<name>`，自绘资源使用 `ic_custom_<name>`，保留来源及许可证记录于 `third_party/`。不得混用 SF Symbols、旧版 Material Icons 或不一致的描边风格。参见 [Material Symbols 指南](https://developers.google.com/fonts/docs/material_symbols)。
- 使用单向数据流：状态向下传递，事件通过回调向上传递。可复用组件接收所需状态和回调，不直接获取 ViewModel、Repository 或导航控制器。
- 可复用 UI 的第一个可选参数使用 `modifier: Modifier = Modifier`，作用于组件根节点；内容插槽放最后。[参数示例](docs/implementation.md#可复用-ui-参数示例)见实现说明。
- 按[状态提升原则](https://developer.android.com/develop/ui/compose/state-hoisting)选择状态所有者：局部临时状态用 `remember`；需要恢复且可保存的 UI 状态用 `rememberSaveable`；业务状态由 ViewModel 暴露只读状态。采用 Flow 时，在屏幕入口通过 `collectAsStateWithLifecycle` 收集，并先在版本目录声明所需依赖。
- 不在组合执行体中直接请求网络、写存储或执行导航。用户操作在事件回调中触发；与组合生命周期相关的工作使用具有正确 key 的 `LaunchedEffect` / `DisposableEffect`，并清理监听器。参见[副作用指南](https://developer.android.com/develop/ui/compose/side-effects)。
- 网络和磁盘工作不得阻塞主线程；使用结构化并发，禁止 `GlobalScope`。捕获取消异常时必须继续传播取消。
- 通过 `KIRAKIRATheme` 和 `MaterialTheme` 复用颜色、字体及形状；避免在业务页面散落品牌颜色。布局使用 `dp`，字体使用 `sp`，通用设计值按实际复用需求提取。
- 新界面文案必须放入字符串资源，包含错误、导航标题和无障碍描述；使用格式化资源和 plurals，不拼接可翻译句子。
- 除非用户明确要求，界面中不添加解释功能如何运作的说明文字；功能机制与实现细节记录在相应文档中。界面保留必要的操作标签、状态反馈、错误信息和无障碍提示，不主动加入机制说明或实现细节。
- 当前支持英语与中文：`res/values/strings.xml` 是完整英语界面回退资源，`res/values-zh/strings.xml` 提供中文；新增可翻译 key 必须同时补齐两套文案。品牌名标记 `translatable="false"`。跟随系统语言，Android 13+ 通过 `res/xml/locales_config.xml` 声明应用语言；新增语言同步配置并检查长文案和字体缩放。
- Demo 占位内容不做多语言，包括用户名、账号标识、签名、评论正文、弹幕正文、简介、视频标题及演示元数据。只维护一份固定内容，集中放入 `res/values/demo_strings.xml` 并标记 `translatable="false"`，不得在 `values-zh/` 等语言目录中重复定义。内容可以使用中文，不受默认界面资源为英语的约束。按钮、导航标题、输入提示、功能待接入提示、数量标签和无障碍描述属于界面文案，仍须维护中英文翻译。
- 交互控件提供语义与可读标签，纯装饰图标使用空描述；保证触摸目标、字体缩放和 TalkBack 可用。实现时兼顾浅色、深色、窄屏和宽屏，使用 `start/end` 方向及系统 Insets，避免用固定屏幕尺寸布局；不因此默认增加设备或截图测试。
- 列表项目使用稳定的业务 key；不要为了压制重组而随意添加 `@Stable` / `@Immutable`。可复用组件提供使用假数据的 Preview，不依赖真实服务或运行中的 ViewModel。
- 异步页面明确表达加载、成功、空内容与失败状态；重试入口应与操作语义一致。
- 无确定进度的页面加载统一使用 Material 3 Expressive 的 `LoadingIndicator`，不得使用不确定进度的 `CircularProgressIndicator`。有可量化进度的加载可使用确定进度指示器。
- 区分首次加载与刷新：已有内容刷新时保留内容与布局，不在列表顶部额外插入占位的 `LoadingIndicator`，避免条目位移；下拉刷新统一由 `ContentPullToRefresh` 的覆盖式指示器反馈，不与列表内加载指示器重复显示。已加载的空结果也属于已有状态，刷新时保留空状态及其占位，不临时隐藏或替换为列表内加载指示器。首次无数据加载及相邻分页加载仍可在对应区域显示加载状态。检查调用 `ContentStatus` 时传入的 `loading`，不要直接把刷新状态映射为列表内加载状态。

### 可选的可折叠大标题栏

共享 `CollapsibleTopAppBar` 适合有返回入口、希望通过纵向滚动展开大标题的二级页面，不要求所有页面使用；固定小标题、搜索页、播放器等按场景选择合适的官方顶栏。

- 每页独立创建 `rememberCollapsibleTopAppBarScrollBehavior()`，将同一状态传给顶栏并接入父容器的 `nestedScroll`；默认首次进入折叠，状态恢复后保留展开程度。
- 顶栏接收资源解析后的标题与事件，页面保留内容、滚动状态及 Snackbar 等职责。
- 顶部内边距放在滚动容器外；底部系统内边距必须随内容滚动：`Column` 放在 `verticalScroll()` 后，`LazyColumn` 使用 `contentPadding`。不要在滚动容器外应用完整 `innerPadding` 截短底部 edge-to-edge 区域；消费 Insets 防止重复避让，确保末项能滚动至导航栏上方。
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

- 手动配色必须生成完整语义颜色，不能只替换 `primary`；主题、色板、算法列表与选色器统一使用 `rememberSeedColorScheme`。算法选择不改变 Expressive 形状、排版与动效体系；单色主题的错误等语义颜色保留必要区分。
- 经典强调色在浅深模式均保留不透明原色为 `primary`，完整生成主色容器、反色与固定色角色，次要／第三色系列维持灰阶；栏面浅色纯白、深色深灰，关闭色调高度叠加以免重新染色。此规则仅作用于经典方案，其余算法与系统配色保持既有行为。
- 经典方案的 `onPrimary` 在主题生成阶段采用白色优先规则：白色与原色的对比达到 2.5:1 时使用白色，否则使用黑色。Switch 滑块与填充按钮文字直接继承该角色；此视觉取舍不保证按钮文字达到 4.5:1。
- TextButton、RadioButton、OutlinedTextField 等使用官方默认配色，主色文字、选中标签与边框直接引用 `MaterialTheme.colorScheme.primary`，不逐组件计算对比度或调整明度，接受极浅／极深选色时前景对比不足。
- `ThemeColorDefaults` 只提供栏面颜色和页面背景两个接口；对比度与 HCT 工具仅作为主题生成器的私有实现。底栏选中指示器及第三方组件需映射不同角色时直接使用 `ColorScheme`，不新增组件专用配色函数或包装组件。
- 自定义颜色草稿仅在确认后持久保存，取消不改变主题。自定义色值与当前生效色值独立存储，并保存预设／自定义选择状态，不能仅按色值相等判断选中项。

算法版本、设置兼容与选色组件实现见[主题实现](docs/implementation.md#主题实现)。

## 依赖与配置

依赖和插件版本集中到 [gradle/libs.versions.toml](gradle/libs.versions.toml)，通过 `libs.*` 引用。Compose 库优先由现有 BOM 管理；禁止动态版本（如 `1.+`）和无需求的工具链升级。新增依赖说明用途、维护状况及体积影响，不为接入单项功能更换现有 BOM 或 Material 3 版本。实际 APK 增量需通过同构建配置比较，不能以依赖包大小代替或将迁移默认视为体积优化。

Material 3 采用显式固定版本例外，以使用公开的 Expressive 主题与分段列表 API；其余 Compose 依赖由 BOM 管理。当前版本与 SDK、JDK、Wrapper、AGP 配置统一见[开发环境](docs/development.md#开发环境)。实验性 opt-in 限于实际调用点；升级或转为稳定版时检查主题、导航、搜索栏及中英文布局相关 API 的兼容性，检查范围遵循下方默认构建约定，不使用编译器抑制绕过内部 API 可见性。

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
| SDK、JDK、Wrapper、AGP、依赖版本、命令与目录 | [开发指南](docs/development.md)；版本值以实际配置为准 |
| 技术选型、架构边界、代码与 UI 约束、检查和提交政策 | 本指南；同步 [AGENTS](AGENTS.md) 与 [PR 模板](.github/pull_request_template.md) 的适用摘要 |
| 第三方来源、许可、移植差异与开源声明维护 | [third_party](third_party/) 下对应文档 |

同一项详细信息只在所属文档维护，其他位置用概览和链接引用。移动章节时同步相对路径与锚点；保留仍被引用的规范入口。完整版本表只维护在开发指南；版本变更若影响 README 的运行要求或快速开始，也要同步入口说明。

## Git 与 Pull Request

以下为项目统一约定。

- 分支建议使用 `feat/<topic>`、`fix/<topic>`、`docs/<topic>` 或 `chore/<topic>`。
- 提交格式为 `type(scope): summary`，scope 可省略；type 使用 `feat`、`fix`、`refactor`、`docs`、`test`、`style`、`build`、`ci` 或 `chore`。例如 `feat(home): add empty state`、`docs: 完善开发环境说明`。summary 简洁说明动作，不用“更新代码”一类泛泛描述。
- 一次提交聚焦一个目的；不要混入无关重排、依赖升级或生成目录。破坏兼容性的变更在正文中解释影响及迁移方式。
- 使用 [PR 模板](.github/pull_request_template.md)，说明问题、方案、关联 issue（如有）、构建结果及风险；测试结果、界面截图或录屏仅在用户要求时提供，规范例外写明原因。
- 合并前由其他开发者审核适用规范与验证证据。当前项目未提供 CI 工作流；远端分支保护需维护者另行设置，不能把本地检查入口视为服务器已强制执行的规则。
