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

- [ ] 已遵循[技术选型与架构](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#技术选型与架构)（包结构与依赖方向）和 [Kotlin 风格](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#kotlin-风格与命名)，完成格式化与导入清理。
- [ ] 界面变更已对照[界面规范](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/docs/ui-guidelines.md)中涉及的小节，优先复用共享组件；新文案已同步英语、简体中文、繁体中文和日语，布局兼顾长文本、字体缩放与无障碍。
- [ ] 读取失败保留已确认的内容，写请求不自动重放；瞬时错误使用 Snackbar，不使用 Toast；不内置演示内容。
- [ ] 涉及认证或 API 时，已遵循[认证状态约束](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#认证状态约束)和 [API 与会话接入规范](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#api-与会话接入规范)；改动已保存数据的格式或位置时，在上方写明迁移方案。
- [ ] 已按[构建与检查](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#构建与检查)如实记录执行过的检查，没有把未运行的检查写成通过。
- [ ] 已按[文档维护](https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android/blob/main/CONTRIBUTING.md#文档维护)更新所属文档：改写所属段落而不是追加，并检查了移动后的链接与锚点。
- [ ] 变更聚焦，没有混入凭据、个人路径、生成文件或无关的工具链升级；规范例外已说明原因。
