# App 内开源声明

AboutLibraries 的 Android Gradle 插件为每个构建变体自动生成
`app/build/generated/aboutLibraries/<variant>/res/raw/aboutlibraries.json`，随 APK 打包。
应用通过 AboutLibraries 的 Material 3 `LibrariesContainer` 和组件详情 Sheet 展示，支持离线阅读。
入口名为“开源组件 / Open source components”；点击组件展示名称、作者、版本、简介与许可证全文，
有对应链接时提供“源码 / 网站 / 查看许可证”等按钮，通过浏览器打开上游页面。
外层页面使用 KIRAKIRATheme 与 Navigation 3；列表保留第三方原生样式，不替换为分段菜单。
系统栏 Insets 作为列表的内容留白，保留延伸到屏幕边缘的滚动区域；底部渐变保护手势条，末项可滚动到导航栏上方。

## 维护方式

- Gradle 依赖及其传递依赖由插件收集；构建工具和测试依赖不应出现在发布版声明中。
  `ui-tooling-preview` 是本项目的 implementation 依赖，因此仍会出现在发布版清单。
- `libraries/` 是手动添加的非 Gradle 组件或依赖元数据补丁。
- `licenses/` 保存手工声明对应的完整正文，通过 `hash` 与 library 的 `licenses` 字段关联。
  为各项手动声明使用独立 hash，以保留各自的来源、修改说明与版权信息。
- [Material Symbols](../material-symbols/README.md) 和 [AOSP 转场](../android-motion/README.md)
  的来源记录及 LICENSE 继续保留在各自目录；更新这些组件时同步本目录的条目与正文。
- [Cerasus 弹幕／颜文字图标及目录](../cerasus-icons/README.md) 以手动条目保留来源与 AGPL-3.0 许可全文。
- 保留上游名称、许可证原文和版权声明，不翻译或缩写法律文本。不要把 README 中的说明视为上游 NOTICE 的替代。
- 新增依赖后检查生成的 JSON：每项必须有正确许可证和非空全文。缺失时依据上游固定版本的
  LICENSE / NOTICE，在本目录补充信息，不按库名称猜测许可证。

图片查看器使用 Telephoto（含 Coil 3 适配）和 Coil，均通过 Gradle 元数据自动收集；构建后检查生成清单中 Telephoto、Coil 及其传递依赖的 Apache-2.0 正文。

生成 JSON 只放在 build 目录，不提交。构建时可能需要联网获取 Maven 元数据和 SPDX 正文；
不需要 GitHub Token，不开启远程许可证或资助信息抓取。App 读取声明不发起网络请求。

## 检查

默认仅按[贡献指南](../../CONTRIBUTING.md#构建与检查)执行 Debug 构建，不运行测试：

```sh
./gradlew :app:assembleDebug
```

以下完整检查仅在用户明确要求时执行：

```sh
./gradlew verify :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

检查 Debug 与 Release APK 中的 `@raw/aboutlibraries` JSON（Release 资源优化可能重命名归档路径），确认都包含全部手动声明、正文非空，
Release 不包含 `ui-tooling`、`ui-tooling-data`、`ui-test-manifest`、JUnit 或 AndroidX Test。
设备测试验证打包数据、列表、详情 Sheet、返回、恢复以及中英文大字体布局。

插件与 UI 版本由版本目录统一管理。升级时检查实际解析的 Compose / Material 3 版本，
避免传递依赖意外改变项目 BOM 或 Expressive 版本，并完成默认 Debug 构建；页面交互测试仅在用户要求时执行。

参考：[AboutLibraries](https://github.com/mikepenz/AboutLibraries)。

API 使用 OkHttp，远程头像使用 Coil 的 OkHttp 适配；两者由 Gradle 依赖与 AboutLibraries 自动收集许可证，不复制依赖源码或手工生成许可证文件。
