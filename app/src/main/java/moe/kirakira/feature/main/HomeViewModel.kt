package moe.kirakira.feature.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.ContentViewModel

internal class HomeViewModel(repository: ContentRepository) : ContentViewModel(repository) {
    private val _videos = MutableStateFlow(ContentState<List<VideoSummary>>(loading = true))
    val videos = _videos.asStateFlow()
    init { observeAccount({ _videos.value = ContentState(loading = true) }, ::refresh) }
    fun refresh() = load(_videos, repository::home)
}
