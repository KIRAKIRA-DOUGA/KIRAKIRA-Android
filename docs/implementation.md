# 实现说明

[返回项目首页](../README.md) · [功能现状](features.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

本文说明当前实现及接入方式；开发约束统一维护在贡献指南。依赖版本及配置来源见[开发环境](development.md#开发环境)。

## 统一内容状态

评论与弹幕空状态通过 `ContentStatus.emptyTitle` 和 `emptyIconRes` 分别显示“暂无评论”与对话气泡、“暂无弹幕”与字幕图标；这些参数只影响空状态，加载和错误继续使用统一呈现。

[`ContentUnavailableView`](../app/src/main/java/moe/kirakira/ui/components/ContentUnavailableView.kt) 统一空内容、内容加载失败及未开放页面占位，`ContentStatus` 将 `ContentState` 的加载／错误／空状态映射到 `LoadingIndicator` 或该组件。错误说明通过 `ApiFailure.messageRes()` 本地化，业务重试仍由调用方回调执行。

- `PAGE` 用于有界页面空间，内容居中且不足时可滚动；`INLINE` 不创建内部滚动，适合 `LazyColumn`，调用方为无内容状态预留剩余视口高度。评论与弹幕扣除计数栏、工具栏及发送框占用，列表底部 Insets 随内容滚动。
- `MEDIA` 用于黑底图片与视频区域，组件集中管理黑底配色和紧凑间距；极短视口隐藏装饰图案，文字与按钮可滚动。宿主预留关闭、返回与全屏操作区域。
- `ContentUnavailableAction` 仅接收资源解析后的文案、回调和启用状态。主操作统一 `Button`，次操作统一 `TextButton`，纵向居中排列，宽度上限 280dp；页面不传入按钮样式或插槽。`onRetry` 自动生成统一文案的主操作，可通过 `retryEnabled` 禁用，与 `primaryAction` 互斥。
- 资料和统计读取失败在对应内容附近显示组件，不重复弹出 Snackbar；操作失败仍由原有表单、Snackbar 或会话弹窗处理。播放器提供独立 `onRetry` 并绑定 `PlaybackViewModel.play()`，避免失败重试走播放／暂停切换。

## 导航与状态管理

应用保持单 Activity、单 `:app` 模块。[KIRAKIRAApp](../app/src/main/java/moe/kirakira/KIRAKIRAApp.kt) 连接应用级状态和导航，[AppNavHost](../app/src/main/java/moe/kirakira/ui/navigation/AppNavHost.kt) 注册页面并处理进入、返回事件。主题、会话及认证状态使用 ViewModel，API 和会话存储由 `data/auth` Repository 隔离，通过构造参数注入依赖。

页面导航使用 Navigation 3，`rememberNavBackStack` 保存返回栈，[ActivityNavDisplay](../app/src/main/java/moe/kirakira/ui/navigation/ActivityNavDisplay.kt) 封装 `NavDisplay`，保留页面状态和生命周期；页面通过 `NavigationPage` 接入宿主。路由和宿主使用要求见[架构规范](../CONTRIBUTING.md#技术选型与架构)。

普通进入与返回复用 AOSP Activity 的横移和透明度参数，预测性返回采用手势与松手收尾两个阶段。源码版本、双页面 Scene、Navigation Event 接入、几何变换、遮罩、完成／取消处理及公开 API 适配差异统一维护在 [Android 转场说明](../third_party/android-motion/README.md)。

### 图片查看与导出

图片页通过 [ImageViewerNavigation](../app/src/main/java/moe/kirakira/ui/navigation/ImageViewerNavigation.kt) 将打开、关闭、预测返回三种转场写入官方 `NavDisplay` 元数据，由 `ActivityScene` 继承顶部 `NavEntry` 的元数据。不要对 `Scene.key` 做路由类型判断：这里的键来自 `NavEntry.contentKey`，是用于内容身份与状态恢复的字符串，并非路由对象。打开时采用淡入与 `KeepUntilTransitionsFinished`，保留原位的来源页直到转场结束；返回时来源页无位移、查看页淡出。图片返回处理器读取同一份元数据标记，普通页继续使用原 AOSP 转场。接入方式与 [Navigation 3 官方转场配置](https://developer.android.com/guide/navigation/navigation-3/animate-destinations) 一致。

资料头像与全屏查看器使用 Compose 官方 `SharedTransitionLayout` / `sharedBounds` 连接。共享键包括来源页与图片身份（例如 `profile/<UUID>/avatar`），避免不同用户的图片错误配对。图片页单独使用 Navigation 3 的可寻址预测返回进度；图片进出时来源页面保持原位，查看器直接覆盖其上，普通页面继续由 AOSP 动效宿主管理。共享边界、页面显隐、背景与控件显隐共用 [EmphasizedEasing](../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 的 Material 3 emphasized 曲线及 420ms 时长；Tab 点击也复用该曲线。共享边界插值时，图片由圆形头像逐渐展开成直角视口：采用 `RemeasureToBounds` 让图片在变化的宽高比中重新排版，避免全屏图片压缩时露出平直内容边缘。静止时由图片层裁剪，匹配过渡时仅由共享覆盖层裁剪；覆盖层圆角根据当前共享边界逐帧计算，关闭及预测返回时跟随实际收缩进度。固定全屏的黑色背景在共享覆盖层下方按查看页可见进度淡入淡出，不随图片边界位移。头像白色圆框留在资料页原位，不参与共享边界动画。返回时用 Telephoto 的当前内容几何反向补偿缩放，手势取消无需修改其内部缩放状态。来源不在组合中时仅淡入或淡出。用户关闭系统动画时，Compose 时长缩放统一生效。

[ViewerImage](../app/src/main/java/moe/kirakira/feature/imageviewer/ViewerImage.kt) 是可序列化的图片描述，支持资源 ID、可读 `content://` URI 和公开 HTTPS 图片 URL；调用方负责保留 URI 读取授权。查看 UI 位于 `ui/components/image/`，只接收图片模型、状态、回调与可选共享元素修饰符；导出由 `feature/imageviewer/` 负责。查看器使用全屏黑色背景，系统状态栏和导航栏保持透明并使用浅色图标；透明顶栏仅放深色圆形底衬的白色关闭按钮，确保白色图片上仍有足够对比度；转场禁用点击时保留相同配色，由整体淡出控制透明度。下载和复制位于避开系统导航栏的右下角 `HorizontalFloatingToolbar`。背景通过单独的覆盖层绘制，两组控件通过共享转场覆盖层悬浮在图片上方并淡入淡出。复用时将稳定的来源键同时用于缩略图的 `imageSharedBounds(key, viewer = false)` 与查看页的 `imageSharedBounds(key, viewer = true)`，并通过 `ImageViewerRoute` 打开页面。无可匹配来源时传入 `null` 键。

来源图片只在匹配的共享动画进行时隐藏，动画停止绘制覆盖层的同一帧恢复原位图片；`isMatchFound` 可能持续到查看器出栈销毁，不能单独用它控制头像显隐。头像圆框在来源页单独绘制于图片外沿，过渡期间始终保持原位；查看器不再创建对应的共享装饰层。圆形点击／水波纹层与共享图片并列，单独裁剪，不给共享图片的父容器再加圆形裁剪。

控件显隐由 [ImageViewerControlsState](../app/src/main/java/moe/kirakira/ui/components/image/ImageViewerControlsState.kt) 管理，查看 UI 接收 `controlsVisible`、`onToggleControls` 和 `onInteractionChange`。默认空闲时长为 3 秒，遵循 `AccessibilityManager.calculateRecommendedTimeoutMillis`；仅在页面 RESUMED、转场结束且无触摸／控件焦点／结果提示时计时，加载、错误、权限请求与文件操作时保留控件。界面根节点只观察触摸，不消费事件；单击使用 [Telephoto 的 `onClick`](https://saket.github.io/telephoto/zoomableimage/#click-listeners)，不叠加 `clickable` 抢占双击与缩放。顶栏和工具栏使用 Material 3 动效淡出，隐藏后移出点击和无障碍树，系统返回始终由导航宿主处理。

缩放使用 [Telephoto 0.19.0](https://saket.github.io/telephoto/zoomableimage/) 与其 Coil 3 适配。保存保留原始字节和 MIME 类型：Android 10+ 通过 `MediaStore.Images` 的 `IS_PENDING` 公开，Android 8–9 获得旧版写权限后写入公共 `Pictures/KIRAKIRA` 并扫描媒体。复制将原始字节写入专用缓存，通过 `FileProvider` 交给系统剪贴板，超过七天的复制缓存于下一次复制时清理。网络头像通过公开 HTTPS 来源接口加载和导出。

### 主界面切换与底栏动效

[MainScreen](../app/src/main/java/moe/kirakira/feature/main/MainScreen.kt) 将四个 Tab 作为主界面内的局部状态，未建立独立返回栈。Tab 切换复用[普通 Activity 转场](../third_party/android-motion/README.md#普通-activity-转场)的位移、时长、系统缓动与淡入淡出参数，按排列顺序决定方向，RTL 布局镜像处理；首页头像跳转“我”使用同一套动画。

底栏图标动画与页面切换独立：首页与“我”在 300ms 内从描边连续形变为官方 Filled 造型，并轻微收缩后回弹，取消选中时反向恢复；搜索轻摆并缩放回弹；关注在 440ms 内完成由内向外扩散并回弹。图标动画不使用透明度渐变，重复点击当前 Tab 不重播，首次显示与状态恢复直接呈现最终形态，快速切换从当前进度转向新状态，并遵循系统动画时长设置。

图标来源、路径对应、形变适配及搜索／关注的缩放和延迟参数统一见 [Material Symbols 说明](../third_party/material-symbols/README.md#底栏图标形变)。

## 视频页与资料页分页

资料页以外层 `LazyColumn` 承载资料信息、`stickyHeader` Tab 栏和固定为剩余视口高度的 `HorizontalPager`。分页内列表通过嵌套滚动优先滚走资料信息，回到列表顶部后再向下展开资料；背景跟随外层列表，各 Tab 保留独立列表位置。Tab 高度按实测值扣除，底部系统内边距仍放在分页列表的 `contentPadding` 中。

两页的 `PrimaryTabRow` 共用 [PagerTabIndicator](../app/src/main/java/moe/kirakira/ui/components/PagerTabIndicator.kt)，并关闭默认底部分隔线；视频页也不在 Tab 栏下方绘制容器阴影。在测量阶段读取 `currentPage + currentPageOffsetFraction`。参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，分别以 `sin(πt/2)` 和 `1−cos(πt/2)` 插值前缘、后缘，使其先伸长再收缩；RTL 下通过相对布局镜像。指示器直接跟随拖动、回弹和点击切页的实际进度，保持官方主 Tab 指示器的颜色与形状。

点击切页共用 [rememberTabChangeHandler](../app/src/main/java/moe/kirakira/ui/components/TabTransition.kt)，取消上一次点击启动的滚动任务后从当前 Pager 位置转向新目标。滚动与图片查看器复用 [EmphasizedEasing](../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 提供的 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration) 双段路径：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 使用 Compose `PathEasing` 兼容。动画快速推进后平缓收尾，不越过目标页；相邻页为 500ms，跨页按距离延长至最多 650ms。Tab 的选中状态统一使用 `currentPage`。

## 视频列表布局

[评论页](../app/src/main/java/moe/kirakira/feature/video/VideoCommentsPage.kt)与[弹幕条目](../app/src/main/java/moe/kirakira/feature/video/DanmakuListItem.kt)使用官方 `SegmentedListItem`，保留默认条目配色、首尾大圆角、内部小圆角和官方分段间距。

评论和弹幕使用 8dp 外边距和官方分段间距；评论复用 `HorizontalFloatingToolbar` 与 `CommentJumpDialog`，在右上角悬浮显示快速前后页定位和页码跳转；计数标题按 Toolbar 实测尺寸避让，窄屏和大字体自动上下排列。不实现本地排序与楼层跳转。底部系统内边距放入 LazyColumn 的 contentPadding，输入框使用 IME 内边距。

评论通过 `CommentListLoader` 保存连续页区间，继续使用服务端每页 20 条的 API。各页合并后按评论 ID 去重并保留所属页；`snapshotFlow` 在可见评论距离已加载区间边缘不超过 3 条时请求相邻页，同页请求去重，空页或首末页停止对应方向加载。上下加载具有独立状态和显式失败重试，不自动重复失败请求。列表使用稳定评论 key；向前插入时额外按旧可见评论及像素偏移保持锚点，避免顶部计数或状态条目成为锚点后引起跳动。翻页器页码来自逻辑滚动锚点之后的首条可见评论，忽略仅处于顶部 contentPadding 中的上一页尾项；自动加载不发出定位事件。跳转使用下一次测量定位，确认目标 key 已出现在新列表布局后才消费请求并恢复相邻页加载；显式跳转使用可消费的定位请求，已加载页直接定位，其他页成功后重建窗口。请求代次和账号版本共同拒绝过期响应，跳转、刷新及账号切换取消失效请求。

评论下拉刷新仅在第 1 页已加载且滚动处于顶部时启用（空列表同样允许）；中间页的上边缘只用于前页加载。刷新成功后重建第 1 页，失败保留原内容；局部赞踩只重读所属页，保留其他页和阅读位置，重读期间禁用该页互动。发布成功后根据最新总数读取末页并定位，回传评论已出现在列表时隐藏发布回显。首页、视频当前标签页、弹幕和作者资料继续使用 Material 3 `PullToRefreshBox`；共享包装器的 `enabled` 默认保持开启。加载与错误状态独立于现有内容，不使用演示数据回填，错误状态提供重试按钮。

## 视频数据与播放器

`PlaybackSettingsViewModel` 通过独立存储封装异步读取 `kirakira_settings` 的 `playback_auto_pip`（默认 true）与 `playback_autoplay`（默认 false），使用 SharedPreferences apply 异步落盘。应用级状态经导航传入视频页与播放设置页，不绑定账号。

`VideoPlayer` 接收播放状态、Media3 画面实例和事件回调，不持有 ViewModel。返回按钮和控制栏共享显隐状态与三秒计时器；暂停、结束、失败、进度交互及触摸探索阻止自动隐藏。控件使用主题效果动效，退出动画期间禁用交互并清除语义。控制层以黑色渐变遮罩衬托白色图标和文字，播放／暂停按钮居中，使用 45% 不透明度的黑色圆形容器和 Rounded FILL 1 白色图标，加载时同一 64dp 圆形容器内改为 48dp 白色 LoadingIndicator，不提供播放点击动作；底部不再使用主题 Surface。Slider 使用官方 `SliderDefaults.Thumb`（16dp 等宽高，保留默认按压／拖动形变），以两层 `SliderDefaults.Track` 显示缓冲和已播放位置；上层未播放轨道透明，底层只绘制、不添加交互或语义。两层使用相同圆头尺寸，保留官方定位手势与无障碍语义。`PlaybackViewModel` 在 Media3 事件及现有 500ms 进度轮询中读取 `bufferedPosition`，按有效时长限制范围；未知时长与播放器释放时显示零缓冲，不持久化缓冲位置。

`data/content/ContentRepository` 将私有 serialization DTO 映射为 `VideoSummary`、`VideoDetail`、`PublicProfile`、`VideoComment` 和 `DanmakuEntry`，UI 不持有 Cookie 或 token。资料映射拒绝非正 UID，以及响应中 UID 与目标不一致的结果，避免将异常资料归给目标用户。首页、视频、资料分别由 ViewModel 管理；视频与资料的 ViewModel 绑定 Navigation 3 条目。`VideoRoute(videoId)` 和 `ProfileRoute(uid)` 使用真实 ID，旧无 ID 的演示路由在恢复时移除。状态变更通过 SessionState.revision 取消旧账号工作；请求前取得账号快照、返回时再次核对 revision，旧响应不覆盖新页面。启动时内容加载等待本地会话恢复及 `INITIALIZE` 账号校验结束（含失败），期间手动刷新也不提前请求，避免本地恢复和校验完成两次发布 revision 导致列表清空重载、加载指示器闪回；正常账号切换仍取消并清空旧账号内容。明确 401 只清除发起请求账号的凭据，其他业务拒绝不会自动清空会话。

| 接口 | 用途 |
| --- | --- |
| GET `video/home` | 首页；无分页参数，目前服务端限制 100 条 |
| GET `video?videoId=` | 详情、分 P、作者、赞踩；登录读取可能由后端更新播放量和历史 |
| GET `video/user?uid=` | 作者作品 |
| GET `user/info?uid=`、`feed/stats?targetUid=` | 作者资料和统计 |
| POST `feed/following` / `feed/unfollowing` | 关注／取消关注 |
| POST `video/upvote` / `video/downvote`；DELETE 对应路径加 `/cancel` | 视频赞踩及撤销 |
| GET `video/comment?videoId=&page=&pageSize=20` | 按楼层升序分页评论 |
| POST `video/comment/emit` | 发布一级评论 |
| POST `video/comment/upvote` / `downvote`；DELETE 对应路径加 `/cancel` | 评论赞踩及撤销 |
| GET `video/danmaku?videoId=`；POST `video/danmaku/emit` | 公共弹幕列表和发布 |

`userDataBootstrapHint` 从登录或 `/user/self` 获取，仅放入加密 StoredAccount 的可选字段，旧 v1 账号库兼容；仅首页过滤请求发送 `uid`、`uuid` 和 `user-data-bootstrap-hint`，缺失时按游客读取。普通鉴权使用账号 token 快照，媒体与图片传输独立且无 Cookie。评论数量字段在空结果时可能省略，游客视频详情可能缺失赞踩计数，UI 显示未知而不是编造零。API 响应继续保留 1 MiB 上限；过大或非法响应显示错误，不无限读取。评论发布少于 20,000 字符；弹幕颜色使用服务端实际要求的不带井号的 `FFFFFF`，默认 `medium`、`rtl`、`enableRainbow=false`，时间单位为秒。

图片地址由 `core/image/DeliveryImage.kt` 统一解析。Cloudflare ID 追加到 Apple 端同款生产地址 `https://kirafile.com/cdn-cgi/imagedelivery/Gyz90amG54C4b_dtJiRpYg/`，缩略图使用宽度变体，全屏和导出使用 `f=auto`；兼容完整合法 HTTPS URL。图片配置本轮固定为生产分发，不随 API 地址自动猜测测试环境。

播放器使用 Media3 ExoPlayer、DASH/HLS、OkHttp data source、Compose `ContentFrame` 和 MediaSession。Media3 的画面组件封装了平台 Surface 的互操作，按钮和进度条采用项目 Material 3 控件，不使用 XML PlayerView。独立媒体客户端拒绝 HTTP 和 URL 凭据，清单及分片均不携带账号会话。页面条目持有播放器，SavedState 保留分 P 索引、播放毫秒数与禁止恢复自动播放的标记；重建时暂停。新页面在设置已加载、路由处于前台且有可播放内容时，可按设置自动准备媒体一次；手动操作、账号变化或离开页面会消耗自动播放资格。公开媒体读取允许播放器的标准恢复行为，API 写请求仍不自动重放。

MainActivity 负责平台画中画桥接和系统栏；窗口全屏状态统一参与系统栏外观与显隐计算，退出全屏、方向配置变化及重新获得窗口焦点时重新应用，视频页状态栏保持浅色图标；页面负责资格、来源矩形与播放器状态。画中画开启期间保留播放器，其他后台／页面退出路径释放。手动画中画资格与自动进入偏好独立：Android 12+ 将两者组合传给 `setAutoEnterEnabled`，旧系统在 `onUserLeaveHint` 检查自动进入偏好；比例限制在系统支持区间，Activity 声明 PiP 和相关屏幕配置处理。MediaSession 提供画中画播放控制，无前台服务或后台音频播放。全屏使用当前窗口并请求 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出时恢复此前方向策略；进入画中画暂时解除方向请求，展开后恢复全屏；返回手势先退出全屏，普通页面仍走原导航宿主。

## 主题实现

### 配色生成

手动主题使用 [MaterialKolor](https://github.com/jordond/MaterialKolor) 的 `rememberDynamicColorScheme` 生成全部语义颜色。该库维护 Google Material Color Utilities 的 Kotlin 移植与 Compose 适配，许可证为 MIT，底层 Material Color Utilities 为 Apache-2.0。

[ThemeColorAlgorithm](../app/src/main/java/moe/kirakira/ui/theme/ThemeColorAlgorithm.kt) 提供九种上游算法及独立的经典强调色方案，默认 `TonalSpot`。`TonalSpot`、`Neutral`、`Vibrant` 和 `Expressive` 使用 `SPEC_2025`，其余算法按上游支持范围使用 `SPEC_2021`。算法通过稳定枚举名保存，旧设置或未知名称回退到默认算法。

主题、预设色板、算法列表与自定义选色器统一使用 `rememberSeedColorScheme`。`Monochrome` 生成灰阶主题强调色与背景，错误等语义颜色保留必要区分。算法选择与应用使用 `MaterialExpressiveTheme` 是不同的设置。

[ClassicAccentColorScheme](../app/src/main/java/moe/kirakira/ui/theme/ClassicAccentColorScheme.kt) 为 `CLASSIC_ACCENT` 从 `Monochrome / SPEC_2021` 的灰阶角色构建经典强调色，生成主色容器、反色与固定色角色。经典方案通过 `LocalTonalElevationEnabled` 关闭色调高度叠加，`surfaceTint` 与 `surface` 同色，避免直接计算高度色时重新染色。原色保留、`onPrimary` 对比度阈值、组件配色接口与视觉取舍统一见[主题配色约束](../CONTRIBUTING.md#主题配色约束)。

### 选色与持久保存

[自定义选色对话框](../app/src/main/java/moe/kirakira/feature/settings/CustomColorDialog.kt)使用 [colorpicker-compose](https://github.com/skydoves/colorpicker-compose)（Apache-2.0）的 HSV 色盘与亮度滑条，封装在 Material 3 `AlertDialog` 中，补充亮度无障碍调节与 HEX 输入。它只增加 Compose 选色绘制与手势代码，不引入 View 互操作，许可证由 AboutLibraries 收集。

自定义色值与当前生效色值独立存储，同时保存预设／自定义的选择状态，避免自定义颜色恰好等于预设时错误标记选中项。旧设置以当前保存的色值初始化独立的自定义颜色。草稿确认与取消的约束见[贡献指南](../CONTRIBUTING.md#主题配色约束)。

预设色板复用官方 `ToggleButton` 和 `ToggleButtonDefaults.shapesFor` 的按压及选中动画，三色绘制随按钮形状一起裁剪。主题状态使用与现有 Lifecycle 同版本的 `lifecycle-runtime-compose` 进行生命周期感知收集。

MaterialKolor 增加颜色算法与 Compose 适配代码，不引入 View 组件库；两项配色依赖的实际 APK 增量需通过同构建配置比较，不以依赖包大小代替，也不因迁移默认认定体积缩小。

### 栏面阴影

[ThemeShadows](../app/src/main/java/moe/kirakira/ui/theme/ThemeShadows.kt) 统一提供应用栏阴影：开启时顶栏始终为 4dp，底部导航栏为 8dp，参考原有 [Material 标准高度](https://m1.material.io/material-design/elevation-shadows.html)。大标题展开或折叠不改变高度；阴影开关与主题配色及其色调高度策略分别管理。

## 认证状态

认证功能位于 [feature/auth](../app/src/main/java/moe/kirakira/feature/auth/)：

| 层次 | 职责 |
| --- | --- |
| `AuthScreen` / `AuthForm` / `LoginForm` | 展示登录、2FA、注册、密码找回步骤，上报操作 |
| `AuthPage` / `AuthNavigation` | 外层条目共享表单状态，内部步骤返回栈、转场、键盘、隐藏密码与成功返回事件 |
| `AuthViewModel` | 条目内表单、校验、异步提交、验证码冷却展示 |
| `SessionViewModel` | 应用级唯一 Repository、启动加载／资料刷新、切换／移除／登出 |
| `AuthRepository` | 认证编排、SHA-256、账号去重、冷却、会话事务和领域状态 |
| `AuthApi` / `SessionStore` | 私有 DTO 与 HTTP 协议／加密原子存储 |

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

会话 UI 状态新增 `SessionOperation(type, targetUuid)`，区分初始化、切换、移除与本机重置。操作串行执行并在写盘期间保留类型与目标；成功提交之前继续选中原账号。账号行采用居中对齐的布局，游客平时仅显示名称，普通账号显示名称与副标题，长文字单行省略；操作状态复用副标题行，loading、单选与删除按钮共用 48dp 尾部槽位，列表不插入顶部加载项或空白状态行。“添加账号”独立成组。错误携带目标 UUID，重试闭包绑定原操作。游客切换、移除当前账号或登出之后尽力调用 `clearCredentialState`，不删除提供者密码；清理失败不撤销本地操作。

参考：[官方密码接入](https://developer.android.com/identity/passwords)、[Compose Autofill](https://developer.android.com/develop/ui/compose/text/autofill)、[Credentials 版本记录](https://developer.android.com/jetpack/androidx/releases/credentials)。

## API 与会话

### 接口契约

流程对照同级 Cerasus 项目的 `components/Login/LoginWindow.vue`、`composables/api/User/UserController.ts`、`utils/hash.ts`；端点与字段核对同级 Rosales 的 `src/route/router.ts`、`src/controller/UserControllerDto.ts`、`src/service/UserService.ts`。这是本次仓库源码核对，不表示已执行生产账号联调。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `user/checkUserHave2FAByEmail` | query `email`；`success`, `have2FA`, `type` | 判断无验证器／email／totp |
| POST `user/login` | `email`, `passwordHash`, `clientOtp` 或 `verificationCode`；`success`, `UUID`, `uid`, `token` | 密码及可选 2FA 登录 |
| POST `user/self` | 请求 Cookie `uuid`, `uid`, `token`；`success`, `result` | 校验会话并获取自己资料 |
| GET `user/checkUsername` | query `username`；`isAvailableUsername` | 用户名可用性 |
| GET `user/existsCheck` | query `email`；`exists` | 邮箱查重 |
| POST `user/checkInvitationCode` | `invitationCode`；`isAvailableInvitationCode` | 邀请码检查 |
| POST `user/sendGeneralEmailVerificationCode` | `email`, `clientLanguage`, `mailTemplate`, `exclusiveBusinessName` | 验证码发送 |
| POST `user/registering` | `email`, `passwordHash`, `passwordHint`, `verificationCode`, `invitationCode`, `username`, `userNickname` | 注册并获取会话 |
| POST `user/forgot/password` | `email`, `newPasswordHash`, `verificationCode` | 设置新密码 |

密码传输格式与 Cerasus `generateHash` 一致：UTF-8 原密码的 SHA-256，小写 64 位十六进制，不能 trim 原密码或把 DTO 中旧的 bcrypt 注释当作契约。摘要依然等价于敏感凭据，只在内存保留，不得记录。

邮件模板／业务名分别是 `SendLoginVerificationCode` / `login`、`SendRegistrationVerificationCode` / `registration`、`SendResetPasswordVerificationCode` / `forgot-password`；语言为 `zh-Hans-CN` 或 `en-US`。Rosales 的冷却按邮箱跨业务共用，Repository 使用单调时钟记录 60 秒截止值并串行发送；服务端冷却或每日发送／校验上限优先于 `success`。邮箱验证码为六位数字；TOTP 输入允许备用／恢复码。注册昵称可选，用户名字符策略与 Cerasus `assets/pomsky/username.pom` 对齐，表单长度上限为 20。

### 账号设置管理

`data/settings/AccountSettingsRepository` 复用共享 API 客户端与唯一 AuthRepository，屏蔽规则和邀请码通过领域模型交给条目级 ViewModel。导航为 `BlockingOverviewRoute`、带 `RuleCategory` 的 `RuleManagementRoute` 与 `InvitationsRoute`，使用现有条目装饰器、`NavigationPage` 和认证返回流程；不增加依赖或持久化规则副本。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `block/list` | `type` = `block` / `hide` / `tag` / `keyword` / `regex`，`page`, `pageSize`；`result`, `blocklistCount` | 总览数量与规则分页 |
| POST `block/user`、DELETE `block/delete/user` | `blockUid` | 屏蔽／解除用户 |
| POST `block/hideuser`、DELETE `block/delete/hideuser` | `hideUid` | 隐藏／显示用户 |
| POST `block/tag`、DELETE `block/delete/tag` | `tagId` | 标签规则 |
| POST `block/keyword`、DELETE `block/delete/keyword` | `blockKeyword` | 关键词规则 |
| POST `block/regex`、DELETE `block/delete/regex` | `blockRegex`；新增还检查 `unsafeRegex` | 正则规则 |
| GET `user/info` | `uid` | 添加用户前复用公开资料查询 |
| GET `video/tag/search` | `tagName`；`result` | 选择已有标签 |
| GET `user/myInvitationCode` | Cookie；`invitationCodeResult` | 当前账号邀请码 |
| POST `user/createInvitationCode` | 空 JSON 对象及 Cookie；`isCoolingDown`, `invitationCodeResult` | 生成邀请码 |

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

### 可选的可折叠大标题栏

共享组件为 [`CollapsibleTopAppBar`](../app/src/main/java/moe/kirakira/ui/components/CollapsibleTopAppBar.kt)，适用场景、状态与 Insets 约束见[贡献指南](../CONTRIBUTING.md#可选的可折叠大标题栏)。

`rememberCollapsibleTopAppBarScrollBehavior()` 默认首次折叠，上滑收起，内容到顶后下拉展开；需要首次展开时传入 `initialCollapsed = false`，此参数不会覆盖已恢复的状态。同一份 `scrollBehavior` 传给顶栏，并将其 `nestedScrollConnection` 接到父容器，才能联动列表手势。

顶栏参数包括资源解析后的 `title`、`onBack` 和可选的 `actions` 插槽，切换账户页的“编辑／完成”使用后者。组件统一返回图标和配色，页面管理内容与 Snackbar。下例通过 `fillMaxSize()` 放在 `verticalScroll()` 前，让短内容的空白区域也能接收下拉手势；底部内边距放在滚动内容中。

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

## 启动器图标

Manifest 的 `icon` 与 `roundIcon` 分别引用 `mipmap-anydpi` 中的 `ic_launcher.xml` 与 `ic_launcher_round.xml`，两者共用品牌粉色背景和白色矢量前景，并以同一前景提供主题图标的 `monochrome` 层；外轮廓由启动器裁切。

项目最低支持 API 27，已覆盖自适应图标所需的 API 26，因此仅维护自适应 XML 与矢量图层，不保留面向旧版 Android 的各密度 launcher WebP。更新品牌图标时修改对应图层；若将来降低最低支持版本至 API 25 或以下，需补齐匹配品牌设计的传统图标，并为自适应 XML 添加 `v26` 限定符。

## 启动动画

[MainActivity](../app/src/main/java/moe/kirakira/MainActivity.kt) 与 [SplashRevealController](../app/src/main/java/moe/kirakira/ui/splash/SplashRevealController.kt) 协调系统 Splash 退出和 Compose 覆盖层，[SplashReveal](../app/src/main/java/moe/kirakira/ui/splash/SplashReveal.kt) 绘制图标遮罩。

启动屏浅色使用品牌粉色 `#F06E8E` 背景和白色图标，深色使用 `#121212` 背景和品牌粉色图标，始终跟随系统浅深色。应用内明暗模式只更新 Compose 主题，不向系统写入应用夜间模式。

系统 Splash 保留到主题设置读取完成、Compose 页面完成首次布局后才允许退出，避免读取完成与页面布局之间短暂露出窗口背景；关闭动画或 Activity 重建时也遵循这一就绪条件。仅在没有保存状态的新 Activity 收到系统 Splash 退出回调时播放：

1. 整个图标使用主题的 M3 Expressive `fastSpatialSpec` 收缩至 92%，首次到达目标时停止。
2. 以星星内部为支点，用 600ms 的强 S 型贝塞尔曲线 `(0.85, 0, 0.15, 1)` 展开，形成慢起步、快速冲开、缓收尾的节奏。放大倍率采用对数插值，保留起步阶段的图标轮廓；展开曲线单调递增，避免重新遮挡页面。
3. 展开进度达到 2% 时，图标填色使用 `fastEffectsSpec` 淡出，页面从星星与线条轮廓内显露；进度达到 92% 时剩余背景才开始淡出，避免过早淡化掩盖遮罩运动。
4. 图标填色消失且遮罩完全揭开后立即结束，不等待不可见的背景淡出尾段。

动画复用矢量路径直接绘制，页面自身不缩放。Compose 覆盖层绘制首帧后才移除系统启动屏，系统栏在结束后恢复当前页面样式。全部动画遵循系统动画时长设置，不叠加额外时长倍率；关闭系统动画时直接显示页面。

后台返回、旋转、页面恢复和应用内主题切换不重播。离开前台、销毁或窗口尺寸变化时清理过渡；过渡期间屏蔽底层触摸与无障碍焦点，不拦截系统返回。

### 弹幕发送样式

`DanmakuStyle` 领域模型包含 RGB 颜色、字号、模式和彩虹开关；`VideoViewModel` 以只读 Flow 暴露当前视频的样式，通过更新事件接收选择。样式只保留在条目 ViewModel 内，账号修订变化时与草稿一起重置。发送捕获正文、时间与样式快照，Repository 将颜色转换为不带 `#` 的六位大写 RGB，并发送 Rosales 的 `color`、`fontSize`、`mode`、`enableRainbow` 字段。提交期间禁用编辑，失败保留草稿与样式，成功仅清空正文。

弹幕发送栏复用 `ContentComposer` 的可选尾部插槽。样式使用官方 ModalBottomSheet，自定义选色在同一面板内进入子页，复用已有 HSV 选色依赖；确认才应用颜色。面板打开时清除输入焦点并隐藏键盘。预览使用 14／20／28sp，彩虹描边参考 Cerasus 的粉蓝渐变；关闭系统动画时静态显示。预览不参与真实播放器渲染，列表与播放器能力保持原有范围。


### 评论与弹幕颜文字

`ContentComposer` 以草稿是否为空切换单行药丸与两行编辑布局：空草稿时操作按钮放在输入框尾部，非空时放在下方操作栏；输入框保持在同一组合位置，避免首次输入时丢失焦点或输入法组合状态。使用 `TextFieldValue` 保存光标、选区和输入法组合状态，文字仍由原有 ViewModel 字符串草稿拥有。外部草稿清空时同步编辑值；颜文字替换 `selection.min..selection.max`，插入后光标移至末尾，不增补空格，超限整项拒绝。`ComposerState` 在视频页分别为评论和弹幕创建，并按视频 ID 与会话修订隔离；可保存状态只记录分类与选区偏移，正文继续来自 ViewModel，面板打开状态不恢复。旋转保留分类／选区，切换 Tab、打开样式、全屏和画中画关闭面板；只有当前活动页签可以拦截返回。

`KaomojiPicker` 只接收分类、最近记录及事件回调。静态目录完整保留 Cerasus 的五类 268 项原始字符；网格按实际字体测量跨列，极长条目可横向滚动阅读。面板最高 300dp，并按可用高度扣除输入区实测高度；等待 IME 收起后显示，系统内边距沿用 `FloatingComposerLayout` 的单一入口，列表底部留白包含整个输入区与面板的实测高度。M3E motionScheme 驱动容器尺寸变化，颜色和排版继承 `KIRAKIRATheme`。

`KaomojiViewModel` 暴露应用级 `RecentKaomojiStore` 的 Flow。Store 在 IO 调度器上先读后串行处理插入事件，以独立 `kirakira_kaomoji.xml` 保存最多 24 项去重记录，只接受目录中存在的值；初始或损坏记录回退为空。最近记录不属于账号私有数据，不保存草稿，不加入备份白名单，也不请求网络。来源与许可见 [Cerasus 资源](../third_party/cerasus-icons/README.md)。
