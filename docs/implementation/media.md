# 播放、图片与弹幕

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录播放会话的归属、播放器的控制与手势、全屏与系统栏、应用内小窗和系统画中画、历史记录与续播、图片查看与导出，以及弹幕的发送、渲染和输入区。内容列表、评论分页与读取失败的处理见[内容浏览](content.md)。

- [视频数据与播放器](#视频数据与播放器)
- [播放器控制与设置](#播放器控制与设置)
- [全屏与系统栏](#全屏与系统栏)
- [小窗与画中画](#小窗与画中画)
- [播放器首次加载背景](#播放器首次加载背景)
- [历史记录与续播](#历史记录与续播)
- [图片查看与导出](#图片查看与导出)
- [弹幕发送样式](#弹幕发送样式)
- [弹幕显示与设置](#弹幕显示与设置)
- [评论与弹幕颜文字](#评论与弹幕颜文字)

## 视频数据与播放器

### 接口

| 接口 | 用途 |
| --- | --- |
| GET `video/home` | 首页；没有分页参数，目前服务端限制 100 条 |
| GET `video?videoId=` | 详情、分 P、作者、赞踩；登录状态下读取可能由后端更新播放量和历史 |
| GET `history/filter`；POST `history/merge` | 账号的视频历史，以及首 P 整数秒进度的同步 |
| GET `video/user?uid=` | 作者作品 |
| GET `user/info?uid=`、`feed/stats?targetUid=` | 作者资料和统计 |
| GET `feed/following/list`／`feed/follower/list` | 关注／粉丝列表；`targetUid`、`page`、`pageSize=50` |
| POST `feed/following`／`feed/unfollowing` | 关注／取消关注 |
| POST `video/upvote`／`video/downvote`；DELETE 对应路径加 `/cancel` | 视频赞踩及撤销 |
| GET `video/comment?videoId=&page=&pageSize=20` | 按楼层升序分页的评论 |
| POST `video/comment/emit` | 发布一级评论 |
| POST `video/comment/upvote`／`downvote`；DELETE 对应路径加 `/cancel` | 评论赞踩及撤销 |
| GET `video/danmaku?videoId=`；POST `video/danmaku/emit` | 公共弹幕列表和发布 |

- 首页的过滤请求额外发送 `uid`、`uuid` 和 `user-data-bootstrap-hint`；这个 hint 来自登录或 `/user/self`，保存在账号文件的可选字段里，缺失时按游客读取。
- 普通鉴权使用账号 token 的快照。**媒体与图片的传输使用独立的客户端，不带 Cookie。**
- 评论数量字段在空结果时可能被省略，游客的视频详情可能没有赞踩计数。界面显示“未知”，不编造零。
- 评论的长度上限是 20,000 字符以内。弹幕颜色是不带井号的六位 RGB，时间单位为秒。

### 数据映射

[ContentRepository](../../app/src/main/kotlin/moe/kirakira/data/content/ContentRepository.kt) 把私有 DTO 映射为领域模型：

- 视频摘要要求 KVID 为正数，标题原样传递，缺失的标题映射为空字符串。
- 详情校验视频和作者的身份，分 P 与标签独立解析。分 P 按 ID 保留首个可解码的条目；缺失或无效的地址标记为“不可播放”。
- 资料映射拒绝非正的 UID，也拒绝响应里的 UID 与目标不一致的结果，避免把异常资料归给目标用户。

图片地址由 [DeliveryImage.kt](../../app/src/main/kotlin/moe/kirakira/core/image/DeliveryImage.kt) 统一解析：Cloudflare 的图片 ID 追加到构建时选定的 `BuildConfig.IMAGE_DELIVERY_BASE_URL`，缩略图带宽度参数，全屏和导出使用原图；完整且不含凭据的 HTTPS URL 直接使用。图片环境的选择见[开发指南](../development.md#api-环境配置)。

### 播放会话的归属

播放器使用 Media3 ExoPlayer、DASH／HLS、OkHttp 数据源、Compose 的 `ContentFrame` 和 MediaSession；按钮和进度条是项目自己的 Material 3 控件，不使用 XML 的 PlayerView。

- **一个共享会话**。`AppNavHost` 持有共享的 [PlaybackViewModel](../../app/src/main/kotlin/moe/kirakira/feature/player/PlaybackViewModel.kt)，视频页、应用内小窗和系统画中画使用同一个 ExoPlayer 与 MediaSession，同一时刻只有一个画面绑定。`PlaybackViewModel` 在构造时把 Context 转成 Application Context，不随宿主作用域持有 Activity。
- **独立配置的媒体客户端**拒绝 HTTP 和带凭据的 URL，清单和分片都不携带账号会话。
- **磁盘缓存**。渐进式媒体和 HLS／DASH 分片经 `CacheDataSource` 写入 `cacheDir/media`，按 LRU 保留至多 256 MiB。清单直接从网络读取，使相同 URL 上的清单更新也能生效。遇到缓存 I/O 错误后，当前视频后续打开数据源时直接从网络读取，包括自动和手动重试。`SimpleCache` 会锁定目录，所以进程内只建一个实例（[MediaCache.kt](../../app/src/main/kotlin/moe/kirakira/feature/player/MediaCache.kt)）。缓存只存公开媒体，不在备份范围内。
- **可恢复的内容**。SavedState 保留视频 ID、分 P ID、播放毫秒数和倍速相关的标量。恢复时按分 P ID 匹配，只有序号的旧快照从首 P 开始，避免把旧位置用到另一分 P。播放毫秒数由 `SavedStateProvider` 在保存时读取。进程重建后保持暂停，小窗只在内存里保留。
- **自动播放只有一次资格**。资格来自新入栈的视频条目，同一视频再次入栈可以重新获得资格。恢复进程、展开小窗和返回被覆盖的条目沿用原播放状态。新页面等待播放设置、前台生命周期和可播放内容就绪；手动操作、账号变化或离开页面都会消耗这次资格。
- **重读详情不打断播放**。回到视频页重新读取详情时，同一分 P 的媒体 URL 更新不打断当前播放。分 P 重排后按 ID 保留当前选择和进度；当前分 P 不存在了才释放播放器并回到首 P。
- **何时释放**。关闭小窗、账号修订变化和普通的进入后台都会释放会话；旋转保留 ViewModel。释放已播完的播放器时，先提交实际位置供云端历史使用，再把本地续播位置归零。

### 画面容器

普通播放、应用内小窗和系统画中画统一使用 [PlayerContentFrame](../../app/src/main/kotlin/moe/kirakira/feature/player/PlayerContentFrame.kt)。它保留 Media3 默认的 `SurfaceView`，并在有明确尺寸的容器上使用 `clipToBounds()`。

**为什么需要这个裁剪**：Android 11 的 `SurfaceView.clearSurfaceViewPort()` 用 `Canvas.drawColor(..., PorterDuff.Mode.CLEAR)` 清空当前 Canvas 的裁剪区域，而 Compose 的 `AndroidView` 默认不按布局边界裁剪，于是之前画好的页面底色也可能被一起清掉。Android 12 起系统改为按视图宽高执行 `punchHole`。外层的矩形裁剪把清空范围限制在视频视口内。

### 视频详情的分区布局

视频详情通过 `VideoPaneLayout` 在宿主提供的稳定安全区和物理分区内排列播放器与详情：连续空间足够时横排；被纵向铰链分隔时各占一侧；横向半开时上区放播放器，下区放详情和播放控制；空间不足时沿用上下布局。有分隔的铰链时不跨区。

- 布局切换时按 `VideoTab` 而不是页码保留互动标签，三个列表的滚动状态、简介的展开状态和编辑草稿不随布局重建。
- 只重新测量同一个播放器视口；视频 Surface、播放会话、分页和草稿都不以窗口形态为键。
- 键盘的 Insets 不参与布局分级。

## 播放器控制与设置

### 控制层

[VideoPlayer](../../app/src/main/kotlin/moe/kirakira/feature/player/VideoPlayer.kt) 接收播放状态、Media3 的画面实例和事件回调，不持有 ViewModel。

- **显隐与可用是两回事**。控制层的显隐独立于控件的业务可用状态；`enabled` 只表达媒体、轨道或页面的实际可用条件。隐藏的退出动画期间清除语义、阻止焦点进入，操作回调也再次检查显隐状态，避免未结束的交互提交操作。
- **谁保持显示**。缓冲、错误、进度拖动、设置面板和 TalkBack 的触摸探索期间保持显示；暂停和播放结束并不强制显示，同样支持轻触显隐和三秒后自动隐藏。
- **画面点击由共同的父容器处理**，接收没有被按钮、进度拖动或滚动消费的轻触。开始隐藏时在控制层上方放一个独立的点击层：点击只恢复控制层并重置计时，不触发底下的控件。
- **自适应布局**。私有的 `PlayerControlsLayout` 用 `SubcomposeLayout` 先测量各部分，把播放按钮放在视频画面的几何中心；只有发生重叠或越界时才把它排进底部操作区。播放形变、弹幕划线和进度拖动的状态保存在这个布局之外，重排时不重播动效，也不丢拖动预览。
- **播放图标**由 `showPauseIcon` 驱动：Media3 的 `playWhenReady` 为真且未结束、未报错时显示暂停图标，缓冲不会让它变回播放图标。`playing` 仍然表达 `isPlaying`，用于画中画等行为。
- 控制层局部提供白色的 `LocalRippleConfiguration`；弹出的设置面板继续跟随应用主题。

播放进度使用共享的 [SmoothSlider](components.md#滑杆点击动画)，缓冲和已播放位置由两层 `SliderDefaults.Track` 显示，底层只绘制，不带交互和语义。跳转在完成回调里提交。未知时长和播放器释放时显示零缓冲。

图标来源见 [Cerasus 播放器图标](../../third_party/cerasus-icons/README.md#播放器图标)。

### 手势

[PlayerGestures.kt](../../app/src/main/kotlin/moe/kirakira/feature/player/PlayerGestures.kt) 的 `PlayerGestureState` 只在组合内保存拖动预览、待判定的单击和双击的累计目标。

- `Modifier.playerGestures` 超过系统触摸阈值之后才锁定方向，尊重子控件对事件的消费；从左右边缘开始的拖动不接管，留给系统返回手势。
- **横向拖动只预览，松手才跳转**。预览期间不调用播放器，`previewPositionMs` 同时驱动进度条和时间文字；取消后恢复实际进度。
- **双击**。左右各三分之一区域双击分别后退和前进；后续同侧的点击使用**累计目标**，否则会因为进度轮询滞后而算错。中间区域双击切换播放与暂停，并取消待执行的单击。
- 双击反馈的箭头动画由一个协程消费“合并容量为一”的装饰脉冲：当前一拍结束后接最新的一拍，不积压全部点击；业务跳转和水波纹仍然每次立即响应。
- 首次加载、失败、设置面板打开和触摸探索时禁用手势；未知时长和不可跳转的媒体禁用进度手势。键盘确认和无障碍的显隐动作保留。

### 音量

`VideoPage` 持有 [MediaVolumeController](../../app/src/main/kotlin/moe/kirakira/feature/player/MediaVolumeController.kt)，通过回调向播放器提供 0–1 的媒体音量读写；`VideoPlayer` 不直接访问平台的音量服务。适配器只操作 `AudioManager.STREAM_MUSIC`，Manifest 声明普通权限 `MODIFY_AUDIO_SETTINGS`。滑动不弹系统音量面板，不绕过固定音量或设备策略，反馈使用系统实际读回的值。

### 画质

画质选项由 `PlaybackViewModel` 在 `EVENT_TRACKS_CHANGED` 时从 `currentTracks` 读取：只包含解码支持且高度有效的视频轨道，同高度去重并降序排列。

- 手动模式通过 `TrackSelectionOverride` 覆盖视频轨道；自动模式只清除视频覆盖，不改音轨和播放位置。
- 每次准备新媒体时重新匹配首选高度；匹配不到时显示“自动”，但不改动保存的偏好。
- 实际清晰度从 `videoFormat` 取得，并随进度轮询更新。不能从“被选中的轨道”推断，因为自适应组里会有多个选中的轨道。

### 倍速

`PlaybackSpeedRules` 统一倍速的范围、预设、连续值的归一化、无障碍步进和“进度／角度”的转换，供 ViewModel、仪表盘、拖动输入、图标和滑杆共用。

- 倍率、无级调速和保持音调由 `PlaybackViewModel` 管理，以非敏感标量存入 SavedState。重建播放器时应用 `PlaybackParameters(speed, pitch)`：保持音调时 pitch 为 1，否则等于 speed。
- 无级值限制在 0.25–4 并保留两位小数，有级模式吸附到预设倍率。
- [PlaybackSpeedGauge](../../app/src/main/kotlin/moe/kirakira/feature/player/PlaybackSpeedGauge.kt) 只消费现有的 `PlayerUiState`。指针显示的是**实际**倍率：`playing` 来自 Media3 的 `isPlaying`，未播放时指针回零位；这里不能用 `showPauseIcon`，因为它在缓冲期间仍为真。拖动时临时显示设定倍率。
- 倍率在对数刻度上映射到角度，1× 位于中央；零刻度单独设置，不计算 `log2(0)`。关闭保持音调时显示对应的半音变化。
- 码表只接管在指针或弧线附近开始的单指手势，其他位置留给面板滚动。
- 无障碍的可调节语义、读数和滑杆都表达**设定**倍率，不受指针动画或归零的影响。

### 设置面板

画质、倍速和通用设置由 [PlayerSettingsSheet](../../app/src/main/kotlin/moe/kirakira/feature/player/PlayerSettingsSheet.kt) 统一创建。通用设置带“播放器／弹幕”两个页签，内容与应用设置页共用 `PlaybackSettingsContent` 和 `DanmakuSettingsContent`，立即生效并保存到同一份设备级存储。

- 普通播放使用 `ModalBottomSheet`。满窗后改为在播放器窗口内使用 `BottomSheet` 加 `Scrim`，这样系统栏仍由 Activity 持续控制。
- 存在分隔的铰链时，面板在播放器所在的区域内打开，不横跨铰链。
- 面板打开时阻止控制栏自动隐藏；全屏切换、画中画、页面失活或播放错误时关闭。打开状态不持久保存。
- **页签的横向拖动**。从手动拖动开始到惯性结束，临时关闭外层的 `sheetGesturesEnabled`。当前 Material 3 的面板会在 `onPostFling` 里把子 Pager 已经消费的横向速度再处理一遍；只有滚动状态变化时不启动这个隔离，普通的纵向滚动与面板拖动保持原样。

### 持久化的键

播放设置和弹幕设置保存在 `kirakira_settings`（参与非敏感设置备份），写入使用 `apply`，不绑定账号。

| 键 | 含义 | 缺省 |
| --- | --- | --- |
| `playback_in_app_mini_player` | 应用内小窗 | 开 |
| `playback_outside_app_mini_player` | 应用外小窗（系统画中画） | 开 |
| `playback_autoplay` | 自动播放 | 关 |
| `playback_auto_quality` | 自动画质 | 开 |
| `playback_video_height` | 首选的视频高度 | 无 |
| `danmaku_*` | 弹幕显示设置，见[弹幕显示与设置](#弹幕显示与设置) | — |

不迁移更早的画中画开关。

## 全屏与系统栏

视频页的播放器固定在 `VideoPlayerViewport` 的同一个组合位置，只在“嵌入布局”与“满窗布局”之间按**实际的窗口测量结果**切换，不对尺寸、位置或详情的透明度做全屏动画。

**为什么不模拟旋转动画**：这个选择保留了 [Media3 推荐优先使用的 SurfaceView](https://developer.android.com/media/media3/ui/surface)，全屏转场交给 Android 默认的窗口旋转（[ROTATION_ANIMATION_ROTATE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#ROTATION_ANIMATION_ROTATE)），具体效果取决于系统和设备。Compose 的[共享元素转场](https://developer.android.com/develop/ui/compose/animation/shared-elements#current-limitations)目前不支持 View 与 Compose 的互操作，无法用于含 `AndroidView` 的视频输出。

- **进入与退出同步**。全屏入口通过同一个 `requestFullscreen` 回调，立即同步页面目标与 Activity 的方向和系统栏请求；返回按钮和系统返回也经它立即退出。没有动画完成回调，也没有固定延时，快速切换始终使用最新目标。
- **等待横屏期间固定嵌入布局**。满窗的条件是 Configuration 已横屏**并且**实际布局宽大于高。只看其中一个会误判：键盘会压缩竖屏的视口，Configuration 也可能先于窗口尺寸更新。
- **不是所有窗口都请求旋转**。只有普通的窄窗口全屏时才请求横屏；宽窗口、分屏、有分隔的折叠窗口，以及 Android 16+ 上最小宽度达到 600dp 的窗口，都原地全屏。
- **详情保留组合**。满窗播放器盖在详情上方时，详情只是停止绘制（画中画同理），标签页、列表位置和草稿都还在；它不可交互时清除语义、拦截触摸并禁止焦点进入，关闭相关弹层，并暂停隐藏评论的相邻页加载。
- **画中画与全屏**。进入系统画中画时暂时解除方向请求，展开回应用后按进入时记录的方向策略恢复全屏。

### 系统栏样式

`MainActivity` 复用状态栏和导航栏的两份 `SystemBarStyle.auto`，它们的检测回调在**执行时**读取当前的启动覆盖层、页面、明暗模式和全屏状态。

AndroidX 会保存首次传入的样式，并在视图收到配置变化时重新应用。如果回调捕获的是启动时的固定值，旋转之后视频页黑底上的白色图标会被覆盖成黑色。

Activity 配置变化时先立即更新一次，再通过 `decorView.post` 在视图的配置分发结束后恢复最新的系统栏显示、交互行为和“关闭导航栏对比度强制遮罩”的设置。待执行的恢复只保留一份，Activity 销毁时移除。

## 小窗与画中画

[PlaybackHost](../../app/src/main/kotlin/moe/kirakira/feature/player/PlaybackHost.kt) 统一管理 Activity 生命周期、屏幕常亮、系统画中画的资格和来源矩形。它与导航宿主通过 [PlaybackViewportState](../../app/src/main/kotlin/moe/kirakira/ui/navigation/PlaybackViewportState.kt) 共享页面的几何与层级。

### 整页 Zoom 而不是迁移 Surface

播放页与应用内小窗之间的切换是**整页 Zoom**：`PlaybackHost` 持续承载同一棵 `VideoPage`，页面始终按窗口尺寸测量，动画只改变外框的位置、大小、圆角、阴影和整页图层的等比缩放。`PlayerContentFrame` 的位置、实例和视频 Surface 都不因切换而重建或迁移，也不用封面或截图顶替视频输出。

- **曲线按方向选择**。放大进入播放页、从小窗恢复和取消返回手势时，使用与 SwiftUI `.smooth` 相同算法的临界阻尼弹簧，到达全屏时不回弹；返回卡片或收为小窗时使用轻微欠阻尼的弹簧。
- **转场交接携带速度**。取消动画时，`Animatable` 会清空内部速度，因此由同一驻留页在动画帧中保留速度，由接手的动画读取。动画落定或被强制定位时清零。预测性返回把手势中的位移叠加到未完成的展开上，交接时合成两路速度。手势采样沿用普通预测性返回的时间来源，与点击身份和转场交接的生命周期无关。
- **展开时的落点也用卡片的实时位置**，找不到时才用点击时记录的矩形。返回途中反悔时，来源页可能已经滚动过，两种位置不一致会让外框错位。
- **缩向小窗时卡片展开继续完成**。进入到一半就返回并缩为小窗时，卡片进度不停在半路，而是在小窗动画下面走完，之后恢复播放页时才是完整尺寸。这段补完只移动外框，不接管背景：背景此时归小窗的返回时间轴所有。小窗返回只有从完整页面开始时才算背景被遮住，进入到一半时背景可见，从屏幕上的值接着走。
- **绘制值要限幅**。位移、缩放和裁剪保留弹簧超出目标的进度；圆角限制为非负，透明度与阴影进度限制在 0–1，避免回弹产生非法的绘制值。
- **圆角**在卡片圆角、屏幕圆角和小窗圆角之间按同一进度插值。屏幕圆角取 Android 12+ 的窗口 Insets 报告的物理边角半径，系统没有报告的边角以零为目标。
- **停在整页时不保留外框效果**。展开弹簧落定后，外框就是窗口本身：形状换成矩形，关闭裁剪，阴影归零，封面覆盖层也移出组合，观看期间不再录制或合成这些图层。动画最后停在屏幕的物理圆角上，所以切换为矩形看不出来。一旦返回或预测性返回让进度离开整页，下一帧就恢复圆角、阴影和封面。
- **关闭小窗**用独立的加速曲线，以当前位置的中心为锚点缩小到零，结束后释放会话。
- 系统画中画沿用平台的窗口动画。

### 来源卡片

首页、搜索、标签、资料作品和历史的视频卡片通过 [VideoZoomSource](../../app/src/main/kotlin/moe/kirakira/ui/components/video/VideoZoomSource.kt) 登记封面的实测矩形、圆角和已加载的图片 Painter。

- 点击时保留 Painter，导航替换了来源 Scene 之后仍能绘制，不依赖原节点的图层。目的地的图片请求完成前，也用这个 Painter 作为 Coil 的占位，避免重新加载时露出空白。
- `PlaybackHost` 等当前视频的完整页面完成布局后才开始展开，以播放区域对齐来源封面。
- 回到卡片期间，确认驻留页已布局且有可绘制的封面后，**只暂停来源封面的绘制**，由动画层独占封面直到回弹结束。否则回弹缩过终点时会露出第二层静止的封面。卡片的布局、文字和触摸范围不受影响。隐藏按来源实例记录，可以同时有多张，因为旧视频还在收回时新视频已经选中了另一张卡片。
- 来源按页面、列表和视频 ID 匹配。历史正文和搜索浮层可以同时显示同一视频，两张卡片各自保留封面、坐标和点击动作。来源卡在 Scene 重建期间也按这个身份隐藏封面，避免新节点尚未布局时短暂画出第二层封面。收回途中找不到封面时沿上一个有效位置继续飞行，避免跳回全屏。卡片滚出视口时沿轨迹淡出，列表被切走时则退出整个飞行。
- 详情由 `VideoViewModel` 在当前账号首次创建时加载。首次进场、从其他页面返回和从小窗恢复都只恢复交互，不自动重刷。

### 与导航的配合

视频路由通过 `VIDEO_ZOOM_METADATA` 声明“动画由驻留层承载”，`NavDisplay` 因此只在推入和弹出视频页时关闭 AOSP 横移；从视频页再打开普通页面时仍使用普通转场（见[视频页的转场](navigation.md#视频页的转场)）。

- 普通页面及其退场 Scene 位于驻留视频层的上方。视频 Scene 把普通横移的进度传给驻留层，驻留的视频页与普通页面共用同一个转场时钟。普通转场期间暂停小窗 Zoom；进入普通页面完成后，在遮挡下切为小窗并淡入。
- **普通转场途中返回卡片**。从作者页等普通页面返回视频页的转场还没结束时再按返回，只有栈中最近的视频 Scene 可以驱动驻留层的普通转场，否则它会跟着周围的普通出栈把驻留页整页滑出，飞行落到卡片左边，同时因为“正在普通转场”而撤掉背景景深。返回开始时驻留页保留当时的转场偏移，再随收回进度把它归零：从屏幕上的位置出发，落在卡片上。
- 视频 Scene 保留来源页的组合和布局，生命周期限制为 STARTED，并屏蔽背景交互。驻留视频页完全盖住来源页时，Scene 只在绘制阶段跳过来源内容，返回所需的滚动位置和封面坐标仍然保留。收回卡片时这个 Scene 随视频路由一起立即退出，屏蔽随之解除。
- **预测性返回直接驱动 Zoom**。手势开始时根据播放资格确定目标，避免播放状态在拖动途中变化时切换路径。 预览绑定手势开始时的视频路由，换页后旧手势的进度和目标不会传给新页面。提交时如果播放器已不具备小窗资格，就回到来源卡片；取消则恢复整页。手势在展开动画途中开始时，不把进度设成“整页减去手势进度”（那会先跳回整页），而是让未完成的展开继续走，手势在它之上按比例拉回。手势期间按采样估算速度，松手后的动画以手指的速度起步。
- **缩到小窗时的提交顺序**：在返回事件里先提交小窗的播放状态，再移除视频路由。顺序反过来的话，驻留层会在“路由已退出、小窗状态尚未发布”的空档被移除。
- **回卡片时路由立即出栈**，视频页和 Surface 由 `exitingVideoId` 留在驻留层，退场完成后才释放会话。这样来源页在动画期间就能接受点击。驻留层这时画在 `ActivityNavDisplay` 之上，即使露出的页面本身盖着另一个视频；露出的是另一个视频页时则仍按旧顺序，等退场完成再出栈，否则那个页面会立即接管播放会话。晚到的完成回调不能清理新的视频。 如果新路由还没接管播放会话就收到返回，直接出栈，避免把上一段会话的状态用在新视频的退场上。
- **返回卡片时，点击按页面上的位置判断**，而不是按飞行中的视频。外框飞回途中会扫过别的卡片，按外框判断的话，点别的视频有概率点中正在返回的那个。
  - **手势由实际命中的卡片接收**。视频路由出栈后，来源页切换 Scene，卡片的点击节点可能在松手前被替换。宿主保留本次按下命中的卡片，只有原节点已被替换、手势仍是未消费的单指点击时，才用同一页面、列表和视频 ID 的新节点补全点击。底栏等悬浮控件按正常命中规则接收事件，封面坐标只用于绘制。
  - **页面照常接收输入**。收回卡片的驻留层把触摸交给下方页面，列表可以边滚动边更新落点。刚露出的来源页可提前接收输入，例外仅适用于这次返回的来源。已出栈的 Video Scene 放行触摸，焦点和无障碍语义照旧隔离；还留在栈内、被作者等页面盖住的 Video Scene 保持完整屏蔽。
  - **旧页面上的触摸**。返回请求生效到来源页完成布局之间，命中测试可能仍落到旧播放页。驻留层拦截这次手势，也拦截返回前按下但尚未松手的手势，避免旧播放器响应。来源页完成布局后的点击按正常命中规则处理。
  - 被系统取消的触摸（`ACTION_CANCEL`，例如从边缘开始的返回手势）在 Compose 里表现为一次已被消费的松手，两处点击判定都把它排除，不当作点击。收为小窗时没有卡片可点，仍由飞行中的视频本身接住点击并恢复。这段监听不以“是否在转场”为键，而是在按下时读取：以它为键的话，转场结束会重启监听，跨越结束的点击会丢失松手。按下时在转场、松手时已落定，则按小窗自己的点击处理（显示或隐藏控件）；多指触摸和超过触摸阈值的拖动都不会触发恢复，阈值按窗口坐标量，因为外框缩得最快时，静止的手指在外框坐标里也会越过阈值。预测性返回的预览期间由手势驱动，不响应。
- **来源离开时沿原路径消散**。换标签或换页面时，正在飞行的封面和背景效果快速淡出，来源封面随自己的页面离场。淡完即可释放退场会话。来源可见性由列表报告，回收或重建单个列表项不会被当作换标签。 同一页面在两个 Scene 短暂并存时，两份卡片和列表登记都保留到各自离场，避免其中一份先卸载时丢失仍在屏幕上的落点。若又打开来自同一列表的视频，旧动画可在新视频下继续落地。已分离的动画也遵守同样的规则。
- **收回期间打开另一个视频**。暂停旧播放器并保留到退场结束，新视频可同时展开。退场回调按会话实例释放播放器。旧播放页刚分离、驻留层还未重组时，完成回调也能凭同一次返回找到已移交的会话。重新点击任何一张仍在收回的原卡片时，对应的驻留页保留原来的飞行进度与速度。
- 展开小窗时校验播放会话仍属于该视频，并由 `openFrom` 确认显示这个小窗的页面仍在栈顶。

### 背景的模糊与景深

Android 12+ 上，`AppNavHost` 在 `ActivityNavDisplay` 的图层上模糊并压暗后方的页面；驻留播放器在独立的同级图层绘制，视频、封面和控件保持清晰。强度由 `PlaybackViewportState` 提供，模糊半径以半像素为一级、压暗以 8 位 alpha 为一级，四舍五入量化，同一级别内的进度变化不会让效果图层更新；只有镜像边距依赖缩放：缩放在 `backgroundScale` 里统一量化（每单位 4096 级，手机宽度上每级边缘移动不到半个像素），图层、镜像边距和驻留层的逆变换看到同一个值，边距不会与页面错开；量化级别、模糊级别和尺寸都没变时复用上一个效果，不重建。级别不能按 dp 向上取整：那样只要强度大于零就至少保留 1dp 的模糊，复原曲线的长尾里这层模糊一直停着，最后一帧才整块消失；半像素的最后一级则看不出跳变。

“背景景深”开关开启时，Android 13+ 上页面按强度略微缩小。缩小是图层变换（`scaleX`／`scaleY`），而不是着色器里的坐标映射：返回卡片期间页面接受点击，命中测试和坐标必须跟随画面，否则点在屏幕边缘附近的卡片会落空或点到相邻的视频，飞回的视频也会落向未缩小的位置。坐标随之有两条约定：来源卡片的矩形用 `boundsInWindow(clipBounds = false)` 取整块变换后的矩形，不能用变换后的左上角加布局尺寸，否则缩小时目标偏大、中心偏移；窗口矩形进入驻留层之前先撤销驻留层自身的缩小，因为收回途中被下一个视频盖住的旧页面自己也随背景缩小，直接把窗口坐标当作局部坐标会缩两次，换回真实卡片时跳一下。撤销用的是施加缩小的同一个函数（`backgroundScale`）按窗口中心求逆，而不是读 `windowToLocal`：这层效果是在旧页面被摘下时才挂上的，挂上后的几帧里布局坐标仍按错误的变换换算（实测比例约 2.3 倍），飞行会被放到屏幕外，留下的卡片空白就是闪白。缩小后露出的边距由外层图层的 `VideoZoomEdgeMirror` 填充：一个 AGSL `RuntimeShader` 把缩小后的页面沿四边各镜像一次，再链接模糊，只改变绘制。露出的边距是页面边缘的镜像，模糊之后与页面连续，没有接缝、黑边或圆角裁剪。Android 12 不支持 `RuntimeShader`，只有模糊和压暗；开关关闭时只保留压暗。

每个视频对背景的推远由 [BackgroundPushMotion](../../app/src/main/kotlin/moe/kirakira/feature/player/BackgroundPushMotion.kt) 持有，分成两路：`strength` 驱动模糊和压暗，上限为进入时的满强度；`depth` 驱动缩放，不会浅于 `strength`。展开和预测性返回时，推远与进度一一对应。

返回卡片或收为小窗时，背景不跟随回收进度，而是走自己的时间轴：`PlaybackHost.animateReturn` 用回收弹簧的 `getDurationNanos` 算出它从当前位置和速度收敛到阈值的总时长（包含回弹），背景在同样的时长内归零。跟随进度的话，欠阻尼弹簧第一次越过终点时强度就已归零，回弹期间背景早已静止。背景归零后保持原位，直到下一次展开主动接管，避免视频尚在回弹时又把背景推远。复原也不能比前景晚结束，因为来源卡片就在背景里，景深未归零时前景落到卡片上会跳一下。

- **从完整页面返回**：背景完全被视频盖住，起点可以任选。缩放从比进入终点更深的位置开始（`RETURN_BACKGROUND_DEPTH` 倍），退场的位移因此比进入大；模糊和压暗仍从满强度开始，避免退场显得更浑浊。曲线是 Material 3 的 emphasized decelerate：背景在视频起跳的同一帧就开始复原，大部分变化落在前段，然后贴着回弹慢慢落定；ease-in-out 起步太慢，显得拖沓。
- **背景已经可见时返回**（进入到一半，或预测性返回提交后）：从屏幕上的值和速度接着走，用按总时长定刚度的临界阻尼弹簧归零。进入时背景正在被推远，所以它带着惯性再退一点、转向、再回来，和视频自己的反向一致；幅度来自真实的速度。这里不人为加深：背景可见时，任何额外的后退都要在很短的时间里走完，看起来就是一次跳动。
- **收回途中又展开**：切回跟随进度，被打断的时间轴与进度之间的差值作为余量保留，带着速度沿展开弹簧衰减到零。

**切换不能有空档。** 两种来源之间的交接有三个要求，少一个都会出现跳变或一两帧完全不模糊：

- 跟随进度的公式不依赖路由状态。返回请求在重组时生效，而接管推远的协程晚一帧才开始，这一帧仍然读到进度对应的值。
- 每次交接都换上新的 `Animatable` 并在同一处切换模式，而不是先 `snapTo` 再切换；`snapTo` 要等上一个动画让出互斥锁，中间可能画出一帧。
- 推远的动画运行在宿主的协程作用域里，不随发起它的 `LaunchedEffect` 取消。它只会落定或被下一段动画接替，不会停在半路，也不需要用 `finally` 复位。

每个驻留页按视频 ID 各自登记一份，背景取最大值，所以旧视频收回与新视频展开重叠时背景不会先跳回清晰。

重叠期间摘下的旧页面画在页面层之上，背景效果原本落不到它身上：后方的页面连同它要落回的卡片一起被模糊、压暗、缩小，旧页面却保持清晰和原尺寸，像贴在玻璃上。所以摘下之后它也算背景的一部分，图层套用同样的效果，强度只取其他视频的部分，自身的复原不作用于自己。缩小改用普通的图层缩放，锚点与背景相同，这样旧页面仍落在缩小后的卡片上；不用镜像着色器，因为透明图层的边缘镜像会把旧页面复制到边距里。原生视频 Surface 不受 RenderEffect 和颜色滤镜影响，所以摘下时让封面快速淡入，盖住暂停的画面。到落地时旧页面自己的复原已经归零，它的模糊与背景一致，换回真实卡片时不会跳。

### 保留的视频页

[RetainedVideoPages](../../app/src/main/kotlin/moe/kirakira/feature/video/RetainedVideoPage.kt) 按视频 ID 隔离播放页的组合。新视频入场和旧小窗退场期间，两个页面各自保留组合，旧小窗沿用原来的 Surface，退场结束后才销毁。退场的会话可以同时有任意多个（`PlaybackViewModel.miniPlayerExits`），每个有自己的驻留页和几何快照，按实例释放；快速连续切换时每个旧视频都能完整落地，不会被后一个顶掉。同一视频再次打开时，它仍在退场的会话立即结束。

- ViewModelStore 由宿主级的 `RetainedVideoPageViewModel` 按“账号修订加视频 ID”持有，跨 Activity 配置重建保留已加载的数据和内存草稿。路由和小窗都不再引用时清理；账号修订变化时清理旧账号的 Store。
- 缩为小窗时，页面生命周期限制到 STARTED，详情停用，原播放器的控件层从组合中移除；视频 Surface 继续驻留。
- 从小窗切换到另一个视频时，旧播放器暂停并退出媒体会话和历史采样，留在原 Surface 里播放退场动画，结束后按**实例**释放。如果原视频路由仍留在栈内，路由只保留页面状态，等退场会话释放后再接管驻留页，避免中途移除负责释放播放器的退场逻辑。

### 图片查看器盖在视频上

从视频页打开图片时，`PlaybackHost` 保留整页和原生 Surface，禁止缩为小窗并停用交互，图片导航层固定画在它上方。从其他页面查看图片时，则隐藏应用内小窗并撤下它的视频输出，关闭后恢复。图片查看器及其退出转场可见期间禁用系统画中画资格，避免把查看器画进画中画窗口。

### 小窗与系统画中画

- 小窗的位置按可用区域的横纵比例保存，拖动结束后贴向左右边缘。布局使用 safeDrawing 与 IME 的 Insets，并排除铰链和主界面导航的实测占位。外框以窗口的物理左上角为原点，内部控件仍跟随阅读方向。
- 小窗的控件显隐是局部状态，每次出现时默认显示，无操作几秒后隐藏；缓冲、首次加载和触摸探索时强制显示。隐藏时清理焦点与语义，并用置顶的点击层拦截退场期间的点击。
- **系统画中画的进入方式**：Android 12+ 使用 `setAutoEnterEnabled`；Android 8–11 在 `onUserLeaveHint` 里检查“应用外小窗”开关和播放资格。没有手动的画中画按钮。
- **宽高比要落在系统允许的范围内**。边界取 `100/239` 和 `239/100`，中间值限制整数分子，避免下限经截断后越界。更新平台参数时保留对参数和 Activity 状态异常的保护：失败时禁用当前的画中画资格，而不是让播放器崩溃。
- 不申请悬浮窗权限，不使用前台服务，也不做后台音频播放。

## 播放器首次加载背景

首次加载有三个相互独立的信号：

| 信号 | 含义 | 何时结束 |
| --- | --- | --- |
| `artworkPending` | 首次详情尚未返回封面地址，或封面还在加载 | 封面加载成功或失败；没有封面时不保持加载状态 |
| Media3 的 shutter | 首帧渲染前，用封面盖住视频表面 | 首帧出现后直接撤下，没有淡出动画 |
| `initialLoading` | 当前分 P 的播放器尚未首次就绪 | 首次进入 `STATE_READY` |

- 封面等待只在**尚未创建媒体播放器**时使用，并且不提前加载媒体；已有详情时的刷新不再设置 `artworkPending`。
- `PlayerContentFrame` 把封面插槽交给 Media3 的 `ContentFrame.shutter`。视频表面始终参与组合，初始化和首帧事件才正常。
- `PlaybackViewModel.artwork` 在内存里保存当前视频的封面地址，供应用内小窗复用；系统画中画不显示封面。
- `initialLoading` 按分 P 的 ID 记录在内存里，不进 SavedState。内部重建播放器、切换分 P 和重试时保留记录；切换视频、账号、关闭小窗或离开页面且没有转入小窗时清空。
- 播放器先赋给会话，再调用 `prepare`；监听器只接受当前实例的就绪和错误事件。

## 历史记录与续播

`AppDependenciesViewModel` 在 Activity 宿主作用域持有唯一的 [HistoryRepository](../../app/src/main/kotlin/moe/kirakira/data/history/HistoryRepository.kt)，用宿主的 `viewModelScope` 管理内存缓存、共享读取和串行的进度队列。历史页的 `HistoryViewModel` 只持有搜索和刷新任务。

### 数据

- 仓库按会话 revision 清空缓存并取消旧账号的读写；游客不调用历史接口。
- Rosales 的单位不统一：`duration` 是秒，`lastUpdateDateTime` 是毫秒时间戳，`anchor` 是秒数的字符串。它们在历史的边界统一换算成毫秒；负数、非有限或溢出的锚点不参与展示和续播。
- **接口没有分页，也没有删除能力。** 历史读取不传 `videoTitle`，标题和作者的过滤在本地完成。
- 队列与缓存不落盘。进程结束时的提交只是尽力而为。

### 进度同步

- 采样附着在共享的播放器上，而不是视频页面，所以覆盖小窗、全屏和系统画中画；进入后台时额外 flush 一次。采样策略在 [PlaybackHistoryRecorder](../../app/src/main/kotlin/moe/kirakira/feature/player/PlaybackHistoryRecorder.kt)。
- **只同步首 P**。提交内容是 `uuid`、`category=video`、字符串形式的视频 ID 和向下取整的秒数。这个锚点协议与 Apple 客户端共用，不能扩展，所以其他分 P 不能覆盖首 P 的秒数。
- 同步队列串行执行，每个尚未发送的视频只保留最新的进度。失败后不自动重放，也不弹周期性的提示。
- 历史页刷新时先等待当时的同步任务，避免从视频返回时读到尚未提交的锚点。刷新期间新提交成功的记录会合并进返回的列表；下一次刷新重新以云端为准，这样内存里的更新不会盖掉其他设备的新进度。

### 续播

- 首次的视频详情与续播准备并发执行。续播准备等待已排队的进度提交和 `loadIfNeeded`，**总等待上限 1500 毫秒**；失败或超时按“没有云端位置”处理，迟到的查询结果不用来跳转。
- 自动播放等待续播快照；手动播放不受历史等待的限制。
- [PlaybackResumeController](../../app/src/main/kotlin/moe/kirakira/feature/player/PlaybackResumeController.kt) 持有“待决策、已处理、等待实际时长校验”三种状态。已有的播放器、保存的分 P 和本地位置优先；手动播放、切换分 P 或跳转之后，不再应用迟到的续播位置。
- Media3 给出实际时长之后才检查“距离结尾”的阈值。临近结尾而重新从头播放时，不显示续播提示。
- 续播提示是只存内存的状态，几秒后清除，不进 SavedState。

采样与续播的具体阈值，以及页面上的交互，见[功能现状的历史记录](../features.md#历史记录)。

## 图片查看与导出

### 转场

图片页通过 [ImageViewerNavigation](../../app/src/main/kotlin/moe/kirakira/ui/navigation/ImageViewerNavigation.kt) 把打开、关闭和预测性返回三种转场写进官方 `NavDisplay` 的条目元数据，做法与 [Navigation 3 的转场配置](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)一致。打开时保留原位的来源页直到转场结束。

**不要对 `Scene.key` 做路由类型判断**：那个键来自 `NavEntry.contentKey`，是用于内容身份和状态恢复的字符串，不是路由对象。

资料头像、顶部横幅与查看器用 Compose 的 `SharedTransitionLayout` 和 `sharedBounds` 连接（[ImageTransitions.kt](../../app/src/main/kotlin/moe/kirakira/ui/navigation/ImageTransitions.kt)）。

- **共享键**是 `profile/<来源页实例 UUID>/<图片身份>`，横幅用同一实例下的 `banner` 键。带上实例 UUID 是为了在多个资料页并存、头像更新时不会错误配对。
- **来源图片何时隐藏**。只在匹配的共享动画正在进行、且目标已经能绘制占位或图片时隐藏；动画停止绘制覆盖层的同一帧恢复原位的图片。`isMatchFound` 可能一直持续到查看器出栈销毁，**不能单独用它控制头像的显隐**。
- 头像的白色圆框留在资料页原位，不参与共享边界动画。圆形的点击与水波纹层和共享图片并列、单独裁剪，不给共享图片的父容器再加圆形裁剪。
- 共享边界插值时采用 `RemeasureToBounds`，让图片在变化的宽高比里重新排版；横幅在来源的 Crop 与全屏的 Fit 之间按边界尺寸插值填充方式，避免直接切换。
- 返回时用 Telephoto 当前的内容几何反向补偿缩放，所以手势取消时不需要改它内部的缩放状态。
- 指定了共享键但来源不在组合中时，只做淡入或淡出。没有共享键的查看器使用 `imageStandaloneMotion`，缩放只作用于图片图层。

共享边界、页面显隐和视频 Zoom 的曲线集中在 [MediaZoomMotion](../../app/src/main/kotlin/moe/kirakira/ui/components/motion/MediaZoomMotion.kt)。

### 返回与下拉退出

- 图片页的跟手返回遵循预测性返回的开关。开启时使用独立的 `NavigationBackHandler`；关闭时由 `ActivityBackHandler` 处理完成，手势进度不传给 `NavDisplay`。处理器随开关的切换重新绑定，避免沿用旧的手势状态。
- [ImageDismissGesture](../../app/src/main/kotlin/moe/kirakira/feature/imageviewer/ImageDismissGesture.kt) 管理下拉退出。只在原图可用、未放大、没有缩放动画和文件操作时，才接管明显向下的单指拖动。手势监听放在**静止的容器**上，图片变换放在它的子层，否则图层移动会改变输入坐标。
- [ImageDismissNavigation](../../app/src/main/kotlin/moe/kirakira/ui/navigation/ImageDismissNavigation.kt) 用独立的 `DirectNavigationEventInput` 向 Navigation 3 提供“零进度的返回预览”，拖动和恢复期间来源 Scene 因而保留。这个输入不接收系统返回，所以不受预测性返回开关的影响。提交后由同一个共享返回转场接手，不会先复位再退出。

### 实例身份

每次打开的 `ImageViewerRoute` 都生成一个可保存的 `instanceId`。返回状态与处理器按当前条目的身份创建，关闭按钮和系统返回都校验目标实例，移除之后再次回调不会重复出栈。系统栏的样式按仍在组合中的查看器实例计数，包含退出动画，旧实例销毁不会覆盖新实例的状态。

[ViewerImage](../../app/src/main/kotlin/moe/kirakira/feature/imageviewer/ViewerImage.kt) 是可序列化的图片描述。它的来源模型 [ImageSource](../../app/src/main/kotlin/moe/kirakira/data/image/ImageSource.kt) 支持 drawable 资源 ID、可读的 `content://` URI 和公开的 HTTPS 图片 URL。调用方负责保留 URI 的读取授权。

视频封面从“更多”菜单的“查看封面”打开，使用不限制宽度的原图地址；不传共享键，不与视频卡片或播放器关联。

### 图片加载与降级

[ViewerImageLoader](../../app/src/main/kotlin/moe/kirakira/feature/imageviewer/ViewerImageLoader.kt) 是应用一侧的 Coil 与 Telephoto 适配器。

- 请求以查看器根布局的**实测视口**为准，而不是共享动画首帧的头像尺寸；预览解码的边长和总面积都有上限。
- 正常路径从 Coil 的磁盘快照或本地来源加载原图分块。快照的锁一直持续到解码器和进行中的 tile 操作都释放。
- 内存预览还在、但磁盘文件已被淘汰时，在同一时限内绕过内存读取补齐一次。
- 资源检查、快照读取、分块初始化和 tile 解码的普通异常，统一降级到已解码的预览；AVIF 等不能分块的格式直接使用预览。取消继续传播。
- 原图请求有总时限；请求成功后仍迟迟显示不出首帧，也降级为预览。完全失败时保留黑底、关闭和重试。
- 每次重试使用独立的适配器，旧回调不能写入新状态。手动重试绕过旧缓存的读取，避免再次命中损坏的内容。

**诊断日志**：关闭查看器的路径都经过 `closeImageViewer` 并记录原因。仅 Debug 构建带有 `ImageViewer` 诊断日志（实例 ID、重试编号、加载阶段、生命周期、转场、栈成员和明确的关闭原因），用来区分实际出栈、页面销毁和绘制异常。日志不输出 URL、用户文案、异常详情或会话信息。

### 图片控件与原图导出

控件显隐由 [ImageViewerControlsState](../../app/src/main/kotlin/moe/kirakira/feature/imageviewer/ImageViewerControlsState.kt) 管理，空闲时长遵循 `AccessibilityManager.calculateRecommendedTimeoutMillis`。只在页面 RESUMED、转场结束且没有触摸、控件焦点或结果提示时计时。

缩放使用 [Telephoto](https://saket.github.io/telephoto/zoomableimage/) 的 `ZoomableImage`。界面根节点只观察触摸，不消费事件；单击使用 [Telephoto 的 `onClick`](https://saket.github.io/telephoto/zoomableimage/#click-listeners)，不叠加 `clickable`，否则会抢走双击和缩放。

[ImageExportRepository](../../app/src/main/kotlin/moe/kirakira/data/image/ImageExportRepository.kt) 负责保存与复制：

- **始终导出原始字节和 MIME 类型**，不导出缩略图或预览。
- 保存：Android 10+ 通过 `MediaStore.Images` 和 `IS_PENDING`；Android 8–9 取得旧版写权限后写入公共的 `Pictures/KIRAKIRA` 并触发媒体扫描。
- 复制：把原始字节写进专用缓存（`cacheDir/copied_images`），通过 `FileProvider` 交给系统剪贴板；超过七天的复制缓存在下一次复制时清理。
- 资源来源只支持能被 `BitmapFactory` 解码的位图 drawable，不支持 XML 或矢量 drawable。
- 远程下载使用单独配置、无认证的 OkHttp 派生客户端，限制 HTTPS 重定向次数和 32 MiB 的文件上限；取消时关闭请求并清理临时文件。

宿主作用域的 [ImageExportViewModel](../../app/src/main/kotlin/moe/kirakira/feature/imageviewer/ImageExportViewModel.kt) 按查看器实例记录保存和复制的状态。

- 已开始的导出不随图片页出栈而取消；结果由导航宿主的 Snackbar 提示，查看器关闭之后仍能看到。系统画中画内不显示。
- 任务只在宿主的生命周期内保留，不保证在 Activity 结束或进程终止之后完成。
- 不同查看器实例的图片**复制**共用一个宿主级的 `Mutex`，按发起顺序执行，避免慢的旧任务盖掉后一次复制。保存不受这把锁限制。

## 弹幕发送样式

`DanmakuStyle` 包含 RGB 颜色、字号、模式和彩虹开关。`VideoViewModel` 以只读 Flow 暴露当前视频的样式；样式只保留在条目 ViewModel 里，账号修订变化时与草稿一起重置。

- 发送时捕获正文、时间和样式的快照。Repository 把颜色转成不带 `#` 的六位大写 RGB，发送 Rosales 的 `color`、`fontSize`、`mode`、`enableRainbow` 字段。
- 提交期间禁用编辑；失败保留草稿和样式，成功只清空正文。
- 样式面板里的自定义选色在同一个面板内进入子页，确认之后才应用颜色（背景的选择见[弹层背景](theme.md#弹层背景)）。
- 位置与方向用 `Segmented` 展示四档；任一标签超过两行或分段过窄时，整组改用纵向的单选列表。
- “创作者专属渐变”的文案和示例沿用 Cerasus 的 `FlyoutDanmakuFormat.vue`。播放器、预览和开关示例共用 `DanmakuColors.rainbowOutline` 的颜色定义。

## 弹幕显示与设置

### 数据

`DanmakuEntry.style` 保留 Rosales 的颜色、字号、模式和彩虹标记。缺失或未知的样式回退为白色、中号、右向左、无彩虹；非法的时间和空白的正文不进入列表。

- 弹幕与视频详情一起加载，列表和播放器共用同一个 Flow。渲染层不发请求，也不维护“本地发送副本”。
- **后端没有分 P 字段**。弹幕池以视频 ID 为单位，按当前分 P 的播放进度解释。
- 请求是公共读取，不做个性化过滤。

### 渲染

[DanmakuOverlay](../../app/src/main/kotlin/moe/kirakira/feature/player/DanmakuOverlay.kt) 使用单个 Compose Canvas，放在画面上方、控制层下方，不拦截触摸，也不逐条播报。视口是整个播放器画布（含视频画面之外的黑边）。

[DanmakuTimeline](../../app/src/main/kotlin/moe/kirakira/feature/player/DanmakuTimeline.kt) 按时间稳定排序并预先排轨：

- 帧查询用二分定位候选的时间窗口，窗口宽度取当前弹幕池的最长显示时长。
- 同向的弹幕检查共享生命期两端的间距，防止追尾；反向和固定的弹幕不与尚未离开的其他类型共用轨道。
- 垂直空间按每条文字布局的**实际高度**分配，不按最大字号统一行高。
- 放不下就跳过，不延迟补发。

**滚动时长沿用 Cerasus 的等时长模型**：基准时长与播放器宽度成正比，并限制在 6–8 秒之间，再除以用户设置的滚动倍率。同一画面里所有滚动弹幕的显示时长相同，实际路程是视口宽度加文字宽度，所以长句移动得更快。6–8 秒是本客户端自己的体验参数：延长窄视口的阅读时间，限制宽视口的停留时长。固定弹幕的时长固定。时长都按媒体时间计算。

**帧循环直接读 Media3 的 `currentPosition`**，不用页面的 500ms 轮询。暂停或缓冲时冻结并等待 Player 事件；空白区等待到下一条的播放时间；seek、恢复和倍速变化通过监听器唤醒。画中画、页面非活动、播放器释放和播放失败时移除绘制层；退出画中画后按真实进度重建，不补发错过的弹幕。

绘制上的两个细节：

- **每次绘制都要显式设置 `Fill`／`Stroke` 和阴影**。同一份缓存的文字布局会保留画笔状态，不能指望空的 `drawStyle` 把描边恢复成填充。
- 极长的文字在绘制层省略，避免超出文字布局允许的尺寸；列表里保留完整正文。多行正文只在画面中转成空格。

### 设置

`DanmakuSettings` 与 `DanmakuSettingsStore` 位于 `data/settings/`，`DanmakuSettingsViewModel` 由导航宿主持有，向设置页和播放器提供同一份状态。它只写 `kirakira_settings` 下独立的 `danmaku_*` 键，不覆盖播放或主题设置；范围和步长由 `DanmakuSettingRanges` 统一归一化，模型和滑杆共用。加载完成前禁用编辑，也不绘制弹幕。

### 选型

采用自有的 Compose 绘制，没有引入引擎依赖，也没有移植外部源码。对照过的项目：

| 项目 | 没有采用的原因 |
| --- | --- |
| [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) | 四种模式齐全，但需要适配旧的 View 引擎 |
| [AkDanmaku](https://github.com/KwaiAppTeam/AkDanmaku) | 引入 libGDX 和 ECS |
| [DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) | 默认缺少反向模式 |
| [tdanmaku](https://github.com/NihilDigit/tdanmaku) | 早期接口把反向滚动降级为普通滚动 |

字号与行高参考了 Cerasus（按每条文字的实际高度排布）和上述项目，但取值是本客户端为较小的视频视口做的取舍，不是直接复制 Cerasus 的 CSS 数值。

## 评论与弹幕颜文字

### 输入区

评论与弹幕共用 [ContentComposer](../../app/src/main/kotlin/moe/kirakira/feature/video/ContentComposer.kt)。

- **未登录时整条编辑区替换为登录按钮**。`onLogin` 回调非空即表示需要登录；登录事件复用视频页的暂停播放和认证导航流程，登录后不自动发送。
- **控件保持在同一个组合位置**。输入框以草稿是否为空，在单行药丸和两行编辑布局之间切换；输入框、颜文字／样式按钮和发送按钮始终留在同一组合位置，否则首次输入时会丢失焦点、输入法组合状态或按钮的交互状态。
- **编辑状态的归属**。`TextFieldState` 持有输入、光标、选区和输入法组合态，ViewModel 持有字符串草稿。发送前同步最新文字；成功后显式清空编辑器和撤销历史，旧草稿的延迟编辑不能覆盖清空的结果。
- 颜文字通过 `edit` 替换当前选区，插入后光标移到颜文字末尾；超出长度上限时整项拒绝，不截断。
- `ComposerState` 在视频页为评论和弹幕分别创建，按视频 ID 和会话修订隔离。可保存状态只记录分类和选区偏移；面板的打开状态不恢复。切换标签页、打开样式、全屏和画中画都会关闭面板。
- `FloatingComposerLayout` 实测包含底部安全区的完整输入区高度，列表据此留白，末项才能滚到输入区上方。

输入框与发送按钮的阴影见[按钮动态阴影](theme.md#发送按钮)；与视频 Pager、点击外部关闭的配合见[输入区与颜文字面板](content.md#输入区与颜文字面板)。

### 颜文字面板

[KaomojiPicker](../../app/src/main/kotlin/moe/kirakira/feature/video/KaomojiPicker.kt) 接收分类、最近记录、事件回调和内容安全内边距。静态目录完整保留 Cerasus 的五类 268 项原始字符，来源与许可见 [Cerasus 资源](../../third_party/cerasus-icons/README.md)。

- 面板顶部两角的圆角半径来自共享的 [windowCornerRadius](../../app/src/main/kotlin/moe/kirakira/ui/adaptive/WindowCornerRadius.kt)，与预测性返回使用同一算法和回退值，读取规则见 [Android 转场适配边界](../../third_party/android-motion/README.md#公开-api-适配边界)。
- 面板等 IME 收起之后才显示；背景铺到窗口底部，安全区放进面板内容里，不重复处理导航栏或 IME 的 Insets。
- 面板打开期间，外层的视频 Pager 停用手势分页并换成空的嵌套滚动连接，分类 Pager 的越界滑动不会带动视频的标签页（见[输入区与颜文字面板](content.md#输入区与颜文字面板)）。面板是否关闭取决于视频 Pager 的 `currentPage` 是否变化。
- 高度过渡使用 `clip = false`，面板上沿和圆角外侧的原生投影才不会被裁掉。

### 最近使用

`AppDependenciesViewModel` 持有唯一的 [RecentKaomojiStore](../../app/src/main/kotlin/moe/kirakira/data/kaomoji/RecentKaomojiStore.kt)，导航宿主级的 `KaomojiViewModel` 负责读取和写入，各视频条目共享它。

| 项目 | 说明 |
| --- | --- |
| 存储 | 独立的 `kirakira_kaomoji` SharedPreferences；**不参与系统备份** |
| 容量 | 最多 24 项，去重；只接受目录里存在的值 |
| 归属 | 设备级，不属于账号私有数据 |

Store 不创建自己的协程作用域，只提供挂起的读写；读写先取得 Mutex 再切到 IO 调度器，首次读取和后续写入因而串行，连续选择的顺序得以保留。格式损坏的记录回退为空。写入失败时保留内存列表，下次选择时重新提交完整记录。
