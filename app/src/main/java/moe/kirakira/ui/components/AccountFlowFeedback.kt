package moe.kirakira.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Animates non-sensitive notices and errors, including spacing supplied inside [content]. */
@Composable
internal fun AccountFlowFeedback(
    message: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(String) -> Unit,
) {
    val feedback = rememberFeedbackTransition(message)
    if (!feedback.visibility.currentState && !feedback.visibility.targetState) return
    val motion = MaterialTheme.motionScheme
    feedback.visibility.AnimatedVisibility(
        visible = { it },
        modifier = modifier.then(if (message == null) Modifier.clearAndSetSemantics { } else Modifier),
        enter = fadeIn(motion.fastEffectsSpec()) + expandVertically(
            animationSpec = motion.fastSpatialSpec(),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(
            animationSpec = motion.fastSpatialSpec(),
            shrinkTowards = Alignment.Top,
        ),
    ) {
        feedback.message?.let { displayedMessage ->
            Column { content(displayedMessage) }
        }
    }
}

/** Returns no supporting slot once the exit finishes, preserving the field's idle height. */
@Composable
internal fun textFieldSupportingError(message: String?): (@Composable () -> Unit)? {
    val feedback = rememberFeedbackTransition(message)
    if (!feedback.visibility.currentState && !feedback.visibility.targetState) return null
    return {
        feedback.visibility.AnimatedVisibility(
            visible = { it },
            modifier = if (message == null) Modifier.clearAndSetSemantics { } else Modifier,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            feedback.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private data class FeedbackTransition(
    val visibility: Transition<Boolean>,
    val message: String?,
)

@Composable
private fun rememberFeedbackTransition(message: String?): FeedbackTransition {
    val visibility = updateTransition(targetState = message != null, label = "accountFlowFeedback")
    var retainedMessage by remember { mutableStateOf(message) }
    SideEffect {
        if (message != null) {
            retainedMessage = message
        } else if (!visibility.currentState && !visibility.targetState) {
            retainedMessage = null
        }
    }
    return FeedbackTransition(visibility, message ?: retainedMessage)
}
