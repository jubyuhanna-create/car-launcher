package com.el7ajb.carlauncher

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlin.math.cos
import kotlin.math.sin

data class Theme(val id: String, val key: String, val color: Color)

val THEMES = listOf(
    Theme("car", "themeCar", Color.Unspecified),
    Theme("music", "themeMusic", Color.Unspecified),
    Theme("midnight", "themeMidnight", Color(0xFF6F8DB8)),
    Theme("ocean", "themeOcean", Color(0xFF2A8CF0)),
    Theme("emerald", "themeEmerald", Color(0xFF1FC08F)),
    Theme("crimson", "themeCrimson", Color(0xFFE8394A)),
    Theme("amber", "themeAmber", Color(0xFFFFA62B)),
    Theme("violet", "themeViolet", Color(0xFF9070F5)),
    Theme("ice", "themeIce", Color(0xFFC6DDF0)),
)

val LocalAccent = compositionLocalOf { Color(0xFF6F8DB8) }

/** Turns a paint colour into a glowing light colour that looks good on black. */
fun glowFrom(argb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return when {
        hsv[2] < 0.22f -> Color(0xFF7FA6D9)            // black / very dark paint -> cool blue light
        hsv[1] < 0.16f -> Color(0xFFC6DDF0)            // white / silver / grey -> ice light
        else -> {
            hsv[1] = hsv[1].coerceAtLeast(0.65f)
            hsv[2] = 0.95f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}

fun paletteGlow(bmp: Bitmap?): Color? {
    bmp ?: return null
    return try {
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val c = p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(0)))
        if (c == 0) null else glowFrom(c)
    } catch (_: Throwable) { null }
}

fun Color.mixWhite(f: Float): Color = Color(
    red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f, alpha
)

@Composable
fun SpeedArc(v: Int, unit: String, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((v / 200f).coerceIn(0f, 1f), tween(700), label = "speed")
    val col = when {
        v >= 130 -> Color(0xFFFF4D4D)
        v >= 100 -> Color(0xFFFFB84D)
        else -> accent
    }
    val d = LocalDensity.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.032f
            val inset = stroke * 2.2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            // ticks
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val rOut = this.size.minDimension / 2
            for (i in 0..20) {
                val a = Math.toRadians((135.0 + 270.0 * i / 20.0))
                val major = i % 5 == 0
                val r1 = rOut - (if (major) stroke * 1.6f else stroke * 1.0f)
                drawLine(
                    Color.White.copy(alpha = if (major) 0.35f else 0.14f),
                    Offset(c.x + (rOut * cos(a)).toFloat(), c.y + (rOut * sin(a)).toFloat()),
                    Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
                    strokeWidth = stroke * 0.35f
                )
            }
            drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0.002f) {
                drawArc(col.copy(alpha = 0.25f), 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke * 3.2f, cap = StrokeCap.Round))
                drawArc(col, 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v.toString(), fontSize = with(d) { (size * 0.34f).toSp() }, fontWeight = FontWeight.ExtraLight, color = Color(0xFFECEEF0), lineHeight = with(d) { (size * 0.36f).toSp() })
            Text(unit, fontSize = with(d) { (size * 0.065f).toSp() }, color = Color(0xFF7A7F86), letterSpacing = 3.sp)
        }
    }
}

@Suppress("unused")
fun Color.argb(): Int = toArgb()
