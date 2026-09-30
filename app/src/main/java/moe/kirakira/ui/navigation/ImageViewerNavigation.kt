package moe.kirakira.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay

private data object ImageTransitionKey : NavMetadataKey<Boolean>

/**
 * NavDisplay selects the top scene's metadata for push, pop and predictive back. Keeping these
 * overrides on the image entry separates its presentation from contentKey's saved-state identity.
 */
internal fun imageViewerNavigationMetadata(easing: Easing) = metadata {
    put(ImageTransitionKey, true)
    put(NavDisplay.TransitionKey) {
        // Retain the stationary source even when it has no matching shared image.
        fadeIn(imageTransitionSpec(easing)) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(imageTransitionSpec(easing))
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith fadeOut(imageTransitionSpec(easing))
    }
}

internal val NavEntry<*>.usesImageTransition: Boolean
    get() = metadata[ImageTransitionKey] == true
