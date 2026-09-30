package moe.kirakira.feature.video

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuMode
import moe.kirakira.data.content.DanmakuStyle

@Composable
internal fun DanmakuStylePreview(draft: String, style: DanmakuStyle, modifier: Modifier = Modifier) {
    val text = draft.ifBlank { stringResource(R.string.danmaku_style_preview_text) }.replace('\n', ' ')
    val description = stringResource(R.string.danmaku_style_preview)
    var width by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val moving = style.mode == DanmakuMode.RTL || style.mode == DanmakuMode.LTR
    val animate = moving && ValueAnimator.areAnimatorsEnabled()
    val progress = if (animate) {
        val transition = rememberInfiniteTransition(label = "Danmaku preview")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "Travel")
        value
    } else 0.5f
    val textStyle = TextStyle(
        color = Color(0xFF000000.toInt() or style.color),
        fontSize = style.fontSize.previewSize.sp,
        fontWeight = FontWeight.Bold,
        shadow = Shadow(Color.Black, blurRadius = 3f),
    )
    Box(
        modifier.fillMaxWidth().height(132.dp).clip(MaterialTheme.shapes.extraLarge)
            .background(Color(0xFF171A22)).onSizeChanged { width = it.width }
            .clearAndSetSemantics { contentDescription = "$description: $text" },
    ) {
        val alignment = when (style.mode) {
            DanmakuMode.TOP -> Alignment.TopCenter
            DanmakuMode.BOTTOM -> Alignment.BottomCenter
            else -> Alignment.CenterStart
        }
        Box(
            Modifier.align(alignment).padding(vertical = 16.dp)
                .offset {
                    if (!moving) IntOffset.Zero else {
                        val travel = if (style.mode == DanmakuMode.RTL) 1f - progress else progress
                        val x = if (animate) -textWidth + (width + textWidth) * travel else (width - textWidth) / 2f
                        IntOffset(x.roundToInt(), 0)
                    }
                }
                .wrapContentSize(unbounded = true, align = Alignment.CenterStart)
                .onSizeChanged { textWidth = it.width },
        ) {
            if (style.enableRainbow) {
                Text(text, maxLines = 1, style = textStyle.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFF2509E), Color(0xFF308BCD))),
                    drawStyle = Stroke(width = 4f),
                    shadow = null,
                ))
            }
            Text(text, maxLines = 1, style = textStyle.copy(shadow = if (style.enableRainbow) null else textStyle.shadow))
        }
    }
}
