package moe.kirakira.feature.video

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    Box(modifier = modifier.clickable(role = Role.Button, onClick = onClick)) {
        if (layout == VideoCardLayout.GRID) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VideoArtwork(
                    image = image,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large),
                )
                VideoCardInfo(
                    title = title,
                    uploader = uploader,
                    viewCount = viewCount,
                    duration = duration,
                    uploadTime = uploadTime,
                    layout = layout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoArtwork(
                    image = image,
                    modifier = Modifier.size(width = 128.dp, height = 72.dp).clip(MaterialTheme.shapes.medium),
                )
                VideoCardInfo(
                    title = title,
                    uploader = uploader,
                    viewCount = viewCount,
                    duration = duration,
                    uploadTime = uploadTime,
                    layout = layout,
                    modifier = Modifier.weight(1f),
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
    layout: VideoCardLayout,
    modifier: Modifier = Modifier,
) {
    val isGrid = layout == VideoCardLayout.GRID
    val informationSpacing = if (isGrid) 6.dp else 8.dp
    val iconSize = if (isGrid) 16.dp else 20.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(informationSpacing)) {
        Column(verticalArrangement = Arrangement.spacedBy(if (isGrid) 2.dp else 8.dp)) {
            if (uploader != null) {
                Text(
                    text = uploader,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = if (isGrid) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isGrid) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = if (isGrid) 2 else 1,
                maxLines = if (isGrid) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            VideoCardMetadata(
                icon = R.drawable.ic_symbol_play_circle,
                text = viewCount,
                description = stringResource(R.string.video_views_description, viewCount),
                iconSize = iconSize,
            )
            VideoCardMetadata(
                icon = R.drawable.ic_symbol_schedule,
                text = duration,
                description = stringResource(R.string.video_duration, duration),
                iconSize = iconSize,
            )
            VideoCardMetadata(
                icon = R.drawable.ic_symbol_calendar_today,
                text = uploadTime,
                description = stringResource(R.string.video_upload_time_description, uploadTime),
                iconSize = iconSize,
            )
        }
    }
}

@Composable
private fun VideoCardMetadata(
    @DrawableRes icon: Int,
    text: String,
    description: String,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
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
