package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.kaomoji.kaomojiCatalog
import moe.kirakira.ui.components.PagerTabRow
import moe.kirakira.ui.components.rememberTabChangeHandler
import moe.kirakira.ui.components.windowCornerRadius
import moe.kirakira.ui.theme.KIRAKIRATheme

private const val KAOMOJI_COLUMNS = 4

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
    contentPadding: PaddingValues = PaddingValues(),
) {
    val cornerRadius = with(LocalDensity.current) { LocalView.current.windowCornerRadius().toDp() }
    val layoutDirection = LocalLayoutDirection.current
    val categoryIds = remember { kaomojiCategories.keys.toList() }
    val pagerState = rememberPagerState(
        initialPage = categoryIds.indexOf(category).coerceAtLeast(0),
        pageCount = { categoryIds.size },
    )
    val changeTab = rememberTabChangeHandler(pagerState)
    LaunchedEffect(pagerState.currentPage) {
        onCategory(categoryIds[pagerState.currentPage])
    }
    // 面板边界阻止带动视频分页器；网格内同样隔离长条目阅读与分类切页。
    val horizontalScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    Surface(
        modifier = modifier.nestedScroll(horizontalScrollConnection),
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            PagerTabRow(
                pagerState = pagerState,
                titles = categoryIds.map { stringResource(kaomojiCategories.getValue(it)) },
                onTabChange = changeTab,
                modifier = Modifier.fillMaxWidth().padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                scrollable = true,
                minTabWidth = 0.dp,
                enabled = enabled,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                userScrollEnabled = enabled,
                key = { categoryIds[it] },
            ) { page ->
                val id = categoryIds[page]
                KaomojiGrid(
                    category = id,
                    entries = if (id == "recent") recent else kaomojiCatalog[id].orEmpty(),
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxSize().nestedScroll(horizontalScrollConnection),
                    enabled = enabled,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun KaomojiGrid(
    category: String,
    entries: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (entries.isEmpty()) {
        Box(modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.kaomoji_recent_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val layoutDirection = LocalLayoutDirection.current
    val gridPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 12.dp,
    )
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val textStyle = MaterialTheme.typography.bodyLarge
        val minFontSize = MaterialTheme.typography.bodySmall.fontSize
        val gridWidth = (maxWidth - gridPadding.calculateStartPadding(layoutDirection) -
            gridPadding.calculateEndPadding(layoutDirection)).coerceAtLeast(0.dp)
        val cellWidth = (gridWidth - 4.dp * (KAOMOJI_COLUMNS - 1)) / KAOMOJI_COLUMNS
        val textWidth = with(density) { (cellWidth - 8.dp).toPx().toInt().coerceAtLeast(0) }
        val autoSize = remember(textWidth, minFontSize, textStyle.fontSize) {
            KaomojiTextAutoSize(
                width = textWidth,
                delegate = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = textStyle.fontSize),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(KAOMOJI_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { index, text -> if (category == "recent") text else "$category:$index" },
            ) { _, text ->
                TextButton(
                    onClick = { onSelect(text) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        style = textStyle,
                        autoSize = autoSize,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Size against the visible cell before horizontalScroll removes its width constraint. */
private data class KaomojiTextAutoSize(
    val width: Int,
    val delegate: TextAutoSize,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        with(delegate) {
            getFontSize(constraints.copy(minWidth = 0, maxWidth = minOf(width, constraints.maxWidth)), text)
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
