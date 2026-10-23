package moe.kirakira.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import moe.kirakira.R

@Composable
internal fun FollowButton(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
) {
    val source = remember { MutableInteractionSource() }
    val motion = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    val followState = remember { MutableTransitionState(following) }
    followState.targetState = following
    val followTransition = rememberTransition(followState, label = "followButton")
    val shapes = ToggleButtonDefaults.shapesFor(buttonSize)
    val pressed by source.collectIsPressedAsState()
    val checkedProgress by followTransition.animateFloat(
        transitionSpec = { motion.fastSpatialSpec() },
        label = "followButtonCheckedShape",
    ) { checked -> if (checked) 1f else 0f }
    val containerColor by followTransition.animateColor(
        transitionSpec = { motion.fastEffectsSpec() },
        label = "followButtonContainerColor",
    ) { checked -> if (checked) colorScheme.surfaceContainerHigh else colorScheme.primary }
    val contentColor by followTransition.animateColor(
        transitionSpec = { motion.fastEffectsSpec() },
        label = "followButtonContentColor",
    ) { checked -> if (checked) colorScheme.onSurfaceVariant else colorScheme.onPrimary }
    val pressedProgress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "followButtonPressedShape",
    )
    val restingShape = interpolateFollowShape(shapes.shape, shapes.checkedShape, checkedProgress)
    val outline = interpolateFollowShape(restingShape, shapes.pressedShape, pressedProgress)
    val colors = ToggleButtonDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        checkedContainerColor = containerColor,
        checkedContentColor = contentColor,
    )
    val buttonModifier = if (following) {
        modifier
    } else {
        modifier.buttonShadow(outline, colors.containerColor, enabled, source)
    }
    val label = stringResource(if (following) R.string.video_following else R.string.video_follow)
    ToggleButton(
        checked = following,
        onCheckedChange = onFollowingChange,
        // Keep one current accessibility label while both visual labels are composed.
        modifier = buttonModifier.semantics { contentDescription = label },
        buttonSize = buttonSize,
        enabled = enabled,
        icon = {
            followTransition.AnimatedContent(
                transitionSpec = {
                    (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.55f))
                        .togetherWith(
                            fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.55f),
                        ).using(null)
                },
                contentAlignment = Alignment.Center,
            ) { checked ->
                val rotation = transition.animateFloat(
                    transitionSpec = { motion.fastSpatialSpec() },
                    label = "followButtonIconRotation",
                ) { state ->
                    when (state) {
                        EnterExitState.PreEnter -> -60f
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> 60f
                    }
                }
                Icon(
                    painter = painterResource(if (checked) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                )
            }
        },
        // The surface and shadow use one animated outline, including checked-state transitions.
        shapes = ToggleButtonShapes(outline, outline, outline),
        colors = colors,
        elevation = null,
        interactionSource = source,
    ) {
        followTransition.AnimatedContent(
            modifier = Modifier.clearAndSetSemantics {},
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { direction * it / 3 })
                    .togetherWith(
                        fadeOut(motion.fastEffectsSpec()) +
                            slideOutVertically(motion.fastSpatialSpec()) { -direction * it / 3 },
                    ).using(SizeTransform { _, _ -> motion.fastSpatialSpec() })
            },
            contentAlignment = Alignment.Center,
        ) { checked ->
            Text(stringResource(if (checked) R.string.video_following else R.string.video_follow))
        }
    }
}

private fun interpolateFollowShape(from: Shape, to: Shape, progress: Float): Shape =
    Interpolatable.lerp(from, to, progress) as? Shape ?: if (progress < 0.5f) from else to
