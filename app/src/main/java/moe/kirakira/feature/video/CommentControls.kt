package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.BarShadowElevation

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentToolbar(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean,
    onPage: (Int) -> Unit,
    onOpenJump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageLabel = stringResource(R.string.video_comments_page_count, currentPage, totalPages)
    val pageDescription = stringResource(R.string.video_comments_page_description, currentPage, totalPages)
    val pageTextStyle = MaterialTheme.typography.labelLarge
    val pageButtonPadding = ButtonDefaults.TextButtonContentPadding
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pageTextHeight = with(density) {
        textMeasurer.measure(pageLabel, pageTextStyle, softWrap = false, maxLines = 1).size.height.toDp()
    }
    val toolbarHeight = (
        pageTextHeight + pageButtonPadding.calculateTopPadding() + pageButtonPadding.calculateBottomPadding()
    ).coerceAtLeast(48.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    HorizontalFloatingToolbar(
        expanded = true,
        // Constrain the official 64dp minimum to a compact height that follows font scaling.
        modifier = modifier.height(toolbarHeight),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surface,
            toolbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        expandedShadowElevation = BarShadowElevation,
        collapsedShadowElevation = BarShadowElevation,
    ) {
        IconButton(onClick = { onPage(currentPage - 1) }, enabled = enabled && currentPage > 1) {
            Icon(
                painterResource(R.drawable.ic_symbol_chevron_right),
                contentDescription = stringResource(R.string.video_comments_previous_page),
                modifier = Modifier.graphicsLayer { rotationZ = if (rtl) 0f else 180f },
            )
        }
        TextButton(
            onClick = onOpenJump,
            enabled = enabled,
            modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = pageDescription },
            contentPadding = pageButtonPadding,
        ) {
            Text(
                text = pageLabel,
                style = pageTextStyle,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onPage(currentPage + 1) }, enabled = enabled && currentPage < totalPages) {
            Icon(
                painterResource(R.drawable.ic_symbol_chevron_right),
                contentDescription = stringResource(R.string.video_comments_next_page),
                modifier = Modifier.graphicsLayer { rotationZ = if (rtl) 180f else 0f },
            )
        }
    }
}

@Composable
internal fun CommentJumpDialog(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(currentPage.toString()) }
    val target = input.toIntOrNull()
    val valid = enabled && target != null && target in 1..totalPages
    val jump = { if (valid) onJump(target) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.video_comments_jump_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.video_comments_page)) },
                    singleLine = true,
                    isError = input.isNotEmpty() && (target == null || target !in 1..totalPages),
                    supportingText = { Text(stringResource(R.string.video_comments_jump_range, totalPages)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { jump() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = jump, enabled = valid) { Text(stringResource(R.string.video_comments_jump)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.video_comments_cancel)) }
        },
    )
}
