package moe.kirakira.feature.main

import android.view.animation.AnimationUtils
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.navigation.activityInterpolatorResource
import moe.kirakira.ui.theme.KIRAKIRATheme

internal enum class MainTabIcon(@param:DrawableRes val resource: Int) {
    HOME(R.drawable.ic_symbol_home),
    SEARCH(R.drawable.ic_symbol_search),
    FOLLOWING(R.drawable.ic_symbol_wifi_tethering),
    ME(R.drawable.ic_symbol_person),
}

@Composable
internal fun AnimatedTabIcon(
    icon: MainTabIcon,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (icon == MainTabIcon.FOLLOWING) {
        FollowingTabIcon(selected = selected, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val easing = remember(context) {
        val interpolator = AnimationUtils.loadInterpolator(context, activityInterpolatorResource())
        Easing { interpolator.getInterpolation(it) }
    }
    val fillProgress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = easing),
        label = "Tab icon fill",
    )
    val neutral = if (icon == MainTabIcon.SEARCH) 0f else 1f
    val impulse = remember(icon) { Animatable(neutral) }
    var previouslySelected by remember(icon) { mutableStateOf(selected) }

    LaunchedEffect(icon, selected) {
        // Start at the restored selection without replaying an entrance pulse.
        if (previouslySelected == selected) return@LaunchedEffect
        previouslySelected = selected
        if (!selected) {
            impulse.animateTo(neutral, tween(durationMillis = 120, easing = easing))
            return@LaunchedEffect
        }

        // A new selection cancels the previous job and continues from its current value.
        val start = impulse.value
        impulse.animateTo(
            targetValue = neutral,
            animationSpec = keyframes {
                durationMillis = 320
                start at 0 using FastOutSlowInEasing
                when (icon) {
                    MainTabIcon.SEARCH -> {
                        -12f at 80 using FastOutSlowInEasing
                        8f at 200 using FastOutSlowInEasing
                    }
                    else -> {
                        0.94f at 64 using FastOutSlowInEasing
                        1.08f at 160 using FastOutSlowInEasing
                    }
                }
                neutral at durationMillis
            },
        )
    }

    val iconModifier = modifier.size(24.dp).graphicsLayer {
        if (icon == MainTabIcon.SEARCH) {
            rotationZ = impulse.value
        } else {
            scaleX = impulse.value
            scaleY = impulse.value
        }
    }
    val morphPath = remember(icon) {
        when (icon) {
            MainTabIcon.HOME -> TabIconMorphPath(homeIconMorph)
            MainTabIcon.ME -> TabIconMorphPath(personIconMorph)
            else -> null
        }
    }
    if (morphPath == null) {
        Icon(painterResource(icon.resource), contentDescription = null, modifier = iconModifier)
    } else {
        val color = LocalContentColor.current
        Canvas(modifier = iconModifier) {
            scale(size.width / 960f, size.height / 960f, pivot = Offset.Zero) {
                drawPath(morphPath.pathAt(fillProgress.value), color)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnimatedTabIconPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MainTabIcon.entries.forEach { icon ->
                AnimatedTabIcon(icon = icon, selected = false)
                AnimatedTabIcon(icon = icon, selected = true)
            }
        }
    }
}
