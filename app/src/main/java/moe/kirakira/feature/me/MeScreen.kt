package moe.kirakira.feature.me

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.PlaceholderAvatar
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ProfileCard()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.me_library),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        icon = R.drawable.ic_symbol_video_library,
                        index = 1,
                        count = 2,
                    )
                }
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
private fun ProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlaceholderAvatar(size = 72.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.me_placeholder_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.me_placeholder_handle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(text = stringResource(R.string.me_placeholder_bio), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 3,
            ) {
                ProfileStat(stringResource(R.string.me_following_count))
                ProfileStat(stringResource(R.string.me_followers_count))
                ProfileStat(stringResource(R.string.me_likes_count))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.me_profile),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(painterResource(R.drawable.ic_symbol_chevron_right), contentDescription = null)
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.placeholder_count), style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.labelMedium)
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
