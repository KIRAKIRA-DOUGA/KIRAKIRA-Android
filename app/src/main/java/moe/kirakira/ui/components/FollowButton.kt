package moe.kirakira.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import moe.kirakira.R

@Composable
internal fun FollowButton(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
    loading: Boolean = false,
) {
    var visualFollowing by remember { mutableStateOf(following) }
    LaunchedEffect(following, enabled, loading) {
        if (enabled && !loading) visualFollowing = following
    }
    val colorScheme = MaterialTheme.colorScheme
    ShadowToggleButton(
        checked = following,
        onCheckedChange = onFollowingChange,
        modifier = modifier,
        buttonSize = buttonSize,
        enabled = enabled,
        loading = loading,
        icon = { checked ->
            Icon(
                painter = painterResource(if (checked) R.drawable.ic_icon_check else R.drawable.ic_icon_add),
                contentDescription = null,
            )
        },
        colors = ToggleButtonDefaults.colors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            checkedContainerColor = colorScheme.surfaceContainerHigh,
            checkedContentColor = colorScheme.onSurfaceVariant,
        ),
        visualChecked = visualFollowing,
        shadowWhenChecked = false,
    ) { checked ->
        Text(
            text = stringResource(if (checked) R.string.video_following else R.string.video_follow),
        )
    }
}
