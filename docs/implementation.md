# 实现说明

[返回项目首页](../README.md) · [功能现状](features.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

本文说明当前实现及接入方式；开发约束统一维护在贡献指南。依赖版本及配置来源见[开发环境](development.md#开发环境)。

## 导航与状态管理

应用保持单 Activity、单 `:app` 模块。[KIRAKIRAApp](../app/src/main/java/moe/kirakira/KIRAKIRAApp.kt) 连接应用级状态和导航，[AppNavHost](../app/src/main/java/moe/kirakira/ui/navigation/AppNavHost.kt) 注册页面并处理进入、返回事件。当前主题与认证状态使用 ViewModel，尚未建立完整数据层或依赖注入。

页面导航使用 Navigation 3，`rememberNavBackStack` 保存返回栈，[ActivityNavDisplay](../app/src/main/java/moe/kirakira/ui/navigation/ActivityNavDisplay.kt) 封装 `NavDisplay`，保留页面状态和生命周期；页面通过 `NavigationPage` 接入宿主。路由和宿主使用要求见[架构规范](../CONTRIBUTING.md#技术选型与架构)。

普通进入与返回复用 AOSP Activity 的横移和透明度参数，预测性返回采用手势与松手收尾两个阶段。源码版本、双页面 Scene、Navigation Event 接入、几何变换、遮罩、完成／取消处理及公开 API 适配差异统一维护在 [Android 转场说明](../third_party/android-motion/README.md)。

### 主界面切换与底栏动效

[MainScreen](../app/src/main/java/moe/kirakira/feature/main/MainScreen.kt) 将四个 Tab 作为主界面内的局部状态，未建立独立返回栈。Tab 切换复用[普通 Activity 转场](../third_party/android-motion/README.md#普通-activity-转场)的位移、时长、系统缓动与淡入淡出参数，按排列顺序决定方向，RTL 布局镜像处理；首页头像跳转“我”使用同一套动画。

底栏图标动画与页面切换独立：首页与“我”在 300ms 内从描边连续形变为官方 Filled 造型，并轻微收缩后回弹，取消选中时反向恢复；搜索轻摆并缩放回弹；关注在 440ms 内完成由内向外扩散并回弹。图标动画不使用透明度渐变，重复点击当前 Tab 不重播，首次显示与状态恢复直接呈现最终形态，快速切换从当前进度转向新状态，并遵循系统动画时长设置。

图标来源、路径对应、形变适配及搜索／关注的缩放和延迟参数统一见 [Material Symbols 说明](../third_party/material-symbols/README.md#底栏图标形变)。

## 视频列表布局

[评论页](../app/src/main/java/moe/kirakira/feature/video/VideoCommentsPage.kt)与[弹幕条目](../app/src/main/java/moe/kirakira/feature/video/DanmakuListItem.kt)使用官方 `SegmentedListItem`，保留默认条目配色、首尾大圆角、内部小圆角和官方分段间距。

评论区以 `surfaceContainer` 为背景，左右留白为 8dp。页码与排序使用官方 M3E 悬浮工具栏，固定在评论区右上角，距顶部、右侧各 8dp。总数标题与工具栏在空间足够时同排，在窄屏或大字体下分行，列表顶部仅保留 8dp 留白。

弹幕沿用相同的背景层次、8dp 外边距、分段圆角与间距。分页、排序、跳转与演示交互见[视频页功能](features.md#视频页)。

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
| `AuthScreen` / `LoginForm` | 展示状态并上报操作 |
| `AuthPage` | 处理键盘、提示与页面事件 |
| `AuthViewModel` | 通过 `StateFlow<AuthUiState>` 管理输入、校验及统一提交入口 |

`ActivityNavDisplay` 在可保存状态装饰器之后添加官方 `rememberViewModelStoreNavEntryDecorator`，让认证 ViewModel 绑定导航条目，旋转时保留、出栈后释放。所需依赖为 `lifecycle-viewmodel-compose` 与 `lifecycle-viewmodel-navigation3`，与现有 Lifecycle 保持一致。

当前仅邮箱通过 `SavedStateHandle` 恢复，密码只留在内存中，进程重建后为空，界面重建后默认隐藏；密码存储限制见[认证状态约束](../CONTRIBUTING.md#认证状态约束)。加载、失败与重试目前通过 Preview 展示，业务边界见[添加账户](features.md#添加账户)。

后续在 ViewModel 的统一提交入口接入 Repository 和 API，服务端 DTO 不进入 UI。当前未引入网络库或会话存储，认证页面继续复用既有导航转场。

## 共享组件接入

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

## 启动动画

[MainActivity](../app/src/main/java/moe/kirakira/MainActivity.kt) 与 [SplashRevealController](../app/src/main/java/moe/kirakira/ui/splash/SplashRevealController.kt) 协调系统 Splash 退出和 Compose 覆盖层，[SplashReveal](../app/src/main/java/moe/kirakira/ui/splash/SplashReveal.kt) 绘制图标遮罩。

启动屏浅色使用品牌粉色 `#F06E8E` 背景和白色图标，深色使用 `#121212` 背景和品牌粉色图标，始终跟随系统浅深色。应用内明暗模式只更新 Compose 主题，不向系统写入应用夜间模式。

仅在没有保存状态的新 Activity 收到系统 Splash 退出回调时，待页面准备好后播放：

1. 整个图标使用主题的 M3 Expressive `fastSpatialSpec` 收缩至 92%，首次到达目标时停止。
2. 以星星内部为支点，用 600ms 的强 S 型贝塞尔曲线 `(0.85, 0, 0.15, 1)` 展开，形成慢起步、快速冲开、缓收尾的节奏。放大倍率采用对数插值，保留起步阶段的图标轮廓；展开曲线单调递增，避免重新遮挡页面。
3. 展开进度达到 2% 时，图标填色使用 `fastEffectsSpec` 淡出，页面从星星与线条轮廓内显露；进度达到 92% 时剩余背景才开始淡出，避免过早淡化掩盖遮罩运动。
4. 图标填色消失且遮罩完全揭开后立即结束，不等待不可见的背景淡出尾段。

动画复用矢量路径直接绘制，页面自身不缩放。Compose 覆盖层绘制首帧后才移除系统启动屏，系统栏在结束后恢复当前页面样式。全部动画遵循系统动画时长设置，不叠加额外时长倍率；关闭系统动画时直接显示页面。

后台返回、旋转、页面恢复和应用内主题切换不重播。离开前台、销毁或窗口尺寸变化时清理过渡；过渡期间屏蔽底层触摸与无障碍焦点，不拦截系统返回。
