package moe.kirakira.feature.componenttest

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsScaffold

internal data class ComponentSelection(
    val toggled: Boolean = false,
    val following: Boolean = false,
    val switched: Boolean = false,
    val checked: Boolean = false,
    val radio: Int = 0,
    val progress: Float = 0.4f,
    val stepped: Float = 0.5f,
    val inputError: Boolean = false,
    val filter: Boolean = false,
    val chip: Boolean = true,
    val option: Int = 0,
) {
    companion object {
        val Saver = listSaver<ComponentSelection, Any>(
            save = {
                listOf(
                    it.toggled,
                    it.following,
                    it.switched,
                    it.checked,
                    it.radio,
                    it.progress,
                    it.stepped,
                    it.inputError,
                    it.filter,
                    it.chip,
                    it.option,
                )
            },
            restore = {
                ComponentSelection(
                    toggled = it[0] as Boolean,
                    following = it[1] as Boolean,
                    switched = it[2] as Boolean,
                    checked = it[3] as Boolean,
                    radio = it[4] as Int,
                    progress = it[5] as Float,
                    stepped = it[6] as Float,
                    inputError = it[7] as Boolean,
                    filter = it[8] as Boolean,
                    chip = it[9] as Boolean,
                    option = it[10] as Int,
                )
            },
        )
    }
}

internal data class ComponentInput(
    val text: String = "",
    val password: String = "",
    val code: String = "",
    val passwordVisible: Boolean = false,
)

internal enum class ComponentOverlay { PLAIN, ICON, DANGER, CHOICE, MENU, SHEET }

@Composable
internal fun ComponentTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var generation by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    // Recreate all sample state and cancel pending feedback jobs when resetting.
    key(generation) {
        ComponentTestContent(
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
private fun ComponentTestContent(onBack: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    var selection by rememberSaveable(stateSaver = ComponentSelection.Saver) { mutableStateOf(ComponentSelection()) }
    var input by remember { mutableStateOf(ComponentInput()) }
    var overlay by remember { mutableStateOf<ComponentOverlay?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) input = input.copy(passwordVisible = false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val feedback: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    SettingsScaffold(
        title = "组件测试",
        onBack = onBack,
        shadingIcon = R.drawable.ic_symbol_palette,
        modifier = modifier,
        imePadding = true,
        actions = { TextButton(onClick = onReset) { Text("重置") } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize().consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = SettingsDefaults.MaxContentWidth).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
            ) {
                item(key = "buttons") {
                    ComponentSection("按钮") {
                        ButtonSamples(selection, { selection = it }, feedback)
                    }
                }
                item(key = "selection") {
                    ComponentSection("选择控件") { SelectionSamples(selection, { selection = it }) }
                }
                item(key = "input") {
                    ComponentSection("输入框") {
                        InputSamples(
                            state = input,
                            onChange = { input = it },
                            error = selection.inputError,
                            onErrorChange = { selection = selection.copy(inputError = it) },
                        )
                    }
                }
                item(key = "progress") {
                    ComponentSection("进度") {
                        ProgressSamples(selection.progress, { selection = selection.copy(progress = it) })
                    }
                }
                item(key = "lists") {
                    ComponentSection("列表与标签") { ListSamples(selection, { selection = it }, feedback) }
                }
                item(key = "overlays") {
                    ComponentSection("弹层与反馈") {
                        OverlaySamples(
                            overlay = overlay,
                            option = selection.option,
                            onOpen = { overlay = it },
                            onChoose = {
                                selection = selection.copy(option = it)
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
    }
    ComponentTestOverlay(
        overlay = overlay,
        option = selection.option,
        onOptionChange = { selection = selection.copy(option = it) },
        onClose = { message ->
            overlay = null
            feedback(message)
        },
    )
}

@Composable
internal fun ComponentSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
internal fun SampleRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SampleIcon(modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(R.drawable.ic_symbol_add), description, modifier.size(24.dp))
}
