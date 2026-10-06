package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import moe.kirakira.R
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.ui.theme.KIRAKIRATheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** The caller supplies the Scaffold safe-drawing bottom inset, which already includes the IME. */
@Composable
internal fun FloatingComposerLayout(
    bottomPadding: Dp,
    composer: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: @Composable (Dp) -> Unit,
) {
    var composerHeight by remember { mutableIntStateOf(0) }
    val height = with(LocalDensity.current) { composerHeight.toDp() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - topPadding - bottomPadding - 24.dp).coerceAtLeast(0.dp)
        content(bottomPadding + height + 24.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 12.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .onSizeChanged { composerHeight = it.height },
        ) {
            composer(availableHeight)
        }
    }
}

/** Local editing state lives with the video, independently for comments and danmaku. */
internal class ComposerState {
    var editor by mutableStateOf(TextFieldValue())
    var category by mutableStateOf("happy")
    var panelOpen by mutableStateOf(false)
}

@Composable
internal fun rememberComposerState(videoId: Int, sessionRevision: Long, draft: String): ComposerState =
    rememberSaveable(
        videoId, sessionRevision,
        saver = listSaver(
            save = { listOf(it.category, it.editor.selection.start, it.editor.selection.end) },
            restore = { saved ->
                ComposerState().apply {
                    category = saved[0] as String
                    editor = TextFieldValue(
                        draft,
                        TextRange(
                            (saved[1] as Int).coerceIn(0, draft.length),
                            (saved[2] as Int).coerceIn(0, draft.length),
                        ),
                    )
                }
            },
        ),
    ) { ComposerState() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContentComposer(
    draft: String,
    @StringRes label: Int,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    maxLength: Int = 2000,
    maxLines: Int = 5,
    state: ComposerState = remember { ComposerState() },
    active: Boolean = true,
    availableHeight: Dp = 460.dp,
    recent: List<String> = emptyList(),
    onKaomojiInserted: (String) -> Unit = {},
    onLogin: (() -> Unit)? = null,
    trailingIcon: (@Composable (() -> Unit) -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (onLogin != null) {
        LaunchedEffect(state, active) {
            state.panelOpen = false
            if (active) {
                focus.clearFocus(force = true)
                keyboard?.hide()
            }
        }
        val buttonHeight = ButtonDefaults.MediumContainerHeight
        ShadowButton(
            onClick = onLogin,
            modifier = modifier.fillMaxWidth().heightIn(min = buttonHeight),
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
        ) {
            Text(
                stringResource(R.string.content_login_to_interact),
                style = ButtonDefaults.textStyleFor(buttonHeight),
            )
        }
        return
    }
    val containerColor = MaterialTheme.colorScheme.surface
    val shadowElevation = 4.dp
    val inputDescription = stringResource(label)
    val requester = remember { FocusRequester() }
    var tooLong by remember(state) { mutableStateOf(false) }
    // The ViewModel owns text; selection/composition stay local. External clears must win.
    val field = if (state.editor.text == draft) state.editor else TextFieldValue(draft, TextRange(draft.length))
    SideEffect { if (state.editor != field) state.editor = field }
    LaunchedEffect(draft) { if (draft.isEmpty()) tooLong = false }
    LaunchedEffect(active, enabled) {
        if (!active || !enabled) state.panelOpen = false
    }
    var inputHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val panelHeight = (availableHeight - with(density) { inputHeight.toDp() } - 8.dp).coerceIn(0.dp, 300.dp)
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val panelOpen = state.panelOpen && active && enabled
    if (panelOpen) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = { state.panelOpen = false },
        )
    }
    val compact = draft.isEmpty()
    val expansion by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        visibilityThreshold = 0.0001f,
        label = "Composer expansion",
    )
    val horizontalExpansion by animateFloatAsState(
        targetValue = if (compact) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        visibilityThreshold = 0.0001f,
        label = "Composer controls",
    )
    val inputShape = remember(expansion) { ComposerInputShape(expansion) }
    val actions: @Composable () -> Unit = {
        IconButton(
            onClick = {
                tooLong = false
                if (panelOpen) {
                    state.panelOpen = false
                    requester.requestFocus()
                    keyboard?.show()
                } else {
                    focus.clearFocus()
                    keyboard?.hide()
                    state.panelOpen = true
                }
            },
            enabled = enabled && !busy,
        ) {
            Icon(
                painterResource(if (panelOpen) R.drawable.ic_symbol_keyboard else R.drawable.ic_custom_kaomoji),
                stringResource(if (panelOpen) R.string.kaomoji_keyboard else R.string.kaomoji_title),
                modifier = Modifier.size(24.dp),
            )
        }
        if (trailingIcon != null) {
            trailingIcon { state.panelOpen = false }
        }
    }
    val send: @Composable () -> Unit = {
        ShadowFilledIconButton(
            onClick = onSend,
            enabled = enabled && !busy && draft.isNotBlank(),
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
        ) {
            Icon(painterResource(R.drawable.ic_symbol_send), stringResource(R.string.content_send))
        }
    }
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { inputHeight = it.height },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = inputShape,
                color = containerColor,
                shadowElevation = shadowElevation,
            ) {
                ComposerInputLayout(
                    expansion = expansion,
                    horizontalExpansion = horizontalExpansion,
                    actions = actions,
                ) {
                    TextField(
                        value = field,
                        onValueChange = {
                            if (!busy && enabled && it.text.length <= maxLength) {
                                state.editor = it
                                tooLong = false
                                onDraft(it.text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) state.panelOpen = false }
                            .semantics { contentDescription = inputDescription },
                        placeholder = { Text(inputDescription, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        enabled = enabled,
                        readOnly = busy,
                        minLines = 1,
                        maxLines = if (compact) 1 else if (panelOpen) minOf(2, maxLines) else maxLines,
                        shape = inputShape,
                        isError = tooLong,
                        supportingText = if (tooLong) {
                            { Text(stringResource(R.string.kaomoji_too_long, maxLength)) }
                        } else null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = containerColor,
                            unfocusedContainerColor = containerColor,
                            disabledContainerColor = containerColor,
                            errorContainerColor = containerColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
            send()
        }
        if (panelOpen && !keyboardVisible && panelHeight >= 96.dp) {
            KaomojiPicker(
                category = state.category,
                recent = recent,
                onCategory = { state.category = it },
                onSelect = { text ->
                    if (!busy && enabled) {
                        val start = field.selection.min
                        val end = field.selection.max
                        val next = field.text.replaceRange(start, end, text)
                        if (next.length <= maxLength) {
                            state.editor = TextFieldValue(next, TextRange(start + text.length))
                            tooLong = false
                            onDraft(next)
                            onKaomojiInserted(text)
                        } else tooLong = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight),
                enabled = !busy,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposerInputLayout(
    expansion: Float,
    horizontalExpansion: Float,
    actions: @Composable () -> Unit,
    editor: @Composable () -> Unit,
) {
    // Keep the bottom-aligned controls steady while the surface expands above them.
    val horizontalProgress = horizontalExpansion * abs(horizontalExpansion)
    Layout(
        content = {
            Box(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) { editor() }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val compactInset = 4.dp.roundToPx()
        val expandedInset = 8.dp.roundToPx()
        val controlConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val actionButtons = measurables[1].measure(
            controlConstraints.copy(maxWidth = (width - expandedInset * 2).coerceAtLeast(0)),
        )
        val reservedWidth = actionButtons.width + compactInset
        val editorWidth = (width - reservedWidth * (1f - horizontalProgress.coerceIn(0f, 1f)))
            .roundToInt().coerceIn(0, width)
        val input = measurables[0].measure(
            constraints.copy(minWidth = editorWidth, maxWidth = editorWidth, minHeight = 0),
        )
        val controlsHeight = actionButtons.height
        val inputHeight = maxOf(input.height, controlsHeight)
        val height = constraints.constrainHeight(
            (inputHeight + ((controlsHeight + expandedInset) * expansion).roundToInt())
                .coerceAtLeast(maxOf(inputHeight, controlsHeight + compactInset * 2)),
        )
        val compactActionsX = width - reservedWidth
        val actionOffset = compactActionsX + (expandedInset - compactActionsX) * horizontalProgress
        // Resist overshoot smoothly inside the edge instead of hard-clamping the spring.
        val actionsX = if (actionOffset < expandedInset) {
            expandedInset / (1f + (expandedInset - actionOffset) / expandedInset)
        } else actionOffset
        layout(width, height) {
            input.placeRelative(0, 0)
            actionButtons.placeRelativeWithLayer(0, height - compactInset - actionButtons.height) {
                translationX = if (layoutDirection == LayoutDirection.Ltr) actionsX else -actionsX
            }
        }
    }
}

private class ComposerInputShape(private val expansion: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val compactRadius = minOf(size.width, size.height) / 2f
        val expandedRadius = with(density) { 28.dp.toPx() }.coerceAtMost(compactRadius)
        val radius = (compactRadius + (expandedRadius - compactRadius) * expansion).coerceIn(0f, compactRadius)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}

@Preview(name = "Composer · Light", showBackground = true, widthDp = 360)
@Preview(name = "Composer · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Preview(name = "Composer · Large text", fontScale = 2f, widthDp = 320)
@Composable
private fun ContentComposerPreview() {
    KIRAKIRATheme {
        ContentComposer("", R.string.comment_write, {}, {}, modifier = Modifier.padding(16.dp))
    }
}
