@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.search.PrepareSettingsSearchList
import moe.kirakira.ui.components.icon.IconBadge
import moe.kirakira.ui.components.icon.IconBadgeTone
import moe.kirakira.ui.components.list.ConnectedLazyColumn
import moe.kirakira.ui.components.list.ConnectedLazyListScope
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.status.ContentLoadingView
import moe.kirakira.ui.components.status.ContentPullToRefresh
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.components.status.ContentUnavailableAction
import moe.kirakira.ui.components.status.ContentUnavailablePresentation
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView

@StringRes
internal fun RuleCategory.titleRes(): Int = when (this) {
    RuleCategory.BLOCK -> R.string.management_blocked_users
    RuleCategory.HIDE -> R.string.management_hidden_users
    RuleCategory.TAG -> R.string.management_tags
    RuleCategory.KEYWORD -> R.string.management_keywords
    RuleCategory.REGEX -> R.string.management_regex
}

@DrawableRes
internal fun RuleCategory.iconRes(): Int = when (this) {
    RuleCategory.BLOCK -> R.drawable.ic_icon_block
    RuleCategory.HIDE -> R.drawable.ic_icon_visibility_off
    RuleCategory.TAG -> R.drawable.ic_icon_tag
    RuleCategory.KEYWORD -> R.drawable.ic_symbol_match_word
    RuleCategory.REGEX -> R.drawable.ic_icon_regexp
}

@Composable
internal fun ManagementIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    tone: IconBadgeTone = IconBadgeTone.SECONDARY,
) {
    IconBadge(icon = icon, modifier = modifier, shape = shape, tone = tone)
}

@Composable
internal fun ManagementFrame(
    title: String,
    @DrawableRes shadingIcon: Int,
    signedIn: Boolean,
    loading: Boolean,
    loaded: Boolean,
    error: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContentHeight: Dp = 0.dp,
    searchItemKey: String? = null,
    searchItemKeys: List<String> = emptyList(),
    appending: Boolean = false,
    message: Int? = null,
    onDismissMessage: () -> Unit = {},
    onRetry: () -> Unit = onRefresh,
    floatingActionButton: @Composable () -> Unit = {},
    content: ConnectedLazyListScope.(Modifier) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onDismissMessage()
        }
    }
    SettingsScaffold(
        title = title,
        onBack = onBack,
        shadingIcon = shadingIcon,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (signedIn) floatingActionButton() },
    ) { padding ->
        StatusBarScrollToTopEffect(
            listState,
            scrollContentAvailable = signedIn && (loaded || !loading),
        )
        ContentPullToRefresh(
            isRefreshing = signedIn && loaded && loading && !appending,
            onRefresh = onRefresh,
            enabled = signedIn,
            indicatorTopPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        ) {
            val listContentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                bottom = padding.calculateBottomPadding() + 104.dp,
            )
            if (!signedIn) {
                ContentUnavailableView(
                    state = ContentUnavailableState.EMPTY,
                    modifier = Modifier.padding(padding),
                    title = stringResource(R.string.management_sign_in),
                    description = null,
                    iconRes = R.drawable.ic_icon_lock,
                    presentation = ContentUnavailablePresentation.PAGE,
                    primaryAction = ContentUnavailableAction(stringResource(R.string.management_login), onLogin),
                )
            } else if (!loaded && loading) {
                ContentLoadingView(modifier = Modifier.fillMaxSize().padding(listContentPadding))
            } else {
                PrepareSettingsSearchList(listState, searchItemKeys, searchItemKey)
                ContentStatusViewport(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .widthIn(max = SettingsDefaults.MaxContentWidth)
                        .fillMaxSize(),
                    contentPadding = listContentPadding,
                    leadingContentHeight = leadingContentHeight,
                ) { statusModifier ->
                    ConnectedLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listContentPadding,
                    ) {
                        content(statusModifier)
                        if (error != null) item {
                            ContentUnavailableView(
                                state = ContentUnavailableState.ERROR,
                                modifier = Modifier.padding(top = 8.dp),
                                description = stringResource(error),
                                onRetry = onRetry,
                                presentation = ContentUnavailablePresentation.INLINE,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManagementEmpty(@DrawableRes icon: Int, title: String, modifier: Modifier = Modifier) {
    ContentUnavailableView(
        state = ContentUnavailableState.EMPTY,
        modifier = modifier,
        title = title,
        description = null,
        iconRes = icon,
        presentation = ContentUnavailablePresentation.INLINE,
    )
}
