package site.callmedraxxx.heat

import org.junit.Assert.assertEquals
import org.junit.Test
import site.callmedraxxx.heat.heat.ExcessHeat
import site.callmedraxxx.heat.heat.HeatMath
import site.callmedraxxx.heat.heat.Heatwave
import site.callmedraxxx.heat.heat.Level

class HeatMathTest {
    private fun f2c(f: Double) = (f - 32) * 5 / 9

    /** Values from the NWS heat index chart (°F), checked to within 1.5 °F. */
    @Test fun heatIndexMatchesNwsChart() {
        listOf(
            Triple(90.0, 40.0, 91.0), Triple(90.0, 70.0, 106.0), Triple(96.0, 65.0, 121.0),
            Triple(100.0, 40.0, 109.0), Triple(86.0, 90.0, 105.0), Triple(80.0, 40.0, 80.0),
        ).forEach { (t, rh, want) ->
            assertEquals("T=$t RH=$rh", f2c(want), HeatMath.heatIndex(f2c(t), rh), 1.5 * 5 / 9)
        }
    }

    @Test fun mildWeatherFeelsAboutTheSame() {
        assertEquals(20.0, HeatMath.heatIndex(20.0, 50.0), 1.0)
    }

    /** Stull (2011): 20 °C at 50% is 13.7 °C. */
    @Test fun wetBulbMatchesStull() {
        assertEquals(13.7, HeatMath.wetBulb(20.0, 50.0), 0.1)
    }

    /** BoM WBGT table: 30 °C at 60% humidity is about 31 °C. */
    @Test fun wbgtShadeMatchesBom() {
        assertEquals(31.0, HeatMath.wbgtShade(30.0, 60.0), 0.3)
    }

    @Test fun levelsFollowNwsBands() {
        assertEquals(Level.SAFE, HeatMath.level(26.9))
        assertEquals(Level.CAUTION, HeatMath.level(27.0))
        assertEquals(Level.EXTREME_CAUTION, HeatMath.level(35.0))
        assertEquals(Level.DANGER, HeatMath.level(41.0))
        assertEquals(Level.EXTREME_DANGER, HeatMath.level(54.0))
        // Higher-risk people move up 3 °C sooner.
        assertEquals(Level.DANGER, HeatMath.level(38.5, higherRisk = true))
    }

    @Test fun excessHeatFactorFlagsAHotSpell() {
        // 30 ordinary days at 28 °C, then three at 36 °C; T95 of the place is 33 °C.
        val days = List(30) { 28.0 } + listOf(36.0, 36.0, 36.0)
        val ehf = ExcessHeat.ehf(days, 30, 33.0)
        assertEquals(3.0 * 8.0, ehf, 1e-9) // (36-33) * max(1, 36-28)
        assertEquals(Heatwave.SEVERE, ExcessHeat.severity(ehf, 20.0))
        assertEquals(Heatwave.EXTREME, ExcessHeat.severity(ehf, 5.0))
        assertEquals(Heatwave.NONE, ExcessHeat.severity(-1.0, 5.0))
    }

    @Test fun percentileInterpolates() {
        assertEquals(5.5, ExcessHeat.percentile((1..10).map { it.toDouble() }, 50.0), 1e-9)
    }
}
