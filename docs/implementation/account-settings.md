# 账号设置

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录设置页的双栏布局与搜索、本人资料编辑、账号安全与二步验证、隐私设置、屏蔽规则和邀请码的实现。贯穿这些页面的一条规则是：**写请求的结果不确定时，先暂停编辑并回读核对，不重发写请求**；各节说明它在具体流程里的做法。表单的界面规则见[账号表单与保存](../ui-guidelines.md#账号表单与保存)。

- [自适应设置布局](#自适应设置布局)
- [设置搜索](#设置搜索)
- [本人资料编辑](#本人资料编辑)
- [设置表单操作区](#设置表单操作区)
- [安全与二步验证](#安全与二步验证)
- [隐私设置](#隐私设置)
- [屏蔽规则与邀请码](#屏蔽规则与邀请码)

## 自适应设置布局

设置使用 `ActivityNavDisplay` 的 Navigation 3 Scene。连续安全区足够宽（840 × 480 dp 起）时显示“目录加详情”双栏；被纵向铰链分隔的窗口在两侧都够用时也使用双栏，目录位于阅读顺序的起始侧。阈值在 [SettingsDestinations.kt](../../app/src/main/kotlin/moe/kirakira/navigation/SettingsDestinations.kt) 的 `settingsPaneLayout` 中，判断使用系统栏与刘海的稳定 Insets，不含键盘。

双栏沿用 [Navigation 3 List–Detail Scene](https://developer.android.com/guide/navigation/navigation-3/recipes/scenes-listdetail) 的结构：

- 以目录条目的 `contentKey` 标识同一个双栏 Scene。选中的分类由“目录之后的第一个条目”派生，切换分类时原子替换详情路径。
- 目录从设置搜索的索引取一级条目并分组，一级条目的元数据记录目录顺序。详情切换时，跨分类按目录顺序纵移，同一分类内进退则横移。
- 目录、阴影和应用侧栏在详情转场期间保持原位；预测性返回的预览与收尾只在详情栏内进行。
- 相接的两栏共用目录表面的阴影（包括宽度为零的折痕）；遇到有宽度的铰链时，两栏各止于自己的物理边界。
- 登录走全页路由。头像裁剪和安全子流程在保留条目 ViewModel 的前提下展开到完整的设置视口。
- 离开详情时复用页面的返回保护。资料页的“放弃修改”确认把待继续的操作存在内存里，取消时清除。

## 设置搜索

[SettingsSearchLayout](../../app/src/main/kotlin/moe/kirakira/feature/settings/search/SettingsSearchBar.kt) 让官方 `SearchBar` 与展开的 `PageSearchOverlay` 共用搜索状态。单栏用 Dialog 展开，双栏直接在目录内展开；覆盖层可见期间，底层目录暂停触摸、键盘焦点和无障碍交互。查询与展开状态可恢复；导航离开时移除搜索弹层但保留搜索状态。

### 索引

[SettingsSearchCatalog.kt](../../app/src/main/kotlin/moe/kirakira/feature/settings/search/SettingsSearchCatalog.kt) 只登记静态资源、分类路径、页面、稳定 ID 和可见条件，不读取账号字段或远端列表。**新增可搜索的设置时，要同步登记目录条目和页面里的锚点。**

- 空查询或只有空白时返回空列表，界面保持空白。
- 匹配使用当前界面语言，忽略大小写；以空白分隔的每个词都要在名称或路径中命中。
- 排序依次为完整标题匹配、标题包含、路径匹配，同级保留目录顺序。
- 系统强调色条目受系统版本限制，账号设置条目受登录状态限制。

无匹配时显示 `SettingsSearchLogo`，持续无结果时不随每次输入重播入场动画；来源见 [Cerasus 图标说明](../../third_party/cerasus-icons/README.md#设置搜索空结果-logo)。

### 定位到结果

`SettingsSearchTargetRoute` 只保存目录项的 ID，与普通路由复用同一套页面装配。[SettingsSearchTarget.kt](../../app/src/main/kotlin/moe/kirakira/feature/settings/search/SettingsSearchTarget.kt) 把定位目标传给页面，页面上的 `settingsSearchAnchor` 通过 `BringIntoViewRequester` 请求可见。

- 懒列表（外观、屏蔽）要先滚动到包含目标的条目，锚点才存在。
- 目标完成布局且页面恢复到 RESUMED 后才定位；加载失败时等目标实际出现。
- 定位的完成状态可恢复，避免返回后重复定位。

## 本人资料编辑

`ProfileEditorRoute` 使用条目级的 [ProfileEditorViewModel](../../app/src/main/kotlin/moe/kirakira/feature/settings/profile/ProfileEditorViewModel.kt)，资料通过 [ProfileRepository](../../app/src/main/kotlin/moe/kirakira/data/profile/ProfileRepository.kt) 访问。个人主页的“编辑资料”按钮按当前登录用户的 UID 显示，点击时再次核对身份；入口经 `openFrom` 打开，不会重复入栈。

### 字段规则

- 用户名与昵称必填，与注册共用 `AccountNameRules` 的字符白名单，最长 20 个 UTF-16 单元；简介最长 200。客户端白名单与 Rosales 现有的校验范围并不完全一致，最终以后端结果为准。
- 保存时统一做 NFC 规范化，用户名执行 trim。重名由资料更新接口判断（服务端会排除本人），不调用注册用的公开查重接口。
- 标签保留顺序和已有 ID，新标签使用最小可用的非负 ID。生日未设置时发送空字符串，不默认写入当天。
- 更新请求始终带上用户名和已有的背景图；不属于编辑表单的字段不主动发送。
- 服务端仍允许昵称缺失，所以回读、旧会话存储和名称展示都保留兼容；旧账号下次保存资料时必须补填昵称。

### 保存流程与检查点

保存依次执行四步：上传头像、更新资料、回读本人资料、提交本机会话。Repository 为单个编辑流程持有检查点：

| 情况 | 处理 |
| --- | --- |
| 头像已上传过 | 同一份头像草稿不重复上传 |
| 更新成功、回读失败 | 锁定编辑，只重试回读 |
| 回读成功、写盘失败 | 保留已读结果，只重试提交 |
| 写入结果不确定 | 暂停编辑，但不宣称服务端已保存。之后的保存按钮只回读，并比较提交过的全部可编辑字段：一致才同步本机会话，不一致则继续保持待核对，不再提交整份旧草稿 |

- 用户可以确认离开核对状态；提示里说明离开不会撤销可能已经生效的修改。
- 提交本机会话时保留 token 与 bootstrap hint，只替换当前账号的资料。发布会话修订后，“我”和本人主页自行刷新。
- 外部的账号或修订变化会取消请求并清除草稿；只有编辑页自己提交产生的修订才被它接纳。
- 保存任务被取消后，它的错误处理与收尾不再更新页面状态，否则旧任务会清掉新账号任务的忙碌状态。

### 头像的选择与裁剪

- 图片用 `PickVisualMedia` 选择，回调绑定选择时的账号修订；原图复制上限 32 MiB。
- 裁剪使用 CanHub Android Image Cropper（裁剪、EXIF 与采样），通过 `AndroidView` 封装 `CropImageView`；这是页面里唯一的第三方 View。固定 1:1 裁剪，输出最大 1024px 的 JPEG。Manifest 移除了库自带的导出 Activity 和未使用的文件提供者。
- **输出必须是 `content://` URI，且扩展名要与压缩格式匹配**，这是裁剪库的要求。应用复用 `${applicationId}.images` FileProvider，把缓存中的 `profile-editor/` 目录映射为输出 URI 并保留 `.jpg` 后缀；不能传 `Uri.fromFile(output)`。
- View 重建时从流程内存恢复裁剪范围；释放时清理图片和监听。仍在运行的异步裁剪只保留收尾回调，旧流程的结果会被拒绝。

### 临时文件

裁剪缓存按 `cacheDir/profile-editor/<进程随机标识>/<流程随机标识>/` 隔离，不写入 SavedState 或会话存储。

- 草稿文件在替换、退出和账号变化时异步删除；删除会等资料保存、图片准备和库内异步裁剪的完成信号。
- 进入编辑页时在 IO 线程扫描并清理**旧进程**的目录。当前进程的整个目录都受保护，否则多个编辑页、页面重建或仍在收尾的任务可能被误删。
- 清理是尽力而为的：删不掉的旧缓存下次进入编辑页时再试。

### 上传

预签名上传使用独立的、不带 Cookie 和认证头的 OkHttp 客户端，只允许 `https://upload.imagedelivery.net:443`，发送 multipart `file`，禁用重定向和自动重试，并检查 HTTP 状态与 JSON 的 `success`。成功后把图片 ID 交给资料更新接口；预签名 URL 不保存也不记录。

### 返回保护

设置条目通过 `RegisterNavigationBackGuard` 注册（机制见[返回保护](navigation.md#返回保护)）。资料有草稿或正在裁剪时，不启动页面的预测性返回转场；手势**完成**时才显示确认、关闭裁剪或忽略保存中的返回，取消手势没有任何副作用。顶栏的返回使用同一个判断。

## 设置表单操作区

- **资料编辑**通过 `SettingsScaffold.actions` 在顶栏右侧放保存按钮，承载保存、加载语义和“重试同步”入口。
- **隐私设置**没有提交按钮，选择直接交给 ViewModel 提交；重置与保存反馈位于顶栏尾侧。
- **认证与账号安全流程**通过 `AccountFlowActionBar` 复用 `FormActionBar`，承载固定在底部的通栏主按钮。`SettingsScaffold` 的可选 `bottomBar` 插槽直接交给 `FrostedScaffold` 内的官方 Scaffold。

Insets 的分工：宿主用 `imePadding` 避让并消费键盘 Insets；操作区只处理尚未消费的横向和底部安全区域；Scaffold 把底栏的实测高度写进内容 padding，滚动的 Column 把它放在 `verticalScroll` **之后**的底部 padding 里，滚动视口才是完整的。Snackbar 由 Scaffold 自动放在底栏上方。

首次加载时不显示操作底栏；游客登录和首次读取失败的重试使用固定底部按钮。

## 安全与二步验证

### 结构

安全页使用 `SecuritySettingsRoute`、条目级的 [SecuritySettingsViewModel](../../app/src/main/kotlin/moe/kirakira/feature/settings/security/SecuritySettingsViewModel.kt) 和 [SecurityRepository](../../app/src/main/kotlin/moe/kirakira/data/security/SecurityRepository.kt)。

| 文件 | 职责 |
| --- | --- |
| `SecuritySettingsPage` | 收集 ViewModel 与会话状态，处理失活和重新登录事件 |
| `SecuritySettingsNavigation` | 管理内部步骤的嵌套返回栈 |
| `SecuritySettingsScreen` | 只接收状态和回调的界面入口 |
| `SecurityOverview`／`SecurityFlowScreen`／`SecurityFormFields`／`SecurityTotpContent` | 概览与二步验证管理、流程布局与提交、输入字段、TOTP 绑定与恢复码展示 |
| `SecuritySensitiveClipboard`／`SecurityQrCode` | 剪贴板操作与二维码生成 |

状态用密封类型分支表达，每个分支只持有自己需要的载荷：`CredentialFlow` 负责邮箱与密码的填写、核验和同步，`TwoFactorFlow` 负责二步验证的管理、启用、停用、确认、代码展示与状态核对。`SecurityStep` 只是由当前分支派生的渲染标识。

每次请求捕获 `AuthRepository.RequestSession`，在散列之后、HTTP 返回之后都核对账号。明确的 401 才使本机会话失效。

### 敏感材料

- 密码、验证码、TOTP 设置密钥、二维码、备用码和恢复码只存流程内存，不进入可保存状态、路由或应用磁盘。敏感模型使用普通类，避免生成含凭据的 `toString()`。Kotlin 字符串无法保证物理内存即时清零。
- 可序列化的 `SecurityStepRoute` 只记录派生出的步骤。进程重建后恢复的步骤路径会被“不含敏感材料的初始业务状态”校正回首页。
- 退出动画使用的内存快照只保留邮箱和非敏感状态。失活或 revision 变化时释放设置材料。
- 复制通过 `ClipboardManager` 进行，并标记 `android.content.extra.IS_SENSITIVE` 和一个随机的所有者标记。离开步骤时只清理仍属于本流程的剪贴板内容，不覆盖其他应用之后复制的内容。
- 只有当前步骤可以处理事件、显示提示和执行复制。

### 邮箱与密码

填写新信息只做本地校验，进入核验阶段才允许发送邮件。核验页返回时保留新邮箱或两次输入的新密码，清空身份凭据；完整退出需要确认舍弃。

发送写请求前记录一个内存检查点，其中不含密码、摘要或验证码：

| 结果 | 处理 |
| --- | --- |
| 成功 | 检查点标记为已确认。邮箱只重读并发布本人资料；密码只删除请求所属的本机会话 |
| 明确拒绝 | 清除检查点 |
| 不确定（网络、超时、服务错误、异常响应、请求被取消） | 保留未确认的检查点，释放表单凭据并暂停编辑 |

- **邮箱未确认**时只回读核对：当前邮箱与目标一致才算完成。读到旧值并不能证明原写请求已经停止，所以旧值、其他值或读取失败都保持只读核对，不恢复可直接提交的表单。
- **密码未确认**时无法核对是否已修改：提示新密码可能已生效，用户点“重新登录”后清除本机会话并打开登录页，不再次提交旧密码。
- 存储失败时提供同步重试，不重放写请求。
- `AuthRepository` 在成功写入本地存储之后、发布会话状态之前，通过回调告知本次实际发布的 revision。资料与安全流程靠它识别“这是我自己引起的变化”，不自行推算下一个修订号。

### 二步验证

安全首页与管理页共用同一个 `SecurityStatus`。管理页未启用时先单选验证方式再点“继续”，默认不预选；所选方式在同状态刷新和返回子流程后保留，验证状态变化、失活和账号变化时清空。

`TwoFactorMutation` 标识结果尚未确认的写操作。结果不确定时清空输入凭据，转入 `TwoFactorFlow.Checking` 并撤下旧的验证状态：

- 用户触发查询或重新进入安全条目时，只调用状态读取接口；查询失败则留在核对分支，不能提交其他写请求。
- 确认绑定后查询到仍未开启时，可以复用已有的设置材料，由用户重新输入验证码并明确提交；查询到 TOTP 已启用则提示代码不可用。
- TOTP 创建结果丢失时，状态接口无法判断是否存在未启用的绑定草稿。当前条目把材料标记为不可用，禁止再次创建。

TOTP 确认页返回后，设置材料交还给管理分支，再次进入时复用同一份材料，避免重复创建。确认成功后解析一次性代码：`TotpConfirmation.CodesAvailable` 持有代码；`CodesUnavailable` 表示已启用但没拿到有效代码，此时清空设置材料并回到管理页，提示重新解绑再绑定，不重放确认请求。

恢复响应要求五个互不相同的六位备用码和一个 24 位恢复码，字符集为数字和大写字母。TOTP URI 按结构解析并校验 `otpauth://totp` 与 Base32 密钥；二维码由 ZXing Core 在后台生成，生成失败时仍可使用手动密钥。备用码与设置密钥的分组排版不改变复制值，也不缓存敏感文本的测量结果。

### 验证码邮件

安全验证码复用 `AuthRepository` 按邮箱的互斥锁、单调时钟和 60 秒冷却。当前邮箱使用认证端点，新邮箱使用带当前会话的通用邮箱端点，两者各自显示剩余时间。

| 用途 | `mailTemplate` | `exclusiveBusinessName` |
| --- | --- | --- |
| 修改邮箱 | `SendChangeEmailVerificationCode` | `update-email` |
| 修改密码 | `SendChangePasswordVerificationCode` | `update-password` |
| 关闭邮箱验证 | `SendDisableUserEmail2FAVerificationCode` | `delete-email-2fa` |

**服务端的一个特例**：Rosales 当前的 `General2FAVerifier` 在“未开启二步验证”的严格校验分支里固定查询 `update-email`。因此这类账号修改密码时，发送的是密码模板，但业务名用 `update-email`，以匹配服务端的实际逻辑；已启用邮箱验证的账号仍使用 `update-password`。

### 嵌套导航与返回

`SecuritySettingsNavigation` 与认证流程一样，复用 `ActivityNavDisplay` 和独立的 NavigationEvent 分发器，并重置继承来的页面变换，避免内外宿主重复缩放。首页使用 `SettingsScaffold`，管理与操作条目使用 `AccountFlowScaffold`。

- 邮箱和密码的填写页是核验页的父条目，完成后返回首页。
- 确认绑定与保存代码是管理页下的相邻条目，成功后不能回到确认绑定。
- 状态核对会替换管理分支，返回安全首页。
- 操作执行期间阻止返回。返回预检会把编辑器里尚未同步的输入算进去；代码展示页和有输入的填写页返回时要求确认，确认后清空相应载荷。

### 服务端限制

Rosales 的 `createUserTotpAuthenticatorService` 会插入一条未启用的记录，确认时按账号查询未启用记录并要求唯一；现有接口既不能取消草稿也不能读取草稿。在当前安全条目内复用材料可以避免重复创建，但离开条目、账号变化或进程重建后材料被释放，服务端遗留的记录仍可能影响之后的绑定。客户端不伪造清理接口。

## 隐私设置

隐私页使用 `PrivacySettingsRoute` 和条目级的 [PrivacySettingsViewModel](../../app/src/main/kotlin/moe/kirakira/feature/settings/privacy/PrivacySettingsViewModel.kt)，复用 `AccountSettingsRepository` 的账号 revision 请求守卫。

| 请求 | 说明 |
| --- | --- |
| POST `user/settings` | 读取。检查 `success`、设置对象和当前账号的 UID；五项中缺失的值默认为公开，出现重复项或未知的可见性时拒绝编辑 |
| POST `user/settings/update` | 提交完整的五项 `userPrivaryVisibilitiesSetting`，并带回原有的 `userLinkedAccountsVisibilitiesSetting` |

- wire ID 为 `privary.birthday`、`privary.age`、`privary.follow`、`privary.fans`、`privary.favorites`（拼写沿用服务端）；可见性取值为 `public`、`following`、`private`。
- 提交时必须带回关联账号的可见性设置，否则后端的更新逻辑会把它覆盖掉。不提交主题等其他偏好。
- 更新接口可能返回不含 UID 的载荷。这时用同一个请求账号再读一次 `user/settings`，确认 UID 和五项值之后才发布“保存成功”。

单项选择、批量选择和确认重置共用一个提交入口，选择当前值不会重复写入。提交时先更新显示值并进入 `saving`，禁用选择和返回。

| Repository 的结果 | ViewModel 的处理 |
| --- | --- |
| `PrivacySaveResult.Confirmed` | 更新最近确认的设置 |
| 明确拒绝（抛出统一的 API 错误） | 恢复最近确认的显示值并提示 |
| `PrivacySaveResult.Unconfirmed`（网络、超时、服务异常、响应异常，以及更新已被接受之后的任何核对失败） | 置 `confirmationPending`，暂停编辑。用户点“重试同步”时只调用读取接口，读取成功后以服务端的当前值作为新的比较基准 |

显示状态与选择面板只存流程内存。账号变化会取消请求并清空状态；旋转保留 ViewModel，进程重建后重新读取。后端在隐私限制上的缺陷见[功能现状的隐私设置](../features.md#隐私设置)。

## 屏蔽规则与邀请码

[AccountSettingsRepository](../../app/src/main/kotlin/moe/kirakira/data/settings/AccountSettingsRepository.kt) 通过领域模型把屏蔽规则和邀请码交给条目级的 ViewModel（位于 [feature/settings/management](../../app/src/main/kotlin/moe/kirakira/feature/settings/management)）。规则不在本机保存副本，过滤全部由后端负责。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `block/list` | `type` 为 `block`／`hide`／`tag`／`keyword`／`regex`，`page`、`pageSize`；`result`、`blocklistCount` | 总览数量与规则分页 |
| POST `block/user`、DELETE `block/delete/user` | `blockUid` | 屏蔽／解除用户 |
| POST `block/hideuser`、DELETE `block/delete/hideuser` | `hideUid` | 隐藏／显示用户 |
| POST `block/tag`、DELETE `block/delete/tag` | `tagId` | 标签规则 |
| POST `block/keyword`、DELETE `block/delete/keyword` | `blockKeyword` | 关键词规则 |
| POST `block/regex`、DELETE `block/delete/regex` | `blockRegex`；新增时还检查 `unsafeRegex` | 正则规则 |
| GET `user/info` | `uid` | 添加用户前查询公开资料 |
| GET `video/tag/search` | `tagName`；`result` | 选择已有标签 |
| GET `user/myInvitationCode` | Cookie；`invitationCodeResult` | 当前账号的邀请码 |
| POST `user/createInvitationCode` | 空 JSON 对象和 Cookie；`isCoolingDown`、`invitationCodeResult` | 生成邀请码 |

规则接口使用 Cookie `uuid`／`token`，邀请码接口使用 `uid`／`token`，都由既有的会话 Cookie 构造器提供。

### 规则

- **数量**。总览对每个类别读取一条来获得数量，管理列表每页 20 条。服务端对空集合的聚合可能省略 `blocklistCount`，只有结果确实为空时才把它当作零。
- **用户规则的标识**。规则的 `value` 是 UUID，解除时必须用关联的 `uid`，不能把 UUID 当 UID 用。关联用户被删除导致 UID 缺失时，保留条目并禁用解除。
- **添加用户规则**用 `profileDetails` 核对 UID。目标已被自己屏蔽、资料不可见时，仍可按已确认的 UID 添加隐藏规则；“资料可见”不是写入规则的前提。
- **关键词与正则**的客户端校验只处理非空和 30 字符的限制。正则不在 Kotlin 侧编译，语法与安全性由 Rosales 判断。
- **标签名称**按 Cerasus 的语言代码（`zhs`、`zht`、`en` 等）选择，依次回退到 `other`、首个语言和标签 ID。添加标签使用共享的[全屏标签选择器](components.md#全屏标签选择器)，候选读取复用 `ContentRepository.searchTags`。
- **写入之后**发布规则版本变化，刷新仍存活的总览，并重读当前类别的首屏。
- **结果不确定时**发布带 `confirmed = false` 的规则变化通知并刷新管理列表。仍保留着的视频页会对“可能已生效的用户屏蔽”先撤下内容和播放，再通过详情读取核对访问状态，不谎报屏蔽成功。

管理页的 `SettingsViewModel` 与内容页共用 `AccountTasks` 的子 Job 取消机制。每次请求捕获会话 revision，完成和失败时都检查身份；账号变化会取消任务并清空草稿。

### 邀请码

- `assignee` 是否存在决定“已使用”状态。先检查业务成功，再处理生成冷却；响应校验 `creatorUid`。
- 已确认生成、但尚未被列表读回的邀请码暂存在该账号的 ViewModel 内存里，刷新失败或短暂的旧结果不会覆盖它。
- **生成结果不确定时**提示并重新读取已有列表。如果之后仍没读到新增的邀请码，再次生成前要先让用户确认“可能重复”的风险：旧列表不能证明原请求已经停止，客户端也不自动重试生成。

页面用 `CollapsingTabLayout` 把统计卡和 Tab 放在同一个顶部表面里，统计区随滚动移出，Tab 吸顶。每个分页有独立的列表状态，重选 Tab 只让当前列表回顶。列表状态区域的接入见[统一内容状态](components.md#统一内容状态)，数量的滚动动画见[数字动画与字数计数](components.md#数字动画与字数计数)。
