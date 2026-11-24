package moe.kirakira.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import moe.kirakira.R
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.components.illustration.SettingsSearchLogo
import moe.kirakira.ui.components.scaffold.LocalPageBackButtonVisible
import moe.kirakira.ui.components.scaffold.LocalStatusBarScrollToTopEnabled
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.barSurfaceLayer

internal data object OrdinaryNavigationEventKey

private data class SettingsSceneKey(val directoryKey: Any)

internal val Scene<*>.destinationKey: Any
    get() = entries.last().contentKey

internal val Scene<*>.usesVideoZoom: Boolean
    get() = entries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true

internal data class ActivityScenePage<T : Any>(
    val top: NavEntry<T>,
    val directory: NavEntry<T>? = null,
    val settingsPath: List<Any> = emptyList(),
    val settingsCategoryIndex: Int? = null,
) {
    fun sharesSettingsDirectory(other: ActivityScenePage<*>?): Boolean =
        directory != null && directory.contentKey == other?.directory?.contentKey
}

internal class ActivitySceneStrategy<T : Any>(
    private val motion: PredictiveBackMotion,
    private val previewKey: Any?,
    private val currentEntries: State<List<NavEntry<T>>>,
    private val currentRoute: State<T?>,
    private val zoomPreview: Boolean,
    private val videoZoomState: PlaybackViewportState?,
    private val videoCovered: State<Boolean>,
    private val videoSourceCovered: State<Boolean>,
    private val settingsPanes: SettingsPaneLayout?,
    private val settingsRoot: T?,
    private val fullPageKeys: Set<Any>,
    private val rail: State<ActivityStartRail<T>?>,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> {
        val page = scenePage(entries)
        val previousEntries = entries.dropLast(1)
        val needsUnderlay = page.top.contentKey == previewKey || page.top.metadata[VIDEO_ZOOM_METADATA] == true
        return ActivityScene(
            page = page,
            underlay = if (needsUnderlay && previousEntries.isNotEmpty()) scenePage(previousEntries) else null,
            previousEntries = previousEntries,
            motion = motion,
            currentEntries = currentEntries,
            currentRoute = currentRoute,
            zoomPreview = zoomPreview,
            videoZoomState = videoZoomState,
            videoCovered = videoCovered,
            videoSourceCovered = videoSourceCovered,
            settingsPanes = settingsPanes,
            settingsRoot = settingsRoot,
            rail = rail,
        )
    }

    private fun scenePage(entries: List<NavEntry<T>>): ActivityScenePage<T> {
        val top = entries.last()
        if (settingsPanes == null || top.contentKey in fullPageKeys) return ActivityScenePage(top)
        val root = entries.indexOfLast { it.metadata[SettingsPaneKey] == SettingsPane.DIRECTORY }
        val directory = entries.getOrNull(root) ?: return ActivityScenePage(top)
        if (entries.subList(root + 1, entries.size).any {
                val role = it.metadata[SettingsPaneKey]
                role != SettingsPane.CATEGORY && role != SettingsPane.SUBPAGE
            }) {
            return ActivityScenePage(top)
        }
        return ActivityScenePage(
            top = top,
            directory = directory,
            settingsPath = entries.subList(root, entries.size).map { it.contentKey },
            settingsCategoryIndex = entries.getOrNull(root + 1)?.metadata?.get(SettingsCategoryIndexKey),
        )
    }
}

