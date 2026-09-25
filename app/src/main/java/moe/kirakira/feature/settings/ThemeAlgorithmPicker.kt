package moe.kirakira.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.theme.KIRAKIRAPink
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorAlgorithm

private val leadingAlgorithms = listOf(
    ThemeColorAlgorithm.TONAL_SPOT,
    ThemeColorAlgorithm.CLASSIC_ACCENT,
)

private val remainingAlgorithms = ThemeColorAlgorithm.entries.filterNot { it in leadingAlgorithms }

@Composable
internal fun ThemeAlgorithmPicker(
    selectedAlgorithm: ThemeColorAlgorithm,
    seedColor: Color,
    onSelect: (ThemeColorAlgorithm) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(items = leadingAlgorithms, key = { it.name }) { algorithm ->
            ThemePaletteOption(
                seedColor = seedColor,
                label = stringResource(algorithm.titleRes),
                selected = selectedAlgorithm == algorithm,
                onClick = { onSelect(algorithm) },
                modifier = Modifier.width(88.dp),
                algorithm = algorithm,
            )
        }
        item(key = "algorithm_divider") {
            VerticalDivider(modifier = Modifier.height(30.dp))
        }
        items(items = remainingAlgorithms, key = { it.name }) { algorithm ->
            ThemePaletteOption(
                seedColor = seedColor,
                label = stringResource(algorithm.titleRes),
                selected = selectedAlgorithm == algorithm,
                onClick = { onSelect(algorithm) },
                modifier = Modifier.width(88.dp),
                algorithm = algorithm,
            )
        }
    }
}

@Preview(name = "Algorithms · English", locale = "en", showBackground = true)
@Preview(name = "配色算法 · 中文", locale = "zh", showBackground = true)
@Composable
private fun ThemeAlgorithmPickerPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        ThemeAlgorithmPicker(
            selectedAlgorithm = ThemeColorAlgorithm.TONAL_SPOT,
            seedColor = KIRAKIRAPink,
            onSelect = {},
        )
    }
}
