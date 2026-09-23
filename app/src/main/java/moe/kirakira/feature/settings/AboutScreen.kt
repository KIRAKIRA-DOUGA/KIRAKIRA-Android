package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRAPink
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    var logoClickCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("about_screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CollapsibleTopAppBar(
                title = stringResource(R.string.settings_about),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                backButtonModifier = Modifier.testTag("about_back"),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Bottom inset scrolls with the content so the viewport reaches behind the navigation bar.
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = 32.dp,
                        bottom = innerPadding.calculateBottomPadding() + 32.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // App Logo and Name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            logoClickCount += 1
                            if (logoClickCount == 10) {
                                logoClickCount = 0
                                onNavigateToTest()
                            }
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        color = KIRAKIRAPink
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.requiredSize(144.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (versionName != null) {
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Links
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SegmentedMenuItem(
                        title = stringResource(R.string.about_github),
                        icon = R.drawable.ic_symbol_info, // Should use a GitHub icon if available
                        index = 0,
                        count = 4,
                        onClick = { uriHandler.openUri("https://github.com/KIRAKIRA-DOUGA/") },
                    )
                    SegmentedMenuItem(
                        title = stringResource(R.string.about_licenses),
                        icon = R.drawable.ic_symbol_shield,
                        index = 1,
                        count = 4,
                        onClick = onNavigateToLicenses,
                        modifier = Modifier.testTag("about_licenses"),
                    )
                    SegmentedMenuItem(
                        title = stringResource(R.string.about_privacy_policy),
                        icon = R.drawable.ic_symbol_lock,
                        index = 2,
                        count = 4,
                        onClick = { /* TODO: Open Privacy Policy */ }
                    )
                    SegmentedMenuItem(
                        title = stringResource(R.string.about_terms),
                        icon = R.drawable.ic_symbol_confirmation_number,
                        index = 3,
                        count = 4,
                        onClick = { /* TODO: Open Terms */ }
                    )
                }
            }
        }
    }
}

@Preview(name = "About · English", locale = "en", showBackground = true)
@Preview(name = "关于 · 中文", locale = "zh", showBackground = true)
@Preview(name = "About · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AboutPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        AboutScreen(onBack = {}, onNavigateToLicenses = {}, onNavigateToTest = {})
    }
}
