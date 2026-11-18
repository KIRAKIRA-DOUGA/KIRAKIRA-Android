package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.ui.components.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.cardColorShadow

@Composable
internal fun ComponentGalleryScreen(
    onBack: () -> Unit,
    onOpenComponent: (GalleryComponent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.component_gallery_title),
        onBack = onBack,
        shadingIcon = R.drawable.ic_icon_palette,
        modifier = modifier,
    ) { padding ->
        val gridState = rememberLazyGridState()
        StatusBarScrollToTopEffect(gridState)
        val direction = LocalLayoutDirection.current
        val minCardWidth = 152.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCardWidth),
                state = gridState,
                modifier = Modifier.widthIn(max = 800.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                galleryComponentsByGroup.forEach { (group, components) ->
                    item(key = group.name, span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                        Text(
                            text = stringResource(group.title),
                            modifier = Modifier
                                .padding(top = if (group == GalleryGroup.BUTTONS) 0.dp else 12.dp)
                                .semantics { heading() },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(components, key = { it.name }, contentType = { "component" }) { component ->
                        ComponentGalleryCard(component, onClick = { onOpenComponent(component) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentGalleryCard(
    component: GalleryComponent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().cardColorShadow(shape, colors.surface),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        ComponentThumbnail(
            component = component,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clearAndSetSemantics { hideFromAccessibility() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(component.title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                painter = painterResource(R.drawable.ic_icon_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
