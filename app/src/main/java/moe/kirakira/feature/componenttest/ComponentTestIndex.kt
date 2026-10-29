package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import moe.kirakira.R
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.connectedListItemShapes

@Serializable
internal enum class ComponentCategory(val title: String) {
    BUTTONS("按钮"),
    CONTROLS("输入与选择"),
    CONTENT("内容展示"),
    LISTS("列表与标签"),
    OVERLAYS("弹层与反馈"),
}

@Composable
internal fun ComponentTestScreen(
    onBack: () -> Unit,
    onOpenCategory: (ComponentCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = "组件测试",
        onBack = onBack,
        shadingIcon = R.drawable.ic_symbol_palette,
        modifier = modifier,
    ) { padding ->
        val direction = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = SettingsDefaults.MaxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                ),
            ) {
                item(key = "categories") {
                    ConnectedListGroup {
                        ComponentCategory.entries.forEachIndexed { index, category ->
                            SegmentedListItem(
                                onClick = { onOpenCategory(category) },
                                shapes = connectedListItemShapes(index, ComponentCategory.entries.size),
                                trailingContent = {
                                    Icon(
                                        painterResource(R.drawable.ic_symbol_chevron_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                content = { Text(category.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}
