package moe.kirakira.ui.components.scaffold

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import moe.kirakira.R
import moe.kirakira.ui.adaptive.LocalRailPageFrame
import moe.kirakira.ui.adaptive.browsePageMaxWidth
import moe.kirakira.ui.theme.BrandTitleFontFamily

internal val LocalPageBackButtonVisible = compositionLocalOf { true }

/** Center the toolbar controls over a bounded body while keeping the host surface full width. */
internal fun Modifier.centeredTopAppBarContent(maxWidth: Dp): Modifier =
    fillMaxWidth().wrapContentWidth().widthIn(max = maxWidth)

/** Place top-level controls in the rail frame while leaving the surface to the caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Modifier.topAppBarContentWidth(maxWidth: Dp = Dp.Infinity): Modifier {
    val contentWidth = if (LocalRailPageFrame.current) browsePageMaxWidth else maxWidth
    return windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal))
        .centeredTopAppBarContent(contentWidth)
}

/** Fixed small top bar for pages with a back action. The host draws the bar surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backButtonModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BrandTitleFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (LocalPageBackButtonVisible.current) IconButton(onClick = onBack, modifier = backButtonModifier) {
                Icon(
                    painter = painterResource(R.drawable.ic_icon_arrow_left),
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        },
        actions = actions,
        colors = appTopAppBarColors(),
    )
}

/** Shared colors for ordinary app-owned top bars. */
@Composable
internal fun appTopAppBarColors(
    containerColor: Color = Color.Transparent,
    scrolledContainerColor: Color = Color.Transparent,
): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        navigationIconContentColor = scheme.onSurfaceVariant,
        titleContentColor = scheme.primary,
        actionIconContentColor = scheme.onSurfaceVariant,
    )
}
