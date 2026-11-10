# Cerasus 图标与颜文字

- 来源：[KIRAKIRA-Cerasus / assets/icons/danmaku.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/danmaku.svg)，版本 `dd5b8cdcabaac4d03ad190f6b7037bfab494c72b`。
- 许可：该版本仓库的 [LICENSE](LICENSE) 为 GNU Affero General Public License v3.0；上游 README 的 BSD 徽章与 LICENSE 不一致，此处以实际 LICENSE 为准。
- 关闭状态来源（同一版本）：[danmaku_off.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/danmaku_off.svg)。
- 本地资源：`app/src/main/res/drawable/ic_custom_danmaku.xml`、`ic_custom_danmaku_off.xml`。
- 适配：SVG 转为 Android VectorDrawable，保留原始路径和 24 × 24 画布，颜色交由 Compose `Icon` 的主题 tint 提供。
- 划线动效：`ui/components/AnimatedSlashIcon.kt` 读取原始开启／关闭资源，并从关闭路径拆出同坐标圆头斜线，逐段裁剪显现或收回；主体仅对差异区域渐变，静止端点保持原图。沿用本目录 GNU AGPL v3.0 许可。
- 设计理由：沿用 Cerasus 三条错位弹幕轨道，2dp 视觉线宽与圆头已协调于 Material Symbols Rounded。使用 `ic_custom_` 命名以区分官方图标，替代原先借用的聊天气泡与字幕图标。
- 使用位置：设置页弹幕入口、视频播放页弹幕列表空状态，以及播放器 Material 3 Expressive Switch 的开启／关闭滑块图标。评论仍使用聊天气泡。

应用内许可正文同步维护于 [AboutLibraries 手动声明](../aboutlibraries/README.md)。

## 设置齿轮

设置入口、设置页旋转底纹和播放器设置按钮共用 `ic_icon_settings.xml`。该资源沿用 Cerasus 的旧版 Material Icons 齿轮，转换为 Android VectorDrawable 时保留原始路径和 24 × 24 画布，颜色由 Compose 主题提供。许可沿用本目录 GNU AGPL v3.0。 [Cerasus settings.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/settings.svg)

## 常用操作与箭头

`ic_icon_<name>.xml` 从固定版本的同名 SVG 转为 Android VectorDrawable，保留原始画布、路径、填充规则和图层顺序。Compose 负责主题着色。许可沿用本目录的 GNU AGPL v3.0。 [Cerasus 图标库](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/tree/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons)

