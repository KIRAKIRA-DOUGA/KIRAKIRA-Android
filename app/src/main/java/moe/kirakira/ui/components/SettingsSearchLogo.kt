package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Cerasus LogoSettings: one opposing quarter-turn on entry. */
@Composable
internal fun SettingsSearchLogo(modifier: Modifier = Modifier) {
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val progress = remember { Animatable(if (durationScale?.scaleFactor == 0f) 1f else 0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1_000, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)))
    }
    val gear = remember { PathParser().parsePathString(GEAR_PATH).toPath() }
    val shadow = remember { PathParser().parsePathString(LONG_SHADOW_PATH).toPath() }
    val gearShape = remember(gear) {
        GenericShape { size, _ ->
            val outline = Path().apply {
                addPath(gear)
                transform(Matrix().apply { scale(size.width / 512f, size.height / 512f) })
            }
            addPath(outline)
        }
    }
    Box(modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(128.dp).graphicsLayer {
                rotationZ = -90f * (1f - progress.value)
                shape = gearShape
                shadowElevation = 4.dp.toPx()
            },
        ) {
            scale(size.width / 512f, size.height / 512f, pivot = Offset.Zero) {
                drawPath(gear, settingsGearColor)
                clipPath(gear) {
                    // The original mask rotates, but its long shadow keeps the same light direction.
                    rotate(90f * (1f - progress.value), pivot = Offset(256f, 256f)) {
                        drawPath(shadow, settingsGearShadowColor)
                    }
                }
            }
        }
        Canvas(
            Modifier.size(64.dp).graphicsLayer {
                shape = CircleShape
                shadowElevation = 2.dp.toPx()
            },
        ) {
            scale(size.width / 256f, size.height / 256f, pivot = Offset.Zero) {
                rotate(90f * (1f - progress.value), pivot = Offset(128f, 128f)) {
                    settingsRingColors.forEachIndexed { index, color ->
                        drawArc(
                            color = color,
                            startAngle = index * 120f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(18f, 18f),
                            size = Size(220f, 220f),
                            style = Stroke(36f),
                        )
                    }
                }
                drawCircle(settingsMintColor, radius = 55f, center = Offset(128f, 128f))
            }
        }
    }
}

private val settingsGearColor = Color(0xFF688491)
private val settingsGearShadowColor = Color(0xFF546A74)
private val settingsMintColor = Color(0xFFAAFFEC)
private val settingsRingColors = listOf(Color(0xFF92A6B0), Color(0xFFD2DADE), settingsMintColor)

private const val GEAR_PATH = "M414.507 276.907C415.36 270.08 416 263.253 416 256C416 248.747 415.36 241.92 414.507 235.093L459.52 199.893C463.573 196.693 464.64 190.933 462.08 186.24L419.413 112.427C416.853 107.733 411.093 106.027 406.4 107.733L353.28 129.067C342.187 120.533 330.24 113.493 317.227 108.16L309.12 51.6267C308.48 46.5068 304 42.6667 298.667 42.6667H213.333C208 42.6667 203.52 46.5068 202.88 51.6267L194.773 108.16C181.76 113.493 169.813 120.747 158.72 129.067L105.6 107.733C100.693 105.813 95.1467 107.733 92.5867 112.427L49.92 186.24C47.1467 190.933 48.4267 196.693 52.48 199.893L97.4933 235.093C96.64 241.92 96 248.96 96 256C96 263.04 96.64 270.08 97.4933 276.907L52.48 312.107C48.4267 315.307 47.36 321.067 49.92 325.76L92.5867 399.573C95.1467 404.267 100.907 405.973 105.6 404.267L158.72 382.933C169.813 391.467 181.76 398.507 194.773 403.84L202.88 460.373C203.52 465.493 208 469.333 213.333 469.333H298.667C304 469.333 308.48 465.493 309.12 460.373L317.227 403.84C330.24 398.507 342.187 391.253 353.28 382.933L406.4 404.267C411.307 406.187 416.853 404.267 419.413 399.573L462.08 325.76C464.64 321.067 463.573 315.307 459.52 312.107L414.507 276.907Z"
private const val LONG_SHADOW_PATH = "M165.64 346.659C142.389 323.484 128 291.422 128 256C128 185.308 185.308 128 256 128C291.642 128 323.881 142.568 347.089 166.073L516.363 335.348L335.346 516.365L165.64 346.66L165.64 346.659Z"
