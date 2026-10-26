package site.callmedraxxx.heat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Forecast
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** NOAA's strength bands for the Niño 3.4 anomaly, with the colour each one gets. */
private data class Band(val from: Double, val name: Int, val colour: Color)

private val BANDS get() = listOf(
    Band(Double.NEGATIVE_INFINITY, R.string.en_lanina, C.water),
    Band(-0.5, R.string.en_neutral, C.safe),
    Band(0.5, R.string.en_weak, C.caution),
    Band(1.0, R.string.en_moderate, C.extremeCaution),
    Band(1.5, R.string.en_strong, C.danger),
    Band(2.0, R.string.en_very_strong, C.extremeDanger),
)
private val EDGES = listOf(-1.5, -0.5, 0.5, 1.0, 1.5, 2.0, 3.5)

private fun bandOf(a: Double) = BANDS.last { a >= it.from }

/** A band's colour as readable text: the level ink for the warm bands. */
private fun bandInk(a: Double) = when {
    a >= 2.0 -> C.look.levelInk[4]; a >= 1.5 -> C.look.levelInk[3]; a >= 1.0 -> C.look.levelInk[2]; a >= 0.5 -> C.look.levelInk[1]
    a > -0.5 -> C.look.levelInk[0]; else -> C.water
}

/** Where an anomaly sits on the strength bar, which runs from -1.5 to +3.5 °C. */
private fun scalePos(a: Double) = ((a + 1.5) / 5.0).coerceIn(0.0, 1.0).toFloat()

/** How far a temperature is from normal, coloured the same way everywhere on the card. */
private fun offsetColour(d: Double) = when { d >= 1.5 -> C.danger; d >= 0.5 -> C.extremeCaution; d <= -0.5 -> C.waterLight; else -> C.safe }

/**
 * El Niño, the reason this app exists: this week's reading, its strength, six months of
 * readings, then what it means here. It shifts the odds of heat; it never decides a day.
 */
@Composable
fun ElNinoCard(app: AppState, f: Forecast) {
    val s = app.store
    val history = s.ninoHistory
    val now = history.lastOrNull()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.en_title), style = T.head, modifier = Modifier.weight(1f))
            if (now != null) Text(prettyWeek(now.first), style = T.small)
        }
        if (now == null) { Text(stringResource(R.string.en_loading), style = T.small); return@Panel }
        val band = bandOf(now.second)

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("%+.1f°".format(now.second), style = T.huge.copy(fontSize = 56.sp, lineHeight = 60.sp))
            Text(stringResource(band.name), style = T.head.copy(color = bandInk(now.second)), modifier = Modifier.padding(bottom = 12.dp))
        }
        StrengthBar(now.second)
        if (history.size >= 4) Spark(history.map { it.second })
        Text(stringResource(when {
            now.second >= 0.5 -> R.string.en_means_hot
            now.second <= -0.5 -> R.string.en_means_cool
            else -> R.string.en_means_neutral
        }), style = T.body.copy(color = C.text))

        // What it looks like here: today, then the next three months.
        val today = todayOffset(app, f)
        val outlook = s.seasonal(f.place.key)
        if (today != null || outlook.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                today?.let { Tile(stringResource(R.string.tab_today), it) }
                outlook.forEach { (m, d) -> Tile(monthName(m), d) }
            }
            Text(stringResource(R.string.en_vs_normal), style = T.small)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(label: String, d: Double) {
    val colour = offsetColour(d)
    Column(Modifier.weight(1f).background(colour.copy(alpha = .10f), RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = T.small)
        Text("%+.1f°".format(d), style = T.head.copy(color = colour, fontSize = 18.sp, fontWeight = FontWeight(400)))
    }
}

/** Today's high against the 1991-2020 normal for this date, if the record is in. */
private fun todayOffset(app: AppState, f: Forecast): Double? {
    val c = app.store.climate(f.place.key) ?: return null
    val high = f.dailyMax.getOrNull(f.days.indexOf(f.today())) ?: return null
    val normal = c.normalMax[f.today().dayOfYear]?.takeUnless { it.isNaN() } ?: return null
    return high - normal
}

private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** The six bands as one bar, with a ring at this week's reading. */
@Composable
private fun StrengthBar(a: Double) = Canvas(Modifier.fillMaxWidth().height(22.dp)) {
    val h = 6.dp.toPx()
    val y = size.height / 2
    BANDS.forEachIndexed { i, b ->
        val x0 = scalePos(EDGES[i]) * size.width
        val x1 = scalePos(EDGES[i + 1]) * size.width
        drawRoundRect(b.colour.copy(alpha = if (bandOf(a) == b) 1f else .3f), Offset(x0 + 1.5f, y - h / 2),
            Size(x1 - x0 - 3f, h), CornerRadius(h / 2))
    }
    val mx = scalePos(a) * size.width
    drawCircle(C.card, 7.dp.toPx(), Offset(mx, y))
    drawCircle(C.strong, 7.dp.toPx(), Offset(mx, y), style = Stroke(2.5.dp.toPx()))
}

/** 26 weeks of readings, with the 0.5 °C El Niño line dashed. */
@Composable
private fun Spark(values: List<Double>) = Canvas(Modifier.fillMaxWidth().height(48.dp)) {
    val lo = minOf(values.min(), 0.0) - 0.2
    val hi = maxOf(values.max(), 1.0) + 0.2
    fun x(i: Int) = i * size.width / (values.size - 1)
    fun y(v: Double) = ((1 - (v - lo) / (hi - lo)) * size.height).toFloat()
    var xx = 0f
    while (xx < size.width) { drawLine(C.muted.copy(alpha = .4f), Offset(xx, y(0.5)), Offset(xx + 6, y(0.5)), 1.5f); xx += 12 }
    val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
    val colours = Brush.horizontalGradient(values.map { bandOf(it).colour })
    val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
    drawPath(fill, colours, alpha = .16f)
    drawPath(line, colours, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(C.strong, 4.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
}

/** "21OCT2026" -> "21 Oct". */
private fun prettyWeek(w: String) = runCatching {
    "${w.take(2).trimStart('0')} ${w.substring(2, 3)}${w.substring(3, 5).lowercase()}"
}.getOrDefault(w)
