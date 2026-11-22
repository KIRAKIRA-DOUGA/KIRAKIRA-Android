package moe.kirakira.feature.player

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.button.LoadingIconButton
import moe.kirakira.ui.components.selection.SmoothSlider
import moe.kirakira.ui.components.video.durationText

@Composable
internal fun TabletopTransport(
    showPause: Boolean,
    loading: Boolean,
    canPlay: Boolean,
    canSeek: Boolean,
    position: Long,
    duration: Long,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember(canSeek, duration) { mutableStateOf<Float?>(null) }
    val slider = rememberSliderState()
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val displayPosition = dragged?.let { (it * duration).toLong() } ?: position
    SideEffect {
        slider.value = (displayPosition.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    LaunchedEffect(interaction, canSeek, duration) {
        interaction.interactions.collect { event ->
            if (event is DragInteraction.Cancel || event is PressInteraction.Cancel && !dragging) dragged = null
        }
    }
    val seekLabel = stringResource(R.string.player_seek)
    val playLabel = stringResource(if (showPause) R.string.player_pause else R.string.player_play)
    val motion = rememberPlaybackIconMotion(showPause)
    Row(modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LoadingIconButton(
            onClick = onToggle,
            enabled = canPlay,
            loading = loading,
            indicatorColor = MaterialTheme.colorScheme.primary,
        ) {
            AnimatedPlaybackIcon(motion, playLabel)
        }
        SmoothSlider(
            state = slider,
            onValueChange = { value -> slider.value = value; dragged = value },
            onValueChangeFinished = {
                dragged?.let { onSeek((it * duration).toLong()) }
                dragged = null
            },
            interactionSource = interaction,
            enabled = canSeek,
            modifier = Modifier.weight(1f).padding(end = 8.dp).semantics { contentDescription = seekLabel },
        )
        Text(
            "${durationText(displayPosition)} / ${durationText(duration)}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
