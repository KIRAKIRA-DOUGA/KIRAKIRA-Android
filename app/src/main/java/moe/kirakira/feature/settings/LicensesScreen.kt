package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.accentDerivedLicenseHueResolver
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.style.LibraryActionBadges
import com.mikepenz.aboutlibraries.ui.compose.style.VariantColors
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.kirakira.R
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

internal sealed interface LicensesUiState {
    data object Loading : LicensesUiState
    data class Ready(val libraries: Libs) : LicensesUiState
    data object Failed : LicensesUiState
}

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LicensesUiState>(LicensesUiState.Loading) }

    LaunchedEffect(resources, attempt) {
        state = LicensesUiState.Loading
        state = try {
            val libraries = withContext(Dispatchers.IO) {
                val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
                Libs.Builder().withJson(json).build()
            }
            LicensesUiState.Ready(libraries)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LicensesUiState.Failed
        }
    }

    LicensesContent(state = state, onBack = onBack, onRetry = { attempt++ }, modifier = modifier)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LicensesContent(
    state: LicensesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    var sheetLibraryId by rememberSaveable { mutableStateOf<String?>(null) }

    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("licenses_screen"),
        containerColor = ThemeColorDefaults.settingsBackgroundColor(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licenses), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("licenses_back")) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state is LicensesUiState.Ready && state.libraries.libraries.isNotEmpty()) {
                // Keep the upstream Material 3 rows and sheet; only the surrounding page is app-owned.
                LibrariesContainer(
                    libraries = state.libraries,
                    dialogLibrary = null,
                    sheetLibrary = state.libraries.libraries.firstOrNull { it.uniqueId == sheetLibraryId },
                    onDialogLibraryChange = {},
                    onSheetLibraryChange = { sheetLibraryId = it?.uniqueId },
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .testTag("licenses_list"),
                    lazyListState = listState,
                    // Insets belong to scrolling content, so the viewport reaches behind the system bars.
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
                    detailMode = LibraryDetailMode.Sheet,
                    actionLabels = LibraryActionBadges(
                        source = stringResource(R.string.licenses_source),
                        website = stringResource(R.string.licenses_website),
                        sponsor = stringResource(R.string.licenses_sponsor),
                        viewLicense = stringResource(R.string.licenses_view_license),
                    ),
                    colors = LibraryDefaults.libraryColors(
                        libraryBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    variantColors = licensesVariantColors(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    when (state) {
                        LicensesUiState.Loading -> {
                            IndeterminateCircularProgressIndicator()
                        }

                        LicensesUiState.Failed -> ContentUnavailableView(
                            state = ContentUnavailableState.ERROR,
                            title = stringResource(R.string.licenses_error),
                            onRetry = onRetry,
                        )

                        is LicensesUiState.Ready -> ContentUnavailableView(
                            state = ContentUnavailableState.EMPTY,
                            title = stringResource(R.string.licenses_empty),
                            description = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun licensesVariantColors(): VariantColors {
    val scheme = MaterialTheme.colorScheme
    return LibraryDefaults.m3VariantColors(
        tabActiveBackground = scheme.primaryContainer,
        tabActiveContent = scheme.onPrimaryContainer,
        licenseHueResolver = accentDerivedLicenseHueResolver(isDark = scheme.surface.luminance() < 0.5f),
    )
}

@Preview(name = "Open source components · English", locale = "en", showBackground = true)
@Preview(name = "开源组件 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Open source components · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LicensesPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        LicensesContent(
//            state = LicensesUiState.Ready(Libs(emptyList(), emptySet())),
            state = LicensesUiState.Failed,
            onBack = {},
            onRetry = {},
        )
    }
}
