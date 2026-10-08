package moe.kirakira.ui.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PagerTabRow(
    pagerState: PagerState,
    titles: List<String>,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    scrollable: Boolean = false,
    minTabWidth: Dp = TabRowDefaults.ScrollableTabRowMinTabWidth,
    enabled: Boolean = true,
) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { onTabChange(index) },
                enabled = enabled,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(title) },
            )
        }
    }
    if (scrollable) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = modifier,
            containerColor = containerColor,
            edgePadding = 12.dp,
            minTabWidth = minTabWidth,
            indicator = { PagerTabIndicator(pagerState) },
            divider = {},
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = modifier,
            containerColor = containerColor,
            indicator = { PagerTabIndicator(pagerState) },
            divider = {},
            tabs = tabs,
        )
    }
}
