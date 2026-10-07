package moe.kirakira.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.ui.theme.BarShadowElevation
import moe.kirakira.ui.theme.barSurfaceLayer

private val LocalAppBarHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
internal fun Modifier.frostedBarBackground(
    shape: Shape = RectangleShape,
    hazeState: HazeState? = LocalAppBarHazeState.current,
    shadowElevation: Dp = 0.dp,
): Modifier {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 1f)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hazeState == null) {
        return barSurfaceLayer(
            shape = shape,
            alpha = 0.9f,
            shadowElevation = shadowElevation,
        ).background(surface)
    }
    val input = remember(hazeState) {
        HazeInput.Sources(
            state = hazeState,
            retention = HazeSourceRetention.ClearWhenUnavailable,
        )
    }
    val style = remember(surface) {
        HazeBlurStyle {
            backgroundColor(surface)
            blurRadius(20.dp)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = 0.8f))))
        }
    }
    return barSurfaceLayer(shape = shape, shadowElevation = shadowElevation).hazeBlur(
        input = input,
        style = style,
    )
}

@Composable
internal fun FrostedScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    hazeState: HazeState = rememberHazeState(),
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalAppBarHazeState provides hazeState) {
        Scaffold(
            modifier = modifier,
            topBar = {
                Box {
                    Box(
                        Modifier.matchParentSize().frostedBarBackground(shadowElevation = BarShadowElevation),
                    )
                    topBar()
                }
            },
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets,
        ) { padding ->
            Box(
                Modifier.fillMaxSize().then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(hazeState) else Modifier,
                ),
            ) {
                content(padding)
            }
        }
    }
}
