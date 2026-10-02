package moe.kirakira

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.feature.account.SessionViewModel
import moe.kirakira.ui.navigation.AppNavHost
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

@Composable
fun KIRAKIRAApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    themeColors: ThemeColorSettings = ThemeColorSettings(),
    onThemeColorsChange: (ThemeColorSettings) -> Unit = {},
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val sessionViewModel = if (LocalInspectionMode.current) null else viewModel<SessionViewModel>()
    val accountState = sessionViewModel?.state?.collectAsStateWithLifecycle()?.value ?: SessionState(isLoading = false)

    AppNavHost(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        themeColors = themeColors,
        onThemeColorsChange = onThemeColorsChange,
        onVideoPageActiveChange = onVideoPageActiveChange,
        onImageViewerActiveChange = onImageViewerActiveChange,
        accountState = accountState,
        authRepository = sessionViewModel?.repository,
        onSelectAccount = { sessionViewModel?.select(it) },
        onRemoveAccount = { sessionViewModel?.remove(it) },
        onLogout = { sessionViewModel?.logout() },
        onResetLocalAccounts = { sessionViewModel?.resetLocalAccounts() },
        onRetrySession = { sessionViewModel?.retry() },
        onDismissSessionError = { sessionViewModel?.dismissError() },
        modifier = modifier,
    )
}

@Preview(name = "English", locale = "en", showBackground = true)
@Preview(name = "中文", locale = "zh", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AppPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        KIRAKIRAApp(
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
        )
    }
}
