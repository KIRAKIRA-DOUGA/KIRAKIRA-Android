package moe.kirakira.feature.me

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun MeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onOpenProfile: () -> Unit = {},
    profile: AccountProfile? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag("me_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ProfileListItem(onOpenProfile, profile)
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedMenuItem(
                    title = stringResource(R.string.me_history),
                    icon = R.drawable.ic_symbol_history,
                    index = 0,
                    count = 2,
                )
                SegmentedMenuItem(
                    title = stringResource(R.string.me_favorites),
                    icon = R.drawable.ic_symbol_star,
                    index = 1,
                    count = 2,
                )
            }
            SegmentedMenuItem(
                title = stringResource(R.string.me_settings),
                icon = R.drawable.ic_symbol_settings,
                onClick = onOpenSettings,
                index = 0,
                count = 1,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileListItem(
    onOpenProfile: () -> Unit,
    profile: AccountProfile?,
    modifier: Modifier = Modifier,
) {
    val supportingContent: (@Composable () -> Unit)? = profile?.username?.takeIf { it.isNotBlank() }?.let { username ->
        {
            Text(
                text = "@$username",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SegmentedListItem(
        onClick = onOpenProfile,
        modifier = modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { AccountAvatar(url = profile?.avatar, size = 72.dp) },
        supportingContent = supportingContent,
        trailingContent = {
            Icon(painterResource(R.drawable.ic_symbol_chevron_right), contentDescription = null)
        },
    ) {
        Text(
            text = profile?.displayName ?: stringResource(R.string.auth_sign_in),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Me · English", locale = "en", showBackground = true)
@Preview(name = "我 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Me · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Me · Large text", fontScale = 1.5f, showBackground = true)
@Composable
private fun MePreview() {
    KIRAKIRATheme(dynamicColor = false) { MeScreen(onOpenSettings = {}) }
}
