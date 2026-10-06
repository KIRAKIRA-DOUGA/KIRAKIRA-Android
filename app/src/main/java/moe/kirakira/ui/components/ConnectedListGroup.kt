package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

internal val ConnectedListShadowElevation = 1.dp

@Composable
fun ConnectedListGroup(
    modifier: Modifier = Modifier,
    clipContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = connectedListItemShapes(0, 1).shape
    Column(
        modifier = modifier.fillMaxWidth()
            .shadow(ConnectedListShadowElevation, shape, clip = clipContent),
    ) {
        content()
    }
}

@Composable
fun connectedListItemShapes(index: Int, count: Int): ListItemShapes {
    val shape = ListItemDefaults.segmentedShapes(
        index = index,
        count = count,
        defaultShapes = ListItemDefaults.shapes(shape = RoundedCornerShape(0.dp)),
    ).shape
    return ListItemDefaults.shapes(
        shape = shape,
        selectedShape = shape,
        pressedShape = shape,
        focusedShape = shape,
        hoveredShape = shape,
        draggedShape = shape,
    )
}
