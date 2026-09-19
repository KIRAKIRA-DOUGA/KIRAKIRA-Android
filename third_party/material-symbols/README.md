# Material Symbols

本目录保存 Google Material Symbols 的 [Apache 2.0 许可证](LICENSE)。图标来自 [Google 官方仓库](https://github.com/google/material-design-icons/tree/master/symbols/android)，导入日期为 2026-09-18。

- 样式：Material Symbols Rounded，24dp，wght 400，GRAD 0，FILL 0。
- 上游路径：`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`。
- 本地路径：`app/src/main/res/drawable/ic_symbol_<name>.xml`。
- 当前图标：`home`、`search`、`subscriptions`、`person`、`history`、`video_library`、`settings`、`chevron_right`、`shield`、`lock`、`block`、`confirmation_number`、`palette`、`play_circle`、`chat_bubble`、`info`、`switch_account`、`logout`、`arrow_back`。
- 修改：移除 XML theme tint（由 Compose `Icon` 提供主题颜色）、调整缩进并增加来源注释；保留原始路径数据及 `chevron_right` 的 RTL 自动镜像。

2026-09-20 补充导入 `star`，用于收藏入口，样式与处理方式同上。

添加或更新官方图标时同步清单。自绘同风格图标应另用 `ic_custom_` 命名并记录设计理由，不能标为官方 Material Symbols。
