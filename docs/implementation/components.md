# 共享 UI 组件

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护内容状态、连接列表、刷新、顶栏与账号流程布局。开发约束以贡献指南为准。

- [统一内容状态](#统一内容状态)
  - [圆形加载器](#圆形加载器)
- [连接列表分组](#连接列表分组)
- [下拉刷新](#下拉刷新)
- [共享组件接入](#共享组件接入)
  - [顶部导航按钮定位](#顶部导航按钮定位)
  - [可复用 UI 参数示例](#可复用-ui-参数示例)
  - [毛玻璃应用栏](#毛玻璃应用栏)
  - [黑底毛玻璃按钮](#黑底毛玻璃按钮)
  - [顶栏淡色图标底纹](#顶栏淡色图标底纹)
  - [可选的可折叠大标题栏](#可选的可折叠大标题栏)
  - [可复用的顶栏搜索](#可复用的顶栏搜索)
- [图标划线过渡](#图标划线过渡)
- [账号流程布局](#账号流程布局)

## 统一内容状态

评论与弹幕空状态通过 `ContentStatus.emptyTitle` 和 `emptyIconRes` 分别显示“暂无评论”与对话气泡、“暂无弹幕”与字幕图标；这些参数只影响空状态，加载和错误继续使用统一呈现。

[`ContentUnavailableView`](../../app/src/main/java/moe/kirakira/ui/components/ContentUnavailableView.kt) 统一空内容、内容加载失败及未开放页面占位，`ContentStatus` 将 `ContentState` 的加载／错误／空状态映射到 `IndeterminateCircularProgressIndicator` 或该组件。错误说明通过 `ApiFailure.messageRes()` 本地化，业务重试仍由调用方回调执行。

- `PAGE` 用于有界页面空间，内容居中且不足时可滚动；`INLINE` 不创建内部滚动，适合 `LazyColumn`，调用方为无内容状态预留剩余视口高度。评论与弹幕扣除计数栏、工具栏及发送框占用，列表底部 Insets 随内容滚动。
- `MEDIA` 用于黑底图片与视频区域，组件集中管理黑底配色和紧凑间距；极短视口隐藏装饰图案，文字与按钮可滚动。宿主预留关闭、返回与全屏操作区域。
- `ContentUnavailableAction` 仅接收资源解析后的文案、回调和启用状态。主操作统一 `Button`，次操作统一 `TextButton`，纵向居中排列，宽度上限 280dp；页面不传入按钮样式或插槽。`onRetry` 自动生成统一文案的主操作，可通过 `retryEnabled` 禁用，与 `primaryAction` 互斥。
- 资料和统计读取失败在对应内容附近显示组件，不重复弹出 Snackbar；操作失败仍由原有表单、Snackbar 或会话弹窗处理。播放器提供独立 `onRetry` 并绑定 `PlaybackViewModel.play()`，避免失败重试走播放／暂停切换。

管理、安全与隐私设置页的页面级加载分支位于滚动列表或列之外，通过 `Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.Center)` 在顶栏与底部系统栏之间居中，继续复用 `IndeterminateCircularProgressIndicator`。标签页仅在标签与视频均无数据且同时加载时使用这一全页分支，部分数据就绪后的独立状态保留列表内呈现；不改变请求、错误重试或下拉刷新条件。

### 圆形加载器

共享 [IndeterminateCircularProgressIndicator](../../app/src/main/java/moe/kirakira/ui/components/IndeterminateCircularProgressIndicator.kt) 封装官方 Material 2 `CircularProgressIndicator` 的不确定进度重载，使用其原生旋转与圆弧伸缩动画；只将端点固定为 `StrokeCap.Round`，轨道为透明，不自行复制动画或切换全局主题。默认颜色来自 Material 3 `MaterialTheme.colorScheme.primary`，媒体区域可传入白色，原有语义与修饰符继续由调用方提供。

普通加载与播放器中央缓冲使用官方默认 40dp 直径、4dp 线宽；文字按钮加载使用 Material 2 `ButtonDefaults.IconSize` 与 2dp 线宽，保留正常图标的 Expressive 尺寸和按钮容器；图标按钮、账号行与小窗按钮使用 24dp、2dp 线宽；刷新内部采用 Material 2 原始比例 20dp、2.5dp 线宽。按钮触摸目标、文字占位及账号行的 48dp 固定槽位不随加载改变。Material 2 依赖使用现有 Compose BOM 管理，其他界面仍复用 `KIRAKIRATheme`。

## 连接列表分组

[ConnectedListGroup](../../app/src/main/java/moe/kirakira/ui/components/ConnectedListGroup.kt) 提供满宽、零行间距的普通分组布局和固定 1dp 整组阴影，不额外绘制背景或添加内边距，默认不裁剪内容；`SettingsSection` 保留标题间距并在内部复用它，独立条目在调用处显式包裹单项分组。共享 `connectedListItemShapes(index, count)` 以零圆角 `RoundedCornerShape` 为基础，通过官方 `ListItemDefaults.segmentedShapes` 获取首项顶部、末项底部及单项的主题圆角，中间连接边为直角；选中、按压、聚焦、悬停与拖动形状均沿用基础形状，保留官方颜色反馈与无障碍语义。菜单、设置、账号、标签及播放器面板共用此入口。阴影属于容器或列表宿主，条目不再通过组合局部上下文跳过投影，也不绘制和拼接行级阴影。账号侧滑组继续通过 `clipContent` 按整体外轮廓裁剪，外侧阴影及删除按钮的横向间距保持不变。

[ConnectedLazyColumn](../../app/src/main/java/moe/kirakira/ui/components/ConnectedLazyColumn.kt) 在官方 `LazyColumn` 外提供分组声明及统一投影，接收 `LazyListState`、`modifier`、`contentPadding` 和内容 DSL，固定零行间距与正向垂直布局。

DSL 的普通 `item`、`items` 与 `connectedItemsIndexed(groupKey, items, key, contentType, itemContent)` 在构建内容时一起生成不可变分组索引范围，保留原有条目 key 和 contentType；groupKey 在同一列表内必须唯一且跨刷新稳定，空组不占条目。连接行必须满宽，不加外部纵向 padding、粘性标题或位移动画；每个懒列表条目只承载一行。宿主在绘制阶段读取 `layoutInfo.visibleItemsInfo`，按索引范围匹配可见分组，每组复用一个只负责投影的官方 `GraphicsLayer`，使用完整主题轮廓及固定 1dp `shadowElevation`，不额外绘制背景。

首尾可见时使用行的实测位置；不可见的端点延伸到视口外一个视口高度，不估算完整组高、不测量屏幕外行，避免在视口边缘产生虚假的顶部或底部投影。位置换算包含顶部内容内边距，横向内边距按 RTL 解析，投影裁剪至宿主视口；滚动只更新绘制，离开视口的分组及销毁的宿主释放图层。保留连续整组投影的视觉目标，不保证与旧逐行投影逐像素一致。

边缘拉伸使用每个 `ConnectedLazyColumn` 独立创建的官方 `rememberOverscrollEffect()`。Modifier 顺序固定为调用方 modifier → `clipToBounds()` → `overscroll(effect)` → 整组阴影绘制节点，让投影和内容一同参与系统 overscroll；内部 `LazyColumn` 接收同一效果的 `withoutVisualEffect()` 包装，仅负责传递滚动及 fling 事件，避免重复附加效果节点或内容拉伸后露出未同步变形的投影。系统效果为空时，外层及内部均使用空效果，遵循系统配置；下拉刷新指示器仍由外部刷新宿主独立承载。

标签名称按语言注册分组；标签搜索、屏蔽规则和邀请码列表各注册数据分组，继续逐项懒加载并保留分页。屏蔽管理入口只有固定的 2 项、3 项菜单，各以一个普通 `ConnectedListGroup` 放入管理宿主的一个条目。标题、筛选、空状态和分页按钮使用普通条目，仅在分组边界及独立状态区域设置间距。

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

## 下拉刷新

共享 [ContentPullToRefresh](../../app/src/main/java/moe/kirakira/ui/components/ContentPullToRefresh.kt) 保留官方 `PullToRefreshBox` 的状态、手势与阈值，通过 `PullToRefreshDefaults.IndicatorBox` 绘制白色圆形容器，浅深模式保持一致。容器使用官方内置的 `PullToRefreshDefaults.Elevation` 高度阴影，与容器共享位移和顶部裁剪，未拉动且不刷新时由官方宿主关闭投影。外层不额外添加阴影 Modifier，避免容器隐藏后顶部仍残留圆形阴影；这一约束同样适用于没有实时模糊的 Android 8.1–11。内部圆弧使用 `primary`、20dp 外径、2.5dp 线宽、透明轨道与 `StrokeCap.Round`。

拉动反馈依据 [Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html) 和 AndroidX Material `PullRefreshIndicator` 的计算方式：跳过前 40% 拉动距离，后续逐步增长至最大 80% 圆弧；超出阈值时使用有上限的非线性张力，使旋转逐渐减缓，而不是在阈值处填满整圈。未达阈值时透明度为 30%，达阈值后以 300ms 线性补间增至 100%，拖回时反向恢复。箭头基于官方 Rounded `chevron_right` 的中心线绘制，沿圆弧末端切线旋转并按拉动进度缩放路径长度，与圆弧共用固定 2.5dp 的圆角描边，避免缩放填充图标时箭头变细；不绘制旧版实心三角箭头。仅该绘制区域固定 LTR，确保 RTL 下仍顺时针旋转。

拉动阶段的圆弧与箭头先以不透明颜色绘制到同一 `Canvas.saveLayer`，合成后统一应用阈值透明度，避免交叠处变深。局部图层边界向外扩展，容纳圆弧外的箭头，不包含白色容器及阴影。

刷新阶段复用 `IndeterminateCircularProgressIndicator` 的官方 Material 2 动画，拉动与旋转图形以 Material 2 的 100ms `Crossfade` 切换，不套用 Expressive 弹簧。刷新完成后，在官方状态回到隐藏位置之前保留旋转图形，用回收距离驱动整个指示器缩小、淡出，避免退出时重新出现拉动箭头。退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度低于 1 时自动创建局部离屏缓冲，裁掉已经位移的容器或外侧阴影。业务刷新状态仍由调用方提供，局部标记仅控制退场显示，不延长请求或重复触发刷新。来源、许可与改动说明见 [Android 刷新指示器说明](../../third_party/android-refresh/README.md)。

## 共享组件接入

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

### 毛玻璃应用栏

[`FrostedScaffold`](../../app/src/main/java/moe/kirakira/ui/components/FrostedScaffold.kt) 为普通页面创建独立的 `HazeState`，用 `hazeSource` 采样内容、单独绘制顶部背景，再绘制透明的官方顶栏。文字和图标不参与模糊。背景形状、alpha 和原生阴影由 `ThemeShadows.barSurfaceLayer` 在背景子节点上统一管理，避免重复投影或降低前景透明度。胶囊底栏同样分离背景与前景，背景通过 `frostedBarBackground(shape, shadowElevation = BarShadowElevation)` 使用外层主页面采样状态，内部各 Tab 的顶部栏使用独立状态。

`frostedBarBackground` 的可选 `hazeState` 参数允许自定义布局显式复用相同背景样式，默认仍读取当前页面的采样状态；`shadowElevation` 默认 0dp，普通顶栏、视频页顶部共享背景和胶囊底栏显式传入固定的 `BarShadowElevation`，资料顶栏保留默认值。

- 背景使用 `HazeInput.Sources` 采样，源消失时使用 `ClearWhenUnavailable`，不继续保留上一页面画面。普通栏面不使用原生 backdrop；无阴影黑底按钮的接入见[黑底毛玻璃按钮](#黑底毛玻璃按钮)。已在 Android 17 模拟器（`CP41.260828.004.A7`，SkiaGL）上复现 `RenderNode.setBackdropRenderEffect()` 与半透明父层、elevation 同时使用时的灰框和内部矩形异常。对照父层 alpha 为 1、0.5、0.2：普通 elevation 及 Haze Sources 正常，Haze Backdrop 异常，强制 Offscreen 仍异常；移除 Compose/Haze、仅使用 Android View 和 RenderNode 也能复现，而移除 backdrop 恢复正常。证据定位到系统原生 backdrop 的合成路径，尚未定位内部实现的具体错误，也未验证所有设备。版本以 `gradle/libs.versions.toml` 为准，不引入玻璃折射模块。
- Android 12+ 使用主题 `surface` 作为缺失源像素的底色，叠加同色 80% 不透明度遮罩、20dp 模糊与零噪点，背景图层 alpha 为 1。Android 8.1–11 绘制不透明主题 `surface`，在同一背景图层上设置 alpha 为 0.9 和原生 elevation，不挂载采样节点；各版本共用[栏面阴影](theme.md#栏面阴影)的机制，不额外增加阴影版本判断。背景透明度不作用于整个栏或前景。
- 共享背景沿用 Haze 默认采样配置，保留实时更新、模糊半径与遮罩。模糊样式按主题 surface 色缓存，采样输入按页面状态缓存，现有采样层级保持不变。
- 滚动列表的顶部与底部安全区域放入 `contentPadding`，滚动 Column 则在 `verticalScroll` 后添加，初始避开栏面、滚动时内容可进入栏后。`ContentPullToRefresh.indicatorTopPadding` 仅移动覆盖式指示器，不移动滚动视口。历史页的搜索输入通过共享组件覆盖顶栏，不占用列表条目。
- 本人及作者资料页使用铺满屏幕的外层列表，将顶部栏避让放入内容内边距，通过扣除顶部栏及 Tab 高度的 Pager 保留栏下吸顶与嵌套分页滚动；包含滚动封面的顶部共享背景单独采样并与内容共享该页状态，同时统一承载封面、资料区与 Tab 的原生投影。顶栏仅在 Tab 吸顶后复用毛玻璃背景，不单独绘制阴影。非滚动表单保留安全布局。头像裁剪页同样接入本组件，由宿主提供顶部背景与固定阴影；裁剪引擎与底部操作栏保持原布局。视频画面、播放控件及系统栏不接入本组件。

- 视频页为 Tab 栏创建独立 `HazeState`，仅在 Android 12+ 将下方 `HorizontalPager` 接入 `hazeSource`，播放器不参与采样。播放器占位区域与 Tab 共用顶部背景，通过 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)` 沿整体下沿投影；播放器区域的背景黑底位于同一图层，实际播放器仍独立绘制。官方 `PrimaryTabRow` 容器透明，文字与指示器保持清晰。简介、评论、弹幕列表将实测栏高加入顶部 `contentPadding`，初始避开栏面，滚动时从栏后经过；空状态高度同步扣除栏高。刷新指示器通过 `indicatorTopPadding` 避让，评论分页工具栏固定在栏下，游客登录提示并入评论头部条目以保持分页索引；`FloatingComposerLayout.topPadding` 扣除输入面板可用高度，底部安全区域与输入区留白保持原逻辑。全屏与画中画继续由既有播放器布局和详情显隐处理。

### 黑底毛玻璃按钮

[BackdropButtons](../../app/src/main/java/moe/kirakira/ui/components/BackdropButtons.kt) 提供资料页使用的 `BackdropIconButton`，在官方 `FilledIconButton` 的透明表面内部绘制背景。模糊不延伸到最小触摸目标留白，不作用于图标或水波纹；官方组件继续拥有形状、尺寸、点击区域与语义。统一使用 20dp 模糊、零噪点与可用 60%／禁用 30% 的黑色遮罩，默认白色前景，所有状态均无投影。

`MainActivity` 在组合前调用 `enableButtonBackdrops()`，让新按钮的 `HazeInput.Backdrop` 可按 Haze 的平台能力判断使用原生路径；普通应用栏继续显式使用 Sources。各按钮显式接收页面拥有的 `HazeState`，原生路径不可用或失败时回退至 `HazeInput.Sources`，采用 `ClearWhenUnavailable` 清除失效源；缺失源像素透明，不填充不透明底色。Android 12+ 挂载模糊，Android 8.1–11 仅绘制同一半透明黑底。

[ProfileScreen](../../app/src/main/java/moe/kirakira/feature/profile/ProfileScreen.kt) 复用该页已有的采样状态，仅在 Tab 未吸顶时将返回和更多按钮替换为共享黑底白色按钮；吸顶后沿用普通图标按钮和既有毛玻璃顶栏。播放器通过 `PlayerContentFrame` 保留 Media3 默认 `SurfaceView`，控制按钮使用半透明黑底，不接入本组件或 Haze 采样。

### 顶栏淡色图标底纹

[`ShadingIcon`](../../app/src/main/java/moe/kirakira/ui/components/ShadingIcon.kt) 复用首页 Logo 底纹样式：128dp 图标、主题 `primaryFixed` 配色和 20% 透明度，在装饰区域靠末端垂直居中，按布局方向定位并避让顶栏横向系统 Insets。图标保持固定尺寸，不受折叠顶栏高度约束，超出区域的部分由组件裁切。

在承载顶栏的 `Box` 内先绘制底纹，再绘制透明顶栏；通过 `Modifier.matchParentSize()` 让底纹跟随顶栏实际尺寸而不参与测量。底纹位于标题槽之外，可延伸到状态栏后方；不要给装饰额外添加顶部系统内边距。毛玻璃背景和阴影继续由 `FrostedScaffold` 管理。

`icon` 接收现有 Drawable 资源，`endPadding` 默认为 16dp，`alignment` 默认为 `Alignment.CenterEnd`，`offset` 默认为 `DpOffset.Zero`。对齐同时用于图标在宿主内的定位及不受约束的尺寸布局，`offset.x` 的正值沿布局方向向末端移动，`offset.y` 的正值向下移动。首页传入 `logo_kirakira` 并保留 72dp 末端留白避让头像，使用默认对齐和偏移。

设置与历史页指定 `Alignment.BottomEnd`、`endPadding = 0.dp` 和 `DpOffset(32.dp, 32.dp)`，让图标向末端及底部各溢出 32dp，由组件边界裁切。底纹随顶栏实际高度始终贴住底边，下拉展开后也保持底部定位。设置首页使用 `ic_symbol_settings` 并开启旋转；历史页使用 `ic_symbol_history`，保持默认静止状态。历史底纹与普通顶栏一起放在 `SearchableTopAppBar` 的 `topBar` 插槽内，搜索展开时随普通顶栏退场并隐藏，退出搜索后恢复。

使用 `SettingsScaffold` 的设置子页面沿用这一右下角定位，但传入页面自身的现有图标且保持静止：外观使用 `ic_symbol_palette`，播放使用 `ic_symbol_play_circle`，弹幕使用 `ic_custom_danmaku`，关于使用 `ic_symbol_info`，资料使用 `ic_symbol_badge`，隐私使用 `ic_symbol_shield`，安全使用 `ic_symbol_lock`。管理页的共享 `ManagementFrame` 接收同一图标参数，屏蔽总览使用 `ic_symbol_block`，屏蔽分类详情使用 `RuleCategory.iconRes()`，邀请码使用 `ic_symbol_confirmation_number`；账户切换和许可证独立包裹顶栏，分别使用 `ic_symbol_switch_account` 与 `ic_symbol_description`。头像裁剪页是独立的图片编辑顶栏，不绘制该底纹。

```kotlin
Box {
    ShadingIcon(
        icon = R.drawable.ic_symbol_settings,
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

[`SearchableTopAppBar`](../../app/src/main/java/moe/kirakira/ui/components/SearchableTopAppBar.kt) 接收关键词、展开状态、提示文案、输入／展开／清空／退出／提交事件及 `enabled`、`isActive`。`topBar` 插槽提供打开搜索的事件，调用方可保留普通顶栏、动态标题及其他操作，使用配套的 `TopAppBarSearchButton` 放置搜索图标。组件不依赖页面、ViewModel、Repository 或导航控制器，退出是否清空由页面决定。

共享组件通过 `AnimatedContent` 与主题 Expressive spatial／effects 动效从逻辑方向的末端展开搜索栏。搜索栏使用官方 `TopAppBar`、单行 `TextField` 和平面 `IconButton`，容器与下划线透明；字体放大时增加最小栏高。动画离场的顶栏清除语义并阻止指针输入。背景采样和阴影仍由外部宿主提供，普通顶栏与搜索栏必须使用相同 `windowInsets`，默认由各自官方顶栏避让一次，不在外层重复 padding。

只有通过插槽事件主动展开才请求焦点和键盘；恢复已展开的页面或从其他页面返回不会自动聚焦。页面失活、禁用或关闭搜索时释放输入焦点，只有本组件持有焦点时才主动收起键盘，避免影响其他页面。提交先释放焦点、收起键盘，再调用 `onSearch`，不会自动退出。活动搜索模式通过 `NavigationBackHandler` 消费局部返回：键盘可见时先收起，否则调用 `onClose`，页面返回栈不变。

普通固定顶栏接入示例（状态、事件和文案由页面提供）：

```kotlin
SearchableTopAppBar(
    query = query,
    expanded = searchExpanded,
    placeholder = searchPlaceholder,
    onQueryChange = onQueryChange,
    onExpand = { searchExpanded = true },
    onClear = { onQueryChange("") },
    onClose = {
        searchExpanded = false
        onQueryChange("")
    },
    onSearch = onSearch,
    isActive = isActive,
) { openSearch ->
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { /* 页面返回按钮 */ },
        actions = { TopAppBarSearchButton(openSearch, searchPlaceholder) },
        colors = appTopAppBarColors(),
    )
}
```

可折叠顶栏沿用上述参数，替换插槽并在页面的 `onExpand` 中先以主题动效将 `scrollBehavior.state.heightOffset` 动画至 `heightOffsetLimit`，完成后才设置展开状态。开始折叠与搜索期间暂停顶栏的 `nestedScroll` 连接，退出后重新接入并保持折叠；异步折叠工作应随页面失活或账号变化取消。历史页的完整接入见 [`HistoryPage`](../../app/src/main/java/moe/kirakira/feature/history/HistoryPage.kt)。插槽示例：

```kotlin
{ openSearch ->
    CollapsibleTopAppBar(
        title = title,
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        actions = { TopAppBarSearchButton(openSearch, searchPlaceholder, enabled = !searchOpening) },
    )
}
```

个人主页后续可把自身带动态昵称、返回按钮与更多操作的 `TopAppBar` 放入同一插槽，保留外层独立 Haze 采样背景；搜索数据、Tab 范围与过滤策略由个人主页定义。当前仅历史页接入。

## 图标划线过渡

共享组件 `ui/components/AnimatedSlashIcon.kt` 为密码可见性按钮和播放器弹幕 Switch 提供 Material 3 Expressive 划线过渡，复用主题的 `fastSpatialSpec` 弹簧。原始 VectorDrawable 路径只在创建时解析并归一化到 24 × 24 画布；关闭图标拆分为主体与原有圆头斜线，主体的共同区域保持不透明，仅差异区域随限定在 0～1 的进度渐变，斜线沿左上至右下通过裁剪逐段显现，反向切换沿原路径收回。斜线保留弹簧超过 1 的进度，以起点为支点沿 45° 方向伸长后回弹，垂直方向不缩放，保持视觉线宽；静止端点直接绘制原始图标路径，保持现有几何与断开细节。

`rememberSlashIconProgress` 初始值取当前状态，后续通过 Compose `Animatable` 平滑切换；快速点击从当前进度反向，系统动画时长缩放及关闭动画由 Compose 处理。播放器在控制栏显隐分支外持有该状态，重新显示控制栏不会重播；弹幕设置异步读入时通过 `ready` 建立初始进度，不播放加载过渡。密码按钮的划线表示“隐藏密码”操作，密码遮罩和弹幕业务状态立即切换；图标仅提供视觉过渡，不改变按钮语义、禁用表现或开关尺寸。设置页普通 Switch 不新增图标。

## 账号流程布局

`AuthScreen` 与账号安全操作页共用 [AccountFlowScaffold](../../app/src/main/java/moe/kirakira/ui/components/AccountFlowScaffold.kt)：`FrostedScaffold` 提供 `surface` 背景和独立毛玻璃采样，`CenterAlignedTopAppBar` 的标题槽居中显示主题色流程图标；登录 Logo 为 40dp，其余图标为 32dp，不使用底板。非登录步骤的返回使用官方平面 `IconButton` 和默认 AppBar 槽位定位，所有步骤均无关闭按钮。`AuthScreen.onClose` 参数保留调用兼容性，认证完成仍通过 `AuthPage` 的回调退出。

`AccountFlowColumn` 提供最大 480dp 内容宽度、24dp 页面留白和完整滚动视口；`AccountFlowHeader` 使用半粗 `headlineLarge` 与可选说明／进度插槽。标题下方间隔 12dp，`AccountFlowProgress` 横排本地化的紧凑步骤文字（如“2 / 2”）和 4dp 进度条，整组提供完整步骤描述与进度语义。页头到表单及独立分组之间为 32dp，字段间距为 24dp。认证页按步骤启用既有本地化说明资源；安全流程按步骤、当前验证方式及同步状态选择说明，常规页以一到两句引导表达待完成操作，状态核对和本机同步分支只显示现有状态说明。

主操作通过 `AccountFlowScaffold.bottomBar` 接入 `AccountFlowActionBar`，固定在底部，横向与正文对齐、纵向留白为 16dp；`AccountFlowSubmitButton` 占满内容宽度，使用 `ButtonDefaults.MediumContainerHeight` 配套形状、内边距和文字 API、共享彩色阴影及加载语义，加载时仍测量标签以适应字体缩放。发送验证码、复制及打开验证器等辅助操作保留在对应内容旁。字段错误说明保留在输入框下，全局错误复用 `AccountFlowError` 的错误色文字与无障碍播报，同步重试和代码保存确认保留短标签与完整无障碍描述。

宿主根节点统一应用 `imePadding`，表单和底栏不重复处理 IME；`AccountFlowActionBar` 复用 [FormActionBar](../../app/src/main/java/moe/kirakira/ui/components/FormActionBar.kt) 的 `surface` 背景、固定 4dp 原生阴影与系统安全区域。Scaffold 将底栏实测高度交给正文的滚动 padding，确保末项可滚动至按钮上方，Snackbar 自动避让底栏和键盘。

`AccountFlowSection` 只负责无底板的标题、上下文和表单布局，接受可空 `title`、可选 `supportingContent`、`actionContent` 及正文插槽，不持有业务状态。有标题时上下文在标题下方，相隔 4dp；标题为空时上下文直接进入页头行，邮箱保留普通文字语义，不标记为标题。页头使用 `FlowRow`，操作插槽由末端对齐的加权 `Box` 承载，并通过 `width(IntrinsicSize.Max)` 按完整操作内容的固有宽度决定换行，避免把长按钮挤进剩余宽度；发送按钮在空间不足时换行，换行后仍靠内容末端对齐，长邮箱允许完整换行。分组页头与字段相隔 16dp，没有标题、上下文或操作时不组合页头及关联间距。认证与当前邮箱核验直接显示邮箱，不重复“账号”或邮箱标签；新邮箱与身份验证保留任务标题。邮箱确认页以 32dp 间距分开身份验证与新邮箱验证，输入框继续使用各自标签。登录辅助入口也使用可换行布局；认证表单通过 `onResend` 回调把发送操作放在验证码旁。发送操作前移后，密码到验证码、当前邮箱验证码到新邮箱验证码的 IME“下一项”通过显式焦点目标连接，普通按钮仍可通过硬件键盘访问。页面只在存在通知或错误时插入反馈区域及 12dp 间距；操作引导保留在页头，不重复添加账号或邮箱分组标签。

账号流程靠边文字按钮沿用 [ExpandableText](../../app/src/main/java/moe/kirakira/ui/components/ExpandableText.kt) 的对齐方式：显式保留 `ButtonDefaults.TextButtonContentPadding`，按当前布局方向读取 start／end 内边距；忘记密码和联系支持向 start 偏移对应内边距，注册和发送验证码向 end 偏移对应内边距，使可见文字与标题及输入框边缘对齐。按钮沿用相同的 48dp 最小宽度，避免短标签因额外居中留白而错位，保留官方最小触摸目标、涟漪及禁用语义。登录注册入口与表单之间使用 `AccountFlowDefaults.FieldSpacing` 的 24dp 留白，并使用按固有宽度换行的末端对齐容器；偏移只作用于按钮，正文宽度与页面留白保持原值。

字段错误态复用内部 `AccountFlowFieldErrorIcon(modifier)`，绘制 24dp 官方 Material Symbols Rounded `error` 的 FILL 1 资源并使用主题 `error` 色，原有空心资源继续保留。普通字段通过 `trailingIcon` 显示；带显隐按钮的密码框在同一行显示错误图标与原按钮，间隔 8dp，保留按钮的点击与键盘语义。图标的 `contentDescription` 为 `null`，无独立点击、背景或阴影，错误说明仍由输入框 supporting text 提供，沿用官方间距并避免重复播报。登录／注册密码及重置密码的新密码字段均绑定现有 `fieldErrors[AuthField.PASSWORD]`；安全表单继续使用 `fieldErrors[SecurityField]`。正常状态不生成错误图标或错误说明占位，不改变校验、焦点与显隐状态的所有权。现有预览提供单字段、多字段及密码显示时的错误态，并配置中英文、浅深主题、320dp 窄屏和两倍字体；这些配置不代表已完成设备或截图验证。

[AccountFlowFeedback](../../app/src/main/java/moe/kirakira/ui/components/AccountFlowFeedback.kt) 在组合内保留非敏感提示文字，供退出动画完成前绘制；`Transition<Boolean>` 仅管理显示状态，业务错误仍由页面传入。页面级提示使用淡入淡出与纵向展开／收缩，关联留白放在内容插槽中一同过渡。`accountFlowSupportingError` 在字段错误退出完成后返回空插槽，输入框通过 `animateContentSize` 平滑调整整体高度，恢复官方正常高度；退出中的提示清除语义，保留当前字段校验及焦点逻辑。所有过渡使用主题 `motionScheme`，遵循系统动画缩放。`accountFlowCodeTextStyle` 为认证与安全验证码以及代码展示提供统一等宽样式，参数遵循[账号表单规范](../../CONTRIBUTING.md#账号表单与保存)。

[OtpInputField](../../app/src/main/java/moe/kirakira/ui/components/OtpInputField.kt) 使用一个 `BasicTextField` 处理六格输入，代码由流程持有，组合内仅用普通 `remember` 保留选择位置；完整编辑器保留布局范围和语义，视觉格使用官方 `OutlinedTextFieldDefaults.Container`，只在当前格连接编辑器的聚焦交互源。六格字符在共享等宽样式上采用主题 `headlineSmall` 的字号与行高，保留 Medium 字重和 1sp 字距；格子仅限制最小高度，字体放大时随文字高度增长。点击已填格选中对应字符，空格定位到已有代码末端；粘贴、退格、全选替换与 IME 操作由同一编辑器处理。六格内容不重复提供语义，整个输入组作为一个编辑目标；错误图标和提示位于组级区域。数字输入与字母数字备用码分别使用数字及 ASCII 键盘，过滤规则对应后端字符集，六位输入不自动提交。认证页额外提供完整恢复码输入，粘贴完整恢复码时切换显示，未将敏感材料放入可保存状态。

`AuthFilledCodePreview` 与 `SecurityFilledCodePreview` 提供独立的“验证码已填写”预览，涵盖邮箱验证码、字母数字备用码和认证页的长恢复码；安全页另展示邮箱与 TOTP 两种修改邮箱核验、邮箱与 TOTP 两种修改密码核验，以及绑定验证器的确认输入。填写值仅为预览专用虚构材料，密码保持为空；预览配置包含中英文、浅深主题和窄屏大字体，不代表已完成设备视觉检查。

TOTP 页面把二维码与打开验证器操作放在同一区域，密钥与复制操作相邻，验证输入独立分组。密钥和恢复码沿用原有专用容器，复制按钮在容器外绘制以保留其阴影；备用码按当前等宽字形的实际测量宽度，在两列和一列之间选择，恢复码按四字符分组换行。窄屏和大字体仅调整内容换行与列数，配色、阴影、底栏和流程状态不变。
