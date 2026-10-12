# 导航与启动

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护返回栈、页面转场、主界面切换与启动资源。开发约束以贡献指南为准。

- [导航与状态管理](#导航与状态管理)
- [主界面切换与底栏动效](#主界面切换与底栏动效)
- [启动器图标](#启动器图标)
- [启动动画](#启动动画)

## 导航与状态管理

应用保持单 Activity、单 `:app` 模块。[KIRAKIRAApp](../../app/src/main/java/moe/kirakira/KIRAKIRAApp.kt) 连接应用级状态和导航，并创建宿主作用域的 [AppDependenciesViewModel](../../app/src/main/java/moe/kirakira/AppDependenciesViewModel.kt)。它持有唯一的 API 客户端、认证、内容、账号设置及历史 Repository，旋转时沿用原实例；历史任务使用其 `viewModelScope`，宿主清除时取消。`SessionViewModel` 接收同一认证 Repository 与凭据网关，只编排会话操作。资料编辑和账号安全 Repository 含有流程检查点，由依赖容器工厂在条目 ViewModel 创建时提供，随条目释放，仍共用 API 客户端。

[AppNavHost](../../app/src/main/java/moe/kirakira/ui/navigation/AppNavHost.kt) 保留统一转场、返回保护和播放协调，同时在宿主作用域创建 `ImageExportViewModel` 并收集其结果 Toast，让导出任务跨越图片页出栈。图片仓库及无认证客户端由依赖容器共享。页面注册分别放在 `MainEntries`、`ContentEntries`、`ImageViewerEntries`、`SettingsEntries` 和 `AccountEntries`；注册函数显式接收依赖与事件。组合内的 [AppNavigationState](../../app/src/main/java/moe/kirakira/ui/navigation/AppNavigationState.kt) 协调返回栈操作、底栏高度、返回保护与图片查看器退场计数，只有返回栈交给 Navigation 3 保存，ViewModel 引用与组合计数不进入保存状态。

页面导航使用 Navigation 3，`rememberNavBackStack` 保存返回栈，[ActivityNavDisplay](../../app/src/main/java/moe/kirakira/ui/navigation/ActivityNavDisplay.kt) 封装 `NavDisplay`，保留页面状态和生命周期；页面通过 `NavigationPage` 接入宿主。路由和宿主使用要求见[架构规范](../../CONTRIBUTING.md#技术选型与架构)。

普通进入与返回复用 AOSP Activity 的横移和透明度参数，开启预测性返回时采用手势与松手收尾两个阶段。`ThemeViewModel.predictiveBackEnabled` 从现有 `kirakira_settings` 的 `predictive_back_enabled` 读取，缺失值为 false；与外观状态一起在 IO 调度器加载，更新使用 SharedPreferences apply，沿用非敏感设置备份。状态及修改回调经 `MainActivity`、`KIRAKIRAApp` 与 `AppNavHost` 显式传入外观页，状态同时传入顶层及认证嵌套 `ActivityNavDisplay`。

普通页面在关闭开关时仍接收 Navigation Event，但忽略手势进度，不创建双页面预览；完成时重新检查返回保护并出栈一次，取消不改变返回栈。开启时保留原有手势与收尾流程；配置变化会替换普通返回处理器并清理临时动画，不主动出栈。`LocalNavigationPageTransform` 提供稳定的变换读取函数，`NavigationPage` 仅在 `graphicsLayer` 块内读取逐帧状态，遮罩仅在 Canvas 绘制阶段读取，避免逐帧更新组合上下文。源码版本、双页面 Scene、几何变换及公开 API 适配差异统一维护在 [Android 转场说明](../../third_party/android-motion/README.md)。

`ActivityScene` 通过稳定状态引用读取最新返回栈，普通页面仅在自身 `contentKey` 为栈顶、生命周期至少为 STARTED、目标可见状态为 `EnterExitState.Visible` 且不处于预测性返回预览时开放内容交互，不再等待整段转场结束后恢复到 RESUMED。退场页保留期间消费触摸并清空内容的无障碍语义；普通返回使用与 `fadeOut` 相同的 `activityCloseFadeSpec`（延迟 35ms、持续 83ms、线性曲线）驱动透明度观察状态，`derivedStateOf` 仅在淡出完成边界移除已透明的页面内容及触摸屏蔽层，使点击落到新页。外层 Scene 仍完成原有 450ms 横移动画；在旧页开始的触摸随旧内容移除而取消，不转发给新页。预测性返回预览、收尾及其无动画出栈不走此提前移除逻辑，图片查看器仍要求 RESUMED 且可见状态与目标状态都为 Visible，并保留页面自身的转场禁用条件。所有动画继续遵循系统动画时长缩放，不使用固定延时交接。

主界面标签切换保留同一套横移与淡入淡出参数。栏面与连接列表使用平台 elevation 投影，半透明栏面由背景图层统一承载透明度与阴影；毛玻璃固定使用 Haze 背景采样，规避已在 Android 17 模拟器上复现的原生 backdrop、页面 alpha 与 elevation 合成异常。底部胶囊导航栏位于标签页转场之外。

导航会在切页开始时组合和测量新显示的页面；可保存状态与 ViewModel 装饰器保存状态，并不保留离屏页面的整棵组合树。`SharedTransitionLayout` 的预布局测量还包含 LazyColumn 首次子组合，不能将整段预布局时间当成重复测量成本。转场定位优先查看首帧的主线程切片；逐组件追踪只用于定位，交付与对照采样不携带临时探针。构建类型与 ART 编译状态会影响 Compose 执行成本，使用 [本地性能变体](../development.md#动画性能采样) 并分别报告 Debug、非调试和预编译样本，不把更换构建类型当作同配置代码优化收益。

## 主界面切换与底栏动效

`MainBottomBar` 使用圆角 `Surface` 包裹四个等宽的可选择 Tab，两侧留白 16dp、上下留白 8dp、最大宽度 560dp，阴影按胶囊形状固定绘制。系统导航 Insets 由外层容器处理，内部按钮行使用 4dp 内边距和 4dp 按钮间距，按钮最小高度为 64dp；选中胶囊覆盖图标与文字，使用 `primaryContainer`，前景使用 `primary`，保留 Tab 选中语义、按压反馈与图标动效。完整容器高度继续反馈给 Scaffold 内容留白与播放小窗定位；首页、搜索和“我”的底部留白放入滚动内容，页面背景延伸至悬浮栏后方。

[MainScreen](../../app/src/main/java/moe/kirakira/feature/main/MainScreen.kt) 将四个 Tab 作为主界面内的局部状态，未建立独立返回栈。Tab 切换复用[普通 Activity 转场](../../third_party/android-motion/README.md#普通-activity-转场)的位移、时长、系统缓动与淡入淡出参数，按排列顺序决定方向，RTL 布局镜像处理；首页头像跳转“我”使用同一套动画。

底栏图标动画与页面切换独立：首页与“我”在 300ms 内从描边连续形变为官方 Filled 造型，并轻微收缩后回弹，取消选中时反向恢复；搜索轻摆并缩放回弹；关注在 440ms 内完成由内向外扩散并回弹。图标动画不使用透明度渐变，重复点击当前 Tab 不重播，首次显示与状态恢复直接呈现最终形态，快速切换从当前进度转向新状态，并遵循系统动画时长设置。

图标来源、路径对应、形变适配及搜索／关注的缩放和延迟参数统一见 [Material Symbols 说明](../../third_party/material-symbols/README.md#底栏图标形变)。

## 启动器图标

Manifest 的 `icon` 与 `roundIcon` 分别引用 `mipmap-anydpi` 中的 `ic_launcher.xml` 与 `ic_launcher_round.xml`，两者共用品牌粉色背景和白色矢量前景，并以同一前景提供主题图标的 `monochrome` 层；外轮廓由启动器裁切。

项目最低支持 API 27，已覆盖自适应图标所需的 API 26，因此仅维护自适应 XML 与矢量图层，不保留面向旧版 Android 的各密度 launcher WebP。更新品牌图标时修改对应图层；若将来降低最低支持版本至 API 25 或以下，需补齐匹配品牌设计的传统图标，并为自适应 XML 添加 `v26` 限定符。

## 启动动画

[MainActivity](../../app/src/main/java/moe/kirakira/MainActivity.kt) 与 [SplashRevealController](../../app/src/main/java/moe/kirakira/ui/splash/SplashRevealController.kt) 协调系统 Splash 退出和 Compose 覆盖层，[SplashReveal](../../app/src/main/java/moe/kirakira/ui/splash/SplashReveal.kt) 绘制图标遮罩。

启动屏浅色使用品牌粉色 `#F06E8E` 背景和白色图标，深色使用 `#121212` 背景和品牌粉色图标，始终跟随系统浅深色。应用内明暗模式只更新 Compose 主题，不向系统写入应用夜间模式。

启动主题的共用属性由 `Theme.KIRAKIRA.Splash.Base` 提供，Manifest 继续引用 `Theme.KIRAKIRA.Splash`；Android 10+ 的系统栏对比度属性仅在 `values-v29` 中声明，低版本沿用基础主题。

所有 Android 版本的 `ic_splash` 均通过资源别名引用独立的透明矢量 `ic_splash_foreground`，不提供自适应图标背景层，移除可能在 HyperOS 上显示为外框描边的图标底板。系统 Splash 和 Compose 覆盖层共用这份矢量几何与配色，桌面与关于页图标保持原样。

Android 12+ 的系统 Splash 仍会在裁切前扩展普通矢量前景。`SplashRevealController` 使用公开的 `AdaptiveIconDrawable.getExtraInsetFraction()` 计算该扩展比例，并沿用平台对中心与半宽高的整数取整方式；Android 8.1–11 直接使用图标视图边界。退出动画不依赖启动资源必须为 `AdaptiveIconDrawable`，保持交接首帧的图标大小与位置一致。

系统 Splash 保留到主题设置读取完成、Compose 页面完成首次布局后才允许退出，避免读取完成与页面布局之间短暂露出窗口背景；关闭动画或 Activity 重建时也遵循这一就绪条件。仅在没有保存状态的新 Activity 收到系统 Splash 退出回调时播放：

1. 整个图标使用主题的 M3 Expressive `fastSpatialSpec` 收缩至 92%，首次到达目标时停止。
2. 以星星内部为支点，用 600ms 的强 S 型贝塞尔曲线 `(0.85, 0, 0.15, 1)` 展开，形成慢起步、快速冲开、缓收尾的节奏。放大倍率采用对数插值，保留起步阶段的图标轮廓；展开曲线单调递增，避免重新遮挡页面。
3. 展开进度达到 2% 时，图标填色使用 `fastEffectsSpec` 淡出，页面从星星与线条轮廓内显露；进度达到 92% 时剩余背景才开始淡出，避免过早淡化掩盖遮罩运动。
4. 图标填色消失且遮罩完全揭开后立即结束，不等待不可见的背景淡出尾段。

动画复用矢量路径直接绘制，页面自身不缩放。Compose 覆盖层绘制首帧后才移除系统启动屏，系统栏在结束后恢复当前页面样式。全部动画遵循系统动画时长设置，不叠加额外时长倍率；关闭系统动画时直接显示页面。

后台返回、旋转、页面恢复和应用内主题切换不重播。离开前台、销毁或窗口尺寸变化时清理过渡；过渡期间屏蔽底层触摸与无障碍焦点，不拦截系统返回。
