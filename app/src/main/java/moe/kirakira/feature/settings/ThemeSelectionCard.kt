package moe.kirakira.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R

/** Equal-height cards for one lazy-list row, including empty cells in the last row. */
@Composable
internal fun <T> ThemeCardRow(
    items: List<T>,
    columns: Int,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    content: @Composable (T, Modifier) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            key(itemKey(item)) {
                content(
                    item,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
internal fun ThemeSelectionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    seedColor: Color? = null,
    preview: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val labelContainer = animateColorAsState(
        if (selected) scheme.primary else scheme.surface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "themeCardLabelContainer",
    ).value
    val contentColor = animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "themeCardContent",
    ).value
    val supportingColor = animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "themeCardSupporting",
    ).value
    val checkColor = animateColorAsState(
        scheme.onPrimary.copy(alpha = if (selected) 1f else 0f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "themeCardCheck",
    ).value
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 8.dp,
            focusedElevation = 4.dp,
            hoveredElevation = 4.dp,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.primary) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clearAndSetSemantics {},
                content = preview,
            )
        }
        // Keep footers aligned when titles wrap to different heights within one row.
        val labelModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(labelContainer)
            .padding(12.dp)
            .clearAndSetSemantics {}
        Row(
            modifier = labelModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seedColor != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(seedColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_check),
                        contentDescription = null,
                        tint = checkColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = supportingColor,
                    )
                }
            }
        }
    }
}
