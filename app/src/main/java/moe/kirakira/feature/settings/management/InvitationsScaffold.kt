package moe.kirakira.feature.settings.management

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsingHeaderBackground
import moe.kirakira.ui.components.CollapsingTabLayout
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.PagerTabRow
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.frostedBarBackground
import moe.kirakira.ui.components.rememberCollapsingTabState
import moe.kirakira.ui.components.rememberTabChangeHandler
import moe.kirakira.ui.theme.BarShadowElevation
import moe.kirakira.ui.theme.ThemeColorDefaults

/** One persistent tab row and a pager, with the statistics scrolling away before each page. */
@Composable
internal fun InvitationsScaffold(
    pagerState: PagerState,
    signedIn: Boolean,
    loading: Boolean,
    loaded: Boolean,
    message: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onTabReselect: suspend (Int) -> Unit = {},
    header: @Composable () -> Unit,
    content: @Composable (Int, PaddingValues, Dp) -> Unit,
) {
    val headerState = rememberCollapsingTabState()
    val tabsPinned = headerState.isCollapsed
    val showContent = signedIn && (loaded || !loading)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var tabHeight by remember { mutableIntStateOf(0) }
    val onTabChange = rememberTabChangeHandler(pagerState, onTabReselect = onTabReselect)
    val pageHazeState = rememberHazeState()
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onDismissMessage()
        }
    }

    Box(modifier.fillMaxSize().background(ThemeColorDefaults.settingsBackgroundColor())) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                Box {
                    if (!showContent || !tabsPinned) {
                        Box(
                            Modifier.matchParentSize().frostedBarBackground(
                                hazeState = pageHazeState,
                                shadowElevation = BarShadowElevation,
                            ),
                        )
                    }
                    ShadingIcon(
                        icon = R.drawable.ic_symbol_confirmation_number,
                        modifier = Modifier.matchParentSize(),
                        alignment = Alignment.BottomEnd,
                        endPadding = 0.dp,
                        offset = DpOffset(32.dp, 32.dp),
                    )
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.settings_invitation_code),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                    contentDescription = stringResource(R.string.navigate_back),
                                )
                            }
                        },
                        colors = appTopAppBarColors(),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            ContentPullToRefresh(
                isRefreshing = signedIn && loaded && loading,
                onRefresh = onRefresh,
                enabled = signedIn,
                indicatorTopPadding = padding.calculateTopPadding() +
                    if (tabsPinned) with(density) { tabHeight.toDp() } else 0.dp,
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
                } else if (!showContent) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        IndeterminateCircularProgressIndicator()
                    }
                } else {
                    Box(
                        Modifier.align(Alignment.TopCenter)
                            .padding(
                                start = padding.calculateStartPadding(direction),
                                end = padding.calculateEndPadding(direction),
                            )
                            .widthIn(max = 640.dp)
                            .fillMaxSize(),
                    ) {
                        CollapsingTabLayout(
                            state = headerState,
                            topPadding = padding.calculateTopPadding(),
                            tabHeight = with(density) { tabHeight.toDp() },
                            modifier = Modifier.fillMaxSize(),
                            headerBackground = {
                                CollapsingHeaderBackground(
                                    state = headerState,
                                    hazeState = pageHazeState,
                                    modifier = Modifier.matchParentSize(),
                                ) {
                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
                                }
                            },
                            header = {
                                Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                                    header()
                                }
                            },
                            tabs = {
                                PagerTabRow(
                                    pagerState = pagerState,
                                    titles = listOf(
                                        stringResource(R.string.invitation_all),
                                        stringResource(R.string.invitation_unused),
                                        stringResource(R.string.invitation_used),
                                    ),
                                    onTabChange = onTabChange,
                                    modifier = Modifier.fillMaxWidth()
                                        .onSizeChanged { tabHeight = it.height },
                                )
                            },
                        ) { topContentPadding ->
                            HorizontalPager(
                                state = pagerState,
                                key = { it },
                                modifier = Modifier.fillMaxSize()
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Modifier.hazeSource(pageHazeState)
                                        } else Modifier,
                                    ),
                            ) { page ->
                                content(
                                    page,
                                    PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = topContentPadding + 16.dp,
                                        bottom = padding.calculateBottomPadding() + 24.dp,
                                    ),
                                    with(density) { (headerState.headerHeight - headerState.offset).toDp() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

