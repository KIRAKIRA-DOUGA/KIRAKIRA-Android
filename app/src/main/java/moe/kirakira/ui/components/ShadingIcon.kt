package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Place behind a top bar with Modifier.matchParentSize() inside a Box so the decoration
 * does not affect measurement and can extend behind the status bar.
 * Positive offset.x follows the layout direction; overflow is clipped to the decoration area.
 */
@Composable
fun ShadingIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    rotating: Boolean = false,
    endPadding: Dp = 16.dp,
    alignment: Alignment = Alignment.CenterEnd,
    offset: DpOffset = DpOffset.Zero,
) {
    val rotation = if (rotating) {
        rememberInfiniteTransition(label = "ShadingIcon").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30_000, easing = LinearEasing),
            ),
            label = "ShadingIconRotation",
        )
    } else null

    Box(
        modifier = modifier
            .clipToBounds()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal))
            .padding(end = endPadding),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .align(alignment)
                .wrapContentSize(align = alignment, unbounded = true)
                .offset(x = offset.x, y = offset.y)
                .requiredSize(128.dp)
                .graphicsLayer {
                    alpha = 0.2f
                    rotationZ = rotation?.value ?: 0f
                },
            tint = MaterialTheme.colorScheme.primaryFixed,
        )
    }
}
