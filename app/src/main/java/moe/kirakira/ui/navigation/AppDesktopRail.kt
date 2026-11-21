package moe.kirakira.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavKey
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.feature.main.AppDestination
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.SlidingSelection
import moe.kirakira.ui.components.navigation.AnimatedNavigationIcon
import moe.kirakira.ui.components.navigation.MotionTrack
import moe.kirakira.ui.components.navigation.NavigationIcon
import moe.kirakira.ui.components.navigation.rememberTabSelectionMotion
import moe.kirakira.ui.components.rememberSlidingSelection
import moe.kirakira.ui.components.rememberSlidingSelectionPressProgress
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.barSurfaceLayer

internal val DesktopRailWidth = 56.dp
internal val DesktopRailWordmarkMinHeight = 678.dp
private val DesktopRailShadowElevation = 8.dp
private val RailItemHeight = 48.dp
private val RailFaceSize = 40.dp
private val RailEdgePadding = 8.dp
private val MarkerWidth = 3.dp
private val MarkerHeight = 24.dp
private val PressedMarkerHeight = 14.dp
private val MaxMarkerHeight = 64.dp

internal enum class DesktopRailItem(
    @param:StringRes val label: Int,
    val icon: NavigationIcon,
    val tab: AppDestination? = null,
    val route: NavKey? = null,
) {
    HOME(R.string.nav_home, NavigationIcon.HOME, tab = AppDestination.HOME),
    SEARCH(R.string.nav_search, NavigationIcon.CERASUS_SEARCH, tab = AppDestination.SEARCH),
    HISTORY(R.string.me_history, NavigationIcon.HISTORY, route = HistoryRoute),
    FAVORITES(R.string.me_favorites, NavigationIcon.FAVORITES, route = FavoritesRoute),
    FOLLOWING(R.string.nav_following, NavigationIcon.CERASUS_FOLLOWING, tab = AppDestination.FOLLOWING),
    ACCOUNT(R.string.me_profile, NavigationIcon.PERSON, route = SelfProfileRoute),
    SETTINGS(R.string.me_settings, NavigationIcon.SETTINGS, route = SettingsRoute),
}

private val PrimaryRailItems = listOf(
    DesktopRailItem.HOME, DesktopRailItem.SEARCH, DesktopRailItem.HISTORY,
    DesktopRailItem.FAVORITES, DesktopRailItem.FOLLOWING,
)

private val SecondaryRailItems = listOf(DesktopRailItem.ACCOUNT, DesktopRailItem.SETTINGS)
private val RailItems = PrimaryRailItems + SecondaryRailItems

private fun selectedRailIndex(route: NavKey, tab: AppDestination): Int? = RailItems.indexOfFirst { item ->
    if (route == MainRoute) item.tab == tab else item.route == route
}.takeIf { it >= 0 }

// Me can still be selected when a narrow window unfolds. Order it after the visible Rail items.
internal fun desktopRailTabIndex(tab: AppDestination): Int = selectedRailIndex(MainRoute, tab) ?: RailItems.size

internal fun desktopRailEnterFromBottom(route: NavKey, tab: AppDestination, target: DesktopRailItem): Boolean? {
    val sourceIndex = if (route == MainRoute) desktopRailTabIndex(tab) else selectedRailIndex(route, tab)
    val targetIndex = RailItems.indexOf(target)
    return sourceIndex?.takeIf { it != targetIndex }?.let { targetIndex > it }
}

internal fun NavKey.isDesktopRailChild(): Boolean = when (this) {
    HistoryRoute, FavoritesRoute, SelfProfileRoute, SettingsRoute -> true
    else -> false
}

@Composable
internal fun desktopRailControlInsets(): WindowInsets {
    // Avoid the taskbar and navigation buttons without reserving space for the transparent gesture strip.
    val bottomInsets = WindowInsets.tappableElement
        .union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.ime)
        .only(WindowInsetsSides.Bottom)
    return WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top).union(bottomInsets)
}

