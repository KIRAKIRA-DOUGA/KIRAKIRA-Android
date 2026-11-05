package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Loading content follows the same caller-owned viewport as empty and error content. */
@Composable
internal fun ContentLoadingView(
    modifier: Modifier = Modifier,
    presentation: ContentUnavailablePresentation = ContentUnavailablePresentation.PAGE,
) {
    Box(
        modifier = modifier
            .then(
                if (presentation == ContentUnavailablePresentation.INLINE) Modifier.fillMaxWidth()
                else Modifier.fillMaxSize(),
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        IndeterminateCircularProgressIndicator()
    }
}
