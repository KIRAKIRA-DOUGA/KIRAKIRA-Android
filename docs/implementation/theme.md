# 主题与投影

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护语义配色、颜色偏好、弹层背景与投影参数。开发约束以贡献指南为准。

- [主题实现](#主题实现)
  - [成功语义色](#成功语义色)
  - [配色生成](#配色生成)
  - [弹层背景](#弹层背景)
  - [选色与持久保存](#选色与持久保存)
  - [栏面阴影](#栏面阴影)
  - [按钮动态阴影](#按钮动态阴影)
  - [单选框动态阴影](#单选框动态阴影)

## 主题实现

界面以 Material 3 Expressive 为基础，配色与高度阴影采用 Material Design 2 的视觉风格，并结合 KIRAKIRA 特色的彩色阴影。`KIRAKIRATheme` 保留 Material 3 语义颜色角色及 `MaterialExpressiveTheme` 的组件、形状、排版与动效；经典强调色和中性表面由项目主题生成器提供，关闭色调高度叠加。彩色按钮与 FAB 的投影取实际容器色，选中单选框的投影取实际选中色，中性色使用黑色；共享双层阴影几何参数来自 Material Web，原生阴影由平台绘制。设计约束、强度与场景例外见[主题配色约束](../../CONTRIBUTING.md#主题配色约束)及[按钮、单选框与阴影](../../CONTRIBUTING.md#按钮单选框与阴影)。

[ThemeSettingsStore](../../app/src/main/java/moe/kirakira/data/settings/ThemeSettingsStore.kt) 负责 `kirakira_settings` 的读取、写入与旧预设颜色迁移，仅处理字符串、布尔值及 ARGB 数值。[ThemeViewModel](../../app/src/main/java/moe/kirakira/ui/theme/ThemeViewModel.kt) 提供当前主题的默认色和迁移映射，将存储结果映射为 UI 状态；保留后台首次读取、就绪门控、无效明暗模式回退和不透明颜色处理。

### 成功语义色

主题层的 [ThemeSemanticColors](../../app/src/main/java/moe/kirakira/ui/theme/ThemeSemanticColors.kt) 提供不可变的 `success` 与 `onSuccess`，通过 `MaterialTheme.semanticColors` 读取，供成功／完成状态复用，不占用 Material 3 的既有颜色角色。`KIRAKIRATheme` 根据自身 `darkTheme` 参数通过 `CompositionLocal` 提供配色：浅色固定使用 `#008577`，深色使用 `#00594F`，配套前景均为白色，不引用个性色预设，不受强调色或壁纸取色影响。

安全页顶部卡片在已开启二步验证时使用 `success/onSuccess`，未开启时使用 `surfaceContainerHigh/onSurfaceVariant`；保护状态同时由文案及盾牌／警示图标表达。邀请码顶部卡片使用 `primary` 与 `onPrimary` 实色配对，默认呈品牌粉色并随主题强调色变化。两者沿用关于页的大圆角与实色视觉语言，图标底座使用低透明度前景色；邀请码生成按钮在浅深主题的可用状态均采用白色容器与 `primary` 文字、图标，直接使用官方 `Button` 并设置 `elevation = null`，所有交互状态均无阴影，保留官方 Expressive 尺寸和按压形变，禁用配色沿用统计块的低透明度前景色。加载指示器使用卡片前景色。

### 配色生成

所有主题颜色固定使用经典强调色，默认原色为 `KIRAKIRAPink`（`#F06E8E`）。[MaterialKolor](https://github.com/jordond/MaterialKolor) 的纯函数 `dynamicColorScheme` 仅用于生成 `Monochrome / SPEC_2021` 灰阶基础，HCT 工具用于生成强调色的容器、反色与固定色角色。该库维护 Google Material Color Utilities 的 Kotlin 移植与 Compose 适配，许可证为 MIT，底层 Material Color Utilities 为 Apache-2.0。

`KIRAKIRATheme`、预设／壁纸色板与自定义选色器统一使用 `rememberSeedColorScheme(seedColor, darkTheme)`。主题与设置不再接收、保存配色算法，经典方案沿用 `MaterialExpressiveTheme` 的形状、排版和动效。

浅色与深色灰阶基础各在进程内惰性生成一次，完整配色按不透明 ARGB 与明暗模式存入容量为 32 的 `LruCache`。多个预览和重复进入页面复用结果，算法与颜色角色保持一致；缓存只持有颜色值和 `ColorScheme`，不持有 Activity、不写入存储，进程退出后释放。

[WallpaperAccentColor](../../app/src/main/java/moe/kirakira/ui/theme/WallpaperAccentColor.kt) 通过 Compose `colorResource` 读取 Android 12+ 的公开资源 `android.R.color.system_accent1_500`，应用主题与壁纸色板共用此入口。资源读取跟随 Compose 的系统资源配置更新，不缓存壁纸色快照；应用明暗模式不改变取色阶。`KIRAKIRATheme` 保留的 `dynamicColor` 参数仅选择壁纸强调色来源，默认关闭；低版本返回空值，主题回退到保存的手动原色。

[ClassicAccentColorScheme](../../app/src/main/java/moe/kirakira/ui/theme/ClassicAccentColorScheme.kt) 从灰阶角色构建经典强调色，并将 `secondaryContainer` 和 `onSecondaryContainer` 映射到同色主色容器与配套前景。默认 Slider、RangeSlider 的未经过轨道自动采用强调色调，活动圆点采用同一容器色；使用次要容器的导航指示器同样着色，不添加组件专用配色接口。

可选择的 `FilterChip` 与搜索已选标签的 `InputChip` 在调用处直接使用官方 `filterChipColors`／`inputChipColors`，将 `selectedContainerColor` 映射到当前主题 `primary`，`selectedLabelColor`、`selectedLeadingIconColor` 与 `selectedTrailingIconColor` 映射到 `onPrimary`；其他颜色、禁用状态、形状与边框保留官方默认。搜索、播放速度、视频分 P、弹幕样式与邀请码筛选共用此规则，无需更改次要容器角色或新增组件配色封装。

`MainBottomBar` 将选中图标与文字映射到 `primary`，覆盖整个按钮的选中胶囊映射到 `primaryContainer`。主题统一通过 `LocalTonalElevationEnabled` 关闭色调高度叠加，`surfaceTint` 与 `surface` 同色，避免直接计算高度色时重新染色。原色保留、`onPrimary` 对比度阈值、组件配色接口与视觉取舍统一见[主题配色约束](../../CONTRIBUTING.md#主题配色约束)。

设置主页、各设置子页与开源组件页通过 `ThemeColorDefaults.settingsBackgroundColor()` 使用 `MaterialTheme.colorScheme.surfaceContainer` 页面背景，普通 `SegmentedListItem` 保留官方默认的 `surface` 容器颜色和内容内边距，分组统一为无间隙、无分隔线的连接式样式，选中和功能总开关状态沿用官方配色。页面背景不再跟随顶栏颜色，避免经典强调色下页面与列表同为 `surface` 而融为一体；经典方案顶栏继续使用浅色纯白／深色深灰的 `surface`。头像裁剪工具保留原有 `surface` 背景。

浅色方案在主题生成层将普通页面的 `background` 与 `surface` 设为纯白，分组列表页面使用的 `surfaceContainer` 保留 `#F5F5F5`，使白色 `SegmentedListItem` 与页面背景保持层次。深色方案保留原有灰阶角色；所有颜色来源共用这些规则。页面直接引用语义颜色，不再通过配色模式判断切换样式。

### 弹层背景

普通弹层在官方组件调用处直接引用 `MaterialTheme.colorScheme`，规则见[贡献指南](../../CONTRIBUTING.md#compose-与界面规范)。业务 Sheet 有实际连接列表条目时使用原有的 `surfaceContainerLow`，否则使用 `surface`；深色模式同样使用对应语义颜色，不硬编码白色。判断基于内容状态，不读取懒列表可见条目或探测组件树；空分组不算连接列表条目，保留已有条目的刷新继续使用灰底，不额外添加颜色动画或保存背景偏好。

播放器画质与倍速面板、隐私可见性面板始终包含连接列表，固定使用灰底。标签搜索根据候选是否非空、标签名称根据是否存在非空名称组、屏蔽规则编辑根据是否有用户预览或非空标签结果选择背景。弹幕样式的 `custom` 状态保留在弹层打开分支、提升到 `ModalBottomSheet` 调用之前：样式页用灰底，自定义颜色编辑用 `surface`；关闭后重新打开仍进入样式页，编辑器草稿和确认／取消逻辑保持原有归属。

普通 `AlertDialog` 与 `DropdownMenu` 显式使用 `surface`。生日选择将同一份 `DatePickerDefaults.colors(containerColor = surface)` 传给 `DatePickerDialog` 和内部 `DatePicker`，确保日历、年份选择和输入模式与外层同色。开源组件通过 `LibraryDefaults.m3VariantColors(sheetSurface = surface)` 配置详情 Sheet，通过 `libraryColors(dialogBackgroundColor = surface)` 配置许可证对话框，保留上游组件与列表页面背景。弹层内列表项、输入框和选中态继续使用原有配色；媒体查看器不适用该规则。

### 选色与持久保存

[自定义选色对话框](../../app/src/main/java/moe/kirakira/feature/settings/CustomColorDialog.kt)使用 [colorpicker-compose](https://github.com/skydoves/colorpicker-compose)（Apache-2.0）的 HSV 色盘与亮度滑条，封装在 Material 3 `AlertDialog` 中，补充亮度无障碍调节与 HEX 输入。它只增加 Compose 选色绘制与手势代码，不引入 View 互操作，许可证由 AboutLibraries 收集。

`ThemeColorSettings` 保存 `useSystemColors`、`seedColorArgb`、`customColorArgb` 与 `useCustomColor`，分别持久化为 `theme_system_colors`、`theme_seed_color`、`theme_custom_color` 与 `theme_use_custom_color`。默认关闭壁纸取色与自定义选择，两份原色均为项目粉色，不保存配色算法。读取时仅将非自定义的旧蓝／紫／绿／琥珀／珊瑚预设原色映射至智乃蓝／理世紫／千夜绿／纱路黄／小惠红，并只写回变更后的 `theme_seed_color`；品牌粉色不变。壁纸来源和独立自定义色值保持原样，自定义状态为真时不迁移，即使色值等于旧预设。

色板始终显示，七种 Cerasus 预设之后依次为受支持的壁纸颜色与自定义入口。颜色依次为 `#F06E8E`、`#4581E1`、`#B044B0`、`#46A12F`、`#F98D00`、`#199BB6`、`#DD1818`。选择壁纸仅更新来源标记，保留手动色值和自定义选择状态；色板以当前生效来源互斥标记选中项。自定义色值与当前手动色值独立存储，避免自定义颜色恰好等于预设时错误标记选中项。草稿确认与取消的约束见[贡献指南](../../CONTRIBUTING.md#主题配色约束)。

设置主页用单一 `LazyColumn` 按“我”“常规”与账户操作分组懒加载，每组继续复用 `ConnectedListGroup` 的整体阴影与连接形状，保留分组间距和滚动状态。外观页复用 `SettingsScaffold` 的毛玻璃顶栏、640dp 内容上限与滚动内 Insets，同样使用单一 `LazyColumn`，按标题、明暗卡片行、个性色卡片行及动画分组组织带稳定 key 和 contentType 的条目。页面私有的 `AppearanceColorOption` 区分 `ThemePresetColor` 预设、带原色的壁纸选项与自定义入口，避免通过字符串分派不同颜色来源；壁纸原色更新时重新生成选项，保留稳定 key。

页面容器仅通过一次 `BoxWithConstraints` 取得可用宽度；明暗／个性色卡片最小宽度为 100dp／156dp，间距 12dp，最小宽度乘以至少为 1 的字体缩放因子后计算列数。`ThemeCardRow` 保留同一行按最长文字内容等高、末行空格占位的排列。分组直接排列卡片，不额外包裹连接列表或背景卡片；自定义颜色对话框由列表外的页面状态管理。

`ThemeSelectionCard` 使用官方可点击 `Card` 管理涟漪、焦点与交互，使用主题 large 形状与 1dp 静止阴影。明暗模式使用 `compact` 布局，选中时整张卡片使用 `primary/onPrimary`，未选中使用 `surface/onSurface`；预览图标通过 `LocalContentColor` 同步继承动画颜色，未选中为 `primary`，选中为 `onPrimary`。角色、壁纸与自定义卡片的预览区保持 `surface` 底色及原始图片／原色图标，仅下方文字区在选中时使用 `primary` 背景，标题和副标题同步使用 `onPrimary`；未选中文字区使用 `surface/onSurface`，副标题使用 `onSurfaceVariant`。

1.5dp 描边在透明与完整 `primary` 之间渐变，背景、标题、图标与色标中的勾选沿用主题 Expressive effects 动效。根节点覆盖为单选角色与选中状态，合并主题／角色名称或 HEX 色值，子预览与标签清除独立语义。明暗卡片的图标区域固定高 64dp，标题区顶部留白为 4dp，使图标与文字间距不随卡片宽度增大；跟随系统／浅色／深色分别居中显示 40dp 的 `brightness_auto`／`light_mode`／`dark_mode`；标题使用 `labelLarge` 居中排列并允许换行。`ThemeViewModel` 初始值、缺失或无效偏好的回退均为 `ThemeMode.SYSTEM`，有效的 `theme_mode` 偏好按已保存值恢复。

七张角色卡片的图片区域固定为 1:1，继续按 `BiasAlignment(0f, -0.84f)` 靠上裁切。文字区保留 24dp 原色色标、`titleSmall` 主题名与 `bodySmall` 日文角色名；选中时在色标内显示 24dp 的 Rounded `check`，前景使用 `onPrimary`，不在图片上叠加标记或为尾部勾选预留宽度。色标与图标共享固定的 24dp 槽位，垂直居中且不随选中状态改变尺寸。文字区通过 Column weight 填满行等高后的剩余空间，保持底边对齐；标题允许换行和增高，选中变化仅影响绘制。角色图片通过 Coil `AsyncImage` 按卡片约束在后台解码并复用内存缓存；首次加载透出卡片原有底色，禁用额外 crossfade，图片出现不改变布局。角色图片离线打包，来源与转换方式见 [Cerasus 个性色资源](../../third_party/cerasus-palettes/README.md)。

壁纸与自定义颜色在七张角色卡片之后共用个性色网格，`ThemeColorSourceCard` 复用 `ThemeSelectionCard` 的方形预览区、文字区与选中样式；预览区居中显示 48dp 的 `wallpaper`／`edit`，按系统强调色／已保存的自定义色着色，文字区保留名称、HEX 色值与原色色标。系统取色不可用时不生成壁纸选项。点击自定义只打开现有对话框，确认才调用 `selectCustomColor`，取消不改变选择。自定义对话框仍复用 `ThemePaletteSwatch` 三色预览。主题状态继续使用 `lifecycle-runtime-compose` 进行生命周期感知收集；页面 Preview 定义覆盖中英文、浅深色、320dp／640dp 宽度及 2 倍字体。

未选中的主题与个性色卡片不绘制描边，仅保留卡片阴影；描边只用于选中项的强调色反馈，避免灰色轮廓干扰阴影。

MaterialKolor 提供灰阶生成、HCT 工具与 Compose 适配代码，不引入 View 组件库；两项配色依赖的实际 APK 增量需通过同构建配置比较，不以依赖包大小代替。

页面顶栏的标题槽通过 `TopAppBarDefaults.topAppBarColors(titleContentColor = MaterialTheme.colorScheme.primary)` 使用当前主题主色；共享 `CollapsibleTopAppBar` 与主界面的公共颜色配置集中设置，独立顶栏在调用处设置。顶栏标题文字显式使用 `FontWeight.Bold`（700），保持组件默认字号与行高；使用系统默认字体，由平台负责字重匹配与必要的加粗合成。可折叠标题在展开与折叠时保持相同颜色与字重，背景与导航图标沿用各页面配置。

### 栏面阴影

[ThemeShadows](../../app/src/main/java/moe/kirakira/ui/theme/ThemeShadows.kt) 的 `barSurfaceLayer(shape, alpha, shadowElevation)` 统一承载栏面形状、透明度与原生投影，使用官方 `graphicsLayer`，阴影高度通过 `BarShadowElevation` 固定为 4dp。默认矩形、alpha 为 1，胶囊底栏传入与背景一致的圆角形状；阴影配色和强度由平台光照模型决定，不手动映射模糊、扩张或双层不透明度。图层仅按形状裁剪内容，保持默认 `CompositingStrategy.Auto`，不使用阴影蒙版、差集裁剪或阴影版本特判。

原生 [Outline.alpha](https://developer.android.com/reference/android/graphics/Outline#setAlpha(float)) 描述轮廓内内容的不透明程度，[Android 11 绘制实现](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/libs/hwui/pipeline/skia/ReorderBarrierDrawables.cpp#208) 据此选择透明遮挡面的阴影路径。仅降低背景颜色的 alpha、却让承载阴影的图层维持 alpha 为 1，会使半透明栏面透出按不透明遮挡面优化后的阴影内缘。

`frostedBarBackground` 在 Android 8.1–11 绘制不透明主题 `surface`，将 0.9 的 alpha 与 elevation 放在同一个背景图层中；Compose 同步图层 alpha 到原生轮廓，保持 90% 不透明度且不重复相乘。Android 12+ 的 Haze 背景图层保持 alpha 为 1，继续使用既有采样配置和同一原生 elevation 机制。

普通顶栏和胶囊底栏通过独立的背景子节点承载透明度、形状与阴影，文字、图标、选中背景和指示器作为独立前景绘制，不随背景变淡。背景使用 `matchParentSize()` 跟随实际栏高且不参与测量，胶囊前景继续由透明 `Surface` 保留内容裁剪与触摸语义。视频页的共享背景涵盖播放器占位区域与 Tab，下沿跟随播放器高度与实测 Tab 高度之和，Tab 上缘不单独投影；实际播放器保持独立绘制。资料页的原生图层承载封面、资料区与 Tab 共用的不透明顶部背景，下沿跟随 Tab 实测位置，避免独立 Tab 的上缘投影落到同一表面的资料区；资料顶栏毛玻璃背景的 `shadowElevation` 保持默认 0dp。尺寸、折叠程度与字体缩放由原有布局决定，不增加额外留白、空投影图层或持久化状态。

浅色与深色模式共用固定的栏面阴影高度，实际投影由平台光照与图层透明度决定。连接列表继续使用固定 1dp 平台 elevation 投影，由普通容器 `shadow` 或懒列表宿主 `GraphicsLayer.shadowElevation` 承载；评论／弹幕输入框使用 `Surface.shadowElevation` 的固定 4dp 原生高度，发送按钮使用同一 4dp 高度，其彩色与中性色路径见[按钮动态阴影](#按钮动态阴影)。刷新阴影仅由官方 `IndicatorBox` 容器承载，跟随容器位移与显示状态，不在外层重复投影。连接列表继续延伸并裁剪分段投影轮廓，账号侧滑组继续通过 `clipContent` 控制内容裁剪。

栏面图层 Modifier 为普通函数，调用处仅使用固定高度；主题、ViewModel 和导航不保存或传递阴影状态，外观页没有关闭入口，也不读取旧阴影偏好。大标题展开或折叠、Tab 切换及吸顶不改变阴影高度；经典强调色仍关闭色调高度叠加。

### 按钮动态阴影

个人主页与视频页复用 [FollowButton](../../app/src/main/java/moe/kirakira/ui/components/FollowButton.kt)，内部使用官方 `ToggleButton` 与官方尺寸配套 API。未关注状态显式使用 `primary/onPrimary`，接入共享 `buttonShadow` 的双层投影；已关注状态使用灰色 `surfaceContainerHigh/onSurfaceVariant`，不接入投影，官方 elevation 同时设为 `null`。禁用时保留官方禁用配色与语义，不绘制阴影。选中状态与按压状态通过公开 `Interpolatable` API 及主题 `fastSpatialSpec` 插值，所得轮廓同时传给表面和投影，官方组件的三种形状设为同一当前轮廓以避免二次动画；关注布尔值、提交条件与回调继续由原页面拥有。

[ShadowButtons](../../app/src/main/java/moe/kirakira/ui/components/ShadowButtons.kt) 为填充、浅色填充、实心图标按钮及 FAB 封装官方 Material 3 组件；文字、描边及裸图标按钮不使用投影。调用方保留现有颜色、尺寸、形状、内容内边距、语义及点击逻辑，官方高度投影设为零，避免重复叠加。按钮不增加缩放、位移或布局空间，也不保存阴影偏好。投影读取官方 `MinimumInteractiveLeftAlignmentLine` 和 `MinimumInteractiveTopAlignmentLine`，按实际可见轮廓内缩并平移，不把最小触摸目标的留白当作按钮表面；测量及触摸区域保持原样。

播放器黑底控制按钮不接入共享阴影或背景模糊，静止、按压、悬停、聚焦及禁用时均无投影。返回、全屏和倍速图标直接使用官方 `FilledIconButton`，清晰度与带数值的倍速入口使用 `TextButton`，中央播放／缓冲容器使用半透明黑底；小窗继续使用无投影的官方 `IconButton`。资料页 Tab 未吸顶时的顶部按钮保留[黑底毛玻璃按钮](components.md#黑底毛玻璃按钮)。

[VideoScreen](../../app/src/main/java/moe/kirakira/feature/video/VideoScreen.kt) 的下载、分享、更多按钮直接使用官方 `FilledTonalIconButton`，在调用处通过 `IconButtonDefaults.filledTonalIconButtonColors` 将容器与前景设为 `surfaceContainer/onSurfaceVariant`，与未选中的赞踩 `ToggleButton` 一致；不接入共享投影，所有交互及禁用状态均无阴影。

`ShadowFloatingActionButton` 默认使用 `CircleShape` 与主题 `primary` 背景，`contentColorFor(containerColor)` 为默认背景匹配 `onPrimary` 前景，与实心按钮配色一致。保留官方 FAB 尺寸及显式覆盖形状、背景和前景的参数；当前规则管理页直接继承默认值，仅显示本地化无障碍描述的 Material Symbols Rounded `add` 加号。

共享交互源驱动投影和形状。普通按钮静止／按压／悬停或聚焦使用 2dp／8dp／4dp 等效高度，FAB 使用 6dp／12dp／8dp；按下 120ms、松开或取消 180ms，无弹跳，系统动画设置由 Compose 处理。普通按钮和 FAB 禁用时立即移除投影，优先于按压、悬停与聚焦；FAB 通过封装的 `enabled` 参数同时阻止操作并声明禁用语义。传入 Expressive `shapes` 时，默认形状和按压形状通过公开 `Interpolatable` API 与主题 `defaultEffectsSpec` 插值，同一动画形状用于按钮轮廓和投影；不访问 Material 3 内部 API。

双层 `dropShadow` 由 [MaterialColorShadow](../../app/src/main/java/moe/kirakira/ui/components/MaterialColorShadow.kt) 统一绘制，在最低支持版本同样绘制彩色投影，几何参数采用 Google 官方 [Material Web 双层 elevation 实现](https://github.com/material-components/material-web/blob/main/elevation/internal/_elevation.scss)，高度映射采用官方 [elevation tokens](https://github.com/material-components/material-web/blob/main/tokens/versions/v0_192/_md-sys-elevation.scss)。

下表按「垂直偏移／模糊／扩张」列出官方逻辑像素值，接入时映射为 dp；水平偏移均为零。这里复用官方设计参数，仍由 Compose 绘制，不使用 Android 平台光照模型，也不保证与浏览器逐像素一致。

| Level | 高度 | 环境层   | 主层    |
|-------|------|----------|---------|
| 0     | 0dp  | 0／0／0  | 0／0／0 |
| 1     | 1dp  | 1／3／1  | 1／2／0 |
| 2     | 3dp  | 2／6／2  | 1／2／0 |
| 3     | 6dp  | 4／8／3  | 1／3／0 |
| 4     | 8dp  | 6／10／4 | 2／3／0 |
| 5     | 12dp | 8／12／6 | 4／4／0 |

保留项目现有交互高度：2dp、4dp 及动画中间值在相邻官方高度间线性插值，环境层包含官方扩张值，主层不扩张。彩色投影取实际容器色，按钮与 FAB 的环境层／主层不透明度统一固定为 25%／50%，这是相对官方强度的显式例外；静止、悬停、聚焦和按压仅改变范围，不再改变不透明度。RGB 最大与最小通道差不大于 0.02 的中性色使用黑色投影及官方 15%／30%。两类均乘以容器透明度；主题切换与自定义颜色变化实时更新，不统一套用主色，也不绘制额外发光效果。

`ContentComposer` 的输入框 `Surface` 与发送按钮 `FilledIconButton` 共用固定 4dp 阴影高度。输入框通过 `Surface.shadowElevation` 绘制原生阴影；禁用发送按钮及中性色容器的可用发送按钮通过 `Modifier.shadow` 绘制原生阴影，使用平台默认配色与光照强度，并设置 `clip = false` 保留组件自身的内容裁剪。彩色容器的可用发送按钮通过 `materialColorShadow` 绘制双层投影，阴影取实际容器色。静止、按压、悬停、聚焦及禁用不改变高度。官方禁用容器色与主题 `surface` 合成，禁用图标色再与该容器色合成，得到不透明灰色配色，滚动内容不会透出。发送条件、官方禁用语义、尺寸与点击反馈保持原有逻辑。每个表面只使用一种阴影路径，保留形状轮廓与内容裁剪，不叠加原生和双层投影。

### 单选框动态阴影

[ShadowRadioButton](../../app/src/main/java/moe/kirakira/ui/components/ShadowRadioButton.kt) 保留官方 `RadioButton`，参数与官方组件一致，默认配色来自 `RadioButtonDefaults.colors()`；仅选中且可用时显示投影。静止／悬停或聚焦／按压采用 1dp／2dp／4dp，按压优先于悬停与聚焦；按下 120ms、恢复或取消 180ms，系统动画设置由 Compose 处理，禁用时立即移除投影。取消选中时同样移除投影，不改变官方圆点和颜色动画。

单选框与按钮共用 `materialColorShadow` 的 Material 官方双层几何参数及高度插值。1dp 的环境层「垂直偏移／模糊／扩张」为 1／3／1dp，主层为 1／2／0dp；2dp 与 4dp 在相邻官方高度之间插值。阴影取 `colors.selectedColor`，单选框传入固定 `opacityScale = 0.5f`，将双层强度降为按钮的一半：彩色环境层／主层为 12.5%／25%；RGB 通道差不大于 0.02 的中性色使用黑色及 7.5%／15%，均乘以选中颜色透明度，主题与自定义颜色变化实时更新。按钮沿用默认 `opacityScale = 1f`，单选框减淡不改变按钮阴影或控件自身颜色。

投影背景层使用 `matchParentSize`，不参与控件尺寸测量；居中 20dp 的圆形轮廓对应当前官方内部 `RadioButtonTokens.IconSize`，该 token 未公开，因此在封装内记录尺寸来源。仅对阴影层使用圆形差集裁剪，防止投影染色空心单选框内部；官方组件的布局、最小触摸目标、涟漪与绘制不被裁剪，也不添加背景填充或平台 elevation。更新 Material 3 时需核对该内部图标尺寸。

设置、账号切换、画质和隐私选项统一使用此封装。整行处理点击时，每行通过 `remember` 创建一个 `MutableInteractionSource`，同时传给 `SegmentedListItem` 和 `ShadowRadioButton`，单选框保持 `onClick = null`；按压、悬停与聚焦整行即驱动选中单选框的阴影。`SettingsItem` 的可选 `interactionSource` 参数由 `SettingsRadioItem` 透传，其余行保留默认交互源。账号切换行使用 `onClick` 重载，整行配色不随选中状态变化；通过显式 `Role.RadioButton`、`selected` 与状态描述保留无障碍单选语义。账号行的加载、编辑和禁用条件沿用原业务状态，不保存阴影偏好。
