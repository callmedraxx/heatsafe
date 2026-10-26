package site.callmedraxxx.heat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.callmedraxxx.heat.data.Hour
import site.callmedraxxx.heat.heat.HeatMath

private val Round = RoundedCornerShape(22.dp)

/** A card. [outlined] gives it the strong ink edge the plan card uses. */
@Composable
fun Panel(modifier: Modifier = Modifier, outlined: Boolean = false, fill: Color = C.card,
          content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().clip(Round).background(fill)
        .then(if (outlined) Modifier.border(2.dp, C.strong, Round) else Modifier)
        .padding(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp), content = content,
)

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) = Text(text, style = T.small.copy(color = color),
    modifier = modifier.clip(RoundedCornerShape(99.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 6.dp))

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, fill: Color = C.accent, textColor: Color = C.onAccent,
              height: Dp = 64.dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
    modifier.fillMaxWidth().pressScale(press).height(height).clip(RoundedCornerShape(26.dp)).background(fill)
        .clickable(press, null, onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(text, style = T.button.copy(color = textColor, fontFamily = C.look.display, fontWeight = androidx.compose.ui.text.font.FontWeight(C.look.displayWeight.coerceAtLeast(600))), textAlign = TextAlign.Center) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
    modifier.fillMaxWidth().pressScale(press).height(56.dp).clip(RoundedCornerShape(22.dp)).border(2.dp, C.strong, RoundedCornerShape(22.dp))
        .clickable(press, null, onClick = onClick),
    contentAlignment = Alignment.Center,
) { Text(text, style = T.button.copy(color = C.text)) }
}

/** A Material icon with a full 48 dp touch target. */
@Composable
fun IconTap(icon: ImageVector, description: String, tint: Color = C.muted, onClick: () -> Unit) =
    IconButton(onClick) { Icon(icon, description, tint = tint) }

@Composable
fun Label(text: String) = Text(text, style = T.label)

@Composable
fun Line(text: String, modifier: Modifier = Modifier) = Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
    Box(Modifier.padding(top = 9.dp, end = 10.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(C.accent))
    Text(text, style = T.body)
}

/** Today's 24 hours as one bar, each hour in its level's colour, with a tick at now. */
@Composable
fun DayBar(hours: List<Hour>, higherRisk: Boolean, nowHour: Int, modifier: Modifier = Modifier) =
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        if (hours.isEmpty()) return@Canvas
        val w = size.width / 24
        val r = size.height / 2
        hours.forEach { h ->
            drawRect(levelColor(HeatMath.level(h.heatIndex, higherRisk)), Offset(h.time.hour * w, 0f), Size(w + 0.5f, size.height))
        }
        // Round the ends by painting the background over the square corners.
        val edge = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(edge, C.card)
        val x = (nowHour + .5f) * w
        drawLine(C.strong, Offset(x, -2f), Offset(x, size.height + 2f), 3.dp.toPx())
    }

/** The day's water as cups: full ones blue, the rest still to drink. */
@Composable
fun Cups(drunk: Int, target: Int, modifier: Modifier = Modifier) {
    val perRow = if (Screen.wide) 7 else 5
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until target).chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i -> Cup(i < drunk, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One cup. Water rises into it when it is drunk. */
@Composable
private fun Cup(full: Boolean, modifier: Modifier) {
    val fill by animateFloatAsState(if (full) 1f else 0f, tween(Motion.ms(600), easing = Motion.ease), label = "cup")
    Canvas(modifier.aspectRatio(40f / 48f)) {
        val sx = size.width / 40f; val sy = size.height / 48f
        val cup = Path().apply {
            moveTo(6 * sx, 6 * sy); lineTo(34 * sx, 6 * sy); lineTo(30 * sx, 44 * sy); lineTo(10 * sx, 44 * sy); close()
        }
        drawPath(cup, C.card)
        if (fill > 0f) clipPath(cup) { drawRect(C.water, Offset(0f, size.height * (1 - fill)), Size(size.width, size.height * fill)) }
        drawPath(cup, C.strong, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    }
}
