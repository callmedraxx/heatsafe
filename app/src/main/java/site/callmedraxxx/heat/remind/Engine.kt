package site.callmedraxxx.heat.remind

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telephony.SmsManager
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.ui.tipText
import site.callmedraxxx.heat.data.FamilyPlace
import site.callmedraxxx.heat.data.Forecast
import site.callmedraxxx.heat.data.Store
import site.callmedraxxx.heat.data.Weather
import site.callmedraxxx.heat.heat.Advice
import site.callmedraxxx.heat.heat.HeatMath
import site.callmedraxxx.heat.heat.Level
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Everything that runs while the app is closed. Alarms wake [AlarmReceiver], which
 * calls in here; nothing leaves the phone except Open-Meteo/NOAA requests and, if the
 * person set it up, one text to their family contact.
 */
object Engine {
    const val REFRESH = "site.callmedraxxx.heat.REFRESH"
    const val REMIND = "site.callmedraxxx.heat.REMIND"
    const val MORNING = "site.callmedraxxx.heat.MORNING"
    const val PRE_DANGER = "site.callmedraxxx.heat.PRE_DANGER"
    const val ESCALATE = "site.callmedraxxx.heat.ESCALATE"
    const val EVENING = "site.callmedraxxx.heat.EVENING"

    /** A night counts as hot when it never feels cooler than this (°C heat index): the room won't cool down. */
    private const val HOT_NIGHT = 29.0

    private const val HOUR = 3_600_000L
    private const val MIN = 60_000L
    /** "Are you OK?" goes out after this long without any tap on a Danger day. */
    private const val SILENCE_BEFORE_CHECKIN = 2 * HOUR
    private const val WAIT_BEFORE_TEXT = 30 * MIN

    /** Fetch fresh weather (and the climate record and El Niño when due), then plan. */
    fun refresh(ctx: Context) {
        val s = Store(ctx)
        val place = s.place ?: return
        runCatching { s.saveForecast(Weather.forecastRaw(place), System.currentTimeMillis()) }
        if (s.climate(place.key) == null) runCatching { s.saveClimate(place.key, Weather.climate(place)) }
        if (s.ninoHistory.isEmpty() || System.currentTimeMillis() - s.ninoAt > 24 * HOUR) runCatching { s.ninoHistory = Weather.ninoHistory() }
        // The 3-month outlook changes slowly: once a day is plenty.
        val f = s.forecast()
        val c = s.climate(place.key)
        if (f != null && c != null && (s.seasonal(place.key).size < 3 || System.currentTimeMillis() - s.seasonalAt(place.key) > 24 * HOUR))
            runCatching { s.saveSeasonal(place.key, Weather.seasonal(place, f, c)) }
        s.familyPlaces = s.familyPlaces.map { fp ->
            runCatching {
                val f = Weather.parseForecast(fp.place, Weather.forecastRaw(fp.place), System.currentTimeMillis())
                fp.copy(maxHeatIndex = f.on(f.today()).maxOfOrNull { it.heatIndex }, checkedAt = System.currentTimeMillis())
            }.getOrDefault(fp)
        }
        plan(ctx)
    }

    /** Set every alarm from what is known now. Safe to call as often as you like. */
    fun plan(ctx: Context) {
        val s = Store(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        // Weather every 2 hours, give or take, so reminders follow the real heat.
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 2 * HOUR, 2 * HOUR, pi(ctx, REFRESH))
        val f = s.forecast() ?: return
        val now = System.currentTimeMillis()

        // The next water reminder: from the last cup, at the pace the heat right now calls for.
        val gap = interval(s, f)
        val due = if (gap == null) null else maxOf(now + MIN, s.lastDrink() + gap * MIN)
        at(am, ctx, REMIND, if (due != null && awake(s, due)) due else nextHourOrWake(s, now))

        // The morning summary, at wake time, and the evening one an hour before bed.
        at(am, ctx, MORNING, nextWake(s, now))
        at(am, ctx, EVENING, nextMinute(s.sleepMinute - 60, now))

        // An hour before Danger starts today.
        val hr = s.profile.higherRisk
        f.on(f.today()).firstOrNull { it.level(hr) >= Level.DANGER && millis(f, it.time) > now + HOUR }?.let {
            if (!s.sentToday("preDanger")) at(am, ctx, PRE_DANGER, millis(f, it.time) - HOUR)
        }
    }

