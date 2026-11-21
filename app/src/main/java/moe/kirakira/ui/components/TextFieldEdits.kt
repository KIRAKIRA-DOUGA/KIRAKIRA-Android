package moe.kirakira.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets form actions send pending edits before asking the domain owner to validate or submit. */
internal class TextFieldEdits {
    private val fields = mutableMapOf<TextFieldBinding, () -> Unit>()

    val hasPendingChanges: Boolean get() = fields.keys.any { it.hasPendingChanges }

    fun register(binding: TextFieldBinding, publish: () -> Unit) { fields[binding] = publish }

    fun unregister(binding: TextFieldBinding) { fields.remove(binding) }

    fun flush() { fields.values.forEach { it() } }
}

internal val LocalTextFieldEdits = staticCompositionLocalOf<TextFieldEdits?> { null }

/** The editor owns editing mechanics; the model owns the string and deliberate replacements. */
@OptIn(ExperimentalFoundationApi::class)
internal class TextFieldBinding(initialText: String, val editor: TextFieldState = TextFieldState(initialText)) {
    private var modelText = initialText
    private var deliveredText = initialText
    val hasPendingChanges: Boolean get() = editor.text.toString() != deliveredText
    // StateFlow can conflate several edits; acknowledging an earlier echo must not move the caret.
    private val pending = ArrayDeque<String>()

    fun acceptModel(text: String) {
        if (modelText == text) return
        modelText = text
        val echo = pending.indexOfLast { it == text }
        if (echo >= 0) {
            repeat(echo + 1) { pending.removeFirst() }
        } else {
            replace(text)
        }
    }

    fun replace(text: String) {
        modelText = text
        deliveredText = text
        pending.clear()
        if (editor.text.toString() != text) editor.setTextAndPlaceCursorAtEnd(text)
        editor.undoState.clearHistory()
    }

    fun publish(onEdit: (String) -> Unit, normalize: (String) -> String = { it }) {
        val text = editor.text.toString()
        if (text == deliveredText) return
        deliveredText = text
        val projected = normalize(text)
        if (projected == (pending.lastOrNull() ?: modelText)) return
        pending.addLast(projected)
        onEdit(projected)
    }

    fun clearHistory() {
        pending.clear()
        editor.undoState.clearHistory()
    }
}

@Composable
internal fun rememberBoundTextField(
    text: String,
    onEdit: (String) -> Unit,
    enabled: Boolean = true,
): TextFieldState {
    val edits = LocalTextFieldEdits.current
    // Never use rememberTextFieldState here: some bound fields contain credentials or codes.
    val binding = remember(edits) { TextFieldBinding(text) }
    ObserveTextFieldBinding(binding, text, onEdit, enabled)
    return binding.editor
}

@Composable
internal fun ObserveTextFieldBinding(
    binding: TextFieldBinding,
    text: String,
    onEdit: (String) -> Unit,
    enabled: Boolean = true,
    normalize: (String) -> String = { it },
) {
    val edits = LocalTextFieldEdits.current
    val currentEdit by rememberUpdatedState(onEdit)
    val currentNormalize by rememberUpdatedState(normalize)
    val publish = remember(binding) { { binding.publish(currentEdit, currentNormalize) } }
    SideEffect { binding.acceptModel(text) }
    DisposableEffect(binding, edits, enabled) {
        if (enabled) edits?.register(binding, publish)
        onDispose { if (enabled) edits?.unregister(binding) }
    }
    LaunchedEffect(binding, enabled) {
        if (enabled) {
            snapshotFlow {
                binding.editor.text.toString().takeIf { binding.editor.composition == null }
            }.collect { if (it != null && binding.editor.composition == null) publish() }
        }
    }
    DisposableEffect(binding) { onDispose { binding.clearHistory() } }
}
