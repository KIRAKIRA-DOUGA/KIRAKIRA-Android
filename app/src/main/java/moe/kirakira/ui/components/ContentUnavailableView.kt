package moe.kirakira.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** INLINE delegates scrolling to its parent; PAGE and MEDIA require a bounded viewport. */
enum class ContentUnavailablePresentation { PAGE, INLINE, MEDIA }

/** Labels come from string resources. Button appearance belongs exclusively to this component. */
data class ContentUnavailableAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * Shared empty/error content and actions. The caller owns Insets and available space.
 * [onRetry] is a primary-action shortcut and cannot be combined with [primaryAction].
 * INLINE never creates a scroll container, including when hosted in a lazy list.
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
    presentation: ContentUnavailablePresentation = ContentUnavailablePresentation.PAGE,
    primaryAction: ContentUnavailableAction? = null,
    secondaryAction: ContentUnavailableAction? = null,
    retryEnabled: Boolean = true,
) {
    require(onRetry == null || primaryAction == null) { "Use either onRetry or primaryAction" }
    val action = primaryAction ?: onRetry?.let {
        ContentUnavailableAction(stringResource(R.string.content_unavailable_retry), it, retryEnabled)
    }
    val media = presentation == ContentUnavailablePresentation.MEDIA
    val inline = presentation == ContentUnavailablePresentation.INLINE
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .then(if (inline) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .then(if (media) Modifier.background(Color.Black) else Modifier)
            .padding(if (media) 8.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val scroll = !inline && constraints.hasBoundedHeight
        val showDecoration = !media || maxHeight >= 180.dp
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (media) 8.dp else 12.dp),
        ) {
            // Keep the action reachable even in the short, embedded player viewport.
            if (showDecoration) {
                Surface(
                    modifier = Modifier.size(if (media) 48.dp else 144.dp),
                    shape = when (state) {
                        ContentUnavailableState.EMPTY -> MaterialShapes.Cookie12Sided
                        ContentUnavailableState.ERROR -> MaterialShapes.Clover4Leaf
                    }.toShape(),
                    color = if (media) Color.DarkGray else scheme.surfaceContainer,
                    contentColor = if (media) Color.White else scheme.onSurfaceVariant,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(if (media) 24.dp else 56.dp),
                        )
                    }
                }
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = if (media) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = if (media) Color.White else scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (media) Color.LightGray else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            val buttonModifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
            if (action != null) {
                ShadowButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = buttonModifier,
                    colors = if (media) ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.DarkGray,
                        disabledContentColor = Color.LightGray,
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    Text(action.label, textAlign = TextAlign.Center)
                }
            }
            if (secondaryAction != null) {
                TextButton(
                    onClick = secondaryAction.onClick,
                    enabled = secondaryAction.enabled,
                    modifier = buttonModifier,
                    colors = if (media) ButtonDefaults.textButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.Gray,
                    ) else ButtonDefaults.textButtonColors(),
                ) {
                    Text(secondaryAction.label, textAlign = TextAlign.Center)
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

@Preview(name = "Media error", widthDp = 264, heightDp = 187)
@Preview(name = "Media large text", fontScale = 2f, widthDp = 224, heightDp = 160)
@Composable
private fun ContentUnavailableMediaPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        ContentUnavailableView(
            state = ContentUnavailableState.ERROR,
            title = stringResource(R.string.player_error),
            description = null,
            presentation = ContentUnavailablePresentation.MEDIA,
            onRetry = {},
        )
    }
}

@Preview(name = "Inline actions", showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun ContentUnavailableInlinePreview() {
    KIRAKIRATheme(dynamicColor = false) {
        LazyColumn {
            item {
                ContentUnavailableView(
                    state = ContentUnavailableState.ERROR,
                    presentation = ContentUnavailablePresentation.INLINE,
                    onRetry = {},
                    retryEnabled = false,
                    secondaryAction = ContentUnavailableAction(stringResource(R.string.navigate_back), {}),
                )
            }
        }
    }
}
