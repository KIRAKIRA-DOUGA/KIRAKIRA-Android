package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode

@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("appearance_screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CollapsibleTopAppBar(
                title = stringResource(R.string.settings_appearance),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                backButtonModifier = Modifier.testTag("appearance_back"),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        val selected = themeMode == mode
                        SegmentedListItem(
                            onClick = { onThemeModeChange(mode) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = ThemeMode.entries.size),
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onThemeModeChange(mode) }
                                )
                            },
                            content = {
                                Text(stringResource(mode.titleRes))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Appearance · English", locale = "en", showBackground = true)
@Preview(name = "外观 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Appearance · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AppearancePreview() {
    KIRAKIRATheme(dynamicColor = false) {
        AppearanceScreen(
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            onBack = {}
        )
    }
}
