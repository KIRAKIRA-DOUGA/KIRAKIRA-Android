package moe.kirakira.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

internal enum class OtpInputType { NUMERIC, ALPHANUMERIC }

/** Six visual cells share one editor, preserving paste, deletion, selection, and IME behavior. */
@Composable
internal fun OtpInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    inputType: OtpInputType = OtpInputType.NUMERIC,
    onRecoveryCodePaste: ((String) -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    // Selection and focus are local; the flow owns the code and never saves it to disk.
    var selection by remember { mutableStateOf(TextRange(value.length)) }
    var isFocused by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val idleInteraction = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = MaterialTheme.motionScheme
    val editorSelection = TextRange(
        selection.start.coerceIn(0, value.length),
        selection.end.coerceIn(0, value.length),
    )
    val activeCell = editorSelection.min.coerceAtMost(5)
    val cursorColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val cursorAlpha = if (enabled && isFocused) {
        key(value, editorSelection) {
            rememberInfiniteTransition(label = "otpCursor").animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        1f at 0
                        1f at 499
                        0f at 500
                        0f at 1000
                    },
                ),
                label = "otpCursorAlpha",
            )
        }
    } else {
        null
    }
    val codeStyle = accountFlowCodeTextStyle().copy(
        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) TextFieldErrorIcon()
        }
        BasicTextField(
            value = TextFieldValue(value, editorSelection),
            onValueChange = { edited ->
                val code = edited.text.uppercase().filter {
                    it in '0'..'9' || (inputType == OtpInputType.ALPHANUMERIC && it in 'A'..'Z')
                }
                if (code.length == 24 && onRecoveryCodePaste != null) {
                    onRecoveryCodePaste(code)
                } else {
                    val next = code.take(6)
                    selection = TextRange(
                        edited.selection.start.coerceIn(0, next.length),
                        edited.selection.end.coerceIn(0, next.length),
                    )
                    onValueChange(next)
                }
            },
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = codeStyle.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = if (inputType == OtpInputType.ALPHANUMERIC) {
                    KeyboardCapitalization.Characters
                } else {
                    KeyboardCapitalization.None
                },
                autoCorrectEnabled = false,
                keyboardType = if (inputType == OtpInputType.ALPHANUMERIC) KeyboardType.Ascii else KeyboardType.Number,
                imeAction = if (onNext != null) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onNext = { if (enabled) onNext?.invoke() },
                onDone = { if (enabled) onDone?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(editorFocus)
                .onFocusChanged { isFocused = it.isFocused }
                .semantics {
                    contentDescription = label
                    if (error != null) this.error(error)
                },
            decorationBox = { editor ->
                Box {
                    // Retain the real editor's full bounds and semantics under the decorative cells.
                    Box(Modifier
                        .matchParentSize()
                        .alpha(0f)) { editor() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(6) { index ->
                            val character = value.getOrNull(index)?.toString().orEmpty()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = TextFieldDefaults.MinHeight)
                                    .then(
                                        if (enabled) Modifier.pointerInput(index, value.length) {
                                            detectTapGestures {
                                                val start = index.coerceAtMost(value.length)
                                                selection = TextRange(start, (start + 1).coerceAtMost(value.length))
                                                editorFocus.requestFocus()
                                                keyboard?.show()
                                            }
                                        } else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                FilledTextFieldContainer(
                                    indicatorThickness = 0.dp,
                                    colors = FilledTextFieldDefaults.colors(),
                                    enabled = enabled,
                                    isError = error != null,
                                    interactionSource = if (index == activeCell) interaction else idleInteraction,
                                    modifier = Modifier.matchParentSize(),
                                    shape = MaterialTheme.shapes.large,
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .drawWithContent {
                                            drawContent()
                                            if (index == activeCell && cursorAlpha != null) {
                                                val cursorWidth = 2.dp.toPx()
                                                val cursorHeight = codeStyle.fontSize.toPx()
                                                val cursorX = when {
                                                    character.isEmpty() -> center.x
                                                    editorSelection.start > index -> size.width + 3.dp.toPx()
                                                    else -> -3.dp.toPx()
                                                }
                                                drawRoundRect(
                                                    color = cursorColor.copy(alpha = cursorAlpha.value),
                                                    topLeft = Offset(
                                                        cursorX - cursorWidth / 2,
                                                        center.y - cursorHeight / 2,
                                                    ),
                                                    size = Size(cursorWidth, cursorHeight),
                                                    cornerRadius = CornerRadius(cursorWidth / 2),
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Keep the empty cell's height stable while removing old characters immediately.
                                    Text(
                                        text = "",
                                        style = codeStyle,
                                        softWrap = false,
                                    )
                                    if (character.isNotEmpty()) {
                                        key(character) {
                                            val visibility = remember {
                                                MutableTransitionState(false).apply { targetState = true }
                                            }
                                            androidx.compose.animation.AnimatedVisibility(
                                                visibleState = visibility,
                                                enter = slideInVertically(
                                                    animationSpec = motion.fastSpatialSpec(),
                                                    initialOffsetY = { it },
                                                ) + fadeIn(motion.fastEffectsSpec()),
                                                label = "otpCharacter",
                                            ) {
                                                Text(
                                                    text = character,
                                                    style = codeStyle,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = if (enabled) 1f else 0.38f,
                                                    ),
                                                    softWrap = false,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
        AccountFlowFeedback(error) {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
