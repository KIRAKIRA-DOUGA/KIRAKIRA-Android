# Material Symbols

本目录保存 Google Material Symbols 的 [Apache 2.0 许可证](LICENSE)。图标来自 [Google 官方仓库](https://github.com/google/material-design-icons/tree/master/symbols/android)。

- 样式：Material Symbols Rounded，24dp，wght 400，GRAD 0，FILL 0。
- 上游路径：`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`。
- 本地路径：`app/src/main/res/drawable/ic_symbol_<name>.xml`。
- 当前图标：`add`、`arrow_back`、`block`、`chat_bubble`、`chevron_right`、`confirmation_number`、`delete`、`error`、`history`、`home`、`info`、`lock`、`logout`、`palette`、`person`、`person_remove`、`play_circle`、`search`、`settings`、`shield`、`star`、`subscriptions`、`switch_account`、`video_library`。
- 修改：移除 XML theme tint（由 Compose `Icon` 提供主题颜色）、调整缩进并增加来源注释；保留原始路径数据及 `chevron_right` 的 RTL 自动镜像。

添加、删除或更新官方图标时维护当前清单及相关说明；变更历史由 Git 记录，不追加逐次变更日志。自绘同风格图标应另用 `ic_custom_` 命名并记录设计理由，不能标为官方 Material Symbols。
