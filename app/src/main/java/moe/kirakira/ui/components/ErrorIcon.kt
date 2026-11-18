package moe.kirakira.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import moe.kirakira.R

@Composable
internal fun ErrorIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_icon_error),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error,
    )
}
