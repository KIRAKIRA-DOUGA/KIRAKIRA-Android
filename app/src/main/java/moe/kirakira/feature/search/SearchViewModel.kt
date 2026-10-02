package moe.kirakira.feature.search

import androidx.annotation.StringRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.content.VideoTag
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.ContentViewModel
import moe.kirakira.feature.video.isReadyForContent

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
    val mode: SearchMode = SearchMode.KEYWORD,
    val keyword: String = "",
    val tags: List<VideoTag> = emptyList(),
    val submitted: SearchCriteria? = null,
    val videos: ContentState<List<VideoSummary>> = ContentState(),
    val pickerOpen: Boolean = false,
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
    data class TagQueryChanged(val value: String) : SearchEvent
    data class ToggleTag(val tag: VideoTag) : SearchEvent
    data class RemoveTag(val id: Long) : SearchEvent
    data object RetryTags : SearchEvent
    data object Refresh : SearchEvent
    data class SortChanged(val sort: SearchSort) : SearchEvent
    data object ToggleDirection : SearchEvent
    data class LayoutChanged(val layout: VideoCardLayout) : SearchEvent
    data class Scrolled(val generation: Long, val index: Int, val offset: Int) : SearchEvent
}

internal class SearchViewModel(repository: ContentRepository) : ContentViewModel(repository) {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
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
            _state.value = SearchUiState(ready = true, generation = _state.value.generation + 1)
        }, {})
    }

    fun onEvent(event: SearchEvent) {
        if (event is SearchEvent.Scrolled) {
            if (event.generation == _state.value.generation) {
                _state.value = _state.value.copy(scrollIndex = event.index, scrollOffset = event.offset)
            }
            return
        }
        if (!session.value.isReadyForContent || session.value.revision != revision) return
        when (event) {
            is SearchEvent.KeywordChanged -> _state.value = _state.value.copy(keyword = event.value)
            SearchEvent.Submit -> {
                val keyword = _state.value.keyword.trim()
                if (keyword.isEmpty()) resetResults()
                else if (_state.value.mode == SearchMode.KEYWORD) {
                    _state.value = _state.value.copy(keyword = keyword)
                    search(SearchCriteria.Keyword(keyword))
                }
            }
            SearchEvent.Clear -> {
                _state.value = _state.value.copy(keyword = "")
                resetResults()
            }
            is SearchEvent.ModeChanged -> if (event.mode != _state.value.mode) {
                closeTags()
                _state.value = _state.value.copy(mode = event.mode)
                resetResults()
                if (event.mode == SearchMode.TAG) searchSelectedTags()
            }
            SearchEvent.OpenTags -> {
                _state.value = _state.value.copy(pickerOpen = true)
                lookupTags()
            }
            SearchEvent.CloseTags -> closeTags()
            is SearchEvent.TagQueryChanged -> {
                _state.value = _state.value.copy(tagQuery = event.value)
                lookupTags(debounce = true)
            }
            is SearchEvent.ToggleTag -> {
                val tags = _state.value.tags
                _state.value = _state.value.copy(tags = if (tags.any { it.id == event.tag.id }) {
                    tags.filterNot { it.id == event.tag.id }
                } else (tags + event.tag).distinctBy { it.id })
                searchSelectedTags()
            }
            is SearchEvent.RemoveTag -> {
                _state.value = _state.value.copy(tags = _state.value.tags.filterNot { it.id == event.id })
                searchSelectedTags()
            }
            SearchEvent.RetryTags -> lookupTags()
            SearchEvent.Refresh -> _state.value.submitted?.let { search(it) }
            is SearchEvent.SortChanged -> _state.value = _state.value.copy(sort = event.sort, descending = true)
            SearchEvent.ToggleDirection -> _state.value = _state.value.copy(descending = !_state.value.descending)
            is SearchEvent.LayoutChanged -> _state.value = _state.value.copy(layout = event.layout)
            is SearchEvent.Scrolled -> Unit
        }
    }

    private fun resetResults() {
        videoJob?.cancel()
        videoVersion++
        _state.value = _state.value.copy(
            submitted = null, videos = ContentState(), generation = _state.value.generation + 1,
            scrollIndex = 0, scrollOffset = 0,
        )
    }

    private fun searchSelectedTags() {
        if (_state.value.mode != SearchMode.TAG) return
        val ids = _state.value.tags.map { it.id }.distinct().sorted()
        if (ids.isEmpty()) resetResults() else search(SearchCriteria.Tags(ids))
    }

    private fun search(criteria: SearchCriteria) {
        val current = _state.value
        val same = criteria == current.submitted
        if (same && current.videos.loading) return
        videoJob?.cancel()
        val version = ++videoVersion
        val expected = revision
        _state.value = current.copy(
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
                        videos = _state.value.videos.copy(loading = false, error = error.failure),
                    )
                }
            }
        }
    }

    private fun closeTags() {
        tagJob?.cancel()
        tagVersion++
        _state.value = _state.value.copy(pickerOpen = false, candidates = ContentState())
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
            if (debounce) delay(500)
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
