# 实现说明

[返回项目首页](../README.md) · [功能现状](features.md) · [开发指南](development.md) · [贡献规范](../CONTRIBUTING.md)

实现细节按专题维护。修改时更新所属专题；开发约束维护在贡献指南，页面行为与限制维护在功能现状。

## 专题导航

| 专题 | 内容 |
| --- | --- |
| [导航与启动](implementation/navigation.md) | 返回栈、页面转场、主界面切换与启动资源 |
| [内容浏览](implementation/content.md) | 搜索、标签、视频列表、关注列表与分页布局 |
| [播放、图片与弹幕](implementation/media.md) | 播放会话、全屏与小窗、续播、图片查看和弹幕输入渲染 |
| [主题与投影](implementation/theme.md) | 语义配色、颜色偏好、弹层背景与投影参数 |
| [共享组件接入](implementation/components.md) | 内容状态、连接列表、刷新、顶栏与账号流程布局 |
| [认证与会话](implementation/auth.md) | 认证状态、系统凭据、接口契约、错误处理与本机会话存储 |
| [账号设置](implementation/account-settings.md) | 资料编辑、安全与二步验证、隐私、屏蔽规则及邀请码 |

## 章节索引

以下章节入口保留原有锚点，正文由对应专题维护。

## 统一内容状态

[统一内容状态实现](implementation/components.md#统一内容状态)

### 圆形加载器

[圆形加载器实现](implementation/components.md#圆形加载器)

## 导航与状态管理

[导航与状态管理实现](implementation/navigation.md#导航与状态管理)

### 图片查看与导出

[图片查看与导出实现](implementation/media.md#图片查看与导出)

### 主界面切换与底栏动效

[主界面切换与底栏动效实现](implementation/navigation.md#主界面切换与底栏动效)

## 视频页与资料页分页

[视频页与资料页分页实现](implementation/content.md#视频页与资料页分页)

## 视频列表布局

[视频列表布局实现](implementation/content.md#视频列表布局)

## 关注与粉丝列表

[关注与粉丝列表实现](implementation/content.md#关注与粉丝列表)

## 视频数据与播放器

[视频数据与播放器实现](implementation/media.md#视频数据与播放器)

### 播放器首次加载背景

[播放器首次加载背景实现](implementation/media.md#播放器首次加载背景)

### 历史记录与续播

[历史记录与续播实现](implementation/media.md#历史记录与续播)

## 主题实现

[主题实现实现](implementation/theme.md#主题实现)

### 成功语义色

[成功语义色实现](implementation/theme.md#成功语义色)

### 配色生成

[配色生成实现](implementation/theme.md#配色生成)

### 弹层背景

[弹层背景实现](implementation/theme.md#弹层背景)

### 选色与持久保存

[选色与持久保存实现](implementation/theme.md#选色与持久保存)

### 栏面阴影

[栏面阴影实现](implementation/theme.md#栏面阴影)

### 按钮动态阴影

[按钮动态阴影实现](implementation/theme.md#按钮动态阴影)

### 单选框动态阴影

[单选框动态阴影实现](implementation/theme.md#单选框动态阴影)

### 彩色卡片阴影

[彩色卡片阴影实现](implementation/theme.md#彩色卡片阴影)

## 图标划线过渡

[图标划线过渡实现](implementation/components.md#图标划线过渡)

## 认证状态

[认证状态实现](implementation/auth.md#认证状态)

### 分步输入与系统凭据

[分步输入与系统凭据实现](implementation/auth.md#分步输入与系统凭据)

## API 与会话

[API 与会话实现](implementation/auth.md#api-与会话)

### 视频搜索

[视频搜索实现](implementation/content.md#视频搜索)

### 视频简介

[视频简介实现](implementation/content.md#视频简介)

### 视频标签

[视频标签实现](implementation/content.md#视频标签)

### 接口契约

[接口契约实现](implementation/auth.md#接口契约)

### 本人资料编辑

[本人资料编辑实现](implementation/account-settings.md#本人资料编辑)

### 设置表单操作区

[设置表单操作区实现](implementation/account-settings.md#设置表单操作区)

### 账号设置管理

[账号设置管理实现](implementation/account-settings.md#账号设置管理)

### 网络与错误

[网络与错误实现](implementation/auth.md#网络与错误)

### 会话存储与切换

[会话存储与切换实现](implementation/auth.md#会话存储与切换)

## 共享组件接入

[共享组件接入实现](implementation/components.md#共享组件接入)

### 顶部导航按钮定位

[顶部导航按钮定位实现](implementation/components.md#顶部导航按钮定位)

### 可复用 UI 参数示例

[可复用 UI 参数示例实现](implementation/components.md#可复用-ui-参数示例)

### 毛玻璃应用栏

[毛玻璃应用栏实现](implementation/components.md#毛玻璃应用栏)

### 黑底毛玻璃按钮

[黑底毛玻璃按钮实现](implementation/components.md#黑底毛玻璃按钮)

### 顶栏淡色图标底纹

[顶栏淡色图标底纹实现](implementation/components.md#顶栏淡色图标底纹)

### 可选的可折叠大标题栏

[可选的可折叠大标题栏实现](implementation/components.md#可选的可折叠大标题栏)

### 可复用的顶栏搜索

[可复用的顶栏搜索实现](implementation/components.md#可复用的顶栏搜索)

## 启动器图标

[启动器图标实现](implementation/navigation.md#启动器图标)

## 启动动画

[启动动画实现](implementation/navigation.md#启动动画)

## 弹幕与输入区

### 弹幕发送样式

[弹幕发送样式实现](implementation/media.md#弹幕发送样式)

### 弹幕显示与设置

[弹幕显示与设置实现](implementation/media.md#弹幕显示与设置)

### 评论与弹幕颜文字

[评论与弹幕颜文字实现](implementation/media.md#评论与弹幕颜文字)
