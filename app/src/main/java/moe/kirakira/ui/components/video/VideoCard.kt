package moe.kirakira.ui.components.video

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import moe.kirakira.R
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun VideoCard(
    title: String,
    uploader: String?,
    viewCount: String,
    duration: String,
    uploadTime: String?,
    layout: VideoCardLayout,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: String? = null,
    videoId: Int? = null,
) {
    val artworkShape = MaterialTheme.shapes.medium
    val zoomSource = rememberVideoZoomSource(videoId, image, artworkShape)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button) {
                if (zoomSource == null) onClick() else zoomSource.open(onClick)
            }
    ) {
        if (layout == VideoCardLayout.GRID) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (uploader == null) 8.dp else 4.dp),
            ) {
                VideoArtwork(
                    image = image,
                    onPainterChange = { zoomSource?.painter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(artworkShape)
                        .videoZoomSource(zoomSource),
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
                    onPainterChange = { zoomSource?.painter = it },
                    modifier = Modifier
                        .size(width = 128.dp, height = 72.dp)
                        .clip(artworkShape)
                        .videoZoomSource(zoomSource),
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
    uploadTime: String?,
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
                icon = R.drawable.ic_icon_play,
                text = viewCount,
                description = stringResource(R.string.video_views_description, viewCount),
                iconSize = iconSize,
            )
            VideoCardMetadata(
                icon = R.drawable.ic_icon_duration,
                text = duration,
                description = stringResource(R.string.video_duration, duration),
                iconSize = iconSize,
            )
            if (uploadTime != null) {
                VideoCardMetadata(
                    icon = R.drawable.ic_icon_calendar,
                    text = uploadTime,
                    description = stringResource(R.string.video_upload_time_description, uploadTime),
                    iconSize = iconSize,
                )
            }
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
    showUploadTime: Boolean = true,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    uploadTime = if (showUploadTime) dateText(video.uploadedAt) else null,
                    layout = layout,
                    image = video.image,
                    videoId = video.id,
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

@Preview(name = "Grid", group = "Video cards", widthDp = 360)
@Composable
private fun VideoCardGridPreview() = VideoCardRowsPreview(VideoCardLayout.GRID, showUploadTime = true)

@Preview(name = "List", group = "Video cards", widthDp = 360)
@Composable
private fun VideoCardListPreview() = VideoCardRowsPreview(VideoCardLayout.LIST, showUploadTime = true)

@Preview(name = "Home · Grid", group = "Video cards", widthDp = 360)
@Composable
private fun HomeVideoCardGridPreview() = VideoCardRowsPreview(VideoCardLayout.GRID, showUploadTime = false)

@Preview(name = "Home · List", group = "Video cards", widthDp = 360)
@Composable
private fun HomeVideoCardListPreview() = VideoCardRowsPreview(VideoCardLayout.LIST, showUploadTime = false)

@Composable
private fun VideoCardRowsPreview(layout: VideoCardLayout, showUploadTime: Boolean) {
    val uploadedAt = Instant.parse("2025-01-15T12:30:00Z").toEpochMilli()
    val videos = listOf(
        VideoSummary(
            id = 1,
            title = "Clouds over the coast",
            author = "Studio Momo",
            views = 12_345,
            durationMs = 195_000,
            uploadedAt = uploadedAt,
        ),
        VideoSummary(
            id = 2,
            title = "A quiet afternoon sketching the city from the train window",
            author = "Paper Lantern",
            views = 980,
            durationMs = 3_865_000,
            uploadedAt = uploadedAt,
        ),
        VideoSummary(id = 3, title = "Night walk", author = "Studio Momo"),
    )
    KIRAKIRATheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(VideoCardRowSpacing),
            ) {
                videos.chunked(layout.columns).forEach { row ->
                    VideoCardRow(
                        videos = row,
                        layout = layout,
                        onOpenVideo = {},
                        modifier = Modifier.fillMaxWidth(),
                        showUploadTime = showUploadTime,
                    )
                }
            }
        }
    }
}
