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
import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Compose port of DefaultCrossActivityBackAnimation and CrossActivityBackAnimation.
 * Geometry, curves, phase durations and spring parameters follow the pinned AOSP revision.
 * Unlike a seekable tween, finishing the gesture starts a separate post-commit animation.
 */
internal class PredictiveBackMotion(
    private val scope: CoroutineScope,
    private val emphasized: Easing,
) {
    var closingKey: Any? by mutableStateOf(null)
        private set
    var completedPop: Pair<Any, Any>? = null
        private set
    var frame by mutableStateOf(PredictiveBackFrame())
        private set
    private var enteringKey: Any? = null
    private var animation: Job? = null
    private var committing = false
    private var cancelling = false
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
    private var gestureProgress = 0f
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
        if (committing || size.width == 0 || size.height == 0) return
        animation?.cancel()
        cancelling = false
        completedPop = null
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
        enteringKey = entering
        closingKey = closing
        updateGesture(0f)
    }

    fun progress(event: NavigationEvent) {
        if (closingKey == null || committing || cancelling) return
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
        if (committing || cancelling) return
        val closing = closingKey
        val entering = enteringKey
        if (closing == null || entering == null) {
            completedPop = null
            onPop()
            return
        }
        committing = true
        val startClosing = frame.closing
        val startEntering = frame.entering
        val targetClosing = bounds.translate(Offset(startClosing.left + offset, 0f))
        val startVelocity = if (gestureProgress < 0.1f) -1.2f else (-velocity).coerceIn(-10f, 0f)
        animation = scope.launch {
            coroutineScope {
                val fling = Animatable(1f)
                val flingJob = launch {
                    fling.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = 0.0075f,
                        ),
                        initialVelocity = startVelocity,
                    )
                }
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween<Float>(
                        durationMillis = ACTIVITY_TRANSITION_MILLIS,
                        easing = Easing { animatorInterpolator.getInterpolation(it) },
                    ),
                ) { fraction, _ ->
                    val progress = emphasized.transform(fraction)
                    val flingScale = min(fling.value, 1f)
                    frame = PredictiveBackFrame(
                        closing = lerp(startClosing, targetClosing, progress).scaleCentered(flingScale),
                        entering = lerp(startEntering, bounds, progress).scaleCentered(flingScale),
                        closingAlpha = max(1f - fraction * 5f, 0f),
                        scrimAlpha = maxScrimAlpha * (1f - fraction),
                        cornerRadius = radius,
                        width = bounds.width,
                    )
                }
                flingJob.cancel()
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
            completedPop = closing to entering
            onPop()
        }
    }

    fun cancel() {
        if (closingKey == null || committing || cancelling) return
        cancelling = true
        animation = scope.launch {
            // Platform cancellation normally already sends progress back to zero. This also
            // handles other NavigationEvent inputs that deliver cancellation immediately.
            if (rawProgress > 0f) {
                animate(
                    initialValue = rawProgress,
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = 0.0075f,
                    ),
                ) { value, _ -> updateGesture(value.coerceIn(0f, 1f)) }
            }
            clear()
        }
    }

    fun reset() {
        animation?.cancel()
        completedPop = null
        clear()
    }

    fun onTopChanged(topKey: Any) {
        // The stack updates before AnimatedContent has removed the old Scene. Keep its
        // transparent closing page and full-size entering page through that handoff.
        val completed = completedPop
        if (completed?.first == closingKey &&
            (topKey == completed?.first || topKey == completed?.second)
        ) {
            return
        }
        if (closingKey != null && closingKey != topKey) reset()
        if (completedPop?.second != topKey) completedPop = null
    }

    fun onSceneDisposed(sceneKey: Any) {
        if (closingKey == sceneKey && completedPop?.first == sceneKey) {
            // Keep completedPop until the next navigation so a late transitionSpec evaluation
            // still skips the ordinary pop animation.
            clear()
        }
    }

    private fun clear() {
        closingKey = null
        enteringKey = null
        committing = false
        cancelling = false
        frame = PredictiveBackFrame()
    }

    private fun updateGesture(progress: Float) {
        rawProgress = progress
        gestureProgress = gestureInterpolator.getInterpolation(progress)
        var targetClosing = bounds.scaleCentered(0.9f)
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
        val entering = lerp(startEntering, startEntering.scaleCentered(0.9f), gestureProgress)
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

private fun Rect.scaleCentered(scale: Float): Rect {
    val halfWidth = width * scale / 2f
    val halfHeight = height * scale / 2f
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}
