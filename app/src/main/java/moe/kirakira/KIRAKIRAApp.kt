package moe.kirakira

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.serialization.Serializable
import moe.kirakira.feature.me.MeScreen
import moe.kirakira.feature.search.SearchScreen
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.ui.components.PlaceholderAvatar
import moe.kirakira.ui.navigation.ActivityNavDisplay
import moe.kirakira.ui.navigation.NavigationPage
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode

@Serializable internal data object MainRoute : NavKey
@Serializable internal data object SettingsRoute : NavKey
@Serializable internal data object AboutRoute : NavKey
@Serializable internal data object AppearanceRoute : NavKey

private enum class AppDestination(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    HOME(R.string.nav_home, R.drawable.ic_symbol_home),
    SEARCH(R.string.nav_search, R.drawable.ic_symbol_search),
    FOLLOWING(R.string.nav_following, R.drawable.ic_symbol_subscriptions),
    ME(R.string.nav_me, R.drawable.ic_symbol_person),
}

@Composable
fun KIRAKIRAApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(MainRoute)

    // Keep the host opaque under translated pages and the predictive back preview.
    ActivityNavDisplay(
        backStack = backStack,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<MainRoute> {
                NavigationPage {
                    MainScreen(
                        onOpenSettings = {
                            if (backStack.lastOrNull() == MainRoute) backStack.add(SettingsRoute)
                        },
                    )
                }
            }
            entry<SettingsRoute> {
                NavigationPage {
                    SettingsScreen(
                        onBack = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.removeLastOrNull()
                        },
                        onNavigateToAbout = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(AboutRoute)
                        },
                        onNavigateToAppearance = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(AppearanceRoute)
                        },
                        onNavigateToAccount = {
                            // TODO: Navigate to account
                        },
                    )
                }
            }
            entry<AboutRoute> {
                NavigationPage {
                    AboutScreen(
                        onBack = {
                            if (backStack.lastOrNull() == AboutRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<AppearanceRoute> {
                NavigationPage {
                    AppearanceScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onBack = {
                            if (backStack.lastOrNull() == AppearanceRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun MainScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val meScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            when (destination) {
                AppDestination.HOME -> TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        PlaceholderAvatar(
                            modifier = Modifier.padding(end = 16.dp),
                            contentDescription = stringResource(R.string.profile_avatar),
                        )
                    },
                )
                AppDestination.FOLLOWING -> TopAppBar(
                    title = { Text(stringResource(R.string.nav_following)) },
                )
                AppDestination.ME -> TopAppBar(
                    title = { Text(stringResource(R.string.nav_me)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                AppDestination.SEARCH -> Unit
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Icon(painterResource(item.icon), contentDescription = null)
                        },
                        label = {
                            Text(
                                text = stringResource(item.label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("nav_${item.name.lowercase()}"),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (destination) {
                AppDestination.HOME -> Box(Modifier.fillMaxSize().testTag("home_screen"))
                AppDestination.SEARCH -> SearchScreen()
                AppDestination.FOLLOWING -> Box(Modifier.fillMaxSize().testTag("following_screen"))
                AppDestination.ME -> MeScreen(
                    onOpenSettings = onOpenSettings,
                    scrollState = meScrollState,
                )
            }
        }
    }
}

@Preview(name = "English", locale = "en", showBackground = true)
@Preview(name = "中文", locale = "zh", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AppPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        KIRAKIRAApp(
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {}
        )
    }
}
