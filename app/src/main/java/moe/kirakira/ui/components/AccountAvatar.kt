package moe.kirakira.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.kirakira.core.image.deliveryImageUrl

@Composable
internal fun AccountAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    clipToCircle: Boolean = true,
    showLoadingIndicator: Boolean = false,
) {
    val imageUrl = deliveryImageUrl(url, with(LocalDensity.current) { size.roundToPx() })
    var loading by remember(imageUrl) { mutableStateOf(imageUrl != null) }
    Box(modifier.size(size).then(if (clipToCircle) Modifier.clip(CircleShape) else Modifier)) {
        if (showLoadingIndicator && loading) {
            Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        } else {
            PlaceholderAvatar(size = size)
        }
        if (imageUrl != null) {
            // Public avatar requests use Coil's independent client, never the API session cookies.
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onLoading = { loading = true },
                onSuccess = { loading = false },
                onError = { loading = false },
            )
        }
        if (showLoadingIndicator && loading) {
            IndeterminateCircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
