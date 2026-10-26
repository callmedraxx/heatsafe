package site.callmedraxxx.heat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

/** The ironbow scale thermal cameras use: cold black-violet up to white-hot. */
private val IRONBOW = listOf(
    Color(0xFF07000F), Color(0xFF2B0A5E), Color(0xFF7A0C7A), Color(0xFFC21A4E),
    Color(0xFFF54A12), Color(0xFFFF9A1F), Color(0xFFFFD866), Color(0xFFFFF8E1),
)

/** A colour along the ironbow scale, 0 cold to 1 white-hot. */
fun ironbow(t: Float): Color {
    val x = t.coerceIn(0f, 0.999f) * (IRONBOW.size - 1)
    val i = x.toInt()
    return lerp(IRONBOW[i], IRONBOW[i + 1], x - i)
}

/** Where a heat index sits on the scale: 15 °C is cold, 50 °C is white-hot. */
fun heatPos(heatIndex: Double) = ((heatIndex - 15) / 35).coerceIn(0.0, 1.0).toFloat()

/**
 * A thermal-camera view of the heat right now: warm masses drifting slowly through a cold
 * field, the hottest of them as hot as today feels. Five soft blobs, each on its own slow
 * orbit, so it never repeats visibly and stays cheap to draw.
 */
@Composable
fun ThermalField(heatIndex: Double, modifier: Modifier = Modifier) {
    val scale = Motion.scale()
    val t by rememberInfiniteTransition(label = "thermal").animateFloat(0f, 1f,
        infiniteRepeatable(tween(if (scale == 0f) 1 else 24_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val hot = heatPos(heatIndex)
    Canvas(modifier) {
        drawRect(ironbow(hot * 0.18f))
        val w = size.width; val h = size.height
        val tau = (2 * Math.PI).toFloat()
        // (x, y, radius, orbit, heat) per blob: the first is the hot core, the rest cooler bodies around it.
        val blobs = listOf(
            floatArrayOf(.62f, .42f, .70f, 1f, 1f), floatArrayOf(.22f, .70f, .55f, -1.3f, .72f),
            floatArrayOf(.85f, .85f, .45f, 1.7f, .6f), floatArrayOf(.35f, .18f, .40f, -2.1f, .55f), floatArrayOf(.75f, .10f, .35f, 2.6f, .45f),
        )
        blobs.forEach { b ->
            val a = t * tau * b[3]
            val c = Offset(w * (b[0] + .08f * sin(a) + .05f * cos(a * .7f)), h * (b[1] + .10f * cos(a) + .04f * sin(a * 1.3f)))
            val r = h * b[2]
            val k = hot * b[4]
            drawCircle(Brush.radialGradient(listOf(ironbow(k), ironbow(k * .55f).copy(alpha = .55f), Color.Transparent), c, r), r, c)
        }
        // The vignette a lens leaves at the edges.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA000000)), Offset(w / 2, h / 2), maxOf(w, h) * .75f))
    }
}
