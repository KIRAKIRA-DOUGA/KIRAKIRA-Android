# 共享 UI 组件

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护内容状态、连接列表、刷新、顶栏与账号流程布局。开发约束以贡献指南为准。

- [统一内容状态](#统一内容状态)
  - [圆形加载器](#圆形加载器)
- [连接列表分组](#连接列表分组)
- [弹出菜单](#弹出菜单)
- [下拉刷新](#下拉刷新)
- [普通对话框](#普通对话框)
- [共享组件接入](#共享组件接入)
  - [按钮加载反馈](#按钮加载反馈)
  - [滑杆点击动画](#滑杆点击动画)
  - [按钮按压轮廓](#按钮按压轮廓)
  - [选择按钮阴影](#选择按钮阴影)
  - [分段选择](#分段选择)
  - [顶部导航按钮定位](#顶部导航按钮定位)
  - [可复用 UI 参数示例](#可复用-ui-参数示例)
  - [Modifier 工厂示例](#modifier-工厂示例)
  - [毛玻璃应用栏](#毛玻璃应用栏)
  - [黑底毛玻璃按钮](#黑底毛玻璃按钮)
  - [顶栏淡色图标底纹](#顶栏淡色图标底纹)
  - [可选的可折叠大标题栏](#可选的可折叠大标题栏)
  - [可复用的顶栏搜索](#可复用的顶栏搜索)
- [用户名展示](#用户名展示)
- [日期与矢量资源](#日期与矢量资源)
- [数字动画与字数计数](#数字动画与字数计数)
- [图标划线过渡](#图标划线过渡)
- [账号流程布局](#账号流程布局)

## 普通对话框

[ActionDialog](../../app/src/main/java/moe/kirakira/ui/components/ActionDialog.kt) 封装官方 `AlertDialog`，沿用官方形状、排版、间距和图标插槽，背景使用主题 `surface`。`onDismissRequest` 与标题插槽必填，`modifier` 是第一个可选参数并传给根 Dialog；`icon` 和正文插槽默认为 `null`，图标省略时由官方组件移除对应空间。

会话错误通过 `icon = { ErrorIcon() }` 显示错误图标。共享 [ErrorIcon](../../app/src/main/java/moe/kirakira/ui/components/ErrorIcon.kt) 使用 Cerasus `ic_icon_error` 和主题 `error` 色，也用于设置错误卡片和字段校验。

危险确认弹窗通过 `icon = { DestructiveActionIcon(...) }` 让顶部图标与确认按钮共用主题 `error` 色。调用处传入对应操作图标，覆盖清空搜索历史、清除本机账号数据、移除账号、登出、重置隐私设置、离开资料编辑和放弃安全设置修改或未保存的恢复码。组件库的危险确认示例使用同一入口。

标题居中，正文保留调用方布局。`actions` 是普通内容插槽，每个直接子项为一个按钮，按主操作、其他操作、取消的优先级提供；调用方不固定按钮宽度，也不通过行列容器包裹整组按钮。共享布局在测量阶段查询各按钮的最大固有宽度，包含单行标签、内边距及最小尺寸，加上 8dp 间距后与操作区实际宽度比较：能够放下时横向靠右、首项位于末端，否则整组纵向全宽、首项位于顶部。排列不由按钮数量、字符数或业务状态决定，宽度、字体和语言变化后重新测量，不写入排列状态。

主操作使用 `ShadowButton`，次操作使用 `TextButton`，危险操作使用 `ShadowButton(destructive = true)` 或 `DestructiveTextButton`。按钮继承居中的文字样式，纵向时保留自然换行高度；组件不接管业务状态、回调或启用条件。

普通 Dialog 的文字操作（含危险文字操作）显式使用 `ButtonDefaults.ContentPadding`，与填充主操作保持同一套官方内容内边距；高度、最小宽度和触摸目标沿用官方默认，不按截图固定宽高，也不要求不同长度文案的按钮等宽。

会话异常在失效或认证被拒绝时优先展示实心登录按钮，其次为平面重试；其他异常以重试为主操作。存储异常的清除入口作为危险次操作，点击后仍须单独确认，取消／继续使用位于操作区末尾。普通确认、选色及评论跳页均使用此组件；生日选择保留官方 `DatePickerDialog`。

## 统一内容状态

评论与弹幕空状态通过 `ContentStatus.emptyTitle` 和 `emptyIconRes` 分别显示“暂无评论”与对话气泡、“暂无弹幕”与字幕图标；这些参数只影响空状态，加载和错误继续使用统一呈现。

[`ContentUnavailableView`](../../app/src/main/java/moe/kirakira/ui/components/ContentUnavailableView.kt) 统一空内容、内容加载失败及未开放页面占位，`ContentStatus` 接收共享 [ContentState](../../app/src/main/java/moe/kirakira/ui/content/ContentState.kt)，将加载／错误／空状态映射到 `ContentLoadingView` 或该组件。错误说明通过 `ApiFailure.messageRes()` 本地化，业务重试仍由调用方回调执行。

读取失败的保留决策集中在 `ApiFailure.retainsContent`：网络、超时、限流、解析异常及通用业务拒绝均保留当前身份和查询下已确认的数据，只有 `CONTENT_UNAVAILABLE` 与 `SESSION_EXPIRED` 撤下旧内容。`ContentState.readFailed` 统一结束加载并写入失败；管理列表的 `SettingsLoad.readFailed` 使用同一规则，并在访问失效时清除追加分页标记。历史、评论和关注分页保留各自的分页状态结构，但共用该判定。账号／查询变更仍由各状态所有者重置，取消旧任务时不发布失败收尾。历史页面与 Repository 共用 `SessionState.isReadyForContent`，等待初始化身份校验完成后再读取内容。

- `PAGE` 用于有界页面空间，内容居中且不足时可滚动。安全首页和隐私设置的整页错误直接使用 `PAGE`，由 Scaffold 提供顶栏与系统安全区域留白。状态组件把纵向留白放在滚动内容内，滚至末尾时按钮投影完整可见。`INLINE` 适合由宿主滚动的内容，调用方为无内容状态预留剩余视口高度。评论与弹幕扣除计数栏、工具栏及发送框占用，列表底部 Insets 随内容滚动。
- 各呈现模式的状态图标均裸露显示，不绘制背景 Shape，保留图标区域尺寸与间距。页面、列表和黑底媒体中的错误图标统一使用主题 `error` 色，自定义错误图标插槽通过 `LocalContentColor` 取得该颜色。
- `MEDIA` 用于黑底图片与视频区域，组件集中管理黑底配色和紧凑间距；有主操作时，紧凑布局也为按压投影保留两侧及底部空间；极短视口隐藏状态图标，文字与按钮可滚动。宿主预留关闭、返回与全屏操作区域。
- `ContentUnavailableAction` 仅接收资源解析后的文案、回调和启用状态。主操作统一 `Button`，次操作统一 `TextButton`，纵向居中排列，宽度上限 280dp；页面不传入按钮样式或插槽。`onRetry` 自动生成统一文案的主操作，可通过 `retryEnabled` 禁用，与 `primaryAction` 互斥。
- 资料和统计读取失败在对应内容附近显示组件，不重复弹出 Snackbar；操作失败仍由原有表单、Snackbar 或会话弹窗处理。播放器提供独立 `onRetry` 并绑定 `PlaybackViewModel.play()`，避免失败重试走播放／暂停切换。

[`ContentStatusViewport`](../../app/src/main/java/moe/kirakira/ui/components/ContentStatusViewport.kt) 集中计算列表状态区域的剩余高度。调用方传入与列表共用的实际 `contentPadding`、包含间距的实测前置内容高度 `leadingContentHeight`，以及尚未由容器避让的底部遮挡高度 `obscuredBottomHeight`。组件要求有界视口，将剩余高度下限限制为 0dp，通过内容插槽提供带全宽与最小高度的 Modifier；加载、空内容及无内容时的错误使用同一 Modifier。状态内容高于剩余空间时随列表自然撑开并滚动，列表内继续使用 `INLINE`，不嵌套滚动容器。有记录时的局部错误不使用整页最小高度。

首页、搜索、标签候选与详情、历史、关注／粉丝、资料分页、视频简介、评论、弹幕和管理列表复用这一视口。页面仍拥有标题、统计区、输入区及其测量，系统 Insets 与列表留白只从实际 padding 扣除一次，不另外维护合计常数。资料与邀请码分页的列表容器底部先扣除尚未折叠的头部高度，状态区域与列表使用同一个可见视口，不再扣除该高度。

播放历史、历史搜索浮层和关注／粉丝列表通过 `ContentStatusList` 共用视口、840dp 列表宽度上限，以及 Scaffold 留白外的顶部 8dp 和底部 24dp 间距。调用方传入各自的 `LazyListState` 和列表项。

[`ContentLoadingView`](../../app/src/main/java/moe/kirakira/ui/components/ContentLoadingView.kt) 统一中央加载圈容器，内部继续复用 `IndeterminateCircularProgressIndicator`。`ContentStatus` 加载分支按原 `presentation` 接入；资料编辑、隐私、安全、管理及许可证页的独立加载分支传入已避让栏面的页面空间。管理页独立加载与列表共用内容 padding；标签页仅在标签与视频均无数据且同时加载时显示正文加载区域，部分数据就绪后沿用列表内状态。下拉刷新、分页尾部、条目及媒体遮罩在各自区域显示加载反馈。

### 圆形加载器

共享 [IndeterminateCircularProgressIndicator](../../app/src/main/java/moe/kirakira/ui/components/IndeterminateCircularProgressIndicator.kt) 封装官方 Material 2 `CircularProgressIndicator` 的不确定进度重载，使用其原生旋转与圆弧伸缩动画；只将端点固定为 `StrokeCap.Round`，轨道为透明，不自行复制动画或切换全局主题。默认颜色来自 Material 3 `MaterialTheme.colorScheme.primary`，媒体区域可传入白色，原有语义与修饰符继续由调用方提供。

普通加载与播放器中央缓冲使用官方默认 40dp 直径、4dp 线宽。账号行、列表尾部操作和小窗播放控件使用 24dp、2dp 线宽。下拉刷新内部使用 Material 2 原始比例 20dp、2.5dp 线宽。账号行尾部保留 48dp 槽位。Material 2 依赖由 Compose BOM 管理，界面复用 `KIRAKIRATheme`。

## 连接列表分组

单组列表（包括无分类的搜索结果）使用普通 `Column`／`LazyColumn` 和官方 `ListItem`，不添加连接形状、连接容器或整组阴影；多个语义分组才使用下述连接列表组件，不人为拆组。设置搜索结果使用普通 `LazyColumn`，逐项保留稳定 key、名称、路径和点击语义。

[ConnectedListGroup](../../app/src/main/java/moe/kirakira/ui/components/ConnectedListGroup.kt) 提供满宽、零行间距的普通分组布局和固定 1dp 整组阴影，不额外绘制背景或添加内边距，默认不裁剪内容；`SettingsSection` 在内部复用它，`title` 默认为 `null`，省略时直接写 `SettingsSection { … }`，不显示标题或预留标题间距；组间留白和列表项语义保持不变，多组列表中的独立条目在调用处显式包裹单项分组。共享 `connectedListItemShapes(index, count)` 以零圆角 `RoundedCornerShape` 为基础，通过官方 `ListItemDefaults.segmentedShapes` 获取首项顶部、末项底部及单项的主题圆角，中间连接边为直角；选中、按压、聚焦、悬停与拖动形状均沿用基础形状，保留官方颜色反馈与无障碍语义。菜单、设置、账号、标签及播放器面板共用此入口。阴影属于容器或列表宿主，条目不再通过组合局部上下文跳过投影，也不绘制和拼接行级阴影。账号侧滑组继续通过 `clipContent` 按整体外轮廓裁剪，外侧阴影及删除按钮的横向间距保持不变。

[ConnectedLazyColumn](../../app/src/main/java/moe/kirakira/ui/components/ConnectedLazyColumn.kt) 在官方 `LazyColumn` 外提供分组声明及统一投影，接收 `LazyListState`、`modifier`、`contentPadding` 和内容 DSL，固定零行间距与正向垂直布局。

DSL 的普通 `item`、`items` 与 `connectedItemsIndexed(groupKey, items, key, contentType, itemContent)` 在构建内容时一起生成不可变分组索引范围，保留原有条目 key 和 contentType；groupKey 在同一列表内必须唯一且跨刷新稳定，空组不占条目。连接行必须满宽，不加外部纵向 padding、粘性标题或位移动画；每个懒列表条目只承载一行。宿主在绘制阶段读取 `layoutInfo.visibleItemsInfo`，按索引范围匹配可见分组，每组复用一个只负责投影的官方 `GraphicsLayer`，使用完整主题轮廓及固定 1dp `shadowElevation`，不额外绘制背景。

首尾可见时使用行的实测位置；不可见的端点延伸到视口外一个视口高度，不估算完整组高、不测量屏幕外行，避免在视口边缘产生虚假的顶部或底部投影。位置换算包含顶部内容内边距，横向内边距按 RTL 解析，投影裁剪至宿主视口；滚动只更新绘制，离开视口的分组及销毁的宿主释放图层。保留连续整组投影的视觉目标，不保证与旧逐行投影逐像素一致。

边缘拉伸使用每个 `ConnectedLazyColumn` 独立创建的官方 `rememberOverscrollEffect()`。Modifier 顺序固定为调用方 modifier → `clipToBounds()` → `overscroll(effect)` → 整组阴影绘制节点，让投影和内容一同参与系统 overscroll；内部 `LazyColumn` 接收同一效果的 `withoutVisualEffect()` 包装，仅负责传递滚动及 fling 事件，避免重复附加效果节点或内容拉伸后露出未同步变形的投影。系统效果为空时，外层及内部均使用空效果，遵循系统配置；下拉刷新指示器仍由外部刷新宿主独立承载。

标签名称按语言注册分组；标签搜索、屏蔽规则和邀请码列表各注册数据分组，继续逐项懒加载并保留分页。屏蔽管理入口只有固定的 2 项、3 项页面内菜单，各以一个普通 `ConnectedListGroup` 放入管理宿主的一个条目。标题、筛选、空状态和分页按钮使用普通条目，仅在分组边界及独立状态区域设置间距。

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

## 弹出菜单

[MenuPopup](../../app/src/main/java/moe/kirakira/ui/components/MenuPopup.kt) 使用官方可聚焦 `Popup` 和 Material 3 Expressive 的 `Below` 定位器，支持点击外部和返回关闭。缩放与透明度沿用主题 `fastSpatialSpec` 和 `fastEffectsSpec`，退出动画完成后移除 Popup。动画层使用 `LayerOutsets(24.dp)` 为整个面板的外阴影预留绘制空间，保持菜单测量和点击边界。[Compose 图层外扩说明](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers#layer-outsets)

面板四角使用主题 `large`（8dp），背景子节点承载 `MenuDefaults.ShadowElevation` 阴影。内容在同一面板内滚动，上限默认 320dp，配色格式菜单为 240dp。菜单上下留白 4dp，条目使用官方 `SelectableDropdownMenuItem` 和 `MenuDefaults.itemShapes()`，通过 `menuItemColors` 统一透明普通／禁用背景和 `primary/onPrimary` 选中态。调用方提供图标和回调，仅在两个可见组选项之间放置使用 `outlineVariant` 的 `MenuDivider`。

背景复用应用栏的 20dp 模糊、零噪点和主题 `surface` 染色。Android 12+ 由 `HazeInput.Sources` 从 Activity 的导航内容采样，自定义配色格式菜单从所在对话框采样。Haze 自动对齐跨窗口坐标，`ClearWhenUnavailable` 清除失效画面。Android 8.1–11 和无采样源的预览使用 90% 不透明度的主题底色。滚动内容和分割线独立绘制在背景上方。[Haze 跨窗口采样说明](https://chrisbanes.github.io/haze/2.1.0/core-concepts/)

## 下拉刷新

共享 [ContentPullToRefresh](../../app/src/main/java/moe/kirakira/ui/components/ContentPullToRefresh.kt) 保留官方 `PullToRefreshBox` 的状态、手势与阈值，通过 `PullToRefreshDefaults.IndicatorBox` 绘制白色圆形容器，浅深模式保持一致。容器使用官方内置的 `PullToRefreshDefaults.Elevation` 高度阴影，与容器共享位移和顶部裁剪，未拉动且不刷新时由官方宿主关闭投影。外层不额外添加阴影 Modifier，避免容器隐藏后顶部仍残留圆形阴影；这一约束同样适用于没有实时模糊的 Android 8.1–11。内部圆弧使用 `primary`、20dp 外径、2.5dp 线宽、透明轨道与 `StrokeCap.Round`。

拉动反馈依据 [Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html) 和 AndroidX Material `PullRefreshIndicator` 的计算方式：跳过前 40% 拉动距离，后续逐步增长至最大 80% 圆弧；超出阈值时使用有上限的非线性张力，使旋转逐渐减缓，而不是在阈值处填满整圈。未达阈值时透明度为 30%，达阈值后以 300ms 线性补间增至 100%，拖回时反向恢复。箭头采用 MD2 的 10 × 5dp 实心三角比例，圆化三个角并按拉动进度等比缩放，沿圆弧末端切线指向刷新方向。仅该绘制区域固定 LTR，确保 RTL 下仍顺时针旋转。

拉动阶段的圆弧与箭头先以不透明颜色绘制到同一 `Canvas.saveLayer`，合成后统一应用阈值透明度，避免交叠处变深。局部图层边界向外扩展，容纳圆弧外的箭头，不包含白色容器及阴影。

刷新阶段复用 `IndeterminateCircularProgressIndicator` 的官方 Material 2 动画，拉动与旋转图形以 Material 2 的 100ms `Crossfade` 切换，不套用 Expressive 弹簧。刷新完成后，在官方状态回到隐藏位置之前保留旋转图形，用回收距离驱动整个指示器缩小、淡出，避免退出时重新出现拉动箭头。退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度低于 1 时自动创建局部离屏缓冲，裁掉已经位移的容器或外侧阴影。业务刷新状态仍由调用方提供，局部标记仅控制退场显示，不延长请求或重复触发刷新。来源、许可与改动说明见 [Android 刷新指示器说明](../../third_party/android-refresh/README.md)。

## 共享组件接入

### 按钮加载反馈

实心、浅色、文字、切换与图标按钮可传入 `loading`。加载期间按钮保持原文案与图标，禁用点击并通过本地化状态描述和不确定进度语义反馈。发送验证码、关注和评论／弹幕发送等入口依据各自的在途操作显示进度。

`ButtonLoadingIndicator` 先测量按钮，再用 Material 的最小触摸目标对齐线定位可见表面。在表面底边叠加 4dp 高的 `IndeterminateLinearProgressIndicator`，沿当前按钮轮廓裁剪，轨道透明。默认使用主题 `onSurface`，自定义背景可传入配套前景色。覆盖层不参与按钮尺寸计算，读屏通过按钮本身获取进度。 [按钮加载组件](../../app/src/main/java/moe/kirakira/ui/components/ButtonLoadingIndicator.kt)

两段圆角进度线按 Cerasus 的 MD2 式节奏伸缩，沿布局方向扫过轨道，每 2 秒循环一次。系统关闭动画时，显示居中的静态圆角线段。 [共享线形进度条](../../app/src/main/java/moe/kirakira/ui/components/IndeterminateLinearProgressIndicator.kt) · [动画来源](../../third_party/cerasus-icons/README.md#不确定线形进度动画)

### 滑杆点击动画

[`SmoothSlider`](../../app/src/main/java/moe/kirakira/ui/components/SmoothSlider.kt) 用于设置、倍速、播放进度和组件库中的可交互横向滑杆。按下轨道即更新实际数值与业务回调，滑块及已填充轨道使用 180ms emphasized 动画移到目标，不等待松手或手指移动；连续按下从当前可见位置继续，开始拖动时取消动画并直接跟手。首次显示、外部状态更新、键盘和无障碍调节直接同步，动画遵循系统设置，倍速原有的数字翻滚保持不变。

组件将官方 `Slider` 的真实输入状态与局部显示状态分开，复用官方 Thumb、Track、步长定位和键盘处理。输入层在按下事件的 Final 阶段消费官方已记录的按压偏移，按官方几何、布局方向和步长计算目标，再由调用方接收或归一化；松手不重复派发数值，完成回调仍沿用官方时机。显示状态关闭 `shouldAutoSnap`，让分档滑杆也能连续移动；它只供显示，不回写业务。输入层保留真实状态与交互源，仅隐藏绘制；显示层禁用输入，两层共用原有插槽和交互反馈，根节点统一暴露真实数值及可调节语义。显示状态不保存到磁盘或恢复状态，离开组合时取消动画。

### 按钮按压轮廓

按场景选择形变或固定轮廓的规则集中在[按钮、单选框与阴影](../../CONTRIBUTING.md#按钮单选框与阴影)，阴影有无不决定按压行为。

[`ButtonPressShapes`](../../app/src/main/java/moe/kirakira/ui/components/ButtonPressShapes.kt) 交换官方 Expressive 的静止和按压形状，按尺寸保留各自的圆角。独立选择按钮通过 `pressToRoundToggleButtonShapes` 保留官方的选中形状。

[`ShadowButtons`](../../app/src/main/java/moe/kirakira/ui/components/ShadowButtons.kt) 的 `ShadowButton`、`ShadowFilledTonalButton` 默认使用 `pressToRoundButtonShapes()`。`ShadowFilledIconButton`、`ShadowFilledTonalIconButton` 默认使用 `IconButtonShapes(CircleShape)`，静止和按压时均为圆形。四类组件通过 `shapes` 接收轮廓配置。表面使用固定 `shape` 重载接收当前轮廓，投影与危险装饰共用该轮廓。

共享动画在形变期间保持 `Shape` 对象身份稳定，在 `createOutline` 中读取动画进度并插值轮廓。Material 3 的水波纹节点工厂会比较形状；不能每帧向按钮传入新的 `Shape`，否则节点重建会打断按压水波纹。

[`MorphingButtonShape`](../../app/src/main/java/moe/kirakira/ui/components/MorphingButtonShape.kt) 管理圆角轮廓的起点、目标与 `Animatable` 进度：返回原起点时交换端点并反转速度，目标轮廓改变时从当前可见轮廓继续，不重置为旧静止形状；非 `CornerBasedShape` 直接切换。同一进度的插值结果复用于表面与双层投影，进度在轮廓计算阶段读取。普通按钮使用主题 `defaultEffectsSpec`，与官方 Button 保持一致；固定轮廓场景继续跳过动画，不额外移动按钮内容。

指定按钮高度时传入 `pressToRoundButtonShapes(height)`，内边距、文字及图标继续使用同尺寸的官方配套 API。带文字的描边按钮使用官方 `OutlinedButton(shapes = pressToRoundButtonShapes(), …)`，描边图标按钮使用 `OutlinedIconButton(shape = CircleShape, …)`。平面文字按钮使用共享 `FlatTextButton`，由官方 `TextButton` 的形变重载接收同一套形状，并沿用文字按钮的默认配色、内边距和涟漪。裸图标按钮使用官方 `IconButton` 的默认圆形。

固定轮廓例外显式令静止与按压形状相同，例如侧滑删除使用 `ButtonShapes(CircleShape, CircleShape)`，图片查看器关闭按钮使用 `IconButtonShapes(CircleShape, CircleShape)`。共享组件识别相同形状后直接使用固定轮廓，仍按原规则处理投影。FAB、发送按钮及媒体叠层按钮继续使用各自已有的固定 `shape`。

开发者组件页的按钮分类使用与业务页面相同的组件，展示带文字按钮的形变、固定圆形图标按钮、工具区和 FAB，以及禁用、加载、选择状态和不同尺寸。

### 选择按钮阴影

[`ShadowToggleButton`](../../app/src/main/java/moe/kirakira/ui/components/ShadowToggleButton.kt) 封装官方 `ToggleButton`，用于独立选择按钮。调用方提供 `checked`、`onCheckedChange`、根节点 `modifier`，并可传入官方 `buttonSize`、`shapes`、`colors`、`border`、`contentPadding` 和 `interactionSource`。可选 `icon` 与末尾 `content` 插槽保留官方图标尺寸、间距及文字排版。两者接收当前绘制分支的 `checked`，状态相关内容应读取该参数，让前景跟随该分支过渡。

选中与按压轮廓同时用于表面和投影，原生 elevation 设为 `null`，避免重复阴影。投影复用共享 `buttonShadow`，颜色取当前选中状态的实际容器色，禁用时立即移除。高度和强度遵循[按钮动态阴影](theme.md#按钮动态阴影)。

`FollowButton` 通过 `ShadowToggleButton` 渲染，提供关注文案、图标、配色和无障碍描述。它用 `visualChecked` 在禁用期间保留原来的视觉选择，恢复可用后同步轮廓、背景和前景。按钮的选中语义使用实际关注状态。`shadowWhenChecked = false` 让已关注状态的投影淡出，过渡中的投影沿用未关注时的容器色。 [按钮动态阴影](theme.md#按钮动态阴影)

两者通过 `ButtonColorReveal` 和 `ButtonRevealContent` 共同绘制背景与前景：有图标时以实测图标中心为圆心，没有图标时以可见按钮中心为圆心；图标中心通过布局坐标转换到按钮可见表面，随尺寸动画保持对齐。状态更新后以 600ms、`FastOutSlowInEasing` 将新颜色圆形扩散至整个表面，遵循系统动画时长缩放，触摸、键盘、无障碍及程序更新均使用同一起点规则。背景裁剪复用当前动画轮廓，并按官方最小交互区域的对齐线避开触摸留白；官方按钮容器透明，保留边框、涟漪及选择语义。前景在过渡期间只保留基础状态与单一扩散层，按各自状态提供内容和前景色；圆内显示新内容，圆外保留旧内容。前景将按钮坐标转换到文字／图标自身坐标，以同一图标中心或按钮中心起点、半径和进度互补裁剪；只保留当前目标分支的内容语义，关注按钮从当前可见文案提供本地化操作名称。快速反向切换时沿用图标中心或按钮中心起点及当前半径，让现有扩散层收回至 0；再次切换则从当前进度继续展开，不追加冻结的圆形层。恢复基础状态时目标宽度立即转向基础分支，避免文字碎片与旧宽度滞留。主题配色引入第三种颜色时将上一目标归为基础，仅保留一个新扩散层。加载禁用期间只显示官方禁用容器色，暂停扩散并保留原状态背景；恢复可用后才向最终状态扩散，同一目标从已有进度续播，剩余时长按至目标进度的距离计算。状态未变且无中断层时直接恢复，不播放状态扩散。动画状态只驻留组合内存，不改变业务状态和请求时机。

两种按钮的 `ButtonRevealContent` 均以当前目标分支决定目标尺寸，新旧分支居中叠放；使用 `animateContentSize`、主题 `fastSpatialSpec` 和居中对齐平滑调整内容宽度。尺寸动画位于官方按钮内部，使表面、轮廓与投影跟随实际布局尺寸变化。

视频操作条的官方 `ToggleButton` 将 `CircleShape` 作为 `ToggleButtonShapes` 的全部三种形状，选中状态通过强调色前景和浅色底纹表达，普通操作使用同样固定轮廓的官方 `TextButton`。按钮整体承载图标与下方标签，保留官方水波纹、禁用与勾选语义，所有状态均无阴影；布局和菜单接入见[视频简介](content.md#视频简介)。

### 分段选择

[`Segmented`](../../app/src/main/java/moe/kirakira/ui/components/Segmented.kt) 通过 `items` 接收互异的选项值，`selected` 由调用方持有，传入 `null` 时隐藏指示块。`content` 为每项提供文字和可选图标，组件负责等宽布局和 Material 3 单选语义。点击已选项保持当前值。

空选时按下即在手指处显示缩小的滑块，在 150ms 内减速展开。键盘、无障碍和程序设置从目标分段中心展开。清空时，滑块向当前中心收起，阴影随之收小淡出。前景着色沿用缩放中的滑块轮廓。

轨道和强调色滑块共用连接列表外圆角。已有选择时，滑块采用底栏同款的按压收缩、弹性滑动与跟手弹簧。空选开始的手势直接读取手指横坐标，松手后吸附到目标分段。两种状态都在超过横向触摸阈值后接管手势，选中新分段后提交一次。取消手势或松手落在轨道外时回到原选择，原本空选时收起。按压反馈由滑块提供，悬停和焦点保留 Material 反馈。 [Cerasus Segmented](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/03047bcec32cc5007a47440c590d643a2e0db04b/components/Segmented/Segmented.vue)

轨道与普通文本框共用控件底色规则和 `Modifier.textFieldInnerShadow`。轨道直接放在灰色页面上时，通过 `parentContainerColor` 传入该页面底色。内阴影绘制在轨道背景与指示块之间。 [普通文本框实现](../../app/src/main/java/moe/kirakira/ui/components/FilledTextField.kt)

指示块的双层彩色投影与可见轮廓同步，沿用普通实心按钮的交互高度和时序。禁用时以无投影的中性色保留选中提示。 [按钮动态阴影](theme.md#按钮动态阴影)

### 顶部导航按钮定位

设置、资料、视频与图片查看器的导航按钮统一放在官方 Material 3 顶栏的 `navigationIcon` 中，使用默认定位与系统 Insets，不额外添加按钮位置边距。设置使用 `LargeFlexibleTopAppBar`，其余三个页面使用透明 `TopAppBar`；设置保留普通返回箭头，资料和视频保留带底色返回箭头，图片查看器保留深色圆形关闭按钮。

普通顶栏（含认证、资料、设置、主页面与头像裁剪）复用 [`appTopAppBarColors`](../../app/src/main/java/moe/kirakira/ui/components/TopAppBar.kt)：容器及滚动后容器透明，标题使用 `primary`，导航与操作图标使用 `onSurfaceVariant`，与普通列表图标保持一致。认证与安全操作页的返回使用平面官方 `IconButton`，保留返回回调和无障碍描述，Auth 保留原返回按钮标签并移除关闭按钮；资料页在 Tab 未吸顶时使用 `BackdropIconButton` 的黑底毛玻璃与默认白色前景，所有状态均无阴影；吸顶后恢复普通 `IconButton` 和栏面配色。首页品牌 Logo 和认证流程标题图标保留强调色，文字操作沿用原组件默认配色；播放器和图片查看器保留白色媒体控件。

视频顶栏作为页面全宽覆盖层，独立于已应用内容 Insets、最大宽度为 840dp 的视频区域，使用顶栏默认的顶部和水平系统 Insets。它不占用额外内容高度、不改变视频尺寸，宽屏时导航按钮仍按页面边缘定位。图片查看器继续由原有控件显隐、焦点与转场状态管理顶栏。

### 可复用 UI 参数示例

以下示例展示状态与事件分离，以及第一个可选参数 `modifier` 的位置；完整约束见 [Compose 与界面规范](../../CONTRIBUTING.md#compose-与界面规范)。

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

自定义 Modifier 的声明与链保留规则见[组件、状态与副作用](../../CONTRIBUTING.md#组件状态与副作用)。例如只在可用时添加语义的工厂：

```kotlin
internal fun Modifier.contentDescriptionWhenEnabled(enabled: Boolean, description: String): Modifier {
    if (!enabled) return this
    return semantics { contentDescription = description }
}
```

调用 `Modifier.padding(16.dp).contentDescriptionWhenEnabled(enabled, description)` 时，两条分支都保留已有 padding。播放器手势入口使用 `Modifier.playerGestures(...)`，在接收者上追加 `pointerInput`，手势状态和事件处理保持在原有实现中。

### 毛玻璃应用栏

[`FrostedScaffold`](../../app/src/main/java/moe/kirakira/ui/components/FrostedScaffold.kt) 为普通页面创建独立的 `HazeState`，用 `hazeSource` 采样内容、单独绘制顶部背景，再绘制透明的官方顶栏。文字和图标不参与模糊。背景形状、alpha 和原生阴影由 `ThemeShadows.barSurfaceLayer` 在背景子节点上统一管理，避免重复投影或降低前景透明度。胶囊底栏同样分离背景与前景，背景通过 `frostedBarBackground(shape, shadowElevation = BarShadowElevation)` 使用外层主页面采样状态，内部各 Tab 的顶部栏使用独立状态。

`frostedBarBackground` 的可选 `hazeState` 参数允许自定义布局显式复用相同背景样式，默认仍读取当前页面的采样状态；`shadowElevation` 默认 0dp，普通顶栏、视频页顶部共享背景和胶囊底栏默认显式传入固定的 `BarShadowElevation`，资料顶栏保留默认值。`FrostedScaffold.topBarShadowElevation` 供 `AccountFlowScaffold` 统一按正文滚动位置调整认证与账号安全流程的顶栏高度。

- 背景使用 `HazeInput.Sources` 采样，源消失时使用 `ClearWhenUnavailable`，不继续保留上一页面画面。普通栏面与资料页无阴影黑底按钮均不使用原生 backdrop；按钮样式见[黑底毛玻璃按钮](#黑底毛玻璃按钮)。已在 Android 17 模拟器（`CP41.260828.004.A7`，SkiaGL）上复现 `RenderNode.setBackdropRenderEffect()` 与半透明父层、elevation 同时使用时的灰框和内部矩形异常。对照父层 alpha 为 1、0.5、0.2：普通 elevation 及 Haze Sources 正常，Haze Backdrop 异常，强制 Offscreen 仍异常；移除 Compose/Haze、仅使用 Android View 和 RenderNode 也能复现，而移除 backdrop 恢复正常。证据定位到系统原生 backdrop 的合成路径，尚未定位内部实现的具体错误，也未验证所有设备。版本以 `gradle/libs.versions.toml` 为准，不引入玻璃折射模块。
- Android 12+ 使用主题 `surface` 作为缺失源像素的底色，叠加同色 80% 不透明度遮罩、20dp 模糊与零噪点，背景图层 alpha 为 1；原生投影所在层在模糊节点之前显式绘制同色不透明底色，避免源尚未捕获时效果跳过绘制造成空投影层，不能仅依赖 Haze 样式的 `backgroundColor`。Android 8.1–11 绘制不透明主题 `surface`，在同一背景图层上设置 alpha 为 0.9 和原生 elevation，不挂载采样节点；各版本共用[栏面阴影](theme.md#栏面阴影)的机制，不额外增加阴影版本判断。背景透明度不作用于整个栏或前景。
- 共享背景沿用 Haze 默认采样配置，保留实时更新、模糊半径与遮罩。模糊样式按主题 surface 色缓存，采样输入按页面状态缓存，现有采样层级保持不变。
- 滚动列表的顶部与底部安全区域放入 `contentPadding`，滚动 Column 则在 `verticalScroll` 后添加，初始避开栏面、滚动时内容可进入栏后。`ContentPullToRefresh.indicatorTopPadding` 仅移动覆盖式指示器，不移动滚动视口。历史页的搜索输入通过共享组件覆盖顶栏，不占用列表条目。
- 本人及作者资料页与邀请码页复用 `CollapsingTabLayout`，分页绘制在下层，头部内容与 Tab 共用上层的实际表面、背景及原生阴影。背景与原生投影由 `CollapsingHeaderBackground` 同层常驻，前景独立；吸顶只改变位置与展开态遮层的绘制，不替换背景节点，顶栏与 Tab 不独立投影。分页占满视口并以列表顶部内边距避让栏面，使内容可滚入毛玻璃后方。资料页封面及正文沿用独立采样状态；吸顶后共享表面只采样 Pager。非滚动表单保留安全布局。头像裁剪页同样接入本组件，由宿主提供顶部背景与固定阴影；裁剪引擎与底部操作栏保持原布局。视频画面、播放控件及系统栏不接入本组件。

- 视频页为 Tab 栏创建独立 `HazeState`，仅在 Android 12+ 将下方 `HorizontalPager` 接入 `hazeSource`，播放器不参与采样。播放器占位区域与 Tab 共用顶部背景，通过 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)` 沿整体下沿投影；播放器区域的背景黑底位于同一图层，实际播放器仍独立绘制。官方 `PrimaryTabRow` 容器透明，文字与指示器保持清晰。简介、评论、弹幕列表将实测栏高加入顶部 `contentPadding`，初始避开栏面，滚动时从栏后经过；空状态高度同步扣除栏高。刷新指示器通过 `indicatorTopPadding` 避让，评论分页工具栏固定在栏下，游客登录提示并入评论头部条目以保持分页索引；`FloatingComposerLayout.topPadding` 扣除输入面板可用高度，底部安全区域与输入区留白保持原逻辑。全屏与画中画继续由既有播放器布局和详情显隐处理。

### 黑底毛玻璃按钮

[BackdropButtons](../../app/src/main/java/moe/kirakira/ui/components/BackdropButtons.kt) 提供资料页使用的 `BackdropIconButton`，在官方 `FilledIconButton` 的透明表面内部绘制背景。模糊不延伸到最小触摸目标留白，不作用于图标或水波纹；官方组件继续拥有形状、尺寸、点击区域与语义。统一使用 20dp 模糊、零噪点与可用 60%／禁用 30% 的黑色遮罩，默认白色前景，所有状态均无投影。

各按钮显式接收页面拥有的 `HazeState`，直接使用 `HazeInput.Sources` 采样页面内容，并通过 `ClearWhenUnavailable` 清除失效源；缺失源像素透明，不填充不透明底色。按钮复用资料页已经注册的顶部背景与正文采样源。即使按钮自身没有阴影，原生 backdrop 仍可能与同一页面的 elevation 和返回淡出产生合成异常，因此按钮与普通栏面统一使用 Sources，应用启动不启用原生 backdrop 的全局实验开关，页面透明度继续由导航宿主管理。Android 12+ 挂载模糊，Android 8.1–11 仅绘制同一半透明黑底。

[ProfileScreen](../../app/src/main/java/moe/kirakira/feature/profile/ProfileScreen.kt) 复用该页已有的采样状态，仅在 Tab 未吸顶时将返回和更多按钮替换为共享黑底白色按钮；吸顶后沿用普通图标按钮和既有毛玻璃顶栏。播放器通过 `PlayerContentFrame` 保留 Media3 默认 `SurfaceView`，控制按钮使用半透明黑底，不接入本组件或 Haze 采样。

### 顶栏淡色图标底纹

[`ShadingIcon`](../../app/src/main/java/moe/kirakira/ui/components/ShadingIcon.kt) 复用首页 Logo 底纹样式：128dp 图标、主题 `primaryFixed` 配色和 20% 透明度，在装饰区域靠末端垂直居中，按布局方向定位并避让顶栏横向系统 Insets。图标保持固定尺寸，不受折叠顶栏高度约束，超出区域的部分由组件裁切。

在承载顶栏的 `Box` 内先绘制底纹，再绘制透明顶栏；通过 `Modifier.matchParentSize()` 让底纹跟随顶栏实际尺寸而不参与测量。底纹位于标题槽之外，可延伸到状态栏后方；不要给装饰额外添加顶部系统内边距。毛玻璃背景和阴影继续由 `FrostedScaffold` 管理。

`icon` 接收现有 Drawable 资源，`endPadding` 默认为 16dp，`alignment` 默认为 `Alignment.CenterEnd`，`offset` 默认为 `DpOffset.Zero`。对齐同时用于图标在宿主内的定位及不受约束的尺寸布局，`offset.x` 的正值沿布局方向向末端移动，`offset.y` 的正值向下移动。首页传入 `logo_kirakira` 并保留 72dp 末端留白避让头像，使用默认对齐和偏移。

设置与历史页指定 `Alignment.BottomEnd`、`endPadding = 0.dp` 和 `DpOffset(32.dp, 32.dp)`，让图标向末端及底部各溢出 32dp，由组件边界裁切。底纹随顶栏实际高度始终贴住底边，下拉展开后也保持底部定位。设置首页使用 Cerasus 的 `ic_icon_settings` 并开启旋转。历史页使用同源的 `ic_icon_history`，保持默认静止状态。历史底纹与普通大标题栏一起放在搜索栏上方；全屏搜索覆盖页面，收起后恢复普通顶栏。

使用 `SettingsScaffold` 的设置子页面沿用这一右下角定位，但传入页面自身的现有图标且保持静止：外观使用 `ic_icon_palette`，播放使用 `ic_icon_play`，弹幕使用 `ic_custom_danmaku`，关于使用 `ic_icon_info`，资料使用 `ic_icon_badge`，隐私使用 `ic_icon_shield`，安全使用 `ic_icon_lock`。管理页的共享 `ManagementFrame` 接收同一图标参数，屏蔽总览使用 `ic_icon_block`，屏蔽分类详情使用 `RuleCategory.iconRes()`，邀请码使用 `ic_icon_gift`；账户切换和许可证独立包裹顶栏，分别使用 `ic_symbol_switch_account` 与 `ic_icon_file`。头像裁剪页是独立的图片编辑顶栏，不绘制该底纹。

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
    CollapsibleTopAppBar(
        title = stringResource(R.string.me_settings),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
    )
}
```

`rotating` 默认关闭，此时不创建无限动画；开启时顺时针线性旋转，系统动画缩放为 1 时每 30 秒转一圈。角度在 `graphicsLayer` 中读取，动画遵循系统动画缩放设置。底纹不接收点击，`contentDescription` 为 `null`，不添加无障碍操作或偏好设置。

### 可选的可折叠大标题栏

共享组件为 [`CollapsibleTopAppBar`](../../app/src/main/java/moe/kirakira/ui/components/CollapsibleTopAppBar.kt)，适用场景、状态与 Insets 约束见[贡献指南](../../CONTRIBUTING.md#可选的可折叠大标题栏)。

展开高度统一设为 136dp（不含状态栏），在默认 120dp 的基础上增加 16dp，缓解标题区域空间不足时官方布局对底部基线间距的压缩，同时保持紧凑感。保留官方标题排版、基线定位与字体缩放适配，不额外给标题添加内边距；增加的栏高不等于标题下方留白直接增加 16dp。顶栏与滚动状态初始化共用同一个展开高度，折叠距离由该高度减去官方折叠高度计算，避免首次进入时残留展开区域。

`rememberCollapsibleTopAppBarScrollBehavior()` 默认首次折叠，上滑收起，内容到顶后下拉展开；需要首次展开时传入 `initialCollapsed = false`，此参数不会覆盖已恢复的状态。同一份 `scrollBehavior` 传给顶栏，并将其 `nestedScrollConnection` 接到父容器，才能联动列表手势。

顶栏参数包括资源解析后的 `title`、`onBack` 和可选的 `actions` 插槽，切换账户页的“编辑／完成”使用后者。组件统一返回图标和配色，页面管理内容与 Snackbar。下例通过 `fillMaxSize()` 放在 `verticalScroll()` 前，让短内容的空白区域也能接收下拉手势；顶部与底部内边距放在滚动内容中。

接入示意（调用方提供 `onBack`，菜单内容按页面填写）：

```kotlin
val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
val layoutDirection = LocalLayoutDirection.current
FrostedScaffold(
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

### 可复用的顶栏搜索

共享 [`dismissKeyboardOnBlankTap`](../../app/src/main/java/moe/kirakira/ui/components/KeyboardDismissal.kt) 在指针 Final 阶段观察未被控件消费的短按，排除输入框与按钮已处理的点击，以及滚动、拖动、长按和多指手势；只清除焦点并隐藏键盘，不消费事件或修改文本。视频搜索在对应主界面页面接入，标签选择器与资料编辑在各自页面根容器接入，历史搜索在活动搜索层接入。设置搜索在 Dialog 内的结果列接入，确保读取该窗口的焦点管理器与键盘控制器；点击空白仅收键盘，保留搜索展开状态和结果。此行为由各页面显式启用，普通输入组件与认证表单不自动继承。

[`PageSearchBarAnchor`](../../app/src/main/java/moe/kirakira/ui/components/PageSearchBar.kt) 供设置与历史页放在可折叠大标题下，接收页面拥有的 `TextFieldState`、`SearchBarState`、提示文案、可用状态及最大内容宽度。使用官方 `SearchBar` 和共享的官方 `SearchBarDefaults.InputField`，不访问 ViewModel 或 Repository；数据过滤与结果列表由页面管理。

搜索栏左右保留 16dp，顶部无额外内边距，底部保留 12dp。默认宽度上限为 640dp，历史页按既有列表宽度传入 840dp。大标题负责顶端系统安全区，搜索栏仅处理横向安全区。收起的搜索栏按直接背景使用共用控件底色。全屏搜索使用 `surface`，输入框与内容分隔线透明。主搜索的关键词框、已选标签框及全屏标签选择器共用 [`SearchFieldContainer`](../../app/src/main/java/moe/kirakira/ui/components/search/QuerySearchBar.kt)，与历史和设置的搜索框共用控件底色规则和普通文本框使用的 `MaterialTheme.shapes.large` 圆角。

主搜索的 `QuerySearchBar` 与标签选择器共用 `SearchQueryField`。输入框采用无底线的 Material 3 `TextField`，通过 `SearchBarDefaults.inputFieldColors()` 沿用搜索栏的文本、占位和图标配色。左侧放大镜为普通 `Icon`。关键词框和标签选择器查询框复用 `SearchFieldTrailingAction`，清空按钮淡入淡出，编辑区宽度保持稳定。关键词通过键盘搜索键提交，空白输入与重复请求由 ViewModel 处理。

设置与历史的收起搜索栏和 `SearchFieldContainer` 复用普通文本框的 `Modifier.textFieldInnerShadow`。阴影沿 `large` 圆角绘制在输入区域的背景与前景内容之间。展开层的起始底色和内阴影作为输入框下方的独立绘制层，随展开进度淡出。搜索顶栏使用与普通应用栏相同的原生 elevation，展开时随进度增强，收起时反向过渡。[普通文本框实现](../../app/src/main/java/moe/kirakira/ui/components/FilledTextField.kt)

设置页在全屏 Dialog 内挂载 `PageSearchOverlay`，历史页在 Activity 来源 Scene 内挂载同一组件。视频入栈时保持历史搜索层挂载，`isActive` 管理焦点与局部返回。`PageSearchOverlayLayout` 按收起搜索栏的实际边界与 `SearchBarState` 进度计算位置、尺寸、圆角及结果透明度。设置搜索的预测性返回手势预览部分收起，松手后沿原动画收起或恢复。来源及局部返回适配见 [AndroidX 搜索布局说明](../../third_party/android-search/README.md)。

设置搜索与标签选择器在各自的 Dialog 窗口调用 `WindowCompat.enableEdgeToEdge`，系统栏透明且图标跟随应用主题。历史搜索沿用 Activity 的系统栏。设置与历史的搜索容器只处理顶部和横向安全区。设置与历史使用 `TopAppBarDefaults.LargeAppBarCollapsedHeight` 作为展开后的最小栏高，在状态栏安全区下方垂直居中输入行。阴影由实测顶栏绘制，结果区从栏底开始。结果视口通过 `imePadding` 避让键盘，剩余底部安全距离由列表作为内容留白消费，设置空态和历史 Snackbar 也使用这段安全距离。标签选择器的候选列表使用 `FrostedScaffold` 提供的底部留白。

收起的输入框始终显示搜索图标。展开层以同一进度交叉淡入淡出放大镜和返回箭头，并插值文字起始间距与输入行的横向安全区。返回按钮在箭头可见后提供交互语义。清空只清空文字，提交只收起键盘。设置与历史在展开动画结束后请求输入焦点，历史页面失活时仅释放自身输入焦点，返回后保留内容和位置；局部返回先收起键盘，再收起搜索，最后由页面处理退出。禁用时收起搜索并禁用输入与清空。历史与设置都保留普通大标题嵌套滚动连接，旧的顶栏替换与先折叠标题再打开搜索的代码已移除。

### 全屏标签选择器

`ui/components/search/TagSearchDialog` 供视频搜索和屏蔽标签设置共用。选择器负责全屏窗口、折叠顶栏、输入框、已选 Chip、候选列表和键盘交互。调用方传入查询状态和选中标签，按场景处理勾选与确认。视频搜索使用 `TagSearchAnchor` 与顶栏 `TagSearchField` 做共享过渡，设置从添加按钮打开时使用窗口内容的淡入淡出。

两个 ViewModel 通过 `TagSearchLookup` 在各自的账号任务范围内读取候选。输入防抖 500 毫秒，键盘搜索与重试立即发起查询。查询条件改变时清空旧结果，同条件重试保留已确认结果。更换账号或关闭选择器时取消查询。

### 搜索空结果图形

主搜索使用 [`SearchEmptyLogos.kt`](../../app/src/main/java/moe/kirakira/ui/components/SearchEmptyLogos.kt) 中的两幅 Compose 矢量插画：`SearchHistoryEmptyLogo` 复用 `HistorySearchLogo` 的绘制、主题配色和回拨动画。表盘充当镜面，圆头斜柄从右下接入表盘背面，柄面与时钟外圈同色，使用 2dp 原生投影。`SearchVideosEmptyLogo` 按用户提供的红色胶片参考图绘制：4:3 圆角纸片，下半部使用当前主题的 `primary` 原色，上半部向黑色混合 8%，两侧各四个圆角镂空孔，不绘制播放三角，前景保留深灰色放大镜。孔洞通过路径差集贯穿纸片，随页面背景显示。视频镜片使用单一中性浅灰色 `#EEEEEE`，不绘制高光、反光斜面或透明玻璃效果；镜框与圆角斜柄采用 Google 经典 Material Icons `search` 的原始路径比例，在镜片内孔补入纯色纸层后整体等比放大为连续纸片，以深灰色 `#616161` 绘制正面，整块轮廓共用 2dp 原生投影。两者沿用收藏、历史和设置图形的经典 Material 剪纸风格，使用 128dp 画布及分层原生投影，胶片卡的原生投影为 4dp。视频空态的胶片正面对齐画布中心，前景放大镜以镜片中心为支点缩至 85%，叠在胶片右下方。仅挂载在主搜索空历史与已提交搜索的空结果分支，加载和失败保留原有提示。空历史的斜柄静置，表盘内的指针和外圈箭头播放回拨动画。视频胶片静置，前景放大镜沿左上方轻扫后回到原位，最大水平／垂直位移为 24dp／8dp。扫动沿半正弦路径移动，镜框、镜片、手柄和投影在同一图层同步移动。

设置搜索的 `SettingsSearchLogo` 移植 Cerasus 的齿轮、长阴影与反向转动内环。以当前主题的 `primary` 作为 `accent-50`，按 Cerasus 的浅深模式混色规则生成外齿轮的 `neutral-50` 及内环的 `accent-60`、`accent-10`、`accent-40`。前两段内环分别为 80% 和 70% 不透明度，第三段与中心圆共用实色 `accent-40`，长阴影叠加 30% 不透明度的 `scrim`。历史搜索的 [`HistorySearchLogo`](../../app/src/main/java/moe/kirakira/ui/components/HistorySearchLogo.kt) 参考用户提供的 Android L Clock 与青色回溯时钟插画，用 Compose 圆形、线段与计算路径绘制钟盘和宽箭头。整体为 128dp，所有图层共用 512 × 512 画布和 `(256, 256)` 中心；表盘半径为 202，箭带内外半径为 192／244，带宽 13dp；箭头头部的内外基点分别延伸到半径 144／292，其中心半径 218 与环带中心一致，避免头部偏在环带内侧。头部与弧带使用同一条连续闭合轮廓。

外圈与箭头正面使用当前主题的 `primary` 原色。表盘渐变的两端向黑色混合 22% 与 26%。时针与分针为白色 `#FFFFFF`，秒针与中心轴使用主题原色和白色的中间色。箭头使用模糊半径 4dp、向下偏移 2dp 的柔和投影，表盘使用 2dp 原生投影。三根指针保留直线、线宽与中心轴设计，按可见表盘半径 192 个画布单位（48dp）调整长度：时针约 27.8dp、分针约 40.3dp、秒针 43dp，最长指针与箭带内缘仍留 5dp。指针和长阴影统一裁剪到表盘内部，箭带绘制在其上方，避免针尖或投影压到外圈；长阴影继续按固定右下光向投射，重叠区域一次填充。参考记录见 [Android Classic Icons](../../third_party/android-classic-icons/README.md)。

历史时钟的外沿箭头和指针共用同一个动画进度。分针回拨四分之一圈、时针按十二分之一角度同步移动、秒针一圈。弧带尾端固定在左侧，前沿从 180° 逆时针扫过 312°，头部以固定大小跟随前沿绕钟盘转动。头部基线与扫切前沿共用角度，尖端保持相对基线 −3° 的固定朝向偏差，避免脱节或形变。弧带与头部全程保持不透明。箭头正面和 Compose 投影共用当帧的路径，投影按路径边界绘制，覆盖箭头扫过画布边缘时伸出的部分。

收藏星形、历史时钟、视频搜索放大镜和设置搜索齿轮共用 [1 秒入场动画](../../app/src/main/java/moe/kirakira/ui/components/IllustrationEntrance.kt)，曲线为 `cubic-bezier(0.1, 0.9, 0.2, 1)`。页面或空态重新显示时从头播放。导航以实际栈顶为准，预测性返回确认后目标页才播放。保留在组合中的页签和搜索浮层按当前显示状态触发，持续无匹配时输入变化不重播。进度只在当前组合内持有，在图层和绘制阶段读取。关闭系统动画和静态预览时直接显示终态。文件内的 `HistorySearchLogoPreview` 和 `HistorySearchLogoDarkPreview` 直接传入固定终态，`HistorySearchMotionPreview` 另将 0／50／100／200／400／700／1000ms 时刻映射到同一曲线，可用 Android CLI 导出 PNG 检查路径揭示、衔接与裁切；独立渲染不需要运行 IDE、设备或模拟器，命令见[Compose 布局预览](../development.md#compose-布局预览)。

图形不增加语义，标题沿用原有中英文空状态文案。`ContentStatus.emptyIcon` 是可选空态图形插槽，仅传给空状态分支；加载、错误与重试不使用它。历史页的普通空记录、搜索无匹配和游客登录提示均提供该时钟 Logo，加载与错误提示保留原有表现。

## 图标划线过渡

共享组件 `ui/components/AnimatedSlashIcon.kt` 为密码可见性按钮和播放器弹幕 Switch 提供 Material 3 Expressive 划线过渡，复用主题的 `fastSpatialSpec` 弹簧。原始 VectorDrawable 路径只在创建时解析并归一化到 24 × 24 画布；关闭图标拆分为主体与原有圆头斜线，主体的共同区域保持不透明，仅差异区域随限定在 0～1 的进度渐变，斜线沿左上至右下通过裁剪逐段显现，反向切换沿原路径收回。斜线保留弹簧超过 1 的进度，以起点为支点沿 45° 方向伸长后回弹，垂直方向不缩放，保持视觉线宽；静止端点直接绘制原始图标路径，保持现有几何与断开细节。

`rememberSlashIconProgress` 初始值取当前状态，后续通过 Compose `Animatable` 平滑切换；快速点击从当前进度反向，系统动画时长缩放及关闭动画由 Compose 处理。播放器在控制栏显隐分支外持有该状态，重新显示控制栏不会重播；弹幕设置异步读入时通过 `ready` 建立初始进度，不播放加载过渡。密码按钮的划线表示“隐藏密码”操作，密码遮罩和弹幕业务状态立即切换；图标仅提供视觉过渡，不改变按钮语义、禁用表现或开关尺寸。设置页普通 Switch 不新增图标。

## 账号流程布局

安全概览正文复用 `SettingsColumn` 的内容宽度、滚动和边距；下拉刷新包裹内容，正文负责 Insets 消费。首次加载、未登录和错误分支保持原有入口。

`AuthScreen` 与账号安全操作页共用 [AccountFlowScaffold](../../app/src/main/java/moe/kirakira/ui/components/AccountFlowScaffold.kt)：`FrostedScaffold` 提供 `surface` 背景和独立毛玻璃采样，`CenterAlignedTopAppBar` 的标题槽居中显示主题色流程图标；登录 Logo 为 40dp，其余图标为 32dp，不使用底板。非登录步骤的返回使用官方平面 `IconButton` 和默认 AppBar 槽位定位，所有步骤均无关闭按钮。`AuthScreen.onClose` 仅保留默认空实现兼容现有离线 UI 检查调用，生产导航不再传递，认证完成仍通过 `AuthPage` 的回调退出。

`AccountFlowScaffold` 在组合内持有 `rememberScrollState()`，通过正文插槽传出内边距和滚动状态；`AuthScreen` 与账号安全操作页将这份状态传给 `AccountFlowColumn` 必填的 `scrollState` 参数。宿主以 `derivedStateOf` 判断是否滚离顶部，统一让顶栏在顶部为 0dp、滚离顶部后为 `BarShadowElevation`、回到顶部去掉阴影。页面不再单独配置顶栏阴影，底部主操作栏继续使用固定阴影。

`AccountFlowColumn` 提供最大 480dp 内容宽度、24dp 页面留白和完整滚动视口；`AccountFlowHeader` 使用半粗 `headlineLarge` 与可选说明／进度插槽。标题下方间隔 12dp，`AccountFlowProgress` 横排本地化的紧凑步骤文字（如“2 / 2”）和 4dp 进度条，整组提供完整步骤描述与进度语义。页头到表单及独立分组之间为 32dp，字段间距为 24dp。认证页按步骤启用既有本地化说明资源；安全流程按步骤、当前验证方式及同步状态选择说明，常规页以一到两句引导表达待完成操作，状态核对和本机同步分支只显示现有状态说明。

主操作通过 `AccountFlowScaffold.bottomBar` 接入 `AccountFlowActionBar`，固定在底部，横向与正文对齐，顶部留白为 16dp，底部留白覆盖按钮按压时的投影；`AccountFlowSubmitButton` 占满内容宽度，使用 `ButtonDefaults.MediumContainerHeight` 配套形状、内边距和文字 API、共享彩色阴影及加载语义，加载时仍测量标签以适应字体缩放。发送验证码、复制及打开验证器等辅助操作保留在对应内容旁。字段错误说明保留在输入框下，全局错误复用 `AccountFlowError` 的错误色文字与无障碍播报，同步重试和代码保存确认保留短标签与完整无障碍描述。

宿主根节点统一应用 `imePadding`，表单和底栏不重复处理 IME；`AccountFlowActionBar` 复用 [FormActionBar](../../app/src/main/java/moe/kirakira/ui/components/FormActionBar.kt) 的 `surface` 背景、固定 4dp 原生阴影与系统安全区域。Scaffold 将底栏实测高度交给正文的滚动 padding，确保末项可滚动至按钮上方，Snackbar 自动避让底栏和键盘。

`AccountFlowSection` 只负责无底板的标题、上下文和表单布局，接受可空 `title`、可选 `supportingContent`、`actionContent` 及正文插槽，不持有业务状态。有标题时上下文在标题下方，相隔 4dp；标题为空时上下文直接进入页头行，邮箱保留普通文字语义，不标记为标题。页头使用 `FlowRow`，操作插槽由末端对齐的加权 `Box` 承载，并通过 `width(IntrinsicSize.Max)` 按完整操作内容的固有宽度决定换行，避免把长按钮挤进剩余宽度；发送按钮在空间不足时换行，换行后仍靠内容末端对齐，长邮箱允许完整换行。分组页头与字段相隔 16dp，没有标题、上下文或操作时不组合页头及关联间距。认证与当前邮箱核验直接显示邮箱，不重复“账号”或邮箱标签；新邮箱与身份验证保留任务标题。邮箱确认页以 32dp 间距分开身份验证与新邮箱验证，输入框继续使用各自标签。登录辅助入口也使用可换行布局；认证表单通过 `onResend` 回调把发送操作放在验证码旁。发送操作前移后，密码到验证码、当前邮箱验证码到新邮箱验证码的 IME“下一项”通过显式焦点目标连接，普通按钮仍可通过硬件键盘访问。页面只在存在通知或错误时插入反馈区域及 12dp 间距；操作引导保留在页头，不重复添加账号或邮箱分组标签。

账号流程靠边文字按钮统一使用 [FormTextButton](../../app/src/main/java/moe/kirakira/ui/components/FormTextButton.kt)，按 `FormButtonEdge.START/END` 选择对齐边，组件沿用 [ExpandableText](../../app/src/main/java/moe/kirakira/ui/components/ExpandableText.kt) 的对齐方式：显式保留 `ButtonDefaults.TextButtonContentPadding`，按当前布局方向读取 start／end 内边距；忘记密码和联系支持向 start 偏移对应内边距，注册和发送验证码向 end 偏移对应内边距，使可见文字与标题及输入框边缘对齐。按钮沿用相同的 48dp 最小宽度，避免短标签因额外居中留白而错位，保留官方最小触摸目标、涟漪及禁用语义。登录注册入口与表单之间使用 `AccountFlowDefaults.FieldSpacing` 的 24dp 留白，并使用按固有宽度换行的末端对齐容器；偏移只作用于按钮，正文宽度与页面留白保持原值。

普通表单输入框统一使用 [FilledTextField](../../app/src/main/java/moe/kirakira/ui/components/FilledTextField.kt)，内部复用 `BasicTextField` 与官方 `TextFieldDefaults.DecorationBox`、`Container`，通过 `FilledTextFieldDefaults.colors(parentContainerColor)` 为聚焦、未聚焦、禁用及错误状态设置同一底色，未聚焦底线使用主题 `outlineVariant`，文字、焦点与错误指示线保留官方默认配色。聚焦与未聚焦底线均为 2dp，参考 Cerasus `TextBox` 的 2px 底线，底线由官方指示线 API 绘制，位置不受 supporting text 高度影响；普通输入统一默认使用主题 large 圆角，调用处不重复指定。认证、资料、屏蔽规则、颜色编辑器和评论跳页均继承这些默认值；六格验证码复用相同的背景配色与内阴影，不绘制底线。共享 `FilledTextFieldContainer` 先绘制官方背景容器，再由同级、同尺寸的独立空 `Box` 应用 `innerShadow`，最后由外层官方 `indicatorLine` 绘制底线；当前 Compose 的 `innerShadow` 先于其后续内容绘制，因此不能直接挂在不透明背景容器外层，否则阴影会被背景覆盖；文字与光标位于整个容器之上，supporting text 不参与内阴影。内阴影使用主题 `scrim`，浅色 6%／深色 12% 不透明度，模糊半径和向下偏移均为 4dp，扩张为 0dp，沿用容器形状且不随聚焦变化；通过当前主题 `surface` 与 `onSurface` 的明度关系识别明暗，不依赖系统模式。搜索栏应用同一底色规则，并沿用搜索组件的前景配色。评论和弹幕发送框使用专用容器配色、指示线与阴影。传入 `errorMessage` 时，组件默认提供错误图标、supporting text 的短过渡和具体错误语义；只有 `isError` 时回退到项目自己的中英文 `text_field_invalid_input` 字符串，不引用 Compose 私有资源。自定义辅助内容（如简介字数）仍由插槽提供，整体高度变化由组件统一动画。

[PasswordTextField](../../app/src/main/java/moe/kirakira/ui/components/PasswordTextField.kt) 在 `FilledTextField` 上统一密码遮蔽、显隐按钮和错误图标排列。登录、注册密码及确认密码、重置密码和账号安全表单均复用该组件；调用方保留现有自动填充语义、IME 动作与焦点目标，并持有仅存内存的显隐状态。提交禁用或页面失活时遮蔽密码，注册确认密码和重置密码不再缺少显隐入口。

字段错误态以 24dp 显示共享 `ErrorIcon`。普通字段通过 `trailingIcon` 显示；带显隐按钮的密码框在同一行显示错误图标与原按钮，间隔 8dp，保留按钮的点击与键盘语义。图标的 `contentDescription` 为 `null`，无独立点击、背景或阴影，错误说明仍由输入框 supporting text 提供，沿用官方间距并避免重复播报。登录／注册密码及重置密码的新密码字段均绑定现有 `fieldErrors[AuthField.PASSWORD]`；安全表单继续使用 `fieldErrors[SecurityField]`。正常状态不生成错误图标或错误说明占位，不改变校验、焦点与显隐状态的所有权。现有预览提供单字段、多字段及密码显示时的错误态，并配置中英文、浅深主题、320dp 窄屏和两倍字体；这些配置不代表已完成设备或截图验证。

[AccountFlowFeedback](../../app/src/main/java/moe/kirakira/ui/components/AccountFlowFeedback.kt) 在组合内保留非敏感提示文字，供退出动画完成前绘制；`Transition<Boolean>` 仅管理显示状态，业务错误仍由页面传入。页面级提示使用淡入淡出与纵向展开／收缩，关联留白放在内容插槽中一同过渡。`textFieldSupportingError` 在字段错误退出完成后返回空插槽，输入框通过 `animateContentSize` 平滑调整整体高度，恢复官方正常高度；退出中的提示清除语义，保留当前字段校验及焦点逻辑。所有过渡使用主题 `motionScheme`，遵循系统动画缩放。`accountFlowCodeTextStyle` 为认证与安全验证码以及代码展示提供统一等宽样式，参数遵循[账号表单规范](../../CONTRIBUTING.md#账号表单与保存)。

[OtpInputField](../../app/src/main/java/moe/kirakira/ui/components/OtpInputField.kt) 使用一个 `BasicTextField` 处理六格输入，代码由流程持有，组合内用普通 `remember` 保留选择位置与焦点状态；完整编辑器保留布局范围和语义，视觉格使用共享 `FilledTextFieldContainer` 封装官方 filled 容器，底色由 `FilledTextFieldDefaults` 提供，并带有共享中性内阴影，聚焦与未聚焦指示线高度均设为 0dp，不绘制底线，只在当前格连接编辑器的聚焦交互源。编辑器聚焦且可用时，当前格显示 2dp 圆角竖光标，颜色使用主题 `primary`，错误时使用 `error`；空格居中，已有字符时放在对应插入位置旁，光标高度随字符字号缩放。光标每 500ms 切换显隐，输入、删除或改变选择位置时重新亮起，失焦或禁用时移除；闪烁透明度只在绘制阶段读取。每格按当前字符创建独立的 `AnimatedVisibility` 入场节点，输入或替换字符时从下往上滑入并淡入；旧字符在删除或替换时立即移除，不执行退场动画，避免与空格中央的光标重叠。删除会移除入场节点，快速重新输入相同字符也重新播放入场动画。空文字占位保留空格的文字高度；位移与透明度分别使用主题快速空间与效果动效，不对容器尺寸添加动画。六格字符在共享等宽样式上采用主题 `headlineSmall` 的字号与行高，保留 Medium 字重和 1sp 字距；格子仅限制最小高度，字体放大时随文字高度增长。点击已填格选中对应字符，空格定位到已有代码末端；粘贴、退格、全选替换与 IME 操作由同一编辑器处理。六格内容不重复提供语义，整个输入组作为一个编辑目标；错误图标和提示位于组级区域。数字输入与字母数字备用码分别使用数字及 ASCII 键盘，过滤规则对应后端字符集，六位输入不自动提交。认证页额外提供完整恢复码输入，粘贴完整恢复码时切换显示，未将敏感材料放入可保存状态。

`AuthFilledCodePreview` 与 `SecurityFilledCodePreview` 提供独立的“验证码已填写”预览，涵盖邮箱验证码、字母数字备用码和认证页的长恢复码；安全页另展示邮箱与 TOTP 两种修改邮箱核验、邮箱与 TOTP 两种修改密码核验，以及绑定验证器的确认输入。填写值仅为预览专用虚构材料，密码保持为空；预览配置包含中英文、浅深主题和窄屏大字体，不代表已完成设备视觉检查。

TOTP 页面把二维码与打开验证器操作放在同一区域，密钥与复制操作相邻，验证输入独立分组。密钥和恢复码沿用原有专用容器，复制按钮在容器外绘制以保留其阴影；备用码按当前等宽字形的实际测量宽度，在两列和一列之间选择，恢复码按四字符分组换行。窄屏和大字体仅调整内容换行与列数，配色、阴影、底栏和流程状态不变。

## 危险操作

[DestructiveActions](../../app/src/main/java/moe/kirakira/ui/components/DestructiveActions.kt) 提供共用危险配色。实心按钮通过 `ShadowButton(destructive = true)` 使用主题 `error/onError`，覆盖登出、移除／清除账号、舍弃内容、隐私重置及关闭／解绑验证；该语义优先于调用方的 `colors`。安全流程由 `AccountFlowSubmitButton.destructive` 传递，加载时沿用原有禁用与进度反馈。`DestructiveTextButton` 保持官方平面文字按钮，菜单项继续使用 `SegmentedMenuItem.destructive`；取消、普通重试及禁用颜色保留 Material 默认值。

[DestructiveDecoration](../../app/src/main/java/moe/kirakira/ui/components/DestructiveDecoration.kt) 在可用危险实心按钮的内容后方绘制五个 Cerasus `close` 图标。横向起点为容器宽度的 0%、20%、50%、60%、80%，基础尺寸为 36、24、48、16、32dp，默认缩放 1.5 倍；每个图形以 4 秒线性周期从自身高度的 +150% 移至 −150%，中点透明度为 0.4、两端为 0，依次延迟 1.6 秒启动。悬停使用 1 秒回弹过渡，缩放至 2 倍并旋转 90°；触屏继续沿用官方按压反馈。装饰使用与按钮实际可见表面同尺寸的独立绘制节点，文字单独测量且保留原布局；五个图形共用固定尺寸的矢量栅格缓存，仅通过画布缩放产生大小差异，避免同帧反复改写缓存造成图形截断。由官方表面裁剪到与投影共用的动画轮廓，不占布局空间、不添加语义，也不改变内容内边距与点击区域。逐帧状态仅在绘制阶段读取；禁用、加载或系统动画缩放为零时不组合循环动画。来源与移植差异见 [Cerasus 图标与动画](../../third_party/cerasus-icons/README.md#危险按钮装饰)。

## 用户名展示

[UsernameText](../../app/src/main/java/moe/kirakira/ui/components/UsernameText.kt) 接收原始 username，统一添加 `@`、使用等宽字体和主题次要文字色；空白值不产生文字或布局。调用方保留字号、行数、溢出处理、对齐与修饰符；列表的可选副标题插槽仍在调用处按空白值省略，避免留下插槽间距。账号列表的 `AccountItem.handle` 保留既有展示值契约，调用组件前移除已有前缀，状态提示继续使用普通文字。

## 日期与矢量资源

[DateFormatting](../../app/src/main/java/moe/kirakira/ui/components/DateFormatting.kt) 的 `dateOnlyText` 与 `dateTimeText` 使用应用配置的 Locale。规则创建日期、二步验证绑定日期和历史日期标题复用日期格式化；生日使用 LocalDate 重载，不经过时区转换；内容发布时间保留短日期与时间格式。应用单独选择语言后，这些日期随应用语言更新。

[VectorPaths](../../app/src/main/java/moe/kirakira/ui/components/VectorPaths.kt) 按资源顺序读取 VectorDrawable 的全部 pathData，并为当前使用绝对 M 命令的动画图标提供轮廓拆分。音量、倍速、双击箭头和划线图标共用资源读取；各图标仍负责选择路径、坐标变换和动画几何，不把所有矢量图标当作相同结构处理。

## 数字动画与字数计数

`AnimatedNumberText` 封装 NumberFlow 的逐位滚动、主题 `fastSpatialSpec` 和 `animateContentSize`，数位增减时平滑过渡数字区域宽度。接收数值、格式函数、根 Modifier、文字样式与颜色，默认继承所在组件的排版和前景色，并启用 OpenType `tnum` 等宽数字，减少相同位数更新时的宽度变化；计数标签的测量与窄屏文字回退使用相同设置。播放时间、验证码重发倒计时、播放器手势提示及滑块读数也启用 `tnum`，倍速仪表读数的测量同步使用该设置；实际效果取决于字体支持。只负责数字，固定单位或上限放在外部。无障碍语义读取完整目标数字，不逐字符朗读。来源、许可与固定提交见 [NumberFlow](../../third_party/number-flow/README.md)。

`CharacterCount` 组合动态当前字数与静态的斜杠、上限，使用中英文共享格式资源，并将整项合并为完整计数语义。资料简介与关键词／正则屏蔽规则输入复用该组件，保留各自原有的长度计算、上限、错误优先级及校验逻辑。播放器的 `PlaybackSpeedText` 同样复用 `AnimatedNumberText`，乘号保持在数字动画区域外。动画遵循系统时长设置；初次显示直接采用当前值。

`AnimatedNumberText` 提供 Double、Long、Int 重载；互动与统计整数走 NumberFlow 的 Long 接口，不转浮点数，负号在动画数字区域外静态显示，不对 Long.MIN_VALUE 求绝对值。`AnimatedCountLabel` 从中英文资源的 `%1$s` 占位符拆分静态前后文，`AnimatedPageCount` 对 `%1$s`、`%2$s` 两个数字分别动画；复数资源由实际数量选择。两者将整个标签合并为完整目标文字语义；已有按钮角色、描述及标题语义由外层保留。受限宽度下按完整文本实测宽度退回普通文字布局，标签保留换行，页码工具栏保留单行省略，避免数字或单位越过工具栏边界。

接入范围为视频赞踩、评论净评分、资料关注／粉丝数、邀请码未使用／总数／已使用数、屏蔽规则总览及管理页总数、评论／弹幕总数、评论页码与跳页上限、搜索及标签已加载结果数。`SettingsSectionHeader` 接收 String 标题，复用共享 `SectionHeader` 的字号、主色和 heading 语义，并添加设置页标题内边距。收藏没有真实计数，保留占位；倒计时、设置读数、播放时间、日期、ID、凭据及固定刻度不接入。

评论跳页上限作为 `FilledTextField` 的辅助文字，输入框会先查询其固有高度以预留空间。计数标签将固有尺寸查询交给完整的普通文本，实际布局按可用宽度测量并放置动画行或换行文本。

数字仅随现有状态更新；初次显示、未知值转为已知值时直接显示当前值，未知占位沿用原逻辑。视频通过 `LocalNumberAnimationIdentity` 按会话／内容标识重置数字内部组合，邀请码按会话修订 key 隔离，资料按 UID 隔离，账号规则页面按会话修订隔离，搜索按 generation 隔离，标签沿用路由与会话身份。刷新同一对象保留数字组件，不增加乐观计数或额外请求。

### 头像占位配色

`PlaceholderAvatar` 参考 Cerasus 无头像状态的灰色背景与中性图标，复用主题现有的 `surfaceContainerHigh/onSurfaceVariant`，随浅深主题切换，不受强调色影响。`AccountAvatar` 共用该占位，覆盖未登录入口、切换账号页的游客及无头像账号；切换账号页的“添加账号”圆形图标使用同一组颜色，编辑状态保留原有禁用配色。

## 开发者组件库

关于页图形 Logo 长按进入 `ComponentTestRoute`。Logo 的 `combinedClickable` 使用独立交互源并关闭 indication，按住时通过主题 `fastSpatialSpec` 缩小至 90%。缩放在图形层读取，触摸区域保持固定。

`feature/componenttest/ComponentCatalog.kt` 登记组件稳定 ID、分组和标题。首页在 `SettingsScaffold` 内使用带通栏组标题的 `LazyVerticalGrid`，内容宽度上限为 800dp，卡片最小宽度从 152dp 随字体比例增大。首页卡片和详情示例区使用设置页的 `large` 圆角、`surface` 底色和 `cardColorShadow`。目录封面铺满卡片上沿，使用中性的 `surfaceContainer`。预览复用只读控件、输入框内阴影、连接列表轮廓及 Cerasus 图标。整张卡片是唯一交互目标，封面和尾部箭头为装饰。详情使用 `ComponentDetailRoute` 和同一个 Navigation 3 宿主。升级时保留 `ComponentSamplesRoute` 的序列化定义以解码旧分类页，再从恢复的栈中移除旧条目。

详情沿用设置页 640dp 内容上限和可折叠毛玻璃顶栏，按组件分区展示示例。输入框、分段选择和标签接收示例区的实际表面颜色。首页和详情分别用惰性网格与列表状态保存位置，并接入状态栏回顶。详情的普通选择使用 `rememberSaveable`，输入、密码显隐和弹层使用 `remember`。密码字段在页面停止时隐藏。重置先清除焦点，再重建本页组合及其协程作用域，恢复滚动位置并取消模拟加载与 Snackbar 任务。

图标按钮封面使用圆形实心按钮与 Cerasus 编辑图标。切换按钮封面并排使用相同的星形图标，轮廓和配色分别取未选中及选中状态。

切换按钮详情展示纯文字、图标和文字、纯图标三种样式，各有独立的可交互选中状态和禁用示例。图文样式复用 `ShadowToggleButton` 的图标插槽，纯图标使用官方 `FilledIconToggleButton`。

切换与关注按钮各有两组模拟加载示例。一组在 1.2 秒结束时更新状态，另一组在 0.6 秒更新并在 1.2 秒恢复可用。内容状态覆盖页面、列表、媒体三种呈现。对话框在同页比较六种变体，长按钮和三操作变体直接展示共享 `ActionDialog` 的换行与纵向布局。弹出菜单在选项 2 和选项 3 之间展示组间分割线，并保留禁用选项。

## 列表快滚条

[`LazyListScrollbar`](../../app/src/main/java/moe/kirakira/ui/components/LazyListScrollbar.kt) 用于已加载内容的正向列表，不支持吸顶标题或反向布局；邀请码与视频弹幕页复用。调用方提供 `LazyListState`、滑轨布局和显示条件，组件不访问业务数据。中间位置按可见条目平均高度估算，首尾通过 `requestScrollToItem` 定位边界。滑块位于末端边缘内侧 2dp，默认使用 Material 的 4dp 宽度、24dp 最小长度与 `outline` 70% 不透明度，按下时变为 8dp，并使用主题强调色 `primary`、100% 不透明度，沿用主题快速空间与效果动画；拖动位置和动画值在绘制阶段读取。快滚条叠加在列表上，不预留额外宽度，也不显示序号或总数提示。

### 收藏剪纸图形

[`FavoritesLogo`](../../app/src/main/java/moe/kirakira/ui/components/FavoritesLogo.kt) 复用 Cerasus 的 `ic_icon_star` 五角星轮廓。图形为 128dp，使用 24 × 24 坐标；正面使用当前主题的 `primary` 原色，左上渐变起点向白色混合 20%。右侧折面叠加 22% 不透明度的色层，色值由原色向黑色混合 11%。星形轮廓使用 4dp 原生投影。入场时由 -72° 旋转到正向，同时从 72% 放大到原尺寸。星形与投影在同一图层旋转、缩放。入场进度沿用[搜索空态插画](#搜索空结果图形)的共享逻辑。

### 关注剪纸图形

[`FollowingLogo`](../../app/src/main/java/moe/kirakira/ui/components/FollowingLogo.kt) 用于主界面关注 Tab 的未开放占位。主轮廓复用底栏 `FollowingTabIcon` 的 `ic_symbol_wifi_tethering` 外圈、内圈与中心路径，等比缩至 82% 后将原信号圆心 `(480, 520)` 对齐纸底圆心 `(480, 480)`，保留原形状和比例，并为动画峰值留出安全区域。整体为 128dp，沿用 960 × 960 坐标；圆形纸底使用当前主题的 `primary` 原色，两层信号弧和中心点均使用浅灰色 `#F5F5F5`。整体叠加左上白色径向柔光。信号弧使用 1dp 投影，中心点和纸底使用 2dp 投影。中心和两层信号弧均沿右下 45° 投射长阴影：路径轮廓按最多 4 个画布单位的弧长间隔采样，将相邻边沿光向扫掠为同绕向四边形，缓存整条投影轮廓；绘制时跟随各纸层缩放，固定裁切在纸底内。三份投影先以不透明黑色合成，再统一施加 20% 透明度，重叠区域不累积加深。这些是基于 Material Design 1 Product icons 纸层、光向与简洁层次原则的应用设计选择，不是 Google 官方产品图标。

入场时保留底栏关注点击的脉冲节奏：中心、内圈、外圈相隔 70ms 启动，各自在 90ms 达到 1.22／1.14／1.10 倍、190ms 回收到 0.95／0.97／0.98 倍、300ms 回到原尺寸，总时长 440ms。各纸层以共同圆心 `(480, 480)` 缩放，纸底静止，信号与投影同步缩放。逐帧状态仅在图层及绘制阶段读取。每次进入组合播放一次，不循环；关闭系统动画及静态预览显示终态。图形无独立语义，保留“暂未开放”标题，无辅助小字；关注业务仍为占位。

`FollowingLogoPreview` 与 `FollowingLogoDarkPreview` 在实际主题 `Surface` 上展示固定终态；`FollowingLogoMotionPreview` 用同一脉冲 AnimationSpec 在 0／90／160／230／440ms 固定采样。通过 Android CLI 独立渲染以上预览检查同心、投影重叠和动画峰值留白，不访问网络或账号状态，不新增截图基线。预览命令见[Compose 布局预览](../development.md#compose-布局预览)。

## 系统状态栏回顶

`StatusBarScrollToTopHost` 在应用导航之上创建 Activity 生命周期绑定的 `StatusBarScrollToTopBridge`。页面通过内部 `StatusBarScrollToTopEffect` 注册现有 `LazyListState`／`ScrollState`，也可提供身份、可回顶条件和挂起回调；`DisposableEffect` 配对注册与清理，最新条件和回调通过 `rememberUpdatedState` 读取，`snapshotFlow` 只同步启用及可回顶条件的变化，不逐像素镜像原生滚动位置。取消异常继续传播，不修改业务数据或触发刷新。

Xiaomi／Redmi／POCO 宿主在 Compose View 下方安装透明、可见、同尺寸的原生 `ScrollView`，以真实滚动范围和 100px 非零哨兵表达可回顶状态。子 View 的内容高度通过 `minimumHeight` 提供，确保在 `ScrollView` 的纵向无界测量下仍形成真实滚动范围。代理不接收触摸与焦点、不显示滚动条，不进入无障碍子树；自身布局和状态同步不转发事件。系统将代理滚回零时，统一分发到当前 Compose 目标。目标或可用状态改变时替换代理，取消旧原生动画；请求期间不重新武装，旧请求的完成结果不能影响新目标。该路线参考 [status_bar_scroll_to_top 的原生实现](https://github.com/HBWuChang/status_bar_scroll_to_top/blob/main/android/src/main/java/com/xiebian/statusbar_scroll_to_top/StatusBarScrollToTopPlugin.java)及其 [HyperOS 3 验证记录](https://github.com/HBWuChang/status_bar_scroll_to_top/blob/main/VALIDATION.md)，参考项目的验证不等同于本客户端的 Compose 实机验收。

OPPO／OnePlus／realme 前台动态监听 `com.oplus.clicktop` 与 `com.color.clicktop`；华为／荣耀兼容 Android 的系统监听 `com.huawei.intent.action.CLICK_STATUSBAR`，并要求发送方具有 `huawei.permission.CLICK_STATUSBAR_BROADCAST`。使用 `ContextCompat.RECEIVER_EXPORTED` 接收系统其他进程发送的事件，后台注销；不在 Manifest 常驻注册，不请求用户权限，不反射厂商私有 API。系统不提供入口或拒绝广播注册时保持原有滚动能力。

导航 Scene 通过 `LocalStatusBarScrollToTopEnabled` 限制为转场结束后的当前可交互页面，嵌套导航同时继承外层条件。主界面、资料与邀请码分页只启用当前且停止切换的页签，保留的视频正文额外遵循 `isActive`、全屏及画中画条件。失焦自动暂停；`StatusBarScrollToTopPauseEffect` 为同窗口弹层保留最高优先级但不可滚动的目标，避免事件穿透到正文。一般正文优先于独立标题，确有内部滚动的页面级空／错误内容优先处理自身滚动；同一时刻仅运行一个回顶任务。

`StatusBarScrollToTopHeader` 为正文提供标题／封面展开动作，也覆盖只有标题折叠的情况。`SettingsColumn`、`AccountFlowScaffold` 集中接入自己的滚动状态；独立列表在状态所有者处注册。`SmoothScrollToTop` 为状态栏、底栏和页内 Tab 重复点击提供共享的 `LazyListState`、`LazyGridState` 与 `ScrollState.smoothScrollToTop` 入口。统一使用 `tween` 与 `FastOutSlowInEasing`，基础时长 600ms，每个视口距离增加 100ms，最多按六个视口计入，最长 1200ms；仅标题折叠时使用 600ms。正文与可折叠标题／封面由同一个进度逐帧驱动，标题位移通过既有 `nestedScrollConnection.onPostScroll` 应用。普通正文按剩余像素距离滚动，Lazy 列表与网格通过公开 `LazyLayoutScrollScope` 随实际布局更新剩余距离，从当前位置连续接近顶部，只在动画结束时对齐首项；已在顶部的内容不启动动画。页面或页签失活、窗口失焦、后台及正文拖动都会取消整段任务。`CollapsibleTopAppBar` 在指针 Initial 阶段观察标题区域的按下事件，立即注册最高优先级的暂停目标，取消当前回顶；该观察不消费事件。标题拖动由 Foundation 的纵向 `draggable` 接管，并通过 Material 滚动行为的 `onPostFling` 完成惯性与吸附，整个拖动和收尾期间维持暂停。新一轮拖动取消旧收尾并接续暂停，组件移除时清理暂停目标。官方顶栏继续按同一 `TopAppBarState` 渲染布局和标题。底栏与页签重复点击共用同一缓动与时长计算，并保留原有的当前列表选择及标题／封面状态规则。

厂商实机验收仍待完成：深列表与仅标题折叠、当前页签独占、重复点击、拖动打断、各类弹层暂停、导航返回、后台恢复，以及全屏与画中画。分别记录设备和系统版本，不将 Debug 构建成功视为这些行为已验证。