internal data class ActivityScene<T : Any>(
    val page: ActivityScenePage<T>,
    val underlay: ActivityScenePage<T>?,
    override val previousEntries: List<NavEntry<T>>,
    val motion: PredictiveBackMotion,
    val currentEntries: State<List<NavEntry<T>>>,
    val currentRoute: State<T?>,
    val zoomPreview: Boolean,
    val videoZoomState: PlaybackViewportState?,
    val videoCovered: State<Boolean>,
    val videoSourceCovered: State<Boolean>,
    val settingsPanes: SettingsPaneLayout?,
    val settingsRoot: T?,
    val rail: State<ActivityStartRail<T>?>,
) : Scene<T> {
    // The directory owns the outer Scene throughout detail navigation, including the empty pane.
    override val key: Any = page.directory?.let { SettingsSceneKey(it.contentKey) } ?: page.top.contentKey
    override val entries: List<NavEntry<T>> =
        listOfNotNull(underlay?.directory, underlay?.top, page.directory, page.top).distinctBy { it.contentKey }

    override val content: @Composable () -> Unit = {
        val destination = page.top.contentKey
        val stack = currentEntries.value
        val topKey = stack.lastOrNull()?.contentKey
        val inStack = stack.any { it.contentKey == destination }
        DisposableEffect(destination, motion) {
            onDispose { motion.onDestinationDisposed(destination) }
        }
        val coversVideo = !usesVideoZoom && !entries.last().usesImageTransition &&
            previousEntries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true
        DisposableEffect(destination, coversVideo, videoZoomState) {
            if (coversVideo) videoZoomState?.coveringPageKeys?.put(destination, Unit)
            onDispose {
                if (coversVideo) videoZoomState?.coveringPageKeys?.remove(destination)
            }
        }
        val preview = underlay != null && (motion.closingKey == destination || zoomPreview)
        val zoomLayer = underlay != null && usesVideoZoom
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (preview || zoomLayer) Lifecycle.State.STARTED else Lifecycle.State.RESUMED,
        )
        val lifecycleState by owner.lifecycle.currentStateAsState()
        val visibility = LocalNavAnimatedContentScope.current.transition
        if (usesVideoZoom && videoZoomState != null &&
            stack.lastOrNull { it.metadata[VIDEO_ZOOM_METADATA] == true }?.contentKey == destination
        ) {
            // A popped or older video Scene can still be composed while NavDisplay settles.
            // Only the stack's most recent video can drive the retained page's ordinary slide.
            val ordinary by rememberUpdatedState(
                videoCovered.value && motion.completedPop?.second != destination,
            )
            val ordinaryMotion = rememberNavigationMotion()
            val offset = visibility.animateFloat(
                transitionSpec = { if (ordinary) ordinaryMotion.pageSlideSpec else snap() },
                label = "retained video page slide",
            ) { state ->
                if (ordinary && state != EnterExitState.Visible) -ordinaryMotion.pageOffset else 0f
            }
            val page = remember(visibility, offset) {
                PlaybackNavigationPage(
                    transform = { NavigationPageTransform(x = offset.value) },
                    isTransitioning = {
                        ordinary && (visibility.isRunning || visibility.currentState != visibility.targetState)
                    },
                )
            }
            DisposableEffect(videoZoomState, page) {
                videoZoomState.navigationPage = page
                onDispose {
                    if (videoZoomState.navigationPage === page) videoZoomState.navigationPage = null
                }
            }
        }
        val imageScene = entries.last().usesImageTransition
        val ordinaryPop = !preview && !imageScene && !inStack &&
            previousEntries.any { it.contentKey == topKey } && motion.completedPop?.first != destination
        val exitOpacity = visibility.animateFloat(
            transitionSpec = { if (ordinaryPop) activityCloseFadeSpec() else snap() },
            label = "scene content retention",
        ) { if (it == EnterExitState.PostExit) 0f else 1f }
        // Keep the slide running, but retire invisible content so hits reach the incoming page.
        val retainContent by remember(ordinaryPop, exitOpacity) {
            derivedStateOf { !ordinaryPop || exitOpacity.value > 0f }
        }
        // A card return pops the video route at once and rebuilds this page as its own Scene; its
        // lifecycle takes a few frames to start. The page was already laid out under the video and the
        // user aims at its cards right away, so it takes input from the first frame.
        val cardReturn = videoZoomState?.cardReturn
        val revealedByCardReturn = cardReturn != null && cardReturn.sourceRoute == currentRoute.value
        val interactive = !preview && !zoomLayer && topKey == destination &&
            (lifecycleState.isAtLeast(Lifecycle.State.STARTED) || revealedByCardReturn) &&
            visibility.targetState == EnterExitState.Visible &&
            (!imageScene || (
                lifecycleState == Lifecycle.State.RESUMED && visibility.currentState == EnterExitState.Visible
                ))
        val parentScrollToTopEnabled = LocalStatusBarScrollToTopEnabled.current
        // A popped video Scene can outlive its flight while NavDisplay settles. It no longer owns
        // input. A video still in the stack under a pushed page keeps its input block.
        val passPointers = zoomLayer && !inStack
        val blockInput = retainContent && !interactive
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalRetainActivityPageContent provides retainContent,
            LocalStatusBarScrollToTopEnabled provides (
                parentScrollToTopEnabled && interactive && lifecycleState == Lifecycle.State.RESUMED &&
                    visibility.currentState == EnterExitState.Visible && !visibility.isRunning
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        // Keep the source laid out for return geometry. Skip drawing it when
                        // the retained video covers it or a covering Scene needs to reveal the video.
                        if (zoomLayer &&
                            (videoSourceCovered.value || videoZoomState?.videoCoversSource?.value == true)
                        ) {
                            return@drawWithContent
                        }
                        drawContent()
                    }
                    .blockNavigationInput(blockInput, passPointers),
            ) {
                if (zoomLayer) {
                    val source = checkNotNull(underlay)
                    if (!passPointers || source.top.contentKey == topKey) {
                        RenderPage(source, interactive = false)
                    }
                    RenderPage(page, interactive = false)
                } else if (preview && !page.sharesSettingsDirectory(underlay)) {
                    BackPreview(
                        motion,
                        entering = { RenderPage(checkNotNull(underlay), interactive = false) },
                        closing = { RenderPage(page, interactive = false) },
                    )
                } else {
                    RenderPage(
                        page,
                        interactive,
                        previewDetail = underlay.takeIf { preview },
                    )
                }
            }
        }
    }

    @Composable
    private fun RenderPage(
        page: ActivityScenePage<T>,
        interactive: Boolean,
        previewDetail: ActivityScenePage<T>? = null,
    ) {
        if (page.directory == null) {
            page.top.Content()
        } else {
            NavigationPage {
                CompositionLocalProvider(LocalNavigationPageTransform provides { NavigationPageTransform() }) {
                    SettingsPagePanes(page, interactive) {
                        Box(Modifier.fillMaxSize().background(ThemeColorDefaults.settingsBackgroundColor())) {
                            if (previewDetail == null) SettingsDetailContent(page, interactive)
                            else BackPreview(
                                motion,
                                entering = { SettingsDetail(previewDetail, isActive = false) },
                                closing = { SettingsDetail(page, isActive = false) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsPagePanes(
        page: ActivityScenePage<T>,
        interactive: Boolean,
        detail: @Composable () -> Unit,
    ) {
        val directory = checkNotNull(page.directory)
        val panes = checkNotNull(settingsPanes)
        val rail = rail.value.takeIf { panes.rail }
        val adjacent = panes.adjacent
        // Let the directory shadow extend over the adjoining detail, leaving a real hinge clear.
        val directorySurface = if (adjacent) {
            Modifier.barSurfaceLayer().background(ThemeColorDefaults.settingsBackgroundColor())
        } else Modifier
        WindowPane(
            panes.directory,
            modifier = if (adjacent) Modifier.zIndex(1f) else Modifier,
            clipContent = !adjacent,
        ) {
            CompositionLocalProvider(
                LocalSettingsPane provides SettingsPane.DIRECTORY,
                LocalSettingsDirectoryActive provides interactive,
                LocalSettingsDirectorySurface provides directorySurface,
                LocalNavigationPageStartGutter provides (rail?.width ?: 0.dp),
                LocalPageBackButtonVisible provides (rail == null),
            ) {
                directory.Content()
            }
            if (rail != null && settingsRoot != null && LocalSceneOwnsStartRail.current) {
                rail.content(settingsRoot, false)
            }
        }
        WindowPane(panes.detail, content = detail)
    }

    @Composable
    private fun SettingsDetailContent(page: ActivityScenePage<T>, interactive: Boolean) {
        val ordinaryMotion = rememberNavigationMotion()
        val statusBarScrollToTopEnabled = LocalStatusBarScrollToTopEnabled.current
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.top.contentKey },
            transitionSpec = {
                val initialCategory = initialState.settingsCategoryIndex
                val targetCategory = targetState.settingsCategoryIndex
                val transition = if (initialCategory != null && targetCategory != null &&
                    initialCategory != targetCategory
                ) {
                    val enterFromBottom = targetCategory > initialCategory
                    ordinaryMotion.vertical(enterFromBottom, isPop = !enterFromBottom)
                } else if (targetState.top.contentKey in initialState.settingsPath) {
                    ordinaryMotion.backward
                } else {
                    ordinaryMotion.forward
                }
                ContentTransform(
                    targetContentEnter = transition.targetContentEnter,
                    initialContentExit = transition.initialContentExit,
                    // Keep the same stacking order when an outgoing detail becomes the target again.
                    targetContentZIndex = (targetCategory ?: -1) +
                        if (targetState.top.metadata[SettingsPaneKey] == SettingsPane.SUBPAGE) 0.5f else 0f,
                    sizeTransform = null,
                )
            },
            label = "settings detail",
        ) { detail ->
            val selected = detail.top.contentKey == page.top.contentKey
            // An outgoing outer Scene only owns its own detail, including when resizing.
            if (selected || interactive) {
                val owner = rememberLifecycleOwner(
                    maxLifecycle = if (selected) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
                )
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalStatusBarScrollToTopEnabled provides (selected && statusBarScrollToTopEnabled),
                ) {
                    Box(Modifier.fillMaxSize().blockNavigationInput(!selected)) {
                        SettingsDetail(detail, isActive = selected && interactive)
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsDetail(page: ActivityScenePage<T>, isActive: Boolean) {
        if (page.top == page.directory) {
            NavigationPage {
                ContentUnavailableView(
                    state = ContentUnavailableState.EMPTY,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    title = stringResource(R.string.settings_select_item),
                    description = null,
                    icon = { SettingsSearchLogo(isActive = isActive) },
                )
            }
        } else {
            val role = checkNotNull(page.top.metadata[SettingsPaneKey])
            CompositionLocalProvider(
                LocalSettingsPane provides role,
                LocalPageBackButtonVisible provides (role == SettingsPane.SUBPAGE),
            ) {
                page.top.Content()
            }
        }
    }
}
