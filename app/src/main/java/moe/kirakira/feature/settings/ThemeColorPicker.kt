package moe.kirakira.feature.settings

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
import moe.kirakira.R
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemePresetColor
import moe.kirakira.ui.theme.formatThemeColor

@Composable
internal fun ThemeColorCard(
    option: String,
    settings: ThemeColorSettings,
    wallpaperColor: Color?,
    onSettingsChange: (ThemeColorSettings) -> Unit,
    onCustomColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usesSystemColors = settings.useSystemColors && wallpaperColor != null
    val preset = ThemePresetColor.entries.firstOrNull { it.name == option }
    if (preset != null) {
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
            selected = !usesSystemColors && !settings.useCustomColor &&
                settings.seedColorArgb == preset.color.toArgb(),
            onClick = { onSettingsChange(settings.selectPreset(preset)) },
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
    } else {
        val custom = option == "custom"
        val color = if (custom) Color(settings.customColorArgb) else wallpaperColor ?: Color(settings.seedColorArgb)
        ThemeSelectionCard(
            title = stringResource(if (custom) R.string.theme_color_custom else R.string.theme_color_wallpaper),
            subtitle = formatThemeColor(color.toArgb()),
            seedColor = color,
            leadingIcon = if (custom) R.drawable.ic_symbol_edit else null,
            selected = if (custom) !usesSystemColors && settings.useCustomColor else usesSystemColors,
            onClick = {
                if (custom) onCustomColor() else onSettingsChange(settings.selectWallpaperColor())
            },
            modifier = modifier,
        ) {
            Icon(
                painter = painterResource(if (custom) R.drawable.ic_symbol_edit else R.drawable.ic_symbol_wallpaper),
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }
    }
}
