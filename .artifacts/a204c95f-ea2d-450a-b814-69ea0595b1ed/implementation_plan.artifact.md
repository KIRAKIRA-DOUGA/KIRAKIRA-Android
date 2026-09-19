# 迁移至 Navigation Compose 以实现原生跳转动画

目前的 `SettingsScreen` 是通过简单的条件判断显示的，这导致了瞬间切换而没有动画。我们将迁移至官方推荐的 `Navigation Compose` (Jetpack Navigation) 方案，利用 `NavHost` 自动处理页面切换动画（默认即为原生的左右滑入效果）。

## Proposed Changes

### 依赖配置

#### [MODIFY] [libs.versions.toml](file:///Users/aira/AndroidStudioProjects/KIRAKIRA-Compose/gradle/libs.versions.toml)
- 添加 `androidx-navigation-compose` 依赖定义。
- 添加 `kotlinx-serialization-json` 用于类型安全导航。

#### [MODIFY] [build.gradle.kts](file:///Users/aira/AndroidStudioProjects/KIRAKIRA-Compose/app/build.gradle.kts)
- 在 `dependencies` 中引入 `androidx-navigation-compose`。
- 应用 `kotlinx-serialization` 插件以支持类型安全导航。

### 核心逻辑重构

#### [MODIFY] [KIRAKIRAApp.kt](file:///Users/aira/AndroidStudioProjects/KIRAKIRA-Compose/app/src/main/java/moe/kirakira/KIRAKIRAApp.kt)
- 定义导航路由类（使用 `kotlinx.serialization.Serializable`）。
- 使用 `rememberNavController()` 初始化导航控制器。
- 将 `Scaffold` 内容和 `SettingsScreen` 切换逻辑迁移到 `NavHost`。
- 配置 `NavHost` 的 `enterTransition` 和 `exitTransition` 以获得一致的系统级动画体验。

### 页面适配

#### [MODIFY] [MeScreen.kt](file:///Users/aira/AndroidStudioProjects/KIRAKIRA-Compose/app/src/main/java/moe/kirakira/feature/me/MeScreen.kt)
- 保持 `onOpenSettings` 回调，供导航逻辑调用。

#### [MODIFY] [SettingsScreen.kt](file:///Users/aira/AndroidStudioProjects/KIRAKIRA-Compose/app/src/main/java/moe/kirakira/feature/settings/SettingsScreen.kt)
- 保持 `onBack` 回调，供导航逻辑调用。

## Verification Plan

### Automated Tests
- 运行 `./gradlew verify` 确保构建正常。
- 运行 `app:connectedDebugAndroidTest` 验证导航路径（如果存在相关测试）。

### Manual Verification
1. 启动应用，进入“我” (Me) 页面。
2. 点击“设置”，观察页面是否平滑地从右侧滑入。
3. 点击返回按钮或使用手势返回，观察页面是否平滑滑出。
