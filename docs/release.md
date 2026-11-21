# 发版

[返回项目首页](../README.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

[Android distribution](../.github/workflows/android-distribution.yml) 使用同一套 Release 构建和签名流程。每次 `main` 推送触发的构建成功后，Releases 中的 [CI build](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/releases/tag/ci) 预发行会更新到最新成功构建。CI 的 APK 文件名包含版本号、运行序号和提交 SHA 的前 7 位，例如 `KIRAKIRA-0.4-ci-12-a659b8b.apk`。每次运行的 APK 和同次生成的 `mapping.txt` 可从 Actions 的 **Artifacts** 下载，保留 90 天。

工作流从 Google 下载固定版本的 Android SDK 命令行工具，校验 SHA-256 后把 SDK Platform 和 Build Tools 安装到 runner 的临时目录。Release 构建与签名使用同一 SDK，不依赖 runner 预装的 `sdkmanager`。

正式版与 CI 版共享应用 ID 和签名。分发包使用 `3 + GITHUB_RUN_NUMBER` 作为 `versionCode`，其中 3 是已发布的 `0.3` APK 的版本号。CI 的版本名包含运行序号，例如 `0.4-ci.12`。如果手机已安装构建序号更高的 CI，需要等序号更高的正式版才能覆盖更新。两个渠道使用同一个工作流计数。迁移工作流前应确保新计数生成的版本号高于已分发的 APK。

## 首次配置

1. 保护 `main`，要求通过审核的 PR 才能合并。在仓库 **Settings → Rules → Rulesets** 启用独立的 tag 规则集，匹配 `[0-9]*.[0-9]*`，限制创建、更新和删除，仅允许发布维护者绕过。`ci` 不属于正式版本规则；不要给发布工作流绕过正式版本规则的权限。
2. 在仓库 **Settings → Environments** 新建 `android-signing`。选择 **Selected branches and tags**，仅允许 `main` 分支和 `[0-9]*.[0-9]*` 标签。tag 规则集必须覆盖环境允许的全部标签，而不只是当前工作流匹配的两段或三段数字版本。工作流从触发它的 tag 读取，仅限制名称不能阻止未经审核的工作流访问签名密钥。
3. 确认前两项已生效，并核对所有已存在的匹配 tag 都指向经审核进入 `main` 的提交。然后使用发布 `0.3` 的原密钥，仅在该 Environment 中设置以下 Secrets，不要设为仓库级 Secrets。Base64 编码应为一行。

   | Secret | 值 |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | 原 keystore 文件的 Base64 |
   | `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
   | `ANDROID_KEY_ALIAS` | 签名密钥别名 |
   | `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

4. 下载 [0.3 APK](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/releases/tag/0.3)，用 `apksigner verify --print-certs` 核对证书指纹，用 `aapt2 dump badging` 核对应用 ID 和版本号。工作流已固定该 APK 的公开证书 SHA-256 指纹，签名不匹配时该次构建会失败。
5. 确认仓库与组织的 Release 设置允许替换 `ci` 附件，tag 规则允许工作流移动 `ci` tag。发布任务使用内置 `GITHUB_TOKEN`。首次推送 `main` 后，核对 Actions 产物和 CI 预发行中的 APK，再确认预发行记录的提交与 `ci` tag 一致。

## 发布正式版

1. 把 [应用构建配置](../app/build.gradle.kts) 的 `appVersionName` 更新为版本号，例如 `0.4`。推送到 `main` 并等该提交的 CI 完成。
2. 在该提交创建并推送同名 tag。

   ```sh
   git tag -a 0.4 -m "0.4"
   git push origin refs/tags/0.4
   ```

3. 在 Releases 打开生成的草稿，核对 `KIRAKIRA-0.4-release.apk`，补充中文说明并点击 **Publish release**。从对应 Actions 产物长期保存同一 APK 的 `mapping.txt`，可用 APK 的 SHA-256 对照 Release 附件和构建产物。重跑会补齐缺失的草稿附件，保留已有 APK 和说明。
