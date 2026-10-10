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

密码按钮的划线过渡由 `ui/components/AnimatedSlashIcon.kt` 绘制，主体读取上述原始资源，斜线轮廓取自 `visibility_off` 的对应边界；静止端点保留原始路径，中间帧为应用定义的裁剪及主体差异区域渐变。沿用 Apache 2.0 许可，没有新增图标来源。

步骤图标来自同一官方 Rounded 资源，2026-09-29 核对；保留原始 pathData，认证页在固定 AppBar 中按 32dp 渲染，登录 Logo 按 40dp 渲染。

| 图标 | 用途 |
| --- | --- |
| [`mail`](https://github.com/google/material-design-icons/blob/master/symbols/android/mail/materialsymbolsrounded/mail_24px.xml) | 登录／注册邮箱验证 |
| [`lock`](https://github.com/google/material-design-icons/blob/master/symbols/android/lock/materialsymbolsrounded/lock_24px.xml) | TOTP 验证、双重认证与身份验证器 |
| [`person`](https://github.com/google/material-design-icons/blob/master/symbols/android/person/materialsymbolsrounded/person_24px.xml) | 注册资料 |
| [`person_add`](https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml) | 注册邮箱与密码 |
| [`confirmation_number`](https://github.com/google/material-design-icons/blob/master/symbols/android/confirmation_number/materialsymbolsrounded/confirmation_number_24px.xml) | 邀请码 |
| [`manage_accounts`](https://github.com/google/material-design-icons/blob/master/symbols/android/manage_accounts/materialsymbolsrounded/manage_accounts_24px.xml) | 找回密码邮箱 |
| [`password`](https://github.com/google/material-design-icons/blob/master/symbols/android/password/materialsymbolsrounded/password_24px.xml) | 新密码、账号安全的密码入口与密码输入框 |
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

倍速码表 `feature/player/PlaybackSpeedGauge.kt` 为应用自绘组件：指针按用户提供的设计参考采用细尖、圆底的渐窄轮廓，以缓存的闭合路径绘制小圆角尖端和一体半圆底部，不叠加独立圆轴；外围参考 Material 3 Slider 的轨道、断口和轨道内圆点，大刻度为细圆头径向短杆，每段弧线中心均衡放置一个小圆点，复用 Slider 的圆点尺寸，与下方 Slider 共享官方默认的主题主色、次要容器色。弧段为填充环形路径，断口平整且有小圆角，不使用第三方轮廓，也不属于官方图标变体。零刻度位于 150°，避免与 0.25× 之间形成孤立圆点；指针按表盘半径缩放，并限制底部半宽以适应窄屏和大字体。播放器速度入口仍使用未修改路径的 [`speed_24px.xml`](https://github.com/google/material-design-icons/blob/master/symbols/android/speed/materialsymbolsrounded/speed_24px.xml)，即 `ic_symbol_speed.xml`。

以下图标来自上述官方 Rounded、24dp、wght 400 路径；播放／暂停采用 FILL 1，其余采用 FILL 0，保留原始路径数据并移除 tint，由主题着色：

- [`play_arrow`](https://github.com/google/material-design-icons/blob/master/symbols/android/play_arrow/materialsymbolsrounded/play_arrow_fill1_24px.xml)
- [`pause`](https://github.com/google/material-design-icons/blob/master/symbols/android/pause/materialsymbolsrounded/pause_fill1_24px.xml)
- [`fullscreen`](https://github.com/google/material-design-icons/blob/master/symbols/android/fullscreen/materialsymbolsrounded/fullscreen_24px.xml)
- [`fullscreen_exit`](https://github.com/google/material-design-icons/blob/master/symbols/android/fullscreen_exit/materialsymbolsrounded/fullscreen_exit_24px.xml)
- [`speed`](https://github.com/google/material-design-icons/blob/master/symbols/android/speed/materialsymbolsrounded/speed_24px.xml)：播放器倍速入口。
- [`picture_in_picture_alt`](https://github.com/google/material-design-icons/blob/master/symbols/android/picture_in_picture_alt/materialsymbolsrounded/picture_in_picture_alt_24px.xml)

`AnimatedPlaybackIcon.kt` 基于上述播放／暂停 FILL 1 路径制作形变：播放三角形沿 y=480 拆成两个同向闭合轮廓，直线转换为等价二次曲线；暂停双竖条重新选择起点并拆分直边，使每个轮廓具有十六段二次曲线。端点保持官方外轮廓，形变中间态为自定义插值。两个方向都对目标控制点反向旋转 90°，再随动画顺时针旋转 90°；采用主题的 Material 3 Expressive `fastSpatialSpec` 弹簧，形变与旋转稍微超过目标后回弹；中断时从当前可见控制点重新插值，保持连续。未新增第三方图标或依赖，沿用 Apache 2.0 许可。

## 视频卡片元数据图标

- 评论空状态复用 `chat_bubble`；弹幕入口及空状态使用 [Cerasus 弹幕图标](../cerasus-icons/README.md)，不属于官方 Material Symbols。
- 播放量复用 `play_circle`，发布时间复用 `calendar_today`。
- 时长使用官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 的 [schedule_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/schedule/materialsymbolsrounded/schedule_24px.xml)，本地为 `ic_symbol_schedule.xml`；仅移除 theme tint、整理缩进并增加来源注释，保留原始路径数据。

## 视频搜索图标

以下官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 图标用于搜索结果工具栏；仅移除 theme tint、整理缩进并增加来源注释，保留原始路径及上游 RTL 自动镜像设置：

| 图标 | 官方来源 | 用途 |
| --- | --- | --- |
| `grid_view` | [grid_view_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/grid_view/materialsymbolsrounded/grid_view_24px.xml) | 网格布局 |
| `view_list` | [view_list_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/view_list/materialsymbolsrounded/view_list_24px.xml) | 列表布局，保留自动镜像 |
| `sort` | [sort_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/sort/materialsymbolsrounded/sort_24px.xml) | 排序菜单，保留自动镜像 |

搜索、添加／移除标签、关闭、选择状态及升降序复用现有 `search`、`add`、`close`、`label`、`check`、`arrow_upward` 与 `arrow_downward`。

## 屏蔽规则图标

分类总览、规则列表、空状态和添加面板共用以下官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 图标；仅移除 theme tint、整理缩进并增加来源注释，保留路径数据及 Label 的 RTL 自动镜像。

| 图标 | 官方来源 | 用途 |
| --- | --- | --- |
| `label` | [label_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/label/materialsymbolsrounded/label_24px.xml) | 标签 |
| `match_word` | [match_word_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/match_word/materialsymbolsrounded/match_word_24px.xml) | 关键词 |
| `regular_expression` | [regular_expression_24px.xml](https://github.com/google/material-design-icons/blob/master/symbols/android/regular_expression/materialsymbolsrounded/regular_expression_24px.xml) | 正则表达式 |

屏蔽用户和隐藏用户分别复用 `block`、`visibility_off`。

## 资料编辑图标

- [`badge`](https://github.com/google/material-design-icons/blob/master/symbols/android/badge/materialsymbolsrounded/badge_24px.xml)：资料设置入口、资料页顶栏底纹与空状态，与 Cerasus 的资料入口一致。
- [`edit`](https://github.com/google/material-design-icons/blob/master/symbols/android/edit/materialsymbolsrounded/edit_24px.xml)：编辑头像与简介。
- [`alternate_email`](https://github.com/google/material-design-icons/blob/master/symbols/android/alternate_email/materialsymbolsrounded/alternate_email_24px.xml)：用户名输入框。
- [`rotate_right`](https://github.com/google/material-design-icons/blob/master/symbols/android/rotate_right/materialsymbolsrounded/rotate_right_24px.xml)：头像裁剪的顺时针旋转，保留上游自动镜像。

均使用官方 Rounded、24dp、wght 400、GRAD 0、FILL 0，保留路径数据，移除 theme tint。日期、标签、保存及删除操作复用已有图标。

## 发送栏图标

- [`send`](https://github.com/google/material-design-icons/blob/master/symbols/android/send/materialsymbolsrounded/send_24px.xml)：评论与弹幕发送，保留上游 RTL 自动镜像。
- [`text_format`](https://github.com/google/material-design-icons/blob/master/symbols/android/text_format/materialsymbolsrounded/text_format_24px.xml)：弹幕样式入口。

均使用官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 路径；仅移除 theme tint、整理 XML 缩进并增加来源注释。样式面板复用现有 `palette` 与 `check`。

## 颜文字输入面板

- [`keyboard`](https://github.com/google/material-design-icons/blob/master/symbols/android/keyboard/materialsymbolsrounded/keyboard_24px.xml)：切换回系统键盘。沿用 Rounded、24dp、wght 400、GRAD 0、FILL 0，保留原始路径并移除 theme tint。

## 设置子页面图标

设置子页面图标、按钮与外观卡片使用以下官方 Rounded、24dp、wght 400、GRAD 0、FILL 0 路径；仅移除 theme tint、整理 XML 缩进并增加来源注释。外观卡片的明暗模式图标显示为 40dp，壁纸与自定义图标显示为 48dp，自定义选项复用上述 `edit` 资源。`east`、`west` 上游未设 `autoMirrored`，表示弹幕的绝对方向，RTL 下不镜像。

| 图标 | 用途 |
| --- | --- |
| [`light_mode`](https://github.com/google/material-design-icons/blob/master/symbols/android/light_mode/materialsymbolsrounded/light_mode_24px.xml) | 外观：浅色 |
| [`dark_mode`](https://github.com/google/material-design-icons/blob/master/symbols/android/dark_mode/materialsymbolsrounded/dark_mode_24px.xml) | 外观：深色 |
| [`brightness_auto`](https://github.com/google/material-design-icons/blob/master/symbols/android/brightness_auto/materialsymbolsrounded/brightness_auto_24px.xml) | 外观：跟随系统 |
| [`wallpaper`](https://github.com/google/material-design-icons/blob/master/symbols/android/wallpaper/materialsymbolsrounded/wallpaper_24px.xml) | 外观：壁纸颜色 |
| [`pip`](https://github.com/google/material-design-icons/blob/master/symbols/android/pip/materialsymbolsrounded/pip_24px.xml) | 播放：应用外／应用内小窗 |
| [`autoplay`](https://github.com/google/material-design-icons/blob/master/symbols/android/autoplay/materialsymbolsrounded/autoplay_24px.xml) | 播放：自动播放 |
| [`opacity`](https://github.com/google/material-design-icons/blob/master/symbols/android/opacity/materialsymbolsrounded/opacity_24px.xml) | 弹幕：不透明度 |
| [`format_size`](https://github.com/google/material-design-icons/blob/master/symbols/android/format_size/materialsymbolsrounded/format_size_24px.xml) | 弹幕：字号 |
| [`fit_screen`](https://github.com/google/material-design-icons/blob/master/symbols/android/fit_screen/materialsymbolsrounded/fit_screen_24px.xml) | 弹幕：显示区域 |
| [`vertical_align_top`](https://github.com/google/material-design-icons/blob/master/symbols/android/vertical_align_top/materialsymbolsrounded/vertical_align_top_24px.xml) | 弹幕：顶部弹幕 |
| [`vertical_align_bottom`](https://github.com/google/material-design-icons/blob/master/symbols/android/vertical_align_bottom/materialsymbolsrounded/vertical_align_bottom_24px.xml) | 弹幕：底部弹幕 |
| [`east`](https://github.com/google/material-design-icons/blob/master/symbols/android/east/materialsymbolsrounded/east_24px.xml) | 弹幕：从左到右 |
| [`west`](https://github.com/google/material-design-icons/blob/master/symbols/android/west/materialsymbolsrounded/west_24px.xml) | 弹幕：从右到左（滚动） |
| [`code`](https://github.com/google/material-design-icons/blob/master/symbols/android/code/materialsymbolsrounded/code_24px.xml) | 关于：源代码 |
| [`description`](https://github.com/google/material-design-icons/blob/master/symbols/android/description/materialsymbolsrounded/description_24px.xml) | 关于：开源许可 |
| [`policy`](https://github.com/google/material-design-icons/blob/master/symbols/android/policy/materialsymbolsrounded/policy_24px.xml) | 关于：隐私政策 |
| [`gavel`](https://github.com/google/material-design-icons/blob/master/symbols/android/gavel/materialsymbolsrounded/gavel_24px.xml) | 关于：服务条款 |

## 下拉刷新箭头

下拉刷新基于现有官方 Rounded `chevron_right` 的中心线绘制圆弧末端的开放式箭头：原始 960 单位画布中的中心线为 `(376,296) → (560,480) → (376,664)`。资源本身保留原始 pathData；指示器使用中心线路径，与圆弧共用固定 2.5dp 的描边及圆角端点、圆角连接，解决填充图标缩放后箭头比圆弧细的问题。绘制时按手势缩放路径长度、沿圆弧切线旋转并应用主题强调色，不引入旧版 Material Icons 或实心三角箭头；指示器局部固定 LTR，保持顺时针刷新方向。圆弧与拉动计算见 [Android 刷新指示器说明](../android-refresh/README.md)。
