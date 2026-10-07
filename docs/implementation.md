# 实现说明

[返回项目首页](../README.md) · [功能现状](features.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

本文说明当前实现及接入方式；开发约束统一维护在贡献指南。依赖与开发环境配置来源见[开发环境](development.md#开发环境)。

## 统一内容状态

评论与弹幕空状态通过 `ContentStatus.emptyTitle` 和 `emptyIconRes` 分别显示“暂无评论”与对话气泡、“暂无弹幕”与字幕图标；这些参数只影响空状态，加载和错误继续使用统一呈现。

[`ContentUnavailableView`](../app/src/main/java/moe/kirakira/ui/components/ContentUnavailableView.kt) 统一空内容、内容加载失败及未开放页面占位，`ContentStatus` 将 `ContentState` 的加载／错误／空状态映射到 `IndeterminateCircularProgressIndicator` 或该组件。错误说明通过 `ApiFailure.messageRes()` 本地化，业务重试仍由调用方回调执行。

- `PAGE` 用于有界页面空间，内容居中且不足时可滚动；`INLINE` 不创建内部滚动，适合 `LazyColumn`，调用方为无内容状态预留剩余视口高度。评论与弹幕扣除计数栏、工具栏及发送框占用，列表底部 Insets 随内容滚动。
- `MEDIA` 用于黑底图片与视频区域，组件集中管理黑底配色和紧凑间距；极短视口隐藏装饰图案，文字与按钮可滚动。宿主预留关闭、返回与全屏操作区域。
- `ContentUnavailableAction` 仅接收资源解析后的文案、回调和启用状态。主操作统一 `Button`，次操作统一 `TextButton`，纵向居中排列，宽度上限 280dp；页面不传入按钮样式或插槽。`onRetry` 自动生成统一文案的主操作，可通过 `retryEnabled` 禁用，与 `primaryAction` 互斥。
- 资料和统计读取失败在对应内容附近显示组件，不重复弹出 Snackbar；操作失败仍由原有表单、Snackbar 或会话弹窗处理。播放器提供独立 `onRetry` 并绑定 `PlaybackViewModel.play()`，避免失败重试走播放／暂停切换。

### 圆形加载器

共享 [IndeterminateCircularProgressIndicator](../app/src/main/java/moe/kirakira/ui/components/IndeterminateCircularProgressIndicator.kt) 封装官方 Material 2 `CircularProgressIndicator` 的不确定进度重载，使用其原生旋转与圆弧伸缩动画；只将端点固定为 `StrokeCap.Round`，轨道为透明，不自行复制动画或切换全局主题。默认颜色来自 Material 3 `MaterialTheme.colorScheme.primary`，媒体区域可传入白色，原有语义与修饰符继续由调用方提供。

普通加载与播放器中央缓冲使用官方默认 40dp 直径、4dp 线宽；文字按钮加载使用 Material 2 `ButtonDefaults.IconSize` 与 2dp 线宽，保留正常图标的 Expressive 尺寸和按钮容器；图标按钮、账号行与小窗按钮使用 24dp、2dp 线宽；刷新内部采用 Material 2 原始比例 20dp、2.5dp 线宽。按钮触摸目标、文字占位及账号行的 48dp 固定槽位不随加载改变。Material 2 依赖使用现有 Compose BOM 管理，其他界面仍复用 `KIRAKIRATheme`。

## 导航与状态管理

应用保持单 Activity、单 `:app` 模块。[KIRAKIRAApp](../app/src/main/java/moe/kirakira/KIRAKIRAApp.kt) 连接应用级状态和导航，[AppNavHost](../app/src/main/java/moe/kirakira/ui/navigation/AppNavHost.kt) 注册页面并处理进入、返回事件。主题、会话及认证状态使用 ViewModel，API 和会话存储由 `data/auth` Repository 隔离，通过构造参数注入依赖。

页面导航使用 Navigation 3，`rememberNavBackStack` 保存返回栈，[ActivityNavDisplay](../app/src/main/java/moe/kirakira/ui/navigation/ActivityNavDisplay.kt) 封装 `NavDisplay`，保留页面状态和生命周期；页面通过 `NavigationPage` 接入宿主。路由和宿主使用要求见[架构规范](../CONTRIBUTING.md#技术选型与架构)。

普通进入与返回复用 AOSP Activity 的横移和透明度参数，开启预测性返回时采用手势与松手收尾两个阶段。`ThemeViewModel.predictiveBackEnabled` 从现有 `kirakira_settings` 的 `predictive_back_enabled` 读取，缺失值为 false；与外观状态一起在 IO 调度器加载，更新使用 SharedPreferences apply，沿用非敏感设置备份。状态及修改回调经 `MainActivity`、`KIRAKIRAApp` 与 `AppNavHost` 显式传入外观页，状态同时传入顶层及认证嵌套 `ActivityNavDisplay`。

普通页面在关闭开关时仍接收 Navigation Event，但忽略手势进度，不创建双页面预览；完成时重新检查返回保护并出栈一次，取消不改变返回栈。开启时保留原有手势与收尾流程；配置变化会替换普通返回处理器并清理临时动画，不主动出栈。`LocalNavigationPageTransform` 提供稳定的变换读取函数，`NavigationPage` 仅在 `graphicsLayer` 块内读取逐帧状态，遮罩仅在 Canvas 绘制阶段读取，避免逐帧更新组合上下文。源码版本、双页面 Scene、几何变换及公开 API 适配差异统一维护在 [Android 转场说明](../third_party/android-motion/README.md)。

`ActivityScene` 通过稳定状态引用读取最新返回栈，普通页面仅在自身 `contentKey` 为栈顶、生命周期至少为 STARTED、目标可见状态为 `EnterExitState.Visible` 且不处于预测性返回预览时开放内容交互，不再等待整段转场结束后恢复到 RESUMED。退场页保留期间消费触摸并清空内容的无障碍语义；普通返回使用与 `fadeOut` 相同的 `activityCloseFadeSpec`（延迟 35ms、持续 83ms、线性曲线）驱动透明度观察状态，`derivedStateOf` 仅在淡出完成边界移除已透明的页面内容及触摸屏蔽层，使点击落到新页。外层 Scene 仍完成原有 450ms 横移动画；在旧页开始的触摸随旧内容移除而取消，不转发给新页。预测性返回预览、收尾及其无动画出栈不走此提前移除逻辑，图片查看器仍要求 RESUMED 且可见状态与目标状态都为 Visible，并保留页面自身的转场禁用条件。所有动画继续遵循系统动画时长缩放，不使用固定延时交接。

主界面标签切换保留同一套横移与淡入淡出参数。栏面与连接列表使用平台 elevation 投影，半透明栏面由背景图层统一承载透明度与阴影；毛玻璃固定使用 Haze 背景采样，规避已在 Android 17 模拟器上复现的原生 backdrop、页面 alpha 与 elevation 合成异常。底部胶囊导航栏位于标签页转场之外。

导航会在切页开始时组合和测量新显示的页面；可保存状态与 ViewModel 装饰器保存状态，并不保留离屏页面的整棵组合树。`SharedTransitionLayout` 的预布局测量还包含 LazyColumn 首次子组合，不能将整段预布局时间当成重复测量成本。转场定位优先查看首帧的主线程切片；逐组件追踪只用于定位，交付与对照采样不携带临时探针。构建类型与 ART 编译状态会影响 Compose 执行成本，使用 [本地性能变体](development.md#动画性能采样) 并分别报告 Debug、非调试和预编译样本，不把更换构建类型当作同配置代码优化收益。

### 图片查看与导出

图片页通过 [ImageViewerNavigation](../app/src/main/java/moe/kirakira/ui/navigation/ImageViewerNavigation.kt) 将打开、关闭、预测返回三种转场写入官方 `NavDisplay` 元数据，由 `ActivityScene` 继承顶部 `NavEntry` 的元数据。不要对 `Scene.key` 做路由类型判断：这里的键来自 `NavEntry.contentKey`，是用于内容身份与状态恢复的字符串，并非路由对象。打开时采用淡入与 `KeepUntilTransitionsFinished`，保留原位的来源页直到转场结束；返回时来源页无位移、查看页淡出。图片返回处理器读取同一份元数据标记，普通页继续使用原 AOSP 转场。接入方式与 [Navigation 3 官方转场配置](https://developer.android.com/guide/navigation/navigation-3/animate-destinations) 一致。

图片页始终使用独立的 `NavigationBackHandler`、图片 `PredictivePopTransitionKey`、`imageSharedBounds` 与 `imageReturnTransform`，不受普通页面的 `predictiveBackEnabled` 开关影响。关闭开关后，匹配来源的图片仍在按钮关闭、系统返回及边缘手势中缩回原位；无来源时沿用淡出，取消手势恢复原有查看与缩放状态。

资料头像与全屏查看器使用 Compose 官方 `SharedTransitionLayout` / `sharedBounds` 连接。共享键包括来源页与图片身份（例如 `profile/<UUID>/avatar`），避免不同用户的图片错误配对。图片页单独使用 Navigation 3 的可寻址预测返回进度；图片进出时来源页面保持原位，查看器直接覆盖其上，普通页面继续由 AOSP 动效宿主管理。共享边界、页面显隐、背景与控件显隐共用 [EmphasizedEasing](../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 的 Material 3 emphasized 曲线及 420ms 时长；Tab 点击也复用该曲线。共享边界插值时，图片由圆形头像逐渐展开成直角视口：采用 `RemeasureToBounds` 让图片在变化的宽高比中重新排版，避免全屏图片压缩时露出平直内容边缘。静止时由图片层裁剪，匹配过渡时仅由共享覆盖层裁剪；覆盖层圆角根据当前共享边界逐帧计算，关闭及预测返回时跟随实际收缩进度。固定全屏的黑色背景在共享覆盖层下方按查看页可见进度淡入淡出，不随图片边界位移。头像白色圆框留在资料页原位，不参与共享边界动画。返回时用 Telephoto 的当前内容几何反向补偿缩放，手势取消无需修改其内部缩放状态。来源不在组合中时仅淡入或淡出。用户关闭系统动画时，Compose
时长缩放统一生效。

[ViewerImage](../app/src/main/java/moe/kirakira/feature/imageviewer/ViewerImage.kt) 是可序列化的图片描述，支持资源 ID、可读 `content://` URI 和公开 HTTPS 图片 URL；调用方负责保留 URI 读取授权。查看 UI 位于 `ui/components/image/`，只接收图片模型、状态、回调与可选共享元素修饰符；导出由 `feature/imageviewer/` 负责。查看器使用全屏黑色背景，系统状态栏和导航栏保持透明并使用浅色图标；透明顶栏仅放深色圆形底衬的白色关闭按钮，确保白色图片上仍有足够对比度；转场禁用点击时保留相同配色，由整体淡出控制透明度。下载和复制位于避开系统导航栏的右下角 `HorizontalFloatingToolbar`。背景通过单独的覆盖层绘制，两组控件通过共享转场覆盖层悬浮在图片上方并淡入淡出。复用时将稳定的来源键同时用于缩略图的 `imageSharedBounds(key, viewer = false)` 与查看页的 `imageSharedBounds(key, viewer = true)`，并通过 `ImageViewerRoute` 打开页面。无可匹配来源时传入 `null` 键。

来源图片只在匹配的共享动画进行时隐藏，动画停止绘制覆盖层的同一帧恢复原位图片；`isMatchFound` 可能持续到查看器出栈销毁，不能单独用它控制头像显隐。头像圆框在来源页单独绘制于图片外沿，过渡期间始终保持原位；查看器不再创建对应的共享装饰层。圆形点击／水波纹层与共享图片并列，单独裁剪，不给共享图片的父容器再加圆形裁剪。

控件显隐由 [ImageViewerControlsState](../app/src/main/java/moe/kirakira/ui/components/image/ImageViewerControlsState.kt) 管理，查看 UI 接收 `controlsVisible`、`onToggleControls` 和 `onInteractionChange`。默认空闲时长为 3 秒，遵循 `AccessibilityManager.calculateRecommendedTimeoutMillis`；仅在页面 RESUMED、转场结束且无触摸／控件焦点／结果提示时计时，加载、错误、权限请求与文件操作时保留控件。界面根节点只观察触摸，不消费事件；单击使用 [Telephoto 的 `onClick`](https://saket.github.io/telephoto/zoomableimage/#click-listeners)，不叠加 `clickable` 抢占双击与缩放。顶栏和工具栏使用 Material 3 动效淡出，隐藏后移出点击和无障碍树，系统返回始终由导航宿主处理。

缩放使用 [Telephoto](https://saket.github.io/telephoto/zoomableimage/) 与其 Coil 3 适配。保存保留原始字节和 MIME 类型：Android 10+ 通过 `MediaStore.Images` 的 `IS_PENDING` 公开，Android 8–9 获得旧版写权限后写入公共 `Pictures/KIRAKIRA` 并扫描媒体。复制将原始字节写入专用缓存，通过 `FileProvider` 交给系统剪贴板，超过七天的复制缓存于下一次复制时清理。网络头像通过公开 HTTPS 来源接口加载和导出。

### 主界面切换与底栏动效

`MainBottomBar` 使用圆角 `Surface` 包裹四个等宽的可选择 Tab，两侧留白 16dp、上下留白 8dp、最大宽度 560dp，阴影按胶囊形状固定绘制。系统导航 Insets 由外层容器处理，内部按钮行使用 4dp 内边距和 4dp 按钮间距，按钮最小高度为 64dp；选中胶囊覆盖图标与文字，使用 `primaryContainer`，前景使用 `primary`，保留 Tab 选中语义、按压反馈与图标动效。完整容器高度继续反馈给 Scaffold 内容留白与播放小窗定位；首页、搜索和“我”的底部留白放入滚动内容，页面背景延伸至悬浮栏后方。

[MainScreen](../app/src/main/java/moe/kirakira/feature/main/MainScreen.kt) 将四个 Tab 作为主界面内的局部状态，未建立独立返回栈。Tab 切换复用[普通 Activity 转场](../third_party/android-motion/README.md#普通-activity-转场)的位移、时长、系统缓动与淡入淡出参数，按排列顺序决定方向，RTL 布局镜像处理；首页头像跳转“我”使用同一套动画。

底栏图标动画与页面切换独立：首页与“我”在 300ms 内从描边连续形变为官方 Filled 造型，并轻微收缩后回弹，取消选中时反向恢复；搜索轻摆并缩放回弹；关注在 440ms 内完成由内向外扩散并回弹。图标动画不使用透明度渐变，重复点击当前 Tab 不重播，首次显示与状态恢复直接呈现最终形态，快速切换从当前进度转向新状态，并遵循系统动画时长设置。

图标来源、路径对应、形变适配及搜索／关注的缩放和延迟参数统一见 [Material Symbols 说明](../third_party/material-symbols/README.md#底栏图标形变)。

## 视频页与资料页分页

两页的 Primary Tab 显式设置选中内容色为 `MaterialTheme.colorScheme.primary`、未选中内容色为 `onSurfaceVariant`，遵循 [Material Tabs 配色规范](https://m3.material.io/components/tabs/specs)。不使用 `Tab` 默认的选中与未选中同色参数，颜色随当前主题更新。

资料页以铺满屏幕的外层 `LazyColumn` 承载资料信息、`stickyHeader` Tab 栏和固定为剩余视口高度的 `HorizontalPager`。顶部栏高度放入外层列表的 `contentPadding`，不从滚动容器外部避让，保证文字可经过透明栏后。Pager 高度扣除顶部栏和实测 Tab 高度，使外层列表滚动到末端时 Tab 恰好停在顶部栏下沿。分页内列表通过嵌套滚动优先滚走资料信息，回到列表顶部后再向下展开资料；背景跟随外层列表，各 Tab 保留独立列表位置。底部系统内边距仍放在分页列表的 `contentPadding` 中，刷新指示器通过 `indicatorTopPadding` 保持在顶部栏下方。

资料页使用页面自身管理的 `Scaffold`，通过外层列表 `layoutInfo.visibleItemsInfo` 中 key 为 `profile_tabs` 的条目是否到达 `viewportStartOffset + beforeContentPadding` 判断顶栏下沿吸顶，不依赖固定滚动距离或条目索引。吸顶前不绘制顶栏毛玻璃背景，返回和更多按钮使用 `ShadowFilledTonalIconButton`；吸顶后单独绘制 `frostedBarBackground(hazeState = …)` 背景并切换为官方普通 `IconButton`，解除吸顶时立即恢复。顶部共享背景和内容沿用该页独立采样状态，顶栏不单独投影，封面、资料区与 Tab 统一由顶部共享背景承载固定阴影。简介展开、字体缩放及错误条目的高度变化由实际布局自动反映，恢复列表位置后重新推导吸顶状态。

`ProfileHeaderBackground` 在既有 `matchParentSize()` 背景宿主中绘制不透明页面底色与封面，使用 `barSurfaceLayer()` 的 alpha 为 1、固定 4dp 原生 elevation。宿主不参与页面尺寸测量，在前景列表测量完成后读取 Tab 条目，下沿为 `beforeContentPadding + tabs.offset + tabs.size`，限制在视口范围内；Tab 尚未进入可见布局时背景覆盖视口，投影边界位于屏幕外，零尺寸不绘制。背景宽度跟随页面视口，封面用顶部对齐的无界高度测量保留原有尺寸、裁切、位移与渐变，不随宿主高度变化缩放。Tab 不独立升高，Pager 不重复绘制不透明容器底色，列表背景由页面底色提供，使投影只在顶部整体表面的外侧出现，不增加阴影裁剪、坐标回调或逐帧组合状态。

资料页的顶栏昵称直接复用 Tab 吸顶状态，与毛玻璃背景和图标按钮样式同步：吸顶后显示单行昵称，超长省略，解除吸顶后立即隐藏。不再测量正文昵称位置或向资料头传递坐标回调。

两页的 `PrimaryTabRow` 共用 [PagerTabIndicator](../app/src/main/java/moe/kirakira/ui/components/PagerTabIndicator.kt)，并关闭默认底部分隔线。视频页由 `VideoPage` 的常驻 `VideoPlayerViewport` 绘制顶部播放器，`VideoScreen` 保留对应高度的占位区域，下方 Pager 与透明 Tab 栏叠放；播放器占位区域与 Tab 共用顶部背景的固定 4dp 原生投影，文字与指示器独立绘制。资料页 Tab 保留不透明配色、吸顶布局与绘制层级，固定 4dp 原生 elevation 由封面、资料区和 Tab 共用的顶部背景承载。在测量阶段读取 `currentPage + currentPageOffsetFraction`。参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，分别以 `sin(πt/2)` 和 `1−cos(πt/2)` 插值前缘、后缘，使其先伸长再收缩；RTL 下通过相对布局镜像。指示器直接跟随拖动、回弹和点击切页的实际进度，保留 Compose 官方主 Tab 指示器的默认高度与主题颜色；形状显式采用 Material Components 主 Tab 的上圆下平样式，顶部左右圆角为 3dp、底部左右为直角，底边贴齐 Tab 栏底部，不使用 Compose 默认的完整胶囊形状。

视频页的 `VideoContentLayout` 使用同一个 `Layout` 承载分页 Column、顶部共享背景与 Tab 前景三个同级节点，按此顺序绘制：分页内容在最下层，背景覆盖经过栏后的滚动内容，Tab 文字、指示器与触摸区域位于背景之上。测量时先测量播放器下方剩余空间内的 Tab，以当前 placeable 高度确定背景下沿，再测量分页和背景；背景高度为播放器高度与实测 Tab 高度之和，限制在视口范围内，零尺寸不绘制。背景复用 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)`，统一承载透明度和 elevation，并在播放器占位区域绘制黑底；Tab 不再有独立背景投影。实际播放器、原生视频表面与控件仍由常驻播放器节点独立绘制，不参与背景模糊或透明度处理。分页位置、列表留白及刷新避让继续使用原有 Tab 高度状态，不新增坐标回调或尺寸状态。

点击切页共用 [rememberTabChangeHandler](../app/src/main/java/moe/kirakira/ui/components/TabTransition.kt)，取消上一次点击启动的滚动任务后从当前 Pager 位置转向新目标。滚动与图片查看器复用 [EmphasizedEasing](../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 提供的 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration) 双段路径：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 使用 Compose `PathEasing` 兼容。动画快速推进后平缓收尾，不越过目标页；相邻页为 500ms，跨页按距离延长至最多 650ms。Tab 的选中状态统一使用 `currentPage`。

## 视频列表布局

[评论页](../app/src/main/java/moe/kirakira/feature/video/VideoCommentsPage.kt)与[弹幕条目](../app/src/main/java/moe/kirakira/feature/video/DanmakuListItem.kt)使用透明背景的普通 `Row`／`Column` 布局，不使用分段背景、首尾圆角或分段间距，也不添加卡片或分割线；保留条目内边距、评论头像与操作按钮，以及弹幕左侧时间和右侧正文的对齐。

视频简介、评论和弹幕三个标签页不指定页面背景，保持透明并显示外层 `Scaffold` 默认的 `MaterialTheme.colorScheme.surface` 背景；输入框等独立组件保留各自的容器色。评论和弹幕使用 8dp 外边距；评论复用 `HorizontalFloatingToolbar` 与 `CommentJumpDialog`，在右上角悬浮显示快速前后页定位和页码跳转；计数标题按 Toolbar 实测尺寸避让，窄屏和大字体自动上下排列。不实现本地排序与楼层跳转。底部系统内边距放入 LazyColumn 的 contentPadding，输入框使用 IME 内边距。

评论翻页工具栏采用 48dp 紧凑高度，横向内边距为 4dp、纵向为 0dp；高度取 48dp 与页码实测文字高度加官方 TextButton 上下内边距的较大值，保留标准触摸区域并适应字体缩放。背景使用主题 `surface`（浅色纯白、深色跟随主题），箭头继承 `onSurfaceVariant`，页码按钮保留官方强调色和禁用配色；展开与折叠的原生阴影均通过工具栏官方 elevation 参数固定为 4dp，沿默认胶囊形状投影。页码按钮按剩余宽度测量，过长时单行省略，完整页码仍由无障碍描述提供；两端箭头优先保留布局空间，标题继续按工具栏实测尺寸避让。

评论通过 `CommentListLoader` 保存连续页区间，继续使用服务端每页 20 条的 API。各页合并后按评论 ID 去重并保留所属页；`snapshotFlow` 在可见评论距离已加载区间边缘不超过 3 条时请求相邻页，同页请求去重，空页或首末页停止对应方向加载。上下加载具有独立状态和显式失败重试，不自动重复失败请求。列表使用稳定评论 key；向前插入时额外按旧可见评论及像素偏移保持锚点，避免顶部计数或状态条目成为锚点后引起跳动。翻页器页码来自逻辑滚动锚点之后的首条可见评论，忽略仅处于顶部 contentPadding 中的上一页尾项；自动加载不发出定位事件。跳转使用下一次测量定位，确认目标 key 已出现在新列表布局后才消费请求并恢复相邻页加载；显式跳转使用可消费的定位请求，已加载页直接定位，其他页成功后重建窗口。请求代次和账号版本共同拒绝过期响应，跳转、刷新及账号切换取消失效请求。

评论下拉刷新仅在第 1 页已加载且滚动处于顶部时启用（空列表同样允许）；中间页的上边缘只用于前页加载。刷新成功后重建第 1 页，失败保留原内容；局部赞踩只重读所属页，保留其他页和阅读位置，重读期间禁用该页互动。发布成功后根据最新总数读取末页并定位，回传评论已出现在列表时隐藏发布回显。首页、视频当前标签页、弹幕和作者资料继续使用 Material 3 `PullToRefreshBox`；共享包装器的 `enabled` 默认保持开启。加载与错误状态独立于现有内容，不使用演示数据回填，错误状态提供重试按钮。

## 关注与粉丝列表

`FollowListRoute(uid, kind)` 为可序列化的 Navigation 3 路由，两种列表共享 `feature/follow/` 页面和导航条目级 `FollowListViewModel`。主页统计通过事件回调进入列表，列表行按 UID 打开 `ProfileRoute`，导航宿主防止重复入栈；返回沿用该条目的数据和滚动状态。

`ContentRepository.followList` 复用账号快照与 revision 守卫，检查 `success`、必需的 `result`、非负 `totalCount` 和正 UID，将私有 DTO 转为用户摘要及分页领域模型。昵称缺失时回退到用户名；头像复用公开图片客户端。页面不逐行补读关注关系，不保存 Cookie 或列表缓存。

ViewModel 从第 1 页开始，每页 50 条，保留后端顺序并按 UID 去重；结束判断使用原始返回条数的累计值与最新总数，空页直接结束。单个请求任务和请求序号协调刷新、分页与取消，过期结果不能覆盖最新状态。刷新成功替换第一页并重置分页进度；网络、超时与服务器失败保留旧内容和原分页进度，刷新失败需重试刷新，分页失败需手动重试该页。其他失败清除内容及分页状态。账号 revision 变化取消任务、清空内容并重新请求，UI 以 revision 校验展示状态并重建滚动状态。

列表复用 `FrostedScaffold`，正文使用普通 `LazyColumn` 与官方 `ListItem`，按稳定 UID 逐项懒加载。条目容器透明，整行点击保留水波纹及禁用语义，不使用分段圆角、卡片背景或分组阴影；头像为 48dp，内容横向内边距采用 `ListItem` 默认值。首次加载与尾部分页使用共享圆形加载器；刷新使用 `ContentPullToRefresh` 覆盖式指示器，空状态也保留占位。底部系统内边距放入列表 `contentPadding`。可见行接近末尾时请求下一页，仅前台页面触发自动分页；分页错误期间停止自动触发。

## 视频数据与播放器

视频画面的控制层局部提供 `LocalRippleConfiguration`，统一使用白色水波纹并保留官方透明度与动效，包含进度条和媒体错误页重试按钮；弹出的清晰度与倍速设置面板继续跟随应用主题，进度条本身保留主题色。弹幕 Switch 开启时使用白色轨道、黑色滑块与白色图标，关闭时使用透明轨道、白色描边及滑块与黑色图标；禁用状态显式使用半透明配色，不随浅深色主题变化，保留官方尺寸、状态语义和划线过渡。

`PlaybackSettingsViewModel` 通过独立存储封装异步读取 `kirakira_settings` 的 `playback_in_app_mini_player`、`playback_outside_app_mini_player`（均默认 true）与 `playback_autoplay`（默认 false），使用 SharedPreferences apply 异步落盘。不迁移旧画中画开关。应用级状态经导航传入视频页与播放设置页，不绑定账号。

`VideoPlayer` 接收播放状态、Media3 画面实例和事件回调，不持有 ViewModel。返回按钮和控制栏共享显隐状态与三秒计时器；缓冲、失败、进度交互、设置面板及触摸探索阻止自动隐藏，暂停与结束状态同样支持轻触显隐和三秒自动隐藏。控件使用主题效果动效，退出动画期间禁用交互并清除语义。控制层以黑色渐变遮罩衬托白色图标和文字，播放／暂停按钮居中，使用 60% 不透明度的黑色圆形容器和 Rounded FILL 1 白色图标，加载时同一 64dp 圆形容器内改为 40dp 白色 Material 2 圆角圆形加载器，不提供播放点击动作；底部不再使用主题 Surface。Slider 使用官方 `SliderDefaults.Thumb`（16dp 等宽高，保留默认按压／拖动形变），以两层 `SliderDefaults.Track` 显示缓冲和已播放位置；上层未播放轨道透明，底层只绘制、不添加交互或语义。两层使用相同圆头尺寸，保留官方定位手势与无障碍语义。`PlaybackViewModel` 在 Media3 事件及现有 500ms 进度轮询中读取 `bufferedPosition`，按有效时长限制范围；未知时长与播放器释放时显示零缓冲，不持久化缓冲位置。

`data/content/ContentRepository` 将私有 serialization DTO 映射为 `VideoSummary`、`VideoDetail`、`PublicProfile`、`VideoComment` 和 `DanmakuEntry`，UI 不持有 Cookie 或 token。资料映射拒绝非正 UID，以及响应中 UID 与目标不一致的结果，避免将异常资料归给目标用户。首页、视频、资料分别由 ViewModel 管理；视频与资料的 ViewModel 绑定 Navigation 3 条目。`VideoRoute(videoId)` 和 `ProfileRoute(uid)` 使用真实 ID，旧无 ID 的演示路由与已移除的加载动画演示页路由在恢复时移除；旧路由类型仅保留用于反序列化兼容。状态变更通过 SessionState.revision 取消旧账号工作；请求前取得账号快照、返回时再次核对 revision，旧响应不覆盖新页面。启动时内容加载等待本地会话恢复及 `INITIALIZE` 账号校验结束（含失败），期间手动刷新也不提前请求，避免本地恢复和校验完成两次发布 revision 导致列表清空重载、加载指示器闪回；正常账号切换仍取消并清空旧账号内容。明确 401 只清除发起请求账号的凭据，其他业务拒绝不会自动清空会话。

| 接口                                                              | 用途                             |
|-----------------------------------------------------------------|--------------------------------|
| GET `video/home`                                                | 首页；无分页参数，目前服务端限制 100 条         |
| GET `video?videoId=`                                            | 详情、分 P、作者、赞踩；登录读取可能由后端更新播放量和历史 |
| GET `history/filter`；POST `history/merge`                        | 账号视频历史与首 P 整数秒进度同步 |
| GET `video/user?uid=`                                           | 作者作品                           |
| GET `user/info?uid=`、`feed/stats?targetUid=`                    | 作者资料和统计                        |
| GET `feed/following/list` / `feed/follower/list`                | 关注／粉丝列表；`targetUid`、`page`、`pageSize=50` |
| POST `feed/following` / `feed/unfollowing`                      | 关注／取消关注                        |
| POST `video/upvote` / `video/downvote`；DELETE 对应路径加 `/cancel`   | 视频赞踩及撤销                        |
| GET `video/comment?videoId=&page=&pageSize=20`                  | 按楼层升序分页评论                      |
| POST `video/comment/emit`                                       | 发布一级评论                         |
| POST `video/comment/upvote` / `downvote`；DELETE 对应路径加 `/cancel` | 评论赞踩及撤销                        |
| GET `video/danmaku?videoId=`；POST `video/danmaku/emit`          | 公共弹幕列表和发布                      |

`userDataBootstrapHint` 从登录或 `/user/self` 获取，仅放入加密 StoredAccount 的可选字段，旧 v1 账号库兼容；仅首页过滤请求发送 `uid`、`uuid` 和 `user-data-bootstrap-hint`，缺失时按游客读取。普通鉴权使用账号 token 快照，媒体与图片传输独立且无 Cookie。评论数量字段在空结果时可能省略，游客视频详情可能缺失赞踩计数，UI 显示未知而不是编造零。API 响应继续保留 1 MiB 上限；过大或非法响应显示错误，不无限读取。评论发布少于 20,000 字符；弹幕颜色使用服务端实际要求的不带井号的 `FFFFFF`，默认 `medium`、`rtl`、`enableRainbow=false`，时间单位为秒。

图片地址由 `core/image/DeliveryImage.kt` 统一解析。Cloudflare ID 追加到 Apple 端同款生产地址 `https://kirafile.com/cdn-cgi/imagedelivery/Gyz90amG54C4b_dtJiRpYg/`，缩略图使用宽度变体，全屏和导出使用 `f=auto`；兼容完整合法 HTTPS URL。图片配置本轮固定为生产分发，不随 API 地址自动猜测测试环境。

播放器使用 Media3 ExoPlayer、DASH/HLS、OkHttp data source、Compose `ContentFrame` 和 MediaSession。Media3 的画面组件封装了平台 Surface 的互操作，按钮和进度条采用项目 Material 3 控件，不使用 XML PlayerView。独立媒体客户端拒绝 HTTP 和 URL 凭据，清单及分片均不携带账号会话。导航宿主持有共享播放会话，SavedState 保留视频 ID、分 P 索引、播放毫秒数与禁止恢复自动播放的标记；进程重建时暂停，不恢复小窗。新页面在设置已加载、路由处于前台且有可播放内容时，可按设置自动准备媒体一次；手动操作、账号变化或离开页面会消耗自动播放资格。公开媒体读取允许播放器的标准恢复行为，API 写请求仍不自动重放。

普通播放、应用内小窗和系统画中画统一复用 [`PlayerContentFrame`](../app/src/main/java/moe/kirakira/feature/player/PlayerContentFrame.kt)。它保留 Media3 默认的 `SurfaceView`、`ContentScale.Fit` 和遮挡层，在具有明确尺寸的容器上使用 `clipToBounds()`。Android 11 的 `SurfaceView.clearSurfaceViewPort()` 使用 `Canvas.drawColor(..., PorterDuff.Mode.CLEAR)` 清空当前 Canvas 裁剪区域，Compose `AndroidView` 默认不按布局边界裁剪，可能将先前绘制的页面底色一并清空；Android 12 起改为按视图宽高执行 `punchHole`。外层矩形裁剪将清空范围限制在视频视口内，沿用默认合成策略，不增加 Offscreen 图层，也不裁剪播放控件或页面其他阴影。

播放器控制层的显隐独立于控件业务可用状态：`AnimatedVisibility` 使用主题淡入淡出动效，隐藏时按钮、开关与进度条保持原有配色，退出完成后移出组合；`enabled` 仅表达媒体、轨道或页面等实际可用条件。画面显隐点击由播放器与控件的共同父容器处理，接收未被按钮、进度拖动或滚动消费的轻触，避免全屏滚动控件布局挡住独立背景点击层。开始隐藏时在控制层上方放置独立的画面点击层，点击只恢复控制层并重置计时，不触发底下控件。退出期间清除控制层语义、阻止焦点进入并释放该层已有焦点，操作回调再次检查显隐状态，避免未结束的交互提交操作。暂停与播放结束不再强制显示控件，均支持轻触显隐与三秒自动隐藏；缓冲、错误、进度拖动、设置面板和 TalkBack 触摸探索期间保持显示。

覆盖层遮罩在上下边缘分别渐隐，中部透明；控件默认使用白色，已播放轨道及非 1× 的倍速入口沿用主题强调色。返回与全屏入口使用官方 FilledIconButton；图标容器与画质／倍速按钮均使用 60% 不透明度黑色，禁用时为 30%，不添加阴影。`PlayerSettingsButtons` 使用半透明黑色背景的官方 TextButton 与同一小号尺寸对应的形状、内边距、文字及图标 API，外围按钮统一为官方小号 40dp 容器高度，图标统一使用 IconButtonDefaults.smallIconSize 的 24dp 尺寸，与标准 TopBar 一致；触摸目标继续由官方组件保证。倍速入口在 1× 时使用圆形 FilledIconButton，不显示数值；非 1× 时使用与画质等高的图标文字按钮并改为主题 primary。AnimatedContent 以实际显示的倍率字符串（1× 对应空串）为目标，所有倍率变化都触发内容淡入／淡出和容器尺寸过渡，尺寸使用主题 fastSpatialSpec 弹簧，颜色与透明度使用 fastEffectsSpec；因此 1.5× 到 2× 等非默认倍率间也有动画，退出内容保留原倍率，不闪出 1×。首次显示直接采用当前状态，遵循系统动画时长设置。顶部使用透明官方 TopAppBar，将返回放在 navigationIcon、画质与倍速放在 actions，复用默认栏高、内容边距和垂直对齐；actions 内部通过 FlowRow 换行，底部时间沿用主题 labelMedium 字体。私有 `PlayerControlsLayout` 使用 SubcomposeLayout 先测量顶部、时间、弹幕／全屏操作与 36dp 高度进度布局区，将 64dp 播放容器定位到视频画面的几何中心，图标与缓冲指示器均为 48dp；全屏时补偿不对称 safeDrawing Insets 对布局中心的偏移。根据实测控件矩形检查居中按钮是否重叠，仅在发生重叠或越界时将播放／缓冲控件以 48dp 容器排入底部操作区。底部按实测宽度换行，极短窗口允许整个控件布局纵向滚动，避免控件互相覆盖。顶部位置由官方 TopAppBar 管理，底部采用进度在上、操作在下的两行布局：36dp 进度布局区紧接 48dp 最小高度的操作行，不叠加 TopBar 行高的额外留白；操作行距播放器安全视口底边保留 4dp 留白；4dp 轨道与 16dp 手柄在各自内容插槽中同步向下偏移 8dp，使可见内容靠近操作行，36dp 进度布局区与下方操作行的测量及点击区域仍按顺序排列，不通过重叠布局缩短间距。中央按钮碰撞检查使用包含该视觉偏移的进度轨道／16dp 手柄实际可见高度，以及底部各操作的实测矩形；全屏操作尾部为 4dp，保持与顶栏最右侧图标按钮同列，组内间隔为 8dp。时间起点为 16dp，进度条扣除官方手柄半径后设置外边距，使轨道端点与 16dp 内容边界对齐；弹幕 Switch 保留官方尺寸和状态动效，轨道在开关两种状态下均为 60% 不透明度黑色并带半透明白色描边，开启时使用白色滑块与黑色图标，关闭时滑块降低不透明度并保留划线提示，禁用状态同步降低对比度。全屏由控件层避让 safeDrawing Insets，顶栏的 windowInsets 为零以免重复避让，遮罩仍覆盖整个画面。播放形变状态、弹幕划线状态和进度拖动状态保留在自适应布局之外，重排不重播动效或清空拖动预览；错误态单独预留顶部返回与全屏工具栏，继续使用统一 MEDIA 错误组件。

播放／暂停图标使用 `AnimatedPlaybackIcon` 的路径形变，两种切换均顺时针旋转 90°，采用主题的 Material 3 Expressive `fastSpatialSpec` 弹簧，与划线图标共用动效曲线，不做透明度切换。路径插值与旋转保留超过 1 的弹簧进度，使形变和旋转略微超过目标后回弹。目标路径反向补偿 90°，结束后仍为正向 Material Symbols Rounded 图标，并归一化内部坐标。连续切换从当前可见轮廓继续向最新状态顺时针形变，不排队或倒放。动画状态保留在加载与控制层显隐分支之外，首次显示直接采用当前播放状态，恢复控制层不重播；使用 Compose 动画时钟遵循系统动画时长设置。绘制缓存复用，两种尺寸模式共用组件，颜色及无障碍描述继承原按钮。 图标及操作描述由 `showPauseIcon` 驱动：Media3 `playWhenReady` 为真且未结束、未报错时显示暂停图标，缓冲不会使其切换为播放图标；暂停、播放结束和释放后显示播放图标。`playing` 继续表达 `isPlaying`，用于原有控制层及画中画行为。图标随目标状态变化播放动画，不再使用点击计数或加载结束补播逻辑；Material 2 圆角圆形加载器仍用于缓冲展示，其间图标动画状态在外层继续更新。

画质选项由 `PlaybackViewModel` 在 `EVENT_TRACKS_CHANGED` 中读取 `currentTracks`，仅包含解码支持且高度有效的视频轨道，同高度去重并降序排列；同高度候选优先当前视频组，再按码率选择。画质领域选项同时携带该候选轨道的高度与有效码率，确保显示码率与手动选择一致；UI 将 bit/s 除以 1000 并四舍五入，以 Kbps 显示在行尾，缺失或非正数时不显示。手动模式通过 `TrackSelectionOverride` 覆盖视频轨道，自动模式仅清理视频覆盖，不改音轨和播放位置。每次准备新媒体重新匹配首选高度；未匹配到时显示自动而不改持久偏好。实际清晰度从 `videoFormat` 获取，并随进度轮询更新，避免将自适应组的多个选中轨道误认为当前画质；释放时清空轨道状态。

`PlaybackSettings` 增加 `autoQuality` 和 `preferredVideoHeight`，由既有应用级设置存储保存为 `playback_auto_quality` 与 `playback_video_height`，默认自动且没有首选高度；UI 通过导航宿主回调更新。倍率、连续调速、保持音调由导航宿主 `PlaybackViewModel` 管理，以非敏感标量存入 SavedState；重建播放器时应用 `PlaybackParameters(speed, pitch)`，保持音调时 pitch 为 1，否则为 speed。无级值限制在 0.25–4 并保留两位小数，有级模式吸附既定倍率。

`PlayerSettingsSheet` 使用官方 `ModalBottomSheet`，宽度上限 640dp，只允许展开和隐藏状态；内容滚动并承载底部安全内边距。画质单选及倍速开关使用官方分段列表；「连续调速」与「保持音调」沿用设置页普通开关规则，以 onClick 重载切换状态并提供 Switch 角色与 toggleableState 语义，尾部 Switch 不独立处理点击，整行背景和圆角不随选中状态变化。倍速滑杆以 log2 映射 -2–2，默认 1× 位于中心，并为无障碍有级调整提供相邻倍率操作。面板局部状态参与控制栏显隐计时，关闭后重新计时；全屏、画中画、失去活动状态或播放错误时关闭，不持久化面板打开状态。

`PlaybackSpeedGauge(speed, playing, modifier)` 使用 Compose Canvas 绘制刻度与指针、Text 显示倍率读数，只消费现有 `PlayerUiState`，不持有播放器或修改业务状态。`playing` 来自 Media3 `isPlaying`，实际倍率为 `if (playing) speed else 0f`，不使用缓冲期间仍为真的 `showPauseIcon`。正倍率以 `180° + 180° × (log2(speed) + 2) / 4` 映射 0.25×–4×，1× 为 270°；零刻度单独设在 150°，为零刻度与 0.25× 之间保留足够弧长，不计算 `log2(0)`。`animateFloatAsState` 使用 300ms 的 `tween`，复用共享 `rememberEmphasizedEasing()` 的 Material 3 emphasized 无回弹曲线，不使用弹簧；首次直接采用当前角度，快速切换从当前动画位置继续，绘制角度限制在 150°–360°，动效遵循系统动画设置。指针与大刻度复用 `primary`，码表和滑杆直接使用 `SliderDefaults.colors()` 默认颜色：活动轨道与非活动圆点为 `primary`，非活动轨道与活动圆点为 `secondaryContainer`；经典强调色在主题层生成带强调色调的次要容器；刻度文字为 `onSurfaceVariant`。布局最大宽度 320dp，表盘几何以宽度／1.7 为基准高度，字体实测尺寸参与半径与标签位置计算；标签与弧线外缘留出 8dp 间隔，先保留 0、0.25、1、4 标签，仅在不重叠时显示 0.5、2。读数靠近轴心下方，以主题 `headlineLarge`、Medium 字重和 `onSurface` 显示；按 `0.25×`、`1.25×`、`3.99×` 等宽读数预留固定避让空间，避免倍率变化推动滑杆，暂停指针至少距读数 24dp，并根据固定字体高度增加布局高度，保持下方滑杆位置与读数不重叠。中英文码表无障碍描述报告实际倍率，Text 与滑杆表达设定倍率，不受指针插值或归零影响。

码表指针按用户提供的设计参考采用细尖、圆底的渐窄轮廓，以缓存的闭合路径绘制，中心端与尖端宽度比例为 5:1，轴心至尖端的长度为轨道中心半径的 68%；尖端保留小圆角，中心端为一体半圆，不叠加独立圆轴。中心端半宽限制在 4dp–8dp，避免窄屏或大字体下过细。外弧为 8dp 宽的填充环形路径，断口使用 2dp 圆角，避免分段形成独立胶囊。0、0.25、0.5、1、2、4 为大刻度，以 2dp 宽、12dp 长的圆头径向短杆绘制，统一使用主色。轨道按大刻度分段，根据刻度半宽和 3dp 间隔计算角度退让；路径圆角随短弧长度收缩，极短区间空间不足时省略弧段及圆点，避免重叠。每段弧线中心放置一个小圆点，不按不均匀的快捷倍率分布，尺寸复用 `SliderDefaults.TickSize`，活动圆点使用 `activeTickColor`，其余使用 `inactiveTickColor`。表盘半径扣除轨道半宽，为外缘和标签保留空间；文字仅显示倍率数字，单位由读数表达。设计理由见 [Material Symbols](../third_party/material-symbols/README.md#播放器控制图标)。

`AppNavHost` 持有共享 `PlaybackViewModel`，视频页、小窗与系统画中画使用同一 ExoPlayer／MediaSession。视频页只在活动且匹配当前视频时绑定 `PlayerContentFrame`；小窗位于导航内容上层，系统画中画从小窗进入时切换为全窗口视频。同一时刻仅一个画面绑定。导航完成后根据 `showPauseIcon`（含缓冲待播放）决定是否保留应用内小窗；预测性返回取消不修改模式，返回全屏先退出全屏。回到视频页重新读取详情时，同分 P 的媒体 URL 更新不打断当前播放。关闭小窗、账号修订变化和普通后台路径释放会话；旋转保留 ViewModel，进程恢复只读取非敏感标量并保持暂停，不保存 URL 或凭据。

视频页的播放器固定在 `VideoPlayerViewport` 的同一个组合位置，仅在嵌入布局与满窗布局之间按实际窗口测量结果切换，不对尺寸、位置或详情透明度施加全屏动画。视频输出保持不透明，继续使用同一个 `SurfaceView` 与播放会话。`VideoScreen` 用嵌入播放器高度占位，详情始终保留组合中的 Tab、列表位置与草稿；满窗播放器覆盖其上时通过 `drawWithContent` 停止详情与栏面阴影绘制，画中画同样处理，不使用透明度转场。详情不可交互时清除语义、拦截触摸并禁止焦点进入，关闭评论跳页与弹幕样式弹层，并暂停隐藏评论的相邻页加载。播放器占位区域与 Tab 共用的顶部背景及底边阴影保持原有布局。

全屏入口通过同一 `requestFullscreen` 回调立即同步页面目标与 Activity 的方向、系统栏请求，返回按钮与系统返回也由该入口立即退出。进入时复制当前嵌入播放器的宽高和 Insets，等待横屏期间固定这些值，避免系统栏隐藏或键盘收起使画面提前位移；满窗条件同时确认 Configuration 已横屏与实际布局宽大于高，避免键盘压缩竖屏视口或 Configuration 先于窗口尺寸更新时误判。横屏窗口首次布局时直接满窗，已横屏时也直接切换。多窗口及 Android 16+ 最小宽度达到 600dp 的大屏不等待方向变化，按当前窗口全屏。退出立即恢复普通布局与原方向策略。快速切换始终使用最新目标，没有动画完成回调或固定延时；页面停用与出栈清理窗口状态，画中画暂时解除方向请求并直接使用全窗口视口，展开回应用按原目标重新请求全屏。

`MainActivity` 复用状态栏与导航栏的两份 `SystemBarStyle.auto`，检测回调执行时读取当前启动覆盖层、页面、明暗模式与全屏状态。AndroidX 会保存首次传入的样式，并在视图收到配置变化时重新应用；回调不捕获启动时的固定值，避免旋转后将视频页黑底上的白色图标覆盖为黑色。启动覆盖层优先使用自己的明暗模式，视频页与图片查看器使用白色状态栏图标，图片查看器与视频全屏使用白色导航栏图标，其他情况跟随当前应用主题。Activity 配置变化时先立即更新，再通过 `decorView.post` 在视图配置分发结束后恢复最新的系统栏显示、交互行为与关闭导航栏对比度强制遮罩的设置；待执行恢复只保留一份，始终读取最新目标，销毁 Activity 时移除，不使用固定延时。

全屏转场交由 Android 默认窗口旋转处理，系统关闭动画时同样按实际窗口切换，不在应用中模拟旋转轨迹或修改旋转时长。此选择保留 [Media3 推荐优先使用的 SurfaceView](https://developer.android.com/media/media3/ui/surface)，使用系统默认的 [ROTATION_ANIMATION_ROTATE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#ROTATION_ANIMATION_ROTATE) 行为；具体效果取决于系统与设备实现。Compose [共享元素转场](https://developer.android.com/develop/ui/compose/animation/shared-elements#current-limitations)目前不支持 View／Compose 互操作，不能直接用于含 `AndroidView` 的视频输出。下方内容保持状态，不要求在窗口旋转时固定于原竖屏坐标。

`PlaybackHost` 统一管理 Activity 生命周期、屏幕常亮、系统画中画资格和来源矩形；小窗位置以可用区域的横纵比例保存，拖动结束贴左右边缘，布局用 safeDrawing 与 IME Insets 并扣除实测主界面底栏高度。MainActivity 负责平台画中画桥接和系统栏，Android 12+ 使用 `setAutoEnterEnabled`，Android 8–11 在 `onUserLeaveHint` 检查应用外开关与播放资格；比例限制在系统支持区间。不新增悬浮窗权限、前台服务或后台音频播放。全屏使用 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出恢复此前方向策略；进入系统画中画暂时解除方向请求，展开后恢复全屏。播放器弹幕开关继续使用官方 Switch 和 Cerasus 图标，暂停时保留，手动画中画按钮已移除。

应用内小窗的控件显隐与交互计数使用局部 `remember` 状态，每次小窗出现时默认显示；`LaunchedEffect` 在 3 秒无操作后隐藏，按钮按压与拖动期间暂停计时，操作结束后重新计时。自动隐藏与点击隐藏使用同一个更新入口，同步修改显隐状态与交互计数。缓冲、首次加载及其背景退场、TalkBack 触摸探索开启时强制显示，普通暂停不延长显示时间；触摸探索监听与视频页复用。单击画面切换显隐，左上角展开按钮调用原有 `onRestore`，中央播放按钮沿用播放图标动画和 24dp／2dp 加载器，右上角关闭沿用会话释放逻辑。三处按钮使用白色平面官方 `IconButton`，正常槽位为 48dp，窄窗口限制到宽度的三分之一，图标按可用槽位缩小；整窗 50% 黑色遮罩与按钮持续保留在组合中，由宿主的 `animateFloatAsState` 驱动共享 `graphicsLayer.alpha`，显示使用主题默认效果动效，隐藏保留主题快速效果动效。计时结束或点击隐藏只改变透明度目标，点击拦截层的显隐不重建淡出状态；淡出过程中再次显示，从当前透明度继续动画，遵循系统动画时长设置。隐藏时清理控件焦点与语义，用置顶点击层拦截退场期间的点击，按钮回调也校验显隐状态，避免显示控件的点击触发播放、展开或关闭。显隐不进入 ViewModel、SavedState 或设置，不改变系统画中画的纯视频画面。

### 播放器首次加载背景

封面等待使用普通 Spinner：`VideoPage` 通过 `PlayerUiState.artworkPending` 表达首次详情尚未返回封面地址，`VideoArtwork` 通过可选 `onLoadingChange` 回调上报 Coil 的加载、成功和失败；播放器按图片地址持有局部加载状态。只有未创建媒体播放器时使用封面等待，在中央播放按钮内复用 40dp 白色 `IndeterminateCircularProgressIndicator`，保留原有黑色圆形容器与自适应位置；成功或失败后立即恢复播放图标，不绘制或淡出几何背景，无封面不保持加载状态。封面等待期间保持控件可见，按钮仍按媒体可用性启用，保留播放操作的无障碍描述。封面等待不写入分 P 就绪记录、不提前加载媒体；点击播放后的媒体首次加载继续使用几何背景。首页、历史等其他封面调用保留原样，已有详情刷新不再设置 `artworkPending`。

`PlaybackViewModel` 在内存中按分 P ID 保存首次就绪记录，`initialLoading` 经 `PlayerUiState` 传给页面；不进入 SavedState 或设置。仅准备未就绪的分 P 时开启，当前播放器首次进入 Media3 `STATE_READY` 即关闭，即使此时处于暂停状态也完成记录。内部重建播放器、切换分 P 和重试保留记录；切换视频、账号、关闭小窗及离页且未转入小窗时清空。首次失败不记录就绪，重试继续显示背景；已就绪媒体失败后重试只使用普通缓冲加载器。播放器赋给会话后再调用 `prepare`，监听器只接受当前实例的就绪和错误事件。

`PlayerInitialLoadingOverlay` 在视频与弹幕之上、控制层之下绘制 Cerasus `LogoCover` 的几何动画，背景为主题 `surface`、图形为 `primary`，条带透明度为 30%。Compose 无限动画使用 96 秒公共周期，分别计算上游各图形的 6／8／12／16／32 秒线性条带运动、4 秒错峰加号翻转和三角形移动、2 秒交替闪烁及条纹缩放、4 秒交替圆环缩放、16 秒条纹圆旋转，保留 CSS 贝塞尔曲线与描边三角形的负延迟。交替动画的反向阶段反转缓动曲线，保持 CSS alternate 行为。几何参数按 dp 转换，窄小视口按较短边等比缩小，所有路径与裁剪轮廓复用缓存。宽度达到 640dp 时使用现有 KIRAKIRA 矢量品牌资源，右上角避让全屏系统栏；不引入播放器名称、YOZORA 图标或字体依赖。

首次开启直接显示背景，首次就绪用 CSS 默认 ease 曲线在 500ms 内淡出，不延迟播放；错误、释放或离开当前宿主时立即撤下。首屏及退场期间保持控件可见，隐藏画质／倍速入口；首次就绪前禁用进度拖动，返回和全屏继续可用。小窗共享同一首次加载状态及几何背景，省略品牌资源，保持原有小尺寸加载器与操作；系统画中画不绘制首屏。动画使用 Compose 时钟遵循系统时长倍率，系统关闭动画时绘制固定几何帧并立即完成退场。来源与许可见 [Cerasus 资源说明](../third_party/cerasus-icons/README.md#播放器首次加载动画)。

### 历史记录与续播

历史页使用官方 `LazyColumn` 按日期标题与记录逐项懒加载，保留日期及视频的稳定 key，并以 `date`、`history`、`status` 区分 `contentType`。记录使用透明背景的官方 `ListItem`，通过 `Modifier.clickable(role = Role.Button)` 保留整行点击、涟漪及按钮语义，以官方行内留白分隔，不添加卡片圆角、阴影或分隔线。列表最大宽度为 840dp，水平 `contentPadding` 仅包含系统 Insets，记录使用 `ListItem` 的官方 16dp 水平内边距，日期标题与状态区域单独对齐到 16dp；顶部与底部安全区域继续放入滚动内容。封面与文字共用内容插槽，保留 16:9 封面、现有封面裁剪及进度条；有效宽度除以字体缩放小于 300dp 时改为纵向布局，避免大字体挤压文字。

`HistoryHostViewModel` 在导航宿主中持有唯一的 `data/history/HistoryRepository`，使用宿主 `viewModelScope` 管理内存缓存、共享读取及串行进度队列；历史页的 `HistoryViewModel` 绑定 `HistoryRoute`，只持有搜索和刷新任务。仓库按会话 revision 清空缓存并取消旧账号读写，请求前后核对账号快照，明确 401 交由统一会话失效处理，游客不调用历史接口。DTO 留在数据层，领域记录使用 `HistoryEntry`：Rosales 的 `duration` 为秒、`lastUpdateDateTime` 为毫秒时间戳、`anchor` 为秒数字符串，在历史边界将时长与有效锚点转换为毫秒；负数、非有限或溢出锚点不参与展示与续播。

历史读取不传 `videoTitle`，标题与作者过滤在本地完成；接口无分页或删除能力。首次视频详情与续播准备并发执行，不等待历史接口才请求详情。续播准备等待已排队的进度提交及 `loadIfNeeded`，总等待上限为 1500 毫秒；完成时由视频 ViewModel 发布一次续播位置快照，失败或超时按无云端位置处理，不使用迟到的查询结果跳转。自动播放等待该快照，手动播放不受历史等待限制。历史页刷新先等待当时的同步任务，避免从视频返回时读到尚未提交的锚点；刷新进行中新提交成功的记录合并到返回列表，后续刷新重新以云端列表为准，避免内存更新覆盖其他设备的新进度。

`PlaybackViewModel.setContent` 在新播放会话仅在续播准备完成后应用一次首 P 位置；已有播放器、保存的分 P 和本地位置优先，手动播放、切换分 P 或跳转后不再应用迟到的续播位置，手动跳转同时取消待执行的云端时长校验。Media3 提供实际时长后再检查距离结尾的阈值。进度采样附着在共享播放器，而不是视频页面，覆盖小窗、全屏和系统画中画；生命周期进入后台时额外 flush。提交只保存 `uuid`、`category=video`、字符串视频 ID 与向下取整的秒数，不扩展 Apple 共用的锚点协议，因此其他 P 不覆盖首 P 秒数。

同步队列串行执行，每个尚未发送的视频只保留最新进度。成功提交更新缓存中的位置、时间、封面与实际媒体时长；失败不输出服务端原文、敏感日志或周期性 Snackbar，不自动重放。队列与缓存不落盘，进程结束时的提交仅尽力完成；采样与续播阈值、页面交互见[历史记录功能](features.md#历史记录)。

## 主题实现

### 成功语义色

主题层的 [ThemeSemanticColors](../app/src/main/java/moe/kirakira/ui/theme/ThemeSemanticColors.kt) 提供不可变的 `success` 与 `onSuccess`，通过 `MaterialTheme.semanticColors` 读取，供成功／完成状态复用，不占用 Material 3 的既有颜色角色。`KIRAKIRATheme` 根据自身 `darkTheme` 参数通过 `CompositionLocal` 提供配色：浅色固定使用 `#008577`，深色使用 `#00594F`，配套前景均为白色，不引用个性色预设，不受强调色或壁纸取色影响。

安全页顶部卡片使用成功色实色填充，绿色仅作为该卡片的视觉识别；真实保护状态仍由文案及盾牌／警示图标表达。邀请码顶部卡片使用 `primary` 与 `onPrimary` 实色配对，默认呈品牌粉色并随主题强调色变化。两者沿用关于页的大圆角与实色视觉语言，图标底座使用低透明度前景色；邀请码生成按钮在浅深主题的可用状态均采用白色容器与 `primary` 文字、图标，直接使用官方 `Button` 并设置 `elevation = null`，所有交互状态均无阴影，保留官方 Expressive 尺寸和按压形变，禁用配色沿用统计块的低透明度前景色。加载指示器使用卡片前景色。

### 配色生成

所有主题颜色固定使用经典强调色，默认原色为 `KIRAKIRAPink`（`#F06E8E`）。[MaterialKolor](https://github.com/jordond/MaterialKolor) 的纯函数 `dynamicColorScheme` 仅用于生成 `Monochrome / SPEC_2021` 灰阶基础，HCT 工具用于生成强调色的容器、反色与固定色角色。该库维护 Google Material Color Utilities 的 Kotlin 移植与 Compose 适配，许可证为 MIT，底层 Material Color Utilities 为 Apache-2.0。

`KIRAKIRATheme`、预设／壁纸色板与自定义选色器统一使用 `rememberSeedColorScheme(seedColor, darkTheme)`。主题与设置不再接收、保存配色算法，经典方案沿用 `MaterialExpressiveTheme` 的形状、排版和动效。

浅色与深色灰阶基础各在进程内惰性生成一次，完整配色按不透明 ARGB 与明暗模式存入容量为 32 的 `LruCache`。多个预览和重复进入页面复用结果，算法与颜色角色保持一致；缓存只持有颜色值和 `ColorScheme`，不持有 Activity、不写入存储，进程退出后释放。

[WallpaperAccentColor](../app/src/main/java/moe/kirakira/ui/theme/WallpaperAccentColor.kt) 通过 Compose `colorResource` 读取 Android 12+ 的公开资源 `android.R.color.system_accent1_500`，应用主题与壁纸色板共用此入口。资源读取跟随 Compose 的系统资源配置更新，不缓存壁纸色快照；应用明暗模式不改变取色阶。`KIRAKIRATheme` 保留的 `dynamicColor` 参数仅选择壁纸强调色来源，默认关闭；低版本返回空值，主题回退到保存的手动原色。

[ClassicAccentColorScheme](../app/src/main/java/moe/kirakira/ui/theme/ClassicAccentColorScheme.kt) 从灰阶角色构建经典强调色，并将 `secondaryContainer` 和 `onSecondaryContainer` 映射到同色主色容器与配套前景。默认 Slider、RangeSlider 的未经过轨道自动采用强调色调，活动圆点采用同一容器色；使用次要容器的选中 Chip 和导航指示器同样着色，不添加组件专用配色接口。`MainBottomBar` 将选中图标与文字映射到 `primary`，覆盖整个按钮的选中胶囊映射到 `primaryContainer`。主题统一通过 `LocalTonalElevationEnabled` 关闭色调高度叠加，`surfaceTint` 与 `surface` 同色，避免直接计算高度色时重新染色。原色保留、`onPrimary` 对比度阈值、组件配色接口与视觉取舍统一见[主题配色约束](../CONTRIBUTING.md#主题配色约束)。

设置主页、各设置子页与开源组件页通过 `ThemeColorDefaults.settingsBackgroundColor()` 使用 `MaterialTheme.colorScheme.surfaceContainer` 页面背景，普通 `SegmentedListItem` 保留官方默认的 `surface` 容器颜色和内容内边距，分组统一为无间隙、无分隔线的连接式样式，选中和功能总开关状态沿用官方配色。页面背景不再跟随顶栏颜色，避免经典强调色下页面与列表同为 `surface` 而融为一体；经典方案顶栏继续使用浅色纯白／深色深灰的 `surface`。头像裁剪工具保留原有 `surface` 背景。

[ConnectedListGroup](../app/src/main/java/moe/kirakira/ui/components/ConnectedListGroup.kt) 提供满宽、零行间距的普通分组布局和固定 1dp 整组阴影，不额外绘制背景或添加内边距，默认不裁剪内容；`SettingsSection` 保留标题间距并在内部复用它，独立条目在调用处显式包裹单项分组。共享 `connectedListItemShapes(index, count)` 以零圆角 `RoundedCornerShape` 为基础，通过官方 `ListItemDefaults.segmentedShapes` 获取首项顶部、末项底部及单项的主题圆角，中间连接边为直角；选中、按压、聚焦、悬停与拖动形状均沿用基础形状，保留官方颜色反馈与无障碍语义。菜单、设置、账号、标签及播放器面板共用此入口。阴影属于容器或列表宿主，条目不再通过组合局部上下文跳过投影，也不绘制和拼接行级阴影。账号侧滑组继续通过 `clipContent` 按整体外轮廓裁剪，外侧阴影及删除按钮的横向间距保持不变。

[ConnectedLazyColumn](../app/src/main/java/moe/kirakira/ui/components/ConnectedLazyColumn.kt) 在官方 `LazyColumn` 外提供分组声明及统一投影，接收 `LazyListState`、`modifier`、`contentPadding` 和内容 DSL，固定零行间距与正向垂直布局。DSL 的普通 `item`、`items` 与 `connectedItemsIndexed(groupKey, items, key, contentType, itemContent)` 在构建内容时一起生成不可变分组索引范围，保留原有条目 key 和 contentType；groupKey 在同一列表内必须唯一且跨刷新稳定，空组不占条目。连接行必须满宽，不加外部纵向 padding、粘性标题或位移动画；每个懒列表条目只承载一行。宿主在绘制阶段读取 `layoutInfo.visibleItemsInfo`，按索引范围匹配可见分组，每组复用一个只负责投影的官方 `GraphicsLayer`，使用完整主题轮廓及固定 1dp `shadowElevation`，不额外绘制背景。首尾可见时使用行的实测位置；不可见的端点延伸到视口外一个视口高度，不估算完整组高、不测量屏幕外行，避免在视口边缘产生虚假的顶部或底部投影。位置换算包含顶部内容内边距，横向内边距按 RTL 解析，投影裁剪至宿主视口；滚动只更新绘制，离开视口的分组及销毁的宿主释放图层。保留连续整组投影的视觉目标，不保证与旧逐行投影逐像素一致。

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

浅色方案在主题生成层将普通页面的 `background` 与 `surface` 设为纯白，分组列表页面使用的 `surfaceContainer` 保留 `#F5F5F5`，使白色 `SegmentedListItem` 与页面背景保持层次。深色方案保留原有灰阶角色；所有颜色来源共用这些规则。页面直接引用语义颜色，不再通过配色模式判断切换样式。

管理、安全与隐私设置页的页面级加载分支位于滚动列表或列之外，通过 `Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.Center)` 在顶栏与底部系统栏之间居中，继续复用 `IndeterminateCircularProgressIndicator`。标签页仅在标签与视频均无数据且同时加载时使用这一全页分支，部分数据就绪后的独立状态保留列表内呈现；不改变请求、错误重试或下拉刷新条件。

共享 [ContentPullToRefresh](../app/src/main/java/moe/kirakira/ui/components/ContentPullToRefresh.kt) 保留官方 `PullToRefreshBox` 的状态、手势与阈值，通过 `PullToRefreshDefaults.IndicatorBox` 绘制白色圆形容器，浅深模式保持一致。容器使用官方内置的 `PullToRefreshDefaults.Elevation` 高度阴影，与容器共享位移和顶部裁剪，未拉动且不刷新时由官方宿主关闭投影。外层不额外添加阴影 Modifier，避免容器隐藏后顶部仍残留圆形阴影；这一约束同样适用于没有实时模糊的 Android 8.1–11。内部圆弧使用 `primary`、20dp 外径、2.5dp 线宽、透明轨道与 `StrokeCap.Round`。

拉动反馈依据 [Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html) 和 AndroidX Material `PullRefreshIndicator` 的计算方式：跳过前 40% 拉动距离，后续逐步增长至最大 80% 圆弧；超出阈值时使用有上限的非线性张力，使旋转逐渐减缓，而不是在阈值处填满整圈。未达阈值时透明度为 30%，达阈值后以 300ms 线性补间增至 100%，拖回时反向恢复。箭头基于官方 Rounded `chevron_right` 的中心线绘制，沿圆弧末端切线旋转并按拉动进度缩放路径长度，与圆弧共用固定 2.5dp 的圆角描边，避免缩放填充图标时箭头变细；不绘制旧版实心三角箭头。仅该绘制区域固定 LTR，确保 RTL 下仍顺时针旋转。

拉动阶段的圆弧与箭头先以不透明颜色绘制到同一 `Canvas.saveLayer`，合成后统一应用阈值透明度，避免交叠处变深。局部图层边界向外扩展，容纳圆弧外的箭头，不包含白色容器及阴影。

刷新阶段复用 `IndeterminateCircularProgressIndicator` 的官方 Material 2 动画，拉动与旋转图形以 Material 2 的 100ms `Crossfade` 切换，不套用 Expressive 弹簧。刷新完成后，在官方状态回到隐藏位置之前保留旋转图形，用回收距离驱动整个指示器缩小、淡出，避免退出时重新出现拉动箭头。退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度低于 1 时自动创建局部离屏缓冲，裁掉已经位移的容器或外侧阴影。业务刷新状态仍由调用方提供，局部标记仅控制退场显示，不延长请求或重复触发刷新。来源、许可与改动说明见 [Android 刷新指示器说明](../third_party/android-refresh/README.md)。

### 弹层背景

普通弹层在官方组件调用处直接引用 `MaterialTheme.colorScheme`，规则见[贡献指南](../CONTRIBUTING.md#compose-与界面规范)。业务 Sheet 有实际连接列表条目时使用原有的 `surfaceContainerLow`，否则使用 `surface`；深色模式同样使用对应语义颜色，不硬编码白色。判断基于内容状态，不读取懒列表可见条目或探测组件树；空分组不算连接列表条目，保留已有条目的刷新继续使用灰底，不额外添加颜色动画或保存背景偏好。

播放器画质与倍速面板、隐私可见性面板始终包含连接列表，固定使用灰底。标签搜索根据候选是否非空、标签名称根据是否存在非空名称组、屏蔽规则编辑根据是否有用户预览或非空标签结果选择背景。弹幕样式的 `custom` 状态保留在弹层打开分支、提升到 `ModalBottomSheet` 调用之前：样式页用灰底，自定义颜色编辑用 `surface`；关闭后重新打开仍进入样式页，编辑器草稿和确认／取消逻辑保持原有归属。

普通 `AlertDialog` 与 `DropdownMenu` 显式使用 `surface`。生日选择将同一份 `DatePickerDefaults.colors(containerColor = surface)` 传给 `DatePickerDialog` 和内部 `DatePicker`，确保日历、年份选择和输入模式与外层同色。开源组件通过 `LibraryDefaults.m3VariantColors(sheetSurface = surface)` 配置详情 Sheet，通过 `libraryColors(dialogBackgroundColor = surface)` 配置许可证对话框，保留上游组件与列表页面背景。弹层内列表项、输入框和选中态继续使用原有配色；媒体查看器不适用该规则。

### 选色与持久保存

[自定义选色对话框](../app/src/main/java/moe/kirakira/feature/settings/CustomColorDialog.kt)使用 [colorpicker-compose](https://github.com/skydoves/colorpicker-compose)（Apache-2.0）的 HSV 色盘与亮度滑条，封装在 Material 3 `AlertDialog` 中，补充亮度无障碍调节与 HEX 输入。它只增加 Compose 选色绘制与手势代码，不引入 View 互操作，许可证由 AboutLibraries 收集。

`ThemeColorSettings` 保存 `useSystemColors`、`seedColorArgb`、`customColorArgb` 与 `useCustomColor`，分别持久化为 `theme_system_colors`、`theme_seed_color`、`theme_custom_color` 与 `theme_use_custom_color`。默认关闭壁纸取色与自定义选择，两份原色均为项目粉色，不保存配色算法。读取时仅将非自定义的旧蓝／紫／绿／琥珀／珊瑚预设原色映射至智乃蓝／理世紫／千夜绿／纱路黄／小惠红，并只写回变更后的 `theme_seed_color`；品牌粉色不变。壁纸来源和独立自定义色值保持原样，自定义状态为真时不迁移，即使色值等于旧预设。

色板始终显示，七种 Cerasus 预设之后依次为受支持的壁纸颜色与自定义入口。颜色依次为 `#F06E8E`、`#4581E1`、`#B044B0`、`#46A12F`、`#F98D00`、`#199BB6`、`#DD1818`。选择壁纸仅更新来源标记，保留手动色值和自定义选择状态；色板以当前生效来源互斥标记选中项。自定义色值与当前手动色值独立存储，避免自定义颜色恰好等于预设时错误标记选中项。草稿确认与取消的约束见[贡献指南](../CONTRIBUTING.md#主题配色约束)。

设置主页用单一 `LazyColumn` 按“我”“常规”与账户操作分组懒加载，每组继续复用 `ConnectedListGroup` 的整体阴影与连接形状，保留分组间距和滚动状态。外观页复用 `SettingsScaffold` 的毛玻璃顶栏、640dp 内容上限与滚动内 Insets，同样使用单一 `LazyColumn`，按标题、明暗卡片行、个性色卡片行及动画分组组织带稳定 key 和 contentType 的条目。页面私有的 `AppearanceColorOption` 区分 `ThemePresetColor` 预设、带原色的壁纸选项与自定义入口，避免通过字符串分派不同颜色来源；壁纸原色更新时重新生成选项，保留稳定 key。页面容器仅通过一次 `BoxWithConstraints` 取得可用宽度；明暗／个性色卡片最小宽度为 100dp／156dp，间距 12dp，最小宽度乘以至少为 1 的字体缩放因子后计算列数。`ThemeCardRow` 保留同一行按最长文字内容等高、末行空格占位的排列。分组直接排列卡片，不额外包裹连接列表或背景卡片；自定义颜色对话框由列表外的页面状态管理。

`ThemeSelectionCard` 使用官方可点击 `Card` 管理涟漪、焦点与交互，使用主题 large 形状与 1dp 静止阴影。明暗模式使用 `compact` 布局，选中时整张卡片使用 `primary/onPrimary`，未选中使用 `surface/onSurface`；预览图标通过 `LocalContentColor` 同步继承动画颜色，未选中为 `primary`，选中为 `onPrimary`。角色、壁纸与自定义卡片的预览区保持 `surface` 底色及原始图片／原色图标，仅下方文字区在选中时使用 `primary` 背景，标题和副标题同步使用 `onPrimary`；未选中文字区使用 `surface/onSurface`，副标题使用 `onSurfaceVariant`。1.5dp 描边在透明与完整 `primary` 之间渐变，背景、标题、图标与色标中的勾选沿用主题 Expressive effects 动效。根节点覆盖为单选角色与选中状态，合并主题／角色名称或 HEX 色值，子预览与标签清除独立语义。明暗卡片的图标区域宽高比为 1.6，跟随系统／浅色／深色分别居中显示 40dp 的 `brightness_auto`／`light_mode`／`dark_mode`；标题使用 `labelLarge` 居中排列并允许换行。`ThemeViewModel` 初始值、缺失或无效偏好的回退均为 `ThemeMode.SYSTEM`，有效的 `theme_mode` 偏好按已保存值恢复。

七张角色卡片的图片区域固定为 1:1，继续按 `BiasAlignment(0f, -0.84f)` 靠上裁切。文字区保留 24dp 原色色标、`titleSmall` 主题名与 `bodySmall` 日文角色名；选中时在色标内显示 24dp 的 Rounded `check`，前景使用 `onPrimary`，不在图片上叠加标记或为尾部勾选预留宽度。色标与图标共享固定的 24dp 槽位，垂直居中且不随选中状态改变尺寸。文字区通过 Column weight 填满行等高后的剩余空间，保持底边对齐；标题允许换行和增高，选中变化仅影响绘制。角色图片通过 Coil `AsyncImage` 按卡片约束在后台解码并复用内存缓存；首次加载透出卡片原有底色，禁用额外 crossfade，图片出现不改变布局。角色图片离线打包，来源与转换方式见 [Cerasus 个性色资源](../third_party/cerasus-palettes/README.md)。

壁纸与自定义颜色在七张角色卡片之后共用个性色网格，`ThemeColorSourceCard` 复用 `ThemeSelectionCard` 的方形预览区、文字区与选中样式；预览区居中显示 48dp 的 `wallpaper`／`edit`，按系统强调色／已保存的自定义色着色，文字区保留名称、HEX 色值与原色色标。系统取色不可用时不生成壁纸选项。点击自定义只打开现有对话框，确认才调用 `selectCustomColor`，取消不改变选择。自定义对话框仍复用 `ThemePaletteSwatch` 三色预览。主题状态继续使用 `lifecycle-runtime-compose` 进行生命周期感知收集；页面 Preview 定义覆盖中英文、浅深色、320dp／640dp 宽度及 2 倍字体。

未选中的主题与个性色卡片不绘制描边，仅保留卡片阴影；描边只用于选中项的强调色反馈，避免灰色轮廓干扰阴影。

MaterialKolor 提供灰阶生成、HCT 工具与 Compose 适配代码，不引入 View 组件库；两项配色依赖的实际 APK 增量需通过同构建配置比较，不以依赖包大小代替。

页面顶栏的标题槽通过 `TopAppBarDefaults.topAppBarColors(titleContentColor = MaterialTheme.colorScheme.primary)` 使用当前主题主色；共享 `CollapsibleTopAppBar` 与主界面的公共颜色配置集中设置，独立顶栏在调用处设置。顶栏标题文字显式使用 `FontWeight.SemiBold`（600），保持组件默认字号与行高；使用系统默认字体，由平台负责字重匹配与必要的加粗合成。可折叠标题在展开与折叠时保持相同颜色与字重，背景与导航图标沿用各页面配置。

### 栏面阴影

[ThemeShadows](../app/src/main/java/moe/kirakira/ui/theme/ThemeShadows.kt) 的 `barSurfaceLayer(shape, alpha, shadowElevation)` 统一承载栏面形状、透明度与原生投影，使用官方 `graphicsLayer`，阴影高度通过 `BarShadowElevation` 固定为 4dp。默认矩形、alpha 为 1，胶囊底栏传入与背景一致的圆角形状；阴影配色和强度由平台光照模型决定，不手动映射模糊、扩张或双层不透明度。图层仅按形状裁剪内容，保持默认 `CompositingStrategy.Auto`，不使用阴影蒙版、差集裁剪或阴影版本特判。

原生 [Outline.alpha](https://developer.android.com/reference/android/graphics/Outline#setAlpha(float)) 描述轮廓内内容的不透明程度，[Android 11 绘制实现](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/libs/hwui/pipeline/skia/ReorderBarrierDrawables.cpp#208) 据此选择透明遮挡面的阴影路径。仅降低背景颜色的 alpha、却让承载阴影的图层维持 alpha 为 1，会使半透明栏面透出按不透明遮挡面优化后的阴影内缘。`frostedBarBackground` 在 Android 8.1–11 绘制不透明主题 `surface`，将 0.9 的 alpha 与 elevation 放在同一个背景图层中；Compose 同步图层 alpha 到原生轮廓，保持 90% 不透明度且不重复相乘。Android 12+ 的 Haze 背景图层保持 alpha 为 1，继续使用既有采样配置和同一原生 elevation 机制。

普通顶栏和胶囊底栏通过独立的背景子节点承载透明度、形状与阴影，文字、图标、选中背景和指示器作为独立前景绘制，不随背景变淡。背景使用 `matchParentSize()` 跟随实际栏高且不参与测量，胶囊前景继续由透明 `Surface` 保留内容裁剪与触摸语义。视频页的共享背景涵盖播放器占位区域与 Tab，下沿跟随播放器高度与实测 Tab 高度之和，Tab 上缘不单独投影；实际播放器保持独立绘制。资料页的原生图层承载封面、资料区与 Tab 共用的不透明顶部背景，下沿跟随 Tab 实测位置，避免独立 Tab 的上缘投影落到同一表面的资料区；资料顶栏毛玻璃背景的 `shadowElevation` 保持默认 0dp。尺寸、折叠程度与字体缩放由原有布局决定，不增加额外留白、空投影图层或持久化状态。

浅色与深色模式共用固定的栏面阴影高度，实际投影由平台光照与图层透明度决定。连接列表继续使用固定 1dp 平台 elevation 投影，由普通容器 `shadow` 或懒列表宿主 `GraphicsLayer.shadowElevation` 承载；评论／弹幕输入区保留 `Surface.shadowElevation` 的 4dp 原生高度。刷新阴影仅由官方 `IndicatorBox` 容器承载，跟随容器位移与显示状态，不在外层重复投影。连接列表继续延伸并裁剪分段投影轮廓，账号侧滑组继续通过 `clipContent` 控制内容裁剪。

栏面图层 Modifier 为普通函数，调用处仅使用固定高度；主题、ViewModel 和导航不保存或传递阴影状态，外观页没有关闭入口，也不读取旧阴影偏好。大标题展开或折叠、Tab 切换及吸顶不改变阴影高度；经典强调色仍关闭色调高度叠加。

### 按钮动态阴影

个人主页与视频页复用 [FollowButton](../app/src/main/java/moe/kirakira/ui/components/FollowButton.kt)，内部使用官方 `ToggleButton` 与官方尺寸配套 API。未关注状态显式使用 `primary/onPrimary`，接入共享 `buttonShadow` 的双层投影；已关注状态使用灰色 `surfaceContainerHigh/onSurfaceVariant`，不接入投影，官方 elevation 同时设为 `null`。禁用时保留官方禁用配色与语义，不绘制阴影。选中状态与按压状态通过公开 `Interpolatable` API 及主题 `fastSpatialSpec` 插值，所得轮廓同时传给表面和投影，官方组件的三种形状设为同一当前轮廓以避免二次动画；关注布尔值、提交条件与回调继续由原页面拥有。

[ShadowButtons](../app/src/main/java/moe/kirakira/ui/components/ShadowButtons.kt) 为填充、浅色填充、实心图标按钮及 FAB 封装官方 Material 3 组件；文字、描边及裸图标按钮不使用投影。调用方保留现有颜色、尺寸、形状、内容内边距、语义及点击逻辑，官方高度投影设为零，避免重复叠加。按钮不增加缩放、位移或布局空间，也不保存阴影偏好。投影读取官方 `MinimumInteractiveLeftAlignmentLine` 和 `MinimumInteractiveTopAlignmentLine`，按实际可见轮廓内缩并平移，不把最小触摸目标的留白当作按钮表面；测量及触摸区域保持原样。

`ShadowFloatingActionButton` 默认使用 `CircleShape` 与主题 `primary` 背景，`contentColorFor(containerColor)` 为默认背景匹配 `onPrimary` 前景，与实心按钮配色一致。保留官方 FAB 尺寸及显式覆盖形状、背景和前景的参数；当前规则管理页直接继承默认值，仅显示本地化无障碍描述的 Material Symbols Rounded `add` 加号。

共享交互源驱动投影和形状。普通按钮静止／按压／悬停或聚焦使用 2dp／8dp／4dp 等效高度，FAB 使用 6dp／12dp／8dp；按下 120ms、松开或取消 180ms，无弹跳，系统动画设置由 Compose 处理。禁用时立即移除投影，优先于按压、悬停与聚焦；FAB 通过封装的 `enabled` 参数同时阻止操作并声明禁用语义。传入 Expressive `shapes` 时，默认形状和按压形状通过公开 `Interpolatable` API 与主题 `defaultEffectsSpec` 插值，同一动画形状用于按钮轮廓和投影；不访问 Material 3 内部 API。

双层 `dropShadow` 由 [MaterialColorShadow](../app/src/main/java/moe/kirakira/ui/components/MaterialColorShadow.kt) 统一绘制，在最低支持版本同样绘制彩色投影，几何参数采用 Google 官方 [Material Web 双层 elevation 实现](https://github.com/material-components/material-web/blob/main/elevation/internal/_elevation.scss)，高度映射采用官方 [elevation tokens](https://github.com/material-components/material-web/blob/main/tokens/versions/v0_192/_md-sys-elevation.scss)。下表按「垂直偏移／模糊／扩张」列出官方逻辑像素值，接入时映射为 dp；水平偏移均为零。这里复用官方设计参数，仍由 Compose 绘制，不使用 Android 平台光照模型，也不保证与浏览器逐像素一致。

| Level | 高度 | 环境层 | 主层 |
| --- | --- | --- | --- |
| 0 | 0dp | 0／0／0 | 0／0／0 |
| 1 | 1dp | 1／3／1 | 1／2／0 |
| 2 | 3dp | 2／6／2 | 1／2／0 |
| 3 | 6dp | 4／8／3 | 1／3／0 |
| 4 | 8dp | 6／10／4 | 2／3／0 |
| 5 | 12dp | 8／12／6 | 4／4／0 |

保留项目现有交互高度：2dp、4dp 及动画中间值在相邻官方高度间线性插值，环境层包含官方扩张值，主层不扩张。彩色投影取实际容器色，按钮与 FAB 的环境层／主层不透明度统一固定为 25%／50%，这是相对官方强度的显式例外；静止、悬停、聚焦和按压仅改变范围，不再改变不透明度。RGB 最大与最小通道差不大于 0.02 的中性色使用黑色投影及官方 15%／30%。两类均乘以容器透明度；主题切换与自定义颜色变化实时更新，不统一套用主色，也不绘制额外发光效果。

评论／弹幕发送按钮直接使用共享实心图标按钮，不再由外层带投影的圆形 `Surface` 承载，避免重复投影和外层裁剪。输入框的固定 4dp 平台阴影不变；发送按钮禁用时不绘制阴影，保持原有禁用配色及发送条件。

### 单选框动态阴影

[ShadowRadioButton](../app/src/main/java/moe/kirakira/ui/components/ShadowRadioButton.kt) 保留官方 `RadioButton`，参数与官方组件一致，默认配色来自 `RadioButtonDefaults.colors()`；仅选中且可用时显示投影。静止／悬停或聚焦／按压采用 1dp／2dp／4dp，按压优先于悬停与聚焦；按下 120ms、恢复或取消 180ms，系统动画设置由 Compose 处理，禁用时立即移除投影。取消选中时同样移除投影，不改变官方圆点和颜色动画。

单选框与按钮共用 `materialColorShadow` 的 Material 官方双层几何参数及高度插值。1dp 的环境层「垂直偏移／模糊／扩张」为 1／3／1dp，主层为 1／2／0dp；2dp 与 4dp 在相邻官方高度之间插值。阴影取 `colors.selectedColor`，单选框传入固定 `opacityScale = 0.5f`，将双层强度降为按钮的一半：彩色环境层／主层为 12.5%／25%；RGB 通道差不大于 0.02 的中性色使用黑色及 7.5%／15%，均乘以选中颜色透明度，主题与自定义颜色变化实时更新。按钮沿用默认 `opacityScale = 1f`，单选框减淡不改变按钮阴影或控件自身颜色。

投影背景层使用 `matchParentSize`，不参与控件尺寸测量；居中 20dp 的圆形轮廓对应当前官方内部 `RadioButtonTokens.IconSize`，该 token 未公开，因此在封装内记录尺寸来源。仅对阴影层使用圆形差集裁剪，防止投影染色空心单选框内部；官方组件的布局、最小触摸目标、涟漪与绘制不被裁剪，也不添加背景填充或平台 elevation。更新 Material 3 时需核对该内部图标尺寸。

设置、账号切换、画质和隐私选项统一使用此封装。整行处理点击时，每行通过 `remember` 创建一个 `MutableInteractionSource`，同时传给 `SegmentedListItem` 和 `ShadowRadioButton`，单选框保持 `onClick = null`；按压、悬停与聚焦整行即驱动选中单选框的阴影。`SettingsItem` 的可选 `interactionSource` 参数由 `SettingsRadioItem` 透传，其余行保留默认交互源。账号切换行使用 `onClick` 重载，整行配色不随选中状态变化；通过显式 `Role.RadioButton`、`selected` 与状态描述保留无障碍单选语义。账号行的加载、编辑和禁用条件沿用原业务状态，不保存阴影偏好。

## 图标划线过渡

共享组件 `ui/components/AnimatedSlashIcon.kt` 为密码可见性按钮和播放器弹幕 Switch 提供 Material 3 Expressive 划线过渡，复用主题的 `fastSpatialSpec` 弹簧。原始 VectorDrawable 路径只在创建时解析并归一化到 24 × 24 画布；关闭图标拆分为主体与原有圆头斜线，主体的共同区域保持不透明，仅差异区域随限定在 0～1 的进度渐变，斜线沿左上至右下通过裁剪逐段显现，反向切换沿原路径收回。斜线保留弹簧超过 1 的进度，以起点为支点沿 45° 方向伸长后回弹，垂直方向不缩放，保持视觉线宽；静止端点直接绘制原始图标路径，保持现有几何与断开细节。

`rememberSlashIconProgress` 初始值取当前状态，后续通过 Compose `Animatable` 平滑切换；快速点击从当前进度反向，系统动画时长缩放及关闭动画由 Compose 处理。播放器在控制栏显隐分支外持有该状态，重新显示控制栏不会重播；弹幕设置异步读入时通过 `ready` 建立初始进度，不播放加载过渡。密码按钮的划线表示“隐藏密码”操作，密码遮罩和弹幕业务状态立即切换；图标仅提供视觉过渡，不改变按钮语义、禁用表现或开关尺寸。设置页普通 Switch 不新增图标。

## 认证状态

认证功能位于 [feature/auth](../app/src/main/java/moe/kirakira/feature/auth/)：

| 层次                                      | 职责                                   |
|-----------------------------------------|--------------------------------------|
| `AuthScreen` / `AuthForm` / `LoginForm` | 展示登录、2FA、注册、密码找回步骤，上报操作              |
| `AuthPage` / `AuthNavigation`           | 外层条目共享表单状态，内部步骤返回栈、转场、键盘、隐藏密码与成功返回事件 |
| `AuthViewModel`                         | 条目内表单、校验、异步提交、验证码冷却展示                |
| `SessionViewModel`                      | 应用级唯一 Repository、启动加载／资料刷新、切换／移除／登出  |
| `AuthRepository`                        | 认证编排、SHA-256、账号去重、冷却、会话事务和领域状态       |
| `AuthApi` / `SessionStore`              | 私有 DTO 与 HTTP 协议／加密原子存储              |

`ActivityNavDisplay` 在可保存状态装饰器之后添加 `rememberViewModelStoreNavEntryDecorator`，认证 ViewModel 绑定外层 `AuthRoute` 条目，旋转时保留、出栈后释放。`AuthRoute` 只携带可选邮箱以便重新登录；内部 `AuthStepRoute` 只携带步骤枚举，不包含表单数据。各步骤通过 `rememberNavBackStack` 与嵌套 `ActivityNavDisplay` 展示，复用普通转场和预测性返回。子分发器连接外层 Navigation Event 分发器，仅在认证页为当前页面时启用；子页面重置继承的页面变换，避免外层退出手势被重复应用。

`AuthStep.previousStep` 统一定义内部路径：注册按资料、凭据、邀请码、验证逐级返回；登录 2FA 返回邮箱密码；重置密码和 TOTP 找回说明返回邮箱输入。顶部返回箭头与系统返回使用同一路径，根登录页的系统返回或关闭按钮退出认证。ViewModel 校验后推进步骤，导航保留路径的公共前缀，各条目独立保存滚动位置。转场中的离开页面使用仅驻留内存的表单快照，避免提前渲染下一步；退出或返回会取消当前请求，旧请求的收尾不再改写新页面状态。

仅邮箱通过 `SavedStateHandle` 恢复。认证内部可保存状态以 ViewModel 生命周期内的随机键隔离：旋转沿用该键，进程重建生成新键并回到登录入口，不恢复密码、验证码及依赖这些输入的步骤。密码显隐使用条目内的普通 `remember`，步骤切换和 UI 重建后隐藏。

提交发生在 `viewModelScope` 中；成功状态仅在 RESUMED 时通过页面回调返回，宿主检查当前路由，避免重复出栈。认证会话只在加密存储成功后发布。如果认证已成功但保存失败，保留内存待保存凭据并进入保存重试步骤，避免再次注册或消耗验证码；关闭页面会释放待保存凭据。密码重置已被服务器确认、但本机会话清理写盘失败时，也进入单独的清理重试步骤，不重发密码重置请求。API 资料暂时不可用时保留登录返回的邮箱和 UID，不把资料读取失败误作需要再次注册。

资料解析或身份一致性检查失败时拒绝保存，也不提供保存未验证身份的重试。已解析的资料随待保存会话留在内存，写盘重试不再发送资料请求；旧请求完成不可取消的写盘阶段后，仅清除属于自身的待处理记录，避免影响后来开始的认证或密码重置。待保存记录通过内存中的 `AuthFlowOwner` 绑定条目，旧页面退出动画结束后的清理不能删除新页面的数据。成功提交后禁止继续编辑表单或切换认证分支，直至页面关闭。这些边界及内部返回路径由[认证离线测试](auth-testing.md)覆盖。

### 分步输入与系统凭据

注册新增 `REGISTER_INVITATION`：凭据步骤验证确认密码、密码提示和邮箱唯一性；邀请码步骤验证格式与可用性，再按邮箱冷却发码并前进。返回仅清空验证码，保留流程中的其他草稿；离开整个分支则清除秘密。`fieldErrors` 与带编号的 `focusField` 指向具体字段，Navigation 3 子条目等到 RESUMED、可编辑和下一帧布局后请求焦点，一次消费；返回没有新的焦点请求。未知业务拒绝仍使用页面错误，不把服务器的含糊 `success=false` 猜作某个字段错误。

`AuthScreen` 使用固定高度的 `CenterAlignedTopAppBar`，在标题槽居中显示主题色图标；登录 Logo 为 40dp，其余步骤图标为 32dp，不使用底板。返回与关闭按钮对齐资料页，使用官方默认按钮尺寸、触控区域和 AppBar 槽位定位，不额外指定 48dp 尺寸或水平 Padding。正文不再根据键盘可见性或 480dp 高度阈值切换 compact 布局，统一使用固定字号与间距，并在 IME Insets 后滚动。内容最大宽度 480dp，底部安全区仍属于滚动内容。

[PasswordCredentialGateway](../app/src/main/java/moe/kirakira/core/credentials/PasswordCredentialGateway.kt) 提供 get / save / clearSession，系统实现固定使用 AndroidX Credentials 与 Play Services 桥接。`GetPasswordOption` 显式关闭自动选中并对重新登录邮箱设置 `allowedUserIds`，返回后再次核对邮箱；不调用 View 专用联动接口。只有完整生产根地址构建的 `SYSTEM_CREDENTIALS_ENABLED` 为 true，其余使用禁用网关；测试直接注入替身。

`AuthCredentialOperation` 是带递增编号的 Get / Save 内存操作。外层 `AuthPage` 在当前 Activity RESUMED 后领取，Activity 身份变化或组合销毁时撤销领取；回调必须同时匹配操作编号及宿主身份，并检查协程未被取消。旋转不会重新弹出自动选择器，旧 Activity 的延迟结果不能触发认证。登录页手动编辑、提交或切换分支先消费自动请求机会并清空 Get 操作与宿主领取记录，操作编号变化使宿主 LaunchedEffect 取消；即使提供者迟到返回，编号和宿主校验也会拒绝其结果及提示。焦点与密码显隐不消费自动请求。系统弹窗暂时暂停 Activity 不会重新发起操作。

认证 ViewModel 保留密码是否源自未经修改的系统选择。仅手输或修改后的密码在完整认证、本地写盘成功后生成 `CreatePasswordRequest`；二步验证前不保存，存储失败仅保留短暂 `PasswordDraft` 供本地重试。密码重置等待本地清理完成。保存阶段为 FINISHING，取消或失败均进入成功／重置完成状态；失败显示非阻断提示。流程完成、退出或取消释放待保存对象，秘密类不生成包含密码的 toString。库内部和 JVM 字符串副本不能保证即时擦除。

所有显式前进、返回、关闭和系统保存之前取消 Compose Autofill 会话，保留邮箱／用户名、Password / NewPassword 语义。系统保存的持久化边界是用户选择的密码提供者，应用不将原密码写入任何存储。未配置 Digital Asset Links，不支持本轮范围外的跨网站密码共享及 Passkey。

会话 UI 状态新增 `SessionOperation(type, targetUuid)`，区分初始化、切换、移除与本机重置。操作串行执行并在写盘期间保留类型与目标；成功提交之前继续选中原账号。账号行采用居中对齐的布局，游客平时仅显示名称，普通账号显示名称与副标题，长文字单行省略；切换期间保留原有文字与布局，仅显示尾部加载器，「正在切换」通过无障碍状态描述提供；移除状态复用副标题行。loading、单选与删除按钮共用 48dp 尾部槽位，列表不插入顶部加载项或空白状态行。“添加账号”独立成组。错误携带目标 UUID，重试闭包绑定原操作。游客切换、移除当前账号或登出之后尽力调用 `clearCredentialState`，不删除提供者密码；清理失败不撤销本地操作。

参考：[官方密码接入](https://developer.android.com/identity/passwords)、[Compose Autofill](https://developer.android.com/develop/ui/compose/text/autofill)、[Credentials 版本记录](https://developer.android.com/jetpack/androidx/releases/credentials)。

## API 与会话

### 视频搜索

`feature/search/SearchViewModel` 复用 `ContentViewModel` 的会话就绪与 revision 防护，在主导航宿主创建；`SearchPage` 与导航宿主的顶栏插槽收集同一个 ViewModel 的状态，`SearchTopBar`、`SearchScreen` 与标签面板仅接收状态、事件回调。UI 不接触 DTO 或会话，结果使用现有 `VideoSummary`、`VideoCardRow` 与 `VideoRoute`，标签复用 `VideoTag.displayName` 的多语言回退。

主页面与标签面板共用 `QuerySearchBar`，使用官方 `SearchBar` 与 `SearchBarDefaults.InputField` 的公开内联重载，保持同页编辑和结果展示，不打开额外的展开搜索页面。当前版本的 state 重载在折叠 SearchBar 内禁用软键盘、要求配合独立展开页面，因此此处保留已弃用但仍公开的内联重载，不抑制弃用警告。输入按受控字符串同步 ViewModel，不额外保存查询；标签入口使用只读输入以避免打开面板前弹出键盘。

`MainScreen.searchTopBar` 插槽将双行 `SearchTopBar` 放入搜索 Tab 的 `FrostedScaffold.topBar`，随页面转场；第一行是搜索框，第二行左侧是紧凑 FilterChip 模式选项，末端并排放置排序菜单与布局切换图标，会话就绪后未提交搜索时也可选择排序和布局。排序菜单复用 `SortChanged` 与 `ToggleDirection` 事件，提供四种排序与两个方向选项，并通过勾选图标和选中语义标记当前选择；默认排序下禁用方向选项，点击当前方向只关闭菜单。菜单展开状态仅存局部 UI 内存，搜索 generation 或就绪状态变化时关闭。栏面满宽，内部居中且最大宽度为 640dp，顶部与横向系统 Insets 使用 `TopAppBarDefaults.windowInsets` 避让一次；模式选项可换行，末端图标保留完整触摸区域，栏高随内容增高。顶栏图标使用 `onSurfaceVariant`，背景与固定 4dp 阴影均由既有宿主管理。已选标签与结果数量保留在列表内容区，图标操作保留 Tooltip 和本地化描述。列表顶部留白与刷新指示器使用宿主返回的实际栏高；空状态最小高度扣除顶栏、底栏、实测标签和数量区域以及列表间距。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `video/search` | `keyword`；`success`, `videos` | 关键词视频搜索，没有分页或排序参数 |
| GET `video/tag/search` | `tagName`；`success`, `result` | 查找已有标签，没有分页参数 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`, `videos` | 同时包含所有标签的视频，没有分页参数 |

以上公开读取不携带 Cookie，查询通过共享 URL builder 编码。数据层检查业务成功和必要列表字段，将缺失列表与空列表区分，分别按视频／标签 ID 去重；多标签 ID 去重并要求为正数，单标签关联视频保留原调用入口。Rosales 关键词查询使用 Elasticsearch `query_string`，标签名称查询使用不区分大小写的正则表达式，客户端保留查询内容，不添加新的查询语法或全站过滤规则。未知业务拒绝映射为统一错误，取消继续传播到 HTTP Call。

ViewModel 分离关键词草稿、所选标签和已提交条件。视频读取与候选读取分别持有任务和递增版本，更新结果时同时检查请求版本及账号 revision；新条件、模式切换或清空会取消旧视频任务。标签候选输入防抖 500 毫秒，每次输入取消旧候选任务，关闭面板也取消；增删所选标签即时查询，以去重排序后的 ID 列表识别相同条件。相同条件正在请求时不重复提交，刷新只读已提交条件，不将未提交草稿当作刷新条件。

首次搜索只显示首次加载状态，同条件刷新保留视频、已加载空状态及列表位置，使用 `ContentPullToRefresh` 覆盖式指示器，失败保留已有数据。结果数量为实际返回并去重后的列表长度。视频 DTO 的 `duration` 以秒计，统一在数据层换算为 `VideoSummary.durationMs` 的毫秒值；非有限值、负数或超出转换范围的值作为缺失时长处理。排序稳定地按上传时间、播放量或时长比较，两个方向均将缺失值排末尾，默认顺序保留接口顺序；不请求不存在的服务端排序和分页能力。

搜索条件、结果、控制选项及滚动位置只存 ViewModel 内存，不进入 SavedState、磁盘或日志。页面的 `LazyListState` 从该内存位置创建，并通过带条件 generation 的事件回传位置；新条件重置位置，旧页面位置事件不能覆盖新条件。主界面搜索分支与首页一样将底栏占位放入滚动内容，键盘内边距由搜索页面处理。旋转、Tab 切换与视频页返回保留状态，进程重建回到空搜索页；账号 revision 改变清空全部搜索状态。

### 视频标签

视频详情的 `videoTagList` 与屏蔽设置共用 `data/content/VideoTag.kt` 的领域模型和 DTO 映射。显示名称优先使用当前语言的默认名、同语言首个非空名称；无匹配语言时回退到 `other`、首个语言组，最后使用标签编号。简繁中文映射为 Cerasus 的 `zhs`／`zht`；原名与主名称相同时不重复显示。

`TagRoute` 仅保存标签 ID，`TagViewModel` 绑定 Navigation 3 条目，标签信息与关联视频使用独立状态。公开读取使用共享 API 客户端、不携带 Cookie，仍捕获账号 revision，切换账号取消旧请求并清理状态；不增加磁盘缓存。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| POST `video/tag/get` | `tagId: [id]`；`success`, `result` | 读取标签名称，空结果表示标签不存在 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`, `videos` | 标签页传单个 ID，搜索页可传多个 ID；当前没有分页参数 |

标签页复用可折叠顶栏和视频卡片，正文顶部展示标签名称与原名；名称面板使用官方 `ModalBottomSheet` 与只读 `SegmentedListItem`，底部 Insets 放入滚动内容。只接入现有只读接口，不使用视频上传接口模拟标签编辑。

### 接口契约

流程对照同级 Cerasus 项目的 `components/Login/LoginWindow.vue`、`composables/api/User/UserController.ts`、`utils/hash.ts`；端点与字段核对同级 Rosales 的 `src/route/router.ts`、`src/controller/UserControllerDto.ts`、`src/service/UserService.ts`。这是本次仓库源码核对，不表示已执行生产账号联调。

| 请求                                                                    | 请求字段／关键响应                                                                                                         | 用途                 |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|--------------------|
| GET `user/checkUserHave2FAByEmail`                                    | query `email`；`success`, `have2FA`, `type`                                                                        | 判断无验证器／email／totp  |
| POST `user/login`                                                     | `email`, `passwordHash`, `clientOtp` 或 `verificationCode`；`success`, `UUID`, `uid`, `token`                       | 密码及可选 2FA 登录       |
| POST `user/self`                                                      | 请求 Cookie `uuid`, `uid`, `token`；`success`, `result`                                                              | 校验会话并获取自己资料        |
| POST `user/update/info`                                               | `username`, `userNickname`, `signature`, `gender`, `userBirthday`, `label`, `avatar`, `userBannerImage`；`success` | 保存本人资料，保留背景图       |
| GET `user/avatar/preUpload`                                           | 会话 Cookie；`success`, `userAvatarUploadSignedUrl`, `userAvatarFilename`                                            | 获取头像上传地址与图片 ID     |
| GET `user/checkUsername`                                              | query `username`；`isAvailableUsername`                                                                            | 用户名可用性             |
| GET `user/existsCheck`                                                | query `email`；`exists`                                                                                            | 邮箱查重               |
| POST `user/checkInvitationCode`                                       | `invitationCode`；`isAvailableInvitationCode`                                                                      | 邀请码检查              |
| POST `user/sendGeneralEmailVerificationCode`                          | `email`, `clientLanguage`, `mailTemplate`, `exclusiveBusinessName`                                                | 验证码发送              |
| POST `user/registering`                                               | `email`, `passwordHash`, `passwordHint`, `verificationCode`, `invitationCode`, `username`, `userNickname`         | 注册并获取会话            |
| POST `user/forgot/password`                                           | `email`, `newPasswordHash`, `verificationCode`                                                                    | 设置新密码              |
| GET `user/checkUserHave2FAByUUID`                                     | 会话 Cookie；`success`, `have2FA`, `type`, `totpCreationDateTime`                                                    | 当前账号验证器状态          |
| POST `user/sendGeneral2FAEmailVerificationCode`                       | 会话 Cookie；`clientLanguage`, `mailTemplate`, `exclusiveBusinessName`                                               | 当前邮箱安全操作验证码        |
| POST `user/update/email`                                              | `oldEmail`, `newEmail`, `passwordHash`, `changeEmailVerificationCode`, `changeEmailNewEmailVerificationCode`      | 修改邮箱               |
| POST `user/update/password`                                           | `oldPasswordHash`, `newPasswordHash`, `verificationCode`                                                          | 修改密码               |
| POST `user/createEmailAuthenticator`                                  | 会话 Cookie；`success`, `isExists`                                                                                   | 启用邮箱验证             |
| DELETE `user/deleteUserEmailAuthenticator`                            | `passwordHash`, `verificationCode`                                                                                | 关闭邮箱验证             |
| POST `user/createTotpAuthenticator`                                   | 会话 Cookie；`success`, `isExists`, `result.otpAuth`                                                                 | 创建待确认 TOTP         |
| POST `user/confirmUserTotpAuthenticator`                              | `clientOtp`, `otpAuth`；`result.backupCode`, `result.recoveryCode`                                                 | 确认 TOTP 并返回一次性恢复信息 |
| DELETE `user/deleteTotpAuthenticatorByTotpVerificationCodeController` | `passwordHash`, `clientOtp`                                                                                       | 使用验证码／备用码解绑 TOTP   |

密码传输格式与 Cerasus `generateHash` 一致：UTF-8 原密码的 SHA-256，小写 64 位十六进制，不能 trim 原密码或把 DTO 中旧的 bcrypt 注释当作契约。摘要依然等价于敏感凭据，只在内存保留，不得记录。

邮件模板／业务名分别是 `SendLoginVerificationCode` / `login`、`SendRegistrationVerificationCode` / `registration`、`SendResetPasswordVerificationCode` / `forgot-password`；语言为 `zh-Hans-CN` 或 `en-US`。Rosales 的冷却按邮箱跨业务共用，Repository 使用单调时钟记录 60 秒截止值并串行发送；服务端冷却或每日发送／校验上限优先于 `success`。邮箱验证码为六位数字；TOTP 输入允许备用／恢复码。注册昵称可选，用户名字符策略与 Cerasus `assets/pomsky/username.pom` 对齐，表单长度上限为 20。

### 本人资料编辑

个人主页通过 `ProfileHeader → ProfileScreen → ProfilePage` 的独立 `onEditProfile` 回调上报编辑操作。`SelfProfileRoute` 与当前用户的 `ProfileRoute` 均通过 `openFrom` 打开同一个 `ProfileEditorRoute`，防止重复入栈；按钮按当前登录用户 UID 显示，点击时再次核对身份。编辑页退出仍使用现有返回保护，保存后沿用会话修订触发主页资料刷新。

个人主页「编辑资料」使用全宽的官方 M3E `Button`，以 `ButtonDefaults.MediumContainerHeight` 设置最小高度，并将同一高度传给 `shapesFor`、`contentPaddingFor` 与 `textStyleFor`，统一使用官方 Medium 形状、内边距与文字样式；`heightIn(min = …)` 允许字体缩放时按钮随内容增高。

`ProfileEditorRoute` 使用 Navigation 3 条目级 `ProfileEditorViewModel`，资料通过 `data/profile/ProfileRepository` 访问，DTO 与凭据保持在数据层。`AccountProfile` 增加生日、性别和标签及兼容旧存储的默认值；读取本人资料时核对 UUID、UID。页面最大宽度 640dp，与设置页同用 `surfaceContainer` 背景和 `SectionHeader` 分组标题；顶部为大圆角横幅，使用 `Image` 与 `ContentScale.Crop` 展示个人主页共用的 `profile_banner_placeholder` 樱花图，保留服务端背景字段；头像为居中重叠的 112dp 头像与带背景色描边的编辑按钮。基本资料与标签放在 `surface` 圆角卡片中，个人信息使用分段资料行；保存操作接入[设置表单操作区](#设置表单操作区)。另含日期选择器与标签 Chips。所有界面文本维护中英文资源，预览使用空表单。

用户名与昵称按 Rosales `ValidTool.validateNameField` 校验，最长 20 个 UTF-16 单元，昵称可为空；简介最长 200。保存统一 NFC 规范化，用户名执行 trim，只有修改用户名才调用查重。标签保留顺序及已有 ID，新项使用最小可用非负 ID；生日未设置时发送空字符串，不默认写入当天。更新始终包含用户名与已有背景，其他不属于编辑表单的字段不主动发送。

保存依次进行头像上传、资料更新、本人资料回读和加密会话提交。Repository 持有单个编辑流程的检查点：已上传的同一头像草稿不重复上传；服务端已保存后锁定编辑，刷新失败只重试刷新，写盘失败保留已读结果并仅重试提交。提交保留 token 与 bootstrap hint，仅替换当前账号资料；发布会话修订后现有内容观察者刷新“我”及本人主页。外部账号／修订变化取消请求和清除草稿，仅自身提交产生的修订被编辑页接纳。HTTP 200 的业务拒绝不通过匹配服务器消息推断过期。

图片选择使用 `PickVisualMedia`，回调绑定选择时的账号修订；原图复制限制 32 MiB。CanHub Android Image Cropper 提供经过验证的裁剪、EXIF 与采样能力，通过 `AndroidView` 封装 `CropImageView`，仅这一引擎使用第三方 View，周围顶栏、工具按钮、加载与确认均使用 Compose M3E。固定 1:1 裁剪和圆形预览，异步输出最大 1024px 的 JPEG（质量 90）；Manifest 移除库自带的导出 Activity 与未使用的文件提供者。View 重建时从流程内存恢复裁剪范围；释放时清理图片和监听，正在运行的异步裁剪仅保留收尾回调，完成后释放图片并拒绝旧流程结果。草稿文件为每个流程独立缓存，替换、退出和账号变化时异步删除；不写 SavedState 或会话存储。

预签名上传使用独立无 Cookie／认证头的 OkHttp 客户端，仅允许 `https://upload.imagedelivery.net:443`，发送 multipart `file`，禁用重定向和自动重试，完整调用上限 60 秒，响应上限 1 MiB，并检查 HTTP 与 JSON `success`。成功的图片 ID 交给资料更新接口，不保存或记录预签名 URL。应用 API 仍使用共享 HTTPS 客户端。

返回保护由 `ActivityNavDisplay` 的默认放行回调协调。资料有草稿或正在裁剪时不启动页面预测性返回转场；手势完成才显示确认、关闭裁剪或忽略正在保存的返回，取消手势没有副作用。顶栏使用同一 ViewModel 判断；用户确认离开后才出栈，不添加页面级低层返回处理器。

### 设置表单操作区

`SettingsScaffold` 的可选 `bottomBar` 插槽直接交给 `FrostedScaffold` 内的官方 Scaffold。资料、隐私及账号安全流程使用 [SettingsActionBar](../app/src/main/java/moe/kirakira/feature/settings/SettingsComponents.kt)，不再将主操作放在滚动内容的悬浮工具栏或表单末尾；头像裁剪保留原底部旋转与 `SettingsPrimaryButton` 完成布局。

操作区以 `surface` 铺满宽度，内部居中限制在设置页 640dp 内容区域，四周留出 16dp，主按钮按标签宽度向尾侧对齐。`ShadowButton` 使用 `ButtonDefaults.MediumContainerHeight` 及同高度的 `shapesFor`、`contentPaddingFor` 和 `textStyleFor`，默认仅显示单行文字，加载时使用 Material 2 官方按钮图标尺寸／2dp 的圆形加载器，前景继承当前按钮内容色。普通操作沿用 `primary/onPrimary`，安全停用为 `error/onError`；保留本项目共享双层彩色阴影和禁用时无阴影的规则，这是相对 [Material 官方默认实心样式](https://m3.material.io/components/buttons/guidelines)的定制。同步重试和恢复码确认使用短标签，原完整资源用于按钮无障碍描述；加载状态通过本地化状态描述表达。

宿主使用 `imePadding` 避让并消费键盘 Insets，操作区仅处理尚未消费的 `safeDrawing` 横向和底部安全区域。Scaffold 将底栏实测高度写入内容 padding，`SettingsColumn` 及安全页滚动 Column 将其放在 `verticalScroll` 后的底部 padding，保持完整滚动视口；Snackbar 自动放在底栏上方，不另加固定偏移或悬浮工具栏留白。游客、首次无数据加载／错误及安全概览不组合底栏，已有表单刷新或提交时保留并禁用操作。隐私重置为顶栏裸图标按钮，保留提示、加载和禁用语义，仍调用原有重新读取入口；返回、舍弃、账号隔离和业务提交继续由现有 ViewModel 管理。

### 账号设置管理

安全页使用 `SecuritySettingsRoute`、导航条目级 `SecuritySettingsViewModel` 与 `data/security/SecurityRepository`。页面内步骤与表单由 ViewModel 管理，敏感模型使用普通类避免生成含凭据的 `toString()`；没有敏感持久化状态或 DTO 进入 UI。每次请求捕获 `AuthRepository.RequestSession`，核对 revision 并在散列后、HTTP 返回后验证账号；取消继续传播，明确 401 才失效本机会话。设置入口对游客导航登录，恢复导航中的游客安全页提供登录操作。

安全验证码复用 AuthRepository 的按邮箱互斥锁、单调时钟与 60 秒冷却，当前邮箱使用认证端点，新邮箱使用带当前会话的通用邮箱端点；两者独立显示剩余时间。模板对应 `SendChangeEmailVerificationCode` / `update-email`、`SendChangePasswordVerificationCode` / `update-password`、`SendDisableUserEmail2FAVerificationCode` / `delete-email-2fa`。Rosales 当前 `General2FAVerifier` 在无二步验证的严格校验分支固定查询 `update-email`：这类账号修改密码时发送密码模板但使用 `update-email` 业务名，兼容实际服务端逻辑；已启用邮箱验证仍使用 `update-password`。不改动 Rosales 或 Cerasus。

邮箱与密码更新服务端确认成功后，Repository 在内存记录完成检查点；邮箱检查点只重读并发布本人资料，密码检查点只删除请求所属本机会话。存储失败释放表单凭据并提供同步重试，不重放写请求。AuthRepository 在会话互斥锁内校验请求并成功写入本地存储后、发布会话状态前，通过回调提供本次实际发布的 revision；资料与安全流程据此识别自身发布／登出，不自行推算下一修订号。账号切换或其他会话变更仍取消旧流程。密码变更后经一次性邮箱状态打开登录并尽力清理密码提供者会话，不自动读取或保存密码。

UI 使用共享可折叠顶栏、官方分段列表、语义主题色状态横幅、启用徽章和分步进度。普通设置入口、开关、单选、滑块、资料和安全列表前后图标使用裸露的 24dp Material Symbols Rounded（含尾部箭头及列表内操作图标），继承列表内容颜色及禁用样式，危险操作使用主题错误色；弹幕总开关保留官方 checked 行强调。共用菜单组件的“我”页面同步使用 24dp 图标；屏蔽管理固定类别与添加表单同样使用 24dp 裸图标，用户头像保持独立。MaterialShapes 图标容器保留于隐私可见性、邀请码使用状态等状态形状切换，以及安全状态横幅、安全流程和邀请码统计的独立大图标。首页刷新保留内容；表单使用滚动布局，底部系统 Insets 随内容滚动并避让 IME。ActivityNavDisplay 的返回保护在流程内消耗返回回到首页，操作期间阻止返回；一次性代码阶段须确认后清空。TOTP URI 按结构解析并校验 `otpauth://totp` 与 Base32 密钥；ZXing Core 固定版本在后台生成带白底静区的 Bitmap，Compose 展示，生成失败仍可使用手动密钥。

恢复响应核对五个不同的六位备用码和 24 位恢复码；已确认绑定但返回不完整时不重新确认，清空设置材料并提示重新解绑绑定。复制通过 Android ClipboardManager 标记 `android.content.extra.IS_SENSITIVE` 与随机所有者标记，离开步骤仅清理仍属于本流程的内容，不覆盖其他应用后续复制的内容。二维码、密钥与恢复码不导出到应用磁盘；生命周期和内存保护边界与认证流程一致，Kotlin 字符串无法保证物理内存即时清零。

隐私页采用 `PrivacySettingsRoute` 与导航条目级 `PrivacySettingsViewModel`，复用 `AccountSettingsRepository` 的账号 revision 请求守卫。`POST user/settings` 读取后检查 `success`、设置对象及当前账号 UID；五项缺失值默认公开，重复项或未知可见性拒绝编辑。`POST user/settings/update` 提交完整五项 `userPrivaryVisibilitiesSetting`，保留其他隐私条目并带回原有 `userLinkedAccountsVisibilitiesSetting`，避免后端更新逻辑覆盖关联设置，不提交主题等其他偏好。wire ID 使用 `privary.birthday`、`privary.age`、`privary.follow`、`privary.fans`、`privary.favorites`，可见性值使用 `public`、`following`、`private`。

更新接口可能返回不含 UID 的更新载荷：此时 Android 通过同一请求账号再次调用 `POST user/settings`，确认 UID 与五项值后再发布保存成功；读取失败保留草稿并显示错误，不自动重发写请求。DTO 仅在数据层使用，领域模型交给 UI；草稿和选择面板仅存流程内存。账号变化取消请求、清空状态；旋转保留 ViewModel，进程重建重新读取。隐私页返回保护复用 `ActivityNavDisplay` 回调，草稿未保存时不启动预测性返回动画，手势完成显示放弃确认，保存／重置期间忽略返回。

界面使用连接式 ToggleButton 组批量设置可见性，按当前可见性着色的 MaterialShapes 形状图标容器，分段列表逐项选择；应用使用共享[设置表单操作区](#设置表单操作区)，重置位于顶栏尾侧。底部选择面板复用相同形状容器。后端隐私限制缺陷及保存语义见[隐私设置](features.md#隐私设置)，本次不改动 Rosales 或 Cerasus。

`data/settings/AccountSettingsRepository` 复用共享 API 客户端与唯一 AuthRepository，屏蔽规则和邀请码通过领域模型交给条目级 ViewModel。导航为 `BlockingOverviewRoute`、带 `RuleCategory` 的 `RuleManagementRoute` 与 `InvitationsRoute`，使用现有条目装饰器、`NavigationPage` 和认证返回流程；不增加依赖或持久化规则副本。

| 请求                                                   | 请求字段／关键响应                                                                                             | 用途            |
|------------------------------------------------------|-------------------------------------------------------------------------------------------------------|---------------|
| GET `block/list`                                     | `type` = `block` / `hide` / `tag` / `keyword` / `regex`，`page`, `pageSize`；`result`, `blocklistCount` | 总览数量与规则分页     |
| POST `block/user`、DELETE `block/delete/user`         | `blockUid`                                                                                            | 屏蔽／解除用户       |
| POST `block/hideuser`、DELETE `block/delete/hideuser` | `hideUid`                                                                                             | 隐藏／显示用户       |
| POST `block/tag`、DELETE `block/delete/tag`           | `tagId`                                                                                               | 标签规则          |
| POST `block/keyword`、DELETE `block/delete/keyword`   | `blockKeyword`                                                                                        | 关键词规则         |
| POST `block/regex`、DELETE `block/delete/regex`       | `blockRegex`；新增还检查 `unsafeRegex`                                                                      | 正则规则          |
| GET `user/info`                                      | `uid`                                                                                                 | 添加用户前复用公开资料查询 |
| GET `video/tag/search`                               | `tagName`；`result`                                                                                    | 选择已有标签        |
| GET `user/myInvitationCode`                          | Cookie；`invitationCodeResult`                                                                         | 当前账号邀请码       |
| POST `user/createInvitationCode`                     | 空 JSON 对象及 Cookie；`isCoolingDown`, `invitationCodeResult`                                             | 生成邀请码         |

规则接口使用 Cookie `uuid` / `token`，邀请码使用 `uid` / `token`，统一通过既有会话 Cookie 构造器提供。每次请求捕获会话 revision，完成及失败时检查身份，账号变化取消任务并清空草稿；邀请码响应校验 `creatorUid`。HTTP 401 沿用会话失效处理，普通业务拒绝不直接撤销 token，也不展示原始服务端消息。

总览每类读取一条以获取数量，管理列表每页 20 条；写入成功发布规则版本变化，刷新存活总览，并重新读取当前类别首屏。服务端空集合的聚合可能省略 `blocklistCount`，仅在结果确实为空时兼容为零。用户规则的 `value` 为 UUID，解除必须使用关联的 `uid`，不能将 UUID 当作 UID；关联用户被删除导致 UID 缺失时保留标识、禁用解除。标签名称按 Cerasus 的 `zhs` / `zht` / `en` 等语言代码选择，回退 `other`、首个语言和标签 ID。

关键词与正则的客户端校验仅处理非空及 30 字符限制；正则不经 Kotlin 编译，语法与安全性由 Rosales 判断。邀请码的 `assignee` 是否存在决定已使用状态；先检查业务成功，再处理生成冷却。确认生成但尚未被列表读回的邀请码暂存在该账号的 ViewModel 内存中，刷新失败或短暂旧结果不覆盖它；不自动重试生成请求。过滤全部由后端负责，不扩展首页、评论或弹幕的本地规则处理。

### 网络与错误

[ApiClient](../app/src/main/java/moe/kirakira/core/network/ApiClient.kt) 统一使用 OkHttp 与 serialization，连接 15 秒、读写 20 秒、完整调用 30 秒，响应上限 1 MiB。通过 callback 与 `suspendCancellableCoroutine` 绑定取消，解析和散列使用 Default，存储使用 IO。认证请求关闭自动重试、重定向和缓存，不安装日志或全局 CookieJar；API 路径为代码常量，query 通过 builder 编码，单次请求明确绑定凭据快照。头像请求不共享会话。

HTTP 401、429、5xx 分别映射失效、限流、服务故障，其余失败与网络／超时／响应格式异常分开。HTTP 2xx 仍需检查业务 `success` 和必要字段；获取自己资料还校验 uuid 与 uid。服务器消息不直接显示，统一使用 UI 字符串资源。

当前 Rosales 的 `/user/self` 对无效 token 和数据库故障都可能返回 HTTP 200 + `success=false`，缺少可靠业务错误码。因此此类拒绝标记需要重新验证、撤下该账号当前身份，但保留加密 token 供用户重试；不能宣称一定已过期。明确 401 才清除 token，账号条目保留。网络和超时保留离线资料及会话。后续后端提供稳定错误码后应更新这一映射，而不是匹配中文 `message`。

### 会话存储与切换

[SessionStore](../app/src/main/java/moe/kirakira/data/auth/SessionStore.kt) 用 Android Keystore 的不可导出 AES-256 密钥、随机 IV 和 AES/GCM/NoPadding 加密完整账号库，通过 `AtomicFile` 写入 `noBackupFilesDir`。读写失败不会退回明文或覆盖为假成功；UI 提供重试及需要确认的本机账号库重置。加密选择参考 [Android 密码学建议](https://developer.android.com/privacy-and-security/cryptography) 与 [Keystore](https://developer.android.com/privacy-and-security/keystore)。

[SessionCipher](../app/src/main/java/moe/kirakira/data/auth/SessionCipher.kt) 保持既有 v1 格式与密钥别名：12 字节随机 IV、密文、16 字节 GCM 认证标签；每次加密由 Keystore 提供者生成 IV，密钥显式要求随机化加密，不允许调用者指定加密 IV。完整标签验证成功后才解码 UTF-8 JSON，再检查版本、HTTPS 环境、账号身份、token 和当前账号的一致性。读写均限制在 1 MiB，密码摘要计算和会话加解密的临时字节数组使用后清零。

读取先调用 `AtomicFile.openRead()` 恢复已提交的 `.bak`，再进行有界读取；不能仅因主文件缺失就返回空账号库。解密从不创建密钥；读取失败或尚未完成初始化时禁止写入，已存在密文但密钥丢失时也禁止生成新密钥覆盖。重置清理主文件、`.bak`、`.new` 与密钥；单实例互斥锁串行化读写。`backup_rules.xml` 与 `data_extraction_rules.xml` 只允许备份外观设置 `kirakira_settings.xml`，账号库保留在系统始终排除的 `noBackupFilesDir`。参考 [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) 与 [自动备份规则](https://developer.android.com/identity/data/autobackup)。

保护边界：该方案保护本地静态数据的机密性与完整性，不提供旧密文回滚检测，也不能在应用进程已被控制时阻止其调用 Keystore 或读取运行中的凭据。是否由 TEE／StrongBox 保护取决于设备，本项目不把模拟器验证当作硬件安全证明。Kotlin／Java 字符串及库内部副本不能保证立即清除；临时字节数组清零不等于所有内存中不存在凭据。密码的 SHA-256 是现有服务端协议要求的摘要，不是加密或服务端慢哈希的替代，仍依赖 HTTPS 与服务端凭据保护。

账号包含最小资料、token 和需要重登标记，按 UUID 去重；UI 只获得无 token 的 `SessionState`。库记录版本及 API 根地址，不跨环境发送凭据。状态变更由 Mutex 串行化；新身份写盘和内存发布组成不可取消的短事务，网络阶段仍可取消。切换前获取 `/user/self`，完成后才发布新的当前身份；切到游客不发请求。移除／登出清除指定账号，其他账号保留。

Rosales `GET /user/logout` 仅设置清除浏览器 Cookie，没有服务端 token 撤销；Android 没有共享 CookieJar，直接移除本地加密凭据即可离线登出。后端未提供 refresh token、过期时间或设备会话管理，不能伪造这些能力。密码重置成功会将本机匹配邮箱的会话标记为需要重新登录。视频和公开资料通过真实数据层加载；本人主页以 `SessionState.activeProfile` 为初始回退，再读取公开资料、作品和统计。图片查看器支持公开 HTTPS 远程头像，导出使用独立无会话的 OkHttp 客户端下载完整原图，限制大小和跳转，失败与取消时清理临时文件。

## 共享组件接入

### 顶部导航按钮定位

设置、资料、视频与图片查看器的导航按钮统一放在官方 Material 3 顶栏的 `navigationIcon` 中，使用默认定位与系统 Insets，不额外添加按钮位置边距。设置使用 `LargeFlexibleTopAppBar`，其余三个页面使用透明 `TopAppBar`；设置保留普通返回箭头，资料和视频保留带底色返回箭头，图片查看器保留深色圆形关闭按钮。

普通顶栏（含认证、资料、设置、主页面与头像裁剪）复用 [`appTopAppBarColors`](../app/src/main/java/moe/kirakira/ui/components/TopAppBar.kt)：容器及滚动后容器透明，标题使用 `primary`，导航与操作图标使用 `onSurfaceVariant`，与普通列表图标保持一致。认证页返回和关闭使用平面官方 `IconButton`，保留原有回调、无障碍描述及测试标签；资料页保留带底色按钮，通过 `appTopAppBarTonalIconButtonColors` 同步内容色与禁用透明度，按钮容器、形状及阴影继续由原组件处理。首页品牌 Logo 和认证流程标题图标保留强调色，文字操作沿用原组件默认配色；播放器和图片查看器保留白色媒体控件。

视频顶栏作为页面全宽覆盖层，独立于已应用内容 Insets、最大宽度为 840dp 的视频区域，使用顶栏默认的顶部和水平系统 Insets。它不占用额外内容高度、不改变视频尺寸，宽屏时导航按钮仍按页面边缘定位。图片查看器继续由原有控件显隐、焦点与转场状态管理顶栏。

### 可复用 UI 参数示例

以下示例展示状态与事件分离，以及第一个可选参数 `modifier` 的位置；完整约束见 [Compose 与界面规范](../CONTRIBUTING.md#compose-与界面规范)。

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

[`FrostedScaffold`](../app/src/main/java/moe/kirakira/ui/components/FrostedScaffold.kt) 为普通页面创建独立的 `HazeState`，用 `hazeSource` 采样内容、单独绘制顶部背景，再绘制透明的官方顶栏。文字和图标不参与模糊。背景形状、alpha 和原生阴影由 `ThemeShadows.barSurfaceLayer` 在背景子节点上统一管理，避免重复投影或降低前景透明度。胶囊底栏同样分离背景与前景，背景通过 `frostedBarBackground(shape, shadowElevation = BarShadowElevation)` 使用外层主页面采样状态，内部各 Tab 的顶部栏使用独立状态。`frostedBarBackground` 的可选 `hazeState` 参数允许自定义布局显式复用相同背景样式，默认仍读取当前页面的采样状态；`shadowElevation` 默认 0dp，普通顶栏、视频页顶部共享背景和胶囊底栏显式传入固定的 `BarShadowElevation`，资料顶栏保留默认值。

- 背景使用 `HazeInput.Sources` 采样，源消失时使用 `ClearWhenUnavailable`，不继续保留上一页面画面。原生 backdrop 暂不启用：已在 Android 17 模拟器（`CP41.260828.004.A7`，SkiaGL）上复现 `RenderNode.setBackdropRenderEffect()` 与半透明父层、elevation 同时使用时的灰框和内部矩形异常。对照父层 alpha 为 1、0.5、0.2：普通 elevation 及 Haze Sources 正常，Haze Backdrop 异常，强制 Offscreen 仍异常；移除 Compose/Haze、仅使用 Android View 和 RenderNode 也能复现，而移除 backdrop 恢复正常。证据定位到系统原生 backdrop 的合成路径，尚未定位内部实现的具体错误，也未验证所有设备。版本以 `gradle/libs.versions.toml` 为准，不引入玻璃折射模块。
- Android 12+ 使用主题 `surface` 作为缺失源像素的底色，叠加同色 80% 不透明度遮罩、20dp 模糊与零噪点，背景图层 alpha 为 1。Android 8.1–11 绘制不透明主题 `surface`，在同一背景图层上设置 alpha 为 0.9 和原生 elevation，不挂载采样节点；各版本共用[栏面阴影](#栏面阴影)的机制，不额外增加阴影版本判断。背景透明度不作用于整个栏或前景。
- 共享背景沿用 Haze 默认采样配置，保留实时更新、模糊半径与遮罩。模糊样式按主题 surface 色缓存，采样输入按页面状态缓存，现有采样层级保持不变。
- 滚动列表的顶部与底部安全区域放入 `contentPadding`，滚动 Column 则在 `verticalScroll` 后添加，初始避开栏面、滚动时内容可进入栏后。`ContentPullToRefresh.indicatorTopPadding` 仅移动覆盖式指示器，不移动滚动视口。历史页的搜索输入通过共享组件覆盖顶栏，不占用列表条目。
- 本人及作者资料页使用铺满屏幕的外层列表，将顶部栏避让放入内容内边距，通过扣除顶部栏及 Tab 高度的 Pager 保留栏下吸顶与嵌套分页滚动；包含滚动封面的顶部共享背景单独采样并与内容共享该页状态，同时统一承载封面、资料区与 Tab 的原生投影。顶栏仅在 Tab 吸顶后复用毛玻璃背景，不单独绘制阴影。非滚动表单保留安全布局。头像裁剪页同样接入本组件，由宿主提供顶部背景与固定阴影；裁剪引擎与底部操作栏保持原布局。视频画面、播放控件及系统栏不接入本组件。

- 视频页为 Tab 栏创建独立 `HazeState`，仅在 Android 12+ 将下方 `HorizontalPager` 接入 `hazeSource`，播放器不参与采样。播放器占位区域与 Tab 共用顶部背景，通过 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)` 沿整体下沿投影；播放器区域的背景黑底位于同一图层，实际播放器仍独立绘制。官方 `PrimaryTabRow` 容器透明，文字与指示器保持清晰。简介、评论、弹幕列表将实测栏高加入顶部 `contentPadding`，初始避开栏面，滚动时从栏后经过；空状态高度同步扣除栏高。刷新指示器通过 `indicatorTopPadding` 避让，评论分页工具栏固定在栏下，游客登录提示并入评论头部条目以保持分页索引；`FloatingComposerLayout.topPadding` 扣除输入面板可用高度，底部安全区域与输入区留白保持原逻辑。全屏与画中画继续由既有播放器布局和详情显隐处理。

### 顶栏淡色图标底纹

[`ShadingIcon`](../app/src/main/java/moe/kirakira/ui/components/ShadingIcon.kt) 复用首页 Logo 底纹样式：128dp 图标、主题 `primaryFixed` 配色和 20% 透明度，在装饰区域靠末端垂直居中，按布局方向定位并避让顶栏横向系统 Insets。图标保持固定尺寸，不受折叠顶栏高度约束，超出区域的部分由组件裁切。

在承载顶栏的 `Box` 内先绘制底纹，再绘制透明顶栏；通过 `Modifier.matchParentSize()` 让底纹跟随顶栏实际尺寸而不参与测量。底纹位于标题槽之外，可延伸到状态栏后方；不要给装饰额外添加顶部系统内边距。毛玻璃背景和阴影继续由 `FrostedScaffold` 管理。

`icon` 接收现有 Drawable 资源，`endPadding` 默认为 16dp，`alignment` 默认为 `Alignment.CenterEnd`，`offset` 默认为 `DpOffset.Zero`。对齐同时用于图标在宿主内的定位及不受约束的尺寸布局，`offset.x` 的正值沿布局方向向末端移动，`offset.y` 的正值向下移动。首页传入 `logo_kirakira` 并保留 72dp 末端留白避让头像，使用默认对齐和偏移。

设置与历史页指定 `Alignment.BottomEnd`、`endPadding = 0.dp` 和 `DpOffset(32.dp, 32.dp)`，让图标向末端及底部各溢出 32dp，由组件边界裁切。底纹随顶栏实际高度始终贴住底边，下拉展开后也保持底部定位。设置首页使用 `ic_symbol_settings` 并开启旋转；历史页使用 `ic_symbol_history`，保持默认静止状态。历史底纹与普通顶栏一起放在 `SearchableTopAppBar` 的 `topBar` 插槽内，搜索展开时随普通顶栏退场并隐藏，退出搜索后恢复。

所有设置子页面沿用这一右下角定位，但传入页面自身的现有图标且保持静止：外观使用 `ic_symbol_palette`，播放使用 `ic_symbol_play_circle`，弹幕使用 `ic_custom_danmaku`，关于使用 `ic_symbol_info`，资料使用 `ic_symbol_person`，隐私使用 `ic_symbol_shield`，安全使用 `ic_symbol_lock`。管理页的共享 `ManagementFrame` 接收同一图标参数，屏蔽总览使用 `ic_symbol_block`，屏蔽分类详情使用 `RuleCategory.iconRes()`，邀请码使用 `ic_symbol_confirmation_number`；账户切换和许可证独立包裹顶栏，分别使用 `ic_symbol_switch_account` 与 `ic_symbol_description`。头像裁剪页是独立的图片编辑顶栏，不绘制该底纹。

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

共享组件为 [`CollapsibleTopAppBar`](../app/src/main/java/moe/kirakira/ui/components/CollapsibleTopAppBar.kt)，适用场景、状态与 Insets 约束见[贡献指南](../CONTRIBUTING.md#可选的可折叠大标题栏)。

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

[`SearchableTopAppBar`](../app/src/main/java/moe/kirakira/ui/components/SearchableTopAppBar.kt) 接收关键词、展开状态、提示文案、输入／展开／清空／退出／提交事件及 `enabled`、`isActive`。`topBar` 插槽提供打开搜索的事件，调用方可保留普通顶栏、动态标题及其他操作，使用配套的 `TopAppBarSearchButton` 放置搜索图标。组件不依赖页面、ViewModel、Repository 或导航控制器，退出是否清空由页面决定。

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

可折叠顶栏沿用上述参数，替换插槽并在页面的 `onExpand` 中先以主题动效将 `scrollBehavior.state.heightOffset` 动画至 `heightOffsetLimit`，完成后才设置展开状态。开始折叠与搜索期间暂停顶栏的 `nestedScroll` 连接，退出后重新接入并保持折叠；异步折叠工作应随页面失活或账号变化取消。历史页的完整接入见 [`HistoryPage`](../app/src/main/java/moe/kirakira/feature/history/HistoryPage.kt)。插槽示例：

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

个人主页后续可把自身带动态昵称、返回按钮与更多操作的 `TopAppBar` 放入同一插槽，保留外层独立 Haze 采样背景；搜索数据、Tab 范围与过滤策略由个人主页定义。本次只接入历史页。

## 启动器图标

Manifest 的 `icon` 与 `roundIcon` 分别引用 `mipmap-anydpi` 中的 `ic_launcher.xml` 与 `ic_launcher_round.xml`，两者共用品牌粉色背景和白色矢量前景，并以同一前景提供主题图标的 `monochrome` 层；外轮廓由启动器裁切。

项目最低支持 API 27，已覆盖自适应图标所需的 API 26，因此仅维护自适应 XML 与矢量图层，不保留面向旧版 Android 的各密度 launcher WebP。更新品牌图标时修改对应图层；若将来降低最低支持版本至 API 25 或以下，需补齐匹配品牌设计的传统图标，并为自适应 XML 添加 `v26` 限定符。

## 启动动画

[MainActivity](../app/src/main/java/moe/kirakira/MainActivity.kt) 与 [SplashRevealController](../app/src/main/java/moe/kirakira/ui/splash/SplashRevealController.kt) 协调系统 Splash 退出和 Compose 覆盖层，[SplashReveal](../app/src/main/java/moe/kirakira/ui/splash/SplashReveal.kt) 绘制图标遮罩。

启动屏浅色使用品牌粉色 `#F06E8E` 背景和白色图标，深色使用 `#121212` 背景和品牌粉色图标，始终跟随系统浅深色。应用内明暗模式只更新 Compose 主题，不向系统写入应用夜间模式。

所有 Android 版本的 `ic_splash` 均通过资源别名引用独立的透明矢量 `ic_splash_foreground`，不提供自适应图标背景层，移除可能在 HyperOS 上显示为外框描边的图标底板。系统 Splash 和 Compose 覆盖层共用这份矢量几何与配色，桌面与关于页图标保持原样。

Android 12+ 的系统 Splash 仍会在裁切前扩展普通矢量前景。`SplashRevealController` 使用公开的 `AdaptiveIconDrawable.getExtraInsetFraction()` 计算该扩展比例，并沿用平台对中心与半宽高的整数取整方式；Android 8.1–11 直接使用图标视图边界。退出动画不依赖启动资源必须为 `AdaptiveIconDrawable`，保持交接首帧的图标大小与位置一致。

系统 Splash 保留到主题设置读取完成、Compose 页面完成首次布局后才允许退出，避免读取完成与页面布局之间短暂露出窗口背景；关闭动画或 Activity 重建时也遵循这一就绪条件。仅在没有保存状态的新 Activity 收到系统 Splash 退出回调时播放：

1. 整个图标使用主题的 M3 Expressive `fastSpatialSpec` 收缩至 92%，首次到达目标时停止。
2. 以星星内部为支点，用 600ms 的强 S 型贝塞尔曲线 `(0.85, 0, 0.15, 1)` 展开，形成慢起步、快速冲开、缓收尾的节奏。放大倍率采用对数插值，保留起步阶段的图标轮廓；展开曲线单调递增，避免重新遮挡页面。
3. 展开进度达到 2% 时，图标填色使用 `fastEffectsSpec` 淡出，页面从星星与线条轮廓内显露；进度达到 92% 时剩余背景才开始淡出，避免过早淡化掩盖遮罩运动。
4. 图标填色消失且遮罩完全揭开后立即结束，不等待不可见的背景淡出尾段。

动画复用矢量路径直接绘制，页面自身不缩放。Compose 覆盖层绘制首帧后才移除系统启动屏，系统栏在结束后恢复当前页面样式。全部动画遵循系统动画时长设置，不叠加额外时长倍率；关闭系统动画时直接显示页面。

后台返回、旋转、页面恢复和应用内主题切换不重播。离开前台、销毁或窗口尺寸变化时清理过渡；过渡期间屏蔽底层触摸与无障碍焦点，不拦截系统返回。

### 弹幕发送样式

`DanmakuStyle` 领域模型包含 RGB 颜色、字号、模式和彩虹开关；`VideoViewModel` 以只读 Flow 暴露当前视频的样式，通过更新事件接收选择。样式只保留在条目 ViewModel 内，账号修订变化时与草稿一起重置。发送捕获正文、时间与样式快照，Repository 将颜色转换为不带 `#` 的六位大写 RGB，并发送 Rosales 的 `color`、`fontSize`、`mode`、`enableRainbow` 字段。提交期间禁用编辑，失败保留草稿与样式，成功仅清空正文。

评论和弹幕发送栏的容器色由共用 `ContentComposer` 内部统一使用 `MaterialTheme.colorScheme.surface`（浅色为纯白，深色遵循主题深灰），页面不单独覆盖。弹幕发送栏复用 `ContentComposer` 的可选尾部插槽。样式使用官方 ModalBottomSheet，自定义选色在同一面板内进入子页，复用已有 HSV 选色依赖；确认才应用颜色。面板打开时清除输入焦点并隐藏键盘。预览使用 14／20／28sp，彩虹描边参考 Cerasus 的粉蓝渐变；关闭系统动画时静态显示。预览独立于播放器，播放器使用更紧凑的三档字号与相同粉蓝描边，见下方弹幕显示实现。

### 弹幕显示与设置

`DanmakuEntry.style` 保留 Rosales 的颜色、字号、模式与彩虹标记；缺失或未知样式回退为白色、中号、右向左、无彩虹，非法时间及空白正文不进入列表。`VideoViewModel` 在账号就绪时与视频详情一起加载弹幕，列表与播放器共用同一 Flow。刷新及发送成功后的重新读取遵循现有账号修订与错误保留逻辑；渲染层不发请求、不维护本地发送副本。后端没有分 P 字段，弹幕池仍以视频 ID 为单位，按当前分 P 的进度解释；请求仍为公共读取，不扩展个性化过滤。

`DanmakuOverlay` 使用单个 Compose Canvas，放在 `ContentFrame` 上方、控制层下方，不拦截触摸也不逐条播报。视口按视频像素宽高比和容器尺寸执行 Fit，裁剪到实际视频区域顶部所选比例；未知视频尺寸时不绘制。`DanmakuTimeline` 按时间稳定排序、预先排轨，帧查询按当前弹幕池的最长显示时长，以二分定位候选时间窗口，覆盖低滚动倍率下最长 16 秒的弹幕；左右滚动位置按播放时间线性计算。同向弹幕检查共享生命期两端的间距以防追尾，反向和固定弹幕不与尚未离开的其他类型共享轨道；按每条文字布局的实际高度分配垂直空间，仅在横向运动可能相撞的弹幕之间保留 2dp 垂直间距，上下边缘各留 1dp。取消最大字号统一行高、整行倍数占位与均摊剩余高度；顶部和滚动模式从上向下寻找空隙，底部模式从所选区域底边向上寻找空隙。放不下则跳过，不延迟补发。横向追尾安全间距仍为 6dp。

播放器文字使用 12／16／22sp 与全局缩放，继续遵循系统字体缩放；显式使用 1.25em 行高、居中对齐、保留行高且关闭额外字体内边距。发送预览保持 14／20／28sp。颜色和描边样式与发送预览一致：普通弹幕为原色实心文字加黑色阴影（3px 模糊），彩虹弹幕先绘制粉蓝渐变描边（4px），再以原色填充，不加阴影。每次绘制显式设置 `Fill`／`Stroke` 和阴影；同一缓存文字布局会保留画笔状态，不能依赖空 `drawStyle` 将描边恢复为填充。TextMeasurer 使用 128 项缓存，帧循环只持有当前可见文字的布局；池布局分批让出协程，取消时终止计算。多行正文仅在画面中转为空格；超过 32,760px 的极长文字在绘制层省略，避免超出文字布局尺寸，列表保留完整正文。

滚动弹幕保留 Cerasus 的等时长模型：基准时长为 `clamp(实际视频宽度 px / (144dp 对应的 px) × 1000, 6000, 8000)` 毫秒，先限制基准时长，再除以用户滚动倍率并四舍五入为整数毫秒。6–8 秒是本客户端的体验参数，用于延长窄视口的阅读时间，并限制宽视口的停留时长。同一画面内所有左右滚动弹幕共享显示时长，实际移动路程为视口宽度加文字宽度，因此长句移动更快。默认 1× 时，360dp 宽显示 6000 毫秒，960dp 为 6667 毫秒，1440dp 为 8000 毫秒；0.5× 时为 12000–16000 毫秒，2× 时为 3000–4000 毫秒。固定弹幕保持 4000 毫秒，均采用媒体时间。默认倍率仍为 1×，已有用户倍率保持不变。

帧循环直接读取 Media3 `currentPosition`，不使用页面 500ms 轮询。暂停／缓冲时冻结并等待 Player 事件；空白区等待下一条的播放时间，seek、恢复、倍速变化通过监听器唤醒。池、尺寸和设置变化重建当前布局，视频／分 P／账号修订构成内容标识。画中画、页面非活动、播放器释放和播放失败时移除绘制层，取消协程及移除监听器；退出画中画后按真实进度重建，不补发错过的弹幕。

`DanmakuSettingsRoute` 使用现有 Navigation 3 宿主和可折叠顶栏。`DanmakuSettingsViewModel` 由导航宿主持有，向设置页与播放器提供同一份状态；只写 `kirakira_settings` 下独立的 `danmaku_*` 键，不覆盖播放或主题设置。读取在 IO 调度器完成，写入使用 SharedPreferences.apply，范围与步长统一归一化；加载完成前禁用编辑并不绘制。偏好为设备级非敏感数据，沿用该偏好文件已有的备份白名单。

弹幕设置的滑块条目保留官方 `SegmentedListItem` 分组样式，标题与当前数值在 `content` 内同排，Slider 在 `supportingContent` 内占满内容宽度，避免尾部数值列压缩滑块。

字号与排布参考：Cerasus 使用 14／20／28 CSS px、继承 1.4 行高，其 `danmaku` DOM 引擎按每条文字的实际 offsetHeight 排布；DanmakuFlameMaster 同样基于 paintHeight 与 margin 寻找位置。tdanmaku 默认 15sp、1.6 倍行高。Android 此处选用 16sp 中号与 1.25em 行高，适配较小的视频视口，并采用逐条高度排布；这些值是本客户端的取舍，并非直接复制 Cerasus 的 CSS 数值。

采用自有 Compose 绘制，不新增引擎依赖或移植外部源码。选型对照：[DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) 提供四模式但需要旧 View 引擎适配，[AkDanmaku](https://github.com/KwaiAppTeam/AkDanmaku) 引入 libGDX/ECS，[DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) 默认缺少反向模式，[tdanmaku](https://github.com/NihilDigit/tdanmaku) 的早期接口将反向滚动降级为普通滚动；这些项目未进入应用依赖或源码。

### 评论与弹幕颜文字

`ContentComposer` 接收可空的 `onLogin` 回调，评论页直接透传，弹幕页通过 `DanmakuComposer` 透传；未登录时回调非空，整条编辑区替换为通栏 `ShadowButton`，复用中英文 `content_login_to_interact` 文案、官方 `ButtonDefaults.MediumContainerHeight` 及同尺寸的形状、内边距和文字 API，以最小高度适应字体缩放。登录按钮沿用 `FloatingComposerLayout` 的宽度约束、底部避让和实测高度留白，不受编辑器的 `enabled`、`busy` 或草稿状态限制。进入登录按钮分支时关闭颜文字面板，当前活动页签清除焦点并隐藏键盘；弹幕样式面板的可保存状态同时按是否需要登录隔离，未登录时不展示。登录事件复用视频页的暂停播放和认证导航流程；登录后回调为空，恢复编辑区，不自动发送。列表标题下方移除原登录提示，提示资源同时供按钮和其他页面占位使用。

`ContentComposer` 的输入框 `Surface` 与独立发送按钮放在同一个底部对齐的 `Row` 内：输入框占剩余宽度，右侧发送按钮为固定 56dp 圆形 `FilledIconButton`，两者相隔 8dp。发送按钮位于输入框外，不参与框内测量和展开动画；整行实测高度用于扣除颜文字面板的可用空间。输入框以草稿是否为空切换单行药丸与两行编辑布局：空草稿时颜文字／样式按钮放在框内尾部，非空时放在下方操作栏。输入框、颜文字／样式按钮和发送按钮始终保留在同一组合位置，避免首次输入时丢失焦点、输入法组合状态或按钮交互状态。`ComposerInputLayout` 按框内按钮实测宽高排布，容器高度和圆角使用主题 `fastSpatialSpec`，颜文字／样式按钮横向位移与编辑区域宽度使用 `defaultSpatialSpec`，不在中途硬切分段。颜文字／样式按钮锚定框内底部，仅在横向移动。横向进度采用保留符号的平方映射，越过展开位置时以连续阻力压缩回弹距离，保持在容器边缘内，不硬截断位置。按钮横向绘制使用浮点图层位移；归一化进度的结束阈值为 0.0001，避免长距离移动尚有数个像素时就瞬间归位。容器保留空间弹簧的轻微回弹，可用宽度、最小高度与合法圆角仍受布局约束。连续输入与清空从当前进度及速度转向最新状态，不排队；Compose 动画时钟遵循系统动画设置。多行文字增减的 `animateContentSize` 仅作用于 `Surface` 内部的编辑区域，外层布局与 Surface 不裁剪阴影。使用 `TextFieldValue` 保存光标、选区和输入法组合状态，文字仍由原有 ViewModel 字符串草稿拥有。外部草稿清空时同步编辑值；颜文字替换 `selection.min..selection.max`，插入后光标移至末尾，不增补空格，超限整项拒绝。`ComposerState` 在视频页分别为评论和弹幕创建，并按视频 ID 与会话修订隔离；可保存状态只记录分类与选区偏移，正文继续来自 ViewModel，面板打开状态不恢复。旋转保留分类／选区，切换 Tab、打开样式、全屏和画中画关闭面板；只有当前活动页签可以拦截返回。

输入框保留固定的 4dp 阴影高度；独立发送按钮使用共享 `ShadowFilledIconButton` 的动态彩色阴影，禁用时无投影。按钮保留官方配色、禁用状态与点击反馈，不再叠加外层圆形 `Surface` 投影，详见[按钮动态阴影](#按钮动态阴影)。

`KaomojiPicker` 只接收分类、最近记录及事件回调。静态目录完整保留 Cerasus 的五类 268 项原始字符；网格按实际字体测量跨列，极长条目可横向滚动阅读。面板最高 300dp，并按可用高度扣除输入区实测高度；等待 IME 收起后显示，系统内边距沿用 `FloatingComposerLayout` 的单一入口，列表底部留白包含整个输入区与面板的实测高度。M3E motionScheme 驱动容器尺寸变化，颜色和排版继承 `KIRAKIRATheme`。

`KaomojiViewModel` 暴露应用级 `RecentKaomojiStore` 的 Flow。Store 在 IO 调度器上先读后串行处理插入事件，以独立 `kirakira_kaomoji.xml` 保存最多 24 项去重记录，只接受目录中存在的值；初始或损坏记录回退为空。最近记录不属于账号私有数据，不保存草稿，不加入备份白名单，也不请求网络。来源与许可见 [Cerasus 资源](../third_party/cerasus-icons/README.md)。
