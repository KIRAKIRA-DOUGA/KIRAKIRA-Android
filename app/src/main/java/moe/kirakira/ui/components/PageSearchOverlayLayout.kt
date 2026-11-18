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

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import moe.kirakira.ui.theme.BarShadowElevation

/**
 * Adapts AndroidX FullScreenSearchBarLayout's expansion geometry to the app's top bar height.
 * See third_party/android-search/README.md for source and intentional differences.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PageSearchOverlayLayout(
    progress: () -> Float,
    collapsedBoundsProvider: () -> Rect?,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowInsets = SearchBarDefaults.fullScreenWindowInsets.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
    val unconsumedInsets = remember { MutableWindowInsets() }
    val horizontalInsets = unconsumedInsets.only(WindowInsetsSides.Horizontal)
    val collapsedShape = MaterialTheme.shapes.large
    val surfaceColor = MaterialTheme.colorScheme.surface
    Layout(
        modifier = modifier
            .onConsumedWindowInsetsChanged { unconsumedInsets.insets = windowInsets.exclude(it) }
            .consumeWindowInsets(windowInsets),
        content = {
            Surface(
                modifier = Modifier.graphicsLayer {
                    shape = searchExpansionShape(collapsedShape, progress())
                    clip = true
                },
                color = surfaceColor,
                content = {},
            )
            Box(
                modifier = Modifier.clip(collapsedShape),
                propagateMinConstraints = true,
            ) {
                inputField()
            }
            Column(Modifier.padding(horizontalInsets.asPaddingValues()), content = content)
            Box(
                Modifier.graphicsLayer {
                    val fraction = progress()
                    shape = searchExpansionShape(collapsedShape, fraction)
                    shadowElevation = BarShadowElevation.toPx() * fraction
                    clip = true
                }.background(surfaceColor),
            )
        },
    ) { measurables, constraints ->
        val fraction = progress()
        val defaultFieldHeight = SearchBarDefaults.InputFieldHeight.roundToPx()
        val collapsed = collapsedBoundsProvider()
            ?: Rect(0f, 0f, constraints.maxWidth.toFloat(), defaultFieldHeight.toFloat())
        val startWidth = collapsed.width.roundToInt().coerceAtLeast(1)
        val startHeight = collapsed.height.roundToInt().coerceAtLeast(1)
        val width = lerp(startWidth, constraints.maxWidth, fraction).coerceIn(1, constraints.maxWidth)
        val height = lerp(startHeight, constraints.maxHeight, fraction).coerceIn(1, constraints.maxHeight)
        val defaultBarHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight.roundToPx()
        val barHeight = lerp(startHeight, maxOf(startHeight, defaultBarHeight), fraction)
        val topInset = lerp(0, unconsumedInsets.getTop(this), fraction)
        val headerHeight = (topInset + barHeight).coerceAtMost(height)
        val surface = measurables[0].measure(Constraints.fixed(width, height))
        val leftInset = lerp(0, horizontalInsets.getLeft(this, layoutDirection), fraction)
        val rightInset = lerp(0, horizontalInsets.getRight(this, layoutDirection), fraction)
        val inputWidth = (width - leftInset - rightInset).coerceAtLeast(0)
        val input = measurables[1].measure(Constraints.fixed(inputWidth, startHeight))
        val results = measurables[2].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                maxHeight = height - headerHeight,
            ),
        )
        val header = measurables[3].measure(Constraints.fixed(width, headerHeight))
        val x = lerp(collapsed.left.roundToInt(), 0, fraction)
        val y = lerp(collapsed.top.roundToInt(), 0, fraction)
        val inputY = y + topInset + (barHeight - input.height) / 2
        layout(constraints.maxWidth, constraints.maxHeight) {
            surface.place(x, y)
            results.placeWithLayer(x, y + header.height) {
                alpha = fraction
            }
            header.place(x, y)
            input.place(x + leftInset, inputY)
        }
    }
}
