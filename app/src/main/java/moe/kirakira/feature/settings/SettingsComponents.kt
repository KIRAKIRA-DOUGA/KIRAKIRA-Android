@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.SectionHeader
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.ThemeColorDefaults
import kotlin.math.roundToInt
import androidx.compose.material.ButtonDefaults as Material2ButtonDefaults

/** Shared spacing for settings subpages so every page lines up with the settings list. */
internal object SettingsDefaults {
    val MaxContentWidth = 640.dp
    val HorizontalPadding = 16.dp
    val TopPadding = 16.dp
    val BottomPadding = 16.dp
    val SectionSpacing = 24.dp

    /** Extra bottom room that keeps the last item clear of [SettingsSaveToolbar]. */
    val FloatingToolbarClearance = 96.dp

    /** Extra bottom room that keeps the last item clear of a FAB. */
    val FabClearance = 88.dp
    val PrimaryButtonHeight = 56.dp
}

/**
 * Scaffold for settings subpages: collapsible large title, settings background and snackbar host.
 * [content] receives the Scaffold padding; pair it with [SettingsColumn] for the standard layout.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    @DrawableRes shadingIcon: Int,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    imePadding: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .then(if (imePadding) Modifier.imePadding() else Modifier)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = ThemeColorDefaults.settingsBackgroundColor(),
        topBar = {
            Box {
                ShadingIcon(
                    icon = shadingIcon,
                    modifier = Modifier.matchParentSize(),
                    alignment = Alignment.BottomEnd,
                    endPadding = 0.dp,
                    offset = DpOffset(32.dp, 32.dp),
                )
                CollapsibleTopAppBar(
                    title = title,
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                    backButtonModifier = backButtonModifier,
                    actions = actions,
                )
            }
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Centered, width-capped scrolling column. The bottom inset scrolls with the content so the last
 * item can reach above the navigation bar (edge-to-edge).
 */
@Composable
internal fun SettingsColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    bottomClearance: Dp = 0.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsDefaults.MaxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding + bottomClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
            content = content,
        )
        overlay()
    }
}

/** Standard settings page: [SettingsScaffold] with a [SettingsColumn] body. */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    @DrawableRes shadingIcon: Int,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsScaffold(
        title = title,
        onBack = onBack,
        shadingIcon = shadingIcon,
        modifier = modifier,
        backButtonModifier = backButtonModifier,
    ) { padding ->
        SettingsColumn(padding, content = content)
    }
}

/** Section header style shared by settings pages and LazyColumn-based management pages. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    SectionHeader(title = title, modifier = modifier.padding(horizontal = SettingsDefaults.HorizontalPadding))
}

/** Titled group of connected rows. */
@Composable
internal fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) SettingsSectionHeader(title)
        ConnectedListGroup(
            modifier = contentModifier,
            content = content,
        )
    }
}

/** Common segmented row with a standard leading icon. Pass [onClick] = null for a static row. */
@Composable
internal fun SettingsItem(
    title: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shapes = connectedListItemShapes(index = index, count = count)
    val leading: (@Composable () -> Unit)? = icon?.let {
        { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(24.dp)) }
    }
    val support: (@Composable () -> Unit)? = supporting?.let { { Text(it) } }
    if (onClick == null) {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = support,
            content = { Text(title) },
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            interactionSource = interactionSource,
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = support,
            content = { Text(title) },
        )
    }
}

/** Navigation row with a trailing chevron. */
@Composable
internal fun SettingsNavigationItem(
    title: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        title = title, index = index, count = count, modifier = modifier, icon = icon,
        supporting = supporting, enabled = enabled, onClick = onClick,
        trailing = {
            Icon(
                painterResource(R.drawable.ic_symbol_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

/**
 * Ordinary settings switch: only the Switch reflects the state, the row keeps its shape and color.
 * Master switches (such as "Show danmaku") use [SettingsMasterSwitchItem] instead.
 */
@Composable
internal fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        title = title, index = index, count = count, icon = icon, supporting = supporting, enabled = enabled,
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** Feature master switch that keeps the emphasized checked row of [SegmentedListItem]. */
@Composable
internal fun SettingsMasterSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        shapes = connectedListItemShapes(index = 0, count = 1),
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Switch },
        leadingContent = icon?.let {
            {
                Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
    )
}

/** Single-choice row inside a `selectableGroup()`. */
@Composable
internal fun SettingsRadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    SettingsItem(
        title = title, index = index, count = count, icon = icon, supporting = supporting, enabled = enabled,
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        onClick = onClick,
        interactionSource = interactionSource,
        trailing = {
            ShadowRadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                interactionSource = interactionSource,
            )
        },
    )
}

/** Stepped slider row; the value label is shown in primary next to the title. */
@Composable
internal fun SettingsSliderItem(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val slider = rememberSliderState(
        steps = (range.last - range.first) / step - 1,
        trackRange = range.first.toFloat()..range.last.toFloat(),
    )
    SideEffect { slider.value = value.toFloat() }
    SegmentedListItem(
        shapes = connectedListItemShapes(index, count),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        leadingContent = icon?.let {
            { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(24.dp)) }
        },
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, modifier = Modifier.weight(1f))
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        supportingContent = {
            Slider(
                state = slider,
                onValueChange = { onValueChange((it / step).roundToInt() * step) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = title
                        stateDescription = valueLabel
                    },
            )
        },
    )
}

/** Inline error surface used by settings forms. */
@Composable
internal fun SettingsErrorCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_symbol_error), contentDescription = null)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Surface that groups form fields so they read as one card next to segmented rows. */
@Composable
internal fun SettingsFormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Icon + label content sized for a [SettingsDefaults.PrimaryButtonHeight] button. */
@Composable
private fun PrimaryButtonContent(@DrawableRes icon: Int?, label: String, busy: Boolean) {
    val height = SettingsDefaults.PrimaryButtonHeight
    val iconSize = ButtonDefaults.iconSizeFor(height)
    if (busy) IndeterminateCircularProgressIndicator(
        modifier = Modifier.size(Material2ButtonDefaults.IconSize),
        strokeWidth = 2.dp,
    )
    else if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
    if (busy || icon != null) Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    Text(label, style = MaterialTheme.typography.titleMedium)
}

/** Full-width 56dp primary action used by settings forms, sheets and the avatar cropper. */
@Composable
internal fun SettingsPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val height = SettingsDefaults.PrimaryButtonHeight
    ShadowButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
        shapes = ButtonDefaults.shapesFor(height),
        colors = colors,
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = icon != null || busy),
    ) {
        PrimaryButtonContent(icon, label, busy)
    }
}

/** Destructive variant colors for [SettingsPrimaryButton]. */
@Composable
internal fun settingsDestructiveButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.error,
    contentColor = MaterialTheme.colorScheme.onError,
)

/**
 * Floating save toolbar for pages with a draft: optional reset action plus the primary save button.
 * Place it bottom-center above the navigation bar and give the content
 * [SettingsDefaults.FloatingToolbarClearance] of bottom room.
 */
@Composable
internal fun SettingsSaveToolbar(
    label: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_symbol_check,
    resetLabel: String? = null,
    resetBusy: Boolean = false,
    resetEnabled: Boolean = true,
    onReset: (() -> Unit)? = null,
) {
    val height = SettingsDefaults.PrimaryButtonHeight
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        if (onReset != null) {
            IconButton(onClick = onReset, enabled = resetEnabled) {
                if (resetBusy) IndeterminateCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_symbol_history), resetLabel)
            }
        }
        ShadowButton(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.heightIn(min = height),
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
        ) {
            PrimaryButtonContent(icon, label, busy)
        }
    }
}
