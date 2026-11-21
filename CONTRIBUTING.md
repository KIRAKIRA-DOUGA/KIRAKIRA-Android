# 贡献指南

本规范适用于人工开发与代码代理。“必须／不得”是提交要求，“建议／优先”允许结合需求判断；例外在 PR 中写明原因、影响和验证方式。既有代码不作为新代码的风格标准，也不要求每次提交清理整个项目。

- [技术选型与架构](#技术选型与架构)
- [Kotlin 风格与命名](#kotlin-风格与命名)
- [界面规范](docs/ui-guidelines.md)（独立文档）
- [认证状态约束](#认证状态约束)
- [API 与会话接入规范](#api-与会话接入规范)
- [依赖与配置](#依赖与配置)
- [构建与检查](#构建与检查)
- [文档维护](#文档维护)
- [Git 与 Pull Request](#git-与-pull-request)

## 技术选型与架构

### 技术选型

- 应用代码使用 Kotlin，Gradle 使用 Kotlin DSL。界面统一使用 Jetpack Compose，以 Material 3 Expressive 为基础，复用 `KIRAKIRATheme`；视觉规则见[界面规范](docs/ui-guidelines.md)。
- 不新增以 XML Layout、Fragment、ViewBinding 或 DataBinding 为核心的页面。只有平台能力或第三方 SDK 需要时才用 `AndroidView` 等互操作，并封装它的生命周期与资源释放。Manifest、图标、字符串和系统主题的 XML 照常使用。
- 保持单 Activity 入口。`MainActivity` 只负责宿主和顶层组合，不承载网络、存储或业务规则。
- 依赖通过构造参数显式传递，暂不引入 DI 框架。

### 包结构

保留单个 `:app` 模块，Kotlin 源码位于各源集的 `kotlin/` 目录。完整的目录说明见[开发指南](docs/development.md#目录结构)。

| 包 | 放什么 |
| --- | --- |
| `moe.kirakira`（根包） | 只放 `MainActivity`、`KIRAKIRAApp` 和宿主依赖 |
| `navigation` | 应用级导航图：路由、页面注册、`AppNavHost` |
| `feature.<name>` | 功能页面及其 ViewModel；页面多时按子功能分包，如 `feature/settings/appearance` |
| `ui/components/<组件族>` | 跨功能复用的 UI，按组件族归类，不在根目录直接放文件 |
| `ui/theme`、`ui/adaptive`、`ui/navigation` | 主题与共享投影；窗口与分栏布局；页面和嵌套流程复用的导航宿主 |
| `data.<domain>` | 数据访问与领域模型；认证与会话在 `data/auth` |
| `core.<name>` | 真正跨功能的基础设施，如 `core/network` |

- **依赖方向**是 `navigation` → `feature` → `ui`／`data` → `core`。`core` 不依赖其他层；`data` 不依赖 `feature` 和 `ui`；`ui` 不依赖 `feature`；`feature` 和 `ui` 不引用 `moe.kirakira.navigation`，页面所需的状态和回调由导航图在注册时传入。
- 只服务单个页面的界面片段放在那个功能包里。
- 一个文件以一个主要声明命名。不新增 `*ViewModels.kt` 这类按类型堆放多个类的文件，也不把无关代码堆进 `Utils.kt`。包名全小写。
- 按需增加层次：不为了符合目录示意而添加空实现；复杂且可复用的业务规则才抽取 use case。

### 状态与数据流

- 页面展示状态并上报事件。涉及业务逻辑的屏幕由 ViewModel 管理状态，通过 Repository 隔离数据访问。
- 主题、会话与认证状态使用 ViewModel。

### 导航

- 使用 Navigation 3。路由实现 `NavKey` 并标注 `@Serializable`，定义在 `navigation/AppRoutes.kt`；页面在同包的 `*Entries.kt` 里按功能注册，并使用 `NavigationPage` 接入宿主。
- 返回栈和状态恢复由 `ui/navigation/ActivityNavDisplay.kt`（封装 `NavDisplay`）管理。
- 普通转场复用 `NavigationMotion.kt`。预测性返回由宿主统一管理：页面不重复配置，也不叠加 `NavDisplay` 默认的预测性返回补间。
- 导航由页面的事件回调触发，保留根页面，连续点击不能重复入栈或出栈。
- 底栏是主界面内的局部状态，不预建多返回栈。
- **移动或重命名路由类会让改动前保存的返回栈无法还原**，原因见[返回栈的保存与路由类名](docs/implementation/navigation.md#返回栈的保存与路由类名)。

接入关系见[导航与状态管理](docs/implementation/navigation.md#导航与状态管理)，转场参数的来源见 [Android 转场说明](third_party/android-motion/README.md)。

## Kotlin 风格与命名

以仓库的 `.editorconfig` 和 [Kotlin 官方风格](https://kotlinlang.org/docs/coding-conventions.html)为准。提交前在 Android Studio 对修改过的代码执行 Reformat Code 和 Optimize Imports。

| 对象 | 约定与示例 |
| --- | --- |
| 类、接口、文件 | `PascalCase`，例如 `HomeScreen.kt`、`HomeViewModel.kt` |
| 返回 `Unit` 的 UI composable | `PascalCase`，例如 `FavoriteButton` |
| 普通函数、属性、有返回值的 composable | `camelCase`，例如 `loadFavorites`、`rememberHomeState` |
| 常量、枚举项 | `UPPER_SNAKE_CASE`，例如 `PAGE_SIZE`、`HOME` |
| Android 资源 | `snake_case`，例如 `home_title`、`ic_favorite` |
| 测试 | `<Subject>Test`；方法名用 `action_condition_expectedResult` |

- Kotlin 和 XML 使用四空格、UTF-8、LF，文件末尾换行；建议行宽不超过 120。Markdown、YAML、JSON 和 TOML 使用两空格缩进；Windows 的 `.bat` 保留 CRLF。
- 不使用通配符导入，删除无用的导入，多行参数使用尾随逗号。
- 优先 `val`、不可变数据和明确的空值处理，不用 `!!` 掩盖状态或接口问题。
- 公共 API 的约束、复杂逻辑及其原因用 KDoc 或注释说明，不复述代码。标识符使用英文，文档和解释性注释可以用中文。

## 认证状态约束

实现见[认证与会话](docs/implementation/auth.md)。

- **ViewModel 的归属**。认证 ViewModel 必须绑定 Navigation 3 的导航条目：旋转时保留，出栈后释放。不使用 Activity 级的认证 ViewModel。
- **什么可以保存**。只有邮箱可以写入 `SavedStateHandle`。密码、密码摘要、验证码、备用码和恢复码不得写入可保存状态、路由、应用磁盘或日志。进程重建后密码为空，认证流程回到登录入口；界面重建和切换步骤后密码默认隐藏。
- **系统密码保存是唯一的例外边界**。只有服务端认证和本机会话保存都完成之后（密码重置则是服务端确认和本机清理都完成之后），才能通过 `CreatePasswordRequest` 请求用户确认，把原密码交给用户选择的系统密码管理器。应用自己不落盘；取消或失败不撤销认证。待保存的密码只留在条目的流程内存里，完成、取消或退出时释放。
- **凭据网关**。系统凭据的获取、保存和清理通过可替换的网关。弹窗必须由当前 Activity 承载；ViewModel 用请求编号和宿主身份处理一次性结果，拒绝来自旋转、退出或旧流程的回调。
- **登录页不主动请求密码选择器**。已保存的密码通过输入框的系统自动填充入口选择，避免与 Autofill 重复弹窗。Compose 保留邮箱／用户名和密码的 Autofill 语义；切换、取消流程和调用凭据保存之前，先取消旧的 Autofill 会话。不使用 View 专用的凭据联动接口。
- **非生产构建不碰真实凭据**。非生产根地址的构建禁用真实的凭据网关，离线测试使用替身，不得读取真实密码管理器里的凭据。
- **登出时**（包括切换到游客和移除当前账号）只尽力清理提供者的会话，不删除它保存的密码。
- 业务接入放在 ViewModel 的统一提交入口，通过 Repository 隔离 API。

## API 与会话接入规范

### 契约与分层

- 接口契约以 Rosales 的路由、Controller DTO 和 Service 实现为依据；Cerasus 用来对照交互、参数和邮件模板。不得根据 DTO 的注释臆造散列方式、Bearer 鉴权、刷新令牌或登出撤销能力。已对照的端点见[接口契约](docs/implementation/auth.md#接口契约)；接口改动时同步契约表和失败语义。
- 分层是单向的：UI → ViewModel → Repository → API／Store。服务端 DTO 只在数据层解码并转成领域模型；UI 不接触 token、Cookie 或密码摘要。
- 应用只持有一份会话 Repository；认证表单按导航条目隔离。

### 网络

- API 地址只能来自 `BuildConfig.API_BASE_URL`，必须是 HTTPS；构建配置里不得含凭据、查询串或片段。
- 禁止放宽证书校验、自动降级到 HTTP、跟随鉴权请求的重定向，禁止把会话发往图片或其他域名。切换环境时不得复用另一个后端的会话。
- 使用共享的 OkHttp API 客户端和 kotlinx.serialization。设置连接、读写和整个调用的超时，限制响应大小，查询值通过 URL builder 编码。
- 网络、散列和磁盘操作不在主线程执行；请求的取消必须传递到 HTTP Call。

### 结果与错误

- **HTTP 成功不等于业务成功**。检查 `success`、必要字段和账号身份的一致性。未知字段允许向前兼容，但缺少关键字段不能产生“已登录”状态。
- 不原样展示服务端的错误；不在日志里输出响应、邮箱、密码、摘要、验证码、Cookie 或 token。
- 统一区分网络、超时、服务不可用、响应异常、业务拒绝、限流、会话失效和本地存储错误，并映射到本地化的提示。
- 网络失败不删除账号。当前 Rosales 的 `success=false` 也可能表示数据库故障，不能一律当作令牌已撤销：校验被拒绝时保留本机凭据供重试，同时撤下受影响的当前身份；**只有明确的 HTTP 401 才清除对应的 token**。
- **认证写请求不自动重放**。登录、注册、发送验证码和找回密码都不自动重试；按钮禁用重复提交，重试由用户触发。验证码冷却按邮箱统一管理，尊重服务端的冷却和每日上限，客户端的倒计时不代替服务端的校验。

### 会话存储

- 会话以服务端的 UUID 去重。token 和最小账号资料以 JSON 原子写入应用私有目录的 `noBackupFilesDir/sessions.json`；正常读写不使用 Keystore 或额外加密，不进入备份、SavedState、预览或普通偏好设置。
- 读取时校验结构、身份和后端地址，并限制文件大小。已提交的新库读取失败或损坏时，保留文件并禁止覆盖，不回退到旧库。
- 写入成功之后才能发布新的当前账号；提交后的核验失败不得再回滚已提交的文件。
- 只有用户明确确认后才能清除本机账号文件；清除后保存空的新库，不访问 Keystore。
- 从旧加密库迁移的条件和失败处理见[从旧加密库迁移](docs/implementation/auth.md#从旧加密库迁移)。修改这部分逻辑时不得放宽其中的条件。
- 保护依靠应用沙盒和系统的存储保护，不宣称应用层加密或密码学完整性。系统备份只允许已明确列出的非敏感偏好。

### 账号隔离

- 切换、移除和会话保存串行执行。请求绑定发起时的账号，结果只能更新所属的账号。
- 之后增加账号私有的缓存时，必须按 UUID 隔离，并在切换或移除时清理对应的页面状态。
- 游客不携带认证信息。切换到游客保留其他会话；登出和移除只清除指定的本机会话，不谎称已撤销服务器的 token。禁止添加并不存在的 refresh-token 流程；失效的账号通过认证页重新登录。

### 测试

用户明确要求认证测试时，优先使用内存响应、内存账号库、虚构身份和可控时钟。测试不得请求生产后端或读取真实凭据。设备测试运行在普通的 debug 包里，不得用它操作真实账号；需要在设备上验证账号存储或认证界面时，先建立独立包名、无网络权限的隔离变体。测试替身只放在测试源码里，不为它增加绕过 TLS 或改用假会话的产品入口。隔离方式与命令见[认证离线测试](docs/auth-testing.md)。

## 依赖与配置

- 依赖和插件的版本集中在 [gradle/libs.versions.toml](gradle/libs.versions.toml)，通过 `libs.*` 引用；文档不重复记录版本号。
- Compose 库优先由现有的 BOM 管理。禁止动态版本（如 `1.+`）和没有需求的工具链升级；不为了接入单项功能更换现有的 BOM 或 Material 3 版本。
- 新增依赖时说明用途、维护状况和对体积的影响，先确认已有能力不够用。APK 的实际增量要在相同的构建配置下比较，不能用依赖包的大小代替。
- 实验性 API 的 opt-in 限于实际的调用点。不用编译器抑制绕过内部 API 的可见性。
- 不得提交 `local.properties`、访问令牌、签名密钥或带个人信息的日志。内置在客户端里的值不能视为秘密；服务端凭据不得写进客户端代码。权限按功能的最小需要声明。

几个需要知道的例外：

| 依赖 | 说明 |
| --- | --- |
| Material 3 | 显式固定版本，不跟随 BOM，因为要使用公开的 Expressive 主题和分段列表 API |
| `androidx.compose.material:material`（Material 2） | 只用来复用官方的圆形加载器，页面和主题不迁移到 Material 2 |
| Navigation 3 | 显式引入 `navigation3-runtime` 和 `navigation3-ui`，不由 Compose BOM 管理；不使用 Navigation 2 的 `navigation-compose` |
| Material 3 Adaptive | 由 BOM 管理，只接入核心库，用来读取窗口与折叠姿态 |
| Lifecycle | `lifecycle-viewmodel-compose`、`lifecycle-viewmodel-navigation3` 和 `lifecycle-runtime-compose` 保持同一版本 |

升级 Material 3 或把实验性 API 转为稳定版时，检查主题、导航、搜索栏和各语言布局相关 API 的兼容性。开发环境与工具链的配置来源见[开发环境](docs/development.md#开发环境)。

## 构建与检查

- **默认只构建**。代码或构建变更默认只执行 `./gradlew :app:assembleDebug`，并修复本次改动引起的编译、资源处理和打包错误。构建成功只表示编译和打包通过，不代表运行时行为已经测试。
- **测试只在用户明确要求时做**。除非用户明确要求，不新增、修改或运行单元测试、回归测试、UI／设备测试和截图测试；业务逻辑变化、缺陷修复和交互变化同样如此，不以测试覆盖率或补充测试作为交付条件。
- **其他检查同理**。默认不执行 `verify` 和额外的 Android Lint，不主动启动模拟器做人工测试或采集截图、录屏。规范里关于布局、颜色、导航和无障碍的要求是实现约束，不自动构成开展测试的要求。
- 保留现有的测试、依赖和检查任务。`verify` 聚合 Debug 构建、单元测试和 Android Lint。
- **纯文档修改**只检查路径、链接、命令和表述的一致性，不运行构建或测试。
- **如实报告**。交付时记录实际执行的检查，以及失败或受阻的原因；不得把没有运行的检查报告为通过。按默认约定没有运行测试，不需要当作规范例外处理。

用户要求测试时，测试放在这些位置（源集的说明见[开发指南](docs/development.md#目录结构)）：

| 测试 | 位置 |
| --- | --- |
| JVM 测试（JUnit 4） | `app/src/test/kotlin/` |
| 测试替身（内存 API、内存账号库、凭据替身） | `app/src/test/kotlin/` 的 `moe.kirakira.testing` |
| 设备测试（AndroidJUnit4／Compose UI Test） | `app/src/androidTest/kotlin/` |

- 包结构与被测源码一致；被测类移动时，测试跟着移动。
- 只覆盖用户要求的行为，避免固定等待和对真实网络的依赖。

## 文档维护

### 分工

同一项详细信息只在一处维护，其他位置用一句概述加链接引用。

| 内容 | 维护位置 |
| --- | --- |
| 项目定位、主要能力、最低运行要求、最短的启动步骤 | [README](README.md) |
| 用户可见的页面行为、保存范围与限制 | [功能现状](docs/features.md) |
| 必须遵守的界面规则 | [界面规范](docs/ui-guidelines.md) |
| 状态归属与数据流、取舍理由、平台限制、接口契约、持久化格式 | [实现说明](docs/implementation.md)下的对应专题 |
| 技术选型、架构边界、代码风格、认证与 API 约束、检查和提交政策 | 本指南 |
| 开发环境、命令与目录 | [开发指南](docs/development.md) |
| 发版流程与签名配置 | [发版](docs/release.md) |
| 测试隔离、专项命令与带日期的历史验证结果 | [认证离线测试](docs/auth-testing.md) |
| 第三方来源、许可与移植差异 | [third_party](third_party) 下的对应文档 |
| 依赖与插件的版本号 | 只在[版本目录](gradle/libs.versions.toml)，文档不重复 |

[AGENTS](AGENTS.md) 是给代码代理的执行摘要，[PR 模板](.github/pull_request_template.md)是自查清单。修改本指南或界面规范时，同步这两处的相关摘要；它们不复制组件参数。

### 写法

- **只写代码读不出来的东西**。实现说明记录“为什么”和“不这样做会怎样”，不复述尺寸、颜色、参数名和调用点清单——那些以代码为准。
- **描述当前状态**。不写“不再”“改为”“仍沿用原有”这类改动记录的口吻；改动的历史属于提交信息。
- **改写而不是追加**。功能变化时修改所属的段落，不在文末追加一段补充说明。新内容放进所属的章节；没有合适的章节就新建，而不是挂在不相关的标题下面。
- **保留证据**。平台缺陷的规避方式要写明复现条件和依据；尚未验证的行为要明说没有验证。
- 段落保持简短，并列的要点用列表，适合对照的内容用表格。
- 移动或重命名章节时，同步指向它的链接和锚点。
- README 不追加逐次的变更记录。带日期的历史验证结果不代表当前版本已经验证。

## Git 与 Pull Request

- 分支建议使用 `feat/<topic>`、`fix/<topic>`、`docs/<topic>` 或 `chore/<topic>`。
- 提交格式为 `type(scope): summary`，scope 可以省略。type 使用 `feat`、`fix`、`refactor`、`docs`、`test`、`style`、`build`、`ci` 或 `chore`，例如 `feat(home): add empty state`、`docs: 完善开发环境说明`。summary 简洁地说明动作，不写“更新代码”这类泛泛的描述。
- 一次提交聚焦一个目的，不混入无关的重排、依赖升级或生成目录。破坏兼容性的变更在正文里解释影响和迁移方式。
- 使用 [PR 模板](.github/pull_request_template.md)，说明问题、方案、关联的 issue（如有）、构建结果和风险。测试结果、界面截图或录屏只在用户要求时提供；规范例外写明原因。
- 合并前由其他开发者审核适用的规范和验证证据。推送到 `main` 或正式版本的 tag 会触发 [GitHub Actions 发版](docs/release.md)。PR 的合并规则由维护者在远端设置。
