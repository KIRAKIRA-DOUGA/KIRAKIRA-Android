# 导航与启动

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录导航代码的分层、返回栈与返回事件的归属、宽窗口侧栏、主界面的标签切换，以及启动图标和启动动画。路由与宿主的使用要求见[架构规范](../../CONTRIBUTING.md#技术选型与架构)。

- [导航与状态管理](#导航与状态管理)
- [窗口布局与侧栏](#窗口布局与侧栏)
- [主界面切换与底栏动效](#主界面切换与底栏动效)
- [启动器图标](#启动器图标)
- [启动动画](#启动动画)

## 导航与状态管理

### 两个导航包

| 包 | 内容 | 谁可以依赖它 |
| --- | --- | --- |
| [`moe.kirakira.navigation`](../../app/src/main/kotlin/moe/kirakira/navigation) | 应用级导航图：`AppRoutes`、`AppNavHost`、`AppNavigationState`、`AppDesktopRail`、各 `*Entries` 和返回保护的注册 | 只有 `KIRAKIRAApp` |
| [`moe.kirakira.ui.navigation`](../../app/src/main/kotlin/moe/kirakira/ui/navigation) | 导航宿主：`ActivityNavDisplay`、`ActivityBackHandler`、转场与预测性返回、`NavigationPage`、图片转场和播放视口状态 | 导航图，以及需要嵌套返回栈的功能（认证、账号安全） |

依赖方向是 `navigation` → `feature` → `ui`／`data` → `core`。功能页面不引用路由类：导航图在注册页面时把状态和回调传进去。

### 宿主依赖

[KIRAKIRAApp](../../app/src/main/kotlin/moe/kirakira/KIRAKIRAApp.kt) 连接应用级状态和导航，并创建宿主作用域的 [AppDependenciesViewModel](../../app/src/main/kotlin/moe/kirakira/AppDependenciesViewModel.kt)。它持有唯一的 API 客户端，以及认证、内容、账号设置和历史 Repository，旋转时沿用原实例；历史任务使用它的 `viewModelScope`，宿主清除时取消。

资料编辑和账号安全的 Repository 带有流程检查点，所以不放在宿主上：由依赖容器的工厂方法在条目 ViewModel 创建时提供，随条目释放，但仍共用同一个 API 客户端。

### 导航图

[AppNavHost](../../app/src/main/kotlin/moe/kirakira/navigation/AppNavHost.kt) 负责统一转场、返回保护和播放协调。页面注册拆在 `MainEntries`、`ContentEntries`、`ImageViewerEntries`、`SettingsEntries` 和 `AccountEntries` 中，注册函数显式接收依赖与事件。

`AppNavHost` 还在宿主作用域创建 `ImageExportViewModel`，这样图片导出任务能在图片页出栈后继续完成并报告结果。

[AppNavigationState](../../app/src/main/kotlin/moe/kirakira/navigation/AppNavigationState.kt) 在组合内协调返回栈操作、主导航占位、返回保护和图片查看器的退场计数。只有返回栈交给 Navigation 3 保存；返回保护的回调和组合计数不进入保存状态。

- 打开和返回通过 `openFrom` 与 `popFrom`，只接受当前栈顶发出的操作，因此连续点击不会重复入栈或出栈。打开已在栈中的目标时回到那个条目；返回时始终保留根页面。
- 主标签由宿主的 `MainTabState` 保存。从子页选择主标签时，先更新目标再回到根页面，不触发回顶；已经处在目标主标签时才执行回顶。普通返回不改变主标签。

### 返回栈的保存与路由类名

返回栈通过 `rememberNavBackStack` 保存。Navigation 3 在 Android 上的 `NavKeySerializer` 把每个路由的 **Java 类名**写进保存状态，还原时用 `Class.forName` 找回序列化器；`@SerialName` 不参与这一步。

保存的返回栈只在同一个安装包内还原（进程被回收后重建）。覆盖安装时系统会强制停止应用并销毁它的 Activity 记录，从最近任务返回是重新启动、不带保存状态，所以旧版本的返回栈不会交给新版本解码。Release 构建经过 R8 混淆，类名本来也随构建变化。因此路由类可以自由移动和重命名，不保留旧类、序列化名或迁移代码。

### 条目、Scene 与装饰器

- 条目的 `contentKey` 标识返回保护、ViewModel 和手势的两端。Scene 的键标识 `NavDisplay` 中的布局：普通页面使用栈顶条目的键，[双栏设置](account-settings.md#自适应设置布局)使用独立的目录 Scene 键，详情切换只发生在 Scene 内部。单栏与双栏切换时，Navigation 3 把同一个条目移交给目标布局，保存状态和 ViewModel 不重建。
- `rememberDecoratedNavEntries` 按返回栈缓存入口，`entryProvider` 更新时不会重新创建入口。宿主因此用 `rememberUpdatedState` 持有最新的 provider，让缓存的入口在组合内容时调用它；否则外观、播放、账号状态和回调的更新传不到已在栈中的页面。
- 导航在切页开始时组合并测量新页面。可保存状态与 ViewModel 装饰器保存的是状态，不保留离屏页面的组合树。

### 返回事件

[ActivityBackHandler](../../app/src/main/kotlin/moe/kirakira/ui/navigation/ActivityBackHandler.kt) 单独拥有普通页面的返回事件、生命周期监听和过期回调的拦截；[ActivityNavDisplay](../../app/src/main/kotlin/moe/kirakira/ui/navigation/ActivityNavDisplay.kt) 负责入口装饰、Scene、共享转场和页面展示。

处理器以一次手势的起始条目和预览许可记录返回尝试，在手势完成和异步收尾出栈之前都重新核对当前条目。返回处理器不随“普通页面”与“视频页”两种模式的切换而重建，而是按最新状态选择普通转场或视频 Zoom，否则仍待退场的帧会被清空。图片页面使用独立的 Navigation 3 返回处理器。

### 返回保护

资料、隐私和安全条目通过 [RegisterNavigationBackGuard](../../app/src/main/kotlin/moe/kirakira/navigation/NavigationBackGuard.kt) 注册两个回调：只读的“能否预览返回”，和可以触发确认弹窗的“请求返回”。

- 注册随页面组合释放，并按实例身份比对，旧组合不能撤销新注册。
- 回调通过 `rememberUpdatedState` 读取最新的页面状态。
- 宿主只查询栈顶的保护回调，不持有业务 ViewModel，也不枚举受保护的页面。

### 转场与预测性返回

普通进入与返回复用 AOSP Activity 的横移和透明度参数。源码版本、双页面 Scene、几何变换以及与公开 API 的适配差异维护在 [Android 转场说明](../../third_party/android-motion/README.md)。

预测性返回的开关保存在 `kirakira_settings` 的 `predictive_back_enabled`，缺省为 false，参与非敏感设置备份。它由 `ThemeViewModel` 读取，经 `MainActivity`、`KIRAKIRAApp` 和 `AppNavHost` 传给外观页，也同时传给顶层和认证流程里嵌套的 `ActivityNavDisplay`。

| 开关 | 行为 |
| --- | --- |
| 关闭 | 仍接收 Navigation Event，但忽略手势进度，不创建双页面预览。完成时重新检查返回保护并出栈一次，取消不改动返回栈 |
| 开启 | 手势阶段显示双页面预览，松手后进入收尾阶段，完成后只出栈一次，取消时复原。手势期间页面生命周期不高于 STARTED |

- `LocalNavigationPageTransform` 提供稳定的读取函数。`NavigationPage` 只在 `graphicsLayer` 块内读取逐帧状态，遮罩只在绘制阶段读取，避免每帧更新组合。
- 配置变化会替换普通返回处理器并清理临时动画，不主动出栈。

### 页面何时可交互

普通页面只在以下条件同时成立时接受内容交互：自身的 `contentKey` 是栈顶、生命周期至少为 STARTED、目标可见状态为 `Visible`，且不处于预测性返回预览。它不等整段转场结束后恢复到 RESUMED，这样新页面在横移动画还没走完时就能点击。

返回时，旧页面在淡出完成的那一刻就被移除（包括它的触摸屏蔽层），点击随即落到新页面；外层的横移动画照常走完。在旧页面上开始的触摸随旧内容一起取消，不转发给新页面。预测性返回的预览与收尾不走这条提前移除的路径；图片查看器仍要求 RESUMED。

退场的 Scene、双栏详情和停用的主标签页会消费触摸、清空无障碍语义并释放键盘焦点。

### 嵌套流程

认证与账号安全流程有自己的嵌套返回栈，通过 `synchronizeNavigationPath` 与业务步骤路径同步：保留公共前缀，只替换变化的尾部，共享的步骤因此保留各自的界面状态。返回保护、焦点和敏感材料的生命周期仍由各流程管理。

### 视频页的转场

视频卡片、播放页与小窗由一个持续驻留的层执行 Zoom，而不是普通横移。视频路由通过条目元数据 `VIDEO_ZOOM_METADATA` 关闭自身推入和弹出时的横移；从视频页再打开用户、标签、登录等普通页面时仍使用 AOSP 转场。实际的 `VideoPage` 由上层的 `PlaybackHost` 持续承载，原生视频 Surface 因而不需要在组合位置之间重新绑定。生命周期、来源登记与动画细节见[小窗与画中画](media.md#小窗与画中画)。

### 排查转场性能

`SharedTransitionLayout` 的预布局测量包含 LazyColumn 的首次子组合，不能把整段预布局时间都算作重复测量的成本。定位时先看首帧的主线程切片。构建类型和 ART 编译状态都会影响 Compose 的执行成本，采样使用[本地性能变体](../development.md#动画性能采样)，并分别报告 Debug、非调试和预编译的样本。

## 窗口布局与侧栏

### 可用区域

`AppNavHost` 从 `currentWindowAdaptiveInfoV2()` 读取折叠姿态，由 [AppWindowLayout](../../app/src/main/kotlin/moe/kirakira/ui/adaptive/AppWindowLayout.kt) 统一计算稳定安全区、物理分区和普通页面可用的边界。

| 窗口状态 | 普通页面使用的区域 |
| --- | --- |
| 平展且屏幕连续（包括系统仍报告折痕坐标的情况） | 整个窗口 |
| 半开，折痕纵向且两侧相接 | 整个窗口；固定侧栏和搜索弹层跟随所在页面 |
| 铰链有宽度、存在遮挡或横向分隔 | 最大的那个分区 |

[双栏设置](account-settings.md#自适应设置布局)和[视频详情](media.md)从同一组物理分区里选择目录、播放器与详情的位置。窗口形态变化只调整布局，导航条目和各自的滚动状态始终由同一个 `ActivityNavDisplay` 持有。

### 侧栏与底栏的切换

普通页面的安全区域达到 600 × 480 dp 且不处于桌面半开姿态时，主界面、历史、收藏、自己的主页和设置目录共用 [AppDesktopRail](../../app/src/main/kotlin/moe/kirakira/navigation/AppDesktopRail.kt)；其余窗口的主界面使用 `MainBottomBar`。设置分栏时，目录一侧还要能同时容纳侧栏和目录。键盘占去空间时侧栏的控件可以滚动，但不改变导航形态。

- 侧栏在 `ActivityNavDisplay` 的 Scene 转场之外绘制。有侧栏的页面之间切换时它保持原位；路由切换或窗口跨过断点使侧栏出现或消失时，宿主用同一份显隐进度控制它的平移和透明度。
- 带侧栏的页面由 `NavigationPage` 在页面变换图层内预留侧栏占用的空间，不修改导航视口或视频坐标。
- 页面通过 `LocalPageBackButtonVisible` 得知是否该显示返回按钮。有侧栏时，账号入口由侧栏头像提供，首页顶栏省略头像按钮。
- 侧栏沿用同一个返回栈：从主界面进入一级子页时压栈，一级子页之间互切时替换栈顶，重复选择当前子页不改动栈。
- 侧栏的点击按图标的上下顺序决定页面的纵移方向。`MainScreen` 不依赖侧栏的定义，这个顺序由导航图通过 `tabOrder` 参数传入。

侧栏的选中标记复用底栏与 `Segmented` 的 `SlidingSelection`，图标动效复用[底栏图标](#主界面切换与底栏动效)的选中时钟。

## 主界面切换与底栏动效

[MainScreen](../../app/src/main/kotlin/moe/kirakira/feature/main/MainScreen.kt) 把四个标签作为主界面内的局部状态，不为每个标签建立独立的返回栈。标签切换复用[普通 Activity 转场](../../third_party/android-motion/README.md#普通-activity-转场)的参数，滑动方向由起止标签在当前导航栏中的顺序决定。底部的胶囊导航栏位于标签页转场之外。

### 底栏

[MainBottomBar](../../app/src/main/kotlin/moe/kirakira/feature/main/MainBottomBar.kt) 的布局和按住拖动的交互参考 [InstallerX-Revived 的 FloatingBottomBar](https://github.com/wxxsfxyzm/InstallerX-Revived/blob/main/app/src/main/java/com/rosan/installer/ui/library/FloatingBottomBar.kt)，用现有的 Compose API 实现，不引入它的 Miuix、液态玻璃或传感器依赖。

- **触摸由底栏统一处理**。底栏消费整个触摸序列，这样才能区分“重选当前项”和“拖动后释放”；条目仍用 `selectable` 提供键盘、无障碍和选中语义。重选通过独立的 `onDestinationReselect` 上报，用于回顶。
- **选中胶囊与前景着色**。胶囊覆盖到的前景像素染成 `primary`，其余为 `onSurface`；胶囊边缘经过图标或文字时只染覆盖到的部分。实现上先画胶囊背景，再把前景画进一个离屏层，用 `BlendMode.SrcAtop` 把强调色限制在前景已有的 alpha 上。图标和文字不需要重复组合。
- **胶囊背景按明度选色**。浅色主题用半透明的 `scrim`，深色主题改用半透明的 `onSurface`，因为黑色叠在深灰栏面上看不见。判断依据是当前 `ColorScheme` 中 `surface` 与 `onSurface` 的明度关系，而不是系统深色模式，这样应用内手动切换明暗时也正确。
- **拖动不触发重组**。连续的指针采样写入独立的状态，由 `snapshotFlow` 驱动两条边缘的跟随弹簧；组合只观察按下位置、拖拽的开始与结束和目标标签，绘制阶段读取动画边界。
- **手势的边界**。松手时提交离手指最近的标签；纵向移出容器、多指介入或手势取消时恢复已选中项。RTL 下坐标镜像。临时拖动状态不持久保存，首次显示和状态恢复直接定位到选中项。

底栏的完整高度反馈给 Scaffold 的内容留白和播放小窗的定位。

`SlidingSelection` 的边缘动画、跟手与吸附弹簧由底栏、侧栏和 `Segmented` 共用，见 [SlidingSelection.kt](../../app/src/main/kotlin/moe/kirakira/ui/components/selection/SlidingSelection.kt)。

### 图标动效

图标动画与页面切换相互独立，由 [TabIconMotion](../../app/src/main/kotlin/moe/kirakira/ui/components/navigation/TabIconMotion.kt) 统一驱动：每次新选中把一个线性时钟从 0 推到 1，各属性的关键帧经样条插值，终点回到原始姿态。取消选中时冻结时钟并把振幅衰减到零；快速重新选中时先收起残余形变，再开始新一轮。变换只在 `graphicsLayer` 或绘制阶段读取。

重复点击当前标签不重播动画，首次显示与状态恢复直接呈现最终形态。未选中的图标保持实心样式，首页与“我”在动画期间也保持 Cerasus 的实心轮廓。

图标来源与适配见 [Cerasus 首页与个人图标](../../third_party/cerasus-icons/README.md#底栏首页与个人图标)和 [Material Symbols 搜索与关注图标](../../third_party/material-symbols/README.md#底栏图标形变)。

## 启动器图标

Manifest 的 `icon` 与 `roundIcon` 引用 `mipmap-anydpi` 中的自适应图标，两者共用品牌粉色背景和白色矢量前景，并以同一前景提供主题图标的 `monochrome` 层。

最低支持 API 27，已经覆盖自适应图标所需的 API 26，所以只维护自适应 XML 与矢量图层，不保留各密度的 launcher 位图。如果将来把最低版本降到 API 25 或以下，需要补齐传统图标，并给自适应 XML 加上 `v26` 限定符。

## 启动动画

[MainActivity](../../app/src/main/kotlin/moe/kirakira/MainActivity.kt) 与 [SplashRevealController](../../app/src/main/kotlin/moe/kirakira/ui/splash/SplashRevealController.kt) 协调系统 Splash 的退出和 Compose 覆盖层，[SplashReveal](../../app/src/main/kotlin/moe/kirakira/ui/splash/SplashReveal.kt) 绘制图标遮罩。

### 主题与资源

- 启动屏始终跟随系统的浅深色。应用内的明暗模式只更新 Compose 主题，不向系统写入应用夜间模式。
- 系统 Splash 和 Compose 覆盖层共用透明矢量 `ic_splash_foreground` 的几何与配色，交接时图标的大小与位置才能一致。
- Android 12+ 通过 `drawable-v31/ic_splash.xml` 中的**单帧 `AnimationDrawable`** 引用这个矢量。单帧资源让系统按图标的显示尺寸绘制矢量；用静态图标的话，系统会先栅格化再放大，图标发虚。
- 启动图标没有底板，否则 HyperOS 会显示图标外框。
- Android 10+ 的系统栏对比度属性只在 `values-v29` 中声明。

### 交接

- Android 12+ 的系统 Splash 在裁切前会扩展图标前景。控制器用公开的 `AdaptiveIconDrawable.getExtraInsetFraction()` 计算扩展比例，并沿用平台对中心与半宽高的取整方式；Android 8.1–11 直接使用图标视图的边界。
- Android 12+ 把图标放在独立的 `SurfaceView` 里。系统把 Splash 视图移入应用窗口时，控制器在首次绘制前用位置和尺寸相同的矢量覆盖层接替它，避免 Surface 交接时闪出空白。
- 系统 Splash 保留到主题设置读取完成、且 Compose 页面完成首次布局之后才允许退出，否则两者之间会短暂露出窗口背景。Compose 覆盖层绘制出首帧后才移除系统启动屏。

### 播放与清理

只有没有保存状态的新 Activity 收到系统 Splash 的退出回调时才播放：图标先整体收缩，再以星星内部为支点展开，页面从图标轮廓内显露；图标填色消失且遮罩完全揭开后立即结束。展开曲线单调递增，不会回头重新遮住页面。页面自身不缩放。

- 后台返回、旋转、页面恢复和应用内主题切换不重播；关闭系统动画时直接显示页面。
- 离开前台、Activity 销毁或窗口尺寸变化时清理过渡。
- 过渡期间屏蔽底层的触摸与无障碍焦点，但不拦截系统返回。
