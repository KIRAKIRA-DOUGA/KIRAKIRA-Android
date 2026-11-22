package moe.kirakira.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlayerContentFrame(
    player: Player,
    modifier: Modifier = Modifier,
    shutter: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(Color.Black)) },
) {
    // Before Android 12, SurfaceView clears the canvas clip rather than its own rectangle.
    Box(modifier.clipToBounds()) {
        ContentFrame(
            player = player,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            shutter = shutter,
        )
    }
}
