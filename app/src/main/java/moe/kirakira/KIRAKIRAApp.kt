package moe.kirakira

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.feature.account.DemoAccountState
import moe.kirakira.ui.navigation.AppNavHost
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode

@Composable
fun KIRAKIRAApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var accountState by rememberSaveable(stateSaver = DemoAccountState.Saver) {
        mutableStateOf(DemoAccountState())
    }

    AppNavHost(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        accountState = accountState,
        onSelectAccount = { accountState = accountState.select(it) },
        onRemoveAccount = { accountState = accountState.remove(it) },
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
