package moe.kirakira.feature.search

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.content.VideoTag
import moe.kirakira.data.search.SearchHistoryEntry
import moe.kirakira.data.search.SearchHistoryRepository
import moe.kirakira.ui.components.video.VideoCardLayout
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.content.ContentViewModel

internal enum class SearchMode(@param:StringRes val label: Int) {
    KEYWORD(R.string.search_mode_keyword), TAG(R.string.search_mode_tag),
}

internal enum class SearchSort(@param:StringRes val label: Int) {
    DEFAULT(R.string.search_sort_default),
    UPLOAD_DATE(R.string.search_sort_upload_date),
    VIEWS(R.string.search_sort_views),
    DURATION(R.string.search_sort_duration),
}

internal sealed interface SearchCriteria {
    data class Keyword(val text: String) : SearchCriteria
    data class Tags(val ids: List<Long>) : SearchCriteria
}

internal data class SearchUiState(
    val ready: Boolean = false,
    val accountRevision: Long = -1,
    val history: List<SearchHistoryEntry> = emptyList(),
    val historyLoaded: Boolean = false,
    val historyBusy: Boolean = false,
    val historyError: Boolean = false,
    val clearHistoryOpen: Boolean = false,
    val mode: SearchMode = SearchMode.KEYWORD,
    val keyword: String = "",
    val tags: List<VideoTag> = emptyList(),
    val submitted: SearchCriteria? = null,
    val videos: ContentState<List<VideoSummary>> = ContentState(),
    val pickerOpen: Boolean = false,
    val draftTags: List<VideoTag> = emptyList(),
    val tagQuery: String = "",
    val candidates: ContentState<List<VideoTag>> = ContentState(),
    val sort: SearchSort = SearchSort.DEFAULT,
    val descending: Boolean = true,
    val layout: VideoCardLayout = VideoCardLayout.GRID,
    val generation: Long = 0,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

internal sealed interface SearchEvent {
    data class KeywordChanged(val value: String) : SearchEvent
    data object Submit : SearchEvent
    data object Clear : SearchEvent
    data class ModeChanged(val mode: SearchMode) : SearchEvent
    data object OpenTags : SearchEvent
    data object CloseTags : SearchEvent
    data object ApplyTags : SearchEvent
    data class UseHistory(val key: String) : SearchEvent
    data class DeleteHistory(val key: String) : SearchEvent
    data object RequestClearHistory : SearchEvent
    data object DismissClearHistory : SearchEvent
    data object ClearHistory : SearchEvent
    data object RetryHistory : SearchEvent
    data object DismissHistoryError : SearchEvent
    data class TagQueryChanged(val value: String) : SearchEvent
    data class ToggleTag(val tag: VideoTag) : SearchEvent
    data class RemoveTag(val id: Long) : SearchEvent
    data object RetryTags : SearchEvent
    data object Refresh : SearchEvent
    data class SortChanged(val sort: SearchSort) : SearchEvent
    data class LayoutChanged(val layout: VideoCardLayout) : SearchEvent
    data class Scrolled(val generation: Long, val index: Int, val offset: Int) : SearchEvent
}

internal class SearchViewModel(
    repository: ContentRepository,
    private val historyRepository: SearchHistoryRepository,
) : ContentViewModel(repository) {
    private val _state = MutableStateFlow(SearchUiState())
    // Never expose a retired account while its reset/load coroutine is catching up.
    val state = combine(_state, session) { current, account ->
        if (current.accountRevision == account.revision && account.isReadyForContent) current
        else SearchUiState(accountRevision = account.revision)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())
    private var historyVersion = 0L
    private var videoJob: Job? = null
    private var tagJob: Job? = null
    private var videoVersion = 0L
    private var tagVersion = 0L

    init {
        observeAccount({
            videoVersion++
            tagVersion++
            videoJob = null
            tagJob = null
            _state.value = SearchUiState(
                ready = true, accountRevision = revision, generation = _state.value.generation + 1,
            )
            updateHistory { historyRepository.load(it) }
        }, {})
    }

    fun onEvent(event: SearchEvent) {
        if (event !is SearchEvent.Scrolled &&
            (!session.value.isReadyForContent || session.value.revision != revision)
        ) return
        when (event) {
            is SearchEvent.KeywordChanged -> _state.value = _state.value.copy(keyword = event.value)
            SearchEvent.Submit -> {
                val keyword = _state.value.keyword.trim()
                if (keyword.isEmpty()) resetResults()
                else if (_state.value.mode == SearchMode.KEYWORD) {
                    _state.value = _state.value.copy(keyword = keyword)
                    search(SearchCriteria.Keyword(keyword), record = true)
                }
            }
            SearchEvent.Clear -> resetResults(clearConditions = true)
            is SearchEvent.ModeChanged -> if (event.mode != _state.value.mode) {
                closeTags()
                _state.value = _state.value.copy(mode = event.mode)
                resetResults()
                if (event.mode == SearchMode.TAG) searchSelectedTags(record = false)
            }
            SearchEvent.OpenTags -> if (!_state.value.pickerOpen) {
                _state.value = _state.value.copy(pickerOpen = true, draftTags = _state.value.tags)
                lookupTags()
            }
            SearchEvent.CloseTags -> closeTags()
            SearchEvent.ApplyTags -> if (_state.value.pickerOpen) {
                _state.value = _state.value.copy(tags = _state.value.draftTags)
                closeTags()
                searchSelectedTags(record = true)
            }
            is SearchEvent.UseHistory -> _state.value.history.find { it.key == event.key }?.let { entry ->
                closeTags()
                if (entry.keyword != null) {
                    _state.value = _state.value.copy(mode = SearchMode.KEYWORD, keyword = entry.keyword)
                    search(SearchCriteria.Keyword(entry.keyword), record = true)
                } else {
                    _state.value = _state.value.copy(mode = SearchMode.TAG, tags = entry.tags)
                    searchSelectedTags(record = true)
                }
            }
            is SearchEvent.DeleteHistory -> if (!_state.value.historyBusy) {
                updateHistory { historyRepository.remove(it, event.key) }
            }
            SearchEvent.RequestClearHistory -> _state.value = _state.value.copy(clearHistoryOpen = true)
            SearchEvent.DismissClearHistory -> _state.value = _state.value.copy(clearHistoryOpen = false)
            SearchEvent.ClearHistory -> if (!_state.value.historyBusy) {
                _state.value = _state.value.copy(clearHistoryOpen = false)
                updateHistory { historyRepository.clear(it) }
            }
            SearchEvent.RetryHistory -> if (!_state.value.historyBusy) updateHistory { historyRepository.load(it) }
            SearchEvent.DismissHistoryError -> _state.value = _state.value.copy(historyError = false)
            is SearchEvent.TagQueryChanged -> {
                _state.value = _state.value.copy(
                    pickerOpen = true,
                    draftTags = if (_state.value.pickerOpen) _state.value.draftTags else _state.value.tags,
                    tagQuery = event.value,
                )
                lookupTags(debounce = true)
            }
            is SearchEvent.ToggleTag -> {
                if (!_state.value.pickerOpen) return
                val tags = _state.value.draftTags
                _state.value = _state.value.copy(draftTags = if (tags.any { it.id == event.tag.id }) {
                    tags.filterNot { it.id == event.tag.id }
                } else (tags + event.tag).distinctBy { it.id })
            }
            is SearchEvent.RemoveTag -> {
                _state.value = _state.value.copy(tags = _state.value.tags.filterNot { it.id == event.id })
                searchSelectedTags(record = true)
            }
            SearchEvent.RetryTags -> lookupTags()
            SearchEvent.Refresh -> _state.value.submitted?.let { search(it) }
            is SearchEvent.SortChanged -> {
                val current = _state.value
                _state.value = current.copy(
                    sort = event.sort,
                    descending = event.sort == SearchSort.DEFAULT || event.sort != current.sort || !current.descending,
                )
            }
            is SearchEvent.LayoutChanged -> _state.value = _state.value.copy(layout = event.layout)
            is SearchEvent.Scrolled -> if (event.generation == _state.value.generation) {
                _state.value = _state.value.copy(scrollIndex = event.index, scrollOffset = event.offset)
            }
        }
    }

    private fun updateHistory(operation: suspend (String?) -> List<SearchHistoryEntry>?) {
        val expected = revision
        val owner = session.value.activeUuid
        val version = ++historyVersion
        _state.value = _state.value.copy(historyBusy = true, historyError = false)
        // Local writes finish for their captured owner even when account-bound HTTP work is cancelled.
        viewModelScope.launch {
            val entries = operation(owner)
            if (expected == session.value.revision && version == historyVersion) {
                _state.value = _state.value.copy(
                    history = entries ?: _state.value.history,
                    historyLoaded = entries != null || _state.value.historyLoaded,
                    historyBusy = false,
                    historyError = entries == null,
                )
            }
        }
    }

    private fun resetResults(clearConditions: Boolean = false) {
        videoJob?.cancel()
        videoJob = null
        videoVersion++
        val current = _state.value
        val cleared = if (clearConditions) {
            when (current.mode) {
                SearchMode.KEYWORD -> current.copy(keyword = "")
                SearchMode.TAG -> current.copy(tags = emptyList())
            }
        } else current
        _state.value = cleared.copy(
            submitted = null, videos = ContentState(), generation = _state.value.generation + 1,
            scrollIndex = 0, scrollOffset = 0,
        )
    }

    private fun searchSelectedTags(record: Boolean) {
        if (_state.value.mode != SearchMode.TAG) return
        val ids = _state.value.tags.map { it.id }.distinct().sorted()
        if (ids.isEmpty()) resetResults() else search(SearchCriteria.Tags(ids), record)
    }

    private fun search(criteria: SearchCriteria, record: Boolean = false) {
        val current = _state.value
        val same = criteria == current.submitted
        if (same && current.videos.loading) return
        if (record) {
            val entry = when (criteria) {
                is SearchCriteria.Keyword -> SearchHistoryEntry(keyword = criteria.text)
                is SearchCriteria.Tags -> SearchHistoryEntry(tags = current.tags)
            }
            updateHistory { historyRepository.record(it, entry) }
        }
        videoJob?.cancel()
        val version = ++videoVersion
        val expected = revision
        _state.value = _state.value.copy(
            submitted = criteria,
            videos = ContentState(data = if (same) current.videos.data else null, loading = true),
            generation = if (same) current.generation else current.generation + 1,
            scrollIndex = if (same) current.scrollIndex else 0,
            scrollOffset = if (same) current.scrollOffset else 0,
        )
        videoJob = launchTask {
            try {
                val videos = when (criteria) {
                    is SearchCriteria.Keyword -> repository.searchVideos(criteria.text, expected)
                    is SearchCriteria.Tags -> repository.tagVideos(criteria.ids, expected)
                }
                if (expected == session.value.revision && version == videoVersion) {
                    _state.value = _state.value.copy(videos = ContentState(videos))
                }
            } catch (error: ApiException) {
                if (expected == session.value.revision && version == videoVersion) {
                    _state.value = _state.value.copy(
                        videos = _state.value.videos.readFailed(error.failure),
                    )
                }
            }
        }
    }

    private fun closeTags() {
        tagJob?.cancel()
        tagVersion++
        _state.value = _state.value.copy(pickerOpen = false, draftTags = emptyList(), candidates = ContentState())
    }

    private fun lookupTags(debounce: Boolean = false) {
        tagJob?.cancel()
        val version = ++tagVersion
        val expected = revision
        val query = _state.value.tagQuery.trim()
        _state.value = _state.value.copy(candidates = ContentState())
        if (!_state.value.pickerOpen || query.isEmpty()) return
        _state.value = _state.value.copy(candidates = ContentState(loading = true))
        tagJob = launchTask {
            if (debounce) delay(500.milliseconds)
            try {
                val tags = repository.searchTags(query, expected)
                if (expected == session.value.revision && version == tagVersion) {
                    _state.value = _state.value.copy(candidates = ContentState(tags))
                }
            } catch (error: ApiException) {
                if (expected == session.value.revision && version == tagVersion) {
                    _state.value = _state.value.copy(candidates = ContentState(error = error.failure))
                }
            }
        }
    }
}

internal fun sortedSearchVideos(videos: List<VideoSummary>, sort: SearchSort, descending: Boolean): List<VideoSummary> {
    if (sort == SearchSort.DEFAULT) return videos
    fun value(video: VideoSummary): Long? = when (sort) {
        SearchSort.UPLOAD_DATE -> video.uploadedAt
        SearchSort.VIEWS -> video.views
        SearchSort.DURATION -> video.durationMs
        SearchSort.DEFAULT -> null
    }
    // Keep missing metadata last in both directions; sortedWith is stable for equal values.
    return videos.sortedWith { first, second ->
        val a = value(first)
        val b = value(second)
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }
}
