package site.callmedraxxx.heat.data

import org.json.JSONArray
import org.json.JSONObject
import site.callmedraxxx.heat.heat.ExcessHeat
import site.callmedraxxx.heat.heat.HeatMath
import site.callmedraxxx.heat.heat.Heatwave
import site.callmedraxxx.heat.heat.Level
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** A place, rounded to about 1 km before it ever leaves the phone. */
data class Place(val name: String, val country: String, val cc: String, val lat: Double, val lon: Double) {
    val key get() = "%.2f,%.2f".format(lat, lon)

    fun toJson() = JSONObject().put("name", name).put("country", country).put("cc", cc).put("lat", lat).put("lon", lon)

    companion object {
        fun round(v: Double) = (v * 100).roundToInt() / 100.0
        fun of(j: JSONObject) = Place(j.getString("name"), j.optString("country"), j.optString("cc"),
            j.getDouble("lat"), j.getDouble("lon"))
    }
}

data class Hour(
    /** local wall-clock time at the place */
    val time: LocalDateTime,
    val temp: Double, val rh: Double, val solar: Double, val wind: Double, val uv: Double,
) {
    val heatIndex = HeatMath.heatIndex(temp, rh)
    val wetBulb = HeatMath.wetBulb(temp, rh)
    val wbgtShade = HeatMath.wbgtShade(temp, rh)
    val wbgtSun = HeatMath.wbgtSun(temp, rh, solar, wind)
    fun level(higherRisk: Boolean) = HeatMath.level(heatIndex, higherRisk)
}

data class Forecast(
    val place: Place,
    val fetchedAt: Long,
    /** seconds east of UTC at the place */
    val utcOffset: Int,
    val hours: List<Hour>,
    /** local dates for [dailyMean], 30 days back to 7 ahead */
    val days: List<LocalDate>,
    val dailyMean: List<Double>,
    val dailyMax: List<Double>,
) {
    fun nowLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.ofTotalSeconds(utcOffset))
    fun today(): LocalDate = nowLocal().toLocalDate()
    fun current(): Hour? = hours.lastOrNull { !it.time.isAfter(nowLocal()) } ?: hours.firstOrNull()
    fun next48(): List<Hour> = hours.filter { !it.time.isBefore(nowLocal().withMinute(0)) }.take(48)
    fun on(day: LocalDate): List<Hour> = hours.filter { it.time.toLocalDate() == day }
    fun maxToday(higherRisk: Boolean): Hour? = on(today()).maxByOrNull { it.heatIndex }
}

/** What the place's climate record says, worked out once per place and kept. */
data class Climate(val t95: Double, val ehf85: Double, val normalMax: Map<Int, Double>) {
    fun toJson() = JSONObject().put("t95", t95).put("ehf85", ehf85)
        .put("normal", JSONObject(normalMax.mapKeys { it.key.toString() }))

    companion object {
        fun of(j: JSONObject): Climate {
            val n = j.getJSONObject("normal")
            return Climate(j.getDouble("t95"), j.getDouble("ehf85"), n.keys().asSequence().associate { it.toInt() to n.getDouble(it) })
        }
    }
}

object Weather {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"
    private const val ARCHIVE = "https://archive-api.open-meteo.com/v1/archive"
    /** NOAA Climate Prediction Center, weekly sea-surface anomalies (public domain). */
    private const val NINO = "https://www.cpc.ncep.noaa.gov/data/indices/wksst9120.for"

    fun get(url: String, timeoutMs: Int = 20000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "HeatSafe (open source heat safety app)")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    fun search(query: String): List<Place> {
        val j = JSONObject(get("$GEOCODE?count=8&language=en&name=" + URLEncoder.encode(query, "UTF-8")))
        val r = j.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { r.getJSONObject(it) }.map {
            val region = listOfNotNull(it.optString("admin1").takeIf { s -> s.isNotBlank() }, it.optString("country")).joinToString(", ")
            Place(it.getString("name"), region, it.optString("country_code"),
                Place.round(it.getDouble("latitude")), Place.round(it.getDouble("longitude")))
        }
    }

    fun forecastRaw(p: Place): String = get("$FORECAST?latitude=${p.lat}&longitude=${p.lon}" +
        "&hourly=temperature_2m,relative_humidity_2m,shortwave_radiation,wind_speed_10m,uv_index" +
        "&daily=temperature_2m_max,temperature_2m_min&past_days=30&forecast_days=7&timezone=auto")

