/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package moe.kirakira.ui.navigation

import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.NavigationEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val BACK_PREVIEW_SCALE = 0.9f

/**
 * Compose port of DefaultCrossActivityBackAnimation and CrossActivityBackAnimation.
 * Gesture geometry, curves, phase durations and cancellation follow the pinned AOSP revision.
 * Commit restores page bounds without AOSP's additional velocity-driven scale spring.
 * Unlike a seekable tween, finishing the gesture starts a separate post-commit animation.
 */
internal class PredictiveBackMotion(
    private val scope: CoroutineScope,
    private val emphasized: Easing,
) {
    private var phase: BackPhase by mutableStateOf(BackPhase.Idle)
    val closingKey: Any?
        get() = when (val current = phase) {
            is BackPhase.Active -> current.pages.closing
            BackPhase.Idle, is BackPhase.Finished -> null
        }
    val completedPop: Pair<Any, Any>?
        get() = when (val current = phase) {
            is BackPhase.AwaitingRemoval -> current.pages.closing to current.pages.entering
            is BackPhase.Finished -> current.pages.closing to current.pages.entering
            BackPhase.Idle, is BackPhase.Gesture, is BackPhase.Committing, is BackPhase.Cancelling -> null
        }
    var frame by mutableStateOf(PredictiveBackFrame())
        private set
    private var animation: Job? = null
    private var bounds = Rect.Zero
    private var offset = 0f
    private var margin = 0f
    private var radius = 0f
    private var maxScrimAlpha = 0f
    private var startY = 0f
    private var touchY = 0f
    private var swipeEdge = NavigationEvent.EDGE_NONE
    private var rawProgress = 0f
    private var velocity = 0f
    private var lastEventTime = 0L
    private val gestureInterpolator = PathInterpolator(0.1f, 0.1f, 0f, 1f)
    // AOSP's post-commit ValueAnimator retains its default time interpolator.
    private val animatorInterpolator = AccelerateDecelerateInterpolator()

    fun start(
        closing: Any,
        entering: Any,
        event: NavigationEvent,
        size: IntSize,
        density: Float,
        cornerRadius: Float,
        darkTheme: Boolean,
    ) {
        if (phase is BackPhase.Committing || phase is BackPhase.AwaitingRemoval ||
            size.width == 0 || size.height == 0
        ) return
        animation?.cancel()
        animation = null
        bounds = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        offset = ACTIVITY_OFFSET_DP * density
        margin = 8f * density
        radius = cornerRadius
        maxScrimAlpha = if (darkTheme) 0.8f else 0.2f
        startY = event.touchY
        touchY = event.touchY
        swipeEdge = event.swipeEdge
        rawProgress = 0f
        velocity = 0f
        lastEventTime = eventTime(event)
        phase = BackPhase.Gesture(BackPages(closing, entering))
        updateGesture(0f)
    }

    fun progress(event: NavigationEvent) {
        if (phase !is BackPhase.Gesture) return
        val time = eventTime(event)
        val deltaTime = time - lastEventTime
        val progress = event.progress.coerceIn(0f, 1f)
        // Android exposes progress samples, but not BackProgressAnimator.getVelocity().
        if (deltaTime > 0) velocity = (progress - rawProgress) * 1000f / deltaTime
        lastEventTime = time
        touchY = event.touchY
        updateGesture(progress)
    }

    fun complete(onPop: () -> Unit) {
        val pages = when (val current = phase) {
            is BackPhase.Gesture -> current.pages
            BackPhase.Idle, is BackPhase.Finished -> {
                reset()
                onPop()
                return
            }
            is BackPhase.Committing, is BackPhase.Cancelling, is BackPhase.AwaitingRemoval -> return
        }
        phase = BackPhase.Committing(pages)
        val startClosing = frame.closing
        val startEntering = frame.entering
        val targetClosing = bounds.translate(Offset(startClosing.left + offset, 0f))
        animation = scope.launch {
            // Public progress samples can imply a large velocity during a quick swipe.
            // Restore directly from the preview bounds so commit never shrinks either page further.
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ACTIVITY_TRANSITION_MILLIS,
                    easing = Easing { animatorInterpolator.getInterpolation(it) },
                ),
            ) { fraction, _ ->
                val progress = emphasized.transform(fraction)
                frame = PredictiveBackFrame(
                    closing = lerp(startClosing, targetClosing, progress),
                    entering = lerp(startEntering, bounds, progress),
                    closingAlpha = max(1f - fraction * 5f, 0f),
                    scrimAlpha = maxScrimAlpha * (1f - fraction),
                    cornerRadius = radius,
                    width = bounds.width,
                )
            }
            // Hold the final frame until NavDisplay disposes the outgoing Scene. Clearing here
            // would make that still-composed page fully visible again before the scene swap.
            frame = frame.copy(
                closing = targetClosing,
                entering = bounds,
                closingAlpha = 0f,
                scrimAlpha = 0f,
                cornerRadius = 0f,
            )
            phase = BackPhase.AwaitingRemoval(pages)
            onPop()
        }
    }

    fun cancel() {
        val current = phase
        if (current !is BackPhase.Gesture) return
        phase = BackPhase.Cancelling(current.pages)
        animation = scope.launch {
            // Platform cancellation normally already sends progress back to zero. This also
            // handles other NavigationEvent inputs that deliver cancellation immediately.
            if (rawProgress > 0f) {
                animate(
                    initialValue = rawProgress,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.0075f,
                    ),
                ) { value, _ -> updateGesture(value.coerceIn(0f, 1f)) }
            }
            animation = null
            phase = BackPhase.Idle
            frame = PredictiveBackFrame()
        }
    }

    fun reset() {
        animation?.cancel()
        animation = null
        phase = BackPhase.Idle
        frame = PredictiveBackFrame()
    }

    fun onTopChanged(topKey: Any) {
        // The stack updates before AnimatedContent has removed the old Scene. Keep its
        // transparent closing page and full-size entering page through that handoff.
        when (val current = phase) {
            BackPhase.Idle -> Unit
            is BackPhase.AwaitingRemoval -> {
                if (topKey != current.pages.closing && topKey != current.pages.entering) reset()
            }
            is BackPhase.Gesture, is BackPhase.Cancelling, is BackPhase.Committing -> {
                if (topKey != current.pages.closing) reset()
            }
            is BackPhase.Finished -> {
                if (topKey != current.pages.entering) reset()
            }
        }
    }

    fun onSceneDisposed(sceneKey: Any) {
        val current = phase
        if (current is BackPhase.AwaitingRemoval && current.pages.closing == sceneKey) {
            // Keep completedPop until the next navigation so a late transitionSpec evaluation
            // still skips the ordinary pop animation.
            animation = null
            phase = BackPhase.Finished(current.pages)
            frame = PredictiveBackFrame()
        }
    }

    private fun updateGesture(progress: Float) {
        rawProgress = progress
        val gestureProgress = gestureInterpolator.getInterpolation(progress)
        var targetClosing = backPreviewBounds(bounds)
        if (swipeEdge != NavigationEvent.EDGE_RIGHT) {
            targetClosing = targetClosing.translate(Offset(bounds.right - targetClosing.right - margin, 0f))
        }
        var closing = lerp(bounds, targetClosing, gestureProgress)
        val deltaY = touchY - startY
        val yRatio = min(bounds.height / 2f, abs(deltaY)) / (bounds.height / 2f)
        val deceleratedY = 1f - (1f - yRatio) * (1f - yRatio)
        val yShift = max(0f, (bounds.height - closing.height) / 2f - margin) * deceleratedY * sign(deltaY)
        closing = closing.translate(Offset(0f, yShift))
        val startEntering = bounds.translate(Offset(-offset, 0f))
        val entering = lerp(startEntering, backPreviewBounds(startEntering), gestureProgress)
            .translate(Offset(0f, yShift))
        frame = PredictiveBackFrame(
            closing = closing,
            entering = entering,
            scrimAlpha = maxScrimAlpha,
            cornerRadius = radius,
            width = bounds.width,
        )
    }

    private fun eventTime(event: NavigationEvent): Long =
        event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
}

