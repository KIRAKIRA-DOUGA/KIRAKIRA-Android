package moe.kirakira.feature.video

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoComment
import moe.kirakira.data.content.VideoDetail

internal class VideoViewModel(val videoId: Int, repository: ContentRepository) : ContentViewModel(repository) {
    private val _detail = MutableStateFlow(ContentState<VideoDetail>(loading = true))
    val detail = _detail.asStateFlow()
    private val commentList = CommentListLoader(videoId, repository, ::launchTask)
    val comments = commentList.state
    private val _danmaku = MutableStateFlow(ContentState<List<DanmakuEntry>>())
    val danmaku = _danmaku.asStateFlow()
    val commentDraft = MutableStateFlow("")
    private val _danmakuStyle = MutableStateFlow(DanmakuStyle())
    val danmakuStyle = _danmakuStyle.asStateFlow()
    fun updateDanmakuStyle(style: DanmakuStyle) {
        if (!busy.value) _danmakuStyle.value = style
    }

    val danmakuDraft = MutableStateFlow("")
    private val _posted = MutableStateFlow<VideoComment?>(null)
    val posted = _posted.asStateFlow()
    private var commentsRequested = false
    private var danmakuRequested = false

    init {
        observeAccount({
            _detail.value = ContentState(loading = true)
            commentList.reset()
            _danmaku.value = ContentState()
            commentDraft.value = ""
            danmakuDraft.value = ""
            _danmakuStyle.value = DanmakuStyle()
            _posted.value = null
            commentsRequested = false
            danmakuRequested = false
        }, {
            refresh()
            ensureDanmaku()
        })
    }

    fun refresh() = load(_detail) { repository.video(videoId, it) }
    fun ensureComments() { if (!commentsRequested) comments(1) }
    fun ensureTab(tab: VideoTab) { if (tab == VideoTab.COMMENTS) ensureComments() else if (tab == VideoTab.DANMAKU) ensureDanmaku() }
    fun ensureDanmaku() { if (!danmakuRequested) refreshDanmaku() }
    fun comments(page: Int) {
        if (!session.value.isReadyForContent || revision != session.value.revision) return
        commentsRequested = true
        commentList.jump(page)
    }
    fun refreshComments() = commentList.refresh()
    fun retryComments() = commentList.retry()
    fun loadAdjacentComments(before: Boolean, retry: Boolean) = commentList.adjacent(before, retry)
    fun consumeCommentLocation(request: Long) = commentList.consumeLocation(request)
    fun refreshDanmaku() {
        if (!session.value.isReadyForContent || revision != session.value.revision) return
        danmakuRequested = true
        load(_danmaku) { repository.danmaku(videoId, it) }
    }
    fun vote(target: Reaction) {
        val value = _detail.value.data ?: return
        mutate { revision ->
            try {
                repository.vote(videoId, value.reaction, if (value.reaction == target) Reaction.NONE else target, revision)
            } finally {
                currentCoroutineContext().ensureActive()
                refresh()
            }
        }
    }
    fun follow() {
        val author = _detail.value.data?.author ?: return
        val following = !author.following
        mutate { revision ->
            repository.follow(author.uid, following, revision)
            currentCoroutineContext().ensureActive()
            if (revision != repository.session.value.revision) return@mutate
            val current = _detail.value
            val detail = current.data ?: return@mutate
            if (detail.author.uid == author.uid) {
                _detail.value = current.copy(data = detail.copy(author = detail.author.copy(following = following)))
            }
        }
    }
    fun voteComment(comment: VideoComment, target: Reaction) = mutate { revision ->
        try {
            repository.commentVote(videoId, comment, if (comment.reaction == target) Reaction.NONE else target, revision)
        } finally {
            currentCoroutineContext().ensureActive()
            commentList.reloadComment(comment.id)
        }
    }
    fun sendComment() {
        val text = commentDraft.value.trim()
        if (text.isEmpty() || text.length >= 20000) return
        mutate { revision ->
            val comment = repository.postComment(videoId, text, revision)
            commentDraft.value = ""
            _posted.value = comment
            commentList.posted()
        }
    }
    fun sendDanmaku(positionMs: Long) {
        val text = danmakuDraft.value.trim()
        if (text.isEmpty() || positionMs < 0) return
        val style = _danmakuStyle.value
        mutate { revision ->
            repository.postDanmaku(videoId, text, positionMs / 1000.0, revision, style)
            danmakuDraft.value = ""
            refreshDanmaku()
        }
    }
}
