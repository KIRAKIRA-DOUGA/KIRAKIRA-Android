# 贡献指南

本规范适用于人工开发与代码代理。文中的“必须 / 不得”是提交要求，“建议 / 优先”允许结合需求判断；例外应在 PR 中记录原因、影响及验证方式。配置变更应同步文档。既有模板不作为新代码的风格标准，也不要求每次提交清理整个项目。

## 技术选型与架构

- 新应用代码使用 Kotlin，Gradle 使用 Kotlin DSL。新界面统一使用 Jetpack Compose，设计语言为 **Material 3 Expressive**；复用 `KIRAKIRATheme` 中的 `MaterialExpressiveTheme`，包括颜色、形状、排版和动效体系。
- 不新增以 XML Layout、Fragment、ViewBinding 或 DataBinding 为核心的页面。只有平台能力或第三方 SDK 需要时使用 `AndroidView` 等互操作，并封装生命周期与资源释放。Manifest、图标、字符串和系统主题 XML 仍正常使用。
- 保持单 Activity 入口；`MainActivity` 负责宿主和顶层组合，不承载网络、数据库或业务规则。
- 保留当前 `:app` 模块。新功能放在 `moe.kirakira.feature.<name>`；跨功能 UI 放 `ui/components/`，主题放 `ui/theme/`，数据访问按需放 `data/`，真正跨功能的基础设施放 `core/`。包名全小写，不将无关代码堆入 `Utils.kt`。
- 页面展示状态并上报事件；涉及业务逻辑的屏幕由 ViewModel 管理状态，通过 Repository 隔离数据访问。复杂且可复用的业务规则才抽取 use case；不强制为简单操作增加层次。
- 页面导航使用 Navigation 3，路由实现 `NavKey` 并标注 `@Serializable`，通过 `rememberNavBackStack` 和封装 `NavDisplay` 的 `ui/navigation/ActivityNavDisplay.kt` 管理返回栈及状态恢复。普通转场复用 `NavigationMotion.kt`，预测性返回由 `PredictiveBackMotion.kt` 移植 AOSP 的两阶段动画，页面使用 `NavigationPage`。参数来源与公开 API 适配差异见 [Android 转场说明](third_party/android-motion/README.md)，不在各页面重复配置。导航宿主通过 Navigation Event 接收手势，使用双页面 Scene 保留预览及收尾阶段的内容，完成后仅出栈一次；不要再叠加 NavDisplay 的默认预测性返回补间。Navigation 3 继续负责场景、可保存状态与生命周期，手势期间页面生命周期不高于 STARTED。导航由页面事件回调触发，保留根页面，避免连续点击重复入栈或出栈。底栏当前是主界面内的局部状态，不预建多返回栈架构。
- 目前尚未建立完整数据层、ViewModel 或依赖注入。新增库时说明具体需求，先复用已有能力，避免为了符合目录示意而添加空实现。

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

