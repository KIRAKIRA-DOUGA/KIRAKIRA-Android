package moe.kirakira.feature.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.settings.DanmakuSettingsContent
import moe.kirakira.feature.settings.PlaybackSettingsContent
import moe.kirakira.ui.components.PagerTabRow
import moe.kirakira.ui.components.rememberTabChangeHandler

@Composable
internal fun PlayerPreferencesContent(
    pagerState: PagerState,
    playbackSettings: PlaybackSettings?,
    danmakuSettings: DanmakuSettings?,
    onOutsideAppMiniPlayerChange: (Boolean) -> Unit,
    onInAppMiniPlayerChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val changeTab = rememberTabChangeHandler(pagerState)
    val playbackScroll = rememberScrollState()
    val danmakuScroll = rememberScrollState()
    val titles = listOf(stringResource(R.string.player_settings_playback), stringResource(R.string.settings_danmaku))
    // Constrain content only: the sheet needs full window constraints to calculate its bottom anchor.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val contentHeight = maxHeight * 0.9f
        Column(
            Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Text(
                stringResource(R.string.player_settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .semantics { heading() },
            )
            PagerTabRow(
                pagerState = pagerState,
                titles = titles,
                onTabChange = changeTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(if (page == 0) playbackScroll else danmakuScroll)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(24.dp),
                ) {
                    if (page == 0) {
                        PlaybackSettingsContent(
                            settings = playbackSettings,
                            onOutsideAppMiniPlayerChange = onOutsideAppMiniPlayerChange,
                            onInAppMiniPlayerChange = onInAppMiniPlayerChange,
                            onAutoplayChange = onAutoplayChange,
                        )
                    } else {
                        DanmakuSettingsContent(settings = danmakuSettings, onChange = onDanmakuSettingsChange)
                    }
                }
            }
        }
    }
}
