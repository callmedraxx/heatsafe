package site.callmedraxxx.heat.heat

/** Where the person is today; working outside follows the CDC/NIOSH heat-work guidance. */
enum class Activity { HOME, OUT, WORK }

/** Kept on the phone only. */
data class Profile(
    val name: String = "",
    val olderOrChild: Boolean = false,
    val pregnant: Boolean = false,
    val outdoorWorker: Boolean = false,
    /** heart, blood pressure, diabetes or mental-health medicine */
    val medicine: Boolean = false,
    /** a doctor has said to limit fluids (kidney or heart failure): cooling prompts only */
    val fluidLimit: Boolean = false,
) {
    val higherRisk get() = olderOrChild || pregnant || medicine || fluidLimit
}

object Advice {

    /**
     * Minutes between water reminders, or null for none. Sitting at home needs far less
     * than hard work in the sun: working outside is one cup every 15–20 minutes (CDC/NIOSH).
     * Higher-risk people are reminded a quarter sooner.
     */
    fun reminderMinutes(level: Level, activity: Activity, profile: Profile): Int? {
        if (level == Level.SAFE) return null
        val i = level.ordinal - 1
        val act = when (activity) {
            Activity.HOME -> listOf(60, 45, 30, 20)
            Activity.OUT -> listOf(45, 30, 20, 15)
            Activity.WORK -> listOf(20, 20, 15, 15)
        }[i]
        return if (profile.higherRisk) maxOf(15, act * 3 / 4) else act
    }

    /**
     * The most cups to suggest in a day. About 3.5 litres at home, 4.5 out and about, and
     * 7 for a day of outdoor work (with ORS or salty food). Too much water is dangerous too.
     */
    fun dailyCap(activity: Activity) = when (activity) {
        Activity.HOME -> 14
        Activity.OUT -> 18
        Activity.WORK -> 28
    }

    /** About 250 ml. Never more than 4 an hour: over ~1 litre an hour is dangerous too. */
    const val CUP_ML = 250
    const val MAX_CUPS_PER_HOUR = 4

    /**
     * Cups the day's heat calls for, from the levels of the waking hours only: the reminders
     * that fall in them, never more than 4 an hour, at least 8 and at most [dailyCap].
     */
    fun cupsToday(wakingHours: List<Level>, activity: Activity, profile: Profile): Int {
        var cups = 0.0
        for (lvl in wakingHours) {
            val m = reminderMinutes(lvl, activity, profile)
            cups += if (m == null) 0.5 else minOf(60.0 / m, MAX_CUPS_PER_HOUR.toDouble())
        }
        return Math.round(cups).toInt().coerceIn(8, dailyCap(activity))
    }

    /**
     * Work/rest minutes per hour for moderate work, from WBGT, after the US Army's
     * TB MED 507 table. Returns null below the first flag.
     */
    fun workRest(wbgt: Double): Pair<Int, Int>? = when {
        wbgt >= 32.2 -> 20 to 40
        wbgt >= 31.1 -> 30 to 30
        wbgt >= 29.4 -> 40 to 20
        wbgt >= 27.8 -> 50 to 10
        else -> null
    }

    /** One cooling prompt, chosen for the heat and turned with each reminder. */
    fun coolingTip(level: Level, tempC: Double, rh: Double, n: Int): Tip {
        if (HeatMath.fansStopHelping(tempC, rh)) return Tip.WET_SKIN_FIRST
        val tips = when (level) {
            Level.SAFE, Level.CAUTION -> listOf(Tip.SHADE, Tip.WET_FACE)
            Level.EXTREME_CAUTION -> listOf(Tip.WET_FACE, Tip.DAMP_CLOTH, Tip.SHADE)
            else -> listOf(Tip.DAMP_CLOTH, Tip.COOL_WASH, Tip.WET_FACE, Tip.SHADE)
        }
        return tips[Math.floorMod(n, tips.size)]
    }
}

enum class Tip { SHADE, WET_FACE, DAMP_CLOTH, COOL_WASH, WET_SKIN_FIRST }
