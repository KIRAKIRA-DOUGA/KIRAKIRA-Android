package moe.kirakira.feature.tag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.content.VideoTag
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.ContentViewModel

internal data class TagLookup(val tag: VideoTag?)

internal class TagViewModel(val tagId: Long, repository: ContentRepository) : ContentViewModel(repository) {
    private val _tag = MutableStateFlow(ContentState<TagLookup>(loading = true))
    val tag = _tag.asStateFlow()
    private val _videos = MutableStateFlow(ContentState<List<VideoSummary>>(loading = true))
    val videos = _videos.asStateFlow()

    init {
        observeAccount({
            _tag.value = ContentState(loading = true)
            _videos.value = ContentState(loading = true)
        }, ::refresh)
    }

    fun refreshTag() = load(_tag) { TagLookup(repository.tag(tagId, it)) }
    fun refreshVideos() = load(_videos) { repository.tagVideos(tagId, it) }
    fun refresh() {
        refreshTag()
        refreshVideos()
    }
}
