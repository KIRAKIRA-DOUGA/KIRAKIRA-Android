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
- 图形复用本项目 Apache-2.0 的 Material Symbols Rounded `ic_symbol_close`，不复制上游 close 图标。颜色继承主题 `onError`；装饰在官方按钮内容后方绘制并由表面轮廓裁剪，保留 Expressive 形变与现有彩色投影。
- 仅危险实心按钮启用；禁用、加载或系统关闭动画时隐藏并停止，文字按钮与菜单保持原样。

## 设置搜索空结果 Logo

- 来源：[LogoSettings.vue](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/blob/03047bcec32cc5007a47440c590d643a2e0db04b/components/Logo/LogoSettings.vue) 与该版本的 `assets/styles/theme/_eases.scss`，许可沿用本目录 GNU AGPL v3.0。
- Compose 实现位于 `ui/components/SettingsSearchLogo.kt`：保留 512 × 512 齿轮与长阴影路径、三色内环和中心圆，按原始 128dp 大小展示；外齿轮 −90° → 0°、内环 +90° → 0°，以原曲线 `cubic-bezier(0.1, 0.9, 0.2, 1)` 播放一次 1 秒入场动画，不循环。
- 配色按用户提供的 Android L `Settings.png` 固定为蓝灰齿轮、深蓝灰长阴影及薄荷绿中心，具体色值见[搜索空结果图形](../../docs/implementation/components.md#搜索空结果图形)，参考来源见 [Android Classic Icons](../android-classic-icons/README.md)；网页模糊投影适配为原生图层阴影。路径、内环与旋转均由 Compose 绘制，不依赖 SVG 运行时或 WebView；遵循系统动画缩放，关闭动画时显示最终状态。
- 仅设置搜索无匹配结果时使用，保留原空结果标题。历史搜索参考用户提供的 Android L Clock 图形，独立绘制大表盘和回转箭头，不复用上游齿轮路径；实现见[搜索空结果图形](../../docs/implementation/components.md#搜索空结果图形)。
