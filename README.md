<img src="docs/assets/app-icon.svg" alt="KIRAKIRA Compose 应用图标" width="128" height="128" />

# KIRAKIRA Compose

KIRAKIRA 的 Android 客户端，使用 **Kotlin + Jetpack Compose** 构建。界面基于 **Material 3 Expressive**，搭配 Material Design 2 风格的经典强调色、高度阴影与 KIRAKIRA 特色彩色阴影，界面图标优先采用 **Cerasus** 的同用途资源。

## 主要功能

- **浏览与搜索**：首页视频、关键词与多标签搜索、搜索历史、云端观看历史
- **视频播放**：基于 Media3 的分 P 播放、全屏、应用内小窗与系统画中画，评论与弹幕的浏览和发布
- **社区互动**：关注、赞踩、作者主页、关注／粉丝列表
- **账号**：接入 Rosales 账号 API，支持登录、邀请码注册、找回密码、邮箱／TOTP 二步验证、本机多账号切换与系统密码管理器
- **个人设置**：资料与头像编辑、隐私、账号安全、屏蔽规则、邀请码管理
- **外观**：明暗模式、预设／系统／自定义强调色、毛玻璃应用栏，中英文界面

“关注”Tab 与收藏等入口尚未接入业务。各页面的具体行为、保存范围与已知限制见[功能现状](docs/features.md)。

## 快速开始

运行需要 **Android 8.1（API 27）** 及以上。开发需要兼容当前工具链的 Android Studio、**JDK 25** 和 **compileSdk 37（minor API 2）** 的 SDK Platform，详见[开发环境](docs/development.md#开发环境)。

1. 用 Android Studio 打开项目根目录，配置 Gradle JDK 并同步。
2. 连接设备或启动模拟器，选择 `app` 运行配置运行。

命令行构建 Debug APK（输出到 `app/build/outputs/apk/debug/`，Windows 使用 `gradlew.bat`）：

```sh
./gradlew :app:assembleDebug
```

API 默认连接 `https://rosales.kirakira.moe/`，可在构建时覆盖，见 [API 环境配置](docs/development.md#api-环境配置)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [功能现状](docs/features.md) | 页面行为、状态保存与限制 |
| [开发指南](docs/development.md) | 环境、命令、目录结构与可选检查 |
| [实现索引](docs/implementation.md) | 导航、内容、媒体、主题、组件、认证与账号设置的实现专题 |
| [贡献指南](CONTRIBUTING.md) | 技术选型、代码与 UI 规范、检查与提交要求 |
| [AGENTS.md](AGENTS.md) | 代码代理执行摘要 |
| [认证离线测试](docs/auth-testing.md) | 认证测试隔离、专项命令与历史验证记录 |

参与开发前请阅读贡献指南，PR 使用[仓库模板](.github/pull_request_template.md)。

## 第三方资源

[Material Symbols](third_party/material-symbols/README.md) · [AOSP 页面转场](third_party/android-motion/README.md) · [Android 下拉刷新](third_party/android-refresh/README.md) · [Cerasus 颜色资源](third_party/cerasus-palettes/README.md) · [Cerasus 图标与动画](third_party/cerasus-icons/README.md) · [GitHub Logo](third_party/github/README.md) · [开源组件声明](third_party/aboutlibraries/README.md)