- `star`、`download`、`history` 用于全项目对应操作与入口。视频详情操作区另用 `thumb_up`、`thumb_down`、`share`、`more_vert` 与 `photo`。
- `autoplay` 用于播放设置。`arrow_up`／`arrow_down` 用于评论投票，搜索排序方向旋转复用 `arrow_down`。回复、发送和退出登录分别使用 `reply`、`send` 和 `logout`。
- 应用外小窗图标 `ic_custom_pip` 将 `autoplay` 的圆角三角箭头旋转为右下方向并接上圆头直杆，窗框沿用 [Material Symbols 的 `pip`](../material-symbols/README.md#设置子页面图标)。
- 返回箭头 `arrow_left`、列表尖括号 `chevron_right`、发送和退出登录沿用 Android 的 RTL 自动镜像。弹幕设置和样式面板共用四个 `danmaku_*` 方向图标，按绝对方向绘制。
- 拖动和双击跳转反馈共用 `fast_forward`／`fast_rewind`。双击动效从靠近画面中心的箭头开始，见[播放器控制与设置](../../docs/implementation/media.md#播放器控制与设置)。

- 应用内小窗使用 `ic_custom_in_app_pip`，以与应用外小窗配对的圆角线框和实心小窗表达窗口层级。
- 头像裁剪使用 `ic_custom_rotate_right`，将 Cerasus 的 `replay.svg` 水平翻转为顺时针圆角箭头，沿用原 RTL 设置。
- 主题卡片的月亮来自 `LogoMoon.vue`。搜索和收藏空状态复用 `ic_icon_search` 和 `ic_icon_star`，沿用剪纸样式。

## 底栏首页与个人图标

「首页」与「我」沿用 Cerasus 的 `ic_icon_home` 和 `ic_icon_person`，选中与未选中均保持实心。首页以反向的宽高缩放形成挤压拉伸，并伴随上抬回落；个人图标从资源读取头部与身体的两个原始闭合轮廓，在 `PersonTabIcon.kt` 中分别缩放，头部附加少量纵向位移，不改写路径坐标。动效节奏参考用户提供的 Play Store 录屏，由 Compose 实现，不引入其图形或代码。参数见[底栏动效](../../docs/implementation/navigation.md#主界面切换与底栏动效)。 [home.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/home.svg) · [person.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/person.svg)

## 播放器图标

播放／暂停的动画端点分别采用 Cerasus 的 `play.svg` 和 `pause.svg`。播放三角从圆角尖端的中点拆成上下两个轮廓，暂停双竖条采用相同绕向。每条轮廓细分为十六段三次曲线，沿用现有的顺时针旋转和弹簧回弹。

倍速入口从 `ic_icon_speed` 读取实心表盘与镂空指针。音量反馈读取 `ic_icon_volume_up` 的扬声器和两道声波轮廓，缩放声波以对应 `volume_none`、`volume_down` 与 `volume_up`。 [Cerasus 图标库](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/tree/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons)

倍速码表 `feature/player/PlaybackSpeedGauge.kt` 为应用自绘组件：指针按用户提供的设计参考采用细尖、圆底的渐窄轮廓，以缓存的闭合路径绘制小圆角尖端和一体半圆底部，不叠加独立圆轴；外围参考 Material 3 Slider 的轨道、断口和轨道内圆点，大刻度为细圆头径向短杆，每段弧线中心均衡放置一个小圆点，复用 Slider 的圆点尺寸，与下方 Slider 共享官方默认的主题主色、次要容器色。弧段为填充环形路径，断口平整且有小圆角，不使用第三方轮廓，也不属于官方图标变体。零刻度位于 150°，避免与 0.25× 之间形成孤立圆点；指针按表盘半径缩放，并限制底部半宽以适应窄屏和大字体。

## 颜文字

- 来源（同一固定版本 `dd5b8cdcabaac4d03ad190f6b7037bfab494c72b`）：[kaomoji.svg](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/assets/icons/kaomoji.svg)、[kaomojis.ts](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/helpers/kaomojis.ts)。许可同上，GNU AGPL v3.0。
- 图标转换为 `app/src/main/res/drawable/ic_custom_kaomoji.xml`，保留全部路径、evenOdd 填充规则与 24 × 24 画布，颜色由主题提供。
- 设计理由：原图为圆润的颜文字表情，圆点、弧线与现有 Material Symbols Rounded 的视觉风格相容；直接沿用，不冒充官方 Material Symbols。
- 五类 268 项颜文字转换为 `app/src/main/java/moe/kirakira/data/kaomoji/KaomojiCatalog.kt`，保留顺序、空格、反斜杠及 Unicode 字符；仅调整 Kotlin 字符串转义。
- 用于评论和弹幕输入区。分类面板重新使用 Compose Material 3 Expressive 实现；最近记录为本机真实选择的 24 项，不沿用上游预置的四项记录。

## 播放器首次加载动画

此前移植的 `LogoCover` 与 `PlayerVideoSplash` 几何加载动画已移除，播放器首次加载改为保留视频封面。

## 危险按钮装饰

- 来源（同一固定版本 `dd5b8cdcabaac4d03ad190f6b7037bfab494c72b`）：[components/Button.vue](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/components/Button.vue) 中的 `decoration-move` 与危险按钮装饰样式，许可沿用本目录 GNU AGPL v3.0。
- Compose 适配位于 `ui/components/DestructiveDecoration.kt`，保留五个错峰图形、向上浮动、淡入淡出及悬停放大旋转；回弹曲线使用 Compose 三次贝塞尔实现，网页尺寸按 dp 适配。
- 图形复用 Cerasus 的 `ic_icon_close`。颜色继承主题 `onError`；装饰在官方按钮内容后方绘制并由表面轮廓裁剪，保留 Expressive 形变与现有彩色投影。
- 仅危险实心按钮启用；禁用、加载或系统关闭动画时隐藏并停止，文字按钮与菜单保持原样。

## 设置搜索空结果 Logo

- 来源：[LogoSettings.vue](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/03047bcec32cc5007a47440c590d643a2e0db04b/components/Logo/LogoSettings.vue) 与该版本的 `assets/styles/theme/_eases.scss`，许可沿用本目录 GNU AGPL v3.0。
- Compose 实现位于 `ui/components/SettingsSearchLogo.kt`：保留 512 × 512 齿轮与长阴影路径、三色内环和中心圆，按原始 128dp 大小展示；外齿轮 −90° → 0°、内环 +90° → 0°，以原曲线 `cubic-bezier(0.1, 0.9, 0.2, 1)` 播放一次 1 秒入场动画，不循环。
- 配色沿用同版本 `assets/styles/theme/_colors.scss` 的浅深模式混色规则，以应用的 `primary` 为强调色。齿轮使用 `neutral-50`，内环与中心圆保留原版的颜色映射及透明度。配色实现见[搜索空结果图形](../../docs/implementation/components.md#搜索空结果图形)。网页模糊投影适配为原生图层阴影。路径、内环与旋转均由 Compose 绘制，不依赖 SVG 运行时或 WebView；遵循系统动画缩放，关闭动画时显示最终状态。
- 仅设置搜索无匹配结果时使用，保留原空结果标题。历史搜索参考用户提供的 Android L Clock 图形，独立绘制大表盘和回转箭头，不复用上游齿轮路径；实现见[搜索空结果图形](../../docs/implementation/components.md#搜索空结果图形)。
