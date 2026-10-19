# NumberFlow

- 上游：[NadeemIqbal/number-flow](https://github.com/NadeemIqbal/number-flow)
- 固定提交：[`f7550f50d8f2914267c02a8e34375f944f97753b`](https://github.com/NadeemIqbal/number-flow/tree/f7550f50d8f2914267c02a8e34375f944f97753b)
- 许可：Apache-2.0，完整原文见 [LICENSE](LICENSE)。上游 README 标注 Copyright 2026 Nadeem Iqbal。

用于倍率、字数、赞踩与评论评分、关注统计、邀请码、屏蔽规则、内容总数、页码及搜索结果数量的逐位滚动，避免每次调整都替换整段文字。具体接入见[媒体实现](../../docs/implementation/media.md#播放器控制与设置)。

## 接入方式与差异

接入时上游 README 列出的 Maven 坐标无法从 Maven Central 解析，元数据地址返回 404。因此保留固定提交的三个 commonMain 源文件于 `src/`，由 `:app` 的 main Kotlin source set 编译，不增加模块或远程仓库。保留上游包名；仅将 `NumberFlow.kt` 的 Double／Long 历史值改用 Compose 原生数值状态，并同步文档示例，避免数值装箱。不引入上游构建插件、工具链、示例或测试。

应用侧共享 `AnimatedNumberText` 负责逐位滚动、完整数字语义与主题动效适配，`PlaybackSpeedText`、`CharacterCount`、`AnimatedCountLabel` 与 `AnimatedPageCount` 负责各自的本地化及完整语义。上游仅对已有字符槽执行内容过渡，增删字符时整体 Row 宽度直接变化；应用在数字区域添加 `animateContentSize` 平滑整体宽度，静态乘号放在该区域外。保留项目现有 Compose BOM 和 Material 3 固定版本。AboutLibraries 通过手动条目收集来源、版权及完整许可证，供应用内离线阅读。

上游仍是早期小型项目，固定提交时维护历史有限。未来切换 Maven 发布物前确认实际可解析，并移除本地 source set 与手动声明，避免重复定义；更新源码时同步固定提交和许可证。未进行同配置 APK 体积比较，不宣称体积增减。
