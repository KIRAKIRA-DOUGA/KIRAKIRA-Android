# 内容浏览

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护搜索、标签、视频列表、关注列表与分页布局。开发约束以贡献指南为准。

- [视频搜索](#视频搜索)
- [视频简介](#视频简介)
- [视频标签](#视频标签)
- [视频列表布局](#视频列表布局)
- [关注与粉丝列表](#关注与粉丝列表)
- [视频页与资料页分页](#视频页与资料页分页)

## 视频搜索

`feature/search/SearchViewModel` 复用共享 [ContentViewModel](../../app/src/main/java/moe/kirakira/ui/content/ContentViewModel.kt) 的会话就绪与 revision 防护，在主导航宿主创建；`SearchPage` 与导航宿主的顶栏插槽收集同一个 ViewModel 的状态，`SearchTopBar`、`SearchScreen` 与标签面板仅接收状态、事件回调。UI 不接触 DTO 或会话，结果使用现有 `VideoSummary`、`VideoCardRow` 与 `VideoRoute`，标签复用 `VideoTag.displayName` 的多语言回退。

主页面与标签面板共用 `QuerySearchBar`，使用官方 `SearchBar` 与 `SearchBarDefaults.InputField` 的公开内联重载，保持同页编辑和结果展示，不打开额外的展开搜索页面。当前版本的 state 重载在折叠 SearchBar 内禁用软键盘、要求配合独立展开页面，因此此处保留已弃用但仍公开的内联重载，不抑制弃用警告。输入按受控字符串同步 ViewModel，不额外保存查询；标签入口使用只读输入以避免打开面板前弹出键盘。

`MainScreen.searchTopBar` 插槽将双行 `SearchTopBar` 放入搜索 Tab 的 `FrostedScaffold.topBar`，随页面转场；第一行是搜索框，第二行左侧是紧凑 FilterChip 模式选项，末端并排放置排序菜单与布局切换图标，会话就绪后未提交搜索时也可选择排序和布局。排序菜单复用 `SortChanged` 与 `ToggleDirection` 事件，提供四种排序与两个方向选项，并通过勾选图标和选中语义标记当前选择；默认排序下禁用方向选项，点击当前方向只关闭菜单。菜单展开状态仅存局部 UI 内存，搜索 generation 或就绪状态变化时关闭。

栏面满宽，内部居中且最大宽度为 640dp，顶部与横向系统 Insets 使用 `TopAppBarDefaults.windowInsets` 避让一次；模式选项可换行，末端图标保留完整触摸区域，栏高随内容增高。顶栏图标使用 `onSurfaceVariant`，背景与固定 4dp 阴影均由既有宿主管理。已选标签与结果数量保留在列表内容区，图标操作保留 Tooltip 和本地化描述。列表顶部留白与刷新指示器使用宿主返回的实际栏高；空状态最小高度扣除顶栏、底栏、实测标签和数量区域以及列表间距。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `video/search` | `keyword`；`success`, `videos` | 关键词视频搜索，没有分页或排序参数 |
| GET `video/tag/search` | `tagName`；`success`, `result` | 查找已有标签，没有分页参数 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`, `videos` | 同时包含所有标签的视频，没有分页参数 |

以上公开读取不携带 Cookie，查询通过共享 URL builder 编码。数据层检查业务成功和必要列表字段，将缺失列表与空列表区分，分别按视频／标签 ID 去重；多标签 ID 去重并要求为正数，单标签关联视频保留原调用入口。Rosales 关键词查询使用 Elasticsearch `query_string`，标签名称查询使用不区分大小写的正则表达式，客户端保留查询内容，不添加新的查询语法或全站过滤规则。未知业务拒绝映射为统一错误，取消继续传播到 HTTP Call。

ViewModel 分离关键词草稿、所选标签和已提交条件。视频读取与候选读取分别持有任务和递增版本，更新结果时同时检查请求版本及账号 revision；新条件、模式切换或清空会取消旧视频任务。标签候选输入防抖 500 毫秒，每次输入取消旧候选任务，关闭面板也取消；增删所选标签即时查询，以去重排序后的 ID 列表识别相同条件。相同条件正在请求时不重复提交，刷新只读已提交条件，不将未提交草稿当作刷新条件。

首次搜索只显示首次加载状态，同条件刷新保留视频、已加载空状态及列表位置，使用 `ContentPullToRefresh` 覆盖式指示器，失败保留已有数据。结果数量为实际返回并去重后的列表长度。视频 DTO 的 `duration` 以秒计，统一在数据层换算为 `VideoSummary.durationMs` 的毫秒值；非有限值、负数或超出转换范围的值作为缺失时长处理。排序稳定地按上传时间、播放量或时长比较，两个方向均将缺失值排末尾，默认顺序保留接口顺序；不请求不存在的服务端排序和分页能力。

搜索条件、结果、控制选项及滚动位置只存 ViewModel 内存，不进入 SavedState、磁盘或日志。页面的 `LazyListState` 从该内存位置创建，并通过带条件 generation 的事件回传位置；新条件重置位置，旧页面位置事件不能覆盖新条件。主界面搜索分支与首页一样将底栏占位放入滚动内容，键盘内边距由搜索页面处理。旋转、Tab 切换与视频页返回保留状态，进程重建回到空搜索页；账号 revision 改变清空全部搜索状态。

## 视频简介

`VideoScreen` 的 `description` 懒列表条目复用共享 [`ExpandableText`](../../app/src/main/java/moe/kirakira/ui/components/ExpandableText.kt)，设置 `collapsedMaxLines = 3` 并沿用 `bodyLarge` 正文样式。展开状态由条目内的 `rememberSaveable(detail.summary.id)` 持有，默认收起，按视频 ID 隔离；同一视频刷新、切换 Tab 和旋转时保留。文字选择、溢出测量、展开／收起动画与中英文操作及无障碍文案由共享组件提供，不额外嵌套 `SelectionContainer`。

## 视频标签

视频详情的 `videoTagList` 与屏蔽设置共用 `data/content/VideoTag.kt` 的领域模型和 DTO 映射。显示名称优先使用当前语言的默认名、同语言首个非空名称；无匹配语言时回退到 `other`、首个语言组，最后使用标签编号。简繁中文映射为 Cerasus 的 `zhs`／`zht`；原名与主名称相同时不重复显示。

`TagRoute` 仅保存标签 ID，`TagViewModel` 绑定 Navigation 3 条目，标签信息与关联视频使用独立状态。公开读取使用共享 API 客户端、不携带 Cookie，仍捕获账号 revision，切换账号取消旧请求并清理状态；不增加磁盘缓存。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| POST `video/tag/get` | `tagId: [id]`；`success`, `result` | 读取标签名称，空结果表示标签不存在 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`, `videos` | 标签页传单个 ID，搜索页可传多个 ID；当前没有分页参数 |

标签页复用可折叠顶栏和视频卡片，正文顶部展示标签名称与原名；名称面板使用官方 `ModalBottomSheet` 与只读 `SegmentedListItem`，底部 Insets 放入滚动内容。只接入现有只读接口，不使用视频上传接口模拟标签编辑。

## 视频列表布局

跨功能的视频列表展示集中在 `ui/components/video/`：`VideoCard`、`VideoCardRow`、`VideoArtwork`、`VideoCardLayout` 与内容格式化函数由首页、搜索、标签、资料、历史和播放器按需复用。通用加载状态与账号请求协调位于 `ui/content/`，会话就绪条件 `SessionState.isReadyForContent` 由 `data/auth` 定义；共享组件不依赖具体功能包。

[评论页](../../app/src/main/java/moe/kirakira/feature/video/VideoCommentsPage.kt)与[弹幕条目](../../app/src/main/java/moe/kirakira/feature/video/DanmakuListItem.kt)使用透明背景的普通 `Row`／`Column` 布局，不使用分段背景、首尾圆角或分段间距，也不添加卡片或分割线；保留条目内边距、评论头像与操作按钮，以及弹幕左侧时间和右侧正文的对齐。

视频简介、评论和弹幕三个标签页不指定页面背景，保持透明并显示外层 `Scaffold` 默认的 `MaterialTheme.colorScheme.surface` 背景；输入框等独立组件保留各自的容器色。评论和弹幕使用 8dp 外边距；评论复用 `HorizontalFloatingToolbar` 与 `CommentJumpDialog`，在右上角悬浮显示快速前后页定位和页码跳转；计数标题按 Toolbar 实测尺寸避让，窄屏和大字体自动上下排列。不实现本地排序与楼层跳转。底部系统内边距放入 LazyColumn 的 contentPadding，输入框使用 IME 内边距。

评论翻页工具栏采用 48dp 紧凑高度，横向内边距为 4dp、纵向为 0dp；高度取 48dp 与页码实测文字高度加官方 TextButton 上下内边距的较大值，保留标准触摸区域并适应字体缩放。背景使用主题 `surface`（浅色纯白、深色跟随主题），箭头继承 `onSurfaceVariant`，页码按钮保留官方强调色和禁用配色；展开与折叠的原生阴影均通过工具栏官方 elevation 参数固定为 4dp，沿默认胶囊形状投影。页码按钮按剩余宽度测量，过长时单行省略，完整页码仍由无障碍描述提供；两端箭头优先保留布局空间，标题继续按工具栏实测尺寸避让。

评论通过 `CommentListLoader` 保存连续页区间，继续使用服务端每页 20 条的 API。各页合并后按评论 ID 去重并保留所属页；`snapshotFlow` 在可见评论距离已加载区间边缘不超过 3 条时请求相邻页，同页请求去重，空页或首末页停止对应方向加载。上下加载具有独立状态和显式失败重试，不自动重复失败请求。列表使用稳定评论 key；向前插入时额外按旧可见评论及像素偏移保持锚点，避免顶部计数或状态条目成为锚点后引起跳动。翻页器页码来自逻辑滚动锚点之后的首条可见评论，忽略仅处于顶部 contentPadding 中的上一页尾项；自动加载不发出定位事件。跳转使用下一次测量定位，确认目标 key 已出现在新列表布局后才消费请求并恢复相邻页加载；显式跳转使用可消费的定位请求，已加载页直接定位，其他页成功后重建窗口。请求代次和账号版本共同拒绝过期响应，跳转、刷新及账号切换取消失效请求。

评论下拉刷新仅在第 1 页已加载且滚动处于顶部时启用（空列表同样允许）；中间页的上边缘只用于前页加载。刷新成功后重建第 1 页，失败保留原内容；局部赞踩只重读所属页，保留其他页和阅读位置，重读期间禁用该页互动。发布成功后根据最新总数读取末页并定位，回传评论已出现在列表时隐藏发布回显。首页、视频当前标签页、弹幕和作者资料继续使用 Material 3 `PullToRefreshBox`；共享包装器的 `enabled` 默认保持开启。加载与错误状态独立于现有内容，不使用演示数据回填，错误状态提供重试按钮。

## 关注与粉丝列表

`FollowListRoute(uid, kind)` 为可序列化的 Navigation 3 路由，两种列表共享 `feature/follow/` 页面和导航条目级 `FollowListViewModel`。主页统计通过事件回调进入列表，列表行按 UID 打开 `ProfileRoute`，导航宿主防止重复入栈；返回沿用该条目的数据和滚动状态。

`ContentRepository.followList` 复用账号快照与 revision 守卫，检查 `success`、必需的 `result`、非负 `totalCount` 和正 UID，将私有 DTO 转为用户摘要及分页领域模型。昵称缺失时回退到用户名；头像复用公开图片客户端。页面不逐行补读关注关系，不保存 Cookie 或列表缓存。

ViewModel 从第 1 页开始，每页 50 条，保留后端顺序并按 UID 去重；结束判断使用原始返回条数的累计值与最新总数，空页直接结束。单个请求任务和请求序号协调刷新、分页与取消，过期结果不能覆盖最新状态。刷新成功替换第一页并重置分页进度；网络、超时与服务器失败保留旧内容和原分页进度，刷新失败需重试刷新，分页失败需手动重试该页。其他失败清除内容及分页状态。账号 revision 变化取消任务、清空内容并重新请求，UI 以 revision 校验展示状态并重建滚动状态。

列表复用 `FrostedScaffold`，正文使用普通 `LazyColumn` 与官方 `ListItem`，按稳定 UID 逐项懒加载。条目容器透明，整行点击保留水波纹及禁用语义，不使用分段圆角、卡片背景或分组阴影；头像为 48dp，内容横向内边距采用 `ListItem` 默认值。首次加载与尾部分页使用共享圆形加载器；刷新使用 `ContentPullToRefresh` 覆盖式指示器，空状态也保留占位。底部系统内边距放入列表 `contentPadding`。可见行接近末尾时请求下一页，仅前台页面触发自动分页；分页错误期间停止自动触发。

## 视频页与资料页分页

两页的 Primary Tab 显式设置选中内容色为 `MaterialTheme.colorScheme.primary`、未选中内容色为 `onSurfaceVariant`，遵循 [Material Tabs 配色规范](https://m3.material.io/components/tabs/specs)。不使用 `Tab` 默认的选中与未选中同色参数，颜色随当前主题更新。

资料页以铺满屏幕的外层 `LazyColumn` 承载资料信息、`stickyHeader` Tab 栏和固定为剩余视口高度的 `HorizontalPager`。顶部栏高度放入外层列表的 `contentPadding`，不从滚动容器外部避让，保证文字可经过透明栏后。Pager 高度扣除顶部栏和实测 Tab 高度，使外层列表滚动到末端时 Tab 恰好停在顶部栏下沿。分页内列表通过嵌套滚动优先滚走资料信息，回到列表顶部后再向下展开资料；背景跟随外层列表，各 Tab 保留独立列表位置。底部系统内边距仍放在分页列表的 `contentPadding` 中，刷新指示器通过 `indicatorTopPadding` 保持在顶部栏下方。

资料页使用页面自身管理的 `Scaffold`，通过外层列表 `layoutInfo.visibleItemsInfo` 中 key 为 `profile_tabs` 的条目是否到达 `viewportStartOffset + beforeContentPadding` 判断顶栏下沿吸顶，不依赖固定滚动距离或条目索引。吸顶前不绘制顶栏毛玻璃背景，返回和更多按钮使用无阴影的 `BackdropIconButton`，默认白色前景和黑底毛玻璃背景；吸顶后单独绘制 `frostedBarBackground(hazeState = …)` 背景并切换为官方普通 `IconButton`，解除吸顶时立即恢复。顶部共享背景和内容沿用该页独立采样状态，顶栏不单独投影，封面、资料区与 Tab 统一由顶部共享背景承载固定阴影。简介展开、字体缩放及错误条目的高度变化由实际布局自动反映，恢复列表位置后重新推导吸顶状态。

`ProfileHeaderBackground` 在既有 `matchParentSize()` 背景宿主中绘制不透明页面底色与封面，使用 `barSurfaceLayer()` 的 alpha 为 1、固定 4dp 原生 elevation。宿主不参与页面尺寸测量，在前景列表测量完成后读取 Tab 条目，下沿为 `beforeContentPadding + tabs.offset + tabs.size`，限制在视口范围内；Tab 尚未进入可见布局时背景覆盖视口，投影边界位于屏幕外，零尺寸不绘制。背景宽度跟随页面视口，封面用顶部对齐的无界高度测量保留原有尺寸、裁切、位移与渐变，不随宿主高度变化缩放。Tab 不独立升高，Pager 不重复绘制不透明容器底色，列表背景由页面底色提供，使投影只在顶部整体表面的外侧出现，不增加阴影裁剪、坐标回调或逐帧组合状态。

资料页的顶栏昵称直接复用 Tab 吸顶状态，与毛玻璃背景和图标按钮样式同步：吸顶后显示单行昵称，超长省略，解除吸顶后立即隐藏。不再测量正文昵称位置或向资料头传递坐标回调。

两页的 Tab 通过共享 [PagerTabRow](../../app/src/main/java/moe/kirakira/ui/components/PagerTabRow.kt) 使用官方 `PrimaryTabRow`，颜文字分类通过同一组件使用 `PrimaryScrollableTabRow`；组件统一选中／未选中内容色、[PagerTabIndicator](../../app/src/main/java/moe/kirakira/ui/components/PagerTabIndicator.kt) 与空分隔线，调用方保留 Pager 状态、标题、点击事件及背景／布局修饰符。

视频页由 `VideoPage` 的常驻 `VideoPlayerViewport` 绘制顶部播放器，`VideoScreen` 保留对应高度的占位区域，下方 Pager 与透明 Tab 栏叠放；播放器占位区域与 Tab 共用顶部背景的固定 4dp 原生投影，文字与指示器独立绘制。资料页 Tab 保留不透明配色、吸顶布局与绘制层级，固定 4dp 原生 elevation 由封面、资料区和 Tab 共用的顶部背景承载。在测量阶段读取 `currentPage + currentPageOffsetFraction`。

参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，分别以 `sin(πt/2)` 和 `1−cos(πt/2)` 插值前缘、后缘，使其先伸长再收缩；RTL 下通过相对布局镜像。

指示器直接跟随拖动、回弹和点击切页的实际进度，保留 Compose 官方主 Tab 指示器的默认高度与主题颜色；形状显式采用 Material Components 主 Tab 的上圆下平样式，顶部左右圆角为 3dp、底部左右为直角，底边贴齐 Tab 栏底部，不使用 Compose 默认的完整胶囊形状。

官方固定 Tab 栏将指示器容器约束为单个 Tab 的宽度并从栏起点放置；可滚动 Tab 栏则将容器约束为选中 Tab 的内容宽度，并额外按该 Tab 居中。`PagerTabIndicator` 的目标左右边界已经使用包含文字居中偏移的 `TabPosition` 栏内坐标，放置时扣除父级的 `max(0, (选中 Tab 宽度 − 指示器容器宽度) / 2)` 像素位移，避免可滚动栏重复居中。选中索引在组合时读取，与官方宿主使用的索引一致；滑动进度仍在测量时读取。固定栏两种宽度相同，补偿为零；滚动位移由官方滚动容器统一处理，RTL 继续使用相对放置，不添加额外像素偏移常量。

视频页的 `VideoContentLayout` 使用同一个 `Layout` 承载分页 Column、顶部共享背景与 Tab 前景三个同级节点，按此顺序绘制：分页内容在最下层，背景覆盖经过栏后的滚动内容，Tab 文字、指示器与触摸区域位于背景之上。测量时先测量播放器下方剩余空间内的 Tab，以当前 placeable 高度确定背景下沿，再测量分页和背景；背景高度为播放器高度与实测 Tab 高度之和，限制在视口范围内，零尺寸不绘制。背景复用 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)`，统一承载透明度和 elevation，并在播放器占位区域绘制黑底；Tab 不再有独立背景投影。实际播放器、原生视频表面与控件仍由常驻播放器节点独立绘制，不参与背景模糊或透明度处理。分页位置、列表留白及刷新避让继续使用原有 Tab 高度状态，不新增坐标回调或尺寸状态。

点击切页共用 [rememberTabChangeHandler](../../app/src/main/java/moe/kirakira/ui/components/TabTransition.kt)，取消上一次点击启动的滚动任务后从当前 Pager 位置转向新目标。

滚动与图片查看器复用 [EmphasizedEasing](../../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 提供的 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration) 双段路径：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 使用 Compose `PathEasing` 兼容。

动画快速推进后平缓收尾，不越过目标页；相邻页为 500ms，跨页按距离延长至最多 650ms。Tab 的选中状态统一使用 `currentPage`。
