# 共享 UI 组件

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文说明 `ui/components/` 下共享组件的接入方式，以及它们背后不容易从代码看出来的设计原因和平台限制。必须遵守的规则在[界面规范](../ui-guidelines.md)，这里不重复；组件按族分包，包的划分见[开发指南](../development.md#目录结构)。

- [参数与 Modifier 约定](#参数与-modifier-约定)
- [内容状态与刷新](#内容状态与刷新)
- [列表、菜单与对话框](#列表菜单与对话框)
- [按钮](#按钮)
- [选择控件](#选择控件)
- [页面骨架](#页面骨架)
- [搜索](#搜索)
- [账号流程布局](#账号流程布局)
- [文字、数字与图标](#文字数字与图标)
- [插画](#插画)
- [系统状态栏回顶](#系统状态栏回顶)
- [开发者组件库](#开发者组件库)

## 参数与 Modifier 约定

### 可复用 UI 参数示例

状态与事件分离，第一个可选参数是 `modifier`。完整约束见[组件、状态与副作用](../ui-guidelines.md#组件状态与副作用)。

```kotlin
@Composable
fun FavoriteButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 根据 selected 渲染，点击时调用 onClick；modifier 应用于根节点。
}
```

### Modifier 工厂示例

只在可用时添加语义的工厂：

```kotlin
internal fun Modifier.contentDescriptionWhenEnabled(enabled: Boolean, description: String): Modifier {
    if (!enabled) return this
    return semantics { contentDescription = description }
}
```

调用 `Modifier.padding(16.dp).contentDescriptionWhenEnabled(enabled, description)` 时，两条分支都保留已有的 padding。

## 内容状态与刷新

### 统一内容状态

[ContentStatus](../../app/src/main/kotlin/moe/kirakira/ui/components/status/ContentStatus.kt) 接收共享的 [ContentState](../../app/src/main/kotlin/moe/kirakira/ui/content/ContentState.kt)，把加载、错误、空三种状态映射到 `ContentLoadingView` 或 [ContentUnavailableView](../../app/src/main/kotlin/moe/kirakira/ui/components/status/ContentUnavailableView.kt)。错误说明经 `ApiFailure.messageRes()` 本地化，重试由调用方的回调执行。

**读取失败是否保留内容**集中在 `ApiFailure.retainsContent`：网络、超时、限流、解析异常和通用业务拒绝保留已确认的数据，只有 `CONTENT_UNAVAILABLE` 与 `SESSION_EXPIRED` 撤下旧内容。`ContentState.readFailed` 和管理列表的 `SettingsLoad.readFailed` 都用这一条规则。账号或查询变化时的重置仍由各状态的所有者负责。

三种呈现模式：

| 模式 | 用途 | 注意 |
| --- | --- | --- |
| `PAGE` | 有界的页面空间，内容居中，不足时可滚动 | 纵向留白放在滚动内容内，滚到末尾时按钮的投影才完整可见 |
| `INLINE` | 由宿主滚动的内容 | 调用方要为“无内容”状态预留剩余的视口高度，用下面的 `ContentStatusViewport` |
| `MEDIA` | 黑底的图片与视频区域 | 极短的视口会隐藏状态图标；宿主要预留关闭、返回与全屏的操作区域 |

- `ContentUnavailableAction` 只接收解析后的文案、回调和启用状态；页面不传按钮样式。`onRetry` 会生成统一文案的主操作，与 `primaryAction` 互斥。
- `emptyIcon` 是可选的空态图形插槽，只传给空状态分支；`emptyDescription` 可以传 `null` 省略辅助说明。
- 资料和统计读取失败时，在内容附近显示组件，不再重复弹 Snackbar。
- 播放器提供独立的 `onRetry` 并绑定 `PlaybackViewModel.play()`，这样失败后的重试不会走“播放／暂停切换”。

[ContentStatusViewport](../../app/src/main/kotlin/moe/kirakira/ui/components/status/ContentStatusViewport.kt) 集中计算列表状态区域的剩余高度。调用方传入与列表共用的实际 `contentPadding`、实测的前置内容高度 `leadingContentHeight`，以及尚未被容器避让的底部遮挡高度 `obscuredBottomHeight`。系统 Insets 和列表留白只从实际 padding 里扣一次，不另外维护合计常数。状态内容比剩余空间高时随列表自然撑开；有记录时的局部错误不使用整页最小高度。

扁平全宽列表用 `ContentStatusList`，它在顶栏之下留 8dp、在底部 Insets 之上留 24dp。8dp 沿用 Material 1／2 的列表首尾留白（M3 只规定列表项内边距）：内容滚到毛玻璃顶栏后方时它不起作用，停在顶部时让首行的按压高亮不贴着顶栏阴影。分段圆角列表自带外边距，不套用这组数值。

### 圆形加载器

[IndeterminateCircularProgressIndicator](../../app/src/main/kotlin/moe/kirakira/ui/components/status/IndeterminateCircularProgressIndicator.kt) 封装官方 Material 2 的 `CircularProgressIndicator`，使用它原生的旋转与圆弧伸缩动画，只把端点固定为圆头、轨道设为透明。尺寸见[加载与刷新](../ui-guidelines.md#加载与刷新)。

### 下拉刷新

[ContentPullToRefresh](../../app/src/main/kotlin/moe/kirakira/ui/components/status/ContentPullToRefresh.kt) 保留官方 `PullToRefreshBox` 的状态、手势与阈值，通过 `PullToRefreshDefaults.IndicatorBox` 绘制白色圆形容器。来源、许可与改动见 [Android 刷新指示器说明](../../third_party/android-refresh/README.md)。

- **阴影只由官方容器承载**。外层不能再加阴影 Modifier，否则容器隐藏之后顶部还会残留一个圆形阴影。
- **拉动阶段**按 [Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html) 和 AndroidX Material `PullRefreshIndicator` 的算法：圆弧随拉动逐步增长，超过阈值后用有上限的非线性张力减缓旋转，而不是在阈值处填满整圈。
- **圆弧与箭头先画进同一个离屏层**，合成后再统一应用透明度；否则两者交叠的地方颜色会变深。
- **刷新结束时**在官方状态回到隐藏位置之前保留旋转图形，用回收距离驱动整个指示器缩小淡出，退场时不会重新出现拉动箭头。退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度低于 1 时自动创建离屏缓冲，把已经位移的容器或外侧阴影裁掉。
- 绘制区域固定为 LTR，RTL 下仍然顺时针旋转。
- `indicatorTopPadding` 只移动覆盖式指示器，不移动滚动视口。

## 列表、菜单与对话框

### 连接列表分组

[ConnectedListGroup](../../app/src/main/kotlin/moe/kirakira/ui/components/list/ConnectedListGroup.kt) 提供满宽、零行间距的分组布局和整组阴影，不绘制背景，也不加内边距，默认不裁剪内容。`SettingsSection` 内部复用它，`title` 默认为 `null`。共享的 `connectedListItemShapes(index, count)` 通过官方 `ListItemDefaults.segmentedShapes` 取得首项、末项和单项的主题圆角。账号侧滑组通过 `clipContent` 按整体外轮廓裁剪。

[ConnectedLazyColumn](../../app/src/main/kotlin/moe/kirakira/ui/components/list/ConnectedLazyColumn.kt) 在官方 `LazyColumn` 之外提供分组声明和统一投影：

```kotlin
ConnectedLazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
    item("header") { SettingsSectionHeader(title) }
    connectedItemsIndexed("rules", entries, key = { _, entry -> entry.value }) { index, entry ->
        SegmentedListItem(
            shapes = connectedListItemShapes(index, entries.size),
            content = { Text(entry.value) },
        )
    }
}
```

- **使用限制**：只支持正向垂直布局；连接行必须满宽，不加外部纵向 padding、粘性标题或位移动画；每个懒列表条目只承载一行。`groupKey` 在同一列表内必须唯一且跨刷新稳定，空组不占条目。
- **投影怎么画**：宿主在绘制阶段读取可见条目，按分组的索引范围匹配，每组复用一个只负责投影的 `GraphicsLayer`。
- **不可见的端点**延伸到视口外一个视口高度，不估算完整的组高，也不测量屏幕外的行。否则视口边缘会出现本不存在的顶部或底部投影。
- **边缘拉伸**：Modifier 的顺序固定为“调用方 modifier、`clipToBounds()`、`overscroll(effect)`、整组阴影”，让投影和内容一起参与系统 overscroll。内部的 `LazyColumn` 接收同一效果的 `withoutVisualEffect()` 包装，只负责传递滚动与 fling；不这样做的话，内容被拉伸时投影不会同步变形。

固定的少量菜单可以作为一个普通的 `ConnectedListGroup` 放进一个懒列表条目；会增长的数据列表保留逐项懒加载和分页。

### 列表快滚条

[LazyListScrollbar](../../app/src/main/kotlin/moe/kirakira/ui/components/list/LazyListScrollbar.kt) 用于已加载内容的正向列表，**不支持吸顶标题和反向布局**。它叠加在列表上，不占宽度。中间位置按可见条目的平均高度估算，拖到两端时直接用 `requestScrollToItem` 请求首项或末项，由 LazyColumn 约束实际边界。每次拖动只保留最新的定位请求。

### 弹出菜单

[MenuPopup](../../app/src/main/kotlin/moe/kirakira/ui/components/overlay/MenuPopup.kt) 使用官方的可聚焦 `Popup` 和 Material 3 Expressive 的定位器，支持点击外部和返回关闭，退出动画结束后才移除 Popup。

- 动画层使用 `LayerOutsets` 为面板的外阴影预留绘制空间，同时不改变菜单的测量和点击边界。见 [Compose 图层外扩说明](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#layer-outsets)。
- 条目使用共享的 `menuItemShapes()` 和 `menuItemColors`；只在两个可见的选项组之间放 `MenuDivider`。
- **毛玻璃的采样源**：Android 12+ 由 `HazeInput.Sources` 采样。普通页面的菜单从 `ActivityNavDisplay` 采样导航内容，视频页的菜单用 `VideoScreen` 的独立状态采样简介和详情，配色格式菜单从所在对话框采样。**驻留播放器和应用内小窗的 `SurfaceView` 不在这些采样节点里**，否则菜单录制 Compose 图层时会扰动视频合成。Haze 会自动对齐跨窗口的坐标，见 [Haze 跨窗口采样说明](https://chrisbanes.github.io/haze/2.1.0/core-concepts/)。
- Android 8.1–11 和没有采样源的预览使用半透明的主题底色。

### 普通对话框

[ActionDialog](../../app/src/main/kotlin/moe/kirakira/ui/components/overlay/ActionDialog.kt) 封装官方 `AlertDialog`。`onDismissRequest` 与标题插槽必填；`icon` 和正文插槽默认为 `null`，省略图标时官方组件会移除对应的空间。

- `actions` 是普通的内容插槽，**每个直接子项是一个按钮**，按主操作、其他操作、取消的优先级提供。调用方不固定按钮宽度，也不用行列容器把整组按钮包起来。
- 布局在测量阶段查询各按钮的最大固有宽度（含单行标签、内边距和最小尺寸），加上间距后与操作区的实际宽度比较：放得下就横向靠右、首项在末端，否则纵向全宽、首项在顶部。排列不写入状态，宽度、字体或语言变化后会重新测量。
- 错误图标用 `icon = { ErrorIcon() }`，危险确认用 `icon = { DestructiveActionIcon(...) }`。
- 生日选择保留官方的 `DatePickerDialog`。

## 按钮

### 按钮加载反馈

按钮通过 `loading` 接收在途操作的状态。加载期间保留正常的前景、容器配色和静止投影，同时锁定点击与选择回调。`enabled` 仍然只控制空闲时能否操作：即使调用方在请求期间传入 `enabled = false`，正在执行操作的按钮仍显示加载外观。

- **带文字的按钮**：[ButtonLoadingIndicator](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ButtonLoadingIndicator.kt) 先测量按钮，再用 Material 的最小触摸目标对齐线定位可见表面，在表面底边叠加 [IndeterminateLinearProgressIndicator](../../app/src/main/kotlin/moe/kirakira/ui/components/status/IndeterminateLinearProgressIndicator.kt)，并沿当前的按钮轮廓裁剪。覆盖层不参与按钮的尺寸计算。进度线的节奏来自 Cerasus，见[动画来源](../../third_party/cerasus-icons/README.md#不确定线形进度动画)。
- **圆形图标按钮**：`CircularButtonContent` 在图标位置显示圆形加载器，图标保留占位和无障碍名称。
- 系统关闭动画时，进度线显示为居中的静态线段。

### 按钮按压轮廓

按场景选择形变还是固定轮廓的规则见[按钮、单选框与阴影](../ui-guidelines.md#按钮单选框与阴影)。

[ButtonPressShapes](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ButtonPressShapes.kt) 交换官方 Expressive 的静止和按压形状：`pressToRoundButtonShapes()` 用于普通按钮（指定高度时传入 `pressToRoundButtonShapes(height)`），`pressToRoundToggleButtonShapes` 保留官方的选中形状。`ShadowButton` 与 `ShadowFilledTonalButton` 默认使用前者；两种实心图标按钮默认是固定圆形。

| 按钮 | 写法 |
| --- | --- |
| 带文字的描边按钮 | `OutlinedButton(shapes = pressToRoundButtonShapes(), …)` |
| 描边图标按钮 | `OutlinedIconButton(shape = CircleShape, …)` |
| 平面文字按钮 | 共享的 `FlatTextButton` |
| 固定轮廓的例外 | 让静止与按压形状相同，如 `ButtonShapes(CircleShape, CircleShape)`；共享组件识别到相同形状后直接使用固定轮廓 |

**形变期间 `Shape` 对象的身份必须保持稳定。** Material 3 的水波纹节点工厂会比较形状，如果每帧给按钮传一个新的 `Shape`，节点重建会打断按压水波纹。所以共享动画保持同一个 `Shape` 对象，在 `createOutline` 里读取动画进度并插值轮廓。

[MorphingButtonShape](../../app/src/main/kotlin/moe/kirakira/ui/components/button/MorphingButtonShape.kt) 管理轮廓的起点、目标和进度：返回原起点时交换端点并反转速度；目标轮廓改变时从当前可见的轮廓继续，不跳回旧的静止形状。同一进度的插值结果同时用于表面和双层投影。

### 选择按钮阴影

[ShadowToggleButton](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ShadowToggleButton.kt) 封装官方 `ToggleButton`，用于独立的选择按钮。可选的 `icon` 和末尾的 `content` 插槽都会收到**当前绘制分支**的 `checked`：随状态变化的内容应读取这个参数，前景才能跟着对应的分支过渡。原生 elevation 设为 `null`，投影规则见[按钮动态阴影](theme.md#按钮动态阴影)。

`FollowButton` 基于它实现。它用 `visualChecked` 在禁用期间保留原来的视觉选择，恢复可用后再同步；无障碍的选中语义始终使用实际的关注状态。`shadowWhenChecked = false` 让已关注状态的投影淡出。

两者通过 [ButtonColorReveal](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ButtonColorReveal.kt) 绘制背景与前景的**圆形扩散**：

- 圆心是实测的图标中心；没有图标时是可见按钮的中心。触摸、键盘、无障碍和程序更新用同一条规则。
- 前景只保留基础状态和一个扩散层：圆内显示新内容，圆外保留旧内容，两者按同一圆心和半径互补裁剪。只有当前目标分支提供内容语义。
- 快速反向切换时，现有的扩散层沿当前半径收回，再次切换则从当前进度继续展开；不叠加冻结的圆形层。
- 加载期间保留当前的前景和背景并暂停扩散，操作完成后向最终状态扩散。
- 内容宽度的变化放在官方按钮**内部**，表面、轮廓和投影才会跟随实际的布局尺寸。

动画状态只存在于组合内存，不改变业务状态和请求时机。

### 危险操作

[DestructiveActions](../../app/src/main/kotlin/moe/kirakira/ui/components/button/DestructiveActions.kt) 提供共用的危险配色。`ShadowButton(destructive = true)` 的危险语义优先于调用方传入的 `colors`；安全流程通过 `AccountFlowSubmitButton.destructive` 传递。

[DestructiveDecoration](../../app/src/main/kotlin/moe/kirakira/ui/components/button/DestructiveDecoration.kt) 在可用的危险实心按钮的内容后方绘制漂浮的 Cerasus `close` 图标，悬停时放大并旋转。来源与移植差异见 [Cerasus 图标与动画](../../third_party/cerasus-icons/README.md#危险按钮装饰)。

- 装饰是与按钮可见表面同尺寸的独立绘制节点，裁剪到与投影共用的动画轮廓；不占布局空间，不添加语义。
- 多个图形共用一份固定尺寸的矢量栅格缓存，只靠画布缩放产生大小差异。如果每个图形按自己的尺寸改写缓存，同一帧里反复改写会造成图形截断。
- 禁用、加载或系统动画缩放为零时不组合循环动画。

### 黑底毛玻璃按钮

[BackdropButtons](../../app/src/main/kotlin/moe/kirakira/ui/components/button/BackdropButtons.kt) 提供资料页使用的 `BackdropIconButton`：在官方 `FilledIconButton` 的透明表面内部绘制背景，模糊不延伸到最小触摸目标的留白，也不作用于图标或水波纹。

- 按钮显式接收页面拥有的 `HazeState`，用 `HazeInput.Sources` 采样，并通过 `ClearWhenUnavailable` 清除失效的源。
- **不使用原生 backdrop**，原因与[毛玻璃应用栏](#毛玻璃应用栏)相同：即使按钮自己没有阴影，原生 backdrop 仍可能与同一页面的 elevation 和返回淡出产生合成异常。
- Android 12+ 挂载模糊；Android 8.1–11 只画同样的半透明黑底。

播放器的控制按钮不接入这个组件，也不接入 Haze 采样。

## 选择控件

### 分段选择

[Segmented](../../app/src/main/kotlin/moe/kirakira/ui/components/selection/Segmented.kt) 通过 `items` 接收互不相同的选项值，`selected` 由调用方持有，传 `null` 时隐藏指示块。`content` 为每项提供文字和可选的图标，组件负责等宽布局和单选语义。交互参考 [Cerasus Segmented](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/03047bcec32cc5007a47440c590d643a2e0db04b/components/Segmented/Segmented.vue)。

- 已有选择时，滑块使用与底栏相同的按压收缩、弹性滑动和跟手弹簧（共用 `SlidingSelection`）。
- 空选时，按下即在手指处显示滑块并跟手；键盘、无障碍和程序设置则从目标分段的中心展开。
- 超过横向触摸阈值后才接管手势，选中新分段时只提交一次。取消手势或松手落在轨道外时回到原选择。
- 轨道直接放在灰色页面上时，通过 `parentContainerColor` 传入页面底色。内阴影画在轨道背景与指示块之间。
- 禁用时用无投影的中性色保留选中提示。

### 滑杆点击动画

[SmoothSlider](../../app/src/main/kotlin/moe/kirakira/ui/components/selection/SmoothSlider.kt) 用于设置、倍速和播放进度。按下轨道即更新实际数值并回调，滑块和已填充轨道用短动画移到目标，不等松手；开始拖动时取消动画并直接跟手。

实现上把官方 `Slider` 的**真实输入状态**和**局部显示状态**分成两层：

- 输入层保留真实的状态和交互源，只是隐藏绘制；它在按下事件的 Final 阶段消费官方已记录的按压偏移，按官方的几何、布局方向和步长计算目标。
- 显示层禁用输入，只用于显示，不回写业务；它关闭了 `shouldAutoSnap`，分档的滑杆也能连续移动。
- 两层共用原有的插槽和交互反馈，根节点统一暴露真实数值和可调节语义。松手不重复派发数值，完成回调沿用官方的时机。

首次显示、外部状态更新、键盘和无障碍调节不走动画，直接同步。

## 页面骨架

### 毛玻璃应用栏

[FrostedScaffold](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/FrostedScaffold.kt) 为普通页面创建独立的 `HazeState`：用 `hazeSource` 采样内容，单独绘制顶部背景，再绘制透明的官方顶栏。文字和图标不参与模糊。背景的形状、alpha 与原生阴影如何承载，见[栏面阴影](theme.md#栏面阴影)。

- `frostedBarBackground` 的可选参数 `hazeState` 让自定义布局显式复用同一套背景样式；`shadowElevation` 默认为 0，普通顶栏、视频页的顶部共享背景和胶囊底栏显式传入 `BarShadowElevation`。
- `FrostedScaffold.topBarShadowElevation` 供 `AccountFlowScaffold` 按正文的滚动位置调整顶栏高度。
- 胶囊底栏的背景使用外层主页面的采样状态，内部各标签页的顶栏使用独立的状态。
- 滚动列表把顶部和底部的安全区域放进 `contentPadding`（滚动的 Column 则放在 `verticalScroll` 之后）：初始时避开栏面，滚动后内容能进入栏后。
- 视频页为 Tab 栏创建独立的 `HazeState`，只把下方的 Pager 接入采样，播放器不参与。横屏双栏时这个背景只覆盖右栏的 Tab。

**为什么不用原生 backdrop**：在 Android 17 模拟器（`CP41.260828.004.A7`，SkiaGL）上复现了 `RenderNode.setBackdropRenderEffect()` 与半透明父层、elevation 同时使用时的灰框和内部矩形异常。

| 对照 | 结果 |
| --- | --- |
| 父层 alpha 为 1、0.5、0.2，普通 elevation 加 Haze Sources | 正常 |
| 同样条件，Haze Backdrop | 异常；强制 Offscreen 仍异常 |
| 去掉 Compose 和 Haze，只用 Android View 与 RenderNode | 仍能复现 |
| 去掉 backdrop | 恢复正常 |

证据把问题定位到系统原生 backdrop 的合成路径，但尚未定位内部实现的具体错误，也没有在所有设备上验证。因此普通栏面和黑底按钮统一使用 Haze 的 Sources 采样，应用启动时不启用原生 backdrop 的全局实验开关。

### 固定页面顶栏

[PageTopAppBar](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/TopAppBar.kt) 封装 Material 3 的小尺寸 `TopAppBar`，不接入滚动行为；透明的栏面由 `FrostedScaffold` 提供毛玻璃与阴影。普通顶栏的配色复用同文件的 `appTopAppBarColors`。

```kotlin
val layoutDirection = LocalLayoutDirection.current
FrostedScaffold(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
        PageTopAppBar(
            title = stringResource(R.string.me_settings),
            onBack = onBack,
        )
    },
) { innerPadding ->
    Column(
        modifier = Modifier
            .consumeWindowInsets(innerPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
    ) {
        // 页面内容
    }
}
```

[RailPageFrame.kt](../../app/src/main/kotlin/moe/kirakira/ui/adaptive/RailPageFrame.kt) 提供侧栏一级页面共用的水平框架：先避让横向的系统 Insets，再居中放置限宽的框架。顶栏通过 `topAppBarContentWidth` 放置标题和操作；首页、主搜索和历史共用 `browsePageMaxWidth`；设置目录与账号正文由 `pageContentWidth` 放在各自的内容区内。

### 顶栏图标底纹

[ShadingIcon](../../app/src/main/kotlin/moe/kirakira/ui/components/icon/ShadingIcon.kt) 是顶栏后方的淡色装饰图标。在承载顶栏的 `Box` 里先画底纹，再画透明的顶栏；用 `Modifier.matchParentSize()` 让底纹跟随顶栏的实际尺寸而不参与测量。底纹可以延伸到状态栏后方，**不要给它加顶部的系统内边距**。

```kotlin
Box {
    ShadingIcon(
        icon = R.drawable.ic_icon_settings,
        modifier = Modifier.matchParentSize(),
        rotating = true,
        endPadding = 0.dp,
        alignment = Alignment.BottomEnd,
        offset = DpOffset(32.dp, 32.dp),
    )
    PageTopAppBar(
        title = stringResource(R.string.me_settings),
        onBack = onBack,
    )
}
```

`rotating` 默认关闭，此时不创建无限动画。使用 `SettingsScaffold` 的设置子页传入各自的图标即可；头像裁剪页不绘制底纹。

## 搜索

### 可复用的顶栏搜索

[PageSearchBarAnchor](../../app/src/main/kotlin/moe/kirakira/ui/components/search/PageSearchBar.kt) 供设置页与历史页放在固定标题栏下方。它接收页面拥有的 `TextFieldState`、`SearchBarState`、提示文案、可用状态和最大内容宽度，使用官方的 `SearchBar`；数据过滤和结果列表归页面。

展开后的 `PageSearchOverlay` 由 `PageSearchOverlayLayout` 按收起搜索栏的实际边界和 `SearchBarState` 的进度，计算位置、尺寸、圆角和结果的透明度。来源与局部返回的适配见 [AndroidX 搜索布局说明](../../third_party/android-search/README.md)。

- 设置页在全屏 Dialog 里挂载覆盖层，历史页在 Activity 的来源 Scene 里挂载（原因见[历史搜索](content.md#历史搜索与收藏)）。
- 局部返回的顺序：先收起键盘，再收起搜索，最后才由页面处理退出。清空只清空文字，提交只收起键盘。
- 展开动画结束后才请求输入焦点。
- 设置搜索和标签选择器在各自的 Dialog 窗口调用 `WindowCompat.enableEdgeToEdge`；结果视口用 `imePadding` 避让键盘。

主搜索的关键词框、已选标签框和全屏标签选择器共用 [SearchFieldContainer](../../app/src/main/kotlin/moe/kirakira/ui/components/search/QuerySearchBar.kt) 与 `SearchQueryField`。

[dismissKeyboardOnBlankTap](../../app/src/main/kotlin/moe/kirakira/ui/components/form/KeyboardDismissal.kt) 在指针的 Final 阶段观察没有被控件消费的短按，只清除焦点并隐藏键盘，不消费事件。**它由各页面显式启用**，普通输入组件和认证表单不会自动继承。在 Dialog 里要挂在 Dialog 的内容上，才能读到那个窗口的焦点管理器和键盘控制器。

### 全屏标签选择器

[TagSearchDialog](../../app/src/main/kotlin/moe/kirakira/ui/components/search/TagSearchDialog.kt) 供视频搜索和屏蔽标签设置共用，负责全屏窗口、固定顶栏、输入框、已选的 Chip、候选列表和键盘交互。调用方传入查询状态和选中的标签，并按自己的场景处理勾选与确认。

- 两个 ViewModel 都通过 `TagSearchLookup` 在各自的账号任务范围内读取候选：输入防抖 500 毫秒，键盘搜索和重试立即查询；查询变化时清空旧结果，更换账号或关闭选择器时取消查询。
- 窗口使用官方 `Dialog`，设置 `usePlatformDefaultWidth = false` 与 `decorFitsSystemWindows = false`，窗口内有独立的返回处理。
- **视频搜索的共享过渡**：`TagSearchAnchor` 记录主搜索框的实测屏幕边界，Dialog 把它换算成本地坐标，并在绘制副本期间隐藏 Activity 上的那个搜索框。圆角底色与内阴影通过 `sharedElement` 共用一个图层，前景通过 `sharedBounds` 交叉淡入淡出。窗口自带的默认动画和遮罩要关闭，否则会与共享过渡叠加。
- 关闭和确认先暂存为界面上的“退出意图”，反向动画完全结束后才发送业务事件，其间忽略重复操作。账号 revision 变化时立即销毁整个窗口，不等退出动画。
- 设置页从添加按钮打开时没有来源边界，使用窗口内容的淡入淡出。

## 账号流程布局

认证页与账号安全的操作页共用 [AccountFlowScaffold](../../app/src/main/kotlin/moe/kirakira/ui/components/form/AccountFlowScaffold.kt)。尺寸与间距见[账号表单与保存](../ui-guidelines.md#账号表单与保存)。

### 骨架

- **滚动状态由宿主持有**。`AccountFlowScaffold` 创建 `rememberScrollState()` 并通过正文插槽传出，页面把它交给 `AccountFlowColumn` 的必填参数 `scrollState`。宿主据此决定顶栏阴影，页面不单独配置。
- **键盘避让只做一次**。宿主的根节点统一应用 `imePadding`，表单和底栏不再重复处理。Scaffold 把底栏的实测高度交给正文的滚动 padding，末项才能滚到按钮上方。
- 主操作通过 `bottomBar` 接入 `AccountFlowActionBar`（复用 [FormActionBar](../../app/src/main/kotlin/moe/kirakira/ui/components/form/FormActionBar.kt)），按钮是 `AccountFlowSubmitButton`；加载时仍然测量标签，以适应字体缩放。
- `AccountFlowSection` 只负责没有底板的标题、上下文和表单布局，接收可空的 `title`、可选的 `supportingContent` 和 `actionContent`。操作插槽按完整操作内容的固有宽度决定是否换行，避免把长按钮挤进剩余的宽度；没有标题、上下文和操作时，不组合页头及其间距。
- 发送验证码的操作移到验证码旁边之后，“密码到验证码”这类 IME 的“下一项”需要用显式的焦点目标连接。
- [FormTextButton](../../app/src/main/kotlin/moe/kirakira/ui/components/form/FormTextButton.kt) 按 `FormButtonEdge.START／END` 选择对齐边，把按钮整体偏移一个内容内边距，使可见文字与标题和输入框的边缘对齐；触摸目标和涟漪不变。

### 输入框

[FilledTextField](../../app/src/main/kotlin/moe/kirakira/ui/components/form/FilledTextField.kt) 接收 `TextFieldState`，内部是 `BasicTextField(state = …)` 加官方的 `TextFieldDefaults.decorator`。

- **内阴影的绘制顺序**。`FilledTextFieldContainer` 先画官方的背景容器，再由一个同级、同尺寸的空 `Box` 应用 `innerShadow`，最后由外层的官方 `indicatorLine` 画底线。当前 Compose 的 `innerShadow` 先于它后面的内容绘制，所以不能直接挂在不透明的背景容器外层，否则阴影会被背景盖住。
- **明暗的判断**用当前主题 `surface` 与 `onSurface` 的明度关系，不依赖系统模式，这样应用内手动切换明暗时也正确。
- **错误**：传入 `errorMessage` 时，组件提供错误图标、supporting text 的过渡和具体的错误语义。只传 `isError` 时回退到项目自己的 `text_field_invalid_input` 字符串，不引用 Compose 的私有资源。
- **编辑状态的归属**：编辑器持有文字、光标、选区和 IME 组合态，ViewModel 接收字符串并负责校验。[TextFieldEdits](../../app/src/main/kotlin/moe/kirakira/ui/components/form/TextFieldEdits.kt) 的绑定只转发已结束组合的文字变化，不把普通的回显写回编辑器；提交前同步读取当前输入，清空或外部替换才显式更新编辑状态。

[PasswordTextField](../../app/src/main/kotlin/moe/kirakira/ui/components/form/PasswordTextField.kt) 用 `BasicSecureTextField` 处理密码编辑：可以粘贴，但即使显示明文也不能复制、剪切或拖出。显隐状态由调用方持有，只存内存。

[AccountFlowFeedback](../../app/src/main/kotlin/moe/kirakira/ui/components/form/AccountFlowFeedback.kt) 在组合内保留非敏感的提示文字，供退出动画完成前绘制；退出中的提示清除无障碍语义。`textFieldSupportingError` 在字段错误退出完成后返回空插槽，输入框恢复正常高度。

### 验证码输入

[OtpInputField](../../app/src/main/kotlin/moe/kirakira/ui/components/form/OtpInputField.kt) 的六个格子只是视觉呈现，底层是**一个** `BasicTextField(state = …)`。

- 完整的编辑器保留布局范围和语义，整个输入组是一个编辑目标，六格内容不重复提供语义。粘贴、退格、全选替换和 IME 操作因此都正常。
- 点击已填的格子会选中对应的字符，点击空格则定位到已有代码的末端。
- 字符输入时有入场动画；删除或替换时旧字符立即移除，不做退场动画，否则会和空格中央的光标重叠。
- `InputTransformation` 按后端的字符集过滤并截断到六位，不自动提交。数字输入和字母数字备用码分别使用数字键盘和 ASCII 键盘。
- TOTP 登录在截断**之前**识别完整的恢复码：粘贴完成后用同一份编辑状态切换到长码输入，不触发认证。

## 文字、数字与图标

### 数字动画与字数计数

[AnimatedNumberText](../../app/src/main/kotlin/moe/kirakira/ui/components/text/AnimatedNumberText.kt) 封装 NumberFlow 的逐位滚动，数位增减时平滑过渡数字区域的宽度，并启用 OpenType 的 `tnum` 等宽数字（实际效果取决于字体是否支持）。来源、许可与固定提交见 [NumberFlow](../../third_party/number-flow/README.md)。

- 只负责数字；固定的单位、上限和负号放在动画区域之外。无障碍语义读取完整的目标数字，不逐字符朗读。
- 提供 Double、Long、Int 重载。互动和统计的整数走 Long 接口，不转成浮点数。
- [AnimatedCountLabel](../../app/src/main/kotlin/moe/kirakira/ui/components/text/AnimatedCountLabel.kt) 从字符串资源的 `%1$s` 占位符拆出静态的前后文；`AnimatedPageCount` 对两个数字分别做动画。复数资源由实际数量选择。
- **受限宽度下的回退**：按完整文本的实测宽度退回普通的文字布局，标签保留换行。固有尺寸的查询也交给完整的普通文本，因为像评论跳页上限这样的辅助文字，输入框会先查询它的固有高度来预留空间。
- **什么时候不播放**：初次显示、未知值变为已知值时直接显示当前值。数字只随现有状态更新，不做乐观计数。
- **按身份重置**：不同对象之间不应该出现“从上一个数字滚到这个数字”。视频通过 `LocalNumberAnimationIdentity` 按会话和内容标识重置，资料按 UID、搜索按 generation、账号相关页面按会话修订隔离；刷新同一个对象则保留数字组件。

[CharacterCount](../../app/src/main/kotlin/moe/kirakira/ui/components/form/CharacterCount.kt) 组合动态的当前字数和静态的斜杠、上限，并合并为一条完整的计数语义。

验证码倒计时、设置读数、播放时间、日期和 ID 不接入数字动画。

### 用户名与日期

- [UsernameText](../../app/src/main/kotlin/moe/kirakira/ui/components/text/UsernameText.kt) 接收原始的 username 并统一添加 `@`；空白值不产生文字和布局。列表的可选副标题插槽仍要在调用处按空白值省略，否则会留下插槽间距。
- [DateFormatting](../../app/src/main/kotlin/moe/kirakira/ui/components/text/DateFormatting.kt) 的 `dateOnlyText` 与 `dateTimeText` 使用应用配置的 Locale，应用单独选择语言后日期随之更新。生日使用 `LocalDate` 的重载，不经过时区转换。

### 图标划线过渡与矢量路径

[AnimatedSlashIcon](../../app/src/main/kotlin/moe/kirakira/ui/components/icon/AnimatedSlashIcon.kt) 为密码可见性按钮和播放器的弹幕开关提供划线过渡。原始 VectorDrawable 的路径只在创建时解析一次；静止的两端直接绘制原始图标路径，保持原有的几何细节。

`rememberSlashIconProgress` 的初始值取当前状态，所以首次显示不播放。播放器在控制栏显隐分支**之外**持有这个状态，重新显示控制栏时不会重播；弹幕设置异步读入时通过 `ready` 建立初始进度。图标只是视觉过渡，业务状态立即切换。

[VectorPaths](../../app/src/main/kotlin/moe/kirakira/ui/components/icon/VectorPaths.kt) 按资源顺序读取 VectorDrawable 的全部 pathData，并为使用绝对 M 命令的动画图标提供轮廓拆分。它只负责读取；各图标自己选择路径、做坐标变换和动画，不能假设所有矢量图标的结构相同。

### 头像

`PlaceholderAvatar` 和 [AccountAvatar](../../app/src/main/kotlin/moe/kirakira/ui/components/avatar/AccountAvatar.kt) 共用同一套中性占位配色，不受强调色影响。`AccountAvatar` 的 `showLoadingIndicator` 按实际的图片 URL 记住局部的加载状态，加载期间用圆形加载器替代默认的人形图标；目前只有个人主页的头像开启。

## 插画

空状态和占位页的插画位于 [ui/components/illustration](../../app/src/main/kotlin/moe/kirakira/ui/components/illustration)，都用 Compose 的图形和计算路径绘制，没有打包参考图。它们不增加无障碍语义，标题沿用原有的空状态文案。

### 搜索空结果图形

| 图形 | 用在哪里 | 来源 |
| --- | --- | --- |
| `SearchHistoryEmptyLogo`、`SearchVideosEmptyLogo` | 主搜索的空历史和空结果 | 前者复用历史时钟的绘制；后者的放大镜采用 Material Icons `search` 的路径比例 |
| `HistorySearchLogo` | 历史页的空记录、搜索无匹配和游客登录提示 | 参考 Android L Clock，见 [Android Classic Icons](../../third_party/android-classic-icons/README.md) |
| `SettingsSearchLogo` | 设置搜索无匹配、双栏设置的空详情 | 移植 Cerasus 的齿轮，见 [Cerasus 图标说明](../../third_party/cerasus-icons/README.md#设置搜索空结果-logo) |
| `FavoritesLogo` | 收藏占位页 | Cerasus 的星形轮廓 |
| `FollowingLogo` | “关注”标签页的占位 | 复用底栏关注图标的路径 |

这些图形是参照 Material Design 1 产品图标的纸层、光向与层次原则做的应用设计，不是 Google 官方的产品图标。

**入场动画**由 [IllustrationEntrance](../../app/src/main/kotlin/moe/kirakira/ui/components/illustration/IllustrationEntrance.kt) 统一提供：

- 页面或空态重新显示时从头播放。导航以实际的栈顶为准，预测性返回确认之后目标页才播放。
- 保留在组合里的页签和搜索浮层按当前的显示状态触发；持续无匹配时，输入变化不会重播。
- 进度只在当前组合内持有，在图层和绘制阶段读取。关闭系统动画或静态预览时直接显示终态。

各图形文件里带有终态预览和按固定时刻采样的动画预览，可以用 Android CLI 导出 PNG 检查路径和裁切，命令见[Compose 布局预览](../development.md#compose-布局预览)。

**长阴影的画法**（`FollowingLogo`）：把路径轮廓按弧长采样，将相邻的边沿光向扫掠成四边形，缓存整条投影轮廓。几份投影先用不透明的黑色合成，再统一施加透明度，重叠的地方才不会越叠越深。

## 系统状态栏回顶

`StatusBarScrollToTopHost` 在应用导航之上创建与 Activity 生命周期绑定的 [StatusBarScrollToTopBridge](../../app/src/main/kotlin/moe/kirakira/core/system/StatusBarScrollToTopBridge.kt)。页面通过 [StatusBarScrollToTopEffect](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/StatusBarScrollToTop.kt) 注册自己的 `LazyListState` 或 `ScrollState`。

### 各厂商的机制

| 系统 | 机制 |
| --- | --- |
| Xiaomi／Redmi／POCO | 在 Compose View 下方安装一个透明、可见、同尺寸的原生 `ScrollView` 作为代理，用真实的滚动范围表达“可以回顶”。系统把代理滚回零时，把事件分发给当前的 Compose 目标 |
| OPPO／OnePlus／realme | 前台动态监听 `com.oplus.clicktop` 与 `com.color.clicktop` 广播 |
| 华为／荣耀（兼容 Android 的系统） | 监听 `com.huawei.intent.action.CLICK_STATUSBAR`，并要求发送方具有 `huawei.permission.CLICK_STATUSBAR_BROADCAST` |
| 其他系统 | 不提供入口；保持原有的滚动行为 |

- 小米的代理不接收触摸和焦点，不显示滚动条，也不进入无障碍子树。子 View 的内容高度通过 `minimumHeight` 提供，才能在 `ScrollView` 的纵向无界测量下形成真实的滚动范围。这条路线参考 [status_bar_scroll_to_top 的原生实现](https://github.com/HBWuChang/status_bar_scroll_to_top/blob/main/android/src/main/java/com/xiebian/statusbar_scroll_to_top/StatusBarScrollToTopPlugin.java)及其 [HyperOS 3 验证记录](https://github.com/HBWuChang/status_bar_scroll_to_top/blob/main/VALIDATION.md)。
- 广播用 `ContextCompat.RECEIVER_EXPORTED` 接收来自系统其他进程的事件，进入后台时注销。不在 Manifest 常驻注册，不请求用户权限，不反射厂商的私有 API。

### 谁响应

- 导航 Scene 通过 `LocalStatusBarScrollToTopEnabled` 把响应限制在转场结束后的当前可交互页面；主界面、资料与邀请码的分页只启用当前且已停止切换的页签。
- 同窗口的弹层通过 `StatusBarScrollToTopPauseEffect` 注册一个最高优先级但不可滚动的目标，事件就不会穿透到正文。
- 同一时刻只运行一个回顶任务。页面或页签失活、窗口失焦、进入后台和手动拖动正文都会取消它。
- `StatusBarScrollToTopHeader` 让个人主页的封面和邀请码的统计区在回顶时一起展开。

[SmoothScrollToTop](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/SmoothScrollToTop.kt) 为状态栏、底栏和页内 Tab 的重复点击提供同一个回顶入口。Lazy 列表和网格通过公开的 `LazyLayoutScrollScope` 随实际布局更新剩余的距离，从当前位置连续接近顶部，只在动画结束时对齐首项。

### 尚未验证

小米 HyperOS 已完成实机验收；ColorOS／realme UI、EMUI 和鸿蒙尚未验收。验收范围包括：深列表与仅头部折叠、当前页签独占、重复点击、拖动打断、各类弹层暂停、导航返回、后台恢复，以及全屏与画中画。参考项目的验证不等同于本客户端的实机验收；Debug 构建成功也不代表这些行为已经验证。

## 开发者组件库

关于页的图形 Logo 长按进入 `ComponentGalleryRoute`，代码在 [feature/gallery](../../app/src/main/kotlin/moe/kirakira/feature/gallery)。它用和业务页面相同的共享组件展示各种状态，供开发时对照。

- [ComponentCatalog.kt](../../app/src/main/kotlin/moe/kirakira/feature/gallery/ComponentCatalog.kt) 登记组件的稳定 ID、分组和标题；详情页使用 `ComponentDetailRoute`。
- 详情页的普通选择用 `rememberSaveable`，输入、密码显隐和弹层用 `remember`。“重置”会重建本页的组合及其协程作用域，从而取消模拟加载和 Snackbar 任务。
- 示例正文是面向开发者的中文，入口、标题和无障碍文案仍然同步四种语言。
