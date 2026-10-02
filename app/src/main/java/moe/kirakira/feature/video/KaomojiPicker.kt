package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.R
import moe.kirakira.data.kaomoji.kaomojiCatalog
import moe.kirakira.ui.theme.KIRAKIRATheme

private val kaomojiCategories = linkedMapOf(
    "recent" to R.string.kaomoji_recent,
    "happy" to R.string.kaomoji_happy,
    "greet" to R.string.kaomoji_greet,
    "moe" to R.string.kaomoji_moe,
    "sad" to R.string.kaomoji_sad,
    "embarrassed" to R.string.kaomoji_embarrassed,
)

@Composable
internal fun KaomojiPicker(
    category: String,
    recent: List<String>,
    onCategory: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(kaomojiCategories.keys.toList(), key = { it }) { id ->
                    FilterChip(
                        selected = category == id,
                        onClick = { onCategory(id) },
                        label = { Text(stringResource(kaomojiCategories.getValue(id))) },
                        enabled = enabled,
                    )
                }
            }
            val entries = if (category == "recent") recent else kaomojiCatalog[category].orEmpty()
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.kaomoji_recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
                    val density = LocalDensity.current
                    val minimumWidth = 112.dp * density.fontScale
                    val columns = ((maxWidth + 8.dp) / (minimumWidth + 8.dp)).toInt().coerceAtLeast(1)
                    val cellWidth = (maxWidth - 8.dp * (columns - 1)) / columns
                    val measurer = rememberTextMeasurer()
                    val textStyle = MaterialTheme.typography.bodyLarge
                    val spans = remember(entries, textStyle, density, cellWidth, columns, measurer) {
                        entries.map { text ->
                            val width = measurer.measure(AnnotatedString(text), style = textStyle, softWrap = false).size.width
                            val desired = with(density) { width.toDp() } + 24.dp
                            ceil((desired + 8.dp) / (cellWidth + 8.dp)).toInt().coerceIn(1, columns)
                        }
                    }
                    key(category) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(entries, key = { index, text -> if (category == "recent") text else "$category:$index" },
                                span = { index, _ -> GridItemSpan(spans[index]) }) { _, text ->
                                ShadowFilledTonalButton(
                                    onClick = { onSelect(text) },
                                    enabled = enabled,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(text, style = textStyle, softWrap = false,
                                        modifier = Modifier.horizontalScroll(rememberScrollState()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Kaomoji · Light", widthDp = 360)
@Preview(name = "Kaomoji · Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Kaomoji · Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun KaomojiPickerPreview() {
    KIRAKIRATheme {
        KaomojiPicker("happy", emptyList(), {}, {}, Modifier.height(300.dp))
    }
}
