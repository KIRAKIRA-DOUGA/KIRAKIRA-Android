package moe.kirakira.feature.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.feature.me.MeScreen
import moe.kirakira.feature.search.SearchScreen
import moe.kirakira.ui.components.PlaceholderAvatar

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
internal fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val meScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MainTopBar(
                destination = destination,
                onOpenMe = { destination = AppDestination.ME },
            )
        },
        bottomBar = {
            MainBottomBar(
                destination = destination,
                onDestinationChange = { destination = it },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (destination) {
                AppDestination.HOME -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_screen"),
                )
                AppDestination.SEARCH -> SearchScreen()
                AppDestination.FOLLOWING -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("following_screen"),
                )
                AppDestination.ME -> MeScreen(
                    onOpenSettings = onOpenSettings,
                    scrollState = meScrollState,
                )
            }
        }
    }
}

@Composable
private fun MainTopBar(
    destination: AppDestination,
    onOpenMe: () -> Unit,
) {
    when (destination) {
        AppDestination.HOME -> TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(
                    onClick = onOpenMe,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    PlaceholderAvatar(
                        contentDescription = stringResource(R.string.nav_me),
                    )
                }
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
}

@Composable
private fun MainBottomBar(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        AppDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = destination == item,
                onClick = { onDestinationChange(item) },
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
}
