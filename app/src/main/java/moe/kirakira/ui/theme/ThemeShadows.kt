package moe.kirakira.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

internal val LocalShadowsEnabled = staticCompositionLocalOf { false }

// Material's standard elevations: app bar 4dp, bottom navigation 8dp.
// https://m1.material.io/material-design/elevation-shadows.html
private val TopAppBarShadowElevation = 4.dp
private val NavigationBarShadowElevation = 8.dp

@Composable
internal fun Modifier.topAppBarShadow(): Modifier = shadow(
    elevation = if (LocalShadowsEnabled.current) TopAppBarShadowElevation else 0.dp,
    shape = RectangleShape,
    clip = false,
)

@Composable
internal fun Modifier.navigationBarShadow(): Modifier = shadow(
    elevation = if (LocalShadowsEnabled.current) NavigationBarShadowElevation else 0.dp,
    shape = RectangleShape,
    clip = false,
)
