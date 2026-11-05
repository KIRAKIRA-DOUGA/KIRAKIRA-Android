# AndroidX 搜索展开布局

`PageSearchOverlayLayout.kt` 适配 AndroidX Compose Material 3 [SearchBar.kt](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt) 中 `FullScreenSearchBarLayout` 的普通展开／收回几何计算，版权为 The Android Open Source Project，采用 [Apache License 2.0](LICENSE)。

实现对照当前版本目录所选 Material 3 依赖附带的 `commonMain/androidx/compose/material3/SearchBar.kt` 源码；原文件 SHA-256：`421e325e5b94c59f1a90284eba912128f6e43dc1d211938b89bb37580da527f7`。依赖版本只在版本目录维护。

- 保留胶囊位置、宽高、圆角、输入框系统顶端 Insets 和 8dp 上下留白、结果透明度的插值，时序继续由公开 `SearchBarState` 提供，使用其公开的截断进度。
- 将全屏搜索放在 Activity 来源 Scene 内，胶囊边界由公开的布局坐标提供，不调用内部 `collapsedBounds`、`animatable` 或窗口 API，不采用已弃用的 SearchBar 重载。
- 仅移植普通展开／收回，不移植官方 Dialog 专用的预测性返回窗口缩放与边缘位置计算；局部返回沿用现有处理顺序。
- 设置页继续使用官方 `ExpandedFullScreenSearchBar`；历史页使用同窗口版本，使视频打开时搜索内容仍挂载在其下方。没有独立窗口撤下、截图复制或页面提前收起。

应用内来源与许可正文同步至 [AboutLibraries 手动声明](../aboutlibraries/README.md)。
