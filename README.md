# KIRAKIRA Compose

KIRAKIRA 的 Android 客户端，使用 **Kotlin + Jetpack Compose**，设计语言为 **Material 3 Expressive**，图标采用官方 **Material Symbols Rounded**。

当前已接入 Rosales 账号 API，支持登录、注册、找回密码及本机多账号会话。首页视频、作者资料、评论与弹幕接入真实数据，支持 Media3 播放和系统画中画。

## 当前功能

| 功能 | 当前状态 |
| --- | --- |
| 主界面 | 首页、搜索、关注、我四个 Tab，支持切换动效和页面状态恢复；搜索与关注仍为占位内容 |
| 视频页 | 真实视频详情与分 P 播放、全屏和画中画，支持关注、赞踩、评论分页和评论／弹幕发布 |
| 外观 | 明暗模式、系统动态配色、预设与自定义颜色、十种手动配色算法和阴影开关，设置持久保存 |
| 账户与认证 | 密码登录、邮箱／TOTP 二步验证、四步邀请码注册、系统密码选择与保存、找回密码、加密会话、游客／多账号切换及登出 |
| 用户主页与图片 | “我”展示真实账号资料；本人和真实作者的头像可放大、保存或复制，作者作品来自 API |
| 屏蔽与邀请 | 五类屏蔽／隐藏规则管理，邀请码统计、生成、筛选与复制，按账号接入真实 API |
| 关于与开源组件 | AboutLibraries 组件列表、详情与离线许可证阅读 |
| 语言与启动体验 | 中英文界面、跟随系统浅深色的品牌启动屏，以及遵循系统动画设置的启动过渡 |

界面默认跟随系统语言，Android 13+ 可使用系统应用语言设置；用户发布内容保留原文。系统动态配色需要 Android 12+，低版本使用手动配色。

外观、播放设置与加密账号会话会持久保存；视频页退出后重新进入恢复默认状态。自己与作者的资料、视频均来自 API；资料编辑、历史、收藏等入口及部分设置仍未接入业务。具体交互、保存范围和已知限制见[功能现状](docs/features.md)。

## 快速开始

运行设备最低要求为 **Android API 27**。开发需要兼容当前工具链的 Android Studio、**JDK 25**，以及匹配 **compileSdk 37（minor API 2）** 的 Android SDK Platform。完整版本表及配置来源见[开发指南](docs/development.md#开发环境)。

1. 使用 Android Studio 打开项目根目录，配置 Gradle JDK，并安装所需 SDK。
2. 同步 Gradle；首次同步需要下载 Gradle 与依赖，由 IDE 生成本机 `local.properties`，不要提交该文件。
3. 连接启用 USB 调试的设备，或准备满足最低版本要求的模拟器，选择 `app` 运行配置运行。

也可从项目根目录构建 Debug APK：

```sh
./gradlew :app:assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/`。需要安装到设备时执行 `./gradlew :app:installDebug`，随后从设备桌面打开应用。Windows 使用 `gradlew.bat` 替换 `./gradlew`。

API 默认连接 `https://rosales.kirakira.moe/`，构建时可覆盖 HTTPS 地址，见[API 环境配置](docs/development.md#api-环境配置)。

代码或构建变更默认只做 Debug 构建；纯文档修改只检查内容与链接。测试、Lint 和设备检查仅按用户明确要求执行，见[构建与检查](CONTRIBUTING.md#构建与检查)。

## 文档导航

| 文档 | 阅读用途 |
| --- | --- |
| [功能现状](docs/features.md) | 各页面能做什么、状态保存与限制 |
| [开发指南](docs/development.md) | 环境配置、运行安装、目录结构与可选检查命令 |
| [实现说明](docs/implementation.md) | 导航、主题、认证、共享顶栏接入与动效实现 |
| [贡献指南](CONTRIBUTING.md) | 技术选型、代码与 UI 规范、依赖、检查及提交要求 |
| [代理执行摘要](AGENTS.md) | 开发者与代码代理的执行约定 |

参与开发前阅读贡献指南，启用 [.editorconfig](.editorconfig) 支持。使用 [PR 模板](.github/pull_request_template.md)记录变更原因与检查结果。

第三方来源与维护记录：[Material Symbols](third_party/material-symbols/README.md)、[AOSP 页面转场](third_party/android-motion/README.md)、[开源组件声明](third_party/aboutlibraries/README.md)。
