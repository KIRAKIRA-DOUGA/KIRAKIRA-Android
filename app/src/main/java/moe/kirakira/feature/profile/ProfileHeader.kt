package moe.kirakira.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.AnimatedCountLabel
import moe.kirakira.ui.components.ExpandableText
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.FollowButton
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.UsernameText
import moe.kirakira.ui.components.pressToRoundButtonShapes
import moe.kirakira.ui.navigation.LocalImageSharedScope
import moe.kirakira.ui.navigation.imageSharedBounds
import moe.kirakira.ui.theme.ThemeColorDefaults

internal val profileAvatarSize = 96.dp

@Composable
internal fun ProfileAvatar(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    size: Dp = profileAvatarSize,
) {
    AccountAvatar(
        url = state.profile.avatar,
        modifier = modifier,
        size = size,
        clipToCircle = LocalImageSharedScope.current == null,
        showLoadingIndicator = true,
    )
}

@Composable
internal fun ProfileHeader(
    state: ProfileUiState,
    bioExpanded: Boolean,
    onBioExpandedChange: (Boolean) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (FollowListKind) -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenBanner: () -> Unit,
    modifier: Modifier = Modifier,
    coverRemainderHeight: Dp = 96.dp,
) {
    val profile = state.profile
    val name = profile.name.ifBlank { stringResource(R.string.content_unknown_author) }
    val bio = profile.signature.takeIf { it.isNotBlank() }
    val background = ThemeColorDefaults.pageBackgroundColor()
    val avatarDescription = stringResource(R.string.profile_view_avatar, name)
    val bannerDescription = stringResource(R.string.profile_view_banner, name)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverRemainderHeight + 56.dp),
        ) {
            // The image is in the scrolling background; keep its hit target in the header.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(coverRemainderHeight)
                    .clickable(onClickLabel = bannerDescription, onClick = onOpenBanner)
                    .semantics { contentDescription = bannerDescription },
            )
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
                    .size(profileAvatarSize),
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
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (profile.username.isNotBlank()) {
                    SelectionContainer {
                        UsernameText(
                            username = profile.username,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (bio != null) {
                ExpandableText(text = bio, expanded = bioExpanded, onExpandedChange = onBioExpandedChange)
            }
            if (state.statsLoading || (state.followingCount != null && state.followerCount != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileStat(
                        count = state.followingCount,
                        label = stringResource(R.string.me_following_count),
                        onClick = { onOpenFollowList(FollowListKind.FOLLOWING) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ProfileStat(
                        count = state.followerCount,
                        label = stringResource(R.string.me_followers_count),
                        onClick = { onOpenFollowList(FollowListKind.FOLLOWERS) },
                        modifier = Modifier.weight(1f, fill = false),
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
                    shapes = pressToRoundButtonShapes(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                ) {
                    Text(
                        text = stringResource(R.string.profile_edit),
                        style = ButtonDefaults.textStyleFor(buttonHeight),
                    )
                }
            } else {
                FollowButton(
                    following = state.following,
                    enabled = !state.busy && !state.profile.blockedByOther,
                    loading = state.followingInProgress,
                    onFollowingChange = onFollowingChange,
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = ToggleButtonSize.Medium,
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(count: Int?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = count?.let { stringResource(R.string.profile_stat_description, it, label) }
    val motion = MaterialTheme.motionScheme
    FlatTextButton(
        onClick = onClick,
        enabled = count != null,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                if (description == null) {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                } else {
                    contentDescription = description
                }
            },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
    ) {
        AnimatedContent(
            targetState = count,
            contentKey = { it != null },
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform { _, _ -> motion.fastSpatialSpec() })
            },
            label = "profileStatCount",
        ) { displayedCount ->
            if (displayedCount == null) {
                Spacer(
                    Modifier
                        .size(width = 24.dp, height = 8.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            } else {
                AnimatedCountLabel(
                    count = displayedCount.toLong(),
                    template = stringResource(R.string.animated_count),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
