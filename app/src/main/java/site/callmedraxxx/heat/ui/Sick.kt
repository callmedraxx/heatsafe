package site.callmedraxxx.heat.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Emergency
import site.callmedraxxx.heat.data.Locator

private val RED_FLAGS = listOf(R.string.s_confused, R.string.s_faint, R.string.s_hot_dry, R.string.s_vomit_cant_drink)
private val EXHAUSTION = listOf(R.string.s_sweat, R.string.s_dizzy, R.string.s_headache, R.string.s_cramps, R.string.s_pulse)

/** Heat exhaustion versus heat stroke, from the WHO/CDC first-aid signs. Guidance, not diagnosis. */
@Composable
fun Sick(app: AppState) {
    val ctx = LocalContext.current
    val ticked = remember { mutableStateListOf<Int>() }
    val cc = app.store.place?.cc?.ifBlank { null } ?: Locator.countryFromNetwork(ctx)
    val number = Emergency.number(cc)
    val stroke = ticked.any { it in RED_FLAGS }
    val exhaustion = !stroke && ticked.any { it in EXHAUSTION }
    // The advice sits under the list: bring it into view the moment it changes,
    // so nobody in a hurry misses it.
    val scroll = rememberScrollState()
    LaunchedEffect(stroke, exhaustion) {
        if (stroke || exhaustion) { withFrameNanos { }; scroll.animateScrollTo(scroll.maxValue) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sick_title), style = T.title, modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.sick_intro), style = T.body)

        Label(stringResource(R.string.sick_emergency_group))
        RED_FLAGS.forEach { Tick(it, it in ticked, C.danger) { on -> if (on) ticked += it else ticked -= it } }
        Label(stringResource(R.string.sick_exhaustion_group))
        EXHAUSTION.forEach { Tick(it, it in ticked, C.caution) { on -> if (on) ticked += it else ticked -= it } }

        when {
            stroke -> Panel(Modifier.border(1.dp, C.danger, RoundedCornerShape(20.dp))) {
                Text(stringResource(R.string.sick_stroke_title), style = T.head.copy(color = C.danger))
                Line(stringResource(R.string.sick_stroke_1, number))
                Line(stringResource(R.string.sick_stroke_2))
                Line(stringResource(R.string.sick_stroke_3))
                Line(stringResource(R.string.sick_stroke_4))
            }
            exhaustion -> Panel {
                Text(stringResource(R.string.sick_exh_title), style = T.head.copy(color = C.caution))
                listOf(R.string.sick_exh_1, R.string.sick_exh_2, R.string.sick_exh_3, R.string.sick_exh_4).forEach { Line(stringResource(it)) }
            }
            else -> Text(stringResource(R.string.sick_none), style = T.small)
        }

        BigButton(stringResource(R.string.call_number, number), fill = C.danger, textColor = onLevel(site.callmedraxxx.heat.heat.Level.DANGER)) {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
        Text(stringResource(R.string.sick_disclaimer), style = T.small, modifier = Modifier.padding(bottom = 14.dp))
    }
}

@Composable
private fun Tick(text: Int, on: Boolean, colour: Color, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) colour.copy(alpha = .12f) else C.card)
        .clickable { onChange(!on) }.padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) colour else C.line))
    Text(stringResource(text), style = T.body.copy(color = if (on) C.text else C.soft), modifier = Modifier.padding(start = 12.dp))
}