    /** Minutes between reminders right now, or null when none are due (safe, paused, asleep). */
    fun interval(s: Store, f: Forecast): Int? {
        if (!s.remindersOn || s.pausedToday()) return null
        val h = f.current() ?: return null
        return Advice.reminderMinutes(h.level(s.profile.higherRisk), s.activity, s.profile)
    }

    /** The levels of today's waking hours, which the day's water target is built from. */
    fun wakingLevels(s: Store, f: Forecast): List<Level> {
        val hr = s.profile.higherRisk
        return f.on(f.today()).filter { h ->
            val m = h.time.hour * 60
            if (s.wakeMinute <= s.sleepMinute) m in s.wakeMinute until s.sleepMinute else m >= s.wakeMinute || m < s.sleepMinute
        }.map { it.level(hr) }
    }

    fun targetCups(s: Store, f: Forecast) = Advice.cupsToday(wakingLevels(s, f), s.activity, s.profile)

    fun onRemind(ctx: Context) {
        val s = Store(ctx)
        val f = s.forecast()
        val now = System.currentTimeMillis()
        if (f != null && awake(s, now)) {
            val gap = interval(s, f)
            val h = f.current()!!
            val level = h.level(s.profile.higherRisk)
            if (gap != null && now - s.lastDrink() >= (gap - 1) * MIN) {
                val tip = Advice.coolingTip(level, h.temp, h.rh, s.nextTipNumber())
                // Today's water is done (or a doctor limits it): cooling prompts only from here.
                val note = when {
                    s.profile.fluidLimit -> ctx.getString(R.string.n_fluid_limit)
                    s.cupsToday() >= targetCups(s, f) -> ctx.getString(R.string.n_target_met)
                    else -> null
                }
                Notify.reminder(ctx, s, level, minutesSince(s.lastDrink(), now), tip, note)
                if (s.speakOn) Speak.say(ctx, (if (note == null) ctx.getString(R.string.say_water) + " " else "") + tipText(ctx, tip))
            }
            // On a Danger day, a long silence gets an "Are you OK?".
            if (s.checkinOn && level >= Level.DANGER && now - s.lastAnything() >= SILENCE_BEFORE_CHECKIN &&
                now - s.checkinPostedAt > SILENCE_BEFORE_CHECKIN) {
                s.checkinPostedAt = now
                Notify.checkin(ctx, s, h.heatIndex)
                at(ctx.getSystemService(AlarmManager::class.java), ctx, ESCALATE, now + WAIT_BEFORE_TEXT)
            }
        }
        plan(ctx)
    }

