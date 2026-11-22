# 实现说明

[返回项目首页](../README.md) · [功能现状](features.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

实现说明按专题维护，记录代码本身读不出来的内容：状态归属与数据流、取舍理由、平台限制与规避方式、接口契约和持久化格式。尺寸、配色与参数取值以代码为准；必须遵守的界面规则在[界面规范](ui-guidelines.md)，页面行为在[功能现状](features.md)。

| 专题 | 内容 |
| --- | --- |
| [导航与启动](implementation/navigation.md) | 返回栈、页面转场、预测性返回、主界面切换与启动动画 |
| [内容浏览](implementation/content.md) | 搜索、标签、视频列表、关注列表、分页布局与读取失败处理 |
| [播放、图片与弹幕](implementation/media.md) | 播放会话、全屏与小窗、续播、图片查看和弹幕输入渲染 |
| [主题与投影](implementation/theme.md) | 配色生成、颜色偏好的保存、弹层背景与投影 |
| [共享组件接入](implementation/components.md) | 内容状态、连接列表、刷新、顶栏、搜索栏与账号流程布局 |
| [认证与会话](implementation/auth.md) | 认证状态、系统凭据、接口契约、错误处理与本机会话存储 |
| [账号设置](implementation/account-settings.md) | 资料编辑、安全与二步验证、隐私、屏蔽规则及邀请码 |
