package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.ui.components.MenuPopup
import moe.kirakira.ui.components.menuItemColors
import moe.kirakira.ui.components.menuItemShapes

@Composable
internal fun VideoActionsMenu(
    videoId: Int,
    coverAvailable: Boolean,
    onViewCover: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (() -> Unit) -> Unit,
) {
    var expanded by remember(videoId) { mutableStateOf(false) }
    Box(modifier) {
        anchor { expanded = true }
        MenuPopup(expanded, onDismissRequest = { expanded = false }) {
            SelectableDropdownMenuItem(
                selected = false,
                onClick = {
                    expanded = false
                    onUnavailable()
                },
                text = { Text(stringResource(R.string.video_watch_later)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_icon_watch_later), contentDescription = null) },
                shapes = menuItemShapes(),
                colors = menuItemColors(),
            )
            SelectableDropdownMenuItem(
                selected = false,
                enabled = coverAvailable,
                onClick = {
                    expanded = false
                    onViewCover()
                },
                text = { Text(stringResource(R.string.video_view_cover)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_icon_photo), contentDescription = null) },
                shapes = menuItemShapes(),
                colors = menuItemColors(),
            )
            SelectableDropdownMenuItem(
                selected = false,
                onClick = {
                    expanded = false
                    onUnavailable()
                },
                text = { Text(stringResource(R.string.video_download)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_icon_download), contentDescription = null) },
                shapes = menuItemShapes(),
                colors = menuItemColors(),
            )
            SelectableDropdownMenuItem(
                selected = false,
                onClick = {
                    expanded = false
                    onUnavailable()
                },
                text = { Text(stringResource(R.string.video_report)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_icon_flag), contentDescription = null) },
                shapes = menuItemShapes(),
                colors = menuItemColors(),
            )
        }
    }
}
