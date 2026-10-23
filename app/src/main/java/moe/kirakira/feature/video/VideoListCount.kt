package moe.kirakira.feature.video

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.AnimatedCountLabel

@Composable
internal fun VideoListCount(
    @PluralsRes labelRes: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedCountLabel(
        count = count.toLong(),
        template = pluralStringResource(labelRes, count),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
    )
}
