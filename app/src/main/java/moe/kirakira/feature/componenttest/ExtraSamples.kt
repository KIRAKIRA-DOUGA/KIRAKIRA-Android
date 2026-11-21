package moe.kirakira.feature.componenttest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.color.ColorValueFormat
import moe.kirakira.ui.components.AppChipDefaults
import moe.kirakira.ui.components.ColorEditorControls
import moe.kirakira.ui.components.ColorEditorInput
import moe.kirakira.ui.components.ColorEditorState
import moe.kirakira.ui.components.FilledTextFieldDefaults
import moe.kirakira.ui.components.PagerTabRow

@Composable
internal fun ColorPickerSample(parentContainerColor: Color, modifier: Modifier = Modifier) {
    val seed = MaterialTheme.colorScheme.primary.toArgb()
    val state = remember { ColorEditorState(seed) }
    ColorEditorControls(
        state = state,
        modifier = modifier.fillMaxWidth(),
        previewContent = {
            Box(
                Modifier.fillMaxWidth().height(24.dp)
                    .background(Color(state.argb), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        inputContent = { onColorChanged ->
            val colors = AppChipDefaults.filterColors(parentContainerColor)
            SampleRow {
                ColorValueFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { state.selectFormat(format) },
                        label = { Text(format.name) },
                        shape = CircleShape,
                        colors = colors,
                        border = null,
                    )
                }
            }
            ColorEditorInput(state, onColorChanged, colors = FilledTextFieldDefaults.colors(parentContainerColor))
        },
    )
}

@Composable
internal fun PagerTabsSample(modifier: Modifier = Modifier) {
    val titles = listOf(
        stringResource(R.string.component_gallery_tab_first),
        stringResource(R.string.component_gallery_tab_second),
        stringResource(R.string.component_gallery_tab_third),
    )
    val pager = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        PagerTabRow(
            pagerState = pager,
            titles = titles,
            onTabChange = { page -> scope.launch { pager.animateScrollToPage(page) } },
            modifier = Modifier.fillMaxWidth(),
            scrollable = true,
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.component_gallery_tab_page, page + 1, titles.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
