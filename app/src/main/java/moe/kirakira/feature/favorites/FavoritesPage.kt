package moe.kirakira.feature.favorites

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FavoritesLogo
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun FavoritesPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberCollapsibleTopAppBarScrollBehavior()
    FrostedScaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Box {
                ShadingIcon(
                    icon = R.drawable.ic_symbol_star,
                    modifier = Modifier.matchParentSize(),
                    endPadding = 0.dp,
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(32.dp, 32.dp),
                )
                CollapsibleTopAppBar(
                    title = stringResource(R.string.me_favorites),
                    onBack = onBack,
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            title = stringResource(R.string.content_not_available_yet),
            description = null,
            icon = { FavoritesLogo() },
        )
    }
}

@Preview(name = "收藏", locale = "zh", widthDp = 360, heightDp = 640)
@Preview(name = "Favorites dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Composable
private fun FavoritesPagePreview() {
    KIRAKIRATheme { FavoritesPage(onBack = {}) }
}
