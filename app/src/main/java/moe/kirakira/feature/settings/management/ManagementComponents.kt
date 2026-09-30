@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior

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
internal fun ManagementIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null) }
    }
}

@Composable
internal fun ManagementFrame(
    title: String,
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
    content: LazyListScope.() -> Unit,
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
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { CollapsibleTopAppBar(title = title, onBack = onBack, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (signedIn) floatingActionButton() },
    ) { padding ->
        ContentPullToRefresh(
            isRefreshing = loaded && loading && !appending,
            onRefresh = onRefresh,
            enabled = signedIn,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).consumeWindowInsets(padding),
        ) {
            LazyColumn(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 16.dp,
                    end = padding.calculateEndPadding(direction) + 16.dp,
                    top = 16.dp,
                    bottom = padding.calculateBottomPadding() + 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                when {
                    !signedIn -> item {
                        ManagementEmpty(R.drawable.ic_symbol_lock, stringResource(R.string.management_sign_in))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Button(onClick = onLogin) { Text(stringResource(R.string.management_login)) }
                        }
                    }
                    !loaded && loading -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    else -> {
                        content()
                        if (error != null) item {
                            ContentUnavailableView(
                                state = ContentUnavailableState.ERROR,
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
