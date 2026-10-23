## 变更与原因

<!-- 描述解决的问题、最终行为和关键取舍。 -->

## 关联问题

<!-- 填写 issue 链接；没有则写“不适用”。 -->

## 构建与检查结果

<!-- 代码或构建变更只要求 assembleDebug；纯文档修改只检查内容与链接。测试和额外检查仅在用户明确要求时补充，不要求提供测试结果。不能将未运行写成通过。 -->

| 检查 | 结果与说明 |
| --- | --- |
| `./gradlew :app:assembleDebug`（代码或构建变更） | |
| 内容与链接检查（纯文档变更） | |

## 界面与交互

<!-- 简述 UI 变更；截图或录屏仅在用户要求时提供。非 UI 变更写“不适用”。 -->

## 风险与规范例外

<!-- 写明兼容性、依赖 / 权限变化、迁移方案或规范例外及理由；没有则写“无”。 -->

## 提交前自查

<!-- 只勾选已完成的检查；不适用项在上方说明。详细参数以 CONTRIBUTING.md 为准。 -->

- [ ] 已遵循 [技术选型与架构](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#技术选型与架构)及 [Kotlin 风格](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#kotlin-风格与命名)，完成适用的格式化与导入清理。
- [ ] 已遵循 [Compose 与界面规范](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#compose-与界面规范)，核对涉及的 Material 3 Expressive 组件、按钮按压形变与固定轮廓场景、列表区域标题排版与数量摘要格式、Material Design 2 风格配色与高度阴影、KIRAKIRA 彩色阴影，以及 Modifier 工厂与链、状态展示、表单、反馈动效和 Insets 规则。
- [ ] 新文案已同步中英文，布局兼顾长文本、字体缩放与无障碍；不内置 Demo 内容，预览使用空表单、游客账号或空列表。
- [ ] 涉及认证或 API 时，已遵循 [认证状态约束](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#认证状态约束)和 [API 与会话规范](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#api-与会话接入规范)，保留账号隔离、错误与取消处理、私有账号文件及备份排除边界；涉及存储迁移时说明旧库恢复失败后的重新登录行为。
- [ ] 已按 [构建与检查](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#构建与检查)记录实际执行结果，未自行增加或运行用户未要求的测试和额外检查，未将历史记录或未运行项写为通过。
- [ ] 已按 [文档维护分工](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#文档维护)同步所属专题、摘要及配置，检查移动后的链接与锚点。
- [ ] 变更聚焦，未混入凭据、个人路径、生成文件或无关工具链升级；规范例外已说明原因。
