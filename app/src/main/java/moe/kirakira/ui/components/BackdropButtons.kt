package moe.kirakira.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

private object BackdropButtonDefaults {
    val containerColor = Color.Black.copy(alpha = 0.6f)
    val disabledContainerColor = Color.Black.copy(alpha = 0.3f)
    val disabledContentColor = Color.White.copy(alpha = 0.38f)
    val blurStyle = HazeBlurStyle {
        backgroundColor(Color.Transparent)
        blurRadius(20.dp)
        noiseFactor(0f)
        colorEffects(emptyList())
    }
}

// The flag is read when effect nodes attach, so enable it before composing any buttons.
@OptIn(ExperimentalHazeApi::class)
internal fun enableButtonBackdrops() {
    HazeFeatureFlags.isPlatformBackdropEnabled = true
}

@Composable
internal fun BackdropIconButton(
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    shape: Shape = IconButtonDefaults.filledShape,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = BackdropButtonDefaults.disabledContentColor,
        ),
        interactionSource = interactionSource,
    ) {
        // The official surface clips the backdrop to its visible shape, outside the touch-target padding.
        Box(
            modifier = Modifier.fillMaxSize().buttonBackdropBackground(hazeState, enabled),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun Modifier.buttonBackdropBackground(hazeState: HazeState, enabled: Boolean): Modifier {
    val input = remember(hazeState) {
        HazeInput.Backdrop(
            fallback = HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable),
        )
    }
    return then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.hazeBlur(input = input, style = BackdropButtonDefaults.blurStyle)
        } else {
            Modifier
        },
    ).background(
        if (enabled) BackdropButtonDefaults.containerColor else BackdropButtonDefaults.disabledContainerColor,
    )
}
