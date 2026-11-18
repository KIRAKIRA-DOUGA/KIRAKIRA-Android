# AndroidX 搜索展开布局

`PageSearchOverlayLayout.kt` 基于 AndroidX Compose Material 3 [SearchBar.kt](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt) 中 `FullScreenSearchBarLayout` 的普通展开／收回几何计算，版权为 The Android Open Source Project，采用 [Apache License 2.0](LICENSE)。

实现对照当前版本目录所选 Material 3 依赖附带的 `commonMain/androidx/compose/material3/SearchBar.kt` 源码；原文件 SHA-256：`421e325e5b94c59f1a90284eba912128f6e43dc1d211938b89bb37580da527f7`。依赖版本只在版本目录维护。

- 搜索栏的位置、宽高、圆角及结果透明度使用 `SearchBarState` 提供的展开进度。展开后的输入行居中于普通折叠顶栏的栏高，实际栏底承载阴影并定位结果区。
- 展开布局处理顶部和横向安全区。结果区避让键盘，剩余底部安全距离随列表内容滚动。
- 收起搜索栏使用接收 `SearchBarState` 的公开重载，边界取自布局坐标。设置搜索在全屏 Dialog 内展开，历史搜索在 Activity 来源 Scene 内展开，使视频打开时搜索内容仍挂载在其下方。
- 设置搜索通过 Navigation Event 将预测性返回映射为部分收起，手势完成或取消时分别继续收起或恢复展开。历史搜索的返回顺序保持为收起键盘、收起搜索、退出页面。

应用内来源与许可正文同步至 [AboutLibraries 手动声明](../aboutlibraries/README.md)。
