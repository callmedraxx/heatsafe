package site.callmedraxxx.heat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Forecast
import site.callmedraxxx.heat.data.Weather
import site.callmedraxxx.heat.heat.Activity
import site.callmedraxxx.heat.heat.Advice
import site.callmedraxxx.heat.heat.ExcessHeat
import site.callmedraxxx.heat.heat.HeatMath
import site.callmedraxxx.heat.heat.Heatwave
import site.callmedraxxx.heat.heat.Level
import site.callmedraxxx.heat.remind.Engine
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun Today(app: AppState, onSettings: () -> Unit, onWater: () -> Unit, onFamily: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = app.version
    val f = app.forecast
    if (f == null) { Loading(app); return }
    val s = app.store
    val hr = s.profile.higherRisk
    val h = f.current() ?: return
    val level = h.level(hr)

    val shown = rememberShown()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(app, f, onSettings)
        Box(Modifier.rise(shown, 0)) { Headline(level) }
        Box(Modifier.rise(shown, 1)) { if (C.look.dark) ThermalHero(level, h.heatIndex, h.temp, h.rh) else LevelCard(level, h.heatIndex, h.temp, h.rh) }
        Box(Modifier.rise(shown, 2)) { PlanCard(app, f, hr) }
        Row(Modifier.rise(shown, 3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(stringResource(R.string.cups_of, s.cupsToday(), Engine.targetCups(s, f)), nextCup(s, f) ?: stringResource(R.string.cups_label), onWater)
            SecondTile(app, f, hr)
        }
        Box(Modifier.rise(shown, 4)) { ElNinoCard(app, f) }
        Box(Modifier.rise(shown, 5)) { Tips(app, f, level, h) }
        s.familyPlaces.forEachIndexed { i, fp -> Box(Modifier.rise(shown, 6 + i)) { FamilyRow(fp, onFamily) } }
        Updated(app, f)
    }
}

@Composable
private fun Header(app: AppState, f: Forecast, onSettings: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = onSettings), verticalAlignment = Alignment.CenterVertically) {
            if (app.store.followMe) Icon(Icons.Rounded.LocationOn, stringResource(R.string.following), tint = C.accent,
                modifier = Modifier.padding(end = 4.dp).size(18.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = C.text, fontWeight = androidx.compose.ui.text.font.FontWeight(C.look.bodyBold))) { append(f.place.name) }
                if (f.place.country.isNotBlank()) append(", ${f.place.country}")
            }, style = T.body.copy(color = C.muted), maxLines = 1)
        }
        Text(f.today().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = T.body.copy(color = C.muted))
        IconTap(Icons.Rounded.Settings, stringResource(R.string.settings), onClick = onSettings)
    }

/** "It's dangerously hot today." with the words that matter set on the level's colour. */
@Composable
private fun Headline(level: Level) {
    val words = stringResource(when (level) {
        Level.SAFE -> R.string.h_safe
        Level.CAUTION -> R.string.h_caution
        Level.EXTREME_CAUTION -> R.string.h_ec
        Level.DANGER -> R.string.h_danger
        Level.EXTREME_DANGER -> R.string.h_xd
    })
    val full = stringResource(R.string.headline, words)
    val i = full.indexOf(words)
    Text(buildAnnotatedString {
        append(full.substring(0, i))
        withStyle(SpanStyle(background = levelColor(level), color = onLevel(level))) { append(" $words ") }
        append(full.substring(i + words.length))
    }, style = if (Screen.compact) T.title.copy(fontSize = 28.sp, lineHeight = 36.sp) else T.title.copy(fontSize = 34.sp, lineHeight = 44.sp))
}

@Composable
private fun LevelCard(level: Level, feels: Double, air: Double, rh: Double) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(levelColor(level)).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically) { Reading(level, feels, air, rh, onLevel(level)) }

/** The same reading over a live thermal-camera field: the dark look's hero. */
@Composable
private fun ThermalHero(level: Level, feels: Double, air: Double, rh: Double) =
    Box(Modifier.fillMaxWidth().height((Screen.width * 0.58f).coerceIn(170.dp, if (Screen.short) 190.dp else 240.dp)).clip(RoundedCornerShape(26.dp))) {
        ThermalField(feels, Modifier.matchParentSize())
        Row(Modifier.matchParentSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Reading(level, feels, air, rh, Color.White)
        }
    }

