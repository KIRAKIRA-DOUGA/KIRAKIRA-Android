# Android 页面转场移植

页面动画移植自 AOSP `frameworks/base`，固定源码版本为 [`f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047`](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/)。这是单 Activity Compose 应用内的动画移植，不会调用系统私有窗口动画服务，也不代表各厂商 ROM 的定制效果。

## 源码与实现对应

| AOSP 源码 | 本项目实现 |
| --- | --- |
| [activity_open_enter.xml](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/res/res/anim/activity_open_enter.xml)、[activity_open_exit.xml](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/res/res/anim/activity_open_exit.xml) | `NavigationMotion.kt` 前进动画 |
| [activity_close_enter.xml](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/res/res/anim/activity_close_enter.xml)、[activity_close_exit.xml](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/res/res/anim/activity_close_exit.xml) | `NavigationMotion.kt` 普通返回动画 |
| [DefaultCrossActivityBackAnimation.kt](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/libs/WindowManager/Shell/src/com/android/wm/shell/back/DefaultCrossActivityBackAnimation.kt) | `PredictiveBackMotion.kt` 双页面位置、缩放与收尾 |
| [CrossActivityBackAnimation.kt](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/libs/WindowManager/Shell/src/com/android/wm/shell/back/CrossActivityBackAnimation.kt) | 手势映射、纵向移动、遮罩与 fling 弹簧 |
| [BackGestureInterpolator.java](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/java/android/view/animation/BackGestureInterpolator.java)、[Interpolators.java](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/libs/WindowManager/Shell/src/com/android/wm/shell/animation/Interpolators.java) | 手势曲线 `(0.1, 0.1, 0, 1)`、收尾 `EMPHASIZED` |
| [BackProgressAnimator.java](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/core/java/android/window/BackProgressAnimator.java) | 取消时的临界阻尼弹簧参数 |
| [dimen.xml](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/libs/WindowManager/Shell/res/values/dimen.xml) | 96dp 进入偏移、8dp 屏幕边距 |

`PredictiveBackMotion.kt` 保留 AOSP 版权声明，并标明 Compose 移植。原始代码采用 [Apache License 2.0](LICENSE)。改动包括以 Compose 绘制层替代 SurfaceControl、用协程动画替代系统动画器，以及下文的公开 API 适配。

## 普通 Activity 转场

- 横移距离 96dp、时长 450ms，API 28+ 使用公开系统资源 `android.R.interpolator.fast_out_extra_slow_in`。它与 Shell 的 `EMPHASIZED` 使用相同的两段贝塞尔路径；API 27 回退为公开的 `fast_out_slow_in`。普通转场按 RTL 镜像。
- 打开：新页从 +96dp 移至原位，旧页移至 −96dp；新页透明度线性变化，延迟 50ms，持续 83ms。
- 返回：上一页从 −96dp 移至原位，当前页移至 +96dp；当前页透明度线性变化，延迟 35ms，持续 83ms。
- 普通转场不再添加自行设计的圆角变化。

## 预测性返回

手势阶段：

- 输入进度经过 `BACK_GESTURE` 曲线；Android 传来的进度已由平台处理，不额外叠加一个跟手弹簧。
- 当前页从原始大小缩至 0.9。右边缘手势以屏幕中心为缩放中心；左边缘手势按 AOSP 几何公式向右靠近，保留 8dp 边距，不人为对称镜像。
- 上一页起始向左偏移 96dp，从 1 缩至 0.9，与当前页同步；保持不透明。去掉旧实现的上一页淡入、24dp 横移、28dp 固定圆角与最后 20% 淡出。
- 两页共同跟随纵向触摸变化，使用 `DecelerateInterpolator` 对应的二次减速公式，并保留 8dp 纵向边距。
- 遮罩为独立固定层，位于两页之间；浅色透明度 0.2，深色透明度 0.8。手势期间保持，不随页面缩放或裁切。

确认返回后的独立阶段：

- 从松手时的实际矩形开始，持续 450ms。上一页回到完整屏幕；退出页恢复完整大小，并移动到 `松手时左边界 + 96dp` 的位置。
- 与原始 `ValueAnimator` 一致，时间先通过默认 `AccelerateDecelerateInterpolator`；矩形插值再通过 `EMPHASIZED`。透明度按动画器输出的 fraction 计算：退出页 `max(1 - fraction × 5, 0)`，遮罩 `maxAlpha × (1 - fraction)`。
- 两页叠加同一个速度驱动缩放弹簧：stiffness 200、dampingRatio 0.75；缩放上限为 1。系统以 100 为单位，本项目换算到 1；初始速度限制为 −10…0，低手势进度时为 −1.2，位移可见阈值为 0.0075。
- 完成后才出栈，并跳过 Navigation 3 的普通返回转场，避免重复播放。出栈交接期间保留最终帧（退出页透明、上一页完整大小、遮罩消失），直到旧 Scene 离开组合再清理，避免旧页面短暂恢复为不透明而闪烁。

取消返回：沿相同手势几何回到原位，不改变返回栈。平台通常已经发送回到零的进度；对直接发送取消的输入，用 stiffness 1500、dampingRatio 1 的弹簧补齐复原。

## Navigation 3 接入

`ActivityNavDisplay` 使用公开的 `rememberDecoratedNavEntries`、`rememberSceneState`、`SceneStrategy`、`NavDisplay` 和 Navigation Event API。普通状态是单页面 Scene，手势及收尾期间是双页面 Scene；可保存状态和页面内容仍交由 Navigation 3 管理，不创建第二份页面截图或独立返回栈。

默认 `predictivePopTransitionSpec` 只能描述同一 seekable 转场，无法直接表达 AOSP 独立的手势／提交阶段。本宿主自行接收 Navigation Event，向 NavDisplay 提供空闲的事件状态以避免重复驱动；预览期间将页面生命周期限制到 STARTED，并屏蔽内容交互。宿主停止、销毁、尺寸或返回栈意外变化时清理临时动画。普通返回键及工具栏返回仍使用普通 Activity 动画，根页面交还系统处理。

## 公开 API 适配边界

- AOSP 的内部 `BackProgressAnimator.getVelocity()` 不公开；使用带时间戳的公开进度样本计算速度，弹簧参数与限幅保持一致。
- AOSP 从系统资源及私有显示修正值读取窗口圆角；这里优先使用 API 31+ 公开 `WindowInsets.getRoundedCorner()` 提供的最小非零屏幕圆角。无有效圆角信息、Insets 尚不可用或运行在 API 27–30 时，使用应用默认的 28dp（按屏幕密度转换为 px）；该回退值不是 AOSP 系统参数。当前项目 minSdk 为 27，不访问隐藏资源。
- 系统在真实窗口 Surface 上实现边缘像素扩展、letterbox 和系统栏外观切换。Compose 页面没有独立窗口 Surface；这些窗口级能力不复制，移动露出的区域使用宿主主题背景，系统栏由 Activity 管理。因此这是 AOSP 页面运动逻辑与参数的移植，不是逐像素的系统窗口复刻。

本次仅执行 Kotlin 编译；按用户要求未运行测试、Lint、设备或视觉检查。
