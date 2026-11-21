package moe.kirakira.feature.componenttest

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FilledTextFieldContainer
import moe.kirakira.ui.components.FilledTextFieldDefaults
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.components.PlaceholderAvatar
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.cardColorShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.pressToRoundButtonShapes
import moe.kirakira.ui.components.pressToRoundToggleButtonShapes
import moe.kirakira.ui.components.textFieldInnerShadow
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun ComponentThumbnail(component: GalleryComponent, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val cover = colors.surfaceContainer
    Box(
        modifier = modifier.background(cover).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (component) {
            GalleryComponent.TEXT_BUTTON -> PreviewButton(shape = pressToRoundButtonShapes().shape) {
                Text(stringResource(R.string.profile_save), style = MaterialTheme.typography.labelLarge)
            }
            GalleryComponent.ICON_BUTTON -> {
                val buttonColors = IconButtonDefaults.filledIconButtonColors()
                PreviewButton(shape = CircleShape, modifier = Modifier.size(44.dp), color = buttonColors.containerColor) {
                    PreviewIcon(R.drawable.ic_icon_edit, Modifier.size(24.dp), tint = buttonColors.contentColor)
                }
            }
            GalleryComponent.FAB -> PreviewButton(
                shape = CircleShape,
                modifier = Modifier.size(50.dp),
            ) {
                PreviewIcon(R.drawable.ic_icon_add, modifier = Modifier.size(24.dp))
            }
            GalleryComponent.TOGGLE_BUTTON -> {
                val shapes = pressToRoundToggleButtonShapes(ToggleButtonDefaults.size)
                val buttonColors = ToggleButtonDefaults.colors()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewButton(
                        shape = shapes.shape,
                        modifier = Modifier.size(40.dp),
                        color = buttonColors.containerColor,
                    ) {
                        PreviewIcon(R.drawable.ic_icon_star, tint = buttonColors.contentColor)
                    }
                    PreviewButton(
                        shape = shapes.checkedShape,
                        modifier = Modifier.size(40.dp),
                        color = buttonColors.checkedContainerColor,
                    ) {
                        PreviewIcon(R.drawable.ic_icon_star, tint = buttonColors.checkedContentColor)
                    }
                }
            }
            GalleryComponent.FOLLOW_BUTTON -> PreviewButton(shape = pressToRoundButtonShapes().shape) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewIcon(R.drawable.ic_icon_person_add, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.video_follow), style = MaterialTheme.typography.labelLarge)
                }
            }
            GalleryComponent.SEGMENTED -> Row(
                modifier = Modifier
                    .background(ThemeColorDefaults.controlContainerColor(cover), shape)
                    .textFieldInnerShadow(shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewButton(shape = shape) { PreviewIcon(R.drawable.ic_icon_list) }
                PreviewIcon(
                    R.drawable.ic_icon_grid,
                    modifier = Modifier.padding(horizontal = 12.dp).size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            GalleryComponent.SWITCH -> Switch(checked = true, onCheckedChange = null)
            GalleryComponent.CHECKBOX -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked = true, onCheckedChange = null)
                Checkbox(checked = false, onCheckedChange = null)
            }
            GalleryComponent.RADIO -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShadowRadioButton(selected = true, onClick = null)
                ShadowRadioButton(selected = false, onClick = null)
            }
            GalleryComponent.SLIDER -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewLine(colors.primary, Modifier.width(36.dp), height = 8)
                PreviewLine(colors.primary, Modifier.width(5.dp), height = 24)
                PreviewLine(colors.surface, Modifier.width(36.dp), height = 8)
            }
            GalleryComponent.TEXT_FIELD -> PreviewField(R.drawable.ic_icon_text_format, cover) {
                PreviewLine(colors.onSurfaceVariant, Modifier.width(34.dp))
            }
            GalleryComponent.PASSWORD -> PreviewField(R.drawable.ic_icon_lock, cover) {
                repeat(3) { PreviewLine(colors.onSurfaceVariant, Modifier.width(4.dp)) }
            }
            GalleryComponent.OTP -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) { index ->
                    val cellShape = MaterialTheme.shapes.medium
                    Box(
                        modifier = Modifier
                            .size(width = 13.dp, height = 28.dp)
                            .background(ThemeColorDefaults.controlContainerColor(cover), cellShape)
                            .textFieldInnerShadow(cellShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index < 2) PreviewLine(colors.primary, Modifier.width(4.dp))
                    }
                }
            }
            GalleryComponent.COLOR_PICKER -> Box(
                modifier = Modifier.size(48.dp).background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PreviewIcon(R.drawable.ic_icon_palette, tint = colors.onPrimary)
            }
            GalleryComponent.PROGRESS -> LinearProgressIndicator(
                progress = { 0.6f },
                modifier = Modifier.width(92.dp),
                trackColor = colors.surface,
            )
            GalleryComponent.CONTENT_STATE -> IconBadge(
                R.drawable.ic_icon_info, tone = IconBadgeTone.NEUTRAL, size = 48.dp,
            )
            GalleryComponent.NUMBER -> Text(
                "09 → 10",
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            GalleryComponent.EXPANDABLE_TEXT -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PreviewLine(colors.onSurfaceVariant, Modifier.width(62.dp))
                PreviewLine(colors.onSurfaceVariant, Modifier.width(46.dp))
                PreviewIcon(R.drawable.ic_icon_arrow_down, tint = colors.onSurfaceVariant)
            }
            GalleryComponent.AVATAR -> PlaceholderAvatar(size = 48.dp)
            GalleryComponent.BADGE -> IconBadge(
                R.drawable.ic_icon_badge, tone = IconBadgeTone.SECONDARY, size = 48.dp,
            )
            GalleryComponent.CONNECTED_LIST -> ConnectedListPreview()
            GalleryComponent.CHIP -> PreviewButton(shape = CircleShape) {
                PreviewIcon(R.drawable.ic_icon_tag, modifier = Modifier.size(16.dp))
            }
            GalleryComponent.PAGER_TABS -> Column(
                modifier = Modifier.width(94.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewLine(colors.primary, Modifier.weight(1f))
                    PreviewLine(colors.onSurfaceVariant, Modifier.weight(1f))
                }
                PreviewLine(colors.primary, Modifier.width(36.dp), height = 3)
                PreviewLine(colors.surface, Modifier.fillMaxWidth(), height = 20)
            }
            GalleryComponent.DIALOG -> PreviewPanel(Modifier.width(94.dp)) {
                PreviewIcon(R.drawable.ic_icon_info, modifier = Modifier.align(Alignment.CenterHorizontally))
                PreviewLine(colors.onSurfaceVariant, Modifier.fillMaxWidth())
                PreviewLine(colors.primary, Modifier.align(Alignment.End).width(26.dp), height = 8)
            }
            GalleryComponent.MENU -> PreviewPanel(Modifier.width(82.dp)) {
                PreviewLine(colors.onSurfaceVariant, Modifier.width(45.dp))
                PreviewLine(colors.primary, Modifier.fillMaxWidth(), height = 10)
                PreviewLine(colors.onSurfaceVariant, Modifier.width(32.dp))
            }
            GalleryComponent.SHEET -> Surface(
                modifier = Modifier.align(Alignment.BottomCenter).width(104.dp).height(52.dp),
                color = colors.surface,
                shape = shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreviewLine(colors.outlineVariant, Modifier.width(24.dp))
                    PreviewLine(colors.primary, Modifier.width(54.dp))
                }
            }
            GalleryComponent.SNACKBAR -> Surface(
                modifier = Modifier.cardColorShadow(MaterialTheme.shapes.small, colors.inverseSurface),
                shape = MaterialTheme.shapes.small,
                color = colors.inverseSurface,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewLine(colors.inverseOnSurface, Modifier.width(36.dp))
                    PreviewIcon(R.drawable.ic_icon_close, tint = colors.inverseOnSurface)
                }
            }
        }
    }
}