/** The big number, the level, and one line about the air, in [ink]. */
@Composable
private fun RowScope.Reading(level: Level, feels: Double, air: Double, rh: Double, ink: Color) {
    val ctx = LocalContext.current
    val n = countTo(feels.roundToInt())
    Text(buildAnnotatedString {
        append(n.toString())
        withStyle(SpanStyle(fontSize = if (Screen.compact) 30.sp else 40.sp, baselineShift = BaselineShift(0.6f))) { append("°") }
    }, style = T.huge.copy(color = ink, fontSize = if (Screen.compact) 64.sp else 84.sp, lineHeight = if (Screen.compact) 64.sp else 84.sp))
    Column(Modifier.padding(start = 18.dp)) {
        Text(levelName(ctx, level), style = T.head.copy(color = ink, fontSize = 22.sp, lineHeight = 26.sp))
        Text(stringResource(when { rh < 30 -> R.string.air_dry; rh > 65 -> R.string.air_humid; else -> R.string.air_plain },
            feels.roundToInt(), air.roundToInt()), style = T.body.copy(color = ink.copy(alpha = .9f)))
    }
}

/** When to stay in, when it's fine to go out, and the whole day as one bar. */
@Composable
private fun PlanCard(app: AppState, f: Forecast, hr: Boolean) {
    val ctx = LocalContext.current
    val today = f.on(f.today())
    val nowHour = f.nowLocal().hour
    val danger = today.filter { it.level(hr) >= Level.DANGER }
    val hot = today.filter { it.level(hr) >= Level.EXTREME_CAUTION }
    val (title, sub) = when {
        danger.isNotEmpty() && danger.last().time.hour >= nowHour ->
            stringResource(R.string.plan_stay, clock(ctx, danger.first().time.hour), clock(ctx, danger.last().time.hour + 1)) to
                hot.let { stringResource(R.string.plan_go, clock(ctx, it.first().time.hour), clock(ctx, it.last().time.hour + 1)) }
        hot.isNotEmpty() && hot.last().time.hour >= nowHour ->
            stringResource(R.string.plan_shade, clock(ctx, hot.first().time.hour), clock(ctx, hot.last().time.hour + 1)) to null
        hot.isNotEmpty() -> stringResource(R.string.plan_over) to null
        else -> stringResource(R.string.plan_fine) to null
    }
    Panel(outlined = true) {
        Text(title, style = T.head)
        sub?.let { Text(it, style = T.body.copy(color = C.muted)) }
        DayBar(today, hr, nowHour, Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(R.string.midnight, R.string.noon, R.string.midnight).forEachIndexed { i, r ->
                Text(stringResource(r), style = T.small, modifier = Modifier.weight(1f),
                    textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 1 -> androidx.compose.ui.text.style.TextAlign.Center
                        else -> androidx.compose.ui.text.style.TextAlign.End })
            }
        }
        Engine.hotNight(app.store, f)?.let { Text(stringResource(R.string.tonight_hot, it.roundToInt()), style = T.body.copy(color = C.text)) }
    }
}

/** An hour as people say it: "noon", "4 pm", "10 am". */
private fun clock(ctx: android.content.Context, hour: Int): String = when (val h = Math.floorMod(hour, 24)) {
    0 -> ctx.getString(R.string.midnight)
    12 -> ctx.getString(R.string.noon)
    else -> if (android.text.format.DateFormat.is24HourFormat(ctx)) "%02d:00".format(h)
        else ctx.getString(if (h < 12) R.string.am else R.string.pm, if (h > 12) h - 12 else h)
}

@Composable
private fun RowScope.Tile(big: String, small: String, onClick: (() -> Unit)? = null) =
    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(C.card)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(big, style = T.title.copy(fontSize = 26.sp, lineHeight = 30.sp), maxLines = 1)
        Text(small, style = T.body.copy(color = C.muted, fontSize = 14.sp, lineHeight = 18.sp))
    }

