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

普通进入与返回复用 AOSP Activity 的横移和透明度参数，预测性返回采用手势与松手收尾两个阶段。源码版本、双页面 Scene、Navigation Event 接入、几何变换、遮罩、完成／取消处理及公开 API 适配差异统一维护在 [Android 转场说明](../third_party/android-motion/README.md)。

主界面标签切换保留同一套横移与淡入淡出参数。栏面及连接列表继续使用平台 elevation 投影；毛玻璃固定使用 Haze 背景采样，规避已在 Android 17 模拟器上复现的原生 backdrop、页面 alpha 与 elevation 合成异常。底部胶囊导航栏位于标签页转场之外。

### 图片查看与导出

图片页通过 [ImageViewerNavigation](../app/src/main/java/moe/kirakira/ui/navigation/ImageViewerNavigation.kt) 将打开、关闭、预测返回三种转场写入官方 `NavDisplay` 元数据，由 `ActivityScene` 继承顶部 `NavEntry` 的元数据。不要对 `Scene.key` 做路由类型判断：这里的键来自 `NavEntry.contentKey`，是用于内容身份与状态恢复的字符串，并非路由对象。打开时采用淡入与 `KeepUntilTransitionsFinished`，保留原位的来源页直到转场结束；返回时来源页无位移、查看页淡出。图片返回处理器读取同一份元数据标记，普通页继续使用原 AOSP 转场。接入方式与 [Navigation 3 官方转场配置](https://developer.android.com/guide/navigation/navigation-3/animate-destinations) 一致。

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

资料页以外层 `LazyColumn` 承载资料信息、`stickyHeader` Tab 栏和固定为剩余视口高度的 `HorizontalPager`。分页内列表通过嵌套滚动优先滚走资料信息，回到列表顶部后再向下展开资料；背景跟随外层列表，各 Tab 保留独立列表位置。Tab 高度按实测值扣除，底部系统内边距仍放在分页列表的 `contentPadding` 中。

资料页按昵称文字的实际底部位置与外层列表视口顶部判断可见性；昵称完全滚出正文后，顶栏显示单行昵称，超长省略，昵称重新可见时隐藏顶栏标题。资料头被列表回收后仍通过首个可见条目保持顶栏昵称显示。

两页的 `PrimaryTabRow` 共用 [PagerTabIndicator](../app/src/main/java/moe/kirakira/ui/components/PagerTabIndicator.kt)，并关闭默认底部分隔线。视频页通过 `VideoScreen` 的 `playerContent` 插槽将播放器与 Tab 放入同一个顶部容器，由容器统一应用 `bottomEdgeShadow()` 和绘制层级，分页内容保持独立；全屏与画中画仅显示播放器。资料页保留吸顶布局，Tab 栏也使用 `bottomEdgeShadow()`，消除上沿向资料头投影。在测量阶段读取 `currentPage + currentPageOffsetFraction`。参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，分别以 `sin(πt/2)` 和 `1−cos(πt/2)` 插值前缘、后缘，使其先伸长再收缩；RTL 下通过相对布局镜像。指示器直接跟随拖动、回弹和点击切页的实际进度，保留 Compose 官方主 Tab 指示器的默认高度与主题颜色；形状显式采用 Material Components 主 Tab 的上圆下平样式，顶部左右圆角为 3dp、底部左右为直角，底边贴齐 Tab 栏底部，不使用 Compose 默认的完整胶囊形状。

点击切页共用 [rememberTabChangeHandler](../app/src/main/java/moe/kirakira/ui/components/TabTransition.kt)，取消上一次点击启动的滚动任务后从当前 Pager 位置转向新目标。滚动与图片查看器复用 [EmphasizedEasing](../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 提供的 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration) 双段路径：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 使用 Compose `PathEasing` 兼容。动画快速推进后平缓收尾，不越过目标页；相邻页为 500ms，跨页按距离延长至最多 650ms。Tab 的选中状态统一使用 `currentPage`。

## 视频列表布局

[评论页](../app/src/main/java/moe/kirakira/feature/video/VideoCommentsPage.kt)与[弹幕条目](../app/src/main/java/moe/kirakira/feature/video/DanmakuListItem.kt)使用透明背景的普通 `Row`／`Column` 布局，不使用分段背景、首尾圆角或分段间距，也不添加卡片或分割线；保留条目内边距、评论头像与操作按钮，以及弹幕左侧时间和右侧正文的对齐。

视频简介、评论和弹幕三个标签页不指定页面背景，保持透明并显示外层 `Scaffold` 默认的 `MaterialTheme.colorScheme.surface` 背景；输入框等独立组件保留各自的容器色。评论和弹幕使用 8dp 外边距；评论复用 `HorizontalFloatingToolbar` 与 `CommentJumpDialog`，在右上角悬浮显示快速前后页定位和页码跳转；计数标题按 Toolbar 实测尺寸避让，窄屏和大字体自动上下排列。不实现本地排序与楼层跳转。底部系统内边距放入 LazyColumn 的 contentPadding，输入框使用 IME 内边距。

评论通过 `CommentListLoader` 保存连续页区间，继续使用服务端每页 20 条的 API。各页合并后按评论 ID 去重并保留所属页；`snapshotFlow` 在可见评论距离已加载区间边缘不超过 3 条时请求相邻页，同页请求去重，空页或首末页停止对应方向加载。上下加载具有独立状态和显式失败重试，不自动重复失败请求。列表使用稳定评论 key；向前插入时额外按旧可见评论及像素偏移保持锚点，避免顶部计数或状态条目成为锚点后引起跳动。翻页器页码来自逻辑滚动锚点之后的首条可见评论，忽略仅处于顶部 contentPadding 中的上一页尾项；自动加载不发出定位事件。跳转使用下一次测量定位，确认目标 key 已出现在新列表布局后才消费请求并恢复相邻页加载；显式跳转使用可消费的定位请求，已加载页直接定位，其他页成功后重建窗口。请求代次和账号版本共同拒绝过期响应，跳转、刷新及账号切换取消失效请求。

评论下拉刷新仅在第 1 页已加载且滚动处于顶部时启用（空列表同样允许）；中间页的上边缘只用于前页加载。刷新成功后重建第 1 页，失败保留原内容；局部赞踩只重读所属页，保留其他页和阅读位置，重读期间禁用该页互动。发布成功后根据最新总数读取末页并定位，回传评论已出现在列表时隐藏发布回显。首页、视频当前标签页、弹幕和作者资料继续使用 Material 3 `PullToRefreshBox`；共享包装器的 `enabled` 默认保持开启。加载与错误状态独立于现有内容，不使用演示数据回填，错误状态提供重试按钮。

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
| POST `feed/following` / `feed/unfollowing`                      | 关注／取消关注                        |
| POST `video/upvote` / `video/downvote`；DELETE 对应路径加 `/cancel`   | 视频赞踩及撤销                        |
| GET `video/comment?videoId=&page=&pageSize=20`                  | 按楼层升序分页评论                      |
| POST `video/comment/emit`                                       | 发布一级评论                         |
| POST `video/comment/upvote` / `downvote`；DELETE 对应路径加 `/cancel` | 评论赞踩及撤销                        |
| GET `video/danmaku?videoId=`；POST `video/danmaku/emit`          | 公共弹幕列表和发布                      |

`userDataBootstrapHint` 从登录或 `/user/self` 获取，仅放入加密 StoredAccount 的可选字段，旧 v1 账号库兼容；仅首页过滤请求发送 `uid`、`uuid` 和 `user-data-bootstrap-hint`，缺失时按游客读取。普通鉴权使用账号 token 快照，媒体与图片传输独立且无 Cookie。评论数量字段在空结果时可能省略，游客视频详情可能缺失赞踩计数，UI 显示未知而不是编造零。API 响应继续保留 1 MiB 上限；过大或非法响应显示错误，不无限读取。评论发布少于 20,000 字符；弹幕颜色使用服务端实际要求的不带井号的 `FFFFFF`，默认 `medium`、`rtl`、`enableRainbow=false`，时间单位为秒。

图片地址由 `core/image/DeliveryImage.kt` 统一解析。Cloudflare ID 追加到 Apple 端同款生产地址 `https://kirafile.com/cdn-cgi/imagedelivery/Gyz90amG54C4b_dtJiRpYg/`，缩略图使用宽度变体，全屏和导出使用 `f=auto`；兼容完整合法 HTTPS URL。图片配置本轮固定为生产分发，不随 API 地址自动猜测测试环境。

播放器使用 Media3 ExoPlayer、DASH/HLS、OkHttp data source、Compose `ContentFrame` 和 MediaSession。Media3 的画面组件封装了平台 Surface 的互操作，按钮和进度条采用项目 Material 3 控件，不使用 XML PlayerView。独立媒体客户端拒绝 HTTP 和 URL 凭据，清单及分片均不携带账号会话。导航宿主持有共享播放会话，SavedState 保留视频 ID、分 P 索引、播放毫秒数与禁止恢复自动播放的标记；进程重建时暂停，不恢复小窗。新页面在设置已加载、路由处于前台且有可播放内容时，可按设置自动准备媒体一次；手动操作、账号变化或离开页面会消耗自动播放资格。公开媒体读取允许播放器的标准恢复行为，API 写请求仍不自动重放。

播放器控制层的显隐独立于控件业务可用状态：`AnimatedVisibility` 使用主题淡入淡出动效，隐藏时按钮、开关与进度条保持原有配色，退出完成后移出组合；`enabled` 仅表达媒体、轨道或页面等实际可用条件。画面显隐点击由播放器与控件的共同父容器处理，接收未被按钮、进度拖动或滚动消费的轻触，避免全屏滚动控件布局挡住独立背景点击层。开始隐藏时在控制层上方放置独立的画面点击层，点击只恢复控制层并重置计时，不触发底下控件。退出期间清除控制层语义、阻止焦点进入并释放该层已有焦点，操作回调再次检查显隐状态，避免未结束的交互提交操作。暂停与播放结束不再强制显示控件，均支持轻触显隐与三秒自动隐藏；缓冲、错误、进度拖动、设置面板和 TalkBack 触摸探索期间保持显示。

覆盖层遮罩在上下边缘分别渐隐，中部透明；控件默认使用白色，已播放轨道及非 1× 的倍速入口沿用主题强调色。返回与全屏入口使用官方 FilledIconButton；图标容器与画质／倍速按钮均使用 60% 不透明度黑色，禁用时为 30%，不添加阴影。`PlayerSettingsButtons` 使用半透明黑色背景的官方 TextButton 与同一小号尺寸对应的形状、内边距、文字及图标 API，外围按钮统一为官方小号 40dp 容器高度，图标统一使用 IconButtonDefaults.smallIconSize 的 24dp 尺寸，与标准 TopBar 一致；触摸目标继续由官方组件保证。倍速入口在 1× 时使用圆形 FilledIconButton，不显示数值；非 1× 时使用与画质等高的图标文字按钮并改为主题 primary。AnimatedContent 以实际显示的倍率字符串（1× 对应空串）为目标，所有倍率变化都触发内容淡入／淡出和容器尺寸过渡，尺寸使用主题 fastSpatialSpec 弹簧，颜色与透明度使用 fastEffectsSpec；因此 1.5× 到 2× 等非默认倍率间也有动画，退出内容保留原倍率，不闪出 1×。首次显示直接采用当前状态，遵循系统动画时长设置。顶部使用透明官方 TopAppBar，将返回放在 navigationIcon、画质与倍速放在 actions，复用默认栏高、内容边距和垂直对齐；actions 内部通过 FlowRow 换行，底部时间沿用主题 labelMedium 字体。私有 `PlayerControlsLayout` 使用 SubcomposeLayout 先测量顶部、时间、弹幕／全屏操作与 36dp 高度进度布局区，将 64dp 播放容器定位到视频画面的几何中心，图标与缓冲指示器均为 48dp；全屏时补偿不对称 safeDrawing Insets 对布局中心的偏移。根据实测控件矩形检查居中按钮是否重叠，仅在发生重叠或越界时将播放／缓冲控件以 48dp 容器排入底部操作区。底部按实测宽度换行，极短窗口允许整个控件布局纵向滚动，避免控件互相覆盖。顶部位置由官方 TopAppBar 管理，底部采用进度在上、操作在下的两行布局：36dp 进度布局区紧接 48dp 最小高度的操作行，不叠加 TopBar 行高的额外留白；操作行距播放器安全视口底边保留 4dp 留白；4dp 轨道与 16dp 手柄在各自内容插槽中同步向下偏移 8dp，使可见内容靠近操作行，36dp 进度布局区与下方操作行的测量及点击区域仍按顺序排列，不通过重叠布局缩短间距。中央按钮碰撞检查使用包含该视觉偏移的进度轨道／16dp 手柄实际可见高度，以及底部各操作的实测矩形；全屏操作尾部为 4dp，保持与顶栏最右侧图标按钮同列，组内间隔为 8dp。时间起点为 16dp，进度条扣除官方手柄半径后设置外边距，使轨道端点与 16dp 内容边界对齐；弹幕 Switch 保留官方尺寸和状态动效，轨道在开关两种状态下均为 60% 不透明度黑色并带半透明白色描边，开启时使用白色滑块与黑色图标，关闭时滑块降低不透明度并保留划线提示，禁用状态同步降低对比度。全屏由控件层避让 safeDrawing Insets，顶栏的 windowInsets 为零以免重复避让，遮罩仍覆盖整个画面。播放形变状态、弹幕划线状态和进度拖动状态保留在自适应布局之外，重排不重播动效或清空拖动预览；错误态单独预留顶部返回与全屏工具栏，继续使用统一 MEDIA 错误组件。

播放／暂停图标使用 `AnimatedPlaybackIcon` 的路径形变，两种切换均顺时针旋转 90°，采用主题的 Material 3 Expressive `fastSpatialSpec` 弹簧，与划线图标共用动效曲线，不做透明度切换。路径插值与旋转保留超过 1 的弹簧进度，使形变和旋转略微超过目标后回弹。目标路径反向补偿 90°，结束后仍为正向 Material Symbols Rounded 图标，并归一化内部坐标。连续切换从当前可见轮廓继续向最新状态顺时针形变，不排队或倒放。动画状态保留在加载与控制层显隐分支之外，首次显示直接采用当前播放状态，恢复控制层不重播；使用 Compose 动画时钟遵循系统动画时长设置。绘制缓存复用，两种尺寸模式共用组件，颜色及无障碍描述继承原按钮。 图标及操作描述由 `showPauseIcon` 驱动：Media3 `playWhenReady` 为真且未结束、未报错时显示暂停图标，缓冲不会使其切换为播放图标；暂停、播放结束和释放后显示播放图标。`playing` 继续表达 `isPlaying`，用于原有控制层及画中画行为。图标随目标状态变化播放动画，不再使用点击计数或加载结束补播逻辑；Material 2 圆角圆形加载器仍用于缓冲展示，其间图标动画状态在外层继续更新。

画质选项由 `PlaybackViewModel` 在 `EVENT_TRACKS_CHANGED` 中读取 `currentTracks`，仅包含解码支持且高度有效的视频轨道，同高度去重并降序排列；同高度候选优先当前视频组，再按码率选择。画质领域选项同时携带该候选轨道的高度与有效码率，确保显示码率与手动选择一致；UI 将 bit/s 除以 1000 并四舍五入，以 Kbps 显示在行尾，缺失或非正数时不显示。手动模式通过 `TrackSelectionOverride` 覆盖视频轨道，自动模式仅清理视频覆盖，不改音轨和播放位置。每次准备新媒体重新匹配首选高度；未匹配到时显示自动而不改持久偏好。实际清晰度从 `videoFormat` 获取，并随进度轮询更新，避免将自适应组的多个选中轨道误认为当前画质；释放时清空轨道状态。

`PlaybackSettings` 增加 `autoQuality` 和 `preferredVideoHeight`，由既有应用级设置存储保存为 `playback_auto_quality` 与 `playback_video_height`，默认自动且没有首选高度；UI 通过导航宿主回调更新。倍率、连续调速、保持音调由导航宿主 `PlaybackViewModel` 管理，以非敏感标量存入 SavedState；重建播放器时应用 `PlaybackParameters(speed, pitch)`，保持音调时 pitch 为 1，否则为 speed。无级值限制在 0.25–4 并保留两位小数，有级模式吸附既定倍率。

`PlayerSettingsSheet` 使用官方 `ModalBottomSheet`，宽度上限 640dp，只允许展开和隐藏状态；内容滚动并承载底部安全内边距。画质单选及倍速开关使用官方分段列表；「连续调速」与「保持音调」沿用设置页普通开关规则，以 onClick 重载切换状态并提供 Switch 角色与 toggleableState 语义，尾部 Switch 不独立处理点击，整行背景和圆角不随选中状态变化。倍速滑杆以 log2 映射 -2–2，默认 1× 位于中心，并为无障碍有级调整提供相邻倍率操作。面板局部状态参与控制栏显隐计时，关闭后重新计时；全屏、画中画、失去活动状态或播放错误时关闭，不持久化面板打开状态。

`PlaybackSpeedGauge(speed, playing, modifier)` 使用 Compose Canvas 绘制刻度与指针、Text 显示倍率读数，只消费现有 `PlayerUiState`，不持有播放器或修改业务状态。`playing` 来自 Media3 `isPlaying`，实际倍率为 `if (playing) speed else 0f`，不使用缓冲期间仍为真的 `showPauseIcon`。正倍率以 `180° + 180° × (log2(speed) + 2) / 4` 映射 0.25×–4×，1× 为 270°；零刻度单独设在 150°，为零刻度与 0.25× 之间保留足够弧长，不计算 `log2(0)`。`animateFloatAsState` 使用主题 `fastSpatialSpec`，首次直接采用当前角度，快速切换从当前动画位置继续，绘制角度限制在 150°–360°。指针与大刻度复用 `primary`，码表和滑杆直接使用 `SliderDefaults.colors()` 默认颜色：活动轨道与非活动圆点为 `primary`，非活动轨道与活动圆点为 `secondaryContainer`；经典强调色在主题层生成带强调色调的次要容器；刻度文字为 `onSurfaceVariant`。布局最大宽度 320dp，表盘几何以宽度／1.7 为基准高度，字体实测尺寸参与半径与标签位置计算；标签与弧线外缘留出 8dp 间隔，先保留 0、0.25、1、4 标签，仅在不重叠时显示 0.5、2。读数靠近轴心下方，以主题 `headlineLarge`、Medium 字重和 `onSurface` 显示；按 `0.25×`、`1.25×`、`3.99×` 等宽读数预留固定避让空间，避免倍率变化推动滑杆，暂停指针至少距读数 24dp，并根据固定字体高度增加布局高度，保持下方滑杆位置与读数不重叠。中英文码表无障碍描述报告实际倍率，Text 与滑杆表达设定倍率，不受指针插值或归零影响。

码表指针参考 Expressive 时钟的圆头短杆，以缓存的闭合路径绘制，中心端与外端宽度比例为 4:3，长度为轨道中心半径的 72%，两端保持圆润；中心端半宽限制在 4dp–6dp，避免窄屏或大字体下过细，同色圆轴半径比中心端半宽多 1dp，随指针一起适配。外弧为 8dp 宽的填充环形路径，断口使用 2dp 圆角，避免分段形成独立胶囊。0、0.25、0.5、1、2、4 为大刻度，以 2dp 宽、12dp 长的圆头径向短杆绘制，统一使用主色。轨道按大刻度分段，根据刻度半宽和 3dp 间隔计算角度退让；路径圆角随短弧长度收缩，极短区间空间不足时省略弧段及圆点，避免重叠。每段弧线中心放置一个小圆点，不按不均匀的快捷倍率分布，尺寸复用 `SliderDefaults.TickSize`，活动圆点使用 `activeTickColor`，其余使用 `inactiveTickColor`。表盘半径扣除轨道半宽，为外缘和标签保留空间；文字仅显示倍率数字，单位由读数表达。设计理由见 [Material Symbols](../third_party/material-symbols/README.md#播放器控制图标)。

`AppNavHost` 持有共享 `PlaybackViewModel`，视频页、小窗与系统画中画使用同一 ExoPlayer／MediaSession。视频页只在活动且匹配当前视频时绑定 `ContentFrame`；小窗位于导航内容上层，系统画中画从小窗进入时切换为全窗口视频。同一时刻仅一个画面绑定。导航完成后根据 `showPauseIcon`（含缓冲待播放）决定是否保留应用内小窗；预测性返回取消不修改模式，返回全屏先退出全屏。回到视频页重新读取详情时，同分 P 的媒体 URL 更新不打断当前播放。关闭小窗、账号修订变化和普通后台路径释放会话；旋转保留 ViewModel，进程恢复只读取非敏感标量并保持暂停，不保存 URL 或凭据。

`PlaybackHost` 统一管理 Activity 生命周期、屏幕常亮、系统画中画资格和来源矩形；小窗位置以可用区域的横纵比例保存，拖动结束贴左右边缘，布局用 safeDrawing 与 IME Insets 并扣除实测主界面底栏高度。MainActivity 负责平台画中画桥接和系统栏，Android 12+ 使用 `setAutoEnterEnabled`，Android 8–11 在 `onUserLeaveHint` 检查应用外开关与播放资格；比例限制在系统支持区间。不新增悬浮窗权限、前台服务或后台音频播放。全屏使用 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出恢复此前方向策略；进入系统画中画暂时解除方向请求，展开后恢复全屏。播放器弹幕开关继续使用官方 Switch 和 Cerasus 图标，暂停时保留，手动画中画按钮已移除。

### 历史记录与续播

`HistoryHostViewModel` 在导航宿主中持有唯一的 `data/history/HistoryRepository`，使用宿主 `viewModelScope` 管理内存缓存、共享读取及串行进度队列；历史页的 `HistoryViewModel` 绑定 `HistoryRoute`，只持有搜索和刷新任务。仓库按会话 revision 清空缓存并取消旧账号读写，请求前后核对账号快照，明确 401 交由统一会话失效处理，游客不调用历史接口。DTO 留在数据层，领域记录使用 `HistoryEntry`：Rosales 的 `duration` 为秒、`lastUpdateDateTime` 为毫秒时间戳、`anchor` 为秒数字符串，在历史边界将时长与有效锚点转换为毫秒；负数、非有限或溢出锚点不参与展示与续播。

历史读取不传 `videoTitle`，标题与作者过滤在本地完成；接口无分页或删除能力。首次视频详情与续播准备并发执行，不等待历史接口才请求详情。续播准备等待已排队的进度提交及 `loadIfNeeded`，总等待上限为 1500 毫秒；完成时由视频 ViewModel 发布一次续播位置快照，失败或超时按无云端位置处理，不使用迟到的查询结果跳转。自动播放等待该快照，手动播放不受历史等待限制。历史页刷新先等待当时的同步任务，避免从视频返回时读到尚未提交的锚点；刷新进行中新提交成功的记录合并到返回列表，后续刷新重新以云端列表为准，避免内存更新覆盖其他设备的新进度。

`PlaybackViewModel.setContent` 在新播放会话仅在续播准备完成后应用一次首 P 位置；已有播放器、保存的分 P 和本地位置优先，手动播放、切换分 P 或跳转后不再应用迟到的续播位置，手动跳转同时取消待执行的云端时长校验。Media3 提供实际时长后再检查距离结尾的阈值。进度采样附着在共享播放器，而不是视频页面，覆盖小窗、全屏和系统画中画；生命周期进入后台时额外 flush。提交只保存 `uuid`、`category=video`、字符串视频 ID 与向下取整的秒数，不扩展 Apple 共用的锚点协议，因此其他 P 不覆盖首 P 秒数。

同步队列串行执行，每个尚未发送的视频只保留最新进度。成功提交更新缓存中的位置、时间、封面与实际媒体时长；失败不输出服务端原文、敏感日志或周期性 Snackbar，不自动重放。队列与缓存不落盘，进程结束时的提交仅尽力完成；采样与续播阈值、页面交互见[历史记录功能](features.md#历史记录)。

## 主题实现

### 成功语义色

主题层的 [ThemeSemanticColors](../app/src/main/java/moe/kirakira/ui/theme/ThemeSemanticColors.kt) 提供不可变的 `success` 与 `onSuccess`，通过 `MaterialTheme.semanticColors` 读取，供成功／完成状态复用，不占用 Material 3 的既有颜色角色。`KIRAKIRATheme` 根据自身 `darkTheme` 参数通过 `CompositionLocal` 提供配色：浅色使用预设绿色 `#008577`，深色使用 `#00594F`，配套前景均为白色，不受强调色或壁纸取色影响。

安全页顶部卡片使用成功色实色填充，绿色仅作为该卡片的视觉识别；真实保护状态仍由文案及盾牌／警示图标表达。邀请码顶部卡片使用 `primary` 与 `onPrimary` 实色配对，默认呈品牌粉色并随主题强调色变化。两者沿用关于页的大圆角与实色视觉语言，图标底座使用低透明度前景色；邀请码生成按钮采用浅色容器与配套前景色，加载指示器使用卡片前景色。

### 配色生成

所有主题颜色固定使用经典强调色，默认原色为 `KIRAKIRAPink`（`#F06E8E`）。[MaterialKolor](https://github.com/jordond/MaterialKolor) 的 `rememberDynamicColorScheme` 仅用于生成 `Monochrome / SPEC_2021` 灰阶基础，HCT 工具用于生成强调色的容器、反色与固定色角色。该库维护 Google Material Color Utilities 的 Kotlin 移植与 Compose 适配，许可证为 MIT，底层 Material Color Utilities 为 Apache-2.0。

`KIRAKIRATheme`、预设／壁纸色板与自定义选色器统一使用 `rememberSeedColorScheme(seedColor, darkTheme)`。主题与设置不再接收、保存配色算法，经典方案沿用 `MaterialExpressiveTheme` 的形状、排版和动效。

[WallpaperAccentColor](../app/src/main/java/moe/kirakira/ui/theme/WallpaperAccentColor.kt) 通过 Compose `colorResource` 读取 Android 12+ 的公开资源 `android.R.color.system_accent1_500`，应用主题与壁纸色板共用此入口。资源读取跟随 Compose 的系统资源配置更新，不缓存壁纸色快照；应用明暗模式不改变取色阶。`KIRAKIRATheme` 保留的 `dynamicColor` 参数仅选择壁纸强调色来源，默认关闭；低版本返回空值，主题回退到保存的手动原色。

[ClassicAccentColorScheme](../app/src/main/java/moe/kirakira/ui/theme/ClassicAccentColorScheme.kt) 从灰阶角色构建经典强调色，并将 `secondaryContainer` 和 `onSecondaryContainer` 映射到同色主色容器与配套前景。默认 Slider、RangeSlider 的未经过轨道自动采用强调色调，活动圆点采用同一容器色；使用次要容器的选中 Chip 和导航指示器同样着色，不添加组件专用配色接口。`MainBottomBar` 将选中图标与文字映射到 `primary`，覆盖整个按钮的选中胶囊映射到 `primaryContainer`。主题统一通过 `LocalTonalElevationEnabled` 关闭色调高度叠加，`surfaceTint` 与 `surface` 同色，避免直接计算高度色时重新染色。原色保留、`onPrimary` 对比度阈值、组件配色接口与视觉取舍统一见[主题配色约束](../CONTRIBUTING.md#主题配色约束)。

设置主页、各设置子页与开源组件页通过 `ThemeColorDefaults.settingsBackgroundColor()` 使用 `MaterialTheme.colorScheme.surfaceContainer` 页面背景，普通 `SegmentedListItem` 保留官方默认的 `surface` 容器颜色和内容内边距，分组统一为无间隙、无分隔线的连接式样式，选中和功能总开关状态沿用官方配色。页面背景不再跟随顶栏颜色，避免经典强调色下页面与列表同为 `surface` 而融为一体；经典方案顶栏继续使用浅色纯白／深色深灰的 `surface`。头像裁剪工具保留原有 `surface` 背景。

[ConnectedListGroup](../app/src/main/java/moe/kirakira/ui/components/ConnectedListGroup.kt) 提供满宽、零行间距的普通分组布局和固定 1dp 整组阴影，不额外绘制背景或添加内边距，默认不裁剪内容；`SettingsSection` 保留标题间距并在内部复用它。共享 `connectedListItemShapes(index, count)` 以零圆角 `RoundedCornerShape` 为基础，通过官方 `ListItemDefaults.segmentedShapes` 获取首项顶部、末项底部及单项的主题圆角，中间连接边为直角；选中、按压、聚焦、悬停与拖动形状均沿用基础形状，保留官方颜色反馈与无障碍语义。菜单、设置、账号、历史记录、标签及播放器面板共用此入口。独立条目和懒列表通过 [connectedListItemShadow](../app/src/main/java/moe/kirakira/ui/components/ConnectedListShadow.kt) 复用固定 1dp 阴影；`ConnectedListGroup` 通过组合局部上下文标记阴影已由整组承载，内部条目不重复投影。懒列表的阴影轮廓在非首尾边缘向相邻行方向延伸，再按条目垂直区域裁剪，仅首尾允许顶部与底部阴影外溢，保持连续侧边并避免组内横向接缝；单项直接使用主题圆角阴影。懒列表保留逐项加载、稳定 key 与分页，仅在标题、分组边界和独立状态区域设置间距；账号侧滑组通过 `clipContent` 按整体外轮廓裁剪，外侧阴影及删除按钮的横向间距保持不变。

浅色方案在主题生成层将普通页面的 `background` 与 `surface` 设为纯白，分组列表页面使用的 `surfaceContainer` 保留 `#F5F5F5`，使白色 `SegmentedListItem` 与页面背景保持层次。深色方案保留原有灰阶角色；所有颜色来源共用这些规则。页面直接引用语义颜色，不再通过配色模式判断切换样式。

共享 [ContentPullToRefresh](../app/src/main/java/moe/kirakira/ui/components/ContentPullToRefresh.kt) 保留官方 `PullToRefreshBox` 的状态、手势与阈值，通过 `PullToRefreshDefaults.IndicatorBox` 绘制白色圆形容器，浅深模式保持一致。容器使用官方内置的 `PullToRefreshDefaults.Elevation` 高度阴影，与容器共享位移和顶部裁剪，未拉动且不刷新时由官方宿主关闭投影。外层不额外添加阴影 Modifier，避免容器隐藏后顶部仍残留圆形阴影；这一约束同样适用于没有实时模糊的 Android 8.1–11。内部圆弧使用 `primary`、20dp 外径、2.5dp 线宽、透明轨道与 `StrokeCap.Round`。

拉动反馈依据 [Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html) 和 AndroidX Material `PullRefreshIndicator` 的计算方式：跳过前 40% 拉动距离，后续逐步增长至最大 80% 圆弧；超出阈值时使用有上限的非线性张力，使旋转逐渐减缓，而不是在阈值处填满整圈。未达阈值时透明度为 30%，达阈值后以 300ms 线性补间增至 100%，拖回时反向恢复。箭头复用官方 Rounded `chevron_right` 原始资源，沿圆弧末端切线旋转并按拉动进度缩放，不绘制旧版实心三角箭头；仅该绘制区域固定 LTR，确保 RTL 下仍顺时针旋转。

刷新阶段复用 `IndeterminateCircularProgressIndicator` 的官方 Material 2 动画，拉动与旋转图形以 Material 2 的 100ms `Crossfade` 切换，不套用 Expressive 弹簧。刷新完成后，在官方状态回到隐藏位置之前保留旋转图形，用回收距离驱动整个指示器缩小、淡出，避免退出时重新出现拉动箭头。退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度低于 1 时自动创建局部离屏缓冲，裁掉已经位移的容器或外侧阴影。业务刷新状态仍由调用方提供，局部标记仅控制退场显示，不延长请求或重复触发刷新。来源、许可与改动说明见 [Android 刷新指示器说明](../third_party/android-refresh/README.md)。

### 选色与持久保存

[自定义选色对话框](../app/src/main/java/moe/kirakira/feature/settings/CustomColorDialog.kt)使用 [colorpicker-compose](https://github.com/skydoves/colorpicker-compose)（Apache-2.0）的 HSV 色盘与亮度滑条，封装在 Material 3 `AlertDialog` 中，补充亮度无障碍调节与 HEX 输入。它只增加 Compose 选色绘制与手势代码，不引入 View 互操作，许可证由 AboutLibraries 收集。

`ThemeColorSettings` 保存 `useSystemColors`、`seedColorArgb`、`customColorArgb` 与 `useCustomColor`，分别持久化为 `theme_system_colors`、`theme_seed_color`、`theme_custom_color` 与 `theme_use_custom_color`。默认关闭壁纸取色与自定义选择，两份原色均为项目粉色；没有旧用户，不保留算法设置或旧值推断的兼容逻辑。

色板始终显示，六种预设之后依次为受支持的壁纸颜色与自定义入口。选择壁纸仅更新来源标记，保留手动色值和自定义选择状态；色板以当前生效来源互斥标记选中项。自定义色值与当前手动色值独立存储，避免自定义颜色恰好等于预设时错误标记选中项。草稿确认与取消的约束见[贡献指南](../CONTRIBUTING.md#主题配色约束)。

预设色板复用官方 `ToggleButton` 和 `ToggleButtonDefaults.shapesFor` 的按压及选中动画，三色绘制随按钮形状一起裁剪。主题状态使用与现有 Lifecycle 同版本的 `lifecycle-runtime-compose` 进行生命周期感知收集。

MaterialKolor 提供灰阶生成、HCT 工具与 Compose 适配代码，不引入 View 组件库；两项配色依赖的实际 APK 增量需通过同构建配置比较，不以依赖包大小代替。

页面顶栏的标题槽通过 `TopAppBarDefaults.topAppBarColors(titleContentColor = MaterialTheme.colorScheme.primary)` 使用当前主题主色；共享 `CollapsibleTopAppBar` 与主界面的公共颜色配置集中设置，独立顶栏在调用处设置。顶栏标题文字显式使用 `FontWeight.SemiBold`（600），保持组件默认字号与行高；使用系统默认字体，由平台负责字重匹配与必要的加粗合成。可折叠标题在展开与折叠时保持相同颜色与字重，背景与导航图标沿用各页面配置。

### 栏面阴影

[ThemeShadows](../app/src/main/java/moe/kirakira/ui/theme/ThemeShadows.kt) 统一管理栏面及连接列表的平台 elevation 投影：顶栏、视频页播放器与 Tab 整体、个人主页 Tab 栏和底部导航栏使用固定 4dp 高度，连接列表使用 1dp；评论／弹幕输入框继续使用 `Surface.shadowElevation`，刷新容器继续使用官方 `IndicatorBox` 的内置高度阴影。阴影内部由内容表面自身遮挡，应用不手动生成外部蒙版或裁切轮廓。

浅色与深色模式共用固定高度，系统光照模型可能使相同高度的阴影随背景变化；输入区保留 4dp 原生高度。刷新阴影仅由官方容器承载，跟随容器位移与显示状态，不在外层重复投影。`bottomEdgeShadow()` 保留既有的栏面底边显示范围；连接列表继续延伸并裁剪分段投影轮廓，账号侧滑组继续通过 `clipContent` 控制内容裁剪。

阴影 Modifier 为普通函数，直接使用固定 elevation；主题、ViewModel 和导航不保存或传递阴影状态，外观页没有关闭入口，也不读取旧阴影偏好。大标题展开或折叠、Tab 切换及吸顶不改变阴影高度；经典强调色仍关闭色调高度叠加。

### 按钮动态阴影

[ShadowButtons](../app/src/main/java/moe/kirakira/ui/components/ShadowButtons.kt) 为填充、浅色填充、实心图标按钮及 FAB 封装官方 Material 3 组件；文字、描边及裸图标按钮不使用投影。调用方保留现有颜色、尺寸、形状、内容内边距、语义及点击逻辑，官方高度投影设为零，避免重复叠加。按钮不增加缩放、位移或布局空间，也不保存阴影偏好。投影读取官方 `MinimumInteractiveLeftAlignmentLine` 和 `MinimumInteractiveTopAlignmentLine`，按实际可见轮廓内缩并平移，不把最小触摸目标的留白当作按钮表面；测量及触摸区域保持原样。

共享交互源驱动投影和形状。普通按钮静止／按压／悬停或聚焦使用 2dp／8dp／4dp 等效高度，FAB 使用 6dp／12dp／8dp；按下 120ms、松开或取消 180ms，无弹跳，系统动画设置由 Compose 处理。禁用时立即移除投影，优先于按压、悬停与聚焦；FAB 通过封装的 `enabled` 参数同时阻止操作并声明禁用语义。传入 Expressive `shapes` 时，默认形状和按压形状通过公开 `Interpolatable` API 与主题 `defaultEffectsSpec` 插值，同一动画形状用于按钮轮廓和投影；不访问 Material 3 内部 API。

双层 `dropShadow` 在最低支持版本同样绘制彩色投影：环境层模糊半径为等效高度的 0.75 倍、无偏移，主投影模糊半径为等效高度、向下偏移为其 0.5 倍，两层均不扩张轮廓。环境层透明度为 `0.08 + 高度 × 0.005`，主层为 `0.14 + 高度 × 0.012`，并乘以容器透明度。彩色投影取实际容器色；RGB 最大与最小通道差不大于 0.02 的中性色使用黑色投影。主题切换与自定义颜色变化实时更新，不统一套用主色，也不绘制发光效果。

评论／弹幕发送按钮直接使用共享实心图标按钮，不再由外层带投影的圆形 `Surface` 承载，避免重复投影和外层裁剪。输入框的固定 4dp 平台阴影不变；发送按钮禁用时不绘制阴影，保持原有禁用配色及发送条件。

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

会话 UI 状态新增 `SessionOperation(type, targetUuid)`，区分初始化、切换、移除与本机重置。操作串行执行并在写盘期间保留类型与目标；成功提交之前继续选中原账号。账号行采用居中对齐的布局，游客平时仅显示名称，普通账号显示名称与副标题，长文字单行省略；操作状态复用副标题行，loading、单选与删除按钮共用 48dp 尾部槽位，列表不插入顶部加载项或空白状态行。“添加账号”独立成组。错误携带目标 UUID，重试闭包绑定原操作。游客切换、移除当前账号或登出之后尽力调用 `clearCredentialState`，不删除提供者密码；清理失败不撤销本地操作。

参考：[官方密码接入](https://developer.android.com/identity/passwords)、[Compose Autofill](https://developer.android.com/develop/ui/compose/text/autofill)、[Credentials 版本记录](https://developer.android.com/jetpack/androidx/releases/credentials)。

## API 与会话

### 视频搜索

`feature/search/SearchViewModel` 复用 `ContentViewModel` 的会话就绪与 revision 防护，在主导航宿主创建；`SearchPage` 收集状态，`SearchScreen` 与标签面板仅接收状态、事件回调。UI 不接触 DTO 或会话，结果使用现有 `VideoSummary`、`VideoCardRow` 与 `VideoRoute`，标签复用 `VideoTag.displayName` 的多语言回退。

主页面与标签面板共用 `QuerySearchBar`，使用官方 `SearchBar` 与 `SearchBarDefaults.InputField` 的公开内联重载，保持同页编辑和结果展示，不打开额外的展开搜索页面。当前版本的 state 重载在折叠 SearchBar 内禁用软键盘、要求配合独立展开页面，因此此处保留已弃用但仍公开的内联重载，不抑制弃用警告。输入按受控字符串同步 ViewModel，不额外保存查询；标签入口使用只读输入以避免打开面板前弹出键盘。搜索模式使用紧凑 FilterChip，结果数量、排序、升降序和布局切换合并为一行图标工具栏，保留 Tooltip 和本地化描述。

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

`ProfileEditorRoute` 使用 Navigation 3 条目级 `ProfileEditorViewModel`，资料通过 `data/profile/ProfileRepository` 访问，DTO 与凭据保持在数据层。`AccountProfile` 增加生日、性别和标签及兼容旧存储的默认值；读取本人资料时核对 UUID、UID。页面最大宽度 640dp，与设置页同用 `surfaceContainer` 背景和 `SectionHeader` 分组标题；顶部为大圆角横幅，使用 `Image` 与 `ContentScale.Crop` 展示个人主页共用的 `profile_banner_placeholder` 樱花图，保留服务端背景字段；头像为居中重叠的 112dp 头像与带背景色描边的编辑按钮。基本资料与标签放在 `surface` 圆角卡片中，个人信息使用分段资料行；保存栏按钮通过 `ButtonDefaults.*For(56.dp)` 获取 Expressive 形状、内边距和图标尺寸。另含日期选择器与标签 Chips。所有界面文本维护中英文资源，预览使用空表单。

用户名与昵称按 Rosales `ValidTool.validateNameField` 校验，最长 20 个 UTF-16 单元，昵称可为空；简介最长 200。保存统一 NFC 规范化，用户名执行 trim，只有修改用户名才调用查重。标签保留顺序及已有 ID，新项使用最小可用非负 ID；生日未设置时发送空字符串，不默认写入当天。更新始终包含用户名与已有背景，其他不属于编辑表单的字段不主动发送。

保存依次进行头像上传、资料更新、本人资料回读和加密会话提交。Repository 持有单个编辑流程的检查点：已上传的同一头像草稿不重复上传；服务端已保存后锁定编辑，刷新失败只重试刷新，写盘失败保留已读结果并仅重试提交。提交保留 token 与 bootstrap hint，仅替换当前账号资料；发布会话修订后现有内容观察者刷新“我”及本人主页。外部账号／修订变化取消请求和清除草稿，仅自身提交产生的修订被编辑页接纳。HTTP 200 的业务拒绝不通过匹配服务器消息推断过期。

图片选择使用 `PickVisualMedia`，回调绑定选择时的账号修订；原图复制限制 32 MiB。CanHub Android Image Cropper 提供经过验证的裁剪、EXIF 与采样能力，通过 `AndroidView` 封装 `CropImageView`，仅这一引擎使用第三方 View，周围顶栏、工具按钮、加载与确认均使用 Compose M3E。固定 1:1 裁剪和圆形预览，异步输出最大 1024px 的 JPEG（质量 90）；Manifest 移除库自带的导出 Activity 与未使用的文件提供者。View 重建时从流程内存恢复裁剪范围；释放时清理图片和监听，正在运行的异步裁剪仅保留收尾回调，完成后释放图片并拒绝旧流程结果。草稿文件为每个流程独立缓存，替换、退出和账号变化时异步删除；不写 SavedState 或会话存储。

预签名上传使用独立无 Cookie／认证头的 OkHttp 客户端，仅允许 `https://upload.imagedelivery.net:443`，发送 multipart `file`，禁用重定向和自动重试，完整调用上限 60 秒，响应上限 1 MiB，并检查 HTTP 与 JSON `success`。成功的图片 ID 交给资料更新接口，不保存或记录预签名 URL。应用 API 仍使用共享 HTTPS 客户端。

返回保护由 `ActivityNavDisplay` 的默认放行回调协调。资料有草稿或正在裁剪时不启动页面预测性返回转场；手势完成才显示确认、关闭裁剪或忽略正在保存的返回，取消手势没有副作用。顶栏使用同一 ViewModel 判断；用户确认离开后才出栈，不添加页面级低层返回处理器。

### 账号设置管理

安全页使用 `SecuritySettingsRoute`、导航条目级 `SecuritySettingsViewModel` 与 `data/security/SecurityRepository`。页面内步骤与表单由 ViewModel 管理，敏感模型使用普通类避免生成含凭据的 `toString()`；没有敏感持久化状态或 DTO 进入 UI。每次请求捕获 `AuthRepository.RequestSession`，核对 revision 并在散列后、HTTP 返回后验证账号；取消继续传播，明确 401 才失效本机会话。设置入口对游客导航登录，恢复导航中的游客安全页提供登录操作。

安全验证码复用 AuthRepository 的按邮箱互斥锁、单调时钟与 60 秒冷却，当前邮箱使用认证端点，新邮箱使用带当前会话的通用邮箱端点；两者独立显示剩余时间。模板对应 `SendChangeEmailVerificationCode` / `update-email`、`SendChangePasswordVerificationCode` / `update-password`、`SendDisableUserEmail2FAVerificationCode` / `delete-email-2fa`。Rosales 当前 `General2FAVerifier` 在无二步验证的严格校验分支固定查询 `update-email`：这类账号修改密码时发送密码模板但使用 `update-email` 业务名，兼容实际服务端逻辑；已启用邮箱验证仍使用 `update-password`。不改动 Rosales 或 Cerasus。

邮箱与密码更新服务端确认成功后，Repository 在内存记录完成检查点；邮箱检查点只重读并发布本人资料，密码检查点只删除请求所属本机会话。存储失败释放表单凭据并提供同步重试，不重放写请求。AuthRepository 在会话互斥锁内校验请求并成功写入本地存储后、发布会话状态前，通过回调提供本次实际发布的 revision；资料与安全流程据此识别自身发布／登出，不自行推算下一修订号。账号切换或其他会话变更仍取消旧流程。密码变更后经一次性邮箱状态打开登录并尽力清理密码提供者会话，不自动读取或保存密码。

UI 使用共享可折叠顶栏、官方分段列表、语义主题色状态横幅、启用徽章和分步进度。普通设置入口、开关、单选、滑块、资料和安全列表前后图标使用裸露的 24dp Material Symbols Rounded（含尾部箭头及列表内操作图标），继承列表内容颜色及禁用样式，危险操作使用主题错误色；弹幕总开关保留官方 checked 行强调。共用菜单组件的“我”页面同步使用 24dp 图标；屏蔽管理固定类别与添加表单同样使用 24dp 裸图标，用户头像保持独立。MaterialShapes 图标容器保留于隐私可见性、邀请码使用状态等状态形状切换，以及安全状态横幅、安全流程和邀请码统计的独立大图标。首页刷新保留内容；表单使用滚动布局，底部系统 Insets 随内容滚动并避让 IME。ActivityNavDisplay 的返回保护在流程内消耗返回回到首页，操作期间阻止返回；一次性代码阶段须确认后清空。TOTP URI 按结构解析并校验 `otpauth://totp` 与 Base32 密钥；ZXing Core 固定版本在后台生成带白底静区的 Bitmap，Compose 展示，生成失败仍可使用手动密钥。

恢复响应核对五个不同的六位备用码和 24 位恢复码；已确认绑定但返回不完整时不重新确认，清空设置材料并提示重新解绑绑定。复制通过 Android ClipboardManager 标记 `android.content.extra.IS_SENSITIVE` 与随机所有者标记，离开步骤仅清理仍属于本流程的内容，不覆盖其他应用后续复制的内容。二维码、密钥与恢复码不导出到应用磁盘；生命周期和内存保护边界与认证流程一致，Kotlin 字符串无法保证物理内存即时清零。

隐私页采用 `PrivacySettingsRoute` 与导航条目级 `PrivacySettingsViewModel`，复用 `AccountSettingsRepository` 的账号 revision 请求守卫。`POST user/settings` 读取后检查 `success`、设置对象及当前账号 UID；五项缺失值默认公开，重复项或未知可见性拒绝编辑。`POST user/settings/update` 提交完整五项 `userPrivaryVisibilitiesSetting`，保留其他隐私条目并带回原有 `userLinkedAccountsVisibilitiesSetting`，避免后端更新逻辑覆盖关联设置，不提交主题等其他偏好。wire ID 使用 `privary.birthday`、`privary.age`、`privary.follow`、`privary.fans`、`privary.favorites`，可见性值使用 `public`、`following`、`private`。

更新接口可能返回不含 UID 的更新载荷：此时 Android 通过同一请求账号再次调用 `POST user/settings`，确认 UID 与五项值后再发布保存成功；读取失败保留草稿并显示错误，不自动重发写请求。DTO 仅在数据层使用，领域模型交给 UI；草稿和选择面板仅存流程内存。账号变化取消请求、清空状态；旋转保留 ViewModel，进程重建重新读取。隐私页返回保护复用 `ActivityNavDisplay` 回调，草稿未保存时不启动预测性返回动画，手势完成显示放弃确认，保存／重置期间忽略返回。

界面使用连接式 ToggleButton 组批量设置可见性，按当前可见性着色的 MaterialShapes 形状图标容器，分段列表逐项选择，以及悬浮工具栏（HorizontalFloatingToolbar）承载重置与应用操作；底部选择面板复用相同形状容器。后端隐私限制缺陷及保存语义见[隐私设置](features.md#隐私设置)，本次不改动 Rosales 或 Cerasus。

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

[`FrostedScaffold`](../app/src/main/java/moe/kirakira/ui/components/FrostedScaffold.kt) 为普通页面创建独立的 `HazeState`，用 `hazeSource` 采样内容、单独绘制顶部背景，再绘制透明的官方顶栏。文字和图标不参与模糊。背景裁剪只作用于背景子节点，阴影由 `ThemeShadows` 的平台 elevation 投影管理，避免重复投影。底部胶囊通过 `frostedBarBackground(shape)` 使用外层主页面采样状态，内部各 Tab 的顶部栏使用独立状态。

- 背景使用 `HazeInput.Sources` 采样，源消失时使用 `ClearWhenUnavailable`，不继续保留上一页面画面。原生 backdrop 暂不启用：已在 Android 17 模拟器（`CP41.260828.004.A7`，SkiaGL）上复现 `RenderNode.setBackdropRenderEffect()` 与半透明父层、elevation 同时使用时的灰框和内部矩形异常。对照父层 alpha 为 1、0.5、0.2：普通 elevation 及 Haze Sources 正常，Haze Backdrop 异常，强制 Offscreen 仍异常；移除 Compose/Haze、仅使用 Android View 和 RenderNode 也能复现，而移除 backdrop 恢复正常。证据定位到系统原生 backdrop 的合成路径，尚未定位内部实现的具体错误，也未验证所有设备。版本以 `gradle/libs.versions.toml` 为准，不引入玻璃折射模块。
- Android 12+ 使用主题 `surface` 作为缺失源像素的底色，叠加同色 80% 不透明度遮罩、20dp 模糊与零噪点。Android 8.1–11 直接绘制 90% 不透明度背景，不挂载采样节点。背景透明度不作用于整个栏或前景。
- 滚动列表的顶部与底部安全区域放入 `contentPadding`，滚动 Column 则在 `verticalScroll` 后添加，初始避开栏面、滚动时内容可进入栏后。`ContentPullToRefresh.indicatorTopPadding` 仅移动覆盖式指示器，不移动滚动视口。历史搜索表单作为列表首项随内容滚动。
- 作者资料页为保留吸顶 Tab 和嵌套分页滚动，保持原有栏下视口，将后方滚动封面单独采样并与内容共享该页状态；非滚动表单保留安全布局。媒体、头像裁剪及系统栏不接入本组件。

### 可选的可折叠大标题栏

共享组件为 [`CollapsibleTopAppBar`](../app/src/main/java/moe/kirakira/ui/components/CollapsibleTopAppBar.kt)，适用场景、状态与 Insets 约束见[贡献指南](../CONTRIBUTING.md#可选的可折叠大标题栏)。

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

评论和弹幕发送栏的容器色由共用 `ContentComposer` 内部统一使用 `MaterialTheme.colorScheme.surface`（浅色为纯白，深色遵循主题深灰），页面不单独覆盖。弹幕发送栏复用 `ContentComposer` 的可选尾部插槽。样式使用官方 ModalBottomSheet，自定义选色在同一面板内进入子页，复用已有 HSV 选色依赖；确认才应用颜色。面板打开时清除输入焦点并隐藏键盘。预览使用 14／20／28sp，彩虹描边参考 Cerasus 的粉蓝渐变；关闭系统动画时静态显示。预览独立于播放器，播放器使用更紧凑的三档字号与相同粉蓝描边，见下方弹幕显示实现。

### 弹幕显示与设置

`DanmakuEntry.style` 保留 Rosales 的颜色、字号、模式与彩虹标记；缺失或未知样式回退为白色、中号、右向左、无彩虹，非法时间及空白正文不进入列表。`VideoViewModel` 在账号就绪时与视频详情一起加载弹幕，列表与播放器共用同一 Flow。刷新及发送成功后的重新读取遵循现有账号修订与错误保留逻辑；渲染层不发请求、不维护本地发送副本。后端没有分 P 字段，弹幕池仍以视频 ID 为单位，按当前分 P 的进度解释；请求仍为公共读取，不扩展个性化过滤。

`DanmakuOverlay` 使用单个 Compose Canvas，放在 `ContentFrame` 上方、控制层下方，不拦截触摸也不逐条播报。视口按视频像素宽高比和容器尺寸执行 Fit，裁剪到实际视频区域顶部所选比例；未知视频尺寸时不绘制。`DanmakuTimeline` 按时间稳定排序、预先排轨，帧查询按当前弹幕池的最长显示时长，以二分定位候选时间窗口，适应宽屏和低滚动倍率下超过 16 秒的弹幕；左右滚动位置按播放时间线性计算。同向弹幕检查共享生命期两端的间距以防追尾，反向和固定弹幕不与尚未离开的其他类型共享轨道；按每条文字布局的实际高度分配垂直空间，仅在横向运动可能相撞的弹幕之间保留 2dp 垂直间距，上下边缘各留 1dp。取消最大字号统一行高、整行倍数占位与均摊剩余高度；顶部和滚动模式从上向下寻找空隙，底部模式从所选区域底边向上寻找空隙。放不下则跳过，不延迟补发。横向追尾安全间距仍为 6dp。

播放器文字使用 12／16／22sp 与全局缩放，继续遵循系统字体缩放；显式使用 1.25em 行高、居中对齐、保留行高且关闭额外字体内边距。发送预览保持 14／20／28sp。颜色和描边样式与发送预览一致：普通弹幕为原色实心文字加黑色阴影（3px 模糊），彩虹弹幕先绘制粉蓝渐变描边（4px），再以原色填充，不加阴影。每次绘制显式设置 `Fill`／`Stroke` 和阴影；同一缓存文字布局会保留画笔状态，不能依赖空 `drawStyle` 将描边恢复为填充。TextMeasurer 使用 128 项缓存，帧循环只持有当前可见文字的布局；池布局分批让出协程，取消时终止计算。多行正文仅在画面中转为空格；超过 32,760px 的极长文字在绘制层省略，避免超出文字布局尺寸，列表保留完整正文。滚动时长为 `实际视频宽度 px / (144dp 对应的 px × 滚动倍率) × 1000` 毫秒，对齐 Cerasus 默认 144 CSS px/s 的宽度计算方式；实际移动路程仍为视口宽度加文字宽度。固定弹幕保持 4000 毫秒，均采用媒体时间。默认倍率仍为 1×，已有用户倍率保持不变。

帧循环直接读取 Media3 `currentPosition`，不使用页面 500ms 轮询。暂停／缓冲时冻结并等待 Player 事件；空白区等待下一条的播放时间，seek、恢复、倍速变化通过监听器唤醒。池、尺寸和设置变化重建当前布局，视频／分 P／账号修订构成内容标识。画中画、页面非活动、播放器释放和播放失败时移除绘制层，取消协程及移除监听器；退出画中画后按真实进度重建，不补发错过的弹幕。

`DanmakuSettingsRoute` 使用现有 Navigation 3 宿主和可折叠顶栏。`DanmakuSettingsViewModel` 由导航宿主持有，向设置页与播放器提供同一份状态；只写 `kirakira_settings` 下独立的 `danmaku_*` 键，不覆盖播放或主题设置。读取在 IO 调度器完成，写入使用 SharedPreferences.apply，范围与步长统一归一化；加载完成前禁用编辑并不绘制。偏好为设备级非敏感数据，沿用该偏好文件已有的备份白名单。

弹幕设置的滑块条目保留官方 `SegmentedListItem` 分组样式，标题与当前数值在 `content` 内同排，Slider 在 `supportingContent` 内占满内容宽度，避免尾部数值列压缩滑块。

字号与排布参考：Cerasus 使用 14／20／28 CSS px、继承 1.4 行高，其 `danmaku` DOM 引擎按每条文字的实际 offsetHeight 排布；DanmakuFlameMaster 同样基于 paintHeight 与 margin 寻找位置。tdanmaku 默认 15sp、1.6 倍行高。Android 此处选用 16sp 中号与 1.25em 行高，适配较小的视频视口，并采用逐条高度排布；这些值是本客户端的取舍，并非直接复制 Cerasus 的 CSS 数值。

采用自有 Compose 绘制，不新增引擎依赖或移植外部源码。选型对照：[DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) 提供四模式但需要旧 View 引擎适配，[AkDanmaku](https://github.com/KwaiAppTeam/AkDanmaku) 引入 libGDX/ECS，[DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) 默认缺少反向模式，[tdanmaku](https://github.com/NihilDigit/tdanmaku) 的早期接口将反向滚动降级为普通滚动；这些项目未进入应用依赖或源码。

### 评论与弹幕颜文字

`ContentComposer` 的输入框 `Surface` 与独立发送按钮放在同一个底部对齐的 `Row` 内：输入框占剩余宽度，右侧发送按钮为固定 56dp 圆形 `FilledIconButton`，两者相隔 8dp。发送按钮位于输入框外，不参与框内测量和展开动画；整行实测高度用于扣除颜文字面板的可用空间。输入框以草稿是否为空切换单行药丸与两行编辑布局：空草稿时颜文字／样式按钮放在框内尾部，非空时放在下方操作栏。输入框、颜文字／样式按钮和发送按钮始终保留在同一组合位置，避免首次输入时丢失焦点、输入法组合状态或按钮交互状态。`ComposerInputLayout` 按框内按钮实测宽高排布，容器高度和圆角使用主题 `fastSpatialSpec`，颜文字／样式按钮横向位移与编辑区域宽度使用 `defaultSpatialSpec`，不在中途硬切分段。颜文字／样式按钮锚定框内底部，仅在横向移动。横向进度采用保留符号的平方映射，越过展开位置时以连续阻力压缩回弹距离，保持在容器边缘内，不硬截断位置。按钮横向绘制使用浮点图层位移；归一化进度的结束阈值为 0.0001，避免长距离移动尚有数个像素时就瞬间归位。容器保留空间弹簧的轻微回弹，可用宽度、最小高度与合法圆角仍受布局约束。连续输入与清空从当前进度及速度转向最新状态，不排队；Compose 动画时钟遵循系统动画设置。多行文字增减的 `animateContentSize` 仅作用于 `Surface` 内部的编辑区域，外层布局与 Surface 不裁剪阴影。使用 `TextFieldValue` 保存光标、选区和输入法组合状态，文字仍由原有 ViewModel 字符串草稿拥有。外部草稿清空时同步编辑值；颜文字替换 `selection.min..selection.max`，插入后光标移至末尾，不增补空格，超限整项拒绝。`ComposerState` 在视频页分别为评论和弹幕创建，并按视频 ID 与会话修订隔离；可保存状态只记录分类与选区偏移，正文继续来自 ViewModel，面板打开状态不恢复。旋转保留分类／选区，切换 Tab、打开样式、全屏和画中画关闭面板；只有当前活动页签可以拦截返回。

输入框保留固定的 4dp 阴影高度；独立发送按钮使用共享 `ShadowFilledIconButton` 的动态彩色阴影，禁用时无投影。按钮保留官方配色、禁用状态与点击反馈，不再叠加外层圆形 `Surface` 投影，详见[按钮动态阴影](#按钮动态阴影)。

`KaomojiPicker` 只接收分类、最近记录及事件回调。静态目录完整保留 Cerasus 的五类 268 项原始字符；网格按实际字体测量跨列，极长条目可横向滚动阅读。面板最高 300dp，并按可用高度扣除输入区实测高度；等待 IME 收起后显示，系统内边距沿用 `FloatingComposerLayout` 的单一入口，列表底部留白包含整个输入区与面板的实测高度。M3E motionScheme 驱动容器尺寸变化，颜色和排版继承 `KIRAKIRATheme`。

`KaomojiViewModel` 暴露应用级 `RecentKaomojiStore` 的 Flow。Store 在 IO 调度器上先读后串行处理插入事件，以独立 `kirakira_kaomoji.xml` 保存最多 24 项去重记录，只接受目录中存在的值；初始或损坏记录回退为空。最近记录不属于账号私有数据，不保存草稿，不加入备份白名单，也不请求网络。来源与许可见 [Cerasus 资源](../third_party/cerasus-icons/README.md)。
