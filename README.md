# KIRAKIRA Compose

KIRAKIRA 的 Android 客户端，使用 **Kotlin + Jetpack Compose**，设计语言为 **Material 3 Expressive**，图标使用官方 **Material Symbols Rounded**。

当前是基础布局原型：底部导航为 **首页（Home）、搜索（Search）、关注（Following）、我（Me）**。首页展示 KIRAKIRA 标题、头像占位及演示视频卡片，搜索页展示静态 SearchBar，关注页展示标题；“我”包含个人资料、统计、个人主页、历史、收藏和设置入口。支持导航切换及“我 → 设置 → 关于”的进入和返回。设置页分为“我”“常规”与账户操作，外观、关于和切换账户入口可用，其他设置项和登出尚未接入业务。

首页演示视频卡片可进入独立的 Material 3 Expressive 播放占位页。播放页状态栏固定为黑色背景和浅色图标，退出后恢复应用主题的系统栏样式。顶部固定 16:9 视频占位和“简介／评论／弹幕”标签栏，下方内容支持左右滑动切换，点击标签同样平滑滑到目标页，标签选中状态同步当前页；三个页面首尾不循环，各页独立纵向滚动并保留位置；宽屏内容居中，视频高度不超过可用高度的 40%。简介包含作者、关注、标题、视频信息、介绍与操作栏，评论和弹幕使用本地假数据。评论页采用官方 `SegmentedListItem` 分段样式的连续滚动列表，使用默认条目配色、首尾大圆角、内部小圆角和官方分段间距；评论区以 `surfaceContainer` 为背景，左右留白为 8dp，提供 120 条演示评论，每 20 条对应一页；页码与排序使用官方 M3E 悬浮工具栏，固定在评论区右上角，距顶部和右侧各 8dp；页码根据悬浮区域下方最上面的可见评论更新，可跳转到当前排序下的指定页或固定楼层，跳转目标避开工具栏。评论与弹幕在列表首项显示各自总条数，随内容滚动；评论总数在空间足够时与悬浮工具栏同一行对齐，窄屏或大字体下自动改为上下排列，列表顶部仅保留 8dp 留白；总数标题不计入评论页码，弹幕数量统一取自占位数据列表。支持按时间、评分正序或倒序排列，默认时间倒序，切换排序回到第一页；赞踩互斥，评分排序随演示投票更新，回复与更多暂时显示待接入提示。弹幕同样使用官方 `SegmentedListItem`，沿用评论区的背景层次、8dp 外边距和分段圆角间距，左侧显示时间，右侧正文支持长按选择部分文字和复制。关注、赞踩和收藏可切换演示状态，赞踩互斥；下载、分享和更多显示待接入提示。标签、滚动位置和交互状态支持旋转及系统状态恢复，退出页面后重新进入恢复默认值。当前不包含播放器、网络请求或真实业务操作。