/** A heatwave when there is one; otherwise the hottest hour coming. */
@Composable
private fun RowScope.SecondTile(app: AppState, f: Forecast, hr: Boolean) {
    val ctx = LocalContext.current
    val c = app.store.climate(f.place.key)
    val days = c?.let { Weather.heatwaves(f, it) }.orEmpty()
    val today = days.firstOrNull()?.second
    if (c != null && today != null && today != Heatwave.NONE) {
        var day = 1
        val i = f.days.indexOf(f.today())
        while (i - day >= 30 && ExcessHeat.ehf(f.dailyMean, i - day, c.t95) > 0) day++
        val left = days.drop(1).takeWhile { it.second != Heatwave.NONE }.size
        Tile(stringResource(R.string.hw_day, day), stringResource(R.string.hw_of, day + left))
        return
    }
    val soon = days.drop(1).firstOrNull { it.second != Heatwave.NONE }
    if (soon != null) {
        Tile(soon.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), stringResource(R.string.hw_coming_tile))
        return
    }
    val peak = f.next48().maxByOrNull { it.heatIndex } ?: return
    val whenText = stringResource(if (peak.time.toLocalDate() == f.today()) R.string.today_word else R.string.tomorrow_word)
    Tile("${peak.heatIndex.roundToInt()}°", stringResource(R.string.peak_tile, whenText, clock(ctx, peak.time.hour)))
}

/** At most three things to do, the ones that fit this person first. */
@Composable
private fun Tips(app: AppState, f: Forecast, level: Level, h: site.callmedraxxx.heat.data.Hour) {
    val s = app.store
    val outside = s.activity != Activity.HOME || s.profile.outdoorWorker
    val lines = buildList {
        add(stringResource(advice(level).first()))
        if (s.profile.medicine && level >= Level.CAUTION) add(stringResource(R.string.adv_medicine))
        if (outside && level >= Level.CAUTION) add(stringResource(R.string.adv_outside))
        if (s.activity == Activity.WORK || s.profile.outdoorWorker)
            Advice.workRest(h.wbgtSun)?.let { (w, r) -> add(stringResource(R.string.adv_workrest, w, r)) }
        if (HeatMath.fansStopHelping(h.temp, h.rh)) add(stringResource(R.string.adv_dry_fans))
        if (level >= Level.DANGER || Engine.hotNight(s, f) != null) add(stringResource(R.string.adv_powercut))
        advice(level).drop(1).forEach { add(stringResource(it)) }
    }.take(3)
    Panel {
        Text(stringResource(R.string.what_to_do), style = T.head)
        lines.forEach { Line(it) }
    }
}

/** Someone the person looks out for, in the same dark band as the mockup. */
@Composable
private fun FamilyRow(fp: site.callmedraxxx.heat.data.FamilyPlace, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val ink = if (C.look.dark) C.text else C.bg
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.strong).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.caution), contentAlignment = Alignment.Center) {
            Text(fp.label.take(1).uppercase(), style = T.head.copy(color = onLevel(Level.CAUTION), fontFamily = C.look.display))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.who_in, fp.label, fp.place.name), style = T.body.copy(color = ink))
            val max = fp.maxHeatIndex
            Text(if (max == null) stringResource(R.string.place_unknown)
                else stringResource(R.string.place_today, levelName(ctx, HeatMath.level(max)), max.roundToInt()),
                style = T.small.copy(color = ink.copy(alpha = .72f)))
        }
    }
}

@Composable
private fun Updated(app: AppState, f: Forecast) {
    val age = (System.currentTimeMillis() - f.fetchedAt) / 3_600_000L
    Text(if (age >= 3) stringResource(R.string.stale, age.toInt()) else stringResource(R.string.updated,
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(f.fetchedAt))),
        style = T.small, modifier = Modifier.clickable { app.refresh() }.padding(bottom = 16.dp))
}

fun advice(l: Level): List<Int> = when (l) {
    Level.SAFE -> listOf(R.string.adv_safe_1, R.string.adv_safe_2)
    Level.CAUTION -> listOf(R.string.adv_caution_1, R.string.adv_caution_2)
    Level.EXTREME_CAUTION -> listOf(R.string.adv_ec_1, R.string.adv_ec_2, R.string.adv_ec_3)
    Level.DANGER -> listOf(R.string.adv_danger_1, R.string.adv_danger_2, R.string.adv_danger_3)
    Level.EXTREME_DANGER -> listOf(R.string.adv_xd_1, R.string.adv_xd_2, R.string.adv_xd_3)
}

@Composable
fun Loading(app: AppState) = Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
    if (app.failed && !app.loading) {
        Text(stringResource(R.string.load_failed), style = T.body)
        BigButton(stringResource(R.string.retry), Modifier.padding(top = 18.dp)) { app.refresh() }
    } else Text(stringResource(R.string.loading, app.store.place?.name ?: ""), style = T.body)
}
