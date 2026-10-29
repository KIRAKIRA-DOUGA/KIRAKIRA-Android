package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.AnimatedCountLabel
import moe.kirakira.ui.components.AnimatedNumberText
import moe.kirakira.ui.components.AnimatedPageCount
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ExpandableText
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.content.ContentState

@Composable
internal fun ContentStateSamples(onFeedback: (String) -> Unit, modifier: Modifier = Modifier) {
    var state by rememberSaveable { mutableIntStateOf(1) }
    var presentation by rememberSaveable { mutableIntStateOf(1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SampleRow {
            listOf("加载", "空状态", "错误").forEachIndexed { index, label ->
                TextButton(onClick = { state = index }, enabled = state != index) { Text(label) }
            }
        }
        SampleRow {
            listOf("页面", "列表", "媒体").forEachIndexed { index, label ->
                TextButton(onClick = { presentation = index }, enabled = presentation != index) { Text(label) }
            }
        }
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            ContentStatus(
                state = ContentState<Unit>(
                    loading = state == 0,
                    error = if (state == 2) ApiFailure.NETWORK else null,
                ),
                onRetry = { onFeedback("已点击重试") },
                presentation = ContentUnavailablePresentation.entries[presentation],
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun NumberSamples(modifier: Modifier = Modifier) {
    var count by rememberSaveable { mutableLongStateOf(9L) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedNumberText(value = count, style = MaterialTheme.typography.headlineMedium)
        AnimatedCountLabel(
            count = count,
            template = "%1\$s 条",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SampleRow {
            TextButton(onClick = { count-- }, enabled = count > -1000) { Text("减一") }
            TextButton(onClick = { count++ }, enabled = count < 1000) { Text("加一") }
            TextButton(onClick = { count = if (count == 9L) 10L else 9L }) { Text("9 ↔ 10") }
            TextButton(onClick = { count = if (count == 99L) 100L else 99L }) { Text("99 ↔ 100") }
            TextButton(onClick = { count = -count }) { Text("切换正负") }
        }
        // The same count in a narrow region exercises the label's overflow fallback.
        AnimatedCountLabel(count = count, template = "%1\$s 条结果", modifier = Modifier.widthIn(max = 80.dp))
        AnimatedPageCount(currentPage = page, totalPages = 12, template = "第 %1\$s / %2\$s 页")
        SampleRow {
            TextButton(onClick = { page-- }, enabled = page > 1) { Text("上一页") }
            TextButton(onClick = { page++ }, enabled = page < 12) { Text("下一页") }
        }
    }
}

@Composable
internal fun ExpandableTextSamples(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var shortExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpandableText(
            text = "这是一段简短的文字",
            expanded = shortExpanded,
            onExpandedChange = { shortExpanded = it },
        )
        ExpandableText(
            text = "午后的阳光落在窗边，桌上的书刚好翻到下一页。远处传来轻轻的脚步声，" +
                "风吹动了窗帘，也带来了花园里的清香。沿着小路走过长椅，再往前就是一片开阔的草地。" +
                "天色渐渐变暖，树影缓缓挪动，人们三三两两地坐下来聊天，享受这段安静的时光。",
            expanded = expanded,
            onExpandedChange = { expanded = it },
        )
    }
}

@Composable
internal fun IdentitySamples(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("游客头像：24dp / 40dp / 64dp")
        SampleRow {
            listOf(24.dp, 40.dp, 64.dp).forEach { size -> AccountAvatar(url = null, size = size) }
        }
        Text("强调 / 次要 / 第三色 / 中性 / 错误")
        SampleRow {
            IconBadgeTone.entries.forEach { tone -> IconBadge(icon = R.drawable.ic_symbol_info, tone = tone) }
        }
        Text("禁用徽章")
        IconBadge(icon = R.drawable.ic_symbol_lock, enabled = false)
    }
}
