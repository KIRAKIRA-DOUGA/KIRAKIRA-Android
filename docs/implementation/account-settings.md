# 账号设置

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护资料编辑、安全与二步验证、隐私、屏蔽规则及邀请码。开发约束以贡献指南为准。

- [本人资料编辑](#本人资料编辑)
- [设置表单操作区](#设置表单操作区)
- [账号设置管理](#账号设置管理)
  - [安全与二步验证](#安全与二步验证)
  - [隐私设置](#隐私设置)
  - [屏蔽规则与邀请码](#屏蔽规则与邀请码)

## 本人资料编辑

个人主页通过 `ProfileHeader → ProfileScreen → ProfilePage` 的独立 `onEditProfile` 回调上报编辑操作。`SelfProfileRoute` 与当前用户的 `ProfileRoute` 均通过 `openFrom` 打开同一个 `ProfileEditorRoute`，防止重复入栈；按钮按当前登录用户 UID 显示，点击时再次核对身份。编辑页退出仍使用现有返回保护，保存后沿用会话修订触发主页资料刷新。

个人主页「编辑资料」使用全宽的 `ShadowButton`（内部保留官方 M3E `Button`），以 `ButtonDefaults.MediumContainerHeight` 设置最小高度，并将同一高度传给 `shapesFor`、`contentPaddingFor` 与 `textStyleFor`，统一使用官方 Medium 形状、内边距与文字样式；`heightIn(min = …)` 允许字体缩放时按钮随内容增高。

`ProfileEditorRoute` 使用 Navigation 3 条目级 `ProfileEditorViewModel`，资料通过 `data/profile/ProfileRepository` 访问，DTO 与凭据保持在数据层。`AccountProfile` 增加生日、性别和标签及兼容旧存储的默认值；读取本人资料时核对 UUID、UID。

页面最大宽度 640dp，与设置页同用 `surfaceContainer` 背景和 `SectionHeader` 分组标题；顶部为大圆角横幅，使用 `Image` 与 `ContentScale.Crop` 展示个人主页共用的 `profile_banner_placeholder` 樱花图，保留服务端背景字段；头像为居中重叠的 112dp 头像，带背景色描边的编辑按钮固定在头像右下角。按钮的 `TooltipBox` 外层使用直接属于头像容器的 `Box(Modifier.align(Alignment.BottomEnd))`，避免 Tooltip 内部锚点截留父布局对齐参数；描边、阴影、提示、加载及禁用行为保持一致。

基本资料与标签放在 `surface` 圆角卡片中，个人信息使用分段资料行；保存操作接入[设置表单操作区](#设置表单操作区)。另含日期选择器与标签 Chips。所有界面文本维护中英文资源，预览使用空表单。

用户名与昵称均为必填，按 Rosales `ValidTool.validateNameField` 校验，最长 20 个 UTF-16 单元；简介最长 200。保存统一 NFC 规范化，用户名执行 trim，只有修改用户名才调用查重。标签保留顺序及已有 ID，新项使用最小可用非负 ID；生日未设置时发送空字符串，不默认写入当天。更新始终包含用户名与已有背景，其他不属于编辑表单的字段不主动发送。昵称必填由 Android 表单在提交前校验，服务端仍允许缺失昵称；资料回读、旧会话存储和名称展示保留兼容，旧账号下次保存资料时必须补填昵称。

保存依次进行头像上传、资料更新、本人资料回读和加密会话提交。Repository 持有单个编辑流程的检查点：已上传的同一头像草稿不重复上传；服务端已保存后锁定编辑，刷新失败只重试刷新，写盘失败保留已读结果并仅重试提交。提交保留 token 与 bootstrap hint，仅替换当前账号资料；发布会话修订后现有内容观察者刷新“我”及本人主页。外部账号／修订变化取消请求和清除草稿，仅自身提交产生的修订被编辑页接纳。HTTP 200 的业务拒绝不通过匹配服务器消息推断过期。

图片选择使用 `PickVisualMedia`，回调绑定选择时的账号修订；原图复制限制 32 MiB。CanHub Android Image Cropper 提供经过验证的裁剪、EXIF 与采样能力，通过 `AndroidView` 封装 `CropImageView`，仅这一引擎使用第三方 View，周围顶栏、工具按钮、加载与确认均使用 Compose M3E。固定 1:1 裁剪和圆形预览，异步输出最大 1024px 的 JPEG（质量 90）；Manifest 移除库自带的导出 Activity 与未使用的文件提供者。

裁剪库要求自定义输出使用 `content://` URI，且文件扩展名须与压缩格式匹配；应用复用 `${applicationId}.images` FileProvider，将缓存中的 `profile-editor/` 限定目录映射为输出 URI，保留 `.jpg` 后缀，不传入 `Uri.fromFile(output)`。URI 生成或裁剪启动的同步异常通过既有失败回调清除运行状态、恢复完成按钮并显示图片处理失败提示，协程取消继续传播。View 重建时从流程内存恢复裁剪范围；释放时清理图片和监听，正在运行的异步裁剪仅保留收尾回调，完成后释放图片并拒绝旧流程结果。草稿文件为每个流程独立缓存，替换、退出和账号变化时异步删除；不写 SavedState 或会话存储。

裁剪缓存按 `profile-editor/<进程随机标识>/<流程随机标识>/` 隔离。进入资料编辑页时，由以 ViewModel 为 key 的 `LaunchedEffect` 在 IO 线程扫描旧进程目录，同时清理旧版直接位于 `profile-editor/` 下的流程目录；整个当前进程目录均受保护，避免多个编辑页、页面重建或仍在收尾的任务被误删。正常删除通过既有后台清理队列执行，等待资料保存、图片准备和库内异步裁剪的完成信号后再删除文件或流程目录；裁剪完成回调也会释放旧流程的占用。图片复制与输出文件创建在取消或失败时清理尚未交给页面的临时文件。清理属于尽力操作，不影响表单操作；无法删除的旧进程缓存可在以后进入编辑页时重试，当前进程目录中的残留可在进程重建后清理。

预签名上传使用独立无 Cookie／认证头的 OkHttp 客户端，仅允许 `https://upload.imagedelivery.net:443`，发送 multipart `file`，禁用重定向和自动重试，完整调用上限 60 秒，响应上限 1 MiB，并检查 HTTP 与 JSON `success`。成功的图片 ID 交给资料更新接口，不保存或记录预签名 URL。应用 API 仍使用共享 HTTPS 客户端。

返回保护由 `ActivityNavDisplay` 的默认放行回调协调。资料有草稿或正在裁剪时不启动页面预测性返回转场；手势完成才显示确认、关闭裁剪或忽略正在保存的返回，取消手势没有副作用。顶栏使用同一 ViewModel 判断；用户确认离开后才出栈，不添加页面级低层返回处理器。

## 设置表单操作区

资料编辑通过 `SettingsScaffold.actions` 在顶栏右侧放置官方 `TextButton`，保留保存标签、加载语义与重试同步入口；未保存返回保护仍由 `ProfileEditorViewModel` 管理。隐私选择直接交给 `PrivacySettingsViewModel` 提交，不组合底部应用栏；重置及保存反馈位于顶栏尾侧。两页继续使用 `SettingsColumn`，底部系统内边距随内容滚动，宿主处理 IME，Snackbar 交由 Scaffold 管理。头像裁剪保留原底部旋转与 `SettingsPrimaryButton` 完成布局。

`SettingsScaffold` 保留可选 `bottomBar` 插槽，直接交给 `FrostedScaffold` 内的官方 Scaffold；需要固定底部操作的设置表单可使用 [SettingsActionBar](../../app/src/main/java/moe/kirakira/feature/settings/SettingsComponents.kt)。认证与账号安全流程使用 `AccountFlowActionBar` 和固定底部通栏按钮。两类操作区共用 `FormActionBar`，由调用方决定内容宽度与按钮排列。

操作区以 `surface` 铺满宽度，根 `Surface` 通过 `shadowElevation = BarShadowElevation` 绘制固定 4dp 原生阴影，按钮可用、禁用及加载状态均保留底栏阴影。`SettingsActionBar` 内部居中限制在设置页 640dp 内容区域，四周留出 16dp；账号流程使用自身的 480dp 内容上限与 24dp 横向留白。主按钮通过 `fillMaxWidth` 占满区域内可用宽度。

`ShadowButton` 使用 `ButtonDefaults.MediumContainerHeight` 及同高度的 `shapesFor`、`contentPaddingFor` 和 `textStyleFor`，默认仅显示单行文字，加载时使用 Material 2 官方按钮图标尺寸／2dp 的圆形加载器，前景继承当前按钮内容色。普通操作沿用 `primary/onPrimary`，安全停用为 `error/onError`；保留本项目共享双层彩色阴影和禁用时无阴影的规则，这是相对 [Material 官方默认实心样式](https://m3.material.io/components/buttons/guidelines)的定制。

完整操作说明可通过按钮无障碍描述提供，加载状态通过本地化状态描述表达。

宿主使用 `imePadding` 避让并消费键盘 Insets，操作区仅处理尚未消费的 `safeDrawing` 横向和底部安全区域。Scaffold 将底栏实测高度写入内容 padding，滚动 Column 将其放在 `verticalScroll` 后的底部 padding，保持完整滚动视口；Snackbar 自动放在底栏上方，不另加固定偏移或悬浮工具栏留白。账号流程的游客登录与初次读取失败重试使用固定底部按钮，首次加载时不显示操作底栏；安全概览不组合提交底栏。返回、账号隔离和业务提交继续由对应 ViewModel 管理。

## 账号设置管理

### 安全与二步验证

安全页使用 `SecuritySettingsRoute`、导航条目级 `SecuritySettingsViewModel` 与 `data/security/SecurityRepository`。页面内步骤与表单由 ViewModel 管理，敏感模型使用普通类避免生成含凭据的 `toString()`；没有敏感持久化状态或 DTO 进入 UI。每次请求捕获 `AuthRepository.RequestSession`，核对 revision 并在散列后、HTTP 返回后验证账号；取消继续传播，明确 401 才失效本机会话。设置入口对游客导航登录，恢复导航中的游客安全页提供登录操作。

二步验证使用统一管理入口，安全首页与管理页共用同一 `SecurityStatus`；首页保留状态横幅，管理页在账号流程布局中展示验证状态。状态横幅与 `TwoFactorEnabledCard` 共用 `surfaceContainerLow`、主题大圆角及 64dp 方式图标容器，已启用时使用主题色图标并复用 `TwoFactorEnabledBadge` 的绿色勾选标记；未启用或待核对时保持中性色，待核对不展示旧方式或启用标记。管理页以 `titleLarge` 突出当前方式，分隔线下直接展示邮箱，绑定日期保留标签及本地化值，长邮箱允许换行，缺少绑定时间时不显示空信息区。`TwoFactorFlow` 分别持有管理、邮箱启用／停用、TOTP 确认、代码展示与状态核对的载荷，`SecurityStep` 仅由当前页面和分支派生为渲染标识。

管理分支的 `selectedFactor` 默认为空，`selectFactor` 只更新单选状态，固定底部“继续”按钮通过统一提交入口进入邮箱启用确认或 TOTP 绑定；已有方式的主按钮进入关闭／解绑核验。所选方式在同状态刷新及返回子流程后保留，验证状态变化、失活及账号变化时清空；材料不可用时禁用身份验证器选项。邮箱与密码表单保持独立，二步验证表单和一次性代码只属于对应分支。TOTP 创建在主按钮内显示加载反馈，成功后直接进入二维码与验证码页；方式选择不计入进度，仍只计算绑定确认与保存代码两步。TOTP 确认页返回后将设置材料交给管理分支，再次进入复用同一材料并显示“继续绑定”；关闭成功回管理页，由用户另行启用另一方式。

邮箱与密码使用 `CredentialFlow` 的填写、核验与同步分支持有表单，`SecurityStep` 由分支派生，替换独立表单与完成标志组合。填写新信息只做本地校验，核验阶段才允许发送邮件，最终提交沿用原有请求契约。邮箱填写页只保留新邮箱输入框，不单独展示当前邮箱；核验区直接显示当前收件地址，新邮箱保留任务标题，TOTP 账号只给新邮箱发送邮件；密码核验页提示需要重新登录。`fieldErrors` 标记具体输入框，校验尝试编号驱动 Compose 在字段组合后聚焦第一个错误；最后一个字段使用 IME Done 对应当前步骤的主操作。核验返回时只保留新邮箱或两次输入的新密码，清空身份凭据，完整退出须确认舍弃；账号变化、失活与同步分支释放表单。验证码在 ViewModel 归一为最多六位数字，发送与提交仍由同一请求入口串行执行。

`SecuritySettingsState.descriptionRes()` 在屏幕层按步骤和当前验证方式选择中英文说明，不引入业务状态；修改邮箱／密码核验分别显示邮箱或验证器引导，密码核验保留重新登录的后果。邮箱启用说明提醒确认收件能力，绑定页说明扫描二维码或输入设置密钥后填写验证码，代码页提醒离开后不可再次查看。同步完成待重试时返回空说明，状态核对页沿用现有状态文案，避免继续显示待填写引导。私有 `SecretCard` 的可空 `supportingContent` 插槽在标题下、分隔线上方显示备用码及恢复码用途，以次要文字颜色呈现；该区域位于 `SelectionContainer` 外，复制内容仍为原始代码，没有说明时不预留间距。

`TwoFactorMutation` 标识结果尚未确认的写操作。网络故障、超时、服务异常或无法解析响应时清空输入凭据，转入 `TwoFactorFlow.Checking` 并撤下旧的验证状态；用户触发查询或重新进入安全条目时只调用状态读取接口。查询失败保留核对分支，不能提交其他写请求；确认操作查询到仍未开启时，可复用已有设置材料，由用户重新输入验证码并明确提交，查询到 TOTP 已启用则提示代码不可用。创建结果丢失时，状态接口无法判断未启用的绑定草稿，当前条目标记材料不可用，禁止再次创建。

安全验证码复用 AuthRepository 的按邮箱互斥锁、单调时钟与 60 秒冷却，当前邮箱使用认证端点，新邮箱使用带当前会话的通用邮箱端点；两者独立显示剩余时间。模板对应 `SendChangeEmailVerificationCode` / `update-email`、`SendChangePasswordVerificationCode` / `update-password`、`SendDisableUserEmail2FAVerificationCode` / `delete-email-2fa`。Rosales 当前 `General2FAVerifier` 在无二步验证的严格校验分支固定查询 `update-email`：这类账号修改密码时发送密码模板但使用 `update-email` 业务名，兼容实际服务端逻辑；已启用邮箱验证仍使用 `update-password`。不改动 Rosales 或 Cerasus。

邮箱与密码更新服务端确认成功后，Repository 在内存记录完成检查点；邮箱检查点只重读并发布本人资料，密码检查点只删除请求所属本机会话。存储失败释放表单凭据并提供同步重试，不重放写请求。AuthRepository 在会话互斥锁内校验请求并成功写入本地存储后、发布会话状态前，通过回调提供本次实际发布的 revision；资料与安全流程据此识别自身发布／登出，不自行推算下一修订号。账号切换或其他会话变更仍取消旧流程。密码变更后经一次性邮箱状态打开登录并尽力清理密码提供者会话，不自动读取或保存密码。

安全首页使用共享可折叠顶栏、官方分段列表和浅色状态横幅；二步验证管理页和操作页复用认证流程布局、居中流程图标与固定底部通栏主按钮，多步操作保留分步进度。验证方式单选使用 `ConnectedListGroup`、`SettingsRadioItem` 与 `selectableGroup`，整行及单选框共用交互源，选中状态由 ViewModel 提供。普通设置入口、开关、单选、滑块、资料和安全列表前后图标使用裸露的 24dp Material Symbols Rounded（含尾部箭头及列表内操作图标），继承列表内容颜色及禁用样式，危险操作使用主题错误色；弹幕总开关保留官方 checked 行强调。

共用菜单组件的“我”页面同步使用 24dp 图标；屏蔽管理固定类别与添加表单同样使用 24dp 裸图标，用户头像保持独立。MaterialShapes 图标容器保留于隐私可见性、邀请码使用状态等状态形状切换，以及安全状态横幅与邀请码统计的独立大图标。首页与管理页刷新保留内容；表单使用滚动布局，底部留白包含底栏实测高度，宿主统一避让 IME。TOTP URI 按结构解析并校验 `otpauth://totp` 与 Base32 密钥；ZXing Core 固定版本在后台生成带白底静区的 Bitmap，Compose 展示，生成失败仍可使用手动密钥。

`SecuritySettingsNavigation` 与 Auth 复用 `ActivityNavDisplay`、`NavigationPage` 和独立的 NavigationEvent 分发器，重置继承的页面变换以免内外宿主重复缩放。可序列化 `SecurityStepRoute` 只记录派生步骤，返回路径由业务状态同步到 `rememberNavBackStack`，共同父条目保留滚动与顶栏状态；首页使用完整 `SettingsScaffold`，二步验证管理与操作条目使用 `AccountFlowScaffold`，顶栏、正文和固定底栏一起执行 AOSP 普通过渡及两阶段预测性返回。

邮箱和密码填写页是核验页的父条目，完成后返回首页；确认绑定与保存代码是管理页下的相邻条目，成功后不会返回确认绑定；状态核对替代管理分支，返回安全首页。返回预检与业务返回分开处理：操作期间阻止返回，代码展示与有输入的填写页阻止手势预览并由完成事件触发离开确认，确认后清空相应载荷。管理页的返回预览保留所选验证方式。退出动画的内存快照只保留邮箱与非敏感状态，不保留密码、验证码或一次性代码，失活或 revision 变化时释放设置材料；核验页返回预览从当前业务草稿取得填写内容，密码默认隐藏。只有当前步骤可以处理事件、显示提示和执行复制，剪贴板在离开步骤时清理。进程重建恢复的步骤路径会被无敏感材料的初始业务状态校正回首页。

恢复响应核对五个不同的六位备用码和 24 位恢复码，两者的字符集均为数字及大写字母；邮箱验证码为六位数字。安全核验和绑定确认复用六格 `OtpInputField`，解绑验证器的输入允许字母数字备用码，其他安全核验仅接受数字。Repository 先读取确认结果，再单独解析代码；`TotpConfirmation.CodesAvailable` 持有一次性代码，`CodesUnavailable` 表示已启用但未取得有效代码，ViewModel 清空设置材料并回到管理页提示重新解绑绑定，不重放确认请求。复制通过 Android ClipboardManager 标记 `android.content.extra.IS_SENSITIVE` 与随机所有者标记，离开步骤仅清理仍属于本流程的内容，不覆盖其他应用后续复制的内容。二维码、密钥与恢复码不导出到应用磁盘；生命周期和内存保护边界与认证流程一致，Kotlin 字符串无法保证物理内存即时清零。

二维码使用白底正方形展示，宽度随可用空间收缩，外层与 `SecretCard` 统一采用 `surfaceContainerLow`；打开验证器及复制按钮均为官方 `OutlinedButton`，在各自展示区末端对齐。`SecretCard` 用标题与分隔线区分内容，选择容器仅包围代码，辅助操作由可选 `actionContent` 提供。备用码按当前字体实际测量选择一列或两列，行间使用轻分隔线，末行单个代码占满行宽后居中；`GroupedSecret` 使用 `FlowRow` 将设置密钥及恢复码按四位分组排布，换行保留完整分组。排版不改变复制值或服务端协议，不缓存敏感文本测量结果；验证码排版与错误过渡复用[账号流程组件](components.md#账号流程布局)。

Rosales `createUserTotpAuthenticatorService` 插入未启用记录，确认验证按账号查询未启用记录并要求唯一；现有接口没有草稿取消或读取能力。当前安全条目内复用材料可以避免重复创建，但离开条目、账号变化或进程重建后会释放材料，服务端遗留记录仍可能影响后续绑定。客户端不伪造清理接口，不改动 Rosales 或 Cerasus。

### 隐私设置

隐私页采用 `PrivacySettingsRoute` 与导航条目级 `PrivacySettingsViewModel`，复用 `AccountSettingsRepository` 的账号 revision 请求守卫。`POST user/settings` 读取后检查 `success`、设置对象及当前账号 UID；五项缺失值默认公开，重复项或未知可见性拒绝编辑。`POST user/settings/update` 提交完整五项 `userPrivaryVisibilitiesSetting`，保留其他隐私条目并带回原有 `userLinkedAccountsVisibilitiesSetting`，避免后端更新逻辑覆盖关联设置，不提交主题等其他偏好。wire ID 使用 `privary.birthday`、`privary.age`、`privary.follow`、`privary.fans`、`privary.favorites`，可见性值使用 `public`、`following`、`private`。

更新接口可能返回不含 UID 的更新载荷：此时 Android 通过同一请求账号再次调用 `POST user/settings`，确认 UID 与五项值后再发布保存成功。ViewModel 的单项选择、批量选择和确认重置共用提交入口，选择当前值不重复写入；先更新显示值并同步设置 `saving`，禁用选择和返回，成功后更新最近确认的设置，明确失败时恢复最近确认的显示值并提示。Repository 返回 `PrivacySaveResult.Confirmed` 或 `Unconfirmed`：网络故障、超时、服务异常、响应异常，以及更新已被接受后的任何核对失败，均返回待确认结果；明确拒绝继续抛出统一 API 错误。

ViewModel 用 `confirmationPending` 暂停编辑并显示待确认状态，用户触发“重试同步”时只调用读取接口，不重发更新请求；读取成功后以服务端当前值建立新的比较基准，读取失败继续保留待确认状态。DTO 仅在数据层使用，领域模型交给 UI；显示状态和选择面板仅存流程内存。账号变化取消请求、清空状态；旋转保留 ViewModel，进程重建重新读取。隐私页复用 `ActivityNavDisplay` 回调，仅在请求期间阻止返回，完成后无需舍弃确认。

界面使用连接式 ToggleButton 组批量设置可见性，按当前可见性着色的 MaterialShapes 形状图标容器，分段列表逐项选择。顶栏重置弹出确认框，明确五项将全部公开，确认后调用同一提交入口，已是默认值时禁用；请求中在原图标位置显示 24dp／2dp 加载器及本地化状态描述。底部选择面板复用相同形状容器。后端隐私限制缺陷及保存语义见[隐私设置](../features.md#隐私设置)，不改动 Rosales 或 Cerasus。

### 屏蔽规则与邀请码

`data/settings/AccountSettingsRepository` 复用共享 API 客户端与唯一 AuthRepository，屏蔽规则和邀请码通过领域模型交给条目级 ViewModel。导航为 `BlockingOverviewRoute`、带 `RuleCategory` 的 `RuleManagementRoute` 与 `InvitationsRoute`，使用现有条目装饰器、`NavigationPage` 和认证返回流程；不增加依赖或持久化规则副本。

| 请求                                                   | 请求字段／关键响应                                                                                             | 用途            |
|------------------------------------------------------|-------------------------------------------------------------------------------------------------------|---------------|
| GET `block/list`                                     | `type` = `block` / `hide` / `tag` / `keyword` / `regex`，`page`, `pageSize`；`result`, `blocklistCount` | 总览数量与规则分页     |
| POST `block/user`、DELETE `block/delete/user`         | `blockUid`                                                                                            | 屏蔽／解除用户       |
| POST `block/hideuser`、DELETE `block/delete/hideuser` | `hideUid`                                                                                             | 隐藏／显示用户       |
| POST `block/tag`、DELETE `block/delete/tag`           | `tagId`                                                                                               | 标签规则          |
| POST `block/keyword`、DELETE `block/delete/keyword`   | `blockKeyword`                                                                                        | 关键词规则         |
| POST `block/regex`、DELETE `block/delete/regex`       | `blockRegex`；新增还检查 `unsafeRegex`                                                                      | 正则规则          |
| GET `user/info`                                      | `uid`                                                                                                 | 添加用户前复用公开资料查询 |
| GET `video/tag/search`                               | `tagName`；`result`                                                                                    | 选择已有标签        |
| GET `user/myInvitationCode`                          | Cookie；`invitationCodeResult`                                                                         | 当前账号邀请码       |
| POST `user/createInvitationCode`                     | 空 JSON 对象及 Cookie；`isCoolingDown`, `invitationCodeResult`                                             | 生成邀请码         |

规则接口使用 Cookie `uuid` / `token`，邀请码使用 `uid` / `token`，统一通过既有会话 Cookie 构造器提供。每次请求捕获会话 revision，完成及失败时检查身份，账号变化取消任务并清空草稿；邀请码响应校验 `creatorUid`。HTTP 401 沿用会话失效处理，普通业务拒绝不直接撤销 token，也不展示原始服务端消息。

总览每类读取一条以获取数量，管理列表每页 20 条；写入成功发布规则版本变化，刷新存活总览，并重新读取当前类别首屏。服务端空集合的聚合可能省略 `blocklistCount`，仅在结果确实为空时兼容为零。用户规则的 `value` 为 UUID，解除必须使用关联的 `uid`，不能将 UUID 当作 UID；关联用户被删除导致 UID 缺失时保留标识、禁用解除。标签名称按 Cerasus 的 `zhs` / `zht` / `en` 等语言代码选择，回退 `other`、首个语言和标签 ID。

关键词与正则的客户端校验仅处理非空及 30 字符限制；正则不经 Kotlin 编译，语法与安全性由 Rosales 判断。邀请码的 `assignee` 是否存在决定已使用状态；先检查业务成功，再处理生成冷却。确认生成但尚未被列表读回的邀请码暂存在该账号的 ViewModel 内存中，刷新失败或短暂旧结果不覆盖它；不自动重试生成请求。过滤全部由后端负责，不扩展首页、评论或弹幕的本地规则处理。
