package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ExpressiveMenuPopup

@Composable
internal fun VideoActionsMenu(
    videoId: Int,
    coverAvailable: Boolean,
    onViewCover: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (() -> Unit) -> Unit,
) {
    var expanded by remember(videoId) { mutableStateOf(false) }
    Box(modifier) {
        anchor { expanded = true }
        ExpressiveMenuPopup(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                SelectableDropdownMenuItem(
                    selected = false,
                    enabled = coverAvailable,
                    onClick = {
                        expanded = false
                        onViewCover()
                    },
                    text = { Text(stringResource(R.string.video_view_cover)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_image), contentDescription = null) },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                    colors = MenuDefaults.selectableItemColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
