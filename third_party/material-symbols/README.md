# Material Symbols

Google Material Symbols 采用 [Apache 2.0](LICENSE)。原始 Android 矢量位于 [Google 官方仓库](https://github.com/google/material-design-icons/tree/master/symbols/android)。

## 保留的界面图标

底栏的搜索和关注使用 Material Symbols，界面优先使用 [Cerasus 图标](../cerasus-icons/README.md#常用操作与箭头)。以下用途沿用 24dp Rounded FILL 1 资源，Cerasus 的资源库缺少同用途图标。

| 本地资源 | 用途 |
| --- | --- |
| `ic_symbol_android` | Android 系统强调色 |
| `ic_symbol_manage_accounts` | 找回密码流程的账号确认 |
| `ic_symbol_match_word` | 关键词搜索与匹配规则 |
| `ic_symbol_switch_account` | 多账号切换 |
| `ic_symbol_policy` | 隐私政策 |
| `ic_symbol_gavel` | 服务条款 |

本地 VectorDrawable 保留原始路径及现有的 RTL 行为，由 Compose 提供主题色。上游路径为 `symbols/android/<name>/materialsymbolsrounded/<name>_fill1_24px.xml`。

## 底栏图标形变

搜索沿用现有 `search` 资源，通过绘制层轻摆与同步缩放回弹提供选中反馈。关注在 `FollowingTabIcon.kt` 中将 `wifi_tethering` 的原始路径按中心点、内圈、外圈拆分，保留每段路径的全部坐标与命令，以 `(480, 520)` 为共同缩放中心。三层错峰收拢、展开后回弹，不改变透明度。节奏参考用户提供的 Play Store 录屏，具体时长与参数统一见[底栏动效](../../docs/implementation/navigation.md#主界面切换与底栏动效)。

## 设置子页面图标

应用外小窗的 `ic_custom_pip` 保留官方 `pip` 的窗框和小窗路径，箭头来自 Cerasus。 [官方 pip 资源](https://github.com/google/material-design-icons/blob/master/symbols/android/pip/materialsymbolsrounded/pip_fill1_24px.xml) · [箭头来源](../cerasus-icons/README.md#常用操作与箭头)
