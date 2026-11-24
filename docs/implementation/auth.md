# 认证与会话

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录认证流程的状态归属、系统密码管理器的接入边界、与 Rosales 的接口契约、错误分类，以及本机会话文件的存储与迁移。必须遵守的约束见[认证状态约束](../../CONTRIBUTING.md#认证状态约束)和 [API 与会话接入规范](../../CONTRIBUTING.md#api-与会话接入规范)。

- [认证状态](#认证状态)
- [分步输入与系统凭据](#分步输入与系统凭据)
- [API 与会话](#api-与会话)
- [接口契约](#接口契约)
- [网络与错误](#网络与错误)
- [会话存储与切换](#会话存储与切换)
- [保护边界与服务端限制](#保护边界与服务端限制)

## 认证状态

认证功能位于 [feature/auth](../../app/src/main/kotlin/moe/kirakira/feature/auth)，账号切换位于 [feature/account](../../app/src/main/kotlin/moe/kirakira/feature/account)。

| 层次 | 职责 |
| --- | --- |
| `AuthScreen`／`AuthForm`／`LoginForm` | 展示登录、二步验证、注册和密码找回步骤，上报操作 |
| `AuthPage`／`AuthNavigation` | 外层条目共享表单状态；内部步骤的返回栈、转场、键盘和成功返回事件 |
| `AuthViewModel` | 条目内的表单、校验、异步提交和验证码冷却展示 |
| `SessionViewModel` | 启动加载、资料刷新、切换、移除和登出 |
| `AuthRepository` | 认证编排、密码摘要、账号去重、冷却、会话事务和领域状态 |
| `AuthApi`／`SessionStore` | 私有 DTO 与 HTTP 协议；私有文件的原子存储 |

### ViewModel 的生命周期

`ActivityNavDisplay` 在可保存状态装饰器之后添加 `rememberViewModelStoreNavEntryDecorator`，因此认证 ViewModel 绑定外层的 `AuthRoute` 条目：旋转时保留，出栈后释放。`AuthRoute` 只携带可选的邮箱（用于重新登录），内部的 `AuthStepRoute` 只携带步骤枚举，都不含表单数据。

各步骤通过嵌套的 `ActivityNavDisplay` 展示，复用普通转场和预测性返回。子分发器连接外层的 Navigation Event 分发器，只在认证页是当前页面时启用；子页面重置继承来的页面变换，否则外层的退出手势会被应用两次。

### 步骤路径与返回

`AuthStep.previousStep` 统一定义内部路径，顶部返回箭头与系统返回走同一条路径：

- 注册按资料、凭据、邀请码、验证逐级返回。
- 登录的二步验证返回邮箱密码页。
- 重置密码和 TOTP 找回说明返回邮箱输入页。

可返回的步骤在退出或返回时取消当前请求，旧请求的收尾不能改写新页面的状态。注册的最终提交和密码重置的写请求执行期间暂停返回，因为取消后用户会重新提交，可能重复消耗验证码或重复注册。转场中正在离开的页面使用只存内存的表单快照，避免提前渲染下一步的内容。

### 哪些状态可恢复

- 只有邮箱通过 `SavedStateHandle` 恢复。
- 认证内部的可保存状态用 ViewModel 生命周期内的随机键隔离：旋转沿用该键；进程重建会生成新键并回到登录入口，不恢复密码、验证码以及依赖这些输入的步骤。
- 密码和验证码的 `TextFieldState` 不接入 Saver；流程清空时同时丢弃对应的撤销历史。
- 密码显隐使用条目内的普通 `remember`，步骤切换和界面重建后恢复隐藏。

### 提交、保存失败与结果不确定

提交在 `viewModelScope` 中执行。成功状态只在 RESUMED 时通过页面回调返回，宿主会检查当前路由，避免重复出栈。认证会话只在本机存储成功后才发布。

| 情况 | 处理 |
| --- | --- |
| 认证已成功但本机保存失败 | 把待保存凭据留在内存并进入“保存重试”步骤，不重新注册、不再消耗验证码；关闭页面即释放。已解析的资料随待保存会话保留，写盘重试不再请求资料 |
| 资料解析或身份一致性检查失败 | 拒绝保存，也不提供保存未验证身份的重试 |
| 密码重置被明确拒绝 | 清除检查点 |
| 密码重置结果不确定（超时、网络故障、服务异常、响应异常） | 保留未确认状态并清空表单凭据。主按钮变为“重新登录”，只清理对应邮箱的本机会话，然后回到带未确认提示的登录页；不把未确认的新密码交给系统密码管理器。完成本机清理前不能返回登录 |
| 密码重置已确认但本机清理失败 | 进入清理重试步骤，不重发密码重置请求 |
| 注册响应不确定且未取得可保存的会话 | 清空草稿，回到预填邮箱的登录页，提示账号可能已创建；不自动注册或登录 |
| 登录成功但资料接口暂时不可用 | 保留登录返回的邮箱和 UID，不当作需要再次注册 |

密码重置在发出写请求前建立一个不含密码和验证码的检查点，上表的各种收尾都以它为依据。

待保存记录通过内存中的 `AuthFlowOwner` 绑定到条目：旧页面的退出动画结束后才执行的清理，不能删除新页面的数据；旧请求完成不可取消的写盘阶段后，也只清除属于自己的待处理记录。成功提交后禁止继续编辑表单或切换认证分支，直到页面关闭。

历史用例与验证边界见[认证离线测试](../auth-testing.md)。

## 分步输入与系统凭据

### 注册步骤

注册分为资料、凭据、邀请码（`REGISTER_INVITATION`）和验证四步。凭据步骤校验确认密码、密码提示和邮箱唯一性；邀请码步骤校验格式与可用性，然后按邮箱冷却发送验证码并前进。返回只清空验证码，保留流程中的其他草稿；离开整个分支才清除秘密。

`fieldErrors` 和带编号的 `focusField` 指向具体字段。子条目等到 RESUMED、可编辑并完成下一帧布局后才请求焦点，且只消费一次。未知的业务拒绝显示为页面级错误，不把服务器含糊的 `success=false` 猜成某个字段的错误。

注册与资料编辑的用户名、昵称校验共用 [AccountNameRules](../../app/src/main/kotlin/moe/kirakira/data/auth/AccountNameRules.kt)。字符范围来自 Cerasus 的 `assets/pomsky/username.pom`，长度上限为 20 个 UTF-16 单元；最终提交仍以 Rosales 的校验结果为准。

表单的组件与布局接入见[账号流程布局](components.md#账号流程布局)。

### 系统密码管理器

[PasswordCredentialGateway](../../app/src/main/kotlin/moe/kirakira/core/credentials/PasswordCredentialGateway.kt) 提供获取、保存和清理会话三个操作，系统实现使用 AndroidX Credentials 与 Play Services 桥接。

- **只在生产根地址构建启用**。只有完整的生产 API 根地址构建的 `SYSTEM_CREDENTIALS_ENABLED` 为 true，其余构建使用禁用网关，测试直接注入替身。`AuthPage` 根据最终选用的网关是否为禁用网关，向 ViewModel 传递开关，不重复判断构建常量。
- **登录页不主动获取密码**。已保存的密码只通过输入框的 Compose Autofill 语义提供，填充后由用户提交；这样不会与系统自动填充重复弹窗。网关保留的获取接口使用 `GetPasswordOption` 并关闭自动选中。
- **保存时机**。只有手动输入或修改过的密码，在完整认证并且本机写盘成功之后才生成 `CreatePasswordRequest`；二步验证完成前不保存，密码重置要等本机清理完成。来自系统选择且未修改的密码不重复保存。
- **保存失败不撤销认证**。取消或失败都进入成功或重置完成状态，失败只显示非阻断提示：`AuthPage` 通过回调通知 `AppNavHost`，由宿主级 Snackbar 显示，认证页返回后提示仍可见。
- **持久化边界**。原密码只交给用户选择的密码提供者，应用不写入任何存储。流程完成、退出或取消时释放待保存对象；秘密类不生成包含密码的 `toString`。库内部和 JVM 字符串的副本无法保证即时擦除。
- **清理**。切换到游客、移除当前账号或登出之后，尽力调用 `clearCredentialState`，不删除提供者里的密码；清理失败不撤销本地操作。

### 一次性结果与宿主身份

`AuthCredentialOperation` 是带递增编号的内存操作。外层 `AuthPage` 在当前 Activity 处于 RESUMED 后领取，Activity 身份变化或组合销毁时撤销领取。回调必须同时匹配操作编号和宿主身份，并检查协程未被取消，所以旧 Activity 的延迟结果影响不到当前流程；系统弹窗使 Activity 暂停时也不会重新发起操作。

所有显式的前进、返回、退出和系统保存之前，先取消 Compose Autofill 会话，同时保留邮箱／用户名和密码字段的 Autofill 语义。

未配置 Digital Asset Links，因此不支持跨网站共享密码，也不支持 Passkey。

参考：[官方密码接入](https://developer.android.com/identity/passwords)、[Compose Autofill](https://developer.android.com/develop/ui/compose/text/autofill)、[Credentials 版本记录](https://developer.android.com/jetpack/androidx/releases/credentials)。

### 账号切换的操作状态

会话状态用 `SessionOperation(type, targetUuid)` 区分初始化、切换、移除与本机重置。操作串行执行，写盘期间保留类型与目标，成功提交之前继续选中原账号。错误携带目标 UUID，重试闭包绑定原操作。

## API 与会话

### 接口契约

流程对照同级 Cerasus 项目的 `components/Login/LoginWindow.vue`、`composables/api/User/UserController.ts` 和 `utils/hash.ts`；端点与字段核对同级 Rosales 的 `src/route/router.ts`、`src/controller/UserControllerDto.ts` 和 `src/service/UserService.ts`。这些契约来自源码对照，不表示已用生产账号联调。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `user/checkUserHave2FAByEmail` | query `email`；`success`、`have2FA`、`type` | 判断无验证器／邮箱／TOTP |
| POST `user/login` | `email`、`passwordHash`、`clientOtp` 或 `verificationCode`；`success`、`UUID`、`uid`、`token` | 密码及可选二步验证登录 |
| POST `user/self` | Cookie `uuid`、`uid`、`token`；`success`、`result` | 校验会话并获取本人资料 |
| POST `user/update/info` | `username`、`userNickname`、`signature`、`gender`、`userBirthday`、`label`、`avatar`、`userBannerImage`；`success` | 保存本人资料，保留背景图 |
| GET `user/avatar/preUpload` | 会话 Cookie；`success`、`userAvatarUploadSignedUrl`、`userAvatarFilename` | 获取头像上传地址与图片 ID |
| GET `user/checkUsername` | query `username`；`isAvailableUsername` | 用户名可用性 |
| GET `user/existsCheck` | query `email`；`exists` | 邮箱查重 |
| POST `user/checkInvitationCode` | `invitationCode`；`isAvailableInvitationCode` | 邀请码检查 |
| POST `user/sendGeneralEmailVerificationCode` | `email`、`clientLanguage`、`mailTemplate`、`exclusiveBusinessName` | 发送验证码 |
| POST `user/registering` | `email`、`passwordHash`、`passwordHint`、`verificationCode`、`invitationCode`、`username`、`userNickname` | 注册并获取会话 |
| POST `user/forgot/password` | `email`、`newPasswordHash`、`verificationCode` | 设置新密码 |
| GET `user/checkUserHave2FAByUUID` | 会话 Cookie；`success`、`have2FA`、`type`、`totpCreationDateTime` | 当前账号的验证器状态 |
| POST `user/sendGeneral2FAEmailVerificationCode` | 会话 Cookie；`clientLanguage`、`mailTemplate`、`exclusiveBusinessName` | 当前邮箱的安全操作验证码 |
| POST `user/update/email` | `oldEmail`、`newEmail`、`passwordHash`、`changeEmailVerificationCode`、`changeEmailNewEmailVerificationCode` | 修改邮箱 |
| POST `user/update/password` | `oldPasswordHash`、`newPasswordHash`、`verificationCode` | 修改密码 |
| POST `user/createEmailAuthenticator` | 会话 Cookie；`success`、`isExists` | 启用邮箱验证 |
| DELETE `user/deleteUserEmailAuthenticator` | `passwordHash`、`verificationCode` | 关闭邮箱验证 |
| POST `user/createTotpAuthenticator` | 会话 Cookie；`success`、`isExists`、`result.otpAuth` | 创建待确认的 TOTP |
| POST `user/confirmUserTotpAuthenticator` | `clientOtp`、`otpAuth`；`result.backupCode`、`result.recoveryCode` | 确认 TOTP 并返回一次性恢复信息 |
| DELETE `user/deleteTotpAuthenticatorByTotpVerificationCodeController` | `passwordHash`、`clientOtp` | 用验证码或备用码解绑 TOTP |

**密码摘要**与 Cerasus 的 `generateHash` 一致：对 UTF-8 原密码计算 SHA-256，输出小写 64 位十六进制。不能 trim 原密码，也不能把 DTO 里旧的 bcrypt 注释当作契约。摘要等价于敏感凭据，只在内存保留，不得记录。

**验证码邮件**：

| 用途 | `mailTemplate` | `exclusiveBusinessName` |
| --- | --- | --- |
| 登录 | `SendLoginVerificationCode` | `login` |
| 注册 | `SendRegistrationVerificationCode` | `registration` |
| 找回密码 | `SendResetPasswordVerificationCode` | `forgot-password` |

- 邮件语言由 `verificationMailLanguage` 从应用 Locale 映射：中文（含繁体）为 `zh-Hans-CN`，日语为 `ja`，其余为 `en-US`。Rosales 的繁体中文邮件模板目前为空，请求 `zh-Hant-TW` 会因读不到标题而发送失败，模板补齐前不使用。
- Rosales 的发送冷却按邮箱跨业务共用。Repository 用单调时钟记录 60 秒的截止值并串行发送；服务端返回的冷却或每日发送／校验上限优先于 `success`。客户端倒计时只是展示，不代替服务端校验。
- `VerificationCodeFormat` 集中定义三种格式：六位数字的邮箱验证码、六位字母数字的备用码和二十四位的恢复码。输入组件、认证、安全设置共用它的长度与字符规则。

### 网络与错误

[ApiClient](../../app/src/main/kotlin/moe/kirakira/core/network/ApiClient.kt) 由宿主的 `AppDependenciesViewModel` 创建并注入各 Repository，认证与内容请求共享同一个 OkHttp 客户端。

- **超时与上限**：连接 15 秒、读写 20 秒、完整调用 30 秒，响应上限 1 MiB。
- **取消**：通过 callback 与 `suspendCancellableCoroutine` 把协程取消传到 HTTP Call。解析和散列在 Default 调度器，存储在 IO 调度器。
- **禁用的能力**：自动重试、重定向、缓存、请求日志和全局 CookieJar。路径是代码常量，query 通过 builder 编码，每次请求明确绑定凭据快照。头像请求使用独立客户端，不共享会话。

`AuthRepository.withRequestSession` 是内容、历史、账号设置和安全请求的统一入口：它捕获账号快照，检查预期的 revision、取消状态和结果归属，并处理明确的会话失效。默认要求登录，内容仓库按接口约定显式允许游客。这个入口不重放请求，代码块内也不得发布新的 revision——资料保存和安全操作在本地完成阶段自己发布身份，否则操作自身推进的版本会被误判为账号切换。账号库初始化或恢复读取失败时保留当前 revision，恢复成功后继续递增，避免同一进程里复用旧请求的版本号。

**错误分类**：

| 来源 | 分类 |
| --- | --- |
| HTTP 401 | 会话失效 |
| HTTP 429 | 限流 |
| HTTP 5xx | 服务故障 |
| `InterruptedIOException` | 超时 |
| 其他 IO 错误 | 网络失败 |
| HTTP 2xx 但 `success=false` 或缺少必要字段 | 业务拒绝／响应异常 |

- IO 错误统一经 `ApiFailure.fromIOException` 分类，API 请求和头像上传共用。
- 写操作遇到网络、超时、服务故障或响应异常时，由 `ApiFailure.isUncertainMutation` 标记为“结果不确定”，隐私、安全和视频操作各自据此进入核对流程。
- 获取本人资料时还校验 uuid 与 uid 的一致性。
- 服务器返回的消息不直接显示，统一使用界面字符串资源。账号弹窗对网络和超时使用专属标题与“继续使用”按钮，由 `SessionFeedback` 提供，不改动通用的 `ApiFailure.messageRes()` 映射。

**`/user/self` 的含糊拒绝**：当前 Rosales 对无效 token 和数据库故障都可能返回 HTTP 200 加 `success=false`，缺少可靠的业务错误码。因此这类拒绝只标记“需要重新验证”并撤下该账号的当前身份，本机 token 保留供用户重试，不能宣称它一定已过期。只有明确的 401 才清除 token，账号条目仍保留。网络错误和超时保留离线资料与会话。后端提供稳定的错误码后应更新这一映射，而不是去匹配中文的 `message`。

### 会话存储与切换

#### 文件与格式

[SessionStore](../../app/src/main/kotlin/moe/kirakira/data/auth/SessionStore.kt) 把 token 与最小账号资料以 UTF-8 JSON 原子保存到 `noBackupFilesDir/sessions.json`。正常的读取、写入、切换、移除和清除都不访问 Keystore。

- 使用 Android 的[应用私有存储](https://developer.android.com/privacy-and-security/security-tips#internal-storage)和 AndroidX Core 的 [AtomicFile](https://developer.android.com/reference/androidx/core/util/AtomicFile)。各 Android 版本统一先写 `.new` 再重命名，避免旧系统的首次写入直接改动主文件。
- [SessionCodec](../../app/src/main/kotlin/moe/kirakira/data/auth/SessionCodec.kt) 沿用 `StoredSessions` 的 v1 JSON 结构，校验版本、HTTPS 环境、UUID 去重、账号身份、token、bootstrap hint 和当前账号的一致性，严格解码 UTF-8，读写限制在 1 MiB。未知字段允许向前兼容。
- 登录响应、会话编解码和 Cookie 构造共用 [SessionFieldRules](../../app/src/main/kotlin/moe/kirakira/data/auth/SessionFieldRules.kt) 的 UUID、token 与 bootstrap hint 基础校验。
- 库记录版本和 API 根地址，不跨环境发送凭据。账号按 UUID 去重，包含最小资料、token 和“需要重新登录”标记；界面只拿到不含 token 的 `SessionState`。

#### 读取

读取先检查主文件和 `.bak` 备份，通过 `AtomicFile.openRead()` 恢复旧式备份。

- 主文件和备份都不存在时视为没有账号，返回空状态，不预先写入文件；只剩 `.new` 说明首次写入未提交，下次写入会覆盖它。
- 读取失败或尚未完成初始化时禁止写入，保留原文件，并提供重试和“确认清除”入口。
- 0.1 和 0.2 的加密账号库 `sessions.v1.enc` 不再读取，从这两个版本直接升级的用户需要重新登录；旧文件和 Keystore 密钥留在设备上，不会被访问。

#### 写入与核验

写入显式同步文件描述符，只有写入或同步失败时才调用 `failWrite`。`finishWrite` 和提交后的核验位于回滚分支之外：核验失败不会回滚或删除已提交的主文件。核验通过与启动相同的 `openRead()` 路径回读并比较字节，同时检查 `.bak` 已恢复，避免只读主文件而漏掉备份恢复失败。

单实例 Mutex 串行化磁盘操作，Repository 在写入成功后才发布身份。进入 `finishWrite` 之前关闭写入许可并设置 `SessionPersistence.requiresReload`，只有核验成功才恢复。提交后核验失败或清除账号文件失败时，Repository 撤下内存身份和旧账号快照并推进 revision，防止旧的内存状态覆盖文件。用户重试时重新初始化并读取实际文件，不重放原来的账号操作。

账号库的后端地址与当前构建不匹配时，只向当前环境返回空账号状态，磁盘保留原后端的数据；首次实际保存当前环境的会话时才替换账号库。

#### 清除与备份

用户确认清除账号后，删除主文件以及 `.bak`、`.new`，逐一确认都已删除；之后的读取返回空状态。

新旧账号文件都位于系统排除备份的 `noBackupFilesDir`；`backup_rules.xml` 和 `data_extraction_rules.xml` 只允许非敏感设置参与备份。参考[自动备份规则](https://developer.android.com/identity/data/autobackup)。

[SessionStorageErrors](../../app/src/main/kotlin/moe/kirakira/data/auth/SessionStorageErrors.kt) 保留已分类的错误和协程取消。只有异常链里存在系统的 `ENOSPC` 才提示空间不足，其余按数据格式异常或通用读写错误处理。上层拿不到底层异常消息、原因链或凭据。

#### 切换、移除与登出

状态变更由 Mutex 串行化。新身份的写盘和内存发布组成不可取消的短事务，网络阶段仍可取消。

- 切换前先请求 `/user/self`，完成后才发布新的当前身份；切到游客不发请求。
- 移除或登出只清除指定账号，其他账号保留。
- 密码重置成功后，本机匹配邮箱的会话被标记为需要重新登录。

## 保护边界与服务端限制

- **没有应用层加密**。新账号文件不提供应用层加密、密码学完整性或回滚检测，保护依靠应用沙盒和系统存储保护；它无法阻止拥有 Root 或应用进程控制权的主体读取、修改凭据。结构校验不等同于密码学完整性校验。这个方案使用的是 Android 官方的私有存储，但并非 Google 对“不额外加密 token”的专项推荐。
- **只存内存的材料**。密码、摘要、验证码和 TOTP 材料只存流程内存。临时读写的字节数组使用后清零，但 Kotlin／Java 字符串和库内部的副本无法保证立即清除。
- **摘要不是加密**。密码的 SHA-256 是现有服务端协议要求的摘要，不能替代加密或服务端慢哈希，仍然依赖 HTTPS 和服务端的凭据保护。
- **登出不撤销 token**。Rosales 的 `GET /user/logout` 只清除浏览器 Cookie，没有服务端 token 撤销。Android 没有共享 CookieJar，直接移除本地凭据即可离线登出，但不能宣称服务器 token 已撤销。
- **没有刷新令牌**。后端未提供 refresh token、过期时间或设备会话管理，客户端不伪造这些能力；失效的账号通过认证页重新登录。
