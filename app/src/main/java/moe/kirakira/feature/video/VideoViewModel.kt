package moe.kirakira.feature.video

import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoComment
import moe.kirakira.data.content.VideoDetail
import moe.kirakira.data.history.HistoryRepository
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.content.ContentViewModel

internal data class VideoResumeState(val ready: Boolean = false, val positionMs: Long? = null)

internal data class VideoAccessState(
    val unavailable: Boolean = false,
    // A later successful read must not erase playback cleanup before the page consumes it.
    val playbackInvalidationPending: Boolean = false,
    val refreshingTab: VideoTab? = null,
)

internal class VideoViewModel(
    val videoId: Int,
    repository: ContentRepository,
    private val history: HistoryRepository? = null,
    private val settings: AccountSettingsRepository? = null,
) : ContentViewModel(repository) {
    private val _detail = MutableStateFlow(ContentState<VideoDetail>(loading = true))
    val detail = _detail.asStateFlow()
    private val _access = MutableStateFlow(VideoAccessState())
    val access = _access.asStateFlow()
    private val _resume = MutableStateFlow(VideoResumeState())
    val resume = _resume.asStateFlow()
    private var resumeTask: Job? = null
    private val commentList = CommentListLoader(videoId, repository, ::launchTask)
    val comments = commentList.state
    private val _danmaku = MutableStateFlow(ContentState<List<DanmakuEntry>>())
    val danmaku = _danmaku.asStateFlow()
    private val _commentDraft = MutableStateFlow("")
    val commentDraft = _commentDraft.asStateFlow()
    private val _danmakuStyle = MutableStateFlow(DanmakuStyle())
    val danmakuStyle = _danmakuStyle.asStateFlow()
    fun updateDanmakuStyle(style: DanmakuStyle) {
        if (!busy.value) _danmakuStyle.value = style
    }

    private val _danmakuDraft = MutableStateFlow("")
    val danmakuDraft = _danmakuDraft.asStateFlow()
    private val _posted = MutableStateFlow<VideoComment?>(null)
    val posted = _posted.asStateFlow()
    private var commentsRequested = false
    private var danmakuRequested = false
    private var authorUid: Long? = null
    private val contentUnavailable: Boolean get() = _access.value.unavailable
    private val canAccessContent: Boolean
        get() = !contentUnavailable && session.value.isReadyForContent && revision == session.value.revision

    init {
        observeAccount({
            _detail.value = ContentState(loading = true)
            _resume.value = VideoResumeState()
            resumeTask = null
            commentList.reset()
            _danmaku.value = ContentState()
            _commentDraft.value = ""
            _danmakuDraft.value = ""
            _danmakuStyle.value = DanmakuStyle()
            _posted.value = null
            commentsRequested = false
            danmakuRequested = false
            authorUid = null
            _access.value = VideoAccessState()
        }, {
            refresh()
            ensureDanmaku()
        })
        viewModelScope.launch {
            settings?.ruleChanges?.collect { change ->
                if (change.revision == revision && change.revision == session.value.revision &&
                    change.category == RuleCategory.BLOCK && change.value.toLongOrNull() == authorUid
                ) {
                    if (change.remove) {
                        if (contentUnavailable) refresh()
                    } else {
                        invalidateContent(ApiFailure.CONTENT_UNAVAILABLE)
                    }
                }
            }
        }
    }

    fun refresh() = refreshDetail()

    private fun refreshDetail(refreshingTab: VideoTab? = null) {
        if (!session.value.isReadyForContent || revision != session.value.revision) return
        if (contentUnavailable) _access.update { it.copy(refreshingTab = refreshingTab) }
        prepareResume()
        load(_detail, onContentUnavailable = { failure ->
            invalidateContent(failure)
        }, onReadFailure = { failure ->
            if (contentUnavailable && failure.retainsContent) {
                commentList.contentUnavailable(failure)
                _danmaku.value = _danmaku.value.readFailed(failure)
                _access.update { it.copy(refreshingTab = null) }
            }
        }) { expected ->
            repository.video(videoId, expected).also {
                authorUid = it.author.uid
                history?.rememberVideo(it.summary.copy(author = it.author.username), expected)
                val restoringAccess = contentUnavailable
                _access.update { it.copy(unavailable = false, refreshingTab = null) }
                if (restoringAccess) ensureDanmaku()
            }
        }
    }

    private fun invalidateContent(failure: ApiFailure) {
        cancelRead(_detail)
        _detail.value = _detail.value.readFailed(failure)
        _access.update { it.copy(unavailable = true, playbackInvalidationPending = true, refreshingTab = null) }
        commentsRequested = false
        danmakuRequested = false
        _posted.value = null
        commentList.contentUnavailable(failure)
        cancelRead(_danmaku)
        _danmaku.value = _danmaku.value.readFailed(failure)
    }

    fun playbackInvalidated() {
        _access.update { it.copy(playbackInvalidationPending = false) }
    }

    private fun prepareResume() {
        if (!session.value.isReadyForContent || revision != session.value.revision ||
            _resume.value.ready || resumeTask?.isActive == true) return
        val expected = revision
        resumeTask = launchTask {
            val positionMs = try {
                withTimeoutOrNull(1_500.milliseconds) {
                    history?.awaitPending(expected)
                    history?.loadIfNeeded(expected)
                    history?.resumePosition(videoId, expected)
                }
            } catch (_: ApiException) {
                null
            }
            currentCoroutineContext().ensureActive()
            if (expected == session.value.revision) {
                _resume.value = VideoResumeState(ready = true, positionMs = positionMs)
            }
        }
    }
    fun ensureComments() { if (!commentsRequested) comments(1) }
    fun ensureTab(tab: VideoTab) { if (tab == VideoTab.COMMENTS) ensureComments() else if (tab == VideoTab.DANMAKU) ensureDanmaku() }
    fun ensureDanmaku() { if (!danmakuRequested) loadDanmaku() }
    fun comments(page: Int) {
        if (!canAccessContent) return
        commentsRequested = true
        commentList.jump(page)
    }
    fun refreshComments() {
        if (contentUnavailable) retryDetail(VideoTab.COMMENTS) else if (canAccessContent) commentList.refresh()
    }
    fun retryComments() {
        if (contentUnavailable) retryDetail() else if (canAccessContent) commentList.retry()
    }
    fun loadAdjacentComments(before: Boolean, retry: Boolean) {
        if (canAccessContent) commentList.adjacent(before, retry)
    }
    fun consumeCommentLocation(request: Long) = commentList.consumeLocation(request)
    fun refreshDanmaku() {
        if (contentUnavailable) retryDetail(VideoTab.DANMAKU) else loadDanmaku()
    }
    private fun loadDanmaku() {
        if (!canAccessContent) return
        danmakuRequested = true
        load(_danmaku) { repository.danmaku(videoId, it) }
    }
    fun retryDanmaku() {
        if (contentUnavailable) retryDetail() else loadDanmaku()
    }
    private fun retryDetail(refreshingTab: VideoTab? = null) {
        if (!session.value.isReadyForContent || revision != session.value.revision) return
        if (_detail.value.loading) {
            if (refreshingTab != null) _access.update { it.copy(refreshingTab = refreshingTab) }
        } else {
            refreshDetail(refreshingTab)
        }
    }
    fun vote(target: Reaction) {
        if (!canAccessContent || _detail.value.loading || _detail.value.error != null) return
        val value = _detail.value.data ?: return
        mutate { revision ->
            try {
                repository.vote(videoId, value.reaction, if (value.reaction == target) Reaction.NONE else target, revision)
            } finally {
                currentCoroutineContext().ensureActive()
                if (canAccessContent) refresh()
            }
        }
    }
    fun follow() {
        if (!canAccessContent || _detail.value.loading || _detail.value.error != null) return
        val author = _detail.value.data?.author ?: return
        val following = !author.following
        mutate { revision ->
            try {
                repository.follow(author.uid, following, revision)
                currentCoroutineContext().ensureActive()
                if (revision != repository.session.value.revision) return@mutate
                val current = _detail.value
                val detail = current.data ?: return@mutate
                if (detail.author.uid == author.uid) {
                    _detail.value = current.copy(data = detail.copy(author = detail.author.copy(following = following)))
                }
            } finally {
                currentCoroutineContext().ensureActive()
                if (canAccessContent) refresh()
            }
        }
    }
    fun voteComment(comment: VideoComment, target: Reaction) {
        if (!canAccessContent) return
        val page = comments.value.entries.firstOrNull { it.comment.id == comment.id }?.page ?: return
        if (busy.value || comments.value.loading || comments.value.unconfirmedPages.isNotEmpty() ||
            page in comments.value.updatingPages) return
        commentList.markUnconfirmed(page)
        mutate { revision ->
            try {
                repository.commentVote(videoId, comment, if (comment.reaction == target) Reaction.NONE else target, revision)
            } finally {
                currentCoroutineContext().ensureActive()
                if (canAccessContent) commentList.reloadComment(comment.id)
            }
        }
    }
    fun updateCommentDraft(text: String) {
        if (!busy.value) _commentDraft.value = text
    }

    fun updateDanmakuDraft(text: String) {
        if (!busy.value) _danmakuDraft.value = text
    }

    fun sendComment() {
        if (!canAccessContent) return
        val text = commentDraft.value.trim()
        if (text.isEmpty() || text.length >= 20000) return
        mutate { revision ->
            val comment = repository.postComment(videoId, text, revision)
            _commentDraft.value = ""
            if (!canAccessContent) return@mutate
            _posted.value = comment
            commentList.posted()
        }
    }
    fun sendDanmaku(positionMs: Long) {
        if (!canAccessContent) return
        val text = danmakuDraft.value.trim()
        if (text.isEmpty() || positionMs < 0) return
        val style = _danmakuStyle.value
        mutate { revision ->
            repository.postDanmaku(videoId, text, positionMs / 1000.0, revision, style)
            _danmakuDraft.value = ""
            loadDanmaku()
        }
    }
}