@Composable
internal fun AppDesktopRail(
    route: NavKey,
    tab: AppDestination,
    profile: AccountProfile?,
    interactive: Boolean,
    controlInsets: WindowInsets,
    showWordmark: Boolean,
    onSelect: (DesktopRailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val selectedIndex = selectedRailIndex(route, tab)
    val selection = rememberSlidingSelection(selectedIndex)
    val interactions = remember { RailItems.map { MutableInteractionSource() } }
    val pressProgress = interactions.map { source ->
        val pressed by source.collectIsPressedAsState()
        rememberSlidingSelectionPressProgress(interactive && pressed)
    }
    val markerPressProgress = selectedIndex?.let(pressProgress::get)
    Box(
        // The surface includes the unsafe edge; only the controls are inset.
        modifier.fillMaxHeight()
            .barSurfaceLayer(shadowElevation = DesktopRailShadowElevation)
            .background(ThemeColorDefaults.pageBackgroundColor())
            .then(if (interactive) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        BoxWithConstraints(
            Modifier
                .windowInsetsPadding(controlInsets)
                .width(DesktopRailWidth)
                .fillMaxHeight(),
        ) {
            val safeHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = interactive)
                    .heightIn(min = safeHeight)
                    .padding(
                        top = RailEdgePadding,
                        bottom = RailEdgePadding - (RailItemHeight - RailFaceSize) / 2,
                    )
                    .railSelectionMarker(selection, colors.primary, markerPressProgress)
                    .selectableGroup(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column {
                    PrimaryRailItems.forEachIndexed { index, item ->
                        RailItem(item, index == selectedIndex, interactive, interactions[index], pressProgress[index], onSelect)
                    }
                }
                if (showWordmark || safeHeight >= 540.dp) RailBrand(showWordmark)
                Column {
                    SecondaryRailItems.forEachIndexed { groupIndex, item ->
                        val index = PrimaryRailItems.size + groupIndex
                        RailItem(
                            item, index == selectedIndex, interactive, interactions[index], pressProgress[index], onSelect,
                            profile = profile.takeIf { item == DesktopRailItem.ACCOUNT },
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.railSelectionMarker(
    selection: SlidingSelection,
    color: Color,
    pressProgress: State<Float>?,
): Modifier = drawWithCache {
    val path = Path()
    val width = MarkerWidth.toPx()
    val ltr = layoutDirection == LayoutDirection.Ltr
    val left = if (ltr) size.width - width else 0f
    val rowHeight = RailItemHeight.roundToPx().toFloat()
    val upperCount = PrimaryRailItems.size
    val lowerTop = size.height - rowHeight * SecondaryRailItems.size
    fun rowCenter(index: Int): Float = if (index < upperCount) {
        (index + 0.5f) * rowHeight
    } else {
        lowerTop + (index - upperCount + 0.5f) * rowHeight
    }
    onDrawWithContent {
        drawContent()
        val press = (pressProgress?.value ?: 0f).coerceIn(0f, 1f)
        val half = lerp(MarkerHeight.toPx(), PressedMarkerHeight.toPx(), press) / 2f
        val bounds = selection.verticalBounds(RailItems.lastIndex) { position ->
            val start = position.toInt()
            val end = (start + 1).coerceAtMost(RailItems.lastIndex)
            val center = lerp(rowCenter(start), rowCenter(end), position - start)
            Rect(left, center - half, left + width, center + half)
        } ?: return@onDrawWithContent
        // Adjacent buttons share the bottom bar's two-edge movement. Cap the stretch across
        // the flexible gap so the marker does not become a full-height stripe on a tablet.
        val halfHeight = minOf(bounds.height, MaxMarkerHeight.toPx()) / 2f
        if (halfHeight <= 0f) return@onDrawWithContent
        val face = Rect(left, bounds.center.y - halfHeight, left + width, bounds.center.y + halfHeight)
        // Cerasus's oval(left): round through the full thickness on the inner side only.
        // A Compose two-corner Shape scales those radii down against the 3 dp width.
        val inner = CornerRadius(minOf(width, halfHeight))
        val start = if (ltr) inner else CornerRadius.Zero
        val end = if (ltr) CornerRadius.Zero else inner
        path.rewind()
        path.addRoundRect(RoundRect(face, topLeft = start, topRight = end, bottomRight = end, bottomLeft = start))
        drawPath(path, color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailItem(
    item: DesktopRailItem,
    selected: Boolean,
    interactive: Boolean,
    interactionSource: MutableInteractionSource,
    pressProgress: State<Float>,
    onSelect: (DesktopRailItem) -> Unit,
    profile: AccountProfile? = null,
) {
    val accountItem = item == DesktopRailItem.ACCOUNT
    val label = stringResource(if (accountItem && profile == null) R.string.auth_sign_in else item.label)
    val colors = MaterialTheme.colorScheme
    val selectionProgress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "Rail item selection",
    )
    val target: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxWidth().height(RailItemHeight).then(
                if (interactive) Modifier.selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = RailFaceSize / 2),
                    role = Role.Tab,
                    onClick = { onSelect(item) },
                ).semantics { contentDescription = label } else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(RailFaceSize).drawBehind {
                    val selectedAmount = selectionProgress.value.coerceIn(0f, 1f)
                    if (selectedAmount > 0f) {
                        val press = pressProgress.value.coerceIn(0f, 1f)
                        drawCircle(
                            color = colors.primary,
                            alpha = selectedAmount * lerp(0.12f, 0.18f, press),
                            radius = size.minDimension / 2f * lerp(0.8f, 1f, selectedAmount) * lerp(1f, 0.9f, press),
                        )
                    }
                },
            )
            if (accountItem) {
                RailAccountAvatar(profile?.avatar, selected, pressProgress)
            } else {
                AnimatedNavigationIcon(
                    icon = item.icon,
                    selected = selected,
                    tint = { lerp(colors.onSurfaceVariant, colors.primary, selectionProgress.value.coerceIn(0f, 1f)) },
                )
            }
        }
    }
    val tooltipState = rememberTooltipState()
    LaunchedEffect(interactive) {
        if (!interactive) tooltipState.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
        enableUserInput = interactive,
        content = target,
    )
}

private val AvatarScale = MotionTrack(0f, 1f, 0.2f, 0.91f, 0.46f, 1.1f, 0.72f, 0.98f, 1f, 1f)

@Composable
private fun RailAccountAvatar(url: String?, selected: Boolean, pressProgress: State<Float>) {
    val motion = rememberTabSelectionMotion(selected, durationMillis = 440)
    AccountAvatar(
        url = url,
        size = RailFaceSize,
        modifier = Modifier.graphicsLayer {
            val press = pressProgress.value.coerceIn(0f, 1f)
            val scale = motion.sample(AvatarScale, 1f) * lerp(1f, 0.92f, press)
            scaleX = scale
            scaleY = scale
        },
    )
}

@Composable
private fun RailBrand(showWordmark: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showWordmark) {
            Box(Modifier.fillMaxWidth().height(125.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.logo_kirakira_wordmark), contentDescription = null,
                    modifier = Modifier.requiredSize(125.dp, 20.dp).rotate(-90f), tint = accent,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        Canvas(Modifier.width(DesktopRailWidth).height(61.dp)) {
            val slant = size.width * 0.57735f // Cerasus's 30-degree skew, rotated with the wordmark.
            val band = 8.dp.toPx()
            repeat(2) { index ->
                val y = index * 20.dp.toPx()
                drawPath(Path().apply {
                    moveTo(0f, y + slant)
                    lineTo(size.width, y)
                    lineTo(size.width, y + band)
                    lineTo(0f, y + slant + band)
                    close()
                }, color = accent)
            }
        }
    }
}
