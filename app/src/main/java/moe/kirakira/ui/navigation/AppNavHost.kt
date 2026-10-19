package moe.kirakira.ui.navigation

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.account.SessionFeedback
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics
import moe.kirakira.feature.imageviewer.ImageExportViewModel
import moe.kirakira.feature.main.HomeViewModel
import moe.kirakira.feature.player.PlaybackHost
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.video.KaomojiViewModel
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

@Composable
internal fun AppNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColors: ThemeColorSettings,
    onThemeColorsChange: (ThemeColorSettings) -> Unit,
    predictiveBackEnabled: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    accountState: SessionState,
    dependencies: AppDependenciesViewModel?,
    onLogout: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onRetrySession: () -> Unit,
    onDismissSessionError: () -> Unit,
    onSelectAccount: (String?) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismissSessionNotice: () -> Unit,
    modifier: Modifier = Modifier,
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val imageExports = dependencies?.imageExports?.let {
        viewModel { ImageExportViewModel(applicationContext, it) }
    }
    LaunchedEffect(imageExports, applicationContext) {
        imageExports?.feedback?.collect { message ->
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
    val playbackSettingsModel = if (LocalInspectionMode.current) null else viewModel<PlaybackSettingsViewModel>()
    val playbackSettings = if (LocalInspectionMode.current) PlaybackSettings()
        else playbackSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val danmakuSettingsModel = if (LocalInspectionMode.current) null else viewModel<DanmakuSettingsViewModel>()
    val danmakuSettings = if (LocalInspectionMode.current) DanmakuSettings()
        else danmakuSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val backStack = rememberNavBackStack(MainRoute)
    val navigation = remember(backStack) { AppNavigationState(backStack) }
    val imageViewerActive = backStack.lastOrNull() is ImageViewerRoute || navigation.composedViewers.isNotEmpty()
    LaunchedEffect(backStack) {
        var previous = emptySet<String>()
        snapshotFlow { backStack.filterIsInstance<ImageViewerRoute>().map { it.instanceId }.toSet() }.collect { current ->
            (current - previous).forEach { ImageViewerDiagnostics.stack(it, present = true) }
            (previous - current).forEach { ImageViewerDiagnostics.stack(it, present = false) }
            previous = current
        }
    }
    // Discard retired demo destinations when restoring navigation after an upgrade.
    LaunchedEffect(Unit) {
        backStack.removeAll {
            it is TestRoute || (it is VideoRoute && it.videoId <= 0) || (it is ProfileRoute && it.uid <= 0) ||
                (it is TagRoute && it.tagId <= 0) || (it is FollowListRoute && it.uid <= 0)
        }
    }
    val autofill = LocalAutofillManager.current
    val imageEasing = rememberEmphasizedEasing()
    val imageMetadata = remember(imageEasing) { imageViewerNavigationMetadata(imageEasing) }
    val contentRepository = dependencies?.content
    val authRepository = dependencies?.auth
    val historyRepository = dependencies?.history
    // Only the IDE preview uses an empty handle; runtime creation uses the saved-state owner below.
    @SuppressLint("VisibleForTests")
    val playback = if (LocalInspectionMode.current) {
        remember { PlaybackViewModel(context.applicationContext, SavedStateHandle()) }
    } else viewModel { PlaybackViewModel(context.applicationContext, createSavedStateHandle(), historyRepository) }
    val kaomojiModel = dependencies?.let { viewModel { KaomojiViewModel(it.recentKaomoji) } }
    val homeViewModel = contentRepository?.let { viewModel { HomeViewModel(it) } }
    val searchViewModel = contentRepository?.let { viewModel { SearchViewModel(it) } }
    val videoPageActive = backStack.lastOrNull() is VideoRoute
    LaunchedEffect(backStack.lastOrNull()) {
        if (backStack.lastOrNull() is AuthRoute) playback.pause()
    }
    SideEffect {
        onVideoPageActiveChange(videoPageActive)
        onImageViewerActiveChange(imageViewerActive)
        val sessionChanged = playback.syncSession(accountState.revision)
        val video = backStack.lastOrNull() as? VideoRoute
        if (video != null && contentRepository != null && video.videoId > 0) playback.showVideo(video.videoId)
        else playback.leaveVideo(playbackSettings?.inAppMiniPlayer == true)
        if (sessionChanged) playback.cancelAutoplay()
        if (playback.miniPlayer && playbackSettings?.inAppMiniPlayer != true) playback.closeMiniPlayer()
    }

    SessionFeedback(
        state = accountState,
        onDismissError = onDismissSessionError,
        onRetry = onRetrySession,
        onResetLocalAccounts = onResetLocalAccounts,
        onLogin = { email ->
            if (backStack.lastOrNull() !is AuthRoute) backStack.add(AuthRoute(email))
        },
        onDismissNotice = onDismissSessionNotice,
    )

    // Keep the host opaque under translated pages and the predictive back preview.
    Box(modifier.fillMaxSize()) {
        ActivityNavDisplay(
            backStack = backStack,
            predictiveBackEnabled = predictiveBackEnabled,
            onBackRequested = {
                when (backStack.lastOrNull()) {
                    ProfileEditorRoute -> navigation.profileBackGuard?.requestBack() == true
                    PrivacySettingsRoute -> navigation.privacyBackGuard?.requestBack() == true
                    SecuritySettingsRoute -> {
                        autofill?.cancel()
                        navigation.securityBackGuard?.requestBack() == true
                    }
                    else -> true
                }
            },
            canNavigateBack = {
                when (backStack.lastOrNull()) {
                    ProfileEditorRoute -> navigation.profileBackGuard?.canNavigateBack == true
                    PrivacySettingsRoute -> navigation.privacyBackGuard?.canNavigateBack == true
                    SecuritySettingsRoute -> navigation.securityBackGuard?.canNavigateBack == true
                    else -> true
                }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            onBack = {
                if (backStack.lastOrNull() is AuthRoute) autofill?.cancel()
                val viewer = backStack.lastOrNull() as? ImageViewerRoute
                if (viewer != null) navigation.closeImageViewer(viewer, ImageViewerCloseReason.SYSTEM_BACK)
                else if (backStack.size > 1) backStack.removeLastOrNull()
            },
            onImageBack = { target ->
                if (target is ImageViewerRoute) navigation.closeImageViewer(target, ImageViewerCloseReason.SYSTEM_BACK)
            },
            entryProvider = entryProvider {
                registerMainEntries(this, navigation, accountState, homeViewModel, searchViewModel)
                registerContentEntries(
                    this, navigation, dependencies, accountState, playback, playbackSettings, danmakuSettings, kaomojiModel,
                    onDanmakuEnabled = { danmakuSettingsModel?.setEnabled(it) },
                    onQualityPreference = { playbackSettingsModel?.setQuality(it) },
                )
                registerImageViewerEntries(this, navigation, imageMetadata, imageExports)
                registerSettingsEntries(
                    this, navigation, dependencies, accountState, onLogout,
                    themeMode, onThemeModeChange, themeColors, onThemeColorsChange,
                    predictiveBackEnabled, onPredictiveBackEnabledChange,
                    playbackSettingsModel, playbackSettings, danmakuSettingsModel, danmakuSettings,
                )
                registerAccountEntries(
                    this, navigation, authRepository, accountState, onSelectAccount, onRemoveAccount,
                    predictiveBackEnabled,
                )
            },
        )
        val restoreOrigin = backStack.lastOrNull()
        PlaybackHost(
            playback = playback,
            settings = playbackSettings,
            videoPageActive = videoPageActive,
            imageViewerActive = imageViewerActive,
            bottomBarHeightPx = if (backStack.lastOrNull() == MainRoute) navigation.bottomBarHeightPx else 0,
            onRestore = {
                if (navigation.composedViewers.isEmpty() && backStack.lastOrNull() !is ImageViewerRoute &&
                    backStack.lastOrNull() == restoreOrigin
                ) {
                    playback.videoId?.let { id ->
                        val route = VideoRoute(id)
                        val index = backStack.indexOf(route)
                        if (index >= 0) {
                            while (backStack.lastIndex > index) backStack.removeLastOrNull()
                        } else backStack.add(route)
                    }
                }
            },
        )
    }
}
