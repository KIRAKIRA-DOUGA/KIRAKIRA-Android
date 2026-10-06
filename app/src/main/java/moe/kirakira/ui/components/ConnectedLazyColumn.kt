package moe.kirakira.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyScopeMarker
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Forward, full-width connected rows with one shadow per visible group. */
@Composable
fun ConnectedLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: ConnectedLazyListScope.() -> Unit,
) {
    val listContent = ConnectedLazyListScope().apply(content).build()
    val shape = connectedListItemShapes(0, 1).shape
    val overscrollEffect = rememberOverscrollEffect()
    LazyColumn(
        // Stretch the shadow and rows together; the list only forwards overscroll events.
        modifier = modifier
            .clipToBounds()
            .overscroll(overscrollEffect)
            .then(ConnectedLazyShadowElement(state, listContent.groups, shape, contentPadding)),
        state = state,
        contentPadding = contentPadding,
        overscrollEffect = overscrollEffect?.withoutVisualEffect(),
    ) {
        listContent.blocks.forEach { block -> block(this) }
    }
}

/** Builds ordinary items and contiguous groups together, keeping their indices in sync. */
@LazyScopeMarker
class ConnectedLazyListScope internal constructor() {
    private val blocks = mutableListOf<LazyListScope.() -> Unit>()
    private val groups = mutableListOf<ConnectedLazyGroup>()
    private val groupKeys = mutableSetOf<Any>()
    private var itemCount = 0

    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        blocks += { item(key = key, contentType = contentType, content = content) }
        itemCount++
    }

    fun items(
        count: Int,
        key: ((Int) -> Any)? = null,
        contentType: (Int) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        require(count >= 0)
        blocks += { items(count = count, key = key, contentType = contentType, itemContent = itemContent) }
        itemCount += count
    }

    /**
     * [groupKey] must be unique within the list and stable across updates; item keys remain unchanged.
     * Rows must fill the available width and have no external vertical padding or placement animation.
     */
    fun <T> connectedItemsIndexed(
        groupKey: Any,
        items: List<T>,
        key: (Int, T) -> Any,
        contentType: (Int, T) -> Any? = { _, _ -> null },
        itemContent: @Composable LazyItemScope.(Int, T) -> Unit,
    ) {
        require(groupKeys.add(groupKey)) { "Connected group keys must be unique within the list." }
        if (items.isEmpty()) return
        groups += ConnectedLazyGroup(groupKey, itemCount, itemCount + items.lastIndex)
        items(
            count = items.size,
            key = { index -> key(index, items[index]) },
            contentType = { index -> contentType(index, items[index]) },
        ) { index ->
            itemContent(index, items[index])
        }
    }

    internal fun build(): ConnectedLazyContent = ConnectedLazyContent(blocks.toList(), groups.toList())
}

internal data class ConnectedLazyContent(
    val blocks: List<LazyListScope.() -> Unit>,
    val groups: List<ConnectedLazyGroup>,
)

internal data class ConnectedLazyGroup(val key: Any, val firstIndex: Int, val lastIndex: Int)

private data class ConnectedLazyShadowElement(
    val state: LazyListState,
    val groups: List<ConnectedLazyGroup>,
    val shape: Shape,
    val contentPadding: PaddingValues,
) : ModifierNodeElement<ConnectedLazyShadowNode>() {
    override fun create(): ConnectedLazyShadowNode = ConnectedLazyShadowNode(state, groups, shape, contentPadding)

    override fun update(node: ConnectedLazyShadowNode) {
        node.state = state
        node.groups = groups
        node.shape = shape
        node.contentPadding = contentPadding
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "connectedLazyListShadow"
    }
}

private class ConnectedLazyShadowNode(
    var state: LazyListState,
    var groups: List<ConnectedLazyGroup>,
    var shape: Shape,
    var contentPadding: PaddingValues,
) : Modifier.Node(), DrawModifierNode {
    private lateinit var graphicsContext: GraphicsContext
    private val layers = mutableMapOf<Any, GraphicsLayer>()

    override fun onAttach() {
        graphicsContext = requireGraphicsContext()
    }

    override fun onDetach() {
        layers.values.forEach(graphicsContext::releaseGraphicsLayer)
        layers.clear()
    }

    override fun ContentDrawScope.draw() {
        // Reading layoutInfo here invalidates drawing on scroll without recomposing rows.
        val layout = state.layoutInfo
        val visibleGroups = mutableMapOf<Any, VisibleConnectedGroup>()
        layout.visibleItemsInfo.forEach { item ->
            val groupIndex = groups.binarySearch { group ->
                when {
                    group.lastIndex < item.index -> -1
                    group.firstIndex > item.index -> 1
                    else -> 0
                }
            }
            if (groupIndex >= 0) {
                val group = groups[groupIndex]
                val top = (item.offset - layout.viewportStartOffset).toFloat()
                val bottom = top + item.size
                val visible = visibleGroups.getOrPut(group.key) { VisibleConnectedGroup(group, top, bottom) }
                visible.top = min(visible.top, top)
                visible.bottom = max(visible.bottom, bottom)
                if (item.index == group.firstIndex) visible.firstTop = top
                if (item.index == group.lastIndex) visible.lastBottom = bottom
            }
        }
        val iterator = layers.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in visibleGroups) {
                graphicsContext.releaseGraphicsLayer(entry.value)
                iterator.remove()
            }
        }
        val left = contentPadding.calculateLeftPadding(layoutDirection).roundToPx().toFloat()
        val right = contentPadding.calculateRightPadding(layoutDirection).roundToPx().toFloat()
        val width = size.width - left - right
        if (width > 0f && size.height > 0f) {
            clipRect {
                visibleGroups.values.forEach { visible ->
                    // Unmeasured ends stay one viewport away; only real group ends cast caps in view.
                    val top = visible.firstTop ?: (min(visible.top, 0f) - size.height)
                    val bottom = visible.lastBottom ?: (max(visible.bottom, size.height) + size.height)
                    val height = bottom - top
                    if (height > 0f) {
                        val layer = layers.getOrPut(visible.group.key) { graphicsContext.createGraphicsLayer() }
                        val layerSize = IntSize(ceil(width).toInt(), ceil(height).toInt())
                        if (layer.size != layerSize) {
                            // An empty display list casts the outline's shadow without painting a background.
                            layer.record(this, layoutDirection, layerSize) {}
                        }
                        layer.setOutline(shape.createOutline(Size(width, height), layoutDirection, this))
                        layer.shadowElevation = ConnectedListShadowElevation.toPx()
                        translate(left, top) { drawLayer(layer) }
                    }
                }
            }
        }
        drawContent()
    }
}

private class VisibleConnectedGroup(
    val group: ConnectedLazyGroup,
    var top: Float,
    var bottom: Float,
) {
    var firstTop: Float? = null
    var lastBottom: Float? = null
}
