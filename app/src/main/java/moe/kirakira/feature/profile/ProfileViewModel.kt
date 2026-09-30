package moe.kirakira.feature.profile

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.FollowStats
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.ContentViewModel

internal class ProfileViewModel(val uid: Long, repository: ContentRepository) : ContentViewModel(repository) {
    private val _profile = MutableStateFlow(ContentState<PublicProfile>(loading = true))
    val profile = _profile.asStateFlow()
    private val _videos = MutableStateFlow(ContentState<List<VideoSummary>>(loading = true))
    val videos = _videos.asStateFlow()
    private val _stats = MutableStateFlow(ContentState<FollowStats>())
    val stats = _stats.asStateFlow()
    init {
        observeAccount({
            val account = repository.session.value.activeProfile?.takeIf { it.uid == uid }
            _profile.value = ContentState(account?.let {
                PublicProfile(it.uid, it.displayName, it.username, it.avatar, it.banner, it.signature, isSelf = true)
            }, loading = true)
            _videos.value = ContentState(loading = true)
            _stats.value = ContentState()
        }, ::refresh)
    }
    fun refresh() {
        load(_profile) { repository.profile(uid, it) }
        load(_videos) { repository.videos(uid, it) }
        load(_stats) { repository.stats(uid, it) }
    }
    fun follow() = mutate { revision ->
        val value = _profile.value.data ?: return@mutate
        try { repository.follow(uid, !value.following, revision) } finally { currentCoroutineContext().ensureActive(); refresh() }
    }
}
