package site.callmedraxxx.heat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import site.callmedraxxx.heat.heat.Activity
import site.callmedraxxx.heat.heat.Profile
import java.time.LocalDate

/** One thing the person did: drank a cup, cooled down, or answered "I'm OK". */
data class LogEntry(val at: Long, val kind: String) {
    companion object {
        const val WATER = "water"
        const val COOL = "cool"
        const val OK = "ok"
    }
}

/** A saved place for someone the person looks out for, with today's worst heat there. */
data class FamilyPlace(val label: String, val place: Place, val maxHeatIndex: Double? = null, val checkedAt: Long = 0) {
    fun toJson(): JSONObject = JSONObject().put("label", label).put("place", place.toJson())
        .put("max", maxHeatIndex ?: JSONObject.NULL).put("at", checkedAt)

    companion object {
        fun of(j: JSONObject) = FamilyPlace(j.getString("label"), Place.of(j.getJSONObject("place")),
            if (j.isNull("max")) null else j.getDouble("max"), j.optLong("at"))
    }
}

/**
 * Everything the app keeps. It all stays on the phone: there is no account and no server.
 */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("heatsafe", Context.MODE_PRIVATE)

    var place: Place?
        get() = p.getString("place", null)?.let { Place.of(JSONObject(it)) }
        set(v) = p.edit().putString("place", v?.toJson()?.toString()).apply()

    /** The place follows the phone (set by "Use my location"), rather than a town picked by hand. */
    var followMe: Boolean
        get() = p.getBoolean("followMe", false)
        set(v) = p.edit().putBoolean("followMe", v).apply()

    /** Asked once for location on an install that picked its town by hand. */
    var askedLocation: Boolean
        get() = p.getBoolean("askedLocation", false)
        set(v) = p.edit().putBoolean("askedLocation", v).apply()

    /** Which look the app wears: daylight, thermal or signal. */
    var look: String?
        get() = p.getString("look", null)
        set(v) = p.edit().putString("look", v).apply()

    fun saveForecast(raw: String, at: Long) = p.edit().putString("forecast", raw).putLong("forecastAt", at).apply()

    fun forecast(): Forecast? {
        val pl = place ?: return null
        val raw = p.getString("forecast", null) ?: return null
        return runCatching { Weather.parseForecast(pl, raw, p.getLong("forecastAt", 0)) }.getOrNull()
    }

    fun climate(key: String): Climate? = p.getString("climate:$key", null)?.let { runCatching { Climate.of(JSONObject(it)) }.getOrNull() }
    fun saveClimate(key: String, c: Climate) = p.edit().putString("climate:$key", c.toJson().toString()).apply()

    var profile: Profile
        get() = p.getString("profile", null)?.let { JSONObject(it) }?.let {
            Profile(it.optString("name"), it.optBoolean("olderOrChild"), it.optBoolean("pregnant"), it.optBoolean("outdoorWorker"),
                it.optBoolean("medicine"), it.optBoolean("fluidLimit"))
        } ?: Profile()
        set(v) = p.edit().putString("profile", JSONObject().put("name", v.name).put("olderOrChild", v.olderOrChild)
            .put("pregnant", v.pregnant).put("outdoorWorker", v.outdoorWorker).put("medicine", v.medicine)
            .put("fluidLimit", v.fluidLimit).toString()).apply()

    var activity: Activity
        get() = runCatching { Activity.valueOf(p.getString("activity", null) ?: "") }.getOrDefault(Activity.HOME)
        set(v) = p.edit().putString("activity", v.name).apply()

    /** Minutes after midnight. Reminders only come between these. */
    var wakeMinute: Int
        get() = p.getInt("wake", 7 * 60)
        set(v) = p.edit().putInt("wake", v).apply()
    var sleepMinute: Int
        get() = p.getInt("sleep", 22 * 60)
        set(v) = p.edit().putInt("sleep", v).apply()

    var remindersOn: Boolean
        get() = p.getBoolean("reminders", true)
        set(v) = p.edit().putBoolean("reminders", v).apply()

    /** Reminders are also said out loud. */
    var speakOn: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    /** Reminders paused for this local date only. */
    var pausedOn: String?
        get() = p.getString("paused", null)
        set(v) = p.edit().putString("paused", v).apply()
    fun pausedToday() = pausedOn == LocalDate.now().toString()

    /** The log of the last 3 days, oldest first. */
    fun log(): List<LogEntry> {
        val a = JSONArray(p.getString("log", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { LogEntry(it.getLong("at"), it.getString("kind")) }
    }

    fun addLog(kind: String, at: Long = System.currentTimeMillis()) {
        val keep = log().filter { at - it.at < 3 * 86_400_000L } + LogEntry(at, kind)
        p.edit().putString("log", JSONArray(keep.map { JSONObject().put("at", it.at).put("kind", it.kind) }).toString()).apply()
    }

    fun lastDrink(): Long = log().lastOrNull { it.kind == LogEntry.WATER }?.at ?: 0
    fun lastAnything(): Long = maxOf(log().lastOrNull()?.at ?: 0, p.getLong("openedAt", 0))
    fun cupsToday(): Int {
        val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return log().count { it.kind == LogEntry.WATER && it.at >= start }
    }
    fun markOpened() = p.edit().putLong("openedAt", System.currentTimeMillis()).apply()

    /** Turns the cooling tip with each reminder. */
    fun nextTipNumber(): Int { val n = p.getInt("tipN", 0); p.edit().putInt("tipN", n + 1).apply(); return n }

    // "Are you OK?" check-ins and the family contact who gets the text.
    var checkinOn: Boolean
        get() = p.getBoolean("checkin", false)
        set(v) = p.edit().putBoolean("checkin", v).apply()
    var contactName: String
        get() = p.getString("contactName", "") ?: ""
        set(v) = p.edit().putString("contactName", v).apply()
    var contactPhone: String
        get() = p.getString("contactPhone", "") ?: ""
        set(v) = p.edit().putString("contactPhone", v).apply()
    var checkinPostedAt: Long
        get() = p.getLong("checkinAt", 0)
        set(v) = p.edit().putLong("checkinAt", v).apply()

    var familyPlaces: List<FamilyPlace>
        get() { val a = JSONArray(p.getString("family", "[]")); return (0 until a.length()).map { FamilyPlace.of(a.getJSONObject(it)) } }
        set(v) = p.edit().putString("family", JSONArray(v.map { it.toJson() }).toString()).apply()

    /** NOAA's weekly Niño 3.4 anomalies, last 26 weeks, refreshed at most once a day. */
    var ninoHistory: List<Pair<String, Double>>
        get() { val a = JSONArray(p.getString("ninoHistory", "[]"))
            return (0 until a.length()).map { a.getJSONObject(it) }.map { it.getString("week") to it.getDouble("anomaly") } }
        set(v) = p.edit().putString("ninoHistory", JSONArray(v.map { JSONObject().put("week", it.first).put("anomaly", it.second) }).toString())
            .putLong("ninoAt", System.currentTimeMillis()).apply()
    val nino: Pair<String, Double>? get() = ninoHistory.lastOrNull()
    val ninoAt get() = p.getLong("ninoAt", 0)

    /** The next 3 months here against normal (°C), for the place it was worked out for. */
    fun seasonal(key: String): List<Pair<java.time.YearMonth, Double>> {
        val a = JSONArray(p.getString("seasonal:$key", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { java.time.YearMonth.parse(it.getString("m")) to it.getDouble("d") }
    }
    fun saveSeasonal(key: String, v: List<Pair<java.time.YearMonth, Double>>) = p.edit()
        .putString("seasonal:$key", JSONArray(v.map { JSONObject().put("m", it.first.toString()).put("d", it.second) }).toString())
        .putLong("seasonalAt:$key", System.currentTimeMillis()).apply()
    fun seasonalAt(key: String) = p.getLong("seasonalAt:$key", 0)

    /** Each daily message is sent once: key -> the date it last went out. */
    fun sentToday(key: String) = p.getString("sent:$key", null) == LocalDate.now().toString()
    fun markSent(key: String) = p.edit().putString("sent:$key", LocalDate.now().toString()).apply()
}
