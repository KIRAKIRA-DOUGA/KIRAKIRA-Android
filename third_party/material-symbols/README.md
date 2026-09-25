# Material Symbols

本目录保存 Google Material Symbols 的 [Apache 2.0 许可证](LICENSE)。图标来自 [Google 官方仓库](https://github.com/google/material-design-icons/tree/master/symbols/android)。

- 样式：Material Symbols Rounded，24dp，wght 400，GRAD 0；默认 FILL 0，底栏首页与“我”的选中形态使用 FILL 1。
- 上游路径：`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`。
- 本地路径：`app/src/main/res/drawable/ic_symbol_<name>.xml`。
- 修改：移除 XML theme tint（由 Compose `Icon` 提供主题颜色）、调整缩进并增加来源注释；保留原始路径数据及 `chevron_right` 的 RTL 自动镜像。

添加、删除或更新官方图标时维护当前清单及相关说明；变更历史由 Git 记录，不追加逐次变更日志。自绘同风格图标应另用 `ic_custom_` 命名并记录设计理由，不能标为官方 Material Symbols。

## 底栏图标形变

首页与“我”使用官方描边与填充路径进行连续插值，数据维护在
`app/src/main/java/moe/kirakira/feature/main/TabIconMorph.kt`，仍遵循本目录的 Apache 2.0 许可证：

| 图标 | 描边来源 | 填充来源 |
| --- | --- | --- |
| 首页 | [home_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/home/materialsymbolsrounded/home_24px.xml) | [home_fill1_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/home/materialsymbolsrounded/home_fill1_24px.xml) |
| 我 | [person_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/person/materialsymbolsrounded/person_24px.xml) | [person_fill1_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/person/materialsymbolsrounded/person_fill1_24px.xml) |

适配保留两端的可见几何与绕向：移除上游描边路径中原有的零面积轮廓，为填充端补齐与空心区域对应的退化轮廓。
首页内部轮廓收拢至 `(480, 490)`，外轮廓的门洞同时变为官方填充端形状；个人图标的身体与头像空心轮廓分别收拢至 `(480, 660)` 和 `(480, 320)`。
两端各保留 50 个对应路径命令，在 960 × 960 坐标系内插值，最终以 24dp 绘制；中间帧是应用定义的过渡，并非额外的官方图标变体。
路径仅在图标创建时解析，动画逐帧复用绘制缓冲区，全程不使用透明度渐变。搜索沿用现有 `search` 资源，通过绘制层轻摆提供选中反馈。关注在 `FollowingTabIcon.kt` 中将 `wifi_tethering` 的原始路径按中心点、内圈、外圈拆分，保留每段路径的全部坐标与命令，以 `(480, 520)` 为共同缩放中心。三层分别延迟 0／70／140ms，峰值缩放为 1.22／1.14／1.10，依次向外扩散后回弹；不改变透明度，不再整体同步缩放。
