# 内容浏览

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文记录内容页如何把请求绑定到账号、搜索与标签的接口和本机历史、视频列表的布局选择、评论分页、资料页与视频页的吸顶分页，以及读取失败和访问失效的处理。播放器、弹幕与图片见[播放、图片与弹幕](media.md)。

- [账号任务与请求归属](#账号任务与请求归属)
- [读取失败与访问失效](#读取失败与访问失效)
- [视频搜索](#视频搜索)
- [视频标签](#视频标签)
- [视频简介](#视频简介)
- [视频列表布局](#视频列表布局)
- [评论与弹幕列表](#评论与弹幕列表)
- [关注与粉丝列表](#关注与粉丝列表)
- [视频页与资料页分页](#视频页与资料页分页)
- [用户页的关系操作与分享](#用户页的关系操作与分享)
- [历史搜索与收藏](#历史搜索与收藏)

## 账号任务与请求归属

内容页的 [ContentViewModel](../../app/src/main/kotlin/moe/kirakira/ui/content/ContentViewModel.kt) 和管理设置的 `SettingsViewModel` 共用 [AccountTasks](../../app/src/main/kotlin/moe/kirakira/core/coroutines/AccountTasks.kt)：当前批次的任务挂在 `viewModelScope` 下的一个 `SupervisorJob` 上。账号重置时取消这批子任务，宿主销毁时取消整棵任务树，不需要手工维护 Job 集合。

- 每个请求在发出前取得账号快照，返回时再核对会话的 revision，旧账号的响应不能覆盖新页面。
- 内容加载要等 `SessionState.isReadyForContent`。启动时会话先发布本地恢复的账号，校验完成后再发布一次；如果不等校验结束，这两次 revision 会让列表清空重载、加载指示器闪回。
- 同一账号的临时忙碌不清空已确认的数据；只有账号的修订变化才清空并重载。
- 公开读取（搜索、标签、作者作品）不携带 Cookie，但仍然捕获 revision，切换账号时照样取消旧请求。

`ContentRepository` 把私有的序列化 DTO 映射为领域模型，界面不接触 DTO 或会话。列表逐条解码，无效记录被省略并按 ID 去重。公开媒体地址统一经 [DeliveryImage.kt](../../app/src/main/kotlin/moe/kirakira/core/image/DeliveryImage.kt) 的 `publicMediaHttpUrl` 校验：必须是 HTTPS，且不含用户名和密码。

## 读取失败与访问失效

规则见[加载与刷新](../ui-guidelines.md#加载与刷新)。判定集中在 `ApiFailure.retainsContent`，组件侧的接入见[统一内容状态](components.md#统一内容状态)。

| 失败 | 分类 | 是否保留已有内容 |
| --- | --- | --- |
| 网络、超时、限流、响应异常、未区分原因的业务拒绝 | 各自的类型 | 保留 |
| HTTP 403／404／410，或接口明确返回 `isBlocked` | `CONTENT_UNAVAILABLE` | 撤下 |
| HTTP 401 | `SESSION_EXPIRED` | 撤下 |

不根据服务端的消息文本推断隐私或资源状态。

**视频详情失去访问权限时**的连锁处理：

- 取消并清除评论、弹幕的读取和评论的发布回显。
- 撤下已确认的分 P 和播放输出，关闭对应的应用内小窗。
- ViewModel 分别保留“访问失效”和“播放器待清理”两个状态，后者要等播放器完成清理才确认。合成一个状态的话，快速重试会覆盖失效信号。
- 子内容的加载、重试、翻页和写操作后的刷新都先检查访问状态。失效期间点击评论或弹幕的重试，会先重新读取视频详情；只有详情成功返回才恢复子请求。

**用户屏蔽变更**按账号和作者通知仍保留着的视频页：屏蔽时立即撤下内容和播放，解除后重新核对详情。评论赞踩之后的页内核对失败时保持待核对状态，在重试或刷新成功前不允许按旧的赞踩状态继续提交。

## 视频搜索

[SearchViewModel](../../app/src/main/kotlin/moe/kirakira/feature/search/SearchViewModel.kt) 在主导航宿主创建，`SearchPage` 与顶栏插槽收集同一个 ViewModel 的状态。

### 接口

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `video/search` | `keyword`；`success`、`videos` | 关键词搜索 |
| GET `video/tag/search` | `tagName`；`success`、`result` | 查找已有标签 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`、`videos` | 同时包含全部标签的视频 |

- **三个接口都没有分页和排序参数。** 结果数量是实际返回并去重后的列表长度；排序在客户端完成，可按上传时间、播放量或时长稳定排序，缺失值始终排在末尾，默认顺序保留接口顺序。
- Rosales 的关键词查询使用 Elasticsearch 的 `query_string`，标签名称查询使用不区分大小写的正则。客户端原样传递查询内容，不添加查询语法。
- 按标签查询时，上传者关联查询未匹配到用户会返回 `uploaderInfo: false`。视频摘要把这个布尔值归一化为“资料缺失”，保留视频和 `uploaderId`。
- 视频 DTO 的 `duration` 以秒计，在数据层统一换算为毫秒；非有限值、负数或超出范围的值按缺失处理。
- 数据层区分“列表缺失”和“空列表”。

### 状态

ViewModel 把关键词草稿、所选标签和已提交的条件分开保存。

- 新条件、模式切换或清空会取消旧的视频任务；结果按请求版本（generation）和账号 revision 核对归属。相同条件正在请求时不重复提交。
- 刷新只读已提交的条件，不把未提交的草稿当作刷新条件。
- 标签在全屏选择器里编辑的是草稿（`draftTags`），`ApplyTags` 才应用并查询，关闭即丢弃。候选的查询复用 `TagSearchLookup`，以 trim 后的查询识别结果归属：同一查询重新提交时保留已确认的候选，查询变化立即清空旧结果。
- 清空按钮无论是否已提交都发送同一个 `Clear` 事件，一次性清掉当前模式的条件、已提交的条件、结果和滚动位置，避免“先退出、再清空”的中间状态。
- 搜索草稿、结果、排序选项和滚动位置只存 ViewModel 内存。旋转、切换标签页和从视频页返回时保留；进程重建后回到未提交状态。结果网格按条目键和偏移恢复位置，位置事件带着条件的 generation，旧页面的位置事件覆盖不了新条件。

选择器的界面与共享过渡见[全屏标签选择器](components.md#全屏标签选择器)。

### 搜索历史

[SearchHistoryRepository](../../app/src/main/kotlin/moe/kirakira/data/search/SearchHistoryRepository.kt) 由应用依赖容器持有。

| 项目 | 说明 |
| --- | --- |
| 存储 | 独立的 `kirakira_search_history` SharedPreferences，版本化 JSON；**不参与系统备份** |
| 分区键 | 由后端地址和账号 UUID 生成，游客使用独立标识 |
| 容量 | 每个分区最多 30 条 |
| 去重 | 关键词 trim 后精确去重；标签组合以排序去重后的 ID 标识，并保存 `VideoTag` 的多语言名称快照 |

- 读取和修改通过 Mutex 串行化，在 IO 调度器执行；确认写入成功后才发布新列表。
- 有效的提交在发出视频请求**之前**记录，所以搜索失败或空结果也会留下历史。候选查询、模式切换和刷新不记录。
- 持久化任务由 ViewModel 的生命周期持有并捕获发起账号，不随账号的 HTTP 任务一起取消；回写时同时核对 revision 和历史操作的版本。
- 读到无法解码或版本不受支持的分区时，删除该分区并回到空历史，避免损坏的数据永久卡住；只有真实的存储失败才保留原列表并报错。

最近搜索是按时间排列的单组列表，使用普通 `LazyColumn` 和官方 `ListItem`，不使用连接列表。

## 视频标签

视频详情的 `videoTagList` 与屏蔽设置共用 [VideoTag.kt](../../app/src/main/kotlin/moe/kirakira/data/content/VideoTag.kt) 的领域模型和 DTO 映射。

**显示名称的回退顺序**：当前语言的默认名、同语言的首个非空名称、`other`、首个语言组，最后是标签编号。简繁中文分别映射到 Cerasus 的 `zhs`／`zht`。原名与主名称相同时不重复显示。

`TagRoute` 只保存标签 ID，`TagViewModel` 绑定导航条目，标签信息和关联视频各有独立的状态，不做磁盘缓存。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| POST `video/tag/get` | `tagId: [id]`；`success`、`result` | 读取标签名称；空结果表示标签不存在 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`、`videos` | 标签页传单个 ID；没有分页参数 |

只接入了只读接口，不借用视频上传接口来模拟标签编辑。

## 视频简介

简介是 [VideoScreen](../../app/src/main/kotlin/moe/kirakira/feature/video/VideoScreen.kt) 里的懒列表，操作条是其中独立的一项。

- [VideoActions](../../app/src/main/kotlin/moe/kirakira/feature/video/VideoActions.kt) 接收领域状态与回调，以五个等宽单元排列。可用宽度不足时横向滚动，按钮高度随标签和字体缩放增长。尺寸与配色见[按钮规范](../ui-guidelines.md#按钮单选框与阴影)。
- 根按钮提供完整的操作名称和已知计数，内部的图标和标签清除重复语义。
- 分享直接触发页面回调。[VideoActionsMenu](../../app/src/main/kotlin/moe/kirakira/feature/video/VideoActionsMenu.kt) 以“更多”按钮为锚点复用 `MenuPopup`，提供稍后再看、查看封面、下载和举报。
- **收藏、稍后再看、下载和举报尚未接入业务**：它们共用“暂不可用”回调，没有本地选择状态和计数，也不发请求。
- 简介正文复用共享的 [ExpandableText](../../app/src/main/kotlin/moe/kirakira/ui/components/text/ExpandableText.kt)。展开状态由 `rememberSaveable(视频 ID)` 持有，同一视频刷新、切换标签页和旋转时保留。

## 视频列表布局

首页、搜索结果和播放历史复用 [VideoFeedGrid](../../app/src/main/kotlin/moe/kirakira/ui/components/video/VideoFeedGrid.kt)；卡片、封面和格式化函数集中在 `ui/components/video/`，不依赖具体的功能包。

| 可用宽度（扣除侧栏、系统 Insets 和内容内边距） | 海报网格的列数 |
| --- | --- |
| 小于 600 dp | 2 |
| 600–839 dp | 3 |
| 840 dp 及以上 | 4 |

- **列表模式**在可用宽度除以字体缩放达到 840 dp 时显示两列横排条目，否则单列。
- **跨折痕时**仍是同一个懒网格和同一份滚动状态。两侧都容得下卡片时按折痕分列，两组列各自对齐页面外缘，多余的宽度留在折痕处；跨列的条目仍占满内容宽度。
- 内容按视频 ID 定位。搜索结果离开再回来时用条目键和偏移恢复，不使用旧网格的行号——列数变化后行号就不对了。
- 首页向 `FeedVideoCard` 传入 `showUploadTime = false`，两种布局都省略上传时间。
- 卡片行距统一由 `VideoCardRowSpacing` 定义。

## 评论与弹幕列表

### 评论分页

评论通过 [CommentListLoader](../../app/src/main/kotlin/moe/kirakira/feature/video/CommentListState.kt) 保存一个**连续的页区间**，使用服务端每页 20 条的接口。

- 请求用 `CommentRequest` 和 `CommentOperation` 明确区分跳转、刷新、末页、向前、向后和页内更新。失败后的重试保存的是原请求，而不是几个布尔参数的组合。
- 各页合并后按评论 ID 去重，并记住每条评论所属的页。
- 可见评论距离已加载区间的边缘不超过 3 条时请求相邻页。同页请求去重；空页或首末页停止对应方向的加载；失败后不自动重试。
- 分页边界按服务端的**原始条数**和总页数判断。过滤后没有可显示评论的页仍参与分页；当前窗口里没有任何可显示评论时，在定位完成后继续尝试相邻页，否则会因为没有可见条目而卡住。
- 请求代次和账号版本一起拒绝过期响应；跳转、刷新和账号切换取消失效的请求。

**保持阅读位置**：

- 向前插入一页时，按旧的可见评论和像素偏移保持锚点。只靠稳定 key 不够：顶部的计数或状态条目可能成为锚点，导致列表跳动。
- 翻页器显示的页码来自逻辑滚动锚点之后的首条可见评论，忽略只露在顶部 `contentPadding` 里的上一页尾项。
- 显式跳转使用可消费的定位请求：已加载的页直接定位，其他页加载成功后重建窗口，列表在下一次测量时定位并核对目标 key，确认后才消费请求、恢复相邻页加载。跳到不足一屏的末页时，按实测高度补足列表底部的空间再定位，否则补进前一页后末页会被挤离顶部。
- 回顶：第 1 页已加载且没有待处理的跳转时直接滚动；否则交给 `jump(1)`，避免向前补页时又恢复旧锚点。

**刷新与互动**：

- 下拉刷新只在第 1 页已加载且位于顶部时启用；中间页的上边缘留给向前加载。
- 局部赞踩只重读所属的页，保留其他页和阅读位置，重读期间禁用该页的互动。
- 发布成功后按最新总数读取末页并定位；回传的评论已经出现在列表里时隐藏发布回显。

### 发布结果不确定

评论与弹幕的发布遇到超时、网络故障、服务异常或响应异常时，保留草稿和“未确认”标记，刷新对应内容，并弹出对话框：主操作是查看最新内容，次操作是再次发送，并明确提示可能重复。之后再提交相同的评论正文（或相同的弹幕正文与样式）仍然需要确认。不因为正文相同就认定服务端已发布，也不自动重放。标记只存当前账号的视频 ViewModel 内存。

### 弹幕列表

弹幕接口一次读完并按播放时间排序。弹幕标签页的长列表复用共享的 `LazyListScrollbar`；滑轨的底部跟随 `FloatingComposerLayout` 提供的留白，避让发送栏。

评论的翻页工具栏使用 `HorizontalFloatingToolbar`，计数标题按工具栏的实测尺寸避让，窄屏和大字体下自动上下排列。不实现本地排序和楼层跳转。

## 关注与粉丝列表

`FollowListRoute(uid, kind)` 的两种列表共用 [feature/follow](../../app/src/main/kotlin/moe/kirakira/feature/follow) 的页面和条目级的 `FollowListViewModel`。

- `ContentRepository.followList` 检查 `success`、必需的 `result` 和非负的 `totalCount`，逐条解码并保留 UID 为正的用户。昵称缺失时回退到用户名。
- 从第 1 页开始，每页 50 条，保留后端顺序并按 UID 去重。
- **结束判断使用原始返回条数的累计值**与最新的总数；分页模型同时记录原始条数，所以整页都被过滤掉时仍能继续读下一页。空页直接结束。
- 刷新成功后替换第一页并重置分页进度；失败时保留旧内容和原来的分页进度。刷新失败要重试刷新，分页失败要手动重试该页。
- 只有前台页面触发自动分页，分页出错期间停止自动触发。
- 页面不逐行补读关注关系，也不做列表缓存。

## 视频页与资料页分页

### 吸顶布局

资料页和邀请码页复用 [CollapsingTabLayout](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/CollapsingTabLayout.kt)：分页内容在下层，资料（或统计）区与 Tab 组成上层的一张完整表面。

- `CollapsingTabState` 保存折叠距离，范围是实测的头部高度；简介展开、字体变化和错误条目都会更新这个范围。
- 嵌套滚动：上滑时优先折叠头部，下滑时先由当前列表消费，剩余的距离再展开头部。未吸顶时头部和 Tab 区域都能驱动折叠，吸顶后 Tab 不发起纵向滚动。
- 分页使用**完整的视口高度**，顶栏和 Tab 的高度放进各列表的 `contentPadding`。这样条目滚动后能进入栏后，供毛玻璃采样。
- 头部背景始终挂载，吸顶前后不替换背景 Modifier 或投影节点；展开态的封面和实色遮层保持组合，只在绘制阶段决定画不画。背景与投影的承载方式见[栏面阴影](theme.md#栏面阴影)。
- 各 Tab 保留独立的列表位置。

资料页以 `CollapsingTabState.isCollapsed` 同步顶栏昵称、按钮样式和背景：未吸顶时按钮使用[黑底毛玻璃按钮](components.md#黑底毛玻璃按钮)，吸顶后恢复普通图标按钮并显示昵称。

宽窗口下资料页改用双栏，不走 `CollapsingTabLayout`。单栏最多加宽到 720dp；扣除侧栏与横向安全区后的可用宽度超过 720dp 就切换为双栏，因此任何宽度下两侧都不会留白，也不设高度门槛（横屏手机同样使用双栏）。起始侧资料栏取可用宽度的 34%、夹在 320–400dp；书本式折叠（`bookFold`）则直接取折痕前的宽度，条件是资料侧至少 320dp、另一侧至少 360dp。双栏不设最大宽度，横幅延伸到窗口起始边，两栏各自避让所在一侧的安全区。内容侧的视频网格按 `（栏宽 − 16dp）/ 196dp` 取 2–6 列（卡片最小 180dp 加 16dp 间距），列表布局仍保持一列。双栏模式没有可展开的头部，状态栏触顶只滚动当前标签的列表。

资料页的顶部横幅是本地图片 `profile_banner_placeholder`，点击后通过独立的共享键进入图片查看器。服务端的 `userBannerImage` 字段只是在保存资料时原样带回，**没有用来显示远程封面**。

首次读取关注／粉丝数时显示骨架并禁用入口；已有的计数在刷新期间继续显示。

### 视频页

视频页的播放器由常驻节点绘制，`VideoScreen` 只保留同样高度的占位区域。`VideoContentLayout` 用一个 `Layout` 承载三个同级节点，按“分页内容、顶部共享背景、Tab 前景”的顺序绘制。测量时先量 Tab，用它的实际高度确定背景下沿，所以不需要额外的坐标回调或尺寸状态。实际的播放器、原生视频表面和控件不参与背景的模糊或透明度处理。

### Tab 与指示器

两页的 Tab 通过共享的 [PagerTabRow](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/PagerTabRow.kt) 使用官方 `PrimaryTabRow`（颜文字分类用 `PrimaryScrollableTabRow`），调用方保留 Pager 状态、标题和点击事件。

- [PagerTabIndicator](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/PagerTabIndicator.kt) 直接跟随拖动、回弹和点击切页的实际进度。前缘和后缘的插值参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，形状采用它的上圆下平样式，而不是 Compose 默认的完整胶囊。
- **可滚动栏的居中补偿**。官方的固定 Tab 栏把指示器容器约束为单个 Tab 的宽度；可滚动栏则约束为选中 Tab 的内容宽度，并额外按该 Tab 居中。指示器自己的左右边界已经用了含居中偏移的栏内坐标，所以放置时要扣掉父级的那一段居中位移，否则可滚动栏里会重复居中。固定栏两种宽度相同，补偿为零。
- 选中索引在组合时读取，滑动进度在测量阶段读取。

点击切页共用 [rememberTabChangeHandler](../../app/src/main/kotlin/moe/kirakira/ui/components/scaffold/TabTransition.kt)：取消上一次点击启动的滚动，从 Pager 的当前位置转向新目标。Pager 停稳后重复点击当前页时调用 `onTabReselect` 执行回顶；用户手动滚动可以中断回顶。

缓动来自 [EmphasizedEasing](../../app/src/main/kotlin/moe/kirakira/ui/components/motion/EmphasizedEasing.kt)，它提供 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration)：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 用 Compose 的 `PathEasing` 兼容。

### 输入区与颜文字面板

评论与弹幕共用 `ContentComposer`。

- **点击外部关闭**。`VideoPage` 的根容器通过 `dismissComposersOnOutsideTap` 在指针的 Initial 阶段观察外部短按，用窗口坐标排除发送行和颜文字面板，并且不消费事件。它关闭面板、清除焦点并隐藏键盘，不改动正文和光标。
- **面板与两个 Pager**。颜文字面板打开期间，外层的视频 Pager 关闭手势分页并使用空的嵌套滚动连接，越界的横向位移才会留给分类 Pager 自己的 overscroll，而不是带动视频的标签页。
- **面板的高度动画**。高度上限设在面板内容上，而不是动画外层；否则 `expandVertically` 的逐帧尺寸约束会截掉弹簧的超调。

## 用户页的关系操作与分享

### 屏蔽与隐藏

`ContentRepository.profileDetails` 读取用户资料以及 `isBlocked`、`isHidden`、`isBlockedByOther`。只有明确屏蔽时才允许资料为空。

[ProfileViewModel](../../app/src/main/kotlin/moe/kirakira/feature/profile/ProfileViewModel.kt) 通过共享的 `AccountSettingsRepository.changeRule` 提交，并监听 `ruleVersion`，让设置页里的变更同步过来。

- 写入不自动重试。失败后重新读取详情，核对成功之前禁用关系操作。
- 成功读到屏蔽状态时，保留关系标记和取消屏蔽的入口，撤下公开资料和关联内容。屏蔽空态保留同一个菜单，否则资料为空时就没有取消屏蔽的入口了。
- 资料读取失败时，同时结束仍在加载的作品与统计状态；否则子请求被取消后刷新指示器会一直显示。已完成的子请求结果保持不变。被取消的旧刷新不执行失败收尾。

### 系统分享

[ContentSharing.kt](../../app/src/main/kotlin/moe/kirakira/core/share/ContentSharing.kt) 构造固定官网域名的用户和视频链接，通过 `ACTION_SEND` 和 `Intent.createChooser` 打开系统分享面板。

- 正文是非空的名称加链接；名称缺失时只发链接。不发送账号身份、认证参数或播放进度。
- 辅助方法只报告分享面板是否成功打开，不推断分享完成还是取消。
- 用户昵称只从当前账号 revision 的页面资料读取，跨账号或资料缺失时退回纯链接。
- 分享不经过认证写请求，游客也可以使用。

## 历史搜索与收藏

### 历史搜索

历史页与设置页共用 `PageSearchBarAnchor` 和 `PageSearchOverlay`（见[可复用的顶栏搜索](components.md#可复用的顶栏搜索)）。两者的差别在宿主：

- 设置搜索在单栏时使用全屏 Dialog。
- **历史搜索在 Activity 窗口内展开**，不使用独立窗口。从搜索结果打开视频时，独立窗口会被销毁；留在同一窗口里，搜索层和封面才能由 Navigation 3 的来源 Scene 保留在播放层下方，封面 Zoom 可以直接开始，返回后也恢复到同样的搜索内容和位置。

搜索层的可见性只由 `SearchBarState` 决定，不随页面是否激活而变；页面失活时只释放输入焦点并停用局部的返回处理。展开与收回的几何规则来自官方的 `FullScreenSearchBarLayout`，来源与适配边界见 [AndroidX 搜索展开布局](../../third_party/android-search/README.md)。

页面分别持有普通列表和搜索列表的滚动状态；查询只存内存，账号 revision 变化时重建。空状态图形见[搜索空结果图形](components.md#搜索空结果图形)。

### 收藏

[FavoritesPage](../../app/src/main/kotlin/moe/kirakira/feature/favorites/FavoritesPage.kt) 是占位页：只显示“尚未开放”的说明，没有数据请求，也没有 ViewModel。
