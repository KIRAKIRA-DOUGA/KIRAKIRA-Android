package moe.kirakira.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

enum class ContentUnavailableState(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    EMPTY(
        titleRes = R.string.content_unavailable_empty_title,
        descriptionRes = R.string.content_unavailable_empty_description,
        iconRes = R.drawable.ic_symbol_info,
    ),
    ERROR(
        titleRes = R.string.content_unavailable_error_title,
        descriptionRes = R.string.content_unavailable_error_description,
        iconRes = R.drawable.ic_symbol_error,
    ),
}

/**
 * 在可用空间中居中展示空内容或加载失败状态；高度不足时可滚动。
 * 调用方负责页面背景和 Insets，也可通过 [modifier] 限定组件大小。
 * [title] 和 [description] 应来自字符串资源；传入 null 可隐藏说明。
 * 仅提供 [onRetry] 时显示重试按钮，实际加载及状态切换由调用方管理。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentUnavailableView(
    state: ContentUnavailableState,
    modifier: Modifier = Modifier,
    title: String = stringResource(state.titleRes),
    description: String? = stringResource(state.descriptionRes),
    @DrawableRes iconRes: Int = state.iconRes,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(144.dp),
                shape = when (state) {
                    ContentUnavailableState.EMPTY -> MaterialShapes.Cookie6Sided
                    ContentUnavailableState.ERROR -> MaterialShapes.Clover4Leaf
                }.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.content_unavailable_retry))
                }
            }
        }
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360, heightDp = 480)
@Preview(name = "Empty wide", showBackground = true, widthDp = 840, heightDp = 480)
@Composable
private fun ContentUnavailableEmptyPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Surface {
            ContentUnavailableView(state = ContentUnavailableState.EMPTY)
        }
    }
}

@Preview(
    name = "Error dark Chinese",
    locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    widthDp = 360,
    heightDp = 480,
)
@Preview(
    name = "Error large text",
    fontScale = 2f,
    showBackground = true,
    widthDp = 320,
    heightDp = 320,
)
@Composable
private fun ContentUnavailableErrorPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Surface {
            ContentUnavailableView(state = ContentUnavailableState.ERROR, onRetry = {})
        }
    }
}
