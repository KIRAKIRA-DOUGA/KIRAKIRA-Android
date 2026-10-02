package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    SettingsPage(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
        modifier = modifier.testTag("about_screen"),
        backButtonModifier = Modifier.testTag("about_back"),
    ) {
        AboutHeader(versionName)
        SettingsSection(title = stringResource(R.string.about_section_more)) {
            SettingsNavigationItem(
                title = stringResource(R.string.about_github),
                icon = R.drawable.ic_logo_github,
                index = 0,
                count = 4,
                onClick = { uriHandler.openUri("https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Android") },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.about_licenses),
                icon = R.drawable.ic_symbol_description,
                index = 1,
                count = 4,
                onClick = onNavigateToLicenses,
                modifier = Modifier.testTag("about_licenses"),
            )
            SettingsNavigationItem(
                title = stringResource(R.string.about_privacy_policy),
                icon = R.drawable.ic_symbol_policy,
                index = 2,
                count = 4,
                onClick = { /* TODO: Open Privacy Policy */ },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.about_terms),
                icon = R.drawable.ic_symbol_gavel,
                index = 3,
                count = 4,
                onClick = { /* TODO: Open Terms */ },
            )
        }
    }
}

@Composable
private fun AboutHeader(versionName: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
//            Surface(
//                modifier = Modifier.size(104.dp),
//                shape = RoundedCornerShape(26.dp),
//                color = MaterialTheme.colorScheme.primary,
//            ) {
//
//            }
            Box(modifier = Modifier.size(88.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.requiredSize(156.dp),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.logo_kirakira_wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.height(20.dp),
                    tint = Color.White,
                )
                Text(
                    text = stringResource(R.string.about_for_android),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (versionName != null) {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.White,
                        backgroundColor = Color.White.copy(alpha = 0.4f),
                    ),
                ) {
                    SelectionContainer {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = stringResource(R.string.about_version, versionName),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
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
        AboutScreen(onBack = {}, onNavigateToLicenses = {})
    }
}
