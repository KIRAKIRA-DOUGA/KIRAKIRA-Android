# Android 下拉刷新指示器

共享实现位于 [ContentPullToRefresh.kt](../../app/src/main/java/moe/kirakira/ui/components/ContentPullToRefresh.kt)，其余加载场景继续复用官方 Material 2 不确定进度组件。

## 官方来源

- 行为依据：[Material 2 Android swipe-to-refresh](https://m2.material.io/design/platform-guidance/android-swipe-to-refresh.html)。
- 拉动圆弧、阈值透明度、超拉张力及切换时长依据：[AndroidX Material PullRefreshIndicator.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material/material/src/commonMain/kotlin/androidx/compose/material/pullrefresh/PullRefreshIndicator.kt)。上游按 Apache 2.0 发布，许可证全文见 [Apache 2.0](../material-symbols/LICENSE)。
- 箭头使用现有官方 Material Symbols Rounded `chevron_right`，来源与资源处理见 [Material Symbols](../material-symbols/README.md)。

## 本地适配

- 保留 Material 3 `PullToRefreshBox` 与 `IndicatorBox` 作为手势、位置、裁剪和固定高度阴影宿主，不使用 Android View，也不切换全局主题。阴影使用官方内置的 `PullToRefreshDefaults.Elevation`，随容器位移，并在未拉动且不刷新时关闭投影；不在外层额外添加阴影 Modifier，避免容器隐藏后仍留下顶部圆形阴影。
- 保留官方拉动计算：前 40% 为圆弧增长前的空段，最大圆弧为 80%，超拉张力有上限；阈值前后透明度以线性补间在 30% 与 100% 之间变化，拉动与旋转图形以 100ms 淡入淡出切换。
- 指示器采用 Material 2 原始刷新比例：40dp 白色容器、20dp 圆弧外径与 2.5dp 线宽，拉动与旋转阶段保持一致；圆弧端点保留圆角；用开放式 Rounded Symbols 箭头替代上游旧式实心三角箭头，保持顺时针旋转，不随 RTL 镜像。
- 刷新完成后保留旋转图形到容器隐藏，用回收距离驱动缩小淡出，避免退场闪回拉动箭头；退场层使用 `CompositingStrategy.ModulateAlpha`，避免透明度变化触发离屏裁剪，截断已位移的容器或外侧阴影。不改变业务刷新状态或重复触发请求。
