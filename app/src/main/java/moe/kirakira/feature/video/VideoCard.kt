package moe.kirakira.feature.video

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.settings.VideoCardLayout

@Composable
internal fun VideoCard(
    title: String,
    uploader: String?,
    viewCount: String,
    duration: String,
    uploadTime: String,
    layout: VideoCardLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (layout == VideoCardLayout.GRID) {
            VideoArtwork(image, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            VideoCardInfo(
                title = title,
                uploader = uploader,
                viewCount = viewCount,
                duration = duration,
                uploadTime = uploadTime,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoArtwork(
                    image, Modifier.weight(0.4f).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium),
                )
                VideoCardInfo(
                    title = title,
                    uploader = uploader,
                    viewCount = viewCount,
                    duration = duration,
                    uploadTime = uploadTime,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
    }
}

@Composable
private fun VideoCardInfo(
    title: String,
    uploader: String?,
    viewCount: String,
    duration: String,
    uploadTime: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (uploader != null) {
            Text(
                text = uploader,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoCardMetadata(
                icon = R.drawable.ic_symbol_play_circle,
                text = viewCount,
                description = stringResource(R.string.video_views_description, viewCount),
                modifier = Modifier.weight(1f),
            )
            VideoCardMetadata(
                icon = R.drawable.ic_symbol_schedule,
                text = duration,
                description = stringResource(R.string.video_duration, duration),
                modifier = Modifier.weight(1f),
            )
        }
        VideoCardMetadata(
            icon = R.drawable.ic_symbol_calendar_today,
            text = uploadTime,
            description = stringResource(R.string.video_upload_time_description, uploadTime),
        )
    }
}

@Composable
private fun VideoCardMetadata(
    @DrawableRes icon: Int,
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun VideoCardRow(
    videos: List<VideoSummary>,
    layout: VideoCardLayout,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showUploader: Boolean = true,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        videos.forEach { video ->
            key(video.id) {
                VideoCard(
                    title = video.title,
                    uploader = if (showUploader) {
                        video.author.ifBlank { stringResource(R.string.content_unknown_author) }
                    } else {
                        null
                    },
                    viewCount = video.views?.let { stringResource(R.string.video_views, it) } ?: "—",
                    duration = durationText(video.durationMs),
                    uploadTime = dateText(video.uploadedAt),
                    layout = layout,
                    image = video.image,
                    onClick = { onOpenVideo(video.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat((layout.columns - videos.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}
