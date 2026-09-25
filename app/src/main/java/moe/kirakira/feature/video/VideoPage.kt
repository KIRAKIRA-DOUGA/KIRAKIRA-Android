package moe.kirakira.feature.video

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import moe.kirakira.R

internal enum class VideoTab { INTRODUCTION, COMMENTS, DANMAKU }

internal enum class VideoReaction { NONE, LIKE, DISLIKE }

internal data class VideoUiState(
    val tab: VideoTab = VideoTab.INTRODUCTION,
    val following: Boolean = false,
    val reaction: VideoReaction = VideoReaction.NONE,
    val saved: Boolean = false,
)

@Composable
internal fun VideoPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(VideoTab.INTRODUCTION) }
    var following by rememberSaveable { mutableStateOf(false) }
    var reaction by rememberSaveable { mutableStateOf(VideoReaction.NONE) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val introductionScrollState = rememberLazyListState()
    val commentsScrollState = rememberLazyListState()
    val danmakuScrollState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.video_action_unavailable)

    VideoScreen(
        state = VideoUiState(tab, following, reaction, saved),
        listState = when (tab) {
            VideoTab.INTRODUCTION -> introductionScrollState
            VideoTab.COMMENTS -> commentsScrollState
            VideoTab.DANMAKU -> danmakuScrollState
        },
        snackbarHostState = snackbarHostState,
        onTabChange = { tab = it },
        onFollowingChange = { following = it },
        onReactionChange = { reaction = if (reaction == it) VideoReaction.NONE else it },
        onSavedChange = { saved = it },
        onUnavailableAction = {
            scope.launch {
                if (snackbarHostState.currentSnackbarData == null) {
                    snackbarHostState.showSnackbar(unavailable)
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}
