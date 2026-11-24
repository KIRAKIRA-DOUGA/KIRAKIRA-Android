package moe.kirakira.feature.video

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.components.avatar.AccountAvatar
import moe.kirakira.ui.components.button.FollowButton
import moe.kirakira.ui.components.overlay.LocalMenuHazeState
import moe.kirakira.ui.components.overlay.menuHazeSource
import moe.kirakira.ui.components.scaffold.frostedBarBackground
import moe.kirakira.ui.components.text.UsernameText
import moe.kirakira.ui.theme.BarShadowElevation

@Composable
internal fun VideoContentLayout(
    playerHeight: Dp,
    hazeState: HazeState,
    detailPane: IntRect?,
    introductionPane: IntRect?,
    modifier: Modifier = Modifier,
    introduction: @Composable () -> Unit,
    aboveTabs: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menuHazeState = rememberHazeState()
    CompositionLocalProvider(LocalMenuHazeState provides menuHazeState) {
        Box(modifier.menuHazeSource(menuHazeState)) {
            if (introductionPane != null) WindowPane(introductionPane, content = introduction)
            WindowPane(detailPane) {
                Column(Modifier.fillMaxSize()) {
                    aboveTabs()
                    VideoTabLayout(
                        playerHeight, hazeState, Modifier.weight(1f).fillMaxWidth(), tabs, content,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoTabLayout(
    playerHeight: Dp,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            // The sampled pages draw below the shared surface, with the tab foreground last.
            Column(Modifier.fillMaxSize(), content = content)
            Box(
                Modifier.frostedBarBackground(
                    hazeState = hazeState,
                    shadowElevation = BarShadowElevation,
                ),
            ) {
                Spacer(Modifier.fillMaxWidth().height(playerHeight).background(Color.Black))
            }
            Box { tabs() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val playerHeightPx = playerHeight.roundToPx().coerceIn(0, height)
        val tabBar = measurables[2].measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = height - playerHeightPx),
        )
        val surfaceHeight = playerHeightPx + tabBar.height
        val pages = measurables[0].measure(Constraints.fixed(width, height))
        val surface = measurables[1].measure(Constraints.fixed(width, surfaceHeight))
        layout(width, height) {
            pages.placeRelative(0, 0)
            if (width > 0 && surfaceHeight > 0) {
                surface.placeRelative(0, 0)
            }
            tabBar.placeRelative(0, playerHeightPx)
        }
    }
}

@Composable
internal fun VideoAuthor(
    author: PublicProfile,
    onOpenProfile: (Long) -> Unit,
    onFollow: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable { onOpenProfile(author.uid) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountAvatar(author.avatar, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = author.name.ifBlank { stringResource(R.string.content_unknown_author) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.username.isNotBlank()) {
                    UsernameText(
                        username = author.username,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!author.isSelf) {
            FollowButton(author.following, { onFollow() }, enabled = enabled, loading = loading)
        }
    }
}

@Composable
internal fun VideoMetadata(@DrawableRes icon: Int, text: String, description: String? = null) {
    Row(
        modifier = if (description != null) {
            Modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            Modifier
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
