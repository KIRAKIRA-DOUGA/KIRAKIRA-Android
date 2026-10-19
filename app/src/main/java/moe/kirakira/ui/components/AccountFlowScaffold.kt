package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.kirakira.R
import moe.kirakira.ui.theme.BarShadowElevation

internal object AccountFlowDefaults {
    val MaxContentWidth = 480.dp
    val ContentPadding = 24.dp
    val HeaderSpacing = 32.dp
    val SectionSpacing = 32.dp
    val FieldSpacing = 24.dp
    val FeedbackSpacing = 12.dp
    val ActionBarVerticalPadding = 16.dp
}

@Composable
internal fun accountFlowCodeTextStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(
    fontFamily = FontFamily.Monospace,
    letterSpacing = 1.sp,
)

/** Owns form scrolling and toolbar shadow. IME avoidance includes the Snackbar and consumes the inset once. */
@Composable
internal fun AccountFlowScaffold(
    @DrawableRes icon: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    backButtonModifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, ScrollState) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBarShadowElevation = if (isScrolled) BarShadowElevation else 0.dp,
        topBar = {
            CenterAlignedTopAppBar(
                colors = appTopAppBarColors(),
                title = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(iconSize),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = backButtonModifier) {
                            Icon(
                                painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        content = { padding -> content(padding, scrollState) },
    )
}

/** Account-flow actions align with the form and stay above the system navigation area. */
@Composable
internal fun AccountFlowActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FormActionBar(modifier) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = AccountFlowDefaults.ContentPadding,
                    vertical = AccountFlowDefaults.ActionBarVerticalPadding,
                )
                .widthIn(max = AccountFlowDefaults.MaxContentWidth)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

/** Width-capped form with system-bar padding inside the full-height scroll viewport. */
@Composable
internal fun AccountFlowColumn(
    padding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
            )
            .consumeWindowInsets(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = AccountFlowDefaults.ContentPadding)
                .widthIn(max = AccountFlowDefaults.MaxContentWidth)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = padding.calculateTopPadding() + AccountFlowDefaults.ContentPadding,
                    bottom = padding.calculateBottomPadding() + AccountFlowDefaults.ContentPadding,
                ),
            content = content,
        )
    }
}

@Composable
internal fun AccountFlowHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    progressContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (progressContent != null) {
            Spacer(Modifier.height(12.dp))
            progressContent()
        }
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AccountFlowProgress(currentStep: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.auth_step_progress, currentStep, stepCount)
    val progress = currentStep.toFloat() / stepCount
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.auth_step_progress_compact, currentStep, stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(4.dp),
        )
    }
}

/** A plain form group. Actions wrap without narrowing the fields or their context. */
@Composable
internal fun AccountFlowSection(
    title: String?,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null || supportingContent != null || actionContent != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .then(if (actionContent != null) Modifier.padding(end = 12.dp) else Modifier)
                            .semantics { heading() },
                    )
                } else if (supportingContent != null) {
                    Box(
                        modifier = Modifier
                            .then(if (actionContent != null) Modifier.padding(end = 12.dp) else Modifier),
                    ) {
                        supportingContent()
                    }
                }
                if (actionContent != null) {
                    Box(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        actionContent()
                    }
                }
            }
            if (title != null && supportingContent != null) {
                Spacer(Modifier.height(4.dp))
                supportingContent()
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing),
            content = content,
        )
    }
}

@Composable
internal fun AccountFlowError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun AccountFlowSubmitButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    actionDescription: String? = null,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val workingDescription = stringResource(R.string.auth_working)
    ShadowButton(
        onClick = onClick,
        enabled = enabled && !busy,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .semantics {
                actionDescription?.let { contentDescription = it }
                if (busy) {
                    contentDescription = actionDescription ?: label
                    stateDescription = workingDescription
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Keep the label measured at the current font scale while the spinner replaces it.
            Text(
                text = label,
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                softWrap = false,
                modifier = if (busy) Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { } else Modifier,
            )
            if (busy) {
                IndeterminateCircularProgressIndicator(
                    modifier = Modifier
                        .size(ButtonDefaults.iconSizeFor(height))
                        .clearAndSetSemantics { },
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