@Composable
private fun ConnectedListPreview() {
    ConnectedListGroup(Modifier.width(96.dp)) {
        repeat(2) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth().height(23.dp),
                shape = connectedListItemShapes(index, 2).shape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewIcon(R.drawable.ic_icon_list, Modifier.size(12.dp))
                    PreviewLine(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Modifier.width(if (index == 0) 36.dp else 26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewField(
    @DrawableRes icon: Int,
    parentColor: Color,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(Modifier.width(96.dp).height(36.dp)) {
        FilledTextFieldContainer(
            enabled = true,
            isError = false,
            interactionSource = interactionSource,
            modifier = Modifier.matchParentSize(),
            colors = FilledTextFieldDefaults.colors(parentColor),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun PreviewPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = MaterialTheme.colorScheme.surface
    val shape = MaterialTheme.shapes.large
    Surface(modifier.cardColorShadow(shape, color), color = color, shape = shape) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun PreviewButton(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Surface(modifier.cardColorShadow(shape, color), shape = shape, color = color) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun PreviewIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(icon), contentDescription = null, modifier.size(18.dp), tint = tint)
}

@Composable
private fun PreviewLine(color: Color, modifier: Modifier, height: Int = 4) {
    Box(modifier.height(height.dp).background(color, CircleShape))
}
