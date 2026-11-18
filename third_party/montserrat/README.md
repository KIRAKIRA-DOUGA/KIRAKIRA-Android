# Montserrat

品牌标题字体随 APK 打包，包含 Montserrat SemiBold 和 Bold 两份静态 TTF。两者均采用 [SIL Open Font License 1.1](OFL.txt)。

## 来源与资源

- 上游仓库：[JulietaUla/Montserrat](https://github.com/JulietaUla/Montserrat)。
- 固定提交：`cc8daf2e7085006b9c112542fc82b58afc13521d`。
- 许可原件：[OFL.txt](https://github.com/JulietaUla/Montserrat/blob/cc8daf2e7085006b9c112542fc82b58afc13521d/OFL.txt)，与字体来自同一提交，本地保留原文。
- 许可 SHA-256：`8b7141c03fa4f8d44e6345d5d4931709290f0f67875e452e95ac1fd3a027802e`。

两份字体均为版本 `9.000` 的静态正体，按 Android 资源规则重命名后保留原始文件内容。

| 上游文件 | 本地资源 | 字重 | SHA-256 |
| --- | --- | ---: | --- |
| [Montserrat-SemiBold.ttf](https://github.com/JulietaUla/Montserrat/blob/cc8daf2e7085006b9c112542fc82b58afc13521d/fonts/ttf/Montserrat-SemiBold.ttf) | [montserrat_semibold.ttf](../../app/src/main/res/font/montserrat_semibold.ttf) | 600 | `b4e1563393d73fdff491a869441245aef31add2ec03d9c97a6dae4de07c52fd0` |
| [Montserrat-Bold.ttf](https://github.com/JulietaUla/Montserrat/blob/cc8daf2e7085006b9c112542fc82b58afc13521d/fonts/ttf/Montserrat-Bold.ttf) | [montserrat_bold.ttf](../../app/src/main/res/font/montserrat_bold.ttf) | 700 | `bc6e854971cea46b463be6f9eef4d9cd52f51cfc1fc0dd90c9d3e6483dc0ec61` |

## 接入

`BrandTitleFontFamily` 集中注册字体，静态页面顶栏标题、认证与账号安全流程大标题和关于页的 `for Android` 在各自的文字入口使用它。应用范围与系统字形回退见[品牌标题字体](../../docs/implementation/theme.md#品牌标题字体)。

应用内[开源组件](../aboutlibraries/README.md)通过手工条目展示来源、版权和许可全文。更新字体时同步本文件的固定提交、元数据、校验值、`OFL.txt` 与 AboutLibraries 条目。
