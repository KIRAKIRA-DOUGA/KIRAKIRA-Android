@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tonal roles for [IconBadge]; each maps to a container/content pair of the color scheme. */
enum class IconBadgeTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL, ERROR }

/**
 * Expressive leading icon: a MaterialShapes container tinted with a tonal role. Colors animate so
 * state-driven tone changes (for example privacy visibility) stay smooth.
 */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    tone: IconBadgeTone = IconBadgeTone.SECONDARY,
    size: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        IconBadgeTone.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        IconBadgeTone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        IconBadgeTone.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        IconBadgeTone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        IconBadgeTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val containerColor by animateColorAsState(
        if (enabled) container else scheme.onSurface.copy(alpha = 0.08f), spec, label = "badgeContainer",
    )
    val contentColor by animateColorAsState(
        if (enabled) content else scheme.onSurface.copy(alpha = 0.38f), spec, label = "badgeContent",
    )
    Surface(modifier = modifier.size(size), shape = shape, color = containerColor, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(size * 0.5f))
        }
    }
}