    fun parseForecast(p: Place, raw: String, fetchedAt: Long): Forecast {
        val j = JSONObject(raw)
        val h = j.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        fun col(name: String) = h.getJSONArray(name)
        fun JSONArray.d(i: Int) = if (isNull(i)) 0.0 else getDouble(i)
        val hours = (0 until t.length()).mapNotNull { i ->
            if (col("temperature_2m").isNull(i)) null
            else Hour(LocalDateTime.parse(t.getString(i)), col("temperature_2m").d(i), col("relative_humidity_2m").d(i),
                col("shortwave_radiation").d(i), col("wind_speed_10m").d(i), col("uv_index").d(i))
        }
        val d = j.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val keep = (0 until dt.length()).filter { !mx.isNull(it) && !mn.isNull(it) }
        return Forecast(p, fetchedAt, j.optInt("utc_offset_seconds"), hours,
            keep.map { LocalDate.parse(dt.getString(it)) },
            keep.map { (mx.getDouble(it) + mn.getDouble(it)) / 2 },
            keep.map { mx.getDouble(it) })
    }

    /** 30 years of daily highs and lows (1991–2020): about 11,000 days, fetched once per place. */
    fun climate(p: Place): Climate {
        val j = JSONObject(get("$ARCHIVE?latitude=${p.lat}&longitude=${p.lon}&start_date=1991-01-01&end_date=2020-12-31" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto", 60000)).getJSONObject("daily")
        val dt = j.getJSONArray("time"); val mx = j.getJSONArray("temperature_2m_max"); val mn = j.getJSONArray("temperature_2m_min")
        val idx = (0 until dt.length()).filter { !mx.isNull(it) && !mn.isNull(it) }
        val means = idx.map { (mx.getDouble(it) + mn.getDouble(it)) / 2 }
        val (t95, ehf85) = ExcessHeat.climate(means)
        // The normal high for each day of the year, averaged over a 15-day window.
        val byDay = idx.groupBy { LocalDate.parse(dt.getString(it)).dayOfYear }.mapValues { e -> e.value.map { mx.getDouble(it) } }
        val normal = (1..366).associateWith { doy ->
            (-7..7).flatMap { byDay[Math.floorMod(doy - 1 + it, 366) + 1].orEmpty() }.average()
        }
        return Climate(t95, ehf85, normal)
    }

    /** Heatwave severity for today and each of the next days the forecast covers. */
    fun heatwaves(f: Forecast, c: Climate): List<Pair<LocalDate, Heatwave>> {
        val today = f.days.indexOf(f.today()).takeIf { it >= 30 } ?: return emptyList()
        return (today..f.dailyMean.size - 3).map { i ->
            f.days[i] to ExcessHeat.severity(ExcessHeat.ehf(f.dailyMean, i, c.t95), c.ehf85)
        }
    }

    /**
     * The last 26 weekly Niño 3.4 anomalies (°C), oldest first, from NOAA. Each line is the
     * week, then SST and anomaly for Niño 1+2, 3, 3.4 and 4: the 3.4 anomaly is the sixth number.
     */
    fun ninoHistory(): List<Pair<String, Double>> = get(NINO).lines()
        .filter { Regex("^\\s*\\d{2}[A-Z]{3}\\d{4}").containsMatchIn(it) }
        .takeLast(26)
        .map { line ->
            val nums = Regex("-?\\d+\\.\\d").findAll(line.trim().drop(9)).map { it.value.toDouble() }.toList()
            line.trim().take(9) to nums[5]
        }

    /**
     * The next 3 months here against the normal, from Open-Meteo's seasonal forecast
     * (the mean of its ensemble). A seasonal model runs warm or cold at each place, so its
     * first week is lined up with the regular forecast and that bias taken off.
     */
    fun seasonal(p: Place, f: Forecast, c: Climate): List<Pair<java.time.YearMonth, Double>> {
        val d = JSONObject(get("https://seasonal-api.open-meteo.com/v1/seasonal?latitude=${p.lat}&longitude=${p.lon}" +
            "&daily=temperature_2m_max&forecast_days=100", 60000)).getJSONObject("daily")
        val t = d.getJSONArray("time")
        val cols = d.keys().asSequence().filter { it.startsWith("temperature_2m_max") }.map { d.getJSONArray(it) }.toList()
        val days = (0 until t.length()).mapNotNull { i ->
            val v = cols.mapNotNull { if (it.isNull(i)) null else it.getDouble(i) }
            if (v.isEmpty()) null else LocalDate.parse(t.getString(i)) to v.average()
        }
        val bias = days.take(7).mapNotNull { (day, v) -> f.dailyMax.getOrNull(f.days.indexOf(day))?.let { v - it } }
            .takeIf { it.size >= 4 }?.average() ?: 0.0
        return days.groupBy { java.time.YearMonth.from(it.first) }.entries.mapNotNull { (month, list) ->
            val normals = list.mapNotNull { c.normalMax[it.first.dayOfYear] }.filter { !it.isNaN() }
            if (list.size < 7 || normals.isEmpty()) null
            else month to (list.map { it.second }.average() - bias - normals.average())
        }.take(3)
    }
}

