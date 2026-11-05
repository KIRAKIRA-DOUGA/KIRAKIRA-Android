/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Adapts AndroidX FullScreenSearchBarLayout's ordinary expansion geometry to an Activity layer.
 * The public SearchBarState supplies official motion timing; only the window host is replaced.
 * See third_party/android-search/README.md for source and intentional differences.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageSearchOverlayLayout(
    state: SearchBarState,
    collapsedBoundsProvider: () -> Rect?,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowInsets = SearchBarDefaults.fullScreenWindowInsets
    val unconsumedInsets = remember { MutableWindowInsets() }
    val nonTopInsets = unconsumedInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Layout(
        modifier = modifier
            .onConsumedWindowInsetsChanged { unconsumedInsets.insets = windowInsets.exclude(it) }
            .consumeWindowInsets(windowInsets),
        content = {
            Surface(
                modifier = Modifier.graphicsLayer {
                    val startHeight = collapsedBoundsProvider()?.height ?: SearchBarDefaults.InputFieldHeight.toPx()
                    shape = RoundedCornerShape((startHeight / 2f * (1f - state.progress)).toDp())
                    clip = true
                },
                color = MaterialTheme.colorScheme.surface,
                content = {},
            )
            Box(
                modifier = Modifier.padding(nonTopInsets.only(WindowInsetsSides.Horizontal).asPaddingValues())
                    .clip(SearchBarDefaults.inputFieldShape),
                propagateMinConstraints = true,
            ) {
                inputField()
            }
            Column(Modifier.padding(nonTopInsets.asPaddingValues()), content = content)
        },
    ) { measurables, constraints ->
        val progress = state.progress
        val fallbackHeight = SearchBarDefaults.InputFieldHeight.roundToPx()
        val collapsed = collapsedBoundsProvider()
            ?: Rect(0f, 0f, constraints.maxWidth.toFloat(), fallbackHeight.toFloat())
        val startWidth = collapsed.width.roundToInt().coerceAtLeast(1)
        val startHeight = collapsed.height.roundToInt().coerceAtLeast(1)
        val width = lerp(startWidth, constraints.maxWidth, progress).coerceIn(1, constraints.maxWidth)
        val height = lerp(startHeight, constraints.maxHeight, progress).coerceIn(1, constraints.maxHeight)
        val topPadding = lerp(0, unconsumedInsets.getTop(this) + 8.dp.roundToPx(), progress)
        val bottomPadding = lerp(0, 8.dp.roundToPx(), progress)
        val surface = measurables[0].measure(Constraints.fixed(width, height))
        val inputWidth = lerp(startWidth, width, progress)
        val input = measurables[1].measure(Constraints.fixed(inputWidth, startHeight))
        val results = measurables[2].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                maxHeight = (height - input.height - topPadding - bottomPadding).coerceAtLeast(0),
            ),
        )
        val x = lerp(collapsed.left.roundToInt(), 0, progress)
        val y = lerp(collapsed.top.roundToInt(), 0, progress)
        val inputCenterX = lerp(collapsed.center.x, x + width / 2f, progress)
        layout(constraints.maxWidth, constraints.maxHeight) {
            surface.place(x, y)
            input.place((inputCenterX - input.width / 2f).roundToInt(), y + topPadding)
            results.placeWithLayer(x, y + topPadding + input.height + bottomPadding) {
                alpha = progress
            }
        }
    }
}
