package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoDetail
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.components.text.AnimatedCountLabel
import moe.kirakira.ui.theme.KIRAKIRATheme

private val VideoActionSize = 72.dp
private val VideoActionPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

@Composable
internal fun VideoActions(
    detail: VideoDetail,
    busy: Boolean,
    onVote: (Reaction) -> Unit,
    onUnavailable: () -> Unit,
    onShare: () -> Unit,
    onViewCover: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
) {
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val rowWidth = maxWidth.coerceIn(48.dp * 5, VideoActionSize * 5)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(rowWidth),
            verticalAlignment = Alignment.Top,
        ) {
            ReactionAction(
                checked = detail.reaction == Reaction.LIKE,
                icon = R.drawable.ic_icon_thumb_up,
                label = R.string.video_like,
                count = detail.upvotes,
                enabled = !busy && !detail.blockedByOther,
                onClick = { onVote(Reaction.LIKE) },
                modifier = Modifier.weight(1f),
            )
            ReactionAction(
                checked = detail.reaction == Reaction.DISLIKE,
                icon = R.drawable.ic_icon_thumb_down,
                label = R.string.video_dislike,
                count = detail.downvotes,
                enabled = !busy && !detail.blockedByOther,
                onClick = { onVote(Reaction.DISLIKE) },
                modifier = Modifier.weight(1f),
            )
            VideoAction(
                icon = R.drawable.ic_icon_star,
                label = R.string.video_save,
                onClick = onUnavailable,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.video_unknown_action_count)) }
            VideoAction(
                icon = R.drawable.ic_icon_share,
                label = R.string.video_share,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.video_share), textAlign = TextAlign.Center) }
            VideoActionsMenu(
                videoId = detail.summary.id,
                coverAvailable = deliveryImageUrl(detail.summary.image) != null,
                onViewCover = onViewCover,
                onUnavailable = onUnavailable,
                modifier = Modifier.weight(1f),
            ) { open ->
                VideoAction(
                    icon = R.drawable.ic_icon_more_vert,
                    label = R.string.video_more,
                    onClick = open,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.video_more), textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun ReactionAction(
    checked: Boolean,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    count: Long?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (count != null) {
        stringResource(R.string.video_action_count, stringResource(label), count)
    } else {
        stringResource(label)
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        ToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VideoActionSize)
                .semantics { contentDescription = description },
            enabled = enabled,
            shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
            colors = ToggleButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                checkedContentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
            ),
            elevation = null,
            contentPadding = VideoActionPadding,
        ) {
            VideoActionContent(icon) {
                if (count != null) {
                    AnimatedCountLabel(count, stringResource(R.string.animated_count), maxLines = 1)
                } else {
                    Text(stringResource(R.string.video_unknown_action_count))
                }
            }
        }
    }
}

@Composable
private fun VideoAction(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val description = stringResource(label)
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VideoActionSize)
                .semantics { contentDescription = description },
            shape = CircleShape,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            contentPadding = VideoActionPadding,
        ) {
            VideoActionContent(icon, content = content)
        }
    }
}

@Composable
private fun VideoActionContent(
    @DrawableRes icon: Int,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        ProvideTextStyle(MaterialTheme.typography.labelMedium) { content() }
    }
}

@Preview(name = "Video actions · Light", widthDp = 360)
@Preview(name = "Video actions · Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Video actions · Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun VideoActionsPreview() {
    KIRAKIRATheme {
        Surface {
            VideoActions(
                detail = VideoDetail(
                    summary = VideoSummary(id = 0, title = ""),
                    parts = emptyList(),
                    author = PublicProfile(uid = 0L, name = ""),
                    description = "",
                    category = "",
                    upvotes = 1280L,
                    downvotes = 24L,
                    reaction = Reaction.LIKE,
                ),
                busy = false,
                onVote = {},
                onUnavailable = {},
                onShare = {},
                onViewCover = {},
            )
        }
    }
}
