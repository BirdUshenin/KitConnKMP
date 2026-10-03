package com.kitconn.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitconn.shared.core.CountryCode

private val drawableFlags = setOf("DE", "IT", "PL", "NL", "FR", "RU", "US")

/** Круглый флаг, нарисованный кодом; для остальных стран — эмодзи в круге. */
@Composable
fun FlagView(country: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val code = CountryCode.code(country)
    if (code != null && code in drawableFlags) {
        Canvas(modifier.size(size).clip(CircleShape)) { drawFlag(code) }
    } else {
        Box(modifier.size(size).clip(CircleShape).background(Color(0xFF232739)), contentAlignment = Alignment.Center) {
            Text(CountryCode.flagEmoji(country), fontSize = (size.value * 0.62f).sp)
        }
    }
}

private fun DrawScope.drawFlag(code: String) {
    // Полотно флага шире круга: показываем его центральную часть (у США — левую, чтобы был виден синий угол)
    val h = size.height
    val w = h * 1.5f
    val left = if (code == "US") 0f else -(w - size.width) / 2f

    fun horizontal(vararg colors: Long) {
        val band = h / colors.size
        colors.forEachIndexed { i, c -> drawRect(Color(c), Offset(left, band * i), Size(w, band + 1f)) }
    }
    fun vertical(vararg colors: Long) {
        val band = w / colors.size
        colors.forEachIndexed { i, c -> drawRect(Color(c), Offset(left + band * i, 0f), Size(band + 1f, h)) }
    }

    when (code) {
        "DE" -> horizontal(0xFF000000, 0xFFDD0000, 0xFFFFCE00)
        "IT" -> vertical(0xFF009246, 0xFFFFFFFF, 0xFFCE2B37)
        "PL" -> horizontal(0xFFFFFFFF, 0xFFDC143C)
        "NL" -> horizontal(0xFFAE1C28, 0xFFFFFFFF, 0xFF21468B)
        "FR" -> vertical(0xFF0055A4, 0xFFFFFFFF, 0xFFEF4135)
        "RU" -> horizontal(0xFFFFFFFF, 0xFF0039A6, 0xFFD52B1E)
        "US" -> {
            drawRect(Color.White, Offset(left, 0f), Size(w, h))
            val stripe = h / 13f
            for (i in 0 until 13 step 2) drawRect(Color(0xFFB22234), Offset(left, stripe * i), Size(w, stripe + 1f))
            val cw = w * 0.45f
            val ch = stripe * 7
            drawRect(Color(0xFF3C3B6E), Offset(left, 0f), Size(cw, ch))
            // Звёзды упрощены до точек: на таком размере звезда не читается
            for (r in 0 until 4) for (c in 0 until 5) {
                drawCircle(Color.White, stripe * 0.32f, Offset(left + cw * (c + 0.5f) / 5, ch * (r + 0.5f) / 4))
            }
        }
    }
}
