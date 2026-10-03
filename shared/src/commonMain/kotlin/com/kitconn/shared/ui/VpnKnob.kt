package com.kitconn.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitconn.shared.vpn.VpnState
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Углы отсчитываются по часовой стрелке от направления «вверх»
private const val ANGLE_OFF = -50f
private const val ANGLE_ON = 50f
private const val ANGLE_MID = (ANGLE_OFF + ANGLE_ON) / 2f

/**
 * Ручка-переключатель Off/On. Положение всегда следует за [state]: если разрешение VPN
 * не выдано и состояние не сменилось, ручка сама вернётся в «Off».
 */
@Composable
fun VpnKnob(
    state: VpnState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 220.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val toggle by rememberUpdatedState(onToggle)
    val isOn = state != VpnState.DISCONNECTED
    val targetAngle = if (isOn) ANGLE_ON else ANGLE_OFF

    // Стартуем из «Off», чтобы при появлении экрана ручка плавно докручивалась до текущего положения
    val angle = remember { Animatable(ANGLE_OFF) }
    val spec = remember { spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) }

    LaunchedEffect(targetAngle) { angle.animateTo(targetAngle, spec) }

    val indicator = when (state) {
        VpnState.CONNECTED -> KitColors.Green
        VpnState.CONNECTING -> KitColors.Cyan
        VpnState.DISCONNECTED -> KitColors.Off
    }

    Canvas(
        modifier = modifier
            .size(knobSize)
            .pointerInput(isOn) {
                detectTapGestures {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    toggle()
                }
            }
            .pointerInput(isOn) {
                val boxSize = size
                detectDragGestures(
                    onDragEnd = {
                        val passedMid = if (isOn) angle.value < ANGLE_MID else angle.value > ANGLE_MID
                        scope.launch {
                            if (passedMid) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggle()
                            }
                            // Если состояние не сменится (отказ в разрешении), ручка вернётся сама
                            angle.animateTo(targetAngle, spec)
                        }
                    },
                    onDragCancel = { scope.launch { angle.animateTo(targetAngle, spec) } },
                ) { change, _ ->
                    val dx = change.position.x - boxSize.width / 2f
                    val dy = change.position.y - boxSize.height / 2f
                    val deg = (atan2(dx.toDouble(), -dy.toDouble()) * 180.0 / PI).toFloat()
                    scope.launch { angle.snapTo(deg.coerceIn(ANGLE_OFF, ANGLE_ON)) }
                }
            },
    ) {
        drawKnob(angle.value, indicator)
    }
}

private fun DrawScope.drawKnob(angleDeg: Float, indicatorColor: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f * 0.86f
    val shadowCenter = center + Offset(0f, radius * 0.12f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x99000000), Color.Transparent),
            center = shadowCenter,
            radius = radius * 1.25f,
        ),
        radius = radius * 1.25f,
        center = shadowCenter,
    )

    rotate(angleDeg, center) {
        val body = gearPath(center, radius, radius * 0.93f, 28)
        drawPath(
            path = body,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3A3E52), Color(0xFF1A1C28), Color(0xFF10111A)),
                center = center - Offset(radius * 0.25f, radius * 0.3f),
                radius = radius * 1.4f,
            ),
        )
        drawPath(body, Color(0xFF1A1C28), style = Stroke(width = radius * 0.03f, join = StrokeJoin.Round))
        drawCircle(Color(0x22FFFFFF), radius * 0.8f, center, style = Stroke(width = 1.5f))

        val barW = radius * 0.1f
        val barH = radius * 0.3f
        val barTopLeft = Offset(center.x - barW / 2f, center.y - radius * 0.82f)
        drawRoundRect(
            color = indicatorColor.copy(alpha = 0.25f),
            topLeft = barTopLeft - Offset(barW * 0.4f, barW * 0.4f),
            size = Size(barW * 1.8f, barH + barW * 0.8f),
            cornerRadius = CornerRadius(barW),
        )
        drawRoundRect(indicatorColor, barTopLeft, Size(barW, barH), CornerRadius(barW / 2f))

        val capR = radius * 0.3f
        drawPath(
            gearPath(center, capR, capR * 0.9f, 22),
            Brush.sweepGradient(
                colors = listOf(Color(0xFFE9EDF5), Color(0xFF8E96A8), Color(0xFFF7F9FC), Color(0xFF7D8598), Color(0xFFE9EDF5)),
                center = center,
            ),
        )
        drawCircle(Color(0x33000000), capR * 0.55f, center, style = Stroke(width = 1.5f))
    }
}

/** Контур шестерни: [teeth] зубьев между радиусами [outer] и [inner]. */
private fun gearPath(center: Offset, outer: Float, inner: Float, teeth: Int): Path {
    val path = Path()
    val step = (2 * PI / teeth).toFloat()
    for (i in 0 until teeth) {
        val a = i * step
        val points = listOf(a to inner, a + step * 0.2f to outer, a + step * 0.5f to outer, a + step * 0.7f to inner)
        points.forEachIndexed { index, (ang, r) ->
            val x = center.x + r * sin(ang)
            val y = center.y - r * cos(ang)
            if (i == 0 && index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    path.close()
    return path
}
