package moe.kirakira.feature.imageviewer

import android.util.Log
import androidx.lifecycle.Lifecycle
import moe.kirakira.BuildConfig

internal enum class ImageViewerCloseReason { CLOSE_BUTTON, SYSTEM_BACK }

/** Only generated instance IDs and application-owned states may be recorded here. */
internal object ImageViewerDiagnostics {
    fun composition(instanceId: String, attached: Boolean) = record(instanceId, "composed=$attached")

    fun stack(instanceId: String, present: Boolean) = record(instanceId, "in_stack=$present")

    fun lifecycle(instanceId: String, state: Lifecycle.State) = record(instanceId, "lifecycle=${state.name}")

    fun transition(instanceId: String, active: Boolean) = record(instanceId, "transition=$active")

    fun loading(instanceId: String, attempt: Int, phase: ViewerImagePhase) =
        record(instanceId, "attempt=$attempt phase=${phase.name}")

    fun fallback(instanceId: String, attempt: Int, reason: ViewerImageFallback) =
        record(instanceId, "attempt=$attempt fallback=${reason.name}")

    fun display(instanceId: String, attempt: Int, image: Boolean, placeholder: Boolean) =
        record(instanceId, "attempt=$attempt image=$image placeholder=$placeholder")

    fun close(instanceId: String, reason: ImageViewerCloseReason, accepted: Boolean) =
        record(instanceId, "close=${reason.name} accepted=$accepted")

    private fun record(instanceId: String, state: String) {
        if (BuildConfig.DEBUG) Log.d("ImageViewer", "viewer=$instanceId $state")
    }
}
