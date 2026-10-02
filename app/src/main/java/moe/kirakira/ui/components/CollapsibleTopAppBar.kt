package moe.kirakira.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

/**
 * 可选的大标题顶栏。页面需将同一个 [scrollBehavior] 的 nestedScrollConnection
 * 接到滚动内容的父容器，并自行处理 Scaffold 的内容内边距。
 */
@Composable
fun CollapsibleTopAppBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack, modifier = backButtonModifier) {
                Icon(
                    painter = painterResource(R.drawable.ic_symbol_arrow_back),
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * 每个页面独立创建；默认首次进入折叠，上滑收起，内容到顶后下拉展开。
 * [initialCollapsed] 仅用于首次创建，状态恢复时保留用户之前的展开程度。
 */
@Composable
fun rememberCollapsibleTopAppBarScrollBehavior(
    initialCollapsed: Boolean = true,
): TopAppBarScrollBehavior {
    val heightOffsetLimit = with(LocalDensity.current) {
        -(TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight -
            TopAppBarDefaults.LargeAppBarCollapsedHeight).toPx()
    }
    val state = rememberTopAppBarState(
        initialHeightOffsetLimit = heightOffsetLimit,
        initialHeightOffset = if (initialCollapsed) heightOffsetLimit else 0f,
    )
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
}

@Preview(name = "Collapsible top bar · Collapsed", showBackground = true)
@Composable
private fun CollapsibleTopAppBarCollapsedPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        CollapsibleTopAppBar(
            title = stringResource(R.string.me_settings),
            onBack = {},
            scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior(),
        )
    }
}

@Preview(name = "Collapsible top bar · Expanded", showBackground = true)
@Composable
private fun CollapsibleTopAppBarExpandedPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        CollapsibleTopAppBar(
            title = stringResource(R.string.me_settings),
            onBack = {},
            scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior(initialCollapsed = false),
        )
    }
}
