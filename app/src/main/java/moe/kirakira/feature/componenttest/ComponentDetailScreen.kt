package moe.kirakira.feature.componenttest

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.StatusBarScrollToTopEffect
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun ComponentDetailScreen(
    component: GalleryComponent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var generation by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    // Recreate the sample and its coroutine scope, including pending feedback, on reset.
    key(generation) {
        ComponentDetailPage(
            component = component,
            onBack = onBack,
            onReset = {
                focus.clearFocus()
                generation++
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun ComponentDetailPage(
    component: GalleryComponent,
    onBack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlay by remember { mutableStateOf<ComponentOverlay?>(null) }
    var option by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val feedback: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    SettingsScaffold(
        title = stringResource(component.title),
        onBack = onBack,
        shadingIcon = R.drawable.ic_icon_palette,
        modifier = modifier,
        imePadding = component == GalleryComponent.TEXT_FIELD ||
            component == GalleryComponent.PASSWORD || component == GalleryComponent.OTP ||
            component == GalleryComponent.COLOR_PICKER,
        actions = { FlatTextButton(onClick = onReset) { Text(stringResource(R.string.component_gallery_reset)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StatusBarScrollToTopEffect(listState, enabled = overlay == null)
        val direction = LocalLayoutDirection.current
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = SettingsDefaults.MaxContentWidth).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                ),
            ) {
                item(key = component.name) {
                    ComponentExample(
                        component = component,
                        overlay = overlay,
                        option = option,
                        onOpenOverlay = { overlay = it },
                        onChooseMenu = {
                            option = it
                            overlay = null
                            feedback("已选择选项 ${it + 1}")
                        },
                        onFeedback = feedback,
                        onSnackbarAction = {
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(
                                    "操作已完成",
                                    actionLabel = "撤销",
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) feedback("已撤销")
                            }
                        },
                    )
                }
            }
        }
    }
    ComponentTestOverlay(
        overlay = overlay,
        option = option,
        onOptionChange = { option = it },
        onClose = { message ->
            overlay = null
            feedback(message)
        },
    )
}

@Composable
private fun ComponentExample(
    component: GalleryComponent,
    overlay: ComponentOverlay?,
    option: Int,
    onOpenOverlay: (ComponentOverlay?) -> Unit,
    onChooseMenu: (Int) -> Unit,
    onFeedback: (String) -> Unit,
    onSnackbarAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageBackgroundColor = ThemeColorDefaults.settingsBackgroundColor()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
    ) {
        when (component) {
            GalleryComponent.TEXT_BUTTON -> TextButtonSamples(onFeedback)
            GalleryComponent.ICON_BUTTON -> IconButtonSamples(onFeedback)
            GalleryComponent.FAB -> FabSample(onFeedback)
            GalleryComponent.TOGGLE_BUTTON -> ToggleButtonSamples()
            GalleryComponent.FOLLOW_BUTTON -> FollowButtonSamples()
            GalleryComponent.SEGMENTED -> SegmentedSample(pageBackgroundColor)
            GalleryComponent.SWITCH -> SwitchSamples()
            GalleryComponent.CHECKBOX -> CheckboxSamples()
            GalleryComponent.RADIO -> RadioSamples()
            GalleryComponent.SLIDER -> SliderSamples()
            GalleryComponent.TEXT_FIELD, GalleryComponent.PASSWORD, GalleryComponent.OTP ->
                InputSamples(component, pageBackgroundColor)
            GalleryComponent.COLOR_PICKER -> ColorPickerSample(pageBackgroundColor)
            GalleryComponent.PROGRESS -> ProgressSamples()
            GalleryComponent.CONTENT_STATE -> ContentStateSamples(onFeedback)
            GalleryComponent.NUMBER -> NumberSamples()
            GalleryComponent.EXPANDABLE_TEXT -> ExpandableTextSamples()
            GalleryComponent.AVATAR -> AvatarSamples()
            GalleryComponent.BADGE -> BadgeSamples()
            GalleryComponent.CONNECTED_LIST -> ConnectedListSamples()
            GalleryComponent.CHIP -> ChipSamples(pageBackgroundColor, onFeedback)
            GalleryComponent.PAGER_TABS -> PagerTabsSample()
            GalleryComponent.DIALOG -> DialogSamples(option, onOpenOverlay)
            GalleryComponent.MENU -> MenuSample(
                expanded = overlay == ComponentOverlay.MENU,
                option = option,
                onExpandedChange = { onOpenOverlay(if (it) ComponentOverlay.MENU else null) },
                onChoose = onChooseMenu,
                onFeedback = onFeedback,
            )
            GalleryComponent.SHEET -> SheetSample { onOpenOverlay(ComponentOverlay.SHEET) }
            GalleryComponent.SNACKBAR -> SnackbarSamples(onFeedback, onSnackbarAction)
        }
    }
}

@Composable
internal fun SampleSection(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(title),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun SampleLabeledItem(
    label: String,
    modifier: Modifier = Modifier,
    minPreviewHeight: Dp = Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.heightIn(min = minPreviewHeight), contentAlignment = Alignment.Center) { content() }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SampleRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun SampleIcon(modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(R.drawable.ic_icon_add), description, modifier.size(24.dp))
}
