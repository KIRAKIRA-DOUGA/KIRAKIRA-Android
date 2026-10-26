# 内容浏览

[实现索引](../implementation.md) · [功能现状](../features.md) · [开发指南](../development.md) · [贡献规范](../../CONTRIBUTING.md)

本文维护搜索、标签、视频列表、关注列表与分页布局。开发约束以贡献指南为准。

最近搜索标题、搜索结果数量、评论／弹幕总数及标签页视频数量沿用同一[列表标题与数量规范](../../CONTRIBUTING.md#列表开关与弹层)，保留各自的列表布局和标题语义。搜索结果的空文字占位、标签页数量未知时的“相关视频”与已知数量使用一致排版；评论标题的操作区避让使用同一文字样式测量与绘制，数字动画及受限宽度下的换行仍由共享计数组件处理。

- [视频搜索](#视频搜索)
- [视频简介](#视频简介)
- [视频标签](#视频标签)
- [视频列表布局](#视频列表布局)
- [关注与粉丝列表](#关注与粉丝列表)
- [视频页与资料页分页](#视频页与资料页分页)

内容页的 `ContentViewModel` 与管理设置的 `SettingsViewModel` 共用 `core/coroutines/AccountTasks`，以挂在各自 `viewModelScope` 下的 `SupervisorJob` 管理当前批次任务；账号重置时取消子任务，宿主销毁时取消整个任务树，不维护手工 Job 集合。内容页保留会话就绪后的 revision 观察，管理设置仍观察 revision、加载及忙碌状态，各自决定清空和重载时机。

`ContentRepository` 的视频与评论赞踩共用私有 `submitVote`，统一根据原状态和目标状态选择赞／踩方向及 POST／DELETE；公开入口分别构建原有路径和请求体。公开媒体地址统一经 `core/image/DeliveryImage.kt` 的 `publicMediaHttpUrl` 校验 HTTPS 及空用户名、密码，头像完整 URL、普通媒体 URL 和 `ImageSource.RemoteUrl` 共用此规则，Cloudflare 图片 ID 仍按原有规则构造。

## 视频搜索

`feature/search/SearchViewModel` 复用共享 [ContentViewModel](../../app/src/main/java/moe/kirakira/ui/content/ContentViewModel.kt) 的会话就绪与 revision 防护，在主导航宿主创建；`SearchPage` 与导航宿主的顶栏插槽收集同一个 ViewModel 的状态，`SearchTopBar`、`SearchScreen`、`TagSearchDialog` 与 `TagSearchCandidates` 仅接收状态、事件回调。UI 不接触 DTO 或会话，结果使用现有 `VideoSummary`、`VideoCardRow` 与 `VideoRoute`，标签复用 `VideoTag.displayName` 的多语言回退。

关键词输入使用 `QuerySearchBar`，全屏选择器直接复用同一官方 `SearchBarDefaults.InputField`；关键词保持同页编辑和结果展示，标签候选查询位于独立全屏 Dialog。输入按受控字符串同步 ViewModel，不额外保存查询；保留公开的受控字符串 `InputField` 重载，不抑制其弃用警告。`SearchFieldContainer` 以官方 `Surface`、`SearchBarDefaults.colors()` 的容器色和主题 extraLarge 圆角统一承载关键词、标签与选择器输入区域，不额外叠加折叠 SearchBar 的顶部留白；标签模式使用 `TagSearchField` 的标签及添加动作槽位。标签框以 `SearchBarDefaults.InputFieldHeight` 为最小高度，复用官方 Chip 与图标按钮触摸区域并保留横向 4dp 容器内边距，纵向由官方最小高度和居中对齐提供留白；Chip 内容保留上下各 8dp 留白，空白或单行时与关键词输入框同高，多行自然增高；Chip 区域上限取两个官方输入高度，避免分别硬编码两种搜索框的高度。已选标签组保留官方最小交互尺寸，Chip 使用 48dp 触摸目标；标签框的标签组上下留白为 4dp，使单行标签时容器仍与关键词输入框同高，多行按 8dp 行距自然增高并受两个输入高度限制，不使用负间距、固定 Chip 高度或局部关闭触摸目标扩展。

`MainScreen.searchTopBar` 插槽将 `SearchTopBar` 放入搜索 Tab 的 `FrostedScaffold.topBar`，随页面转场；顶部是搜索框，下方提供紧凑 FilterChip 模式选项。提交搜索后，`SearchScreen` 在结果列表顶部将数量与 `SearchResultControls` 并排显示，排序菜单与布局切换图标随结果列表滚动；未提交时只展示历史，不提供结果操作。排序菜单通过 `SortChanged` 事件统一选择排序字段与切换方向，单组显示四种排序，以强调色和选中语义标记当前选择，不显示勾选图标；选择未选中的非默认字段从降序开始，重复选择当前非默认字段切换方向，默认顺序重复选择不改变状态。非默认字段尾部复用向下箭头，通过 `animateFloatAsState` 配合主题 `fastSpatialSpec` 在 0°／180° 间过渡，`graphicsLayer` 读取动画值；未选中字段显示降序箭头，首次打开直接呈现当前方向，方向描述复用本地化字符串。点击选项不关闭菜单，外部点击和返回仍关闭。菜单展开状态仅存局部 UI 内存，搜索 generation 或就绪状态变化时关闭。

栏面满宽，内部与历史、结果列表统一居中且最大宽度为 840dp，输入区域保留 16dp 横向内边距，顶部与横向系统 Insets 使用 `TopAppBarDefaults.windowInsets` 避让一次；模式选项可换行，栏高随内容增高。结果操作图标保留完整触摸区域，并使用 `onSurfaceVariant`。顶栏背景与固定 4dp 阴影均由既有宿主管理。已提交标签显示在顶栏搜索框内部，添加入口打开全屏 `TagSearchDialog`；草稿与查询仅在选择器内展示，结果数量保留在列表内容区，图标操作保留 Tooltip 和本地化描述。列表顶部留白与刷新指示器使用宿主返回的实际栏高；空状态最小高度扣除顶栏、底栏、实测结果标题行以及列表间距。

| 请求 | 请求字段／关键响应 | 用途 |
| --- | --- | --- |
| GET `video/search` | `keyword`；`success`, `videos` | 关键词视频搜索，没有分页或排序参数 |
| GET `video/tag/search` | `tagName`；`success`, `result` | 查找已有标签，没有分页参数 |
| POST `video/search/tag` | `tagId: [id, …]`；`success`, `videos` | 同时包含所有标签的视频，没有分页参数 |

以上公开读取不携带 Cookie，查询通过共享 URL builder 编码。数据层检查业务成功和必要列表字段，将缺失列表与空列表区分，分别按视频／标签 ID 去重；多标签 ID 去重并要求为正数，单标签关联视频保留原调用入口。Rosales 关键词查询使用 Elasticsearch `query_string`，标签名称查询使用不区分大小写的正则表达式，客户端保留查询内容，不添加新的查询语法或全站过滤规则。未知业务拒绝映射为统一错误，取消继续传播到 HTTP Call。

Rosales 按标签查询视频时，上传者关联查询未匹配到用户会返回 `uploaderInfo: false`。视频 DTO 仅将该布尔值归一化为缺失资料，保留视频及 `uploaderId`，作者名称沿用既有回退；资料对象正常解码，其他异常类型仍按响应格式错误处理。此映射由搜索结果与标签页共用。

ViewModel 分离关键词草稿、所选标签和已提交条件。视频读取与候选读取分别持有任务和递增版本，更新结果时同时检查请求版本及账号 revision；新条件、模式切换或清空会取消旧视频任务。标签候选输入防抖 500 毫秒，每次输入取消旧候选任务，取消选择也取消。打开全屏选择器时复制已选标签到 `draftTags`，勾选和移除只更新草稿；`ApplyTags` 才应用并查询，关闭时丢弃草稿，底层搜索结果及滚动列表保持组合；系统返回先收起键盘，再关闭选择器。全屏窗口使用官方 `Dialog`，设置 `usePlatformDefaultWidth = false` 与 `decorFitsSystemWindows = false`；窗口内复用 `FrostedScaffold`、系统 Insets 和独立的 `NavigationBackHandler`，顶栏使用中性标题，左右提供关闭图标与平面“搜索”文字按钮；候选输入与草稿 Chip 合并在一个使用官方搜索栏容器色、主题 extraLarge 圆角的区域内，横向外边距为 16dp。Chip 区域保留 16dp 内边距和 8dp 行距，增删时使用主题 `fastSpatialSpec` 平滑调整容器高度。`MainEntries` 按账号 revision 记住 `TagSearchAnchor`，顶栏通过 `onGloballyPositioned` 记录搜索框的屏幕坐标；坐标仅用于组合内动效，不进入 ViewModel 或 SavedState。Dialog 内用窗口原点将坐标换算到局部空间，在同一个 `SharedTransitionLayout` 中以原位标签框作为过渡起点，`AnimatedContent` 配合官方 `sharedBounds` 将其连续变换为选择器输入区域，使用主题 `defaultSpatialSpec` 与 `fastEffectsSpec`，保留原生系统动画缩放。窗口默认动画和遮罩关闭，避免与共享过渡叠加。关闭／确认暂存为 UI 退出意图，反向动画完全结束后才发送对应业务事件，期间忽略重复操作；确认时收拢端显示待应用的标签，宽度取主搜索框实测值，高度按内容自然测量。`FocusRequester` 仅在展开和共享边界动画均完成后由窗口内容的 `LaunchedEffect` 请求查询框焦点，不保存在 ViewModel；账号 revision 改变仍立即销毁整个窗口，不等待退出动画。未展开选择时移除顶栏标签仍即时查询，以去重排序后的 ID 列表识别相同条件。相同条件正在请求时不重复提交，刷新只读已提交条件，不将未提交草稿当作刷新条件。

首次搜索只显示首次加载状态，同条件刷新保留视频、已加载空状态及列表位置，使用 `ContentPullToRefresh` 覆盖式指示器，失败保留已有数据。结果数量为实际返回并去重后的列表长度。视频 DTO 的 `duration` 以秒计，统一在数据层换算为 `VideoSummary.durationMs` 的毫秒值；非有限值、负数或超出转换范围的值作为缺失时长处理。排序稳定地按上传时间、播放量或时长比较，两个方向均将缺失值排末尾，默认顺序保留接口顺序；不请求不存在的服务端排序和分页能力。

当前搜索草稿、结果、控制选项及滚动位置只存 ViewModel 内存，不进入 SavedState、磁盘或日志；明确提交的搜索条件另按下述历史规则持久保存。页面的 `LazyListState` 从该内存位置创建，并通过带条件 generation 的事件回传位置；新条件重置位置，旧页面位置事件不能覆盖新条件。主界面搜索分支与首页一样将底栏占位放入滚动内容，键盘内边距由搜索页面处理。旋转、Tab 切换与视频页返回保留状态，进程重建回到未提交状态并加载本机历史；账号 revision 改变清空临时搜索状态并重新加载历史。

`data/search/SearchHistoryRepository` 由应用依赖容器持有并注入搜索 ViewModel，使用独立的 `kirakira_search_history` SharedPreferences 保存版本化 JSON，不在系统备份允许列表中。存储键由后端地址及账号 UUID（游客独立标识）生成；每个分区最多 30 条，关键词 trim 后精确去重，标签以排序去重后的 ID 标识组合，保留 `VideoTag` 多语言名称快照。读取及修改通过 Mutex 串行化并在 IO 调度器执行，确认写入成功后才发布新列表，失败保留原列表并反馈。有效提交在视频请求前记录，故失败与空结果也保留；候选查询、模式切换和刷新不记录。持久化任务由 ViewModel 生命周期持有并捕获发起账号，不因账号 HTTP 任务取消而误归属；回写同时核对 revision 和历史操作版本。读取到无法解码或版本不受支持的分区时删除该分区并回到空历史，避免损坏数据永久阻塞；仅真实存储失败保留原列表并反馈。

`SearchHistoryContent` 以普通 `LazyColumn` 与官方 `ListItem` 混排最近搜索，作为仓库连接列表规范的场景例外：记录仅按时间排列，没有连接分组关系。列表不设置额外横向外边距，条目与点击反馈铺满列表视口，内容使用起始侧 16dp、末端 4dp、纵向 4dp 内边距，补偿尾部 48dp 按钮内居中 24dp 图标的留白，使两侧图标画布距列表边缘均为 16dp，并保留完整触摸区域；历史顶部额外留白为 4dp；标题与操作各自设置内容留白。标题使用次要文字色，标签历史组合通过 `FlowRow` 展示无描边、`surfaceContainerLow` 底色的静态 `Surface` 标签，复用 `SuggestionChipDefaults` 的形状、最小高度和内容内边距，采用 8dp 行距；标签不处理点击、不拥有独立按钮语义，按压反馈由整行统一提供。条目使用官方 `ListItem.onClick` 处理整行交互，各交互态保留矩形外观；关键词与标签共用内容槽内的同一横排布局、16dp 列间距和相同删除按钮触摸占位，避免原生图标槽缩减最小交互占位后造成尾部横向错位及单行行高差异；关键词与标签区域不再叠加上下内边距，常规单行由统一按钮占位决定内容高度。关键词与标签行统一使用 `Row` 垂直居中，两侧图标与标签组整体对齐；标签组换行或字体缩放时仍按实际高度居中，不以固定偏移补偿。点击整行恢复整组条件，标签区域也属于该行点击范围。标题与清空操作直接参与正常列表布局，首次显示及滚回标题时不单独播放入场动画。历史条目使用稳定条件 key 与 `animateItem`，历史区域整体切换时使用 `AnimatedVisibility` 淡入及轻微位移，复用主题 motion scheme 并遵循系统动画缩放。屏幕按账号 revision 重建动画子树，切换身份时不保留旧账号的退出动画；公开 UI 状态同时过滤未就绪或过期的账号状态。删除与清空不影响当前搜索条件，清空使用共享 `ActionDialog` 和危险主按钮确认；结果内容区顶部保留数量、排序与布局切换，下方展示结果，不放置历史返回按钮；关键词与标签输入框末端的叉号统一提供返回入口。无论是否已提交，叉号均发送同一个 `Clear` 事件；`resetResults(clearConditions = true)` 先取消视频任务并递增版本，再一次发布清空当前模式条件、已提交条件、结果和滚动位置后的状态，避免“先退出、再清空”的两次点击及中间状态。叉号统一使用“清空搜索”的本地化描述，标签模式同时保留加号和各 Chip 的独立移除操作。

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

弹幕接口一次读取并按播放时间排序，弹幕 Tab 的长列表复用共享 `LazyListScrollbar`，与邀请码页使用相同的贴边滑块及按压宽度／透明度动画。快滚条绑定原有 `danmakuListState`，叠加绘制且不改变条目宽度；滑轨顶部避让 Tab，底部使用 `FloatingComposerLayout` 提供的 `listBottomPadding` 避让发送栏与系统区域，随输入面板变化更新。仅有弹幕且列表可滚动时显示，不增加位置提示、时间提示或额外请求。

视频评论与弹幕草稿只暴露只读 StateFlow，页面通过 ViewModel 的更新方法提交输入，忙碌时不改写草稿。

评论通过 `CommentListLoader` 保存连续页区间，继续使用服务端每页 20 条的 API。`CommentPages` 将分页数据与排序、展开、去重后的条目绑定，仅页数据变化时重建；加载、错误与定位状态的复制复用同一份派生数据。分页请求用 `CommentRequest` 与 `CommentOperation` 明确区分跳转、刷新、末页、向前、向后和页内更新，失败重试保存原请求，不组合多个操作布尔参数。各页合并后按评论 ID 去重并保留所属页；`snapshotFlow` 在可见评论距离已加载区间边缘不超过 3 条时请求相邻页，同页请求去重，空页或首末页停止对应方向加载。上下加载具有独立状态和显式失败重试，不自动重复失败请求。列表使用稳定评论 key；向前插入时额外按旧可见评论及像素偏移保持锚点，避免顶部计数或状态条目成为锚点后引起跳动。翻页器页码来自逻辑滚动锚点之后的首条可见评论，忽略仅处于顶部 contentPadding 中的上一页尾项；自动加载不发出定位事件。跳转使用下一次测量定位，确认目标 key 已出现在新列表布局后才消费请求并恢复相邻页加载；显式跳转使用可消费的定位请求，已加载页直接定位，其他页成功后重建窗口。请求代次和账号版本共同拒绝过期响应，跳转、刷新及账号切换取消失效请求。

评论下拉刷新仅在第 1 页已加载且滚动处于顶部时启用（空列表同样允许）；中间页的上边缘只用于前页加载。刷新成功后重建第 1 页，失败保留原内容；局部赞踩只重读所属页，保留其他页和阅读位置，重读期间禁用该页互动。发布成功后根据最新总数读取末页并定位，回传评论已出现在列表时隐藏发布回显。首页、视频当前标签页、弹幕和作者资料继续使用 Material 3 `PullToRefreshBox`；共享包装器的 `enabled` 默认保持开启。加载与错误状态独立于现有内容，不使用演示数据回填，错误状态提供重试按钮。

## 关注与粉丝列表

`FollowListRoute(uid, kind)` 为可序列化的 Navigation 3 路由，两种列表共享 `feature/follow/` 页面和导航条目级 `FollowListViewModel`。主页统计通过事件回调进入列表，列表行按 UID 打开 `ProfileRoute`，导航宿主防止重复入栈；返回沿用该条目的数据和滚动状态。

`ContentRepository.followList` 复用账号快照与 revision 守卫，检查 `success`、必需的 `result`、非负 `totalCount` 和正 UID，将私有 DTO 转为用户摘要及分页领域模型。昵称缺失时回退到用户名；头像复用公开图片客户端。页面不逐行补读关注关系，不保存 Cookie 或列表缓存。

ViewModel 从第 1 页开始，每页 50 条，保留后端顺序并按 UID 去重；结束判断使用原始返回条数的累计值与最新总数，空页直接结束。单个请求任务和请求序号协调刷新、分页与取消，过期结果不能覆盖最新状态。刷新成功替换第一页并重置分页进度；网络、超时与服务器失败保留旧内容和原分页进度，刷新失败需重试刷新，分页失败需手动重试该页。其他失败清除内容及分页状态。账号 revision 变化取消任务、清空内容并重新请求，UI 以 revision 校验展示状态并重建滚动状态。

列表复用 `FrostedScaffold`，正文使用普通 `LazyColumn` 与官方 `ListItem`，按稳定 UID 逐项懒加载。条目容器透明，整行点击保留水波纹及禁用语义，不使用分段圆角、卡片背景或分组阴影；头像为 48dp，内容横向内边距采用 `ListItem` 默认值。首次加载与尾部分页使用共享圆形加载器；刷新使用 `ContentPullToRefresh` 覆盖式指示器，空状态也保留占位。底部系统内边距放入列表 `contentPadding`。可见行接近末尾时请求下一页，仅前台页面触发自动分页；分页错误期间停止自动触发。

## 视频页与资料页分页

两页的 Primary Tab 显式设置选中内容色为 `MaterialTheme.colorScheme.primary`、未选中内容色为 `onSurfaceVariant`，遵循 [Material Tabs 配色规范](https://m3.material.io/components/tabs/specs)。不使用 `Tab` 默认的选中与未选中同色参数，颜色随当前主题更新。

资料页与邀请码页复用 `CollapsingTabLayout`：分页内容位于下层，资料／统计信息与 Tab 组成上层的一张完整表面。头部的 `CollapsingHeaderBackground` 持续挂载 `frostedBarBackground(shadowElevation = BarShadowElevation)`，背景、透明度和唯一的固定 4dp 原生投影位于同一背景节点，资料文字与 Tab 前景独立绘制。吸顶前后不替换背景 Modifier 或投影节点；展开态封面／实色遮层保持组合，只按同一折叠状态在绘制阶段决定是否绘制。页面不使用空投影图层、阴影补偿裁剪、外层懒列表占位项或第二份 Tab。

`CollapsingTabState` 保存折叠距离，以实测头部高度为范围。分页的 `NestedScrollConnection` 在上滑时优先折叠头部，下滑时先由当前列表消费，剩余距离再展开头部；惯性滚动沿用滚动组件的嵌套分发。头部可直接拖动，未吸顶的 Tab 区域也可驱动折叠；吸顶后 Tab 不发起纵向滚动。分页使用完整视口高度，顶部栏和 Tab 高度经布局的 `topContentPadding` 放入各列表的 `contentPadding`；初始条目避让栏面，滚动后条目可进入栏后供毛玻璃采样，吸顶时分页上沿位于视口顶部。展开时分页只下移尚未折叠的正文高度。各 Tab 保留独立列表位置和原有回弹效果，底部系统安全区域与展开态遮挡高度按列表／滚动条各自规则避让。刷新指示器从页面顶部滑入。

资料页使用页面自身管理的 `Scaffold`，以 `CollapsingTabState.isCollapsed` 同步顶栏昵称、返回／更多按钮和表面背景。未吸顶时背景绘制封面与不透明页面底色，资料可滚过透明顶栏，按钮使用 `BackdropIconButton`。吸顶后头部正文移出可用内容区域并隐藏，停止绘制展开态封面／实色遮层，显露已挂载且仅采样 Pager 的毛玻璃背景，顶栏恢复普通图标按钮。顶栏与 Tab 不重复投影。封面随表面滚动，渐变补偿相同折叠距离以保持在视口顶部；简介展开、字体变化及错误条目以实测头部高度更新折叠范围。

资料页的顶栏昵称直接复用 Tab 吸顶状态，与毛玻璃背景和图标按钮样式同步：吸顶后显示单行昵称，超长省略，解除吸顶后立即隐藏。不再测量正文昵称位置或向资料头传递坐标回调。

两页的 Tab 通过共享 [PagerTabRow](../../app/src/main/java/moe/kirakira/ui/components/PagerTabRow.kt) 使用官方 `PrimaryTabRow`，颜文字分类通过同一组件使用 `PrimaryScrollableTabRow`；组件统一选中／未选中内容色、[PagerTabIndicator](../../app/src/main/java/moe/kirakira/ui/components/PagerTabIndicator.kt) 与空分隔线，调用方保留 Pager 状态、标题、点击事件及背景／布局修饰符。

颜文字面板展开使用与官方 `BottomSheet` 弹出相同的主题 `defaultSpatialSpec` 弹簧，收起沿用相同的空间弹簧；不叠加淡入淡出，避免透明度过渡改变可见节奏。高度限制放在面板内容上，动画外层不以展开高度设置上限，避免 `expandVertically` 的逐帧尺寸约束截断弹簧超调；实际回弹空间仍受页面可用高度限制。

颜文字条目不添加横向滚动，使用 `TextAutoSize.StepBased` 在单元格宽度约束内缩小字号，字号范围最低至 1sp，保证长条目完整显示。网格内左右拖动交给分类 Pager；面板打开期间，外层视频 Pager 关闭手势分页并使用空的页面嵌套滚动连接，保留顶部 Tab 点击切换，让越界位移留给分类 Pager 的原生 overscroll，而不带动视频 Tab。分类 Pager 在首尾类别继续拖动时显示系统边缘反馈（Android 12 起为拉伸，旧系统为边缘光晕）；分类 Pager 与网格使用组件默认的 overscroll 效果，网格保留纵向滚动及上下边界的原生拉伸反馈。

视频页由 `VideoPage` 的常驻 `VideoPlayerViewport` 绘制顶部播放器，`VideoScreen` 保留对应高度的占位区域，下方 Pager 与透明 Tab 栏叠放；播放器占位区域与 Tab 共用顶部背景的固定 4dp 原生投影，文字与指示器独立绘制。资料页 Tab 保留不透明配色、吸顶布局与绘制层级，固定 4dp 原生 elevation 由封面、资料区和 Tab 共用的 `CollapsingHeaderBackground` 承载。在测量阶段读取 `currentPage + currentPageOffsetFraction`。

参考 [Material Components 的 Elastic 指示器](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/tabs/ElasticTabIndicatorInterpolator.java)，分别以 `sin(πt/2)` 和 `1−cos(πt/2)` 插值前缘、后缘，使其先伸长再收缩；RTL 下通过相对布局镜像。

`PagerTabRow` 为各 Tab 复用显式 `MutableInteractionSource`，读取当前选中项的按压状态；指示器通过共享 `rememberTabIndicatorWidthScale` 和主题 `fastSpatialSpec` 将宽度动画缩至 85%，松开或取消后恢复。宽度状态在指示器测量阶段读取，左右边界按中心对称内收，保留 RTL 和可滚动栏的父级偏移补偿，文字和触摸区域不缩放。

指示器直接跟随拖动、回弹和点击切页的实际进度，保留 Compose 官方主 Tab 指示器的默认高度与主题颜色；形状显式采用 Material Components 主 Tab 的上圆下平样式，顶部左右圆角为 3dp、底部左右为直角，底边贴齐 Tab 栏底部，不使用 Compose 默认的完整胶囊形状。

官方固定 Tab 栏将指示器容器约束为单个 Tab 的宽度并从栏起点放置；可滚动 Tab 栏则将容器约束为选中 Tab 的内容宽度，并额外按该 Tab 居中。`PagerTabIndicator` 的目标左右边界已经使用包含文字居中偏移的 `TabPosition` 栏内坐标，放置时扣除父级的 `max(0, (选中 Tab 宽度 − 指示器容器宽度) / 2)` 像素位移，避免可滚动栏重复居中。选中索引在组合时读取，与官方宿主使用的索引一致；滑动进度仍在测量时读取。固定栏两种宽度相同，补偿为零；滚动位移由官方滚动容器统一处理，RTL 继续使用相对放置，不添加额外像素偏移常量。

视频页的 `VideoContentLayout` 使用同一个 `Layout` 承载分页 Column、顶部共享背景与 Tab 前景三个同级节点，按此顺序绘制：分页内容在最下层，背景覆盖经过栏后的滚动内容，Tab 文字、指示器与触摸区域位于背景之上。测量时先测量播放器下方剩余空间内的 Tab，以当前 placeable 高度确定背景下沿，再测量分页和背景；背景高度为播放器高度与实测 Tab 高度之和，限制在视口范围内，零尺寸不绘制。背景复用 `frostedBarBackground(hazeState = …, shadowElevation = BarShadowElevation)`，统一承载透明度和 elevation，并在播放器占位区域绘制黑底；Tab 不再有独立背景投影。实际播放器、原生视频表面与控件仍由常驻播放器节点独立绘制，不参与背景模糊或透明度处理。分页位置、列表留白及刷新避让继续使用原有 Tab 高度状态，不新增坐标回调或尺寸状态。

点击切页共用 [rememberTabChangeHandler](../../app/src/main/java/moe/kirakira/ui/components/TabTransition.kt)，取消上一次点击启动的滚动任务后从当前 Pager 位置转向新目标。 Pager 停稳后重复点击当前页时调用可选的挂起回调 `onTabReselect`，不再启动横向切页；切页途中仍按原有转向逻辑处理。共享 `rememberTabReselectHandler` 在组合协程作用域内取消前一次回顶任务并执行最新操作，切换目标页时取消回顶。视频页三组列表状态由 `VideoScreen` 持有，用户主页复用各 Tab 独立列表，播放器设置复用独立 `ScrollState`，颜文字按分类持有网格状态；回顶分别调用 `animateScrollToItem(0)` 或 `animateScrollTo(0)`，用户手动滚动可中断。用户主页不滚动外层资料列表。

滚动与图片查看器复用 [EmphasizedEasing](../../app/src/main/java/moe/kirakira/ui/components/EmphasizedEasing.kt) 提供的 [Material 3 emphasized easing](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#curves-easing--duration) 双段路径：API 28+ 读取路径相同的公开系统资源 `fast_out_extra_slow_in`，API 27 使用 Compose `PathEasing` 兼容。

动画快速推进后平缓收尾，不越过目标页；相邻页为 500ms，跨页按距离延长至最多 650ms。Tab 的选中状态统一使用 `currentPage`。

搜索与标签页的已加载结果数量使用共享 `AnimatedCountLabel`，英文单复数仍由实际数量选择，标签和单位保持静态，数值逐位滚动并平滑调整宽度。搜索切换 generation 后重置数字动画，标签沿用既有路由与会话隔离；加载、空状态、失败和数量来源均沿用原逻辑，不将已加载数量改为服务端总数。实现约束见[数字动画与字数计数](components.md#数字动画与字数计数)。
