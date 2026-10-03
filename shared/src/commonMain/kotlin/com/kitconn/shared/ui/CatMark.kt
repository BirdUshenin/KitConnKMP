package com.kitconn.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

// Геометрия кота — та же, что в векторной иконке (viewport 108)
private val CatCenter = Offset(54f, 52.5f)
private const val CAT_UNITS = 60f
private const val EYE_Y = 57f
private const val EYE_R = 2.8f
private const val LEFT_EYE_X = 45.2f
private const val RIGHT_EYE_X = 62.8f

/** Кот-логотип. [wink] 0…1 прикрывает правый глаз. */
@Composable
fun CatMark(modifier: Modifier = Modifier, wink: Float = 0f) {
    Canvas(modifier) { drawCat(wink) }
}

fun DrawScope.drawCat(wink: Float) {
    val unit = size.minDimension / CAT_UNITS
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(unit, unit, pivot = Offset.Zero)
        translate(-CatCenter.x, -CatCenter.y)
    }) {
        val ears = Path().apply {
            moveTo(31f, 52f); lineTo(29f, 27f); lineTo(49f, 37f); close()
            moveTo(77f, 52f); lineTo(79f, 27f); lineTo(59f, 37f); close()
        }
        drawPath(ears, KitColors.Cyan)
        drawOval(KitColors.Cyan, Offset(30f, 36f), Size(48f, 42f))
        drawEye(LEFT_EYE_X, 1f)
        drawEye(RIGHT_EYE_X, 1f - 0.85f * wink)
    }
}

private fun DrawScope.drawEye(cx: Float, openness: Float) {
    val h = EYE_R * openness
    drawOval(Color(0xFF0C0D14), Offset(cx - EYE_R, EYE_Y - h), Size(EYE_R * 2, h * 2))
}