private data class BackPages(val closing: Any, val entering: Any)

/** Only active phases retain a preview; finished phases retain solely the pop transition identity. */
private sealed interface BackPhase {
    data object Idle : BackPhase

    sealed interface Active : BackPhase {
        val pages: BackPages
    }

    data class Gesture(override val pages: BackPages) : Active
    data class Cancelling(override val pages: BackPages) : Active
    data class Committing(override val pages: BackPages) : Active
    data class AwaitingRemoval(override val pages: BackPages) : Active
    data class Finished(val pages: BackPages) : BackPhase
}

internal data class PredictiveBackFrame(
    val closing: Rect = Rect.Zero,
    val entering: Rect = Rect.Zero,
    val closingAlpha: Float = 1f,
    val scrimAlpha: Float = 0f,
    val cornerRadius: Float = 0f,
    val width: Float = 0f,
) {
    fun pageTransform(enteringPage: Boolean): NavigationPageTransform {
        val rect = if (enteringPage) entering else closing
        return NavigationPageTransform(
            scale = if (width > 0f) rect.width / width else 1f,
            x = rect.left,
            y = rect.top,
            alpha = if (enteringPage) 1f else closingAlpha,
            cornerRadius = cornerRadius,
        )
    }
}

private fun backPreviewBounds(bounds: Rect): Rect {
    val halfWidth = bounds.width * BACK_PREVIEW_SCALE / 2f
    val halfHeight = bounds.height * BACK_PREVIEW_SCALE / 2f
    val center = bounds.center
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}
