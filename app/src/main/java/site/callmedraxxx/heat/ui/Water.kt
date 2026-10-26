package site.callmedraxxx.heat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Forecast
import site.callmedraxxx.heat.data.LogEntry
import site.callmedraxxx.heat.data.Store
import site.callmedraxxx.heat.heat.Activity
import site.callmedraxxx.heat.heat.Advice
import site.callmedraxxx.heat.remind.Engine

/** When the next cup is due, or null when no reminder is running. Shown on both Today and Water. */
@Composable
fun nextCup(s: Store, f: Forecast): String? {
    if (s.pausedToday()) return null
    val gap = Engine.interval(s, f) ?: return null
    val mins = ((s.lastDrink() + gap * 60_000L - System.currentTimeMillis()) / 60_000L).toInt()
    return if (mins <= 0) stringResource(R.string.next_cup_now) else stringResource(R.string.next_cup_in, mins)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Water(app: AppState) {
    @Suppress("UNUSED_VARIABLE") val v = app.version
    val f = app.forecast
    if (f == null) { Loading(app); return }
    val ctx = LocalContext.current
    val s = app.store
    val h = f.current() ?: return
    val level = h.level(s.profile.higherRisk)
    val gap = Engine.interval(s, f)
    val cups = s.cupsToday()
    val target = Engine.targetCups(s, f)
    val fluidLimit = s.profile.fluidLimit
    fun log(kind: String) { s.addLog(kind); app.touched() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_water), style = T.head, modifier = Modifier.weight(1f))
            Text(nextCup(s, f) ?: stringResource(if (s.pausedToday()) R.string.water_paused else R.string.water_pace_none),
                style = T.body.copy(color = C.muted))
        }

        if (!fluidLimit) {
            val n = countTo(cups)
            Text(if (cups >= target) stringResource(R.string.water_done, n) else pluralStringResource(R.plurals.water_headline, n, n, target), style = T.title)
            Cups(cups, target)
            BigButton(stringResource(R.string.drank_cup), fill = C.water, textColor = C.onWater, height = 72.dp) { log(LogEntry.WATER) }
        } else Text(stringResource(R.string.fluid_note), style = T.title.copy(fontSize = 24.sp))
        QuietButton(stringResource(R.string.cooled_down)) { log(LogEntry.COOL) }

        // What kind of day: the pace follows it.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Activity.HOME to R.string.activity_home, Activity.OUT to R.string.activity_out, Activity.WORK to R.string.activity_work)
                .forEach { (a, label) ->
                    val on = s.activity == a
                    Text(stringResource(label), style = T.small.copy(color = if (on) C.onWater else C.text), maxLines = 1, softWrap = false,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) C.water else C.card)
                            .clickable { s.activity = a; app.touched() }.padding(horizontal = 14.dp, vertical = 12.dp))
                }
        }
        if (gap != null) Text(stringResource(R.string.water_pace, gap), style = T.small)

        Panel {
            Text(stringResource(R.string.cool_down), style = T.head)
            Text(tipText(ctx, Advice.coolingTip(level, h.temp, h.rh, cups)), style = T.body)
        }
        if (!fluidLimit) Text(stringResource(R.string.cup_note), style = T.small)

        Text(stringResource(if (s.pausedToday()) R.string.resume else R.string.pause_today), style = T.label.copy(color = C.accent),
            modifier = Modifier.clickable {
                s.pausedOn = if (s.pausedToday()) null else java.time.LocalDate.now().toString(); app.touched()
            }.padding(vertical = 14.dp))
    }
}