    /** Still nothing since the check-in went out: text the family contact. */
    fun onEscalate(ctx: Context) {
        val s = Store(ctx)
        if (s.lastAnything() > s.checkinPostedAt || s.contactPhone.isBlank()) return
        val f = s.forecast() ?: return
        val feels = f.current()?.heatIndex?.roundToInt() ?: return
        val hours = ((System.currentTimeMillis() - s.lastAnything()) / (30 * MIN)) / 2.0
        val who = s.profile.name.ifBlank { ctx.getString(R.string.sms_someone) }
        val text = ctx.getString(R.string.sms_text, who, formatHours(hours), feels, f.place.name.ifBlank { "their area" })
        val sent = if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) runCatching {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(s.contactPhone, null, sms.divideMessage(text), null, null)
        }.isSuccess else false
        Notify.escalated(ctx, s.contactName.ifBlank { s.contactPhone }, sent)
    }

    fun onMorning(ctx: Context) {
        val s = Store(ctx)
        val f = s.forecast()
        if (f != null && !s.sentToday("morning")) {
            val hr = s.profile.higherRisk
            val hours = f.on(f.today())
            val peak = hours.maxByOrNull { it.heatIndex }
            if (peak != null && peak.level(hr) >= Level.CAUTION) {
                val bad = hours.filter { it.level(hr) >= Level.DANGER }
                Notify.morning(ctx, f.place.name, peak.heatIndex, peak.level(hr),
                    bad.firstOrNull()?.time?.hour, bad.lastOrNull()?.time?.hour?.plus(1))
            }
            s.familyPlaces.filter { (it.maxHeatIndex ?: 0.0) >= 41 }.forEach { Notify.family(ctx, it) }
            s.markSent("morning")
        }
        plan(ctx)
    }

    /**
     * The coolest the coming night gets, if even that is hot: the "2am, no light, hot room"
     * nights. Null on a night that cools down. Covers bedtime to wake-up, from tonight on.
     */
    fun hotNight(s: Store, f: Forecast): Double? {
        val now = f.nowLocal()
        val bed = now.toLocalDate().atStartOfDay().plusMinutes(s.sleepMinute.toLong()).let { if (it.isBefore(now.minusHours(6))) it.plusDays(1) else it }
        val wake = bed.toLocalDate().atStartOfDay().plusMinutes(s.wakeMinute.toLong()).let { if (!it.isAfter(bed)) it.plusDays(1) else it }
        val night = f.hours.filter { !it.time.isBefore(bed) && it.time.isBefore(wake) }
        if (night.size < 4) return null
        return night.minOf { it.heatIndex }.takeIf { it >= HOT_NIGHT }
    }

    /** An hour before bed: warn about a hot night, and nudge a call to family on a dangerous day. */
    fun onEvening(ctx: Context) {
        val s = Store(ctx)
        val f = s.forecast()
        if (f != null && !s.sentToday("evening")) {
            hotNight(s, f)?.let { Notify.evening(ctx, it) }
            s.familyPlaces.filter { (it.maxHeatIndex ?: 0.0) >= 41 }.forEach { Notify.familyNight(ctx, it) }
            s.markSent("evening")
        }
        plan(ctx)
    }

    fun onPreDanger(ctx: Context) {
        val s = Store(ctx)
        val f = s.forecast() ?: return
        if (s.sentToday("preDanger")) return
        val hr = s.profile.higherRisk
        val bad = f.on(f.today()).filter { it.level(hr) >= Level.DANGER && !it.time.isBefore(f.nowLocal()) }
        val first = bad.firstOrNull() ?: return
        Notify.preDanger(ctx, f.place.name, first.time.hour, bad.last().time.hour + 1, bad.maxOf { it.heatIndex }, bad.maxOf { it.level(hr) })
        s.markSent("preDanger")
    }

    // ---- small helpers ----

    private fun millis(f: Forecast, t: LocalDateTime) = t.toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(f.utcOffset)) * 1000

    private fun minuteOfDay(ms: Long): Int {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return t.hour * 60 + t.minute
    }

    fun awake(s: Store, ms: Long): Boolean {
        val m = minuteOfDay(ms)
        return if (s.wakeMinute <= s.sleepMinute) m in s.wakeMinute until s.sleepMinute
        else m >= s.wakeMinute || m < s.sleepMinute
    }

    private fun nextWake(s: Store, now: Long) = nextMinute(s.wakeMinute, now)

    /** The next time the clock shows this minute of the day. */
    private fun nextMinute(minute: Int, now: Long): Long {
        val z = ZoneId.systemDefault()
        var t = java.time.LocalDate.now(z).atStartOfDay(z).plusMinutes(Math.floorMod(minute, 1440).toLong())
        if (t.toInstant().toEpochMilli() <= now) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    /** Check again at the top of the next hour, or at wake time if that is asleep. */
    private fun nextHourOrWake(s: Store, now: Long): Long {
        val next = (now / HOUR + 1) * HOUR + MIN
        return if (awake(s, next)) next else nextWake(s, now)
    }

    private fun minutesSince(t: Long, now: Long) = if (t == 0L) null else ((now - t) / MIN).toInt()

    private fun formatHours(h: Double) = if (h % 1.0 == 0.0) h.toInt().toString() else "%.1f".format(h)

    private fun pi(ctx: Context, action: String) = PendingIntent.getBroadcast(ctx, action.hashCode(),
        Intent(ctx, AlarmReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /**
     * Fires even in Doze, with no exact-alarm permission. Android may run these late by up to
     * 3/4 of the wait, so a long wait is split into hops of at most [HOP]: each early wake-up
     * lands in [onAlarm], which sets the next hop until the real time comes.
     */
    private fun at(am: AlarmManager, ctx: Context, action: String, ms: Long) {
        ctx.getSharedPreferences("heatsafe", Context.MODE_PRIVATE).edit().putLong("due:$action", ms).apply()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, minOf(ms, System.currentTimeMillis() + HOP), pi(ctx, action))
    }

    private const val HOP = 10 * MIN

    /** An alarm went off: true if its real time has come, else the next hop is set. */
    fun onAlarm(ctx: Context, action: String): Boolean {
        val due = ctx.getSharedPreferences("heatsafe", Context.MODE_PRIVATE).getLong("due:$action", 0)
        if (System.currentTimeMillis() >= due - 2 * MIN) return true
        at(ctx.getSystemService(AlarmManager::class.java), ctx, action, due)
        return false
    }

    fun levelOf(heatIndex: Double) = HeatMath.level(heatIndex)
}
