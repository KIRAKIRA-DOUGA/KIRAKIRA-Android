# 播放、图片与弹幕

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护播放会话、全屏与小窗、续播、图片查看和弹幕输入渲染。开发约束以贡献指南为准。

- [视频数据与播放器](#视频数据与播放器)
  - [播放器控制与设置](#播放器控制与设置)
  - [全屏与系统栏](#全屏与系统栏)
  - [小窗与画中画](#小窗与画中画)
  - [播放器首次加载背景](#播放器首次加载背景)
  - [历史记录与续播](#历史记录与续播)
- [图片查看与导出](#图片查看与导出)
  - [图片加载与降级](#图片加载与降级)
  - [图片控件与原图导出](#图片控件与原图导出)
- [弹幕与输入区](#弹幕与输入区)
  - [弹幕发送样式](#弹幕发送样式)
  - [弹幕显示与设置](#弹幕显示与设置)
  - [评论与弹幕颜文字](#评论与弹幕颜文字)

## 视频数据与播放器

视频画面的控制层局部提供 `LocalRippleConfiguration`，统一使用白色水波纹并保留官方透明度与动效，包含进度条和媒体错误页重试按钮；弹出的清晰度与倍速设置面板继续跟随应用主题，进度条本身保留主题色。弹幕 Switch 开启时使用白色轨道、黑色滑块与白色图标，关闭时使用透明轨道、白色描边及滑块与黑色图标；禁用状态显式使用半透明配色，不随浅深色主题变化，保留官方尺寸、状态语义和划线过渡。

`PlaybackSettings` 模型与 `PlaybackSettingsStore` 位于 `data/settings/`；`PlaybackSettingsViewModel` 通过该存储封装异步读取 `kirakira_settings` 的 `playback_in_app_mini_player`、`playback_outside_app_mini_player`（均默认 true）与 `playback_autoplay`（默认 false），使用 SharedPreferences apply 异步落盘。不迁移旧画中画开关。应用级状态经导航传入视频页与播放设置页，不绑定账号。

`VideoPlayer` 接收播放状态、Media3 画面实例和事件回调，不持有 ViewModel。返回按钮和控制栏共享显隐状态与三秒计时器；缓冲、失败、进度交互、设置面板及触摸探索阻止自动隐藏，暂停与结束状态同样支持轻触显隐和三秒自动隐藏。控件使用主题效果动效，退出动画期间禁用交互并清除语义。控制层以黑色渐变遮罩衬托白色图标和文字，播放／暂停按钮居中，使用 60% 不透明度的黑色圆形容器和 Rounded FILL 1 白色图标，加载时同一 64dp 圆形容器内改为 40dp 白色 Material 2 圆角圆形加载器，不提供播放点击动作；底部不再使用主题 Surface。

Slider 使用官方 `SliderDefaults.Thumb`（16dp 等宽高，保留默认按压／拖动形变），以两层 `SliderDefaults.Track` 显示缓冲和已播放位置；上层未播放轨道透明，底层只绘制、不添加交互或语义。两层使用相同圆头尺寸，保留官方定位手势与无障碍语义。`PlaybackViewModel` 在 Media3 事件及现有 500ms 进度轮询中读取 `bufferedPosition`，按有效时长限制范围；未知时长与播放器释放时显示零缓冲，不持久化缓冲位置。

`data/content/ContentRepository` 将私有 serialization DTO 映射为 `VideoSummary`、`VideoDetail`、`PublicProfile`、`VideoComment` 和 `DanmakuEntry`，UI 不持有 Cookie 或 token。资料映射拒绝非正 UID，以及响应中 UID 与目标不一致的结果，避免将异常资料归给目标用户。首页、视频、资料分别由 ViewModel 管理；视频与资料的 ViewModel 绑定 Navigation 3 条目。

`VideoRoute(videoId)` 和 `ProfileRoute(uid)` 使用真实 ID，旧无 ID 的演示路由与已移除的加载动画演示页路由在恢复时移除；旧路由类型仅保留用于反序列化兼容。状态变更通过 SessionState.revision 取消旧账号工作；请求前取得账号快照、返回时再次核对 revision，旧响应不覆盖新页面。启动时内容加载等待本地会话恢复及 `INITIALIZE` 账号校验结束（含失败），期间手动刷新也不提前请求，避免本地恢复和校验完成两次发布 revision 导致列表清空重载、加载指示器闪回；正常账号切换仍取消并清空旧账号内容。明确 401 只清除发起请求账号的凭据，其他业务拒绝不会自动清空会话。

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

普通播放、应用内小窗和系统画中画统一复用 [`PlayerContentFrame`](../../app/src/main/java/moe/kirakira/feature/player/PlayerContentFrame.kt)。它保留 Media3 默认的 `SurfaceView`、`ContentScale.Fit` 和遮挡层，在具有明确尺寸的容器上使用 `clipToBounds()`。Android 11 的 `SurfaceView.clearSurfaceViewPort()` 使用 `Canvas.drawColor(..., PorterDuff.Mode.CLEAR)` 清空当前 Canvas 裁剪区域，Compose `AndroidView` 默认不按布局边界裁剪，可能将先前绘制的页面底色一并清空；Android 12 起改为按视图宽高执行 `punchHole`。外层矩形裁剪将清空范围限制在视频视口内，沿用默认合成策略，不增加 Offscreen 图层，也不裁剪播放控件或页面其他阴影。

播放器控制层的显隐独立于控件业务可用状态：`AnimatedVisibility` 使用主题淡入淡出动效，隐藏时按钮、开关与进度条保持原有配色，退出完成后移出组合；`enabled` 仅表达媒体、轨道或页面等实际可用条件。画面显隐点击由播放器与控件的共同父容器处理，接收未被按钮、进度拖动或滚动消费的轻触，避免全屏滚动控件布局挡住独立背景点击层。开始隐藏时在控制层上方放置独立的画面点击层，点击只恢复控制层并重置计时，不触发底下控件。退出期间清除控制层语义、阻止焦点进入并释放该层已有焦点，操作回调再次检查显隐状态，避免未结束的交互提交操作。暂停与播放结束不再强制显示控件，均支持轻触显隐与三秒自动隐藏；缓冲、错误、进度拖动、设置面板和 TalkBack 触摸探索期间保持显示。

### 播放器控制与设置

覆盖层遮罩在上下边缘分别渐隐，中部透明；控件默认使用白色，已播放轨道及非 1× 的倍速入口沿用主题强调色。返回与全屏入口使用官方 FilledIconButton；图标容器与画质／倍速按钮均使用 60% 不透明度黑色，禁用时为 30%，不添加阴影。`PlayerSettingsButtons` 使用半透明黑色背景的官方 TextButton 与同一小号尺寸对应的形状、内边距、文字及图标 API，外围按钮统一为官方小号 40dp 容器高度，图标统一使用 IconButtonDefaults.smallIconSize 的 24dp 尺寸，与标准 TopBar 一致；触摸目标继续由官方组件保证。

倍速入口在 1× 时使用圆形 FilledIconButton，不显示数值；非 1× 时使用与画质等高的图标文字按钮并改为主题 primary。AnimatedContent 以实际显示的倍率字符串（1× 对应空串）为目标，所有倍率变化都触发内容淡入／淡出和容器尺寸过渡，尺寸使用主题 fastSpatialSpec 弹簧，颜色与透明度使用 fastEffectsSpec；因此 1.5× 到 2× 等非默认倍率间也有动画，退出内容保留原倍率，不闪出 1×。首次显示直接采用当前状态，遵循系统动画时长设置。

顶部使用透明官方 TopAppBar，将返回放在 navigationIcon、画质与倍速放在 actions，复用默认栏高、内容边距和垂直对齐；actions 内部通过 FlowRow 换行，底部时间沿用主题 labelMedium 字体。私有 `PlayerControlsLayout` 使用 SubcomposeLayout 先测量顶部、时间、弹幕／全屏操作与 36dp 高度进度布局区，将 64dp 播放容器定位到视频画面的几何中心，图标与缓冲指示器均为 48dp；全屏时补偿不对称 safeDrawing Insets 对布局中心的偏移。根据实测控件矩形检查居中按钮是否重叠，仅在发生重叠或越界时将播放／缓冲控件以 48dp 容器排入底部操作区。

底部按实测宽度换行，极短窗口允许整个控件布局纵向滚动，避免控件互相覆盖。顶部位置由官方 TopAppBar 管理，底部采用进度在上、操作在下的两行布局：36dp 进度布局区紧接 48dp 最小高度的操作行，不叠加 TopBar 行高的额外留白；操作行距播放器安全视口底边保留 4dp 留白；4dp 轨道与 16dp 手柄在各自内容插槽中同步向下偏移 8dp，使可见内容靠近操作行，36dp 进度布局区与下方操作行的测量及点击区域仍按顺序排列，不通过重叠布局缩短间距。中央按钮碰撞检查使用包含该视觉偏移的进度轨道／16dp 手柄实际可见高度，以及底部各操作的实测矩形；全屏操作尾部为 4dp，保持与顶栏最右侧图标按钮同列，组内间隔为 8dp。

时间起点为 16dp，进度条扣除官方手柄半径后设置外边距，使轨道端点与 16dp 内容边界对齐；弹幕 Switch 保留官方尺寸和状态动效，轨道在开关两种状态下均为 60% 不透明度黑色并带半透明白色描边，开启时使用白色滑块与黑色图标，关闭时滑块降低不透明度并保留划线提示，禁用状态同步降低对比度。全屏由控件层避让 safeDrawing Insets，顶栏的 windowInsets 为零以免重复避让，遮罩仍覆盖整个画面。播放形变状态、弹幕划线状态和进度拖动状态保留在自适应布局之外，重排不重播动效或清空拖动预览；错误态单独预留顶部返回与全屏工具栏，继续使用统一 MEDIA 错误组件。

播放／暂停图标使用 `AnimatedPlaybackIcon` 的路径形变，两种切换均顺时针旋转 90°，采用主题的 Material 3 Expressive `fastSpatialSpec` 弹簧，与划线图标共用动效曲线，不做透明度切换。路径插值与旋转保留超过 1 的弹簧进度，使形变和旋转略微超过目标后回弹。目标路径反向补偿 90°，结束后仍为正向 Material Symbols Rounded 图标，并归一化内部坐标。连续切换从当前可见轮廓继续向最新状态顺时针形变，不排队或倒放。动画状态保留在加载与控制层显隐分支之外，首次显示直接采用当前播放状态，恢复控制层不重播；使用 Compose 动画时钟遵循系统动画时长设置。绘制缓存复用，两种尺寸模式共用组件，颜色及无障碍描述继承原按钮。

 图标及操作描述由 `showPauseIcon` 驱动：Media3 `playWhenReady` 为真且未结束、未报错时显示暂停图标，缓冲不会使其切换为播放图标；暂停、播放结束和释放后显示播放图标。`playing` 继续表达 `isPlaying`，用于原有控制层及画中画行为。图标随目标状态变化播放动画，不再使用点击计数或加载结束补播逻辑；Material 2 圆角圆形加载器仍用于缓冲展示，其间图标动画状态在外层继续更新。

画质选项由 `PlaybackViewModel` 在 `EVENT_TRACKS_CHANGED` 中读取 `currentTracks`，仅包含解码支持且高度有效的视频轨道，同高度去重并降序排列；同高度候选优先当前视频组，再按码率选择。画质领域选项同时携带该候选轨道的高度与有效码率，确保显示码率与手动选择一致；UI 将 bit/s 除以 1000 并四舍五入，以 Kbps 显示在行尾，缺失或非正数时不显示。手动模式通过 `TrackSelectionOverride` 覆盖视频轨道，自动模式仅清理视频覆盖，不改音轨和播放位置。每次准备新媒体重新匹配首选高度；未匹配到时显示自动而不改持久偏好。实际清晰度从 `videoFormat` 获取，并随进度轮询更新，避免将自适应组的多个选中轨道误认为当前画质；释放时清空轨道状态。

`PlaybackSettings` 增加 `autoQuality` 和 `preferredVideoHeight`，由既有应用级设置存储保存为 `playback_auto_quality` 与 `playback_video_height`，默认自动且没有首选高度；UI 通过导航宿主回调更新。倍率、连续调速、保持音调由导航宿主 `PlaybackViewModel` 管理，以非敏感标量存入 SavedState；重建播放器时应用 `PlaybackParameters(speed, pitch)`，保持音调时 pitch 为 1，否则为 speed。无级值限制在 0.25–4 并保留两位小数，有级模式吸附既定倍率。

`PlayerSettingsSheet` 使用官方 `ModalBottomSheet`，宽度上限 640dp，只允许展开和隐藏状态；内容滚动并承载底部安全内边距。画质单选及倍速开关使用官方分段列表；「连续调速」与「保持音调」沿用设置页普通开关规则，以 onClick 重载切换状态并提供 Switch 角色与 toggleableState 语义，尾部 Switch 不独立处理点击，整行背景和圆角不随选中状态变化。倍速滑杆以 log2 映射 -2–2，默认 1× 位于中心，并为无障碍有级调整提供相邻倍率操作。面板局部状态参与控制栏显隐计时，关闭后重新计时；全屏、画中画、失去活动状态或播放错误时关闭，不持久化面板打开状态。

`PlaybackSpeedGauge(speed, playing, modifier)` 使用 Compose Canvas 绘制刻度与指针、Text 显示倍率读数，只消费现有 `PlayerUiState`，不持有播放器或修改业务状态。`playing` 来自 Media3 `isPlaying`，实际倍率为 `if (playing) speed else 0f`，不使用缓冲期间仍为真的 `showPauseIcon`。正倍率以 `180° + 180° × (log2(speed) + 2) / 4` 映射 0.25×–4×，1× 为 270°；零刻度单独设在 150°，为零刻度与 0.25× 之间保留足够弧长，不计算 `log2(0)`。

`animateFloatAsState` 使用 300ms 的 `tween`，复用共享 `rememberEmphasizedEasing()` 的 Material 3 emphasized 无回弹曲线，不使用弹簧；首次直接采用当前角度，快速切换从当前动画位置继续，绘制角度限制在 150°–360°，动效遵循系统动画设置。指针与大刻度复用 `primary`，码表和滑杆直接使用 `SliderDefaults.colors()` 默认颜色：活动轨道与非活动圆点为 `primary`，非活动轨道与活动圆点为 `secondaryContainer`；经典强调色在主题层生成带强调色调的次要容器；刻度文字为 `onSurfaceVariant`。

布局最大宽度 320dp，表盘几何以宽度／1.7 为基准高度，字体实测尺寸参与半径与标签位置计算；标签与弧线外缘留出 8dp 间隔，先保留 0、0.25、1、4 标签，仅在不重叠时显示 0.5、2。读数靠近轴心下方，以主题 `headlineLarge`、Medium 字重和 `onSurface` 显示；按 `0.25×`、`1.25×`、`3.99×` 等宽读数预留固定避让空间，避免倍率变化推动滑杆，暂停指针至少距读数 24dp，并根据固定字体高度增加布局高度，保持下方滑杆位置与读数不重叠。中英文码表无障碍描述报告实际倍率，Text 与滑杆表达设定倍率，不受指针插值或归零影响。

码表指针按用户提供的设计参考采用细尖、圆底的渐窄轮廓，以缓存的闭合路径绘制，中心端与尖端宽度比例为 5:1，轴心至尖端的长度为轨道中心半径的 68%；尖端保留小圆角，中心端为一体半圆，不叠加独立圆轴。中心端半宽限制在 4dp–8dp，避免窄屏或大字体下过细。外弧为 8dp 宽的填充环形路径，断口使用 2dp 圆角，避免分段形成独立胶囊。0、0.25、0.5、1、2、4 为大刻度，以 2dp 宽、12dp 长的圆头径向短杆绘制，统一使用主色。轨道按大刻度分段，根据刻度半宽和 3dp 间隔计算角度退让；路径圆角随短弧长度收缩，极短区间空间不足时省略弧段及圆点，避免重叠。

每段弧线中心放置一个小圆点，不按不均匀的快捷倍率分布，尺寸复用 `SliderDefaults.TickSize`，活动圆点使用 `activeTickColor`，其余使用 `inactiveTickColor`。表盘半径扣除轨道半宽，为外缘和标签保留空间；文字仅显示倍率数字，单位由读数表达。设计理由见 [Material Symbols](../../third_party/material-symbols/README.md#播放器控制图标)。

`AppNavHost` 持有共享 `PlaybackViewModel`，视频页、小窗与系统画中画使用同一 ExoPlayer／MediaSession。视频页只在活动且匹配当前视频时绑定 `PlayerContentFrame`；小窗位于导航内容上层，系统画中画从小窗进入时切换为全窗口视频。同一时刻仅一个画面绑定。导航完成后根据 `showPauseIcon`（含缓冲待播放）决定是否保留应用内小窗；预测性返回取消不修改模式，返回全屏先退出全屏。回到视频页重新读取详情时，同分 P 的媒体 URL 更新不打断当前播放。关闭小窗、账号修订变化和普通后台路径释放会话；旋转保留 ViewModel，进程恢复只读取非敏感标量并保持暂停，不保存 URL 或凭据。

### 全屏与系统栏

视频页的播放器固定在 `VideoPlayerViewport` 的同一个组合位置，仅在嵌入布局与满窗布局之间按实际窗口测量结果切换，不对尺寸、位置或详情透明度施加全屏动画。视频输出保持不透明，继续使用同一个 `SurfaceView` 与播放会话。`VideoScreen` 用嵌入播放器高度占位，详情始终保留组合中的 Tab、列表位置与草稿；满窗播放器覆盖其上时通过 `drawWithContent` 停止详情与栏面阴影绘制，画中画同样处理，不使用透明度转场。详情不可交互时清除语义、拦截触摸并禁止焦点进入，关闭评论跳页与弹幕样式弹层，并暂停隐藏评论的相邻页加载。播放器占位区域与 Tab 共用的顶部背景及底边阴影保持原有布局。

全屏入口通过同一 `requestFullscreen` 回调立即同步页面目标与 Activity 的方向、系统栏请求，返回按钮与系统返回也由该入口立即退出。进入时复制当前嵌入播放器的宽高和 Insets，等待横屏期间固定这些值，避免系统栏隐藏或键盘收起使画面提前位移；满窗条件同时确认 Configuration 已横屏与实际布局宽大于高，避免键盘压缩竖屏视口或 Configuration 先于窗口尺寸更新时误判。横屏窗口首次布局时直接满窗，已横屏时也直接切换。多窗口及 Android 16+ 最小宽度达到 600dp 的大屏不等待方向变化，按当前窗口全屏。退出立即恢复普通布局与原方向策略。快速切换始终使用最新目标，没有动画完成回调或固定延时；页面停用与出栈清理窗口状态，画中画暂时解除方向请求并直接使用全窗口视口，展开回应用按原目标重新请求全屏。

`MainActivity` 复用状态栏与导航栏的两份 `SystemBarStyle.auto`，检测回调执行时读取当前启动覆盖层、页面、明暗模式与全屏状态。AndroidX 会保存首次传入的样式，并在视图收到配置变化时重新应用；回调不捕获启动时的固定值，避免旋转后将视频页黑底上的白色图标覆盖为黑色。启动覆盖层优先使用自己的明暗模式，视频页与图片查看器使用白色状态栏图标，图片查看器与视频全屏使用白色导航栏图标，其他情况跟随当前应用主题。Activity 配置变化时先立即更新，再通过 `decorView.post` 在视图配置分发结束后恢复最新的系统栏显示、交互行为与关闭导航栏对比度强制遮罩的设置；待执行恢复只保留一份，始终读取最新目标，销毁 Activity 时移除，不使用固定延时。

全屏转场交由 Android 默认窗口旋转处理，系统关闭动画时同样按实际窗口切换，不在应用中模拟旋转轨迹或修改旋转时长。此选择保留 [Media3 推荐优先使用的 SurfaceView](https://developer.android.com/media/media3/ui/surface)，使用系统默认的 [ROTATION_ANIMATION_ROTATE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#ROTATION_ANIMATION_ROTATE) 行为；具体效果取决于系统与设备实现。

Compose [共享元素转场](https://developer.android.com/develop/ui/compose/animation/shared-elements#current-limitations)目前不支持 View／Compose 互操作，不能直接用于含 `AndroidView` 的视频输出。下方内容保持状态，不要求在窗口旋转时固定于原竖屏坐标。

### 小窗与画中画

`PlaybackHost` 统一管理 Activity 生命周期、屏幕常亮、系统画中画资格和来源矩形；小窗位置以可用区域的横纵比例保存，拖动结束贴左右边缘，布局用 safeDrawing 与 IME Insets 并扣除实测主界面底栏高度。MainActivity 负责平台画中画桥接和系统栏，Android 12+ 使用 `setAutoEnterEnabled`，Android 8–11 在 `onUserLeaveHint` 检查应用外开关与播放资格；比例限制在系统支持区间。不新增悬浮窗权限、前台服务或后台音频播放。全屏使用 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出恢复此前方向策略；进入系统画中画暂时解除方向请求，展开后恢复全屏。播放器弹幕开关继续使用官方 Switch 和 Cerasus 图标，暂停时保留，手动画中画按钮已移除。

应用内小窗的控件显隐与交互计数使用局部 `remember` 状态，每次小窗出现时默认显示；`LaunchedEffect` 在 3 秒无操作后隐藏，按钮按压与拖动期间暂停计时，操作结束后重新计时。自动隐藏与点击隐藏使用同一个更新入口，同步修改显隐状态与交互计数。缓冲、首次加载及其背景退场、TalkBack 触摸探索开启时强制显示，普通暂停不延长显示时间；触摸探索监听与视频页复用。单击画面切换显隐，左上角展开按钮调用原有 `onRestore`，中央播放按钮沿用播放图标动画和 24dp／2dp 加载器，右上角关闭沿用会话释放逻辑。

三处按钮使用白色平面官方 `IconButton`，正常槽位为 48dp，窄窗口限制到宽度的三分之一，图标按可用槽位缩小；整窗 50% 黑色遮罩与按钮持续保留在组合中，由宿主的 `animateFloatAsState` 驱动共享 `graphicsLayer.alpha`，显示使用主题默认效果动效，隐藏保留主题快速效果动效。计时结束或点击隐藏只改变透明度目标，点击拦截层的显隐不重建淡出状态；淡出过程中再次显示，从当前透明度继续动画，遵循系统动画时长设置。隐藏时清理控件焦点与语义，用置顶点击层拦截退场期间的点击，按钮回调也校验显隐状态，避免显示控件的点击触发播放、展开或关闭。显隐不进入 ViewModel、SavedState 或设置，不改变系统画中画的纯视频画面。

### 播放器首次加载背景

封面等待使用普通 Spinner：`VideoPage` 通过 `PlayerUiState.artworkPending` 表达首次详情尚未返回封面地址，`VideoArtwork` 通过可选 `onLoadingChange` 回调上报 Coil 的加载、成功和失败；播放器按图片地址持有局部加载状态。只有未创建媒体播放器时使用封面等待，在中央播放按钮内复用 40dp 白色 `IndeterminateCircularProgressIndicator`，保留原有黑色圆形容器与自适应位置；成功或失败后立即恢复播放图标，不绘制或淡出几何背景，无封面不保持加载状态。封面等待期间保持控件可见，按钮仍按媒体可用性启用，保留播放操作的无障碍描述。封面等待不写入分 P 就绪记录、不提前加载媒体；点击播放后的媒体首次加载继续使用几何背景。首页、历史等其他封面调用保留原样，已有详情刷新不再设置 `artworkPending`。

`PlaybackViewModel` 在内存中按分 P ID 保存首次就绪记录，`initialLoading` 经 `PlayerUiState` 传给页面；不进入 SavedState 或设置。仅准备未就绪的分 P 时开启，当前播放器首次进入 Media3 `STATE_READY` 即关闭，即使此时处于暂停状态也完成记录。内部重建播放器、切换分 P 和重试保留记录；切换视频、账号、关闭小窗及离页且未转入小窗时清空。首次失败不记录就绪，重试继续显示背景；已就绪媒体失败后重试只使用普通缓冲加载器。播放器赋给会话后再调用 `prepare`，监听器只接受当前实例的就绪和错误事件。

`PlayerInitialLoadingOverlay` 在视频与弹幕之上、控制层之下绘制 Cerasus `LogoCover` 的几何动画，背景为主题 `surface`、图形为 `primary`，条带透明度为 30%。Compose 无限动画使用 96 秒公共周期，分别计算上游各图形的 6／8／12／16／32 秒线性条带运动、4 秒错峰加号翻转和三角形移动、2 秒交替闪烁及条纹缩放、4 秒交替圆环缩放、16 秒条纹圆旋转，保留 CSS 贝塞尔曲线与描边三角形的负延迟。交替动画的反向阶段反转缓动曲线，保持 CSS alternate 行为。几何参数按 dp 转换，窄小视口按较短边等比缩小，所有路径与裁剪轮廓复用缓存。宽度达到 640dp 时使用现有 KIRAKIRA 矢量品牌资源，右上角避让全屏系统栏；不引入播放器名称、YOZORA 图标或字体依赖。

首次开启直接显示背景，首次就绪用 CSS 默认 ease 曲线在 500ms 内淡出，不延迟播放；错误、释放或离开当前宿主时立即撤下。首屏及退场期间保持控件可见，隐藏画质／倍速入口；首次就绪前禁用进度拖动，返回和全屏继续可用。小窗共享同一首次加载状态及几何背景，省略品牌资源，保持原有小尺寸加载器与操作；系统画中画不绘制首屏。动画使用 Compose 时钟遵循系统时长倍率，系统关闭动画时绘制固定几何帧并立即完成退场。来源与许可见 [Cerasus 资源说明](../../third_party/cerasus-icons/README.md#播放器首次加载动画)。

### 历史记录与续播

历史页使用官方 `LazyColumn` 按日期标题与记录逐项懒加载，保留日期及视频的稳定 key，并以 `date`、`history`、`status` 区分 `contentType`。记录使用透明背景的官方 `ListItem`，通过 `Modifier.clickable(role = Role.Button)` 保留整行点击、涟漪及按钮语义，以官方行内留白分隔，不添加卡片圆角、阴影或分隔线。列表最大宽度为 840dp，水平 `contentPadding` 仅包含系统 Insets，记录使用 `ListItem` 的官方 16dp 水平内边距，日期标题与状态区域单独对齐到 16dp；顶部与底部安全区域继续放入滚动内容。封面与文字共用内容插槽，保留 16:9 封面、现有封面裁剪及进度条；有效宽度除以字体缩放小于 300dp 时改为纵向布局，避免大字体挤压文字。

`AppDependenciesViewModel` 在 Activity 宿主作用域持有唯一的 `data/history/HistoryRepository`，使用宿主 `viewModelScope` 管理内存缓存、共享读取及串行进度队列；历史页的 `HistoryViewModel` 绑定 `HistoryRoute`，只持有搜索和刷新任务。仓库按会话 revision 清空缓存并取消旧账号读写，请求前后核对账号快照，明确 401 交由统一会话失效处理，游客不调用历史接口。DTO 留在数据层，领域记录使用 `HistoryEntry`：Rosales 的 `duration` 为秒、`lastUpdateDateTime` 为毫秒时间戳、`anchor` 为秒数字符串，在历史边界将时长与有效锚点转换为毫秒；负数、非有限或溢出锚点不参与展示与续播。

历史读取不传 `videoTitle`，标题与作者过滤在本地完成；接口无分页或删除能力。首次视频详情与续播准备并发执行，不等待历史接口才请求详情。续播准备等待已排队的进度提交及 `loadIfNeeded`，总等待上限为 1500 毫秒；完成时由视频 ViewModel 发布一次续播位置快照，失败或超时按无云端位置处理，不使用迟到的查询结果跳转。自动播放等待该快照，手动播放不受历史等待限制。历史页刷新先等待当时的同步任务，避免从视频返回时读到尚未提交的锚点；刷新进行中新提交成功的记录合并到返回列表，后续刷新重新以云端列表为准，避免内存更新覆盖其他设备的新进度。

`PlaybackViewModel.setContent` 在新播放会话仅在续播准备完成后应用一次首 P 位置；已有播放器、保存的分 P 和本地位置优先，手动播放、切换分 P 或跳转后不再应用迟到的续播位置，手动跳转同时取消待执行的云端时长校验。Media3 提供实际时长后再检查距离结尾的阈值。进度采样附着在共享播放器，而不是视频页面，覆盖小窗、全屏和系统画中画；生命周期进入后台时额外 flush。提交只保存 `uuid`、`category=video`、字符串视频 ID 与向下取整的秒数，不扩展 Apple 共用的锚点协议，因此其他 P 不覆盖首 P 秒数。

同步队列串行执行，每个尚未发送的视频只保留最新进度。成功提交更新缓存中的位置、时间、封面与实际媒体时长；失败不输出服务端原文、敏感日志或周期性 Snackbar，不自动重放。队列与缓存不落盘，进程结束时的提交仅尽力完成；采样与续播阈值、页面交互见[历史记录功能](../features.md#历史记录)。

## 图片查看与导出

图片页通过 [ImageViewerNavigation](../../app/src/main/java/moe/kirakira/ui/navigation/ImageViewerNavigation.kt) 将打开、关闭、预测返回三种转场写入官方 `NavDisplay` 元数据，由 `ActivityScene` 继承顶部 `NavEntry` 的元数据。不要对 `Scene.key` 做路由类型判断：这里的键来自 `NavEntry.contentKey`，是用于内容身份与状态恢复的字符串，并非路由对象。打开时采用淡入与 `KeepUntilTransitionsFinished`，保留原位的来源页直到转场结束；返回时来源页无位移、查看页淡出。图片返回处理器读取同一份元数据标记，普通页继续使用原 AOSP 转场。接入方式与 [Navigation 3 官方转场配置](https://developer.android.com/guide/navigation/navigation-3/animate-destinations) 一致。

图片页始终使用独立的 `NavigationBackHandler`、图片 `PredictivePopTransitionKey`、`imageSharedBounds` 与 `imageReturnTransform`，不受普通页面的 `predictiveBackEnabled` 开关影响。关闭开关后，匹配来源的图片仍在按钮关闭、系统返回及边缘手势中缩回原位；无来源时沿用淡出，取消手势恢复原有查看与缩放状态。

资料头像与全屏查看器使用 Compose 官方 `SharedTransitionLayout` / `sharedBounds` 连接。共享键由来源页可保存的实例 UUID 与 UID／头像来源的 SHA-256 身份组成（`profile/<实例 UUID>/<图片身份>`），避免多个资料页及头像更新时错误配对；共享边界的几何缓存随来源或查看器参与者释放。图片页单独使用 Navigation 3 的可寻址预测返回进度；图片进出时来源页面保持原位，查看器直接覆盖其上，普通页面继续由 AOSP 动效宿主管理。

共享边界、页面显隐、背景与控件显隐共用 [EmphasizedEasing](../../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 的 Material 3 emphasized 曲线及 420ms 时长；Tab 点击也复用该曲线。共享边界插值时，图片由圆形头像逐渐展开成直角视口：采用 `RemeasureToBounds` 让图片在变化的宽高比中重新排版，避免全屏图片压缩时露出平直内容边缘。静止时由图片层裁剪，匹配过渡时仅由共享覆盖层裁剪；覆盖层圆角根据当前共享边界逐帧计算，关闭及预测返回时跟随实际收缩进度。

固定全屏的黑色背景由查看器页面绘制为不透明黑色，仅随 Navigation 3 页面转场淡入淡出，不再移入共享覆盖层或叠加自己的透明度动画。头像白色圆框留在资料页原位，不参与共享边界动画。返回时用 Telephoto 的当前内容几何反向补偿缩放，手势取消无需修改其内部缩放状态。来源不在组合中时仅淡入或淡出。用户关闭系统动画时，Compose
时长缩放统一生效。

[ViewerImage](../../app/src/main/java/moe/kirakira/feature/imageviewer/ViewerImage.kt) 是可序列化的图片描述，其来源模型 [ImageSource](../../app/src/main/java/moe/kirakira/data/image/ImageSource.kt) 支持 drawable 资源 ID、可读 `content://` URI 和公开 HTTPS 图片 URL，并保留迁移前的序列化类型名。描述接收默认 `null` 的可选 `thumbnail`；调用方负责保留 URI 读取授权。查看 UI 位于 `ui/components/image/`，接收受控的 `ZoomableImageSource`、加载状态、回调与可选共享元素修饰符；导出数据访问由 `data/image/` 负责，只接收来源与文件名。

查看器使用全屏黑色背景，系统状态栏和导航栏保持透明并使用浅色图标；透明顶栏仅放深色圆形底衬的白色关闭按钮，确保白色图片上仍有足够对比度；转场禁用点击时保留相同配色，由整体淡出控制透明度。下载和复制位于避开系统导航栏的右下角 `HorizontalFloatingToolbar`。两组控件通过共享转场覆盖层悬浮在图片上方并淡入淡出，黑底留在页面内。复用时将稳定的来源键同时用于缩略图的 `imageSharedBounds(key, viewer = false)` 与查看页的 `imageSharedBounds(key, viewer = true, drawable = 可绘制状态)`，并通过 `ImageViewerRoute` 打开页面。无可匹配来源时传入 `null` 键。

来源图片仅在匹配的共享动画进行且目标能够绘制占位或图片时隐藏，动画停止绘制覆盖层的同一帧恢复原位图片；`isMatchFound` 可能持续到查看器出栈销毁，不能单独用它控制头像显隐。占位复用资料头像同一尺寸分发地址与 Coil 缓存，不代表原图加载成功。头像圆框在来源页单独绘制于图片外沿，过渡期间始终保持原位；查看器不再创建对应的共享装饰层。圆形点击／水波纹层与共享图片并列，单独裁剪，不给共享图片的父容器再加圆形裁剪。

每次打开的 `ImageViewerRoute` 都生成可保存的 `instanceId`，缺少该字段的旧返回栈在反序列化时补上新身份。图片返回状态与处理器按当前条目身份创建，关闭按钮及系统返回统一校验目标实例，移除后再次回调不会重复出栈。系统栏按仍在组合中的查看器实例计数，包含退出动画；旧实例销毁不会覆盖新实例状态。同一期间 `PlaybackHost` 隐藏应用内小窗而保留播放会话，恢复点击同时校验来源栈顶和查看器状态，避免旧回调移除新打开的查看器。

### 图片加载与降级

[ViewerImageLoader](../../app/src/main/java/moe/kirakira/feature/imageviewer/ViewerImageLoader.kt) 是应用侧 Coil／Telephoto 适配器。请求以查看器根布局实测视口为准，使用 FIT／EXACT 预览解码，按视口缩小至单边不超过 4096px、总面积不超过 400 万像素，不取共享动画首帧的头像尺寸；窗口变化重建加载适配器并保留同一缩放状态。正常路径从 Coil 磁盘快照或本地来源加载原图分块，快照锁持续到解码器与进行中的 tile 操作都释放。内存预览仍在但磁盘文件已淘汰时，在同一时限内绕过内存读取补齐一次。资源检查、快照读取、分块初始化和 tile 解码的普通异常统一降级到已解码预览，取消继续传播；AVIF 等不能分块的格式直接使用预览。完全失败时保留黑底、关闭与重试，不调用导航。

原图请求及来源准备总时限为 45 秒，请求成功后 15 秒内仍无法显示首帧则降级预览。每次重试使用独立适配器，释放旧请求及快照，旧回调不能写入新状态；手动重试只写入内存／磁盘缓存、绕过旧缓存读取，避免重复命中损坏内容。仅 Debug 的 `ImageViewer` 日志记录生成的实例 ID、重试编号、加载阶段、显示状态、生命周期、转场、栈成员和明确关闭原因，不输出 URL、用户文案、异常详情或会话信息。手机上无操作自动退出的根因尚未确认，诊断用于区分实际出栈、页面销毁与绘制异常。

### 图片控件与原图导出

控件显隐由 [ImageViewerControlsState](../../app/src/main/java/moe/kirakira/ui/components/image/ImageViewerControlsState.kt) 管理，查看 UI 接收 `controlsVisible`、`onToggleControls` 和 `onInteractionChange`。默认空闲时长为 3 秒，遵循 `AccessibilityManager.calculateRecommendedTimeoutMillis`；仅在页面 RESUMED、转场结束且无触摸／控件焦点／结果提示时计时，加载、错误、权限请求与文件操作时保留控件。

界面根节点只观察触摸，不消费事件；单击使用 [Telephoto 的 `onClick`](https://saket.github.io/telephoto/zoomableimage/#click-listeners)，不叠加 `clickable` 抢占双击与缩放。顶栏和工具栏使用 Material 3 动效淡出，隐藏后移出点击和无障碍树，系统返回始终由导航宿主处理。

缩放继续使用 [Telephoto](https://saket.github.io/telephoto/zoomableimage/) 的 `ZoomableImage`，图片请求由上述适配器控制。资源来源的导出仅支持能由 `BitmapFactory` 解码的位图 drawable，不支持 XML／vector drawable；资源仍以 `@DrawableRes` 标记，使用 `openRawResource()` 获取编码字节。保存和复制始终使用 `ViewerImage.source` 的原始字节和 MIME 类型，不导出缩略图或预览：Android 10+ 通过 `MediaStore.Images` 的 `IS_PENDING` 公开，Android 8–9 获得旧版写权限后写入公共 `Pictures/KIRAKIRA` 并扫描媒体。复制将原始字节写入专用缓存，通过 `FileProvider` 交给系统剪贴板，超过七天的复制缓存于下一次复制时清理。网络头像通过公开 HTTPS 来源接口加载和导出。

[ImageExportRepository](../../app/src/main/java/moe/kirakira/data/image/ImageExportRepository.kt) 由宿主依赖容器注入独立、无认证的共享 OkHttp 客户端；远程下载在可取消回调内完成响应头与流读取，取消时关闭请求，外层始终清理临时文件。保留 HTTPS 重定向限制和 32 MiB 文件上限。宿主作用域的 [ImageExportViewModel](../../app/src/main/java/moe/kirakira/feature/imageviewer/ImageExportViewModel.kt) 按查看器实例记录保存／复制状态，同一实例操作期间禁用两个导出按钮，只将对应图标替换为固定 24dp 的白色转圈。关闭和缩放仍可使用，中央转圈仅用于图片加载。已开始的导出不随图片页出栈取消；导航宿主收集完成事件，以 Toast 提示成功或失败，包括 Android 13+ 的复制成功。权限拒绝继续使用带设置入口的 Snackbar。任务只在宿主生命周期内保留，不保证 Activity 结束或进程终止后完成。

不同查看器实例的图片复制共用宿主 `Mutex`，按发起顺序执行缓存准备、剪贴板提交及成功反馈，避免慢速旧任务覆盖后一次复制。排队期间保留对应按钮的加载状态；失败或取消会释放锁，后续复制可继续，图片保存不受此锁限制。

## 弹幕与输入区

### 弹幕发送样式

`DanmakuStyle` 领域模型包含 RGB 颜色、字号、模式和彩虹开关；`VideoViewModel` 以只读 Flow 暴露当前视频的样式，通过更新事件接收选择。样式只保留在条目 ViewModel 内，账号修订变化时与草稿一起重置。发送捕获正文、时间与样式快照，Repository 将颜色转换为不带 `#` 的六位大写 RGB，并发送 Rosales 的 `color`、`fontSize`、`mode`、`enableRainbow` 字段。提交期间禁用编辑，失败保留草稿与样式，成功仅清空正文。

评论和弹幕发送栏的容器色由共用 `ContentComposer` 内部统一使用 `MaterialTheme.colorScheme.surface`（浅色为纯白，深色遵循主题深灰），页面不单独覆盖。弹幕发送栏复用 `ContentComposer` 的可选尾部插槽。样式使用官方 ModalBottomSheet，自定义选色在同一面板内进入子页，复用已有 HSV 选色依赖；确认才应用颜色。面板打开时清除输入焦点并隐藏键盘。

`DanmakuStyleContent` 采用紧凑分组：标题与重置固定在顶部，100dp 预览、颜色、渐变开关、字号及位置选项放在可滚动的内容区，标题与控件间距为 8dp、组间为 18dp。内容区使用 `weight(1f, fill = false)`，短内容按实际高度显示，受限高度下独立滚动，底部保留 20dp 随内容滚动的留白。关闭沿用 ModalBottomSheet 的默认拖动手柄、下滑手势、遮罩点击与系统返回。系统栏及 IME Insets 由 ModalBottomSheet 处理，内容不重复追加。位置与方向按左右滚动、上下固定排列为等宽 2×2 FilterChip，复用 Rounded `west`、`east`、`vertical_align_top` 和 `vertical_align_bottom`，选中配色沿用 `primary/onPrimary`。

`DanmakuColorPalette` 将九种预设色与自定义入口排成两排五列，保持每格至少 48dp 的点击区域。可见色块使用官方 Extra Small `IconToggleButton` 容器，`IconButtonDefaults.toggleableShapes` 提供未选中圆形、选中圆角矩形及按压形状。色块与外描边共用动画 `Shape` 和交互源，使用主题 `defaultEffectsSpec` 插值，按压形状优先；色块传入官方组件的 `shape` 重载，描边按当前可见色块轮廓向外扩张 4dp，并同步扩张圆角，保持 2dp 主题色描边。未选中时不绘制边框；描边淡入和勾选的淡入缩放由同一选择 Transition 驱动，采用主题 `fastEffectsSpec` 与 `fastSpatialSpec`。动画值在轮廓绘制及 `graphicsLayer` 中读取，不改变色板测量尺寸。自定义颜色选中后以实际颜色显示，并将调色板图标过渡为勾选标记；当前 HEX 显示在颜色标题尾侧。默认样式下禁用重置。

色板之后的开关沿用 Cerasus `FlyoutDanmakuFormat.vue` 的「创作者专属渐变」／「Creator's Gradient」文案与 `rainbow-example`：24dp 前导区域内绘制 20dp、3dp 边框的粉蓝空心圆角示例。开关使用 `SegmentedListItem` 的 `onClick` 重载与 `Role.Switch`、`toggleableState` 语义，尾部 Switch 不独立处理点击，整行背景及形状不随开启状态变化。预览使用 14／20／28sp，彩虹描边参考 Cerasus 的粉蓝渐变；关闭系统动画时静态显示。预览独立于播放器，播放器使用更紧凑的三档字号与相同粉蓝描边，见下方弹幕显示实现。

### 弹幕显示与设置

`DanmakuEntry.style` 保留 Rosales 的颜色、字号、模式与彩虹标记；缺失或未知样式回退为白色、中号、右向左、无彩虹，非法时间及空白正文不进入列表。`VideoViewModel` 在账号就绪时与视频详情一起加载弹幕，列表与播放器共用同一 Flow。刷新及发送成功后的重新读取遵循现有账号修订与错误保留逻辑；渲染层不发请求、不维护本地发送副本。后端没有分 P 字段，弹幕池仍以视频 ID 为单位，按当前分 P 的进度解释；请求仍为公共读取，不扩展个性化过滤。

`DanmakuOverlay` 使用单个 Compose Canvas，放在 `ContentFrame` 上方、控制层下方，不拦截触摸也不逐条播报。视口按视频像素宽高比和容器尺寸执行 Fit，裁剪到实际视频区域顶部所选比例；未知视频尺寸时不绘制。`DanmakuTimeline` 按时间稳定排序、预先排轨，帧查询按当前弹幕池的最长显示时长，以二分定位候选时间窗口，覆盖低滚动倍率下最长 16 秒的弹幕；左右滚动位置按播放时间线性计算。同向弹幕检查共享生命期两端的间距以防追尾，反向和固定弹幕不与尚未离开的其他类型共享轨道；按每条文字布局的实际高度分配垂直空间，仅在横向运动可能相撞的弹幕之间保留 2dp 垂直间距，上下边缘各留 1dp。取消最大字号统一行高、整行倍数占位与均摊剩余高度；顶部和滚动模式从上向下寻找空隙，底部模式从所选区域底边向上寻找空隙。放不下则跳过，不延迟补发。横向追尾安全间距仍为 6dp。

播放器文字使用 12／16／22sp 与全局缩放，继续遵循系统字体缩放；显式使用 1.25em 行高、居中对齐、保留行高且关闭额外字体内边距。发送预览保持 14／20／28sp。颜色和描边样式与发送预览一致：普通弹幕为原色实心文字加黑色阴影（3px 模糊），彩虹弹幕先绘制粉蓝渐变描边（4px），再以原色填充，不加阴影。每次绘制显式设置 `Fill`／`Stroke` 和阴影；同一缓存文字布局会保留画笔状态，不能依赖空 `drawStyle` 将描边恢复为填充。TextMeasurer 使用 128 项缓存，帧循环只持有当前可见文字的布局；池布局分批让出协程，取消时终止计算。多行正文仅在画面中转为空格；超过 32,760px 的极长文字在绘制层省略，避免超出文字布局尺寸，列表保留完整正文。

滚动弹幕保留 Cerasus 的等时长模型：基准时长为 `clamp(实际视频宽度 px / (144dp 对应的 px) × 1000, 6000, 8000)` 毫秒，先限制基准时长，再除以用户滚动倍率并四舍五入为整数毫秒。6–8 秒是本客户端的体验参数，用于延长窄视口的阅读时间，并限制宽视口的停留时长。同一画面内所有左右滚动弹幕共享显示时长，实际移动路程为视口宽度加文字宽度，因此长句移动更快。默认 1× 时，360dp 宽显示 6000 毫秒，960dp 为 6667 毫秒，1440dp 为 8000 毫秒；0.5× 时为 12000–16000 毫秒，2× 时为 3000–4000 毫秒。固定弹幕保持 4000 毫秒，均采用媒体时间。默认倍率仍为 1×，已有用户倍率保持不变。

帧循环直接读取 Media3 `currentPosition`，不使用页面 500ms 轮询。暂停／缓冲时冻结并等待 Player 事件；空白区等待下一条的播放时间，seek、恢复、倍速变化通过监听器唤醒。池、尺寸和设置变化重建当前布局，视频／分 P／账号修订构成内容标识。画中画、页面非活动、播放器释放和播放失败时移除绘制层，取消协程及移除监听器；退出画中画后按真实进度重建，不补发错过的弹幕。

`DanmakuSettingsRoute` 使用现有 Navigation 3 宿主和可折叠顶栏。`DanmakuSettings` 模型与 `DanmakuSettingsStore` 位于 `data/settings/`，播放器直接依赖数据层模型。`DanmakuSettingsViewModel` 由导航宿主持有，向设置页与播放器提供同一份状态，通过该存储只写 `kirakira_settings` 下独立的 `danmaku_*` 键，不覆盖播放或主题设置。读取在 IO 调度器完成，写入使用 SharedPreferences.apply，范围与步长统一归一化；加载完成前禁用编辑并不绘制。偏好为设备级非敏感数据，沿用该偏好文件已有的备份白名单。

弹幕设置的滑块条目保留官方 `SegmentedListItem` 分组样式，标题与当前数值在 `content` 内同排，Slider 在 `supportingContent` 内占满内容宽度，避免尾部数值列压缩滑块。

字号与排布参考：Cerasus 使用 14／20／28 CSS px、继承 1.4 行高，其 `danmaku` DOM 引擎按每条文字的实际 offsetHeight 排布；DanmakuFlameMaster 同样基于 paintHeight 与 margin 寻找位置。tdanmaku 默认 15sp、1.6 倍行高。Android 此处选用 16sp 中号与 1.25em 行高，适配较小的视频视口，并采用逐条高度排布；这些值是本客户端的取舍，并非直接复制 Cerasus 的 CSS 数值。

采用自有 Compose 绘制，不新增引擎依赖或移植外部源码。选型对照：[DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) 提供四模式但需要旧 View 引擎适配，[AkDanmaku](https://github.com/KwaiAppTeam/AkDanmaku) 引入 libGDX/ECS，[DanmakuRenderEngine](https://github.com/bytedance/DanmakuRenderEngine) 默认缺少反向模式，[tdanmaku](https://github.com/NihilDigit/tdanmaku) 的早期接口将反向滚动降级为普通滚动；这些项目未进入应用依赖或源码。

### 评论与弹幕颜文字

`ContentComposer` 接收可空的 `onLogin` 回调，评论页直接透传，弹幕页通过 `DanmakuComposer` 透传；未登录时回调非空，整条编辑区替换为通栏 `ShadowButton`，复用中英文 `content_login_to_interact` 文案、官方 `ButtonDefaults.MediumContainerHeight` 及同尺寸的形状、内边距和文字 API，以最小高度适应字体缩放。登录按钮沿用 `ContentComposer` 的悬浮间距、640dp 宽度上限与底部避让，列表留白由 `FloatingComposerLayout` 按实测高度提供，不受编辑器的 `enabled`、`busy` 或草稿状态限制。进入登录按钮分支时关闭颜文字面板，当前活动页签清除焦点并隐藏键盘；弹幕样式面板的可保存状态同时按是否需要登录隔离，未登录时不展示。登录事件复用视频页的暂停播放和认证导航流程；登录后回调为空，恢复编辑区，不自动发送。列表标题下方移除原登录提示，提示资源同时供按钮和其他页面占位使用。

`ContentComposer` 的输入框 `Surface` 与独立发送按钮放在同一个底部对齐的 `Row` 内：输入框占剩余宽度，右侧发送按钮为固定 56dp 圆形 `FilledIconButton`，两者相隔 8dp。发送按钮位于输入框外，不参与框内测量和展开动画；整行实测高度用于扣除颜文字面板的可用空间。输入框以草稿是否为空切换单行药丸与两行编辑布局：空草稿时颜文字／样式按钮放在框内尾部，非空时放在下方操作栏。输入框、颜文字／样式按钮和发送按钮始终保留在同一组合位置，避免首次输入时丢失焦点、输入法组合状态或按钮交互状态。

`ComposerInputLayout` 按框内按钮实测宽高排布，容器高度和圆角使用主题 `fastSpatialSpec`，颜文字／样式按钮横向位移与编辑区域宽度使用 `defaultSpatialSpec`，不在中途硬切分段。颜文字／样式按钮锚定框内底部，仅在横向移动。横向进度采用保留符号的平方映射，越过展开位置时以连续阻力压缩回弹距离，保持在容器边缘内，不硬截断位置。按钮横向绘制使用浮点图层位移；归一化进度的结束阈值为 0.0001，避免长距离移动尚有数个像素时就瞬间归位。容器保留空间弹簧的轻微回弹，可用宽度、最小高度与合法圆角仍受布局约束。连续输入与清空从当前进度及速度转向最新状态，不排队；Compose 动画时钟遵循系统动画设置。

多行文字增减的 `animateContentSize` 仅作用于 `Surface` 内部的编辑区域，外层布局与 Surface 不裁剪阴影。使用 `TextFieldValue` 保存光标、选区和输入法组合状态，文字仍由原有 ViewModel 字符串草稿拥有。外部草稿清空时同步编辑值；颜文字替换 `selection.min..selection.max`，插入后光标移至末尾，不增补空格，超限整项拒绝。`ComposerState` 在视频页分别为评论和弹幕创建，并按视频 ID 与会话修订隔离；可保存状态只记录分类与选区偏移，正文继续来自 ViewModel，面板打开状态不恢复。旋转保留分类／选区，切换 Tab、打开样式、全屏和画中画关闭面板；只有当前活动页签可以拦截返回。

输入框与独立发送按钮共用固定 4dp 阴影高度，在所有交互及禁用状态保持一致；输入框与中性色发送按钮使用系统原生阴影，彩色容器的可用发送按钮使用共享双层彩色投影。禁用发送按钮保留不透明灰色配色与原生阴影。按钮保留发送条件、官方禁用语义与点击反馈，详见[按钮动态阴影](theme.md#按钮动态阴影)。

`KaomojiPicker` 接收分类、最近记录、事件回调与内容安全内边距。面板背景延伸至窗口左右及底部边缘；Tab 在内部避让左右安全区，网格将安全区加入 `contentPadding`，末项可滚动至导航栏上方，最近记录为空时的占位同样避让。面板背景直接使用主题现有的 `surfaceContainerLow`，浅色为接近白色的浅灰、深色跟随主题，不混合额外颜色。面板整体通过官方 `Surface.shadowElevation = 4.dp` 绘制固定的原生阴影，不叠加条目或 Tab 阴影。

形状为仅顶部两角带圆角的 `RoundedCornerShape`，底部两角保持直角；通过共享 [windowCornerRadius](../../app/src/main/java/moe/kirakira/ui/components/WindowCornerRadius.kt) 读取根窗口圆角并按当前密度换算为 dp，与 `ActivityNavDisplay` 的预测性返回使用同一算法及 28dp 回退，读取规则见 [Android 转场适配边界](../../third_party/android-motion/README.md#公开-api-适配边界)。

分类使用共享 `PagerTabRow` 的官方可滚动主 Tab，调用处将 `minTabWidth` 设为 `0.dp`，实际宽度由文字长度与官方水平内边距决定；内容由独立 `HorizontalPager` 承载，点击复用 `rememberTabChangeHandler`，指示器与视频页、资料页共用 `PagerTabIndicator`。Pager 从保存的分类初始化，`currentPage` 改变时通过回调更新 `ComposerState.category`，分类切换不会关闭面板。

静态目录完整保留 Cerasus 的五类 268 项原始字符。各分类使用固定四列 `LazyVerticalGrid`，每项独占一个单元格，不跨列；条目使用官方透明 `TextButton`，无容器填充与阴影，保留点击反馈、禁用语义及最小 48dp 高度。正文沿用 `bodyLarge`，通过官方 `TextAutoSize.StepBased` 在 `bodySmall` 至 `bodyLarge` 字号之间寻找适合单元格宽度的最大值；`KaomojiTextAutoSize` 仅在字号测量时提供单元格内可见宽度，实际文字仍可横向滚动，达到字号下限的极长内容不会被截断或改写。

面板根节点和每页网格共用 `NestedScrollConnection`，在 `onPostScroll` 和 `onPostFling` 消费子滚动剩余的横向位移与速度。面板边界隔离分类 Tab 栏和分页器与外层视频 `HorizontalPager`，网格边界隔离长条目阅读与分类 Pager；子组件先处理自身滚动，纵向滚动仍沿用原路径。评论与弹幕输入区的活动状态由视频页可交互状态与视频 Pager 的 `currentPage` 决定；子列表 fling 会经嵌套滚动短暂改变父分页器的 `isScrollInProgress`，因此该属性不作为关闭面板的条件，网格、分类与长条目滚动不会清除面板状态。实际切换视频页签仍由外层 `currentPage` 变化关闭面板。

面板的内容区域最高 300dp，背景另包含底部系统安全区，并按可用高度扣除输入区实测高度、间距与安全区；等待 IME 收起后显示。`VideoPage` 仅在详情宿主外应用顶部安全区，左右与底部安全内边距由 Scaffold 透传至 `VideoScreen`、`FloatingComposerLayout` 和 `ContentComposer`；Tab 与列表单独避让左右安全区，面板宿主保持全宽贴底。`ContentComposer` 仅对编辑行与登录按钮应用左右 16dp 悬浮间距和 640dp 宽度上限；面板收起时底部保留安全区加 12dp 间距，展开时背景铺到底部，安全区放入面板内容，不重复处理导航栏或 IME Insets。

`FloatingComposerLayout` 实测包含底部安全区的完整输入区高度，列表留白仅另加 12dp 间距，确保末项能滚动至输入区上方。`AnimatedVisibility` 管理面板展开和收起，高度变化使用主题 `defaultSpatialSpec`，淡入淡出使用 `fastEffectsSpec`；面板与顶部 8dp 间距一同参与过渡，退出结束后移出组合。底部宿主固定保留安全区加 12dp 的最小高度，面板在宿主内靠底对齐，编辑行随实际动画高度移动，避免切换底部内边距和外部间距造成跳动；内容从自身顶部展开，Tab 随面板上沿移动，动画最大高度限制为完整面板高度，背景持续贴底。

高度过渡使用 `clip = false`，保留面板上沿及圆角外的原生投影；面板靠屏幕底部对齐，超出动画高度的内容向窗口底部延伸，由窗口边界约束，面板本身仍由 `Surface` 按形状裁剪。投影与面板共用淡入淡出过渡，输入框与发送按钮的阴影保留在其外。退出开始时清除面板无障碍语义并禁用分类切换与条目插入；快速反向切换由同一过渡接续，动效遵循系统动画设置。颜色和排版继承 `KIRAKIRATheme`。

`KaomojiViewModel` 暴露应用级 `RecentKaomojiStore` 的 Flow。Store 在 IO 调度器上先读后串行处理插入事件，以独立 `kirakira_kaomoji.xml` 保存最多 24 项去重记录，只接受目录中存在的值；初始或损坏记录回退为空。最近记录不属于账号私有数据，不保存草稿，不加入备份白名单，也不请求网络。来源与许可见 [Cerasus 资源](../../third_party/cerasus-icons/README.md)。
