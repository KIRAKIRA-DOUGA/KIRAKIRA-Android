package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import moe.kirakira.core.image.deliveryImageUrl

@Composable
internal fun AccountAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    clipToCircle: Boolean = true,
) {
    val imageUrl = deliveryImageUrl(url, with(LocalDensity.current) { size.roundToPx() })
    Box(modifier.size(size).then(if (clipToCircle) Modifier.clip(CircleShape) else Modifier)) {
        PlaceholderAvatar(size = size)
        if (imageUrl != null) {
            // Public avatar requests use Coil's independent client, never the API session cookies.
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
