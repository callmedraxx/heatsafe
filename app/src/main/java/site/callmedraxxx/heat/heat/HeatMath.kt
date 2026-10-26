package site.callmedraxxx.heat.heat

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The heat numbers. Every formula here is a published method, named where it is used,
 * and checked against reference values in HeatMathTest.
 */
object HeatMath {

    private fun cToF(c: Double) = c * 9 / 5 + 32
    private fun fToC(f: Double) = (f - 32) * 5 / 9

    /**
     * Heat index ("feels like"), US National Weather Service: Steadman's simple formula,
     * and the Rothfusz regression with its low- and high-humidity adjustments once that
     * reaches 80 °F.
     */
    fun heatIndex(tempC: Double, rh: Double): Double {
        val t = cToF(tempC)
        val simple = 0.5 * (t + 61.0 + (t - 68.0) * 1.2 + rh * 0.094)
        if ((simple + t) / 2 < 80) return fToC(simple)
        var hi = -42.379 + 2.04901523 * t + 10.14333127 * rh - 0.22475541 * t * rh -
            0.00683783 * t * t - 0.05481717 * rh * rh + 0.00122874 * t * t * rh +
            0.00085282 * t * rh * rh - 0.00000199 * t * t * rh * rh
        if (rh < 13 && t in 80.0..112.0) hi -= ((13 - rh) / 4) * sqrt((17 - abs(t - 95)) / 17)
        else if (rh > 85 && t in 80.0..87.0) hi += ((rh - 85) / 10) * ((87 - t) / 5)
        return fToC(hi)
    }

    /** Wet-bulb temperature, Stull (2011). Good to about 1 °C for 5–99% humidity. */
    fun wetBulb(tempC: Double, rh: Double): Double =
        tempC * atan(0.151977 * sqrt(rh + 8.313659)) + atan(tempC + rh) - atan(rh - 1.676331) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) - 4.686035

    /**
     * WBGT in the shade, Australian Bureau of Meteorology approximation.
     * Direct sun adds a few degrees on top; [wbgtSun] gives a rough figure for that.
     */
    fun wbgtShade(tempC: Double, rh: Double): Double {
        val vapourHpa = rh / 100 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return 0.567 * tempC + 0.393 * vapourHpa + 3.94
    }

    /**
     * A rough outdoor-in-sun WBGT: shade WBGT plus up to 3 °C for strong sun, less in wind.
     * An estimate for work/rest advice only, labelled as such in the app.
     */
    fun wbgtSun(tempC: Double, rh: Double, solarWm2: Double, windKmh: Double): Double {
        val sun = (solarWm2 / 1000.0).coerceIn(0.0, 1.0) * 3.0
        val windCut = (windKmh / 20.0).coerceIn(0.0, 0.5)
        return wbgtShade(tempC, rh) + sun * (1 - windCut)
    }

    /**
     * The risk level for a heat index, from the US National Weather Service bands.
     * A higher-risk person moves up a level 3 °C sooner.
     */
    fun level(heatIndexC: Double, higherRisk: Boolean = false): Level {
        val hi = heatIndexC + if (higherRisk) 3.0 else 0.0
        return when {
            hi >= 54 -> Level.EXTREME_DANGER
            hi >= 41 -> Level.DANGER
            hi >= 32 -> Level.EXTREME_CAUTION
            hi >= 27 -> Level.CAUTION
            else -> Level.SAFE
        }
    }

    /** In very dry heat, fans blow hot air and dry you out faster: wet the skin first. */
    fun fansStopHelping(tempC: Double, rh: Double) = tempC >= 38 && rh <= 35
}

enum class Level { SAFE, CAUTION, EXTREME_CAUTION, DANGER, EXTREME_DANGER }

/** Heatwave severity from the Excess Heat Factor. */
enum class Heatwave { NONE, LOW, SEVERE, EXTREME }

/**
 * Excess Heat Factor, Nairn & Fawcett (2013), as used by the Australian Bureau of Meteorology.
 * "Unusually hot for this place for 3 days" rather than one fixed temperature.
 *
 * Daily mean temperature is (max + min) / 2. T95 is the 95th percentile of that over the
 * place's climate record; EHF85 is the 85th percentile of the positive EHF values in the
 * same record, which sets the "severe" line for that place.
 */
object ExcessHeat {

    /** EHF for the 3 days starting at [i], given at least 30 days before it. */
    fun ehf(daily: List<Double>, i: Int, t95: Double): Double {
        val t3 = daily.subList(i, i + 3).average()
        val t30 = daily.subList(i - 30, i).average()
        val sig = t3 - t95
        val accl = t3 - t30
        return sig * maxOf(1.0, accl)
    }

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val rank = p / 100 * (s.size - 1)
        val lo = rank.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (rank - lo)
    }

    /** T95 and EHF85 from a long run of daily means (ideally 1991–2020). */
    fun climate(daily: List<Double>): Pair<Double, Double> {
        val t95 = percentile(daily, 95.0)
        val positive = (30..daily.size - 3).map { ehf(daily, it, t95) }.filter { it > 0 }
        return t95 to percentile(positive, 85.0)
    }

    fun severity(ehf: Double, ehf85: Double): Heatwave = when {
        ehf <= 0 || ehf85.isNaN() -> Heatwave.NONE
        ehf >= 3 * ehf85 -> Heatwave.EXTREME
        ehf >= ehf85 -> Heatwave.SEVERE
        else -> Heatwave.LOW
    }
}
