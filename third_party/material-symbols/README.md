# Material Symbols

本目录保存 Google Material Symbols 的 [Apache 2.0 许可证](LICENSE)。图标来自 [Google 官方仓库](https://github.com/google/material-design-icons/tree/master/symbols/android)。

- 样式：Material Symbols Rounded，24dp，wght 400，GRAD 0；默认 FILL 0，底栏首页与“我”的选中形态、播放器播放／暂停图标使用 FILL 1。
- 上游路径：`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`。
- 本地路径：`app/src/main/res/drawable/ic_symbol_<name>.xml`。
- 修改：移除 XML theme tint（由 Compose `Icon` 提供主题颜色）、调整缩进并增加来源注释；保留原始路径数据及上游 `autoMirrored` 设置（例如 `chevron_right`、`label`）。

添加、删除或更新官方图标时维护当前清单及相关说明；变更历史由 Git 记录，不追加逐次变更日志。自绘同风格图标应另用 `ic_custom_` 命名并记录设计理由，不能标为官方 Material Symbols。

## 认证页图标

认证页使用以下官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 图标；仅移除上游 theme tint 并整理 XML 格式，保留路径数据：

| 图标 | 官方来源 | 用途 |
| --- | --- | --- |
| `close` | [close_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/close/materialsymbolsrounded/close_24px.xml) | 关闭认证页 |
| `visibility` | [visibility_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/visibility/materialsymbolsrounded/visibility_24px.xml) | 显示密码 |
| `visibility_off` | [visibility_off_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/visibility_off/materialsymbolsrounded/visibility_off_24px.xml) | 隐藏密码 |

注册入口复用现有 `add` 图标，品牌标识复用应用自身的 `logo_kirakira`。

步骤图标来自同一官方 Rounded 资源，2026-09-29 核对；保留原始 pathData，认证页在固定 AppBar 中按 32dp 渲染，登录 Logo 按 40dp 渲染。

| 图标 | 用途 |
| --- | --- |
| [`mail`](https://github.com/google/material-design-icons/blob/master/symbols/android/mail/materialsymbolsrounded/mail_24px.xml) | 登录／注册邮箱验证 |
| [`shield`](https://github.com/google/material-design-icons/blob/master/symbols/android/shield/materialsymbolsrounded/shield_24px.xml) | TOTP 验证 |
| [`person`](https://github.com/google/material-design-icons/blob/master/symbols/android/person/materialsymbolsrounded/person_24px.xml) | 注册资料 |
| [`person_add`](https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml) | 注册邮箱与密码 |
| [`confirmation_number`](https://github.com/google/material-design-icons/blob/master/symbols/android/confirmation_number/materialsymbolsrounded/confirmation_number_24px.xml) | 邀请码 |
| [`manage_accounts`](https://github.com/google/material-design-icons/blob/master/symbols/android/manage_accounts/materialsymbolsrounded/manage_accounts_24px.xml) | 找回密码邮箱 |
| [`lock_reset`](https://github.com/google/material-design-icons/blob/master/symbols/android/lock_reset/materialsymbolsrounded/lock_reset_24px.xml) | 新密码 |
| [`help`](https://github.com/google/material-design-icons/blob/master/symbols/android/help/materialsymbolsrounded/help_24px.xml) | TOTP 恢复说明 |
| [`save`](https://github.com/google/material-design-icons/blob/master/symbols/android/save/materialsymbolsrounded/save_24px.xml) | 本机会话保存／清理重试与已保存密码入口 |

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
路径仅在图标创建时解析，动画逐帧复用绘制缓冲区，全程不使用透明度渐变。搜索沿用现有 `search` 资源，通过绘制层轻摆与同步缩放回弹提供选中反馈：320ms 内缩放依次为 1 → 0.94 → 1.08 → 1，与首页及“我”的回弹节奏一致。关注在 `FollowingTabIcon.kt` 中将 `wifi_tethering` 的原始路径按中心点、内圈、外圈拆分，保留每段路径的全部坐标与命令，以 `(480, 520)` 为共同缩放中心。三层分别延迟 0／70／140ms，峰值缩放为 1.22／1.14／1.10，依次向外扩散后回弹；不改变透明度，不再整体同步缩放。

## 播放器控制图标

以下图标来自上述官方 Rounded、24dp、wght 400 路径；播放／暂停采用 FILL 1，其余采用 FILL 0，保留原始路径数据并移除 tint，由主题着色：

- [`play_arrow`](https://github.com/google/material-design-icons/blob/master/symbols/android/play_arrow/materialsymbolsrounded/play_arrow_fill1_24px.xml)
- [`pause`](https://github.com/google/material-design-icons/blob/master/symbols/android/pause/materialsymbolsrounded/pause_fill1_24px.xml)
- [`fullscreen`](https://github.com/google/material-design-icons/blob/master/symbols/android/fullscreen/materialsymbolsrounded/fullscreen_24px.xml)
- [`fullscreen_exit`](https://github.com/google/material-design-icons/blob/master/symbols/android/fullscreen_exit/materialsymbolsrounded/fullscreen_exit_24px.xml)
- [`picture_in_picture_alt`](https://github.com/google/material-design-icons/blob/master/symbols/android/picture_in_picture_alt/materialsymbolsrounded/picture_in_picture_alt_24px.xml)

## 视频卡片元数据图标

- 评论空状态复用 `chat_bubble`；弹幕入口及空状态使用 [Cerasus 弹幕图标](../cerasus-icons/README.md)，不属于官方 Material Symbols。
- 播放量复用 `play_circle`，发布时间复用 `calendar_today`。
- 时长使用官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 的 [schedule_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/schedule/materialsymbolsrounded/schedule_24px.xml)，本地为 `ic_symbol_schedule.xml`；仅移除 theme tint、整理缩进并增加来源注释，保留原始路径数据。

## 屏蔽规则图标

分类总览、规则列表、空状态和添加面板共用以下官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 图标；仅移除 theme tint、整理缩进并增加来源注释，保留路径数据及 Label 的 RTL 自动镜像。

| 图标 | 官方来源 | 用途 |
| --- | --- | --- |
| `label` | [label_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/label/materialsymbolsrounded/label_24px.xml) | 标签 |
| `match_word` | [match_word_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/match_word/materialsymbolsrounded/match_word_24px.xml) | 关键词 |
| `regular_expression` | [regular_expression_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/regular_expression/materialsymbolsrounded/regular_expression_24px.xml) | 正则表达式 |

屏蔽用户和隐藏用户分别复用 `block`、`visibility_off`。

## 发送栏图标

- [`send`](https://github.com/google/material-design-icons/blob/master/symbols/android/send/materialsymbolsrounded/send_24px.xml)：评论与弹幕发送，保留上游 RTL 自动镜像。
- [`text_format`](https://github.com/google/material-design-icons/blob/master/symbols/android/text_format/materialsymbolsrounded/text_format_24px.xml)：弹幕样式入口。

均使用官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 路径；仅移除 theme tint、整理 XML 缩进并增加来源注释。样式面板复用现有 `palette` 与 `check`。

## 颜文字输入面板

- [`keyboard`](https://github.com/google/material-design-icons/blob/master/symbols/android/keyboard/materialsymbolsrounded/keyboard_24px.xml)：切换回系统键盘。沿用 Rounded、24dp、wght 400、GRAD 0、FILL 0，保留原始路径并移除 theme tint。