- 使用 Material 3 Expressive 的组件、色彩层次和圆角分组；保持浅色、深色及动态颜色兼容，不用手绘控件替代已有标准组件。参考 [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)。
- 分组菜单直接使用官方 `SegmentedListItem`，以 `ListItemDefaults.segmentedShapes(index, count)` 处理首尾与单项圆角，以 `SegmentedGap` 设置组内间距，并保留默认分段配色及内容内边距。不再以整组大圆角 `Surface` 模拟此样式。静态占位使用无 `onClick` 的重载；无副标题时传入空的 `supportingContent`，不要提供空内容 lambda。
- 图标采用官方 **Material Symbols**，默认统一为 **Rounded、24dp、wght 400、GRAD 0、FILL 0**；选中状态如使用填充图标应保持其他参数一致。按需导入 Android VectorDrawable，不打包完整字体或旧版 `material-icons-extended`。
- 必要时可自行绘制相同风格的图标：保持 24dp 画布、相近视觉重量、圆角和光学对齐，检查浅深色与小尺寸可读性，在 PR 中说明缺少合适标准图标的原因。官方资源使用 `ic_symbol_<name>`，自绘资源使用 `ic_custom_<name>`，保留来源及许可证记录于 `third_party/`。不得混用 SF Symbols、旧版 Material Icons 或不一致的描边风格。参见 [Material Symbols 指南](https://developers.google.com/fonts/docs/material_symbols)。
- 使用单向数据流：状态向下传递，事件通过回调向上传递。可复用组件接收所需状态和回调，不直接获取 ViewModel、Repository 或导航控制器。
- 可复用 UI 的第一个可选参数使用 `modifier: Modifier = Modifier`，作用于组件根节点；内容插槽放最后。例如：

  ```kotlin
  @Composable
  fun FavoriteButton(
      selected: Boolean,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      // 根据 selected 渲染，点击时调用 onClick。
  }
  ```

- 按[状态提升原则](https://developer.android.com/develop/ui/compose/state-hoisting)选择状态所有者：局部临时状态用 `remember`；需要恢复且可保存的 UI 状态用 `rememberSaveable`；业务状态由 ViewModel 暴露只读状态。采用 Flow 时，在屏幕入口通过 `collectAsStateWithLifecycle` 收集，并先在版本目录声明所需依赖。
- 不在组合执行体中直接请求网络、写存储或执行导航。用户操作在事件回调中触发；与组合生命周期相关的工作使用具有正确 key 的 `LaunchedEffect` / `DisposableEffect`，并清理监听器。参见[副作用指南](https://developer.android.com/develop/ui/compose/side-effects)。
- 网络和磁盘工作不得阻塞主线程；使用结构化并发，禁止 `GlobalScope`。捕获取消异常时必须继续传播取消。
- 通过 `KIRAKIRATheme` 和 `MaterialTheme` 复用颜色、字体及形状；避免在业务页面散落品牌颜色。布局使用 `dp`，字体使用 `sp`，通用设计值按实际复用需求提取。
- 新用户文案必须放入字符串资源，包含错误、导航标题和无障碍描述；使用格式化资源和 plurals，不拼接可翻译句子。
- 当前支持英语与中文：`res/values/strings.xml` 是完整英语回退资源，`res/values-zh/strings.xml` 提供中文；新增可翻译 key 必须同时补齐两套文案。品牌名及无须翻译的占位值标记 `translatable="false"`。跟随系统语言，Android 13+ 通过 `res/xml/locales_config.xml` 声明应用语言；新增语言同步配置并检查长文案和字体缩放。
- 交互控件提供语义与可读标签，纯装饰图标使用空描述；保证触摸目标、字体缩放和 TalkBack 可用。验证浅色、深色、窄屏和宽屏，使用 `start/end` 方向及系统 Insets，避免用固定屏幕尺寸布局。
- 列表项目使用稳定的业务 key；不要为了压制重组而随意添加 `@Stable` / `@Immutable`。可复用组件提供使用假数据的 Preview，不依赖真实服务或运行中的 ViewModel。
- 异步页面明确表达加载、成功、空内容与失败状态；重试入口应与操作语义一致。
- 无确定进度的页面加载统一使用 Material 3 Expressive 的 `LoadingIndicator`，不得使用不确定进度的 `CircularProgressIndicator`。有可量化进度的加载可使用确定进度指示器。

### 可选的可折叠大标题栏

设置、外观、关于和切换账户页使用共享组件
[`CollapsibleTopAppBar`](app/src/main/java/moe/kirakira/ui/components/CollapsibleTopAppBar.kt)。
它适合有返回入口、希望通过纵向滚动展开大标题的二级页面；**不是所有页面的强制顶栏**。
固定小标题、搜索页、播放器或其他有特殊布局需求的页面，可按场景选择合适的官方顶栏。

- 在每个页面内调用 `rememberCollapsibleTopAppBarScrollBehavior()`，各页面独立保存展开程度；默认首次进入折叠，上滑收起，内容回到顶部后下拉展开。需要首次展开时传入 `initialCollapsed = false`；此参数不会覆盖已恢复的状态。
- 将同一份 `scrollBehavior` 传给顶栏，并将其 `nestedScrollConnection` 接到 `Scaffold` 或滚动内容的父容器。只替换顶栏、不接嵌套滚动，无法联动列表手势。
- 顶栏接收字符串资源解析后的 `title`、`onBack` 和可选 `actions` 插槽，切换账户页的“编辑／完成”就是后者的示例。组件统一返回图标和配色，页面保留内容、滚动状态及 Snackbar 等职责。
- 顶部内边距放在滚动容器外，避免内容被顶栏遮住；底部系统内边距必须随内容滚动：`Column` 将底部 `padding` 放在 `verticalScroll()` 后，`LazyColumn` 使用 `contentPadding`。不要在滚动容器外应用完整的 `innerPadding`，否则滚动区域会在导航栏上方截断，破坏底部 edge-to-edge。消费 Insets 防止子组件重复避让，滚动到底时保留末项所需的底部安全距离。
- 短内容页也让滚动容器占满可用高度，使空白区域能接收下拉手势；使用 `Column` 时将 `fillMaxSize()` 放在 `verticalScroll()` 前。

接入示意（调用方提供 `onBack`，菜单内容按页面填写）：

```kotlin
val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
val layoutDirection = LocalLayoutDirection.current
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
        CollapsibleTopAppBar(
            title = stringResource(R.string.me_settings),
            onBack = onBack,
            scrollBehavior = scrollBehavior,
        )
    },
) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(top = innerPadding.calculateTopPadding())
            .consumeWindowInsets(innerPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
    ) {
        // 页面内容
    }
}
```

## 依赖与配置

依赖和插件版本集中到 `gradle/libs.versions.toml`，通过 `libs.*` 引用。Compose 库优先由现有 BOM 管理；禁止动态版本（如 `1.+`）和无需求的工具链升级。新增依赖说明用途、维护状况及体积影响。SDK、JDK、Wrapper 或 AGP 变更应独立说明并更新 README。

当前例外：Material 3 显式固定为 `1.5.0-alpha28`，以使用公开的 Expressive 主题与分段列表 API；其余 Compose 依赖由 BOM `2026.09.00` 管理。实验性 opt-in 限于实际调用点。升级或转为稳定版时需验证主题、导航、搜索栏及中英文布局，不使用编译器抑制绕过内部 API 可见性。

导航依赖采用 AndroidX 官方维护的 Navigation 3 稳定版 `1.1.7`，显式引入 `navigation3-runtime` 和 `navigation3-ui`，替换 Navigation 2 的 `navigation-compose`；Navigation 3 不由 Compose BOM 管理。仅引入当前所需的返回栈和 UI 能力，暂不添加 ViewModel、adaptive 等扩展库。依赖及其传递依赖会影响 APK 体积，不能将迁移视为体积优化；实际大小以构建产物为准。参考[官方入门指南](https://developer.android.com/guide/navigation/navigation-3/get-started)。

不得提交 `local.properties`、访问令牌、签名密钥或带个人信息的日志。客户端内置值不能视为秘密；服务端凭据不得写入客户端代码。权限按功能最小需要声明。

## 测试与质量检查

- JVM 逻辑测试使用 JUnit 4，放入 `app/src/test/`；平台和 Compose 交互测试使用 AndroidJUnit4 / Compose UI Test，放入 `app/src/androidTest/`。测试包路径与源码对应。
- 修改业务逻辑需覆盖正常路径和相关边界、错误路径；修复缺陷时优先增加可复现的回归测试。UI 行为测试通过语义断言，必要时使用稳定的 test tag，避免固定等待或依赖真实网络。
- 使用 fake 数据源及可控异步调度；不以堆积无意义断言满足数量目标。目前没有覆盖率百分比要求，现有示例测试也不代表业务覆盖。
- 代码或构建变更提交前执行 `./gradlew verify`。涉及 UI 或平台行为时，另执行 `./gradlew :app:connectedDebugAndroidTest` 并记录设备 API；视觉变化附截图和人工检查结果。
- 纯文档修改验证路径、链接、命令及表述一致性即可。无法执行某项检查时，明确标注“未运行 / 失败”、原因及需要补充的验证，不得勾选通过。
- `verify` 聚合 Debug 构建、单元测试和 Android Lint；不含设备测试或 Kotlin 格式检查。不得通过全局忽略 Lint 或跳过测试掩盖失败；局部抑制必须说明理由。

## Git 与 Pull Request

当前工作副本没有可读取的 Git 历史；以下是新增统一约定。

- 分支建议使用 `feat/<topic>`、`fix/<topic>`、`docs/<topic>` 或 `chore/<topic>`。
- 提交格式为 `type(scope): summary`，scope 可省略；type 使用 `feat`、`fix`、`refactor`、`docs`、`test`、`style`、`build`、`ci` 或 `chore`。例如 `feat(home): add empty state`、`docs: 完善开发环境说明`。summary 简洁说明动作，不用“更新代码”一类泛泛描述。
- 一次提交聚焦一个目的；不要混入无关重排、依赖升级或生成目录。破坏兼容性的变更在正文中解释影响及迁移方式。
- 使用 [PR 模板](.github/pull_request_template.md)，说明问题、方案、关联 issue（如有）、测试结果及风险；界面变更提供截图或录屏，规范例外写明原因。
- 合并前由其他开发者审核适用规范与验证证据。当前项目未提供 CI 工作流；远端分支保护需维护者另行设置，不能把本地检查入口视为服务器已强制执行的规则。
