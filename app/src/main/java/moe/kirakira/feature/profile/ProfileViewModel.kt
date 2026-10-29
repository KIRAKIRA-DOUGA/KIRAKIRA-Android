package moe.kirakira.feature.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.FollowStats
import moe.kirakira.data.content.ProfileDetails
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.content.ContentViewModel

internal class ProfileViewModel(
    val uid: Long,
    repository: ContentRepository,
    private val settings: AccountSettingsRepository,
) : ContentViewModel(repository) {
    private val _details = MutableStateFlow(ContentState<ProfileDetails>(loading = true))
    val details = _details.asStateFlow()
    private val _videos = MutableStateFlow(ContentState<List<VideoSummary>>(loading = true))
    val videos = _videos.asStateFlow()
    private val _stats = MutableStateFlow(ContentState<FollowStats>())
    val stats = _stats.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private var refreshJob: Job? = null
    val isCurrentAccount: Boolean get() = revision == session.value.revision

    init {
        observeAccount({
            val account = session.value.activeProfile?.takeIf { it.uid == uid }
            _details.value = ContentState(account?.let {
                ProfileDetails(
                    profile = PublicProfile(
                        it.uid, it.displayName, it.username, it.avatar, it.banner, it.signature, isSelf = true,
                    ),
                    isBlocked = false,
                    isHidden = false,
                    isBlockedByOther = false,
                )
            }, loading = true)
            _videos.value = ContentState(loading = true)
            _stats.value = ContentState()
            _message.value = null
        }, ::refresh)
        viewModelScope.launch {
            settings.ruleVersion.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        if (!session.value.isReadyForContent || !isCurrentAccount) return
        refreshJob?.cancel()
        val expected = revision
        _details.value = _details.value.copy(loading = true, error = null)
        refreshJob = launchTask {
            try {
                val value = repository.profileDetails(uid, expected)
                if (expected != session.value.revision) return@launchTask
                _details.value = ContentState(value)
                if (value.isBlocked) {
                    _videos.value = ContentState()
                    _stats.value = ContentState()
                } else {
                    coroutineScope {
                        launch { readContent(_videos, expected) { repository.videos(uid, expected) } }
                        launch { readContent(_stats, expected) { repository.stats(uid, expected) } }
                    }
                }
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) {
                    // Keep the last confirmed relation visible, but disable writes until a fresh read succeeds.
                    _details.value = _details.value.readFailed(error.failure)
                    // Replacing a refresh cancels its child reads; a failed profile read cannot restart them.
                    val videos = _videos.value
                    if (videos.loading || !error.failure.retainsContent) {
                        _videos.value = videos.readFailed(error.failure)
                    }
                    val stats = _stats.value
                    if (stats.loading || !error.failure.retainsContent) {
                        _stats.value = stats.readFailed(error.failure)
                    }
                }
            }
        }
    }

    private suspend fun <T> readContent(
        target: MutableStateFlow<ContentState<T>>,
        expected: Long,
        fetch: suspend () -> T,
    ) {
        target.value = target.value.copy(loading = true, error = null)
        try {
            val value = fetch()
            if (expected == session.value.revision) target.value = ContentState(value)
        } catch (error: ApiException) {
            currentCoroutineContext().ensureActive()
            if (expected == session.value.revision) {
                target.value = target.value.readFailed(error.failure)
            }
        }
    }

    fun changeRule(category: RuleCategory) {
        if (!session.value.isReadyForContent || !isCurrentAccount) return
        if (category != RuleCategory.BLOCK && category != RuleCategory.HIDE) return
        val state = _details.value
        val value = state.data ?: return
        if (state.loading || state.error != null || session.value.activeProfile == null ||
            session.value.activeProfile?.uid == uid
        ) return
        val remove = if (category == RuleCategory.BLOCK) value.isBlocked else value.isHidden
        mutate { expected ->
            refreshJob?.cancel()
            _details.value = state.copy(loading = true)
            try {
                settings.changeRule(category, uid.toString(), remove, expected)
                if (expected == session.value.revision) {
                    val updated = if (category == RuleCategory.BLOCK) {
                        value.copy(profile = if (remove) value.profile else null, isBlocked = !remove)
                    } else value.copy(isHidden = !remove)
                    _details.value = ContentState(updated, loading = true)
                    if (updated.isBlocked) {
                        _videos.value = ContentState()
                        _stats.value = ContentState()
                    }
                    _message.value = when (category) {
                        RuleCategory.BLOCK -> if (remove) R.string.profile_unblocked else R.string.profile_blocked
                        else -> if (remove) R.string.profile_unhidden else R.string.profile_hidden
                    }
                }
            } finally {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) refresh()
            }
        }
    }

    fun dismissMessage() { _message.value = null }

    fun follow() {
        if (!session.value.isReadyForContent || !isCurrentAccount) return
        val state = _details.value
        val value = state.data?.profile ?: return
        if (state.loading || state.error != null || state.data.isBlocked) return
        mutate { expected ->
            try {
                repository.follow(uid, !value.following, expected)
            } finally {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) refresh()
            }
        }
    }
}
