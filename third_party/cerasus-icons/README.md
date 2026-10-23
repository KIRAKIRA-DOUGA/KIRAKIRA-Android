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
