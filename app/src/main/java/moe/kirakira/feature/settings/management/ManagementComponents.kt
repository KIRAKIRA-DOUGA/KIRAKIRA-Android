@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ConnectedLazyColumn
import moe.kirakira.ui.components.ConnectedLazyListScope
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.ThemeColorDefaults

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
    RuleCategory.BLOCK -> R.drawable.ic_symbol_block
    RuleCategory.HIDE -> R.drawable.ic_symbol_visibility_off
    RuleCategory.TAG -> R.drawable.ic_symbol_label
    RuleCategory.KEYWORD -> R.drawable.ic_symbol_match_word
    RuleCategory.REGEX -> R.drawable.ic_symbol_regular_expression
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
    appending: Boolean = false,
    message: Int? = null,
    onDismissMessage: () -> Unit = {},
    onRetry: () -> Unit = onRefresh,
    floatingActionButton: @Composable () -> Unit = {},
    content: ConnectedLazyListScope.() -> Unit,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    val direction = LocalLayoutDirection.current
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onDismissMessage()
        }
    }
    FrostedScaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = ThemeColorDefaults.settingsBackgroundColor(),
        topBar = {
            Box {
                ShadingIcon(
                    icon = shadingIcon,
                    modifier = Modifier.matchParentSize(),
                    alignment = Alignment.BottomEnd,
                    endPadding = 0.dp,
                    offset = DpOffset(32.dp, 32.dp),
                )
                CollapsibleTopAppBar(title = title, onBack = onBack, scrollBehavior = scrollBehavior)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (signedIn) floatingActionButton() },
    ) { padding ->
        ContentPullToRefresh(
            isRefreshing = signedIn && loaded && loading && !appending,
            onRefresh = onRefresh,
            enabled = signedIn,
            indicatorTopPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        ) {
            if (!signedIn) {
                ContentUnavailableView(
                    state = ContentUnavailableState.EMPTY,
                    modifier = Modifier.padding(padding),
                    title = stringResource(R.string.management_sign_in),
                    description = null,
                    iconRes = R.drawable.ic_symbol_lock,
                    presentation = ContentUnavailablePresentation.PAGE,
                    primaryAction = ContentUnavailableAction(stringResource(R.string.management_login), onLogin),
                )
            } else if (!loaded && loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    IndeterminateCircularProgressIndicator()
                }
            } else {
                ConnectedLazyColumn(
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(direction) + 16.dp,
                        end = padding.calculateEndPadding(direction) + 16.dp,
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = padding.calculateBottomPadding() + 104.dp,
                    ),
                ) {
                    content()
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
