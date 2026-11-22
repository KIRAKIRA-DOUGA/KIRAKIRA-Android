# 主题与投影

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录配色的生成方式、颜色与语言偏好的保存、弹层背景的选择，以及三类投影（栏面、按钮与单选框、卡片）的绘制方式和取舍。必须遵守的规则见[主题配色约束](../ui-guidelines.md#主题配色约束)和[按钮、单选框与阴影](../ui-guidelines.md#按钮单选框与阴影)。

- [主题实现](#主题实现)
- [配色生成](#配色生成)
- [选色与持久保存](#选色与持久保存)
- [弹层背景](#弹层背景)
- [应用语言](#应用语言)
- [栏面阴影](#栏面阴影)
- [按钮动态阴影](#按钮动态阴影)
- [单选框动态阴影](#单选框动态阴影)
- [彩色卡片阴影](#彩色卡片阴影)

## 主题实现

[KIRAKIRATheme](../../app/src/main/kotlin/moe/kirakira/ui/theme/Theme.kt) 基于 `MaterialExpressiveTheme`：组件、形状档位、排版与动效沿用 Material 3 Expressive，配色由项目自己的生成器提供，并通过 `shapes` 参数覆盖八档圆角。头像与胶囊 Chip 使用 `CircleShape`。

主题偏好由 [ThemeSettingsStore](../../app/src/main/kotlin/moe/kirakira/data/settings/ThemeSettingsStore.kt) 读写，[ThemeViewModel](../../app/src/main/kotlin/moe/kirakira/ui/theme/ThemeViewModel.kt) 把存储结果映射为界面状态。首次读取在后台进行，读取完成前系统启动屏不退出（见[启动动画](navigation.md#启动动画)）；明暗模式缺失或无效时回退为跟随系统。

### 品牌标题字体

[Type.kt](../../app/src/main/kotlin/moe/kirakira/ui/theme/Type.kt) 在 `BrandTitleFontFamily` 中注册两份随 APK 打包的 Montserrat 正体：SemiBold（600）用于静态页面的顶栏标题和关于页的 `for Android`，Bold（700）用于账号流程的大标题。来源与校验值见 [Montserrat 来源记录](../../third_party/montserrat/README.md)，OFL 许可全文可在应用的[开源组件](../../third_party/aboutlibraries/README.md)页离线查看。

- 字体只含拉丁字符。中文等字形由系统字体回退显示，不按界面语言切换字体，也不拆分字符串。
- 全局 `Typography()` 仍是 Material 3 的默认排版。正文、普通控件、用户昵称、视频标题、评论和统计数字不使用品牌字体。

### 成功语义色

[ThemeSemanticColors](../../app/src/main/kotlin/moe/kirakira/ui/theme/ThemeSemanticColors.kt) 提供成功色 `success`／`onSuccess` 和警告色 `warning`／`onWarning`，通过 `MaterialTheme.semanticColors` 读取。它们是 Material 3 颜色角色之外的补充，浅深色各有固定取值，不随强调色或系统强调色变化。目前用于安全页的状态横幅：已开启二步验证为成功色，确认未开启为警告色，待核对为中性色。

## 配色生成

所有颜色来源（预设、系统强调色、自定义颜色）都经过 `rememberSeedColorScheme(seedColor, darkTheme)`，由 [ClassicAccentColorScheme](../../app/src/main/kotlin/moe/kirakira/ui/theme/ClassicAccentColorScheme.kt) 生成。默认原色是 `KIRAKIRAPink`。

- **灰阶加强调色**。[MaterialKolor](https://github.com/jordond/MaterialKolor)（MIT，底层 Material Color Utilities 为 Apache-2.0）的 `dynamicColorScheme` 只用来生成 `Monochrome` 灰阶基础；强调色的容器、反色与固定色角色由 HCT 工具另行生成。原色本身不经色调映射，直接作为 `primary`。
- **次要容器跟随主色**。`secondaryContainer` 与 `onSecondaryContainer` 映射到主色容器及其前景。这样默认 Slider 的未经过轨道、使用次要容器的导航指示器都会自动带上强调色，不需要为每个组件单独配色。
- **关闭色调高度叠加**。主题通过 `LocalTonalElevationEnabled` 关闭它，并让 `surfaceTint` 与 `surface` 同色，栏面才不会在经典配色下被重新染色。
- **页面底色**。浅色方案把 `background` 与 `surface` 设为纯白，`surfaceContainer` 保留浅灰。设置页用 `surfaceContainer` 作页面背景，白色的列表项才与背景有层次；如果页面和列表都用 `surface`，两者会融在一起。
- **缓存**。灰阶基础在进程内各生成一次；完整配色按“不透明 ARGB 加明暗模式”放进容量 32 的 `LruCache`。缓存只含颜色值，不持有 Activity，也不写入存储。

[SystemAccentColor](../../app/src/main/kotlin/moe/kirakira/ui/theme/SystemAccentColor.kt) 通过 `colorResource` 读取 Android 12+ 的公开资源 `android.R.color.system_accent1_500`。读取跟随系统资源配置更新，不缓存快照；低版本返回空值，主题回退到保存的手动原色。这个颜色来自系统的配色设置，不一定是壁纸取色。

控件底色与 Chip 的取色入口是 [ThemeColorDefaults](../../app/src/main/kotlin/moe/kirakira/ui/theme/ThemeColorDefaults.kt) 和 [AppChipDefaults](../../app/src/main/kotlin/moe/kirakira/ui/components/selection/AppChipDefaults.kt)，规则见[主题配色约束](../ui-guidelines.md#主题配色约束)。

配色依赖带来的 APK 体积增量没有测量过，需要在相同构建配置下比较，不能用依赖包的大小代替。

## 选色与持久保存

### 保存的键

主题、播放和弹幕的 Store 共用 `settingsPreferences` 取得应用级的 `kirakira_settings` 文件，写入使用 `apply`。这个文件是唯一参与系统备份的偏好文件。

| 键 | 含义 | 缺省 |
| --- | --- | --- |
| `theme_mode` | 明暗模式 | 跟随系统 |
| `theme_system_colors` | 是否使用系统强调色 | 关闭 |
| `theme_seed_color` | 当前生效的手动原色（ARGB） | 项目粉色 |
| `theme_custom_color` | 自定义色值（ARGB） | 项目粉色 |
| `theme_use_custom_color` | 当前选中的是否为自定义色 | 否 |
| `predictive_back_enabled` | 预测性返回动画 | 关闭 |
| `background_depth_enabled` | 视频缩放时的背景景深 | 开启 |
| `app_language` | Android 12 及以下的手动语言选择 | 无（跟随系统） |

- **自定义色值与生效色值分开保存**，并单独保存“是否选中自定义”。如果只比较色值，自定义颜色恰好等于某个预设时会标错选中项。
- **选择系统强调色只改来源标记**，不覆盖手动色值和自定义选择。
- **旧预设迁移**。读取时把旧的蓝／紫／绿／琥珀／珊瑚预设原色映射到对应的 Cerasus 角色色，并只写回变化后的 `theme_seed_color`。自定义状态为真时不迁移，即使色值等于旧预设。
- 不保存配色算法；搜索历史与最近颜文字使用各自独立的偏好文件。

### 选色器

[CustomColorDialog](../../app/src/main/kotlin/moe/kirakira/feature/settings/appearance/CustomColorDialog.kt) 使用 [colorpicker-compose](https://github.com/skydoves/colorpicker-compose)（Apache-2.0）的 HSV 色盘与亮度滑条，并补充亮度的无障碍调节和 HEX／RGB／OKLCH 输入。

主题色对话框和弹幕颜色面板共用 [ColorEditorControls](../../app/src/main/kotlin/moe/kirakira/ui/components/form/ColorEditorControls.kt)。`ColorEditorState` 保存不透明的 ARGB 草稿、输入格式和原始输入，可随界面重建恢复；容器和确认／取消操作归调用方。

- 草稿只在确认后生效。无效或未完成的输入保留上一次的有效颜色，并禁用“应用”。
- 切换输入格式只转换上一次的有效颜色，不改变实际颜色。格式属于对话框草稿，不持久保存。
- 主题色确认时返回 ARGB；弹幕确认时才去掉 alpha 转成 RGB，因为弹幕协议的颜色不带透明度也不带 `#`。

### 格式与色域

格式化与解析位于 [core/color](../../app/src/main/kotlin/moe/kirakira/core/color)。

| 格式 | 接受的输入 |
| --- | --- |
| HEX | 六位十六进制；界面入口接受可选的 `#` 和首尾空白，协议入口（`formatRgbHex`／`parseRgbHex`）严格不接受 |
| RGB | 0–255 的整数，空格或逗号分隔，可带 `rgb(...)` |
| OKLCH | L 为 0–1 或百分比，C 为 0–0.4，H 为度数并归一化到 0–360，可带 `oklch(...)` |

Oklab 的转换采用[原作者公开的公式](https://bottosson.github.io/posts/oklab/)。底层保存的始终是不透明的 8 位 sRGB。输入超出 sRGB 色域时，固定亮度和色相，用二分法降低彩度直到落入色域；界面保留原始输入并提示调整，色块和最终确认使用映射后的颜色。这是固定色相的彩度压缩，不是 CSS 规定的完整色域映射算法。

### 外观页

[AppearanceScreen](../../app/src/main/kotlin/moe/kirakira/feature/settings/appearance/AppearanceScreen.kt) 的色板固定显示七种 Cerasus 预设，其后是系统强调色（系统取色可用时）和自定义入口；选中项按当前生效的来源互斥标记。页面私有的 `AppearanceColorOption` 用类型区分预设、系统强调色和自定义入口，避免用字符串分派颜色来源。

角色图片随 APK 离线打包，来源与转换方式见 [Cerasus 个性色资源](../../third_party/cerasus-palettes/README.md)。角色名副标题带 `LocaleList("ja")`，让 Android 为这一行选用日文字形。

## 弹层背景

规则见[列表、开关与弹层](../ui-guidelines.md#列表开关与弹层)：弹层里有连接列表条目时用灰底，否则用 `surface`。实现上有三点需要注意。

- **按内容状态判断**，不读取懒列表的可见条目，也不探测组件树。空分组不算条目；刷新时保留着已有条目则继续用灰底。
- **日期选择器的内外容器要用同一份配色**。把同一个 `DatePickerDefaults.colors(containerColor = surface)` 同时传给 `DatePickerDialog` 和内部的 `DatePicker`，否则日历、年份选择和输入模式会与外层颜色不一致。
- **第三方组件用它公开的颜色参数**。开源组件页通过 `LibraryDefaults.m3VariantColors(sheetSurface = surface)` 配置详情 Sheet，通过 `libraryColors(dialogBackgroundColor = surface)` 配置许可证对话框。

弹幕样式面板包含“样式页”和“自定义颜色编辑”两个子页，前者用灰底，后者用 `surface`。子页状态提升到 `ModalBottomSheet` 的调用之前，才能在创建弹层时就选对背景。

[弹出菜单](components.md#弹出菜单)用 `surface` 为毛玻璃染色，Android 8.1–11 使用同色的半透明底。

## 应用语言

[AppLanguage.kt](../../app/src/main/kotlin/moe/kirakira/core/system/AppLanguage.kt) 定义可选语言（跟随系统、`en`、`zh`、`zh-Hant`、`ja`），`locales_config.xml` 同步声明。中文按书写系统区分简繁：标为 `Hant`，或未标书写系统但地区是台湾、香港、澳门的，归为繁体中文，其余归为简体中文。

| 系统版本 | 机制 |
| --- | --- |
| Android 13+ | 通过 `LocaleManager.applicationLocales` 读写，清空列表即交还系统管理。应用自己不保存语言 |
| Android 12 及以下 | 手动选择保存在 `kirakira_settings` 的 `app_language`。启动时和 Activity 收到配置变化后，把它应用到默认 `LocaleList` 以及 Activity 与 Application 的资源 |

- `MainActivity` 自行处理 `locale|layoutDirection` 配置变化：资源与 `LocalConfiguration` 原地更新后 Compose 重组，Activity 不重建。
- 低版本上 `ProvideAppLanguage` 始终包住主题，为组合提供对应的 `LocalContext`、`LocalConfiguration` 和 `LocalLayoutDirection`，切换语言时保留组合状态。切回跟随系统时删除偏好，并恢复设备当时的语言列表。
- 语言设置页在语言配置变化、收到系统语言广播或页面恢复时重新读取当前选项。

## 栏面阴影

[ThemeShadows](../../app/src/main/kotlin/moe/kirakira/ui/theme/ThemeShadows.kt) 的 `barSurfaceLayer(shape, alpha, shadowElevation)` 用官方 `graphicsLayer` 统一承载栏面的形状、透明度与原生投影。阴影的配色和强度交给平台的光照模型，不手动映射模糊、扩张或不透明度。

### 为什么 alpha 必须和 elevation 在同一图层

原生的 [Outline.alpha](https://developer.android.com/reference/android/graphics/Outline#setAlpha(float)) 描述轮廓内内容的不透明程度，[绘制实现](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/libs/hwui/pipeline/skia/ReorderBarrierDrawables.cpp#208)据此选择阴影路径。如果只降低背景颜色的 alpha，而承载阴影的图层 alpha 仍为 1，平台会按不透明遮挡面来优化阴影，半透明的栏面就会透出阴影的内缘。

所以半透明栏面把 alpha 设在承载 elevation 的那个背景图层上，由 Compose 同步到原生轮廓；背景颜色本身保持不透明。文字、图标、选中背景和指示器作为独立的前景绘制，不随背景变淡。

### 各系统版本的背景

| 系统版本 | `frostedBarBackground` 的做法 |
| --- | --- |
| Android 8.1–11 | 不透明的主题 `surface`，把 0.9 的 alpha 与 elevation 放在同一个背景图层 |
| Android 12+ | 背景图层 alpha 为 1；先显式画一层不透明的 `surface`，再叠加 Haze 的模糊 |

Android 12+ 之所以要先显式画底色：Haze 的 `backgroundColor` 只在效果实际绘制时才生效，采样源或坐标未就绪时效果会被跳过。只靠它的话，首帧和转场淡入期间原生投影下面没有实际填充。`FrostedScaffold` 的普通顶栏把带 elevation 的不透明底层与裁剪在栏面内的 Haze 分开绘制，阴影才能投到正文上。

### 谁来承载阴影

| 场景 | 承载方式 |
| --- | --- |
| 普通顶栏、胶囊底栏 | 独立的背景子节点，尺寸跟随实际栏高且不参与测量 |
| 认证与账号安全流程的顶栏 | `AccountFlowScaffold` 持有正文的滚动状态，按是否在顶部向 `FrostedScaffold.topBarShadowElevation` 传入对应高度 |
| 宽窗口侧栏 | 显式传入更高的 elevation，背景不透明 |
| 视频页 | 一个共享背景涵盖播放器占位区域与 Tab，下沿跟随两者高度之和；Tab 上缘不单独投影，播放器本身独立绘制 |
| 资料页 | 原生图层承载封面、资料区与 Tab 共用的不透明顶部背景，下沿跟随 Tab 的实测位置；这样 Tab 上缘的投影不会落到同一表面的资料区上 |
| 连接列表 | 容器的 `shadow`，或懒列表宿主的 `GraphicsLayer.shadowElevation` |
| 评论／弹幕输入框 | `Surface.shadowElevation` |
| 下拉刷新 | 只由官方的 `IndicatorBox` 容器承载，外层不重复投影 |

阴影高度不保存，也不经主题、ViewModel 或导航传递。

## 按钮动态阴影

### 双层投影

双层投影由 [MaterialColorShadow](../../app/src/main/kotlin/moe/kirakira/ui/theme/MaterialColorShadow.kt) 通过官方的 `ShadowContext` 与 `DropShadowPainter` 绘制，在最低支持版本上也能画出彩色投影。它由 Compose 绘制，不使用 Android 的平台光照模型，也不保证与浏览器逐像素一致。

几何参数来自 Google 的 [Material Web 双层 elevation 实现](https://github.com/material-components/material-web/blob/main/elevation/internal/_elevation.scss)，高度映射来自官方 [elevation tokens](https://github.com/material-components/material-web/blob/main/tokens/versions/v0_192/_md-sys-elevation.scss)。下表按“垂直偏移／模糊／扩张”列出各级取值，水平偏移为零：

| Level | 高度 | 环境层 | 主层 |
| --- | --- | --- | --- |
| 0 | 0dp | 0／0／0 | 0／0／0 |
| 1 | 1dp | 1／3／1 | 1／2／0 |
| 2 | 3dp | 2／6／2 | 1／2／0 |
| 3 | 6dp | 4／8／3 | 1／3／0 |
| 4 | 8dp | 6／10／4 | 2／3／0 |
| 5 | 12dp | 8／12／6 | 4／4／0 |

项目使用的交互高度（见[界面规范](../ui-guidelines.md#按钮单选框与阴影)）以及动画的中间值，在相邻的官方高度之间线性插值。

**强度是相对官方的显式例外**：彩色投影的环境层／主层不透明度固定为 25%／50%，交互状态只改变范围，不改变不透明度。RGB 最大与最小通道之差不大于 0.02 的颜色视为中性色，改用黑色投影和更低的 7.5%／15%，以减轻白色、灰色按钮和连接缝隙处的黑灰感。两类都乘以容器的透明度。

### 封装

[ShadowButtons](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ShadowButtons.kt) 为填充、浅色填充、实心图标按钮和 FAB 封装官方组件，把官方的高度投影设为零，避免重复叠加。

- **按可见轮廓绘制**。投影读取官方的 `MinimumInteractiveLeftAlignmentLine` 和 `MinimumInteractiveTopAlignmentLine`，按实际可见轮廓内缩并平移。否则最小触摸目标的留白会被当成按钮表面，投影比按钮大一圈。
- **形状与投影共用一个交互源**。带文字的按钮在静止和按压形状之间用公开的 `Interpolatable` API 插值，同一个动画形状用于按钮轮廓、投影和装饰裁剪，不访问 Material 3 的内部 API。
- **禁用优先**。禁用时立即移除投影，优先于按压、悬停和聚焦。
- **容器要给投影留空间**。投影画在按钮外侧，状态提示、账号流程、头像裁剪、规则管理页和邀请码页的 FAB 等位置在各自的容器里预留了空间。

[ShadowToggleButton](../../app/src/main/kotlin/moe/kirakira/ui/components/button/ShadowToggleButton.kt) 是通用选择按钮的封装，`FollowButton` 基于它实现；`Segmented` 的强调色指示块也共用按钮的高度动画与双层投影。接入见[选择按钮阴影](components.md#选择按钮阴影)和[分段选择](components.md#分段选择)，按压形变见[按钮按压轮廓](components.md#按钮按压轮廓)。

### 两个容易出错的地方

- **轮廓缓存**。投影在 `drawWithCache` 中读取动画形状当前的 `Outline`，封装成固定轮廓后再交给阴影缓存。不能把“内部轮廓会变、但对象身份不变”的动画 `Shape` 直接当缓存键，否则按压或选中形变之后，阴影会继续用旧的圆角。
- **颜色过渡**。切换按钮在两种状态之间渐变阴影时，先把两个端点各自映射为实际的彩色或黑色中性投影，再对颜色与透明度插值。如果先插值颜色再判断是否中性，会在中性色阈值处出现强度跳变。投影颜色通过绘制阶段的 `ColorFilter` 着色，颜色或透明度变化时不重建阴影位图。

关注按钮在“关注”和“已关注”之间保留同一个投影节点，按遮罩的扩散进度淡入淡出，被反向打断时沿用当前进度，不瞬间添加或移除投影。

### 发送按钮

`ContentComposer` 的输入框与发送按钮使用固定高度，每个表面只走一条阴影路径：输入框、禁用的发送按钮和中性色的可用发送按钮走原生阴影，彩色的可用发送按钮走 `materialColorShadow`。禁用时把官方的禁用容器色与主题 `surface` 合成为不透明的灰色，这样滚动内容不会从按钮下面透出来。

## 单选框动态阴影

[ShadowRadioButton](../../app/src/main/kotlin/moe/kirakira/ui/components/selection/ShadowRadioButton.kt) 保留官方 `RadioButton`，参数与官方一致，与按钮共用 `materialColorShadow` 的几何与高度插值；它传入 `opacityScale = 0.5f`，把强度降为按钮的一半。

- **投影层依赖一个未公开的尺寸**。居中的 20dp 圆形轮廓对应官方内部的 `RadioButtonTokens.IconSize`，这个 token 没有公开，所以尺寸记录在封装内。**更新 Material 3 时需要核对它。**
- **差集裁剪只作用于阴影层**。它防止投影染进空心单选框的内部；官方组件的布局、触摸目标、涟漪和绘制不被裁剪。
- **整行点击时共用交互源**。每行创建一个 `MutableInteractionSource`，同时传给 `SegmentedListItem` 和 `ShadowRadioButton`，单选框的 `onClick` 为 `null`；按压、悬停或聚焦整行，就会驱动单选框的阴影。`SettingsRadioItem` 已经这样接好。

## 彩色卡片阴影

设置页的顶部特色卡片使用 [MaterialColorShadow](../../app/src/main/kotlin/moe/kirakira/ui/theme/MaterialColorShadow.kt) 中的 `cardColorShadow`，几何与强度和按钮相同，高度固定。卡片自身没有交互状态，所以不接入交互源；容器色随安全状态或主题变化时，阴影读取当前的实际颜色。

- 把同一个 `MaterialTheme.shapes.large` 传给 `Surface` 和 `cardColorShadow`。
- 不设置 `Surface.shadowElevation`、`tonalElevation` 或 `CardDefaults.cardElevation`，每个表面只保留一条投影路径。
- 带 `animateContentSize()` 的卡片把阴影修饰符放在尺寸动画的**外侧**，阴影才会按动画后的实际尺寸绘制。
- 容器的留白要容纳外侧的阴影。
