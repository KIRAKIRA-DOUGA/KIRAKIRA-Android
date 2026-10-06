package moe.kirakira.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.kirakira.ui.theme.ThemePresetColor
import moe.kirakira.ui.theme.formatThemeColor

@Composable
internal fun ThemeColorCard(
    preset: ThemePresetColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(context, preset.artworkRes) {
        ImageRequest.Builder(context)
            .data(preset.artworkRes)
            .crossfade(false)
            .build()
    }
    ThemeSelectionCard(
        title = stringResource(preset.titleRes),
        subtitle = stringResource(preset.characterRes),
        seedColor = preset.color,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.84f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun ThemeColorSourceCard(
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    ThemeSelectionCard(
        title = title,
        subtitle = formatThemeColor(color.toArgb()),
        seedColor = color,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.align(Alignment.Center).size(48.dp),
        )
    }
}
