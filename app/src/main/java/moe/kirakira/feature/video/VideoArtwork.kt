package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl

@Composable
internal fun VideoArtwork(image: String?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_symbol_video_library), contentDescription = null)
            AsyncImage(model = deliveryImageUrl(image, 720), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
