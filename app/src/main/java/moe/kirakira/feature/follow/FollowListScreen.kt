package moe.kirakira.feature.follow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.kirakira.R
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.FollowListUser
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.messageRes

@Composable
internal fun FollowListScreen(
    state: FollowListState,
    kind: FollowListKind,
    ready: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val list = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    val content = state.content
    val users = content.data.orEmpty()
    val canLoadMore = isActive && ready && state.hasMore && !content.loading && !state.loadingMore &&
        content.error == null && state.moreError == null
    LaunchedEffect(list, users.size, canLoadMore) {
        if (canLoadMore) {
            snapshotFlow {
                val lastVisible = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                lastVisible != null && lastVisible >= (users.size - 5).coerceAtLeast(0)
            }.distinctUntilChanged().collect { nearEnd ->
                if (nearEnd) onLoadMore()
            }
        }
    }
    val title = stringResource(when (kind) {
        FollowListKind.FOLLOWING -> R.string.follow_list_following_title
        FollowListKind.FOLLOWERS -> R.string.follow_list_followers_title
    })
    val emptyTitle = stringResource(when (kind) {
        FollowListKind.FOLLOWING -> R.string.follow_list_following_empty
        FollowListKind.FOLLOWERS -> R.string.follow_list_followers_empty
    })
    FrostedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_symbol_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                colors = appTopAppBarColors(),
            )
        },
    ) { padding ->
        ContentPullToRefresh(
            isRefreshing = content.loading && content.data != null,
            onRefresh = onRefresh,
            enabled = ready && isActive,
            indicatorTopPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val statusHeight = (maxHeight - padding.calculateTopPadding() -
                    padding.calculateBottomPadding() - 32.dp).coerceAtLeast(0.dp)
                LazyColumn(
                    state = list,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(direction),
                        end = padding.calculateEndPadding(direction),
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    if (content.data == null) {
                        item(key = "status", contentType = "status") {
                            ContentStatus(
                                state = content.copy(loading = !ready || content.loading),
                                onRetry = onRefresh,
                                modifier = Modifier.fillMaxWidth().heightIn(min = statusHeight),
                            )
                        }
                    } else if (users.isEmpty()) {
                        item(key = "empty", contentType = "status") {
                            ContentUnavailableView(
                                state = ContentUnavailableState.EMPTY,
                                title = emptyTitle,
                                description = null,
                                iconRes = R.drawable.ic_symbol_person,
                                modifier = Modifier.fillMaxWidth().heightIn(min = statusHeight),
                                presentation = ContentUnavailablePresentation.INLINE,
                            )
                        }
                    } else {
                        items(
                            items = users,
                            key = { user -> user.uid },
                            contentType = { "user" },
                        ) { user ->
                            FollowListUserRow(
                                user = user,
                                onClick = { onOpenProfile(user.uid) },
                                enabled = ready && isActive,
                            )
                        }
                    }
                    if (content.data != null && content.error != null) {
                        item(key = "refresh_error", contentType = "status") {
                            ContentUnavailableView(
                                state = ContentUnavailableState.ERROR,
                                description = stringResource(content.error.messageRes()),
                                onRetry = onRefresh,
                                retryEnabled = ready && isActive && !content.loading,
                                presentation = ContentUnavailablePresentation.INLINE,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item(key = "loading_more", contentType = "status") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                IndeterminateCircularProgressIndicator()
                            }
                        }
                    } else if (state.moreError != null) {
                        item(key = "more_error", contentType = "status") {
                            ContentUnavailableView(
                                state = ContentUnavailableState.ERROR,
                                description = stringResource(state.moreError.messageRes()),
                                onRetry = onRetryMore,
                                retryEnabled = ready && isActive && !content.loading,
                                presentation = ContentUnavailablePresentation.INLINE,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowListUserRow(
    user: FollowListUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AccountAvatar(user.avatar, size = 48.dp) },
        supportingContent = if (user.username.isNotBlank()) {
            {
                Text(
                    "@${user.username}",
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else null,
        content = {
            Text(
                user.name.ifBlank { stringResource(R.string.content_unknown_author) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
