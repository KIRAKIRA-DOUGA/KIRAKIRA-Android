package moe.kirakira.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ExpandableText
import moe.kirakira.ui.navigation.LocalImageSharedScope
import moe.kirakira.ui.navigation.imageSharedBounds
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun ProfileAvatar(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    AccountAvatar(state.profile.avatar, modifier, size, clipToCircle = LocalImageSharedScope.current == null)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileHeader(
    state: ProfileUiState,
    bioExpanded: Boolean,
    onBioExpandedChange: (Boolean) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onUnavailableAction: (ProfileAction) -> Unit,
    onOpenAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    coverRemainderHeight: Dp = 96.dp,
    onNameBottomChange: (Float) -> Unit = {},
) {
    val profile = state.profile
    val name = profile.name.ifBlank { stringResource(R.string.content_unknown_author) }
    val username = profile.username.takeIf { it.isNotBlank() }?.let { "@$it" }
    val bio = profile.signature.takeIf { it.isNotBlank() }
    val background = ThemeColorDefaults.pageBackgroundColor()
    val avatarDescription = stringResource(R.string.profile_view_avatar, name)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverRemainderHeight + 56.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(background),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .size(96.dp),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            val strokeWidth = 4.dp.toPx()
                            drawCircle(
                                color = background,
                                radius = size.minDimension / 2f + strokeWidth / 2f,
                                style = Stroke(strokeWidth),
                            )
                        },
                )
                ProfileAvatar(
                    state = state,
                    modifier = Modifier.imageSharedBounds(state.avatarKey, viewer = false),
                )
                // Clip only the hit/ripple layer so it cannot crop the shared image animation.
                if (deliveryImageUrl(profile.avatar) != null) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .clickable(onClickLabel = avatarDescription, onClick = onOpenAvatar)
                            .semantics { contentDescription = avatarDescription },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .background(background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SelectionContainer {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .semantics { heading() }
                            .onGloballyPositioned { coordinates ->
                                onNameBottomChange(coordinates.positionInWindow().y + coordinates.size.height)
                            },
                    )
                }
                if (username != null) {
                    SelectionContainer {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (bio != null) {
                ExpandableText(text = bio, expanded = bioExpanded, onExpandedChange = onBioExpandedChange)
            }
            if (state.followingCount != null && state.followerCount != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileStat(
                        count = state.followingCount,
                        label = stringResource(R.string.me_following_count),
                        onClick = { onUnavailableAction(ProfileAction.FOLLOWING_LIST) },
                    )
                    ProfileStat(
                        count = state.followerCount,
                        label = stringResource(R.string.me_followers_count),
                        onClick = { onUnavailableAction(ProfileAction.FOLLOWERS_LIST) },
                    )
                }
            }
            if (state.isSelf) {
                val buttonHeight = ButtonDefaults.MediumContainerHeight
                ShadowButton(
                    onClick = onEditProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = buttonHeight),
                    shapes = ButtonDefaults.shapesFor(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                ) {
                    Text(
                        text = stringResource(R.string.profile_edit),
                        style = ButtonDefaults.textStyleFor(buttonHeight),
                    )
                }
            } else {
                ToggleButton(
                    checked = state.following,
                    enabled = !state.busy && !state.profile.blockedByOther,
                    onCheckedChange = onFollowingChange,
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = ToggleButtonSize.Medium,
                    icon = {
                        Icon(
                            painterResource(
                                if (state.following) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add,
                            ),
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(if (state.following) R.string.video_following else R.string.video_follow))
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(count: Int, label: String, onClick: () -> Unit) {
    val description = stringResource(R.string.profile_stat_description, count, label)
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.video_count, count), style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