设置、外观、关于和切换账户页共用可折叠大标题栏：首次进入默认折叠，内容到顶后下拉展开，上滑收起，并保存展开程度。其他页面可按需要复用，适用场景与接入示例见[贡献指南](CONTRIBUTING.md#可选的可折叠大标题栏)。

“设置 → 外观”支持独立选择明暗模式与主题配色。默认以产品主色 `#F06E8E` 为种子生成完整浅深色方案；可选择 Android 12+ 的系统动态颜色，或使用六种预设及自定义颜色选择器（色盘、亮度与六位 HEX 输入）。色板以三色分区显示，选中时通过官方 `ToggleButton` 的 Expressive 动画从圆形变为圆角矩形。选择立即生效并持久保存，系统配色与手动配色之间切换会保留手动选色；自定义颜色独立保存，选择预设不会覆盖，再次打开选色器时恢复上次确认的自定义颜色。低版本 Android 使用手动配色。手动配色可选择柔和色调（默认）、单色、中性、鲜艳、表现力、忠实、内容、彩虹、缤纷与经典强调色十种算法，色板和选色器预览同步更新，算法选择持久保存。黑白灰主题可选择“单色”；系统动态配色沿用系统算法。「经典强调色」使用白色／深灰色顶栏和底栏、分层灰阶背景，并在浅深模式下保留所选原色作为 `primary`；组件直接继承主题颜色，Switch 滑块与填充按钮文字统一使用 `onPrimary`：白色与主色对比达到 2.5:1 时优先白色，否则使用黑色。文字按钮、单选按钮与输入框焦点直接使用原色，不逐组件调整明度；这是保留原色的视觉取舍，不保证所有文字达到 4.5:1，极浅或极深选色可能对比不足。所有方案沿用 `MaterialExpressiveTheme` 的形状、排版和动效。

页面导航使用 **Navigation 3 1.1.7**：`rememberNavBackStack` 保存返回栈，`ActivityNavDisplay` 封装 `NavDisplay`，保留页面状态和生命周期。普通进入和返回采用 AOSP Activity 的横移与透明度参数；预测性返回移植 AOSP 的手势曲线、双页面几何变换、浅深色遮罩和独立的松手收尾阶段，完成后出栈，取消时复原。固定源码版本及公开 API 的适配差异见 [Android 转场说明](third_party/android-motion/README.md)。底栏仍在主界面内切换：四个 Tab 按排列顺序左右平移，复用普通 Activity 转场的 96dp 横移、450ms 时长、系统缓动与淡入淡出，RTL 布局镜像处理。各页顶栏和内容一起移动，底部导航栏保持固定；跨 Tab 直接切换，连续点击转向最新目标，重复点击当前 Tab 不重播。首页头像跳转“我”使用相同动画；选中 Tab、首页与“我”的滚动位置保持可恢复，首次显示和状态恢复不播放切换动画，从设置或视频页返回时恢复原 Tab 和滚动位置。在主界面按系统返回退出应用，不额外跳转首页。

底栏图标在选中时独立播放动效：首页与“我”在 300ms 内从描边连续形变为官方 Filled 造型，并轻微收缩后回弹；取消选中时反向恢复，全程不使用淡入淡出。搜索轻微左右摆动一次，关注的中心点、内圈、外圈以 70ms 间隔依次脉冲，缩放幅度由内向外递减，在 440ms 内完成一次由内向外扩散并回弹的动效，完成后回到原位。重复点击当前 Tab 不重播，首次显示和状态恢复直接呈现最终形态，快速切换从当前进度转向新状态，遵循系统动画时长设置。图标路径来源与形变适配见 [Material Symbols 说明](third_party/material-symbols/README.md#底栏图标形变)。

“设置 → 外观”还提供「阴影」开关，默认关闭，切换立即生效并持久保存。开启后所有应用顶栏始终显示 4dp 阴影，底部导航栏显示 8dp 阴影，参考 [Material 标准高度](https://m1.material.io/material-design/elevation-shadows.html)；大标题展开或折叠不改变阴影高度。搜索页没有顶栏，仅底栏生效。浅深色、系统动态配色和所有手动配色共用此设置，保留现有 Material 3 Expressive 组件、配色与色调高度。

界面支持中文和英语，默认跟随系统语言；Android 13+ 可通过系统的应用语言设置切换。Demo 用户名、签名、评论、弹幕、视频标题、简介及演示元数据只维护一份固定内容，不随界面语言切换，集中存放于 `app/src/main/res/values/demo_strings.xml`。“我”页面当前使用占位资料和官方 `SegmentedListItem` 分组菜单，尚未接入真实账号数据。

“设置 → 切换账户”提供 Material 3 Expressive 交互原型：游客固定置顶，两个演示账户使用 RadioButton 表示单选状态。普通账户可向左滑动露出圆角移除按钮，完整滑动同样进入移除确认；游客不可滑动。保留编辑模式和无障碍移除操作，移除当前账户会切回游客。添加账户暂时显示登录待接入提示。选择及移除结果在页面往返、旋转和系统状态恢复时保留，全新启动恢复演示数据，不保存真实登录会话，也不改变“我”的占位资料。

“我 → 设置 → 关于 → 开源组件”使用 **AboutLibraries 15.2.0** 自带的 Material 3 列表和组件详情 Sheet，
继承应用主题，支持离线阅读及返回栈、列表位置恢复。许可证数据按 Debug / Release 构建自动生成，
并补充 Material Symbols 与 AOSP 转场移植声明。维护与检查方法见
[开源声明说明](third_party/aboutlibraries/README.md)。此页采用第三方列表样式，外层导航与顶栏沿用项目组件。

启动屏始终跟随系统浅深色模式：浅色为品牌粉色 `#F06E8E` 背景和白色图标，深色为 `#121212` 背景和品牌粉色图标，配色不随应用内外观设置、动态颜色或自定义主题色变化。应用页面仍使用保存的外观设置，切换应用内浅深色只更新 Compose 主题，不向系统写入应用夜间模式。启动图标使用独立资源，桌面与关于页图标保持原样。

全新启动时，页面准备好后播放 M3 Expressive 图标遮罩退出动画：整个图标使用主题的 `fastSpatialSpec` 收缩至 92%，随后以星星内部为支点，使用 `slowSpatialSpec` 放大。展开与淡出阶段在系统动画时长倍率基础上再乘 1.5，放大倍率采用对数插值，避免前半段就从小图标骤然放大数十倍。图标填色同步使用 `fastEffectsSpec` 淡出，页面从星星与线条轮廓内显露，展开弹簧进度达到 65% 时剩余背景也使用 `fastEffectsSpec` 淡出。缩放在首次到达目标时停止，避免弹簧回缩重新遮挡页面；图标填色消失且遮罩完全揭开后立即结束，不等待不可见的背景淡出尾段。动画时长由主题弹簧规格、展开倍率和系统动画设置共同决定。复用矢量路径直接绘制，页面自身不缩放；Compose 覆盖层绘制首帧后才移除系统启动屏，系统栏在结束后恢复当前页面样式。仅在没有保存状态的新 Activity 收到系统 Splash 退出回调时播放；后台返回、旋转、页面恢复和应用内主题切换均不重播。关闭系统动画时直接显示页面；离开前台、销毁或窗口尺寸变化时清理过渡。过渡期间屏蔽底层页面触摸与无障碍焦点，不拦截系统返回。

## 开发环境

| 配置 | 当前仓库值 |
| --- | --- |
| 应用 ID / namespace | `moe.kirakira` |
| Android 最低版本 | API 32 |
| compileSdk / targetSdk | 37 / 37 |
| Gradle Wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin Compose 插件 | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Material 3 Expressive | 1.5.0-alpha28（显式版本例外） |
| Navigation 3 | 1.1.7（runtime / ui） |
| Gradle Daemon JDK | 25 |
| Java 源码 / 字节码兼容级别 | 11 |

以上记录仓库配置；版本来源为 `gradle/`、`gradle/libs.versions.toml` 和 `app/build.gradle.kts`。使用支持这些版本的 Android Studio，并安装 Android SDK Platform 37。JDK 25 用于运行 Gradle，与 Java 11 的编译兼容设置用途不同。

Material 3 单独使用公开 Expressive 主题与分段列表 API 的版本，其余 Compose 库仍由 BOM 管理；原因和升级要求见贡献指南。

## 快速开始

1. 使用 Android Studio 打开项目根目录，配置 JDK 25 并同步 Gradle。
2. 在 SDK Manager 中安装项目要求的 SDK；由 IDE 生成本机 `local.properties`，不要提交此文件。
3. 创建 API 32 或更高版本的模拟器，或连接启用 USB 调试的设备。
4. 选择 `app` 运行配置并运行。首次同步需要下载 Gradle 与依赖。

也可以在项目根目录执行以下命令；Windows 将 `./gradlew` 替换为 `gradlew.bat`。

| 命令 | 用途 |
| --- | --- |
| `./gradlew :app:assembleDebug` | 默认检查：编译并生成 `app/build/outputs/apk/debug/` 下的 APK，不运行测试 |
| `./gradlew :app:installDebug` | 安装到设备，随后从设备桌面打开应用 |
| `./gradlew verify` | 仅在用户明确要求时：构建 Debug、单元测试、Android Lint |
| `./gradlew :app:testDebugUnitTest` | 仅在用户明确要求时：运行本机 JUnit 4 测试 |
| `./gradlew :app:lintDebug` | 仅在用户明确要求时：运行 Android Lint |
| `./gradlew :app:connectedDebugAndroidTest` | 仅在用户明确要求时：在设备或模拟器上运行仪器测试 |

代码或构建变更默认只执行 `:app:assembleDebug`；除非用户明确要求，不新增、修改或运行测试，不执行 `verify`、额外 Lint 或设备／截图检查。纯文档修改只检查内容和链接，不运行构建。现有测试与命令保留为按需工具；`verify` 不包含设备测试，也不检查全部 Kotlin 格式规则。测试和 Lint 报告位于 `app/build/reports/`。依赖解析失败时，先核对 SDK、JDK、仓库网络和错误日志，不要以任意降级依赖作为默认解决方案。

## 目录结构

```text
app/src/main/
├── java/moe/kirakira/
│   ├── MainActivity.kt       # Activity 宿主
│   ├── KIRAKIRAApp.kt        # 应用入口、应用级状态与导航连接
│   ├── feature/             # 主界面、搜索、个人页、设置页布局
│   │   └── main/MainScreen.kt # 四栏切换、顶栏与底栏
│   └── ui/                  # Expressive 主题、共享组件与导航
│       └── navigation/
│           ├── AppRoutes.kt  # 页面路由定义
│           └── AppNavHost.kt # 页面注册与进入、返回逻辑
├── res/                     # 英文 / 中文字符串、Material Symbols、系统配置
└── AndroidManifest.xml
app/src/test/                 # 本机单元测试
app/src/androidTest/          # Android 与 Compose UI 测试
gradle/                      # Wrapper、JDK 配置和版本目录
```

后续功能继续按 `feature/<name>/` 组织，详细边界见贡献指南。官方图标来源与许可见 [Material Symbols 记录](third_party/material-symbols/README.md)。

## 参与开发

- [CONTRIBUTING.md](CONTRIBUTING.md)：技术选型、架构、Compose、代码风格、构建检查与提交规范。
- [AGENTS.md](AGENTS.md)：开发者与代码代理的执行摘要。
- [.editorconfig](.editorconfig)：编辑器格式配置；在 Android Studio 中启用 EditorConfig 支持。
- [PR 模板](.github/pull_request_template.md)：提交变更时的说明与检查清单。

目前未配置远端 CI、ktlint、Detekt 或覆盖率门槛，默认在 PR 中报告构建结果。已有布局测试覆盖中英文导航和选中状态恢复，仅在用户要求时运行；设备测试需要模拟器或连接的设备。
