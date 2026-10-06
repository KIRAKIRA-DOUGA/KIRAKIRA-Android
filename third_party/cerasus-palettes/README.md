# Cerasus 个性色资源

- 上游：[KIRAKIRA-Cerasus](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/tree/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b)，固定版本 `dd5b8cdcabaac4d03ad190f6b7037bfab494c72b`。
- 原图目录：[public/static/images/palettes](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/tree/dd5b8cdcabaac4d03ad190f6b7037bfab494c72b/public/static/images/palettes)，包括 `pink.png`、`blue.png`、`purple.png`、`green.png`、`yellow.png`、`cyan.png`、`red.png`；每张为 750 × 1000 RGB PNG。
- 名称、角色与顺序来自上游 `pages/settings/appearance.vue`，中英文主题名来自 `i18n/locales/`，原色来自 `assets/styles/theme/_colors.scss`。
- 本地资源：`app/src/main/res/drawable-nodpi/theme_palette_<color>.webp`。通过 `cwebp -lossless -exact -m 6` 无损转换，保留原始尺寸与像素；不在源图中加入着色、文字或裁切。界面使用 1:1 方形图片区域并靠上裁切，主题名与日文角色名置于图片下方，选中勾选标记由 Compose 绘制于文字区色标内。
- 使用位置：Android「设置 → 外观 → 个性色」七张预设卡片，资源随 APK 打包，展示不请求网络。

## 版权记录

图片版权归原作者所有。

明暗主题缩略图使用 Android 自身布局与已有品牌／Material Symbols 资源绘制，没有移植 Cerasus 的 `LogoThemePreview.vue` 图形。
