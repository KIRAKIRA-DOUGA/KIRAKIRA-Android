# 认证与会话

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护认证状态、系统凭据、接口契约、错误处理与加密会话。开发约束以贡献指南为准。

- [认证状态](#认证状态)
  - [分步输入与系统凭据](#分步输入与系统凭据)
- [API 与会话](#api-与会话)
  - [接口契约](#接口契约)
  - [网络与错误](#网络与错误)
  - [会话存储与切换](#会话存储与切换)

## 认证状态

认证功能位于 [feature/auth](../../app/src/main/java/moe/kirakira/feature/auth)：

| 层次                                      | 职责                                   |
|-----------------------------------------|--------------------------------------|
| `AuthScreen` / `AuthForm` / `LoginForm` | 展示登录、2FA、注册、密码找回步骤，上报操作              |
| `AuthPage` / `AuthNavigation`           | 外层条目共享表单状态，内部步骤返回栈、转场、键盘、隐藏密码与成功返回事件 |
| `AuthViewModel`                         | 条目内表单、校验、异步提交、验证码冷却展示                |
| `SessionViewModel`                      | 应用级唯一 Repository、启动加载／资料刷新、切换／移除／登出  |
| `AuthRepository`                        | 认证编排、SHA-256、账号去重、冷却、会话事务和领域状态       |
| `AuthApi` / `SessionStore`              | 私有 DTO 与 HTTP 协议／加密原子存储              |

`ActivityNavDisplay` 在可保存状态装饰器之后添加 `rememberViewModelStoreNavEntryDecorator`，认证 ViewModel 绑定外层 `AuthRoute` 条目，旋转时保留、出栈后释放。`AuthRoute` 只携带可选邮箱以便重新登录；内部 `AuthStepRoute` 只携带步骤枚举，不包含表单数据。各步骤通过 `rememberNavBackStack` 与嵌套 `ActivityNavDisplay` 展示，复用普通转场和预测性返回。子分发器连接外层 Navigation Event 分发器，仅在认证页为当前页面时启用；子页面重置继承的页面变换，避免外层退出手势被重复应用。

`AuthStep.previousStep` 统一定义内部路径：注册按资料、凭据、邀请码、验证逐级返回；登录 2FA 返回邮箱密码；重置密码和 TOTP 找回说明返回邮箱输入。顶部返回箭头与系统返回使用同一路径，根登录页通过系统返回退出认证，所有步骤均不提供关闭按钮。ViewModel 校验后推进步骤，导航保留路径的公共前缀，各条目独立保存滚动位置。转场中的离开页面使用仅驻留内存的表单快照，避免提前渲染下一步；退出或返回会取消当前请求，旧请求的收尾不再改写新页面状态。

仅邮箱通过 `SavedStateHandle` 恢复。认证内部可保存状态以 ViewModel 生命周期内的随机键隔离：旋转沿用该键，进程重建生成新键并回到登录入口，不恢复密码、验证码及依赖这些输入的步骤。密码显隐使用条目内的普通 `remember`，步骤切换和 UI 重建后隐藏。

提交发生在 `viewModelScope` 中；成功状态仅在 RESUMED 时通过页面回调返回，宿主检查当前路由，避免重复出栈。认证会话只在加密存储成功后发布。如果认证已成功但保存失败，保留内存待保存凭据并进入保存重试步骤，避免再次注册或消耗验证码；关闭页面会释放待保存凭据。密码重置已被服务器确认、但本机会话清理写盘失败时，也进入单独的清理重试步骤，不重发密码重置请求。API 资料暂时不可用时保留登录返回的邮箱和 UID，不把资料读取失败误作需要再次注册。

资料解析或身份一致性检查失败时拒绝保存，也不提供保存未验证身份的重试。已解析的资料随待保存会话留在内存，写盘重试不再发送资料请求；旧请求完成不可取消的写盘阶段后，仅清除属于自身的待处理记录，避免影响后来开始的认证或密码重置。待保存记录通过内存中的 `AuthFlowOwner` 绑定条目，旧页面退出动画结束后的清理不能删除新页面的数据。成功提交后禁止继续编辑表单或切换认证分支，直至页面关闭。历史用例及验证边界见[认证离线测试](../auth-testing.md)；其中 UI 记录对应文内标明的验证日期。

### 分步输入与系统凭据

注册新增 `REGISTER_INVITATION`：凭据步骤验证确认密码、密码提示和邮箱唯一性；邀请码步骤验证格式与可用性，再按邮箱冷却发码并前进。返回仅清空验证码，保留流程中的其他草稿；离开整个分支则清除秘密。`fieldErrors` 与带编号的 `focusField` 指向具体字段，Navigation 3 子条目等到 RESUMED、可编辑和下一帧布局后请求焦点，一次消费；返回没有新的焦点请求。未知业务拒绝仍使用页面错误，不把服务器的含糊 `success=false` 猜作某个字段错误。

账号表单的组件、间距、错误图标与底栏接入见[账号流程布局](components.md#账号流程布局)。

[PasswordCredentialGateway](../../app/src/main/java/moe/kirakira/core/credentials/PasswordCredentialGateway.kt) 提供 get / save / clearSession，系统实现固定使用 AndroidX Credentials 与 Play Services 桥接。登录页面不再发起 get 操作，已保存密码仅通过输入框的 Compose Autofill 语义提供，填充后由用户提交。保留的获取接口使用 `GetPasswordOption` 关闭自动选中，支持邮箱筛选并在返回后核对邮箱；不调用 View 专用联动接口。只有完整生产根地址构建的 `SYSTEM_CREDENTIALS_ENABLED` 为 true，其余使用禁用网关；测试直接注入替身。

`AuthCredentialOperation` 保留带递增编号的 Get / Save 内存操作，当前页面仅发起认证成功或密码重置后的 Save。外层 `AuthPage` 在当前 Activity RESUMED 后领取，Activity 身份变化或组合销毁时撤销领取；回调必须同时匹配操作编号及宿主身份，并检查协程未被取消。旧 Activity 的延迟结果不能影响当前流程，系统弹窗暂时暂停 Activity 不会重新发起操作。登录页进入、返回、旋转、重组及字段聚焦均不主动调用凭据获取网关；系统 Autofill 建议由提供者根据字段语义展示。

认证 ViewModel 保留密码是否源自未经修改的系统选择。仅手输或修改后的密码在完整认证、本地写盘成功后生成 `CreatePasswordRequest`；二步验证前不保存，存储失败仅保留短暂 `PasswordDraft` 供本地重试。密码重置等待本地清理完成。保存阶段为 FINISHING，取消或失败均进入成功／重置完成状态；失败显示非阻断提示。流程完成、退出或取消释放待保存对象，秘密类不生成包含密码的 toString。库内部和 JVM 字符串副本不能保证即时擦除。

所有显式前进、返回、退出和系统保存之前取消 Compose Autofill 会话，保留邮箱／用户名、Password / NewPassword 语义。系统保存的持久化边界是用户选择的密码提供者，应用不将原密码写入任何存储。未配置 Digital Asset Links，不支持本轮范围外的跨网站密码共享及 Passkey。

会话 UI 状态新增 `SessionOperation(type, targetUuid)`，区分初始化、切换、移除与本机重置。操作串行执行并在写盘期间保留类型与目标；成功提交之前继续选中原账号。账号行采用居中对齐的布局，游客平时仅显示名称，普通账号显示名称与副标题，长文字单行省略；切换期间保留原有文字与布局，仅显示尾部加载器，「正在切换」通过无障碍状态描述提供；移除状态复用副标题行。loading、单选与删除按钮共用 48dp 尾部槽位，列表不插入顶部加载项或空白状态行。“添加账号”独立成组。错误携带目标 UUID，重试闭包绑定原操作。游客切换、移除当前账号或登出之后尽力调用 `clearCredentialState`，不删除提供者密码；清理失败不撤销本地操作。

参考：[官方密码接入](https://developer.android.com/identity/passwords)、[Compose Autofill](https://developer.android.com/develop/ui/compose/text/autofill)、[Credentials 版本记录](https://developer.android.com/jetpack/androidx/releases/credentials)。

## API 与会话

### 接口契约

流程对照同级 Cerasus 项目的 `components/Login/LoginWindow.vue`、`composables/api/User/UserController.ts`、`utils/hash.ts`；端点与字段核对同级 Rosales 的 `src/route/router.ts`、`src/controller/UserControllerDto.ts`、`src/service/UserService.ts`。这些契约来自仓库源码对照，不表示已执行生产账号联调。

| 请求                                                                    | 请求字段／关键响应                                                                                                         | 用途                 |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|--------------------|
| GET `user/checkUserHave2FAByEmail`                                    | query `email`；`success`, `have2FA`, `type`                                                                        | 判断无验证器／email／totp  |
| POST `user/login`                                                     | `email`, `passwordHash`, `clientOtp` 或 `verificationCode`；`success`, `UUID`, `uid`, `token`                       | 密码及可选 2FA 登录       |
| POST `user/self`                                                      | 请求 Cookie `uuid`, `uid`, `token`；`success`, `result`                                                              | 校验会话并获取自己资料        |
| POST `user/update/info`                                               | `username`, `userNickname`, `signature`, `gender`, `userBirthday`, `label`, `avatar`, `userBannerImage`；`success` | 保存本人资料，保留背景图       |
| GET `user/avatar/preUpload`                                           | 会话 Cookie；`success`, `userAvatarUploadSignedUrl`, `userAvatarFilename`                                            | 获取头像上传地址与图片 ID     |
| GET `user/checkUsername`                                              | query `username`；`isAvailableUsername`                                                                            | 用户名可用性             |
| GET `user/existsCheck`                                                | query `email`；`exists`                                                                                            | 邮箱查重               |
| POST `user/checkInvitationCode`                                       | `invitationCode`；`isAvailableInvitationCode`                                                                      | 邀请码检查              |
| POST `user/sendGeneralEmailVerificationCode`                          | `email`, `clientLanguage`, `mailTemplate`, `exclusiveBusinessName`                                                | 验证码发送              |
| POST `user/registering`                                               | `email`, `passwordHash`, `passwordHint`, `verificationCode`, `invitationCode`, `username`, `userNickname`         | 注册并获取会话            |
| POST `user/forgot/password`                                           | `email`, `newPasswordHash`, `verificationCode`                                                                    | 设置新密码              |
| GET `user/checkUserHave2FAByUUID`                                     | 会话 Cookie；`success`, `have2FA`, `type`, `totpCreationDateTime`                                                    | 当前账号验证器状态          |
| POST `user/sendGeneral2FAEmailVerificationCode`                       | 会话 Cookie；`clientLanguage`, `mailTemplate`, `exclusiveBusinessName`                                               | 当前邮箱安全操作验证码        |
| POST `user/update/email`                                              | `oldEmail`, `newEmail`, `passwordHash`, `changeEmailVerificationCode`, `changeEmailNewEmailVerificationCode`      | 修改邮箱               |
| POST `user/update/password`                                           | `oldPasswordHash`, `newPasswordHash`, `verificationCode`                                                          | 修改密码               |
| POST `user/createEmailAuthenticator`                                  | 会话 Cookie；`success`, `isExists`                                                                                   | 启用邮箱验证             |
| DELETE `user/deleteUserEmailAuthenticator`                            | `passwordHash`, `verificationCode`                                                                                | 关闭邮箱验证             |
| POST `user/createTotpAuthenticator`                                   | 会话 Cookie；`success`, `isExists`, `result.otpAuth`                                                                 | 创建待确认 TOTP         |
| POST `user/confirmUserTotpAuthenticator`                              | `clientOtp`, `otpAuth`；`result.backupCode`, `result.recoveryCode`                                                 | 确认 TOTP 并返回一次性恢复信息 |
| DELETE `user/deleteTotpAuthenticatorByTotpVerificationCodeController` | `passwordHash`, `clientOtp`                                                                                       | 使用验证码／备用码解绑 TOTP   |

密码传输格式与 Cerasus `generateHash` 一致：UTF-8 原密码的 SHA-256，小写 64 位十六进制，不能 trim 原密码或把 DTO 中旧的 bcrypt 注释当作契约。摘要依然等价于敏感凭据，只在内存保留，不得记录。

邮件模板／业务名分别是 `SendLoginVerificationCode` / `login`、`SendRegistrationVerificationCode` / `registration`、`SendResetPasswordVerificationCode` / `forgot-password`；语言为 `zh-Hans-CN` 或 `en-US`。Rosales 的冷却按邮箱跨业务共用，Repository 使用单调时钟记录 60 秒截止值并串行发送；服务端冷却或每日发送／校验上限优先于 `success`。邮箱验证码为六位数字；TOTP 输入允许备用／恢复码。注册用户名与昵称均为必填，字符策略与 Cerasus `assets/pomsky/username.pom` 对齐，表单长度上限为 20 个 UTF-16 单元。

### 网络与错误

[ApiClient](../../app/src/main/java/moe/kirakira/core/network/ApiClient.kt) 统一使用 OkHttp 与 serialization，连接 15 秒、读写 20 秒、完整调用 30 秒，响应上限 1 MiB。通过 callback 与 `suspendCancellableCoroutine` 绑定取消，解析和散列使用 Default，存储使用 IO。认证请求关闭自动重试、重定向和缓存，不安装日志或全局 CookieJar；API 路径为代码常量，query 通过 builder 编码，单次请求明确绑定凭据快照。头像请求不共享会话。

HTTP 401、429、5xx 分别映射失效、限流、服务故障，其余失败与网络／超时／响应格式异常分开。HTTP 2xx 仍需检查业务 `success` 和必要字段；获取自己资料还校验 uuid 与 uid。服务器消息不直接显示，统一使用 UI 字符串资源。

当前 Rosales 的 `/user/self` 对无效 token 和数据库故障都可能返回 HTTP 200 + `success=false`，缺少可靠业务错误码。因此此类拒绝标记需要重新验证、撤下该账号当前身份，但保留加密 token 供用户重试；不能宣称一定已过期。明确 401 才清除 token，账号条目保留。网络和超时保留离线资料及会话。后续后端提供稳定错误码后应更新这一映射，而不是匹配中文 `message`。

`SessionFeedback` 为 `NETWORK` 与 `TIMEOUT` 使用账号弹窗专属的中英文标题、共用保留账号说明及“继续使用”按钮，不修改通用 `ApiFailure.messageRes()` 映射。目标账号名称与正文间隔 8dp；关闭按钮、系统返回及点击弹窗外侧统一调用 `onDismissError`，重试仍调用原操作的回调。其他异常沿用原有文案与按钮，会话状态、API 和存储格式不变。

### 会话存储与切换

[SessionStore](../../app/src/main/java/moe/kirakira/data/auth/SessionStore.kt) 用 Android Keystore 的不可导出 AES-256 密钥、随机 IV 和 AES/GCM/NoPadding 加密完整账号库，通过 `AtomicFile` 写入 `noBackupFilesDir`。读写失败不会退回明文或覆盖为假成功；UI 提供重试及需要确认的本机账号库重置。加密选择参考 [Android 密码学建议](https://developer.android.com/privacy-and-security/cryptography) 与 [Keystore](https://developer.android.com/privacy-and-security/keystore)。

[SessionCipher](../../app/src/main/java/moe/kirakira/data/auth/SessionCipher.kt) 保持既有 v1 格式与密钥别名：12 字节随机 IV、密文、16 字节 GCM 认证标签；每次加密由 Keystore 提供者生成 IV，密钥显式要求随机化加密，不允许调用者指定加密 IV。完整标签验证成功后才解码 UTF-8 JSON，再检查版本、HTTPS 环境、账号身份、token 和当前账号的一致性。读写均限制在 1 MiB，密码摘要计算和会话加解密的临时字节数组使用后清零。

读取先调用 `AtomicFile.openRead()` 恢复已提交的 `.bak`，再进行有界读取；不能仅因主文件缺失就返回空账号库。解密从不创建密钥；读取失败或尚未完成初始化时禁止写入，已存在密文但密钥丢失时也禁止生成新密钥覆盖。重置清理主文件、`.bak`、`.new` 与密钥；单实例互斥锁串行化读写。`backup_rules.xml` 与 `data_extraction_rules.xml` 只允许备份外观设置 `kirakira_settings.xml`，账号库保留在系统始终排除的 `noBackupFilesDir`。参考 [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) 与 [自动备份规则](https://developer.android.com/identity/data/autobackup)。

保护边界：该方案保护本地静态数据的机密性与完整性，不提供旧密文回滚检测，也不能在应用进程已被控制时阻止其调用 Keystore 或读取运行中的凭据。是否由 TEE／StrongBox 保护取决于设备，本项目不把模拟器验证当作硬件安全证明。Kotlin／Java 字符串及库内部副本不能保证立即清除；临时字节数组清零不等于所有内存中不存在凭据。密码的 SHA-256 是现有服务端协议要求的摘要，不是加密或服务端慢哈希的替代，仍依赖 HTTPS 与服务端凭据保护。

账号包含最小资料、token 和需要重登标记，按 UUID 去重；UI 只获得无 token 的 `SessionState`。库记录版本及 API 根地址，不跨环境发送凭据。状态变更由 Mutex 串行化；新身份写盘和内存发布组成不可取消的短事务，网络阶段仍可取消。切换前获取 `/user/self`，完成后才发布新的当前身份；切到游客不发请求。移除／登出清除指定账号，其他账号保留。

Rosales `GET /user/logout` 仅设置清除浏览器 Cookie，没有服务端 token 撤销；Android 没有共享 CookieJar，直接移除本地加密凭据即可离线登出。后端未提供 refresh token、过期时间或设备会话管理，不能伪造这些能力。密码重置成功会将本机匹配邮箱的会话标记为需要重新登录。视频和公开资料通过真实数据层加载；本人主页以 `SessionState.activeProfile` 为初始回退，再读取公开资料、作品和统计。图片查看器支持公开 HTTPS 远程头像，导出使用独立无会话的 OkHttp 客户端下载完整原图，限制大小和跳转，失败与取消时清理临时文件。
