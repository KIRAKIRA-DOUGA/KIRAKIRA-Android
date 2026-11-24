package moe.kirakira

import android.app.Application
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.feature.account.SessionViewModel
import moe.kirakira.navigation.AppNavHost
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopHost
import moe.kirakira.ui.theme.AppearanceSettings
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun KIRAKIRAApp(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    modifier: Modifier = Modifier,
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val dependencies = if (LocalInspectionMode.current) null else viewModel<AppDependenciesViewModel>()
    val sessionViewModel = dependencies?.let {
        viewModel { SessionViewModel(context.applicationContext as Application, it.auth, it.credentials) }
    }
    val accountState = sessionViewModel?.state?.collectAsStateWithLifecycle()?.value ?: SessionState(isLoading = false)

    StatusBarScrollToTopHost {
        AppNavHost(
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            onVideoPageActiveChange = onVideoPageActiveChange,
            onImageViewerActiveChange = onImageViewerActiveChange,
            accountState = accountState,
            dependencies = dependencies,
            onSelectAccount = { sessionViewModel?.select(it) },
            onRemoveAccount = { sessionViewModel?.remove(it) },
            onLogout = { sessionViewModel?.logout() },
            onResetLocalAccounts = { sessionViewModel?.resetLocalAccounts() },
            onRetrySession = { sessionViewModel?.retry() },
            onDismissSessionError = { sessionViewModel?.dismissError() },
            modifier = modifier,
        )
    }
}

@Preview(name = "English", locale = "en", showBackground = true)
@Preview(name = "中文", locale = "zh", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AppPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        KIRAKIRAApp(
            appearance = AppearanceSettings(),
            onAppearanceChange = {},
        )
    }
}
