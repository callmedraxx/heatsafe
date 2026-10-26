package site.callmedraxxx.heat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.callmedraxxx.heat.heat.Activity
import site.callmedraxxx.heat.heat.Advice
import site.callmedraxxx.heat.heat.Level
import site.callmedraxxx.heat.heat.Profile

class AdviceTest {
    private val adult = Profile()
    private val older = Profile(olderOrChild = true)

    @Test fun noRemindersWhenSafe() {
        assertNull(Advice.reminderMinutes(Level.SAFE, Activity.WORK, adult))
    }

    @Test fun outdoorWorkFollowsCdc() {
        // One cup every 15–20 minutes when working in the heat.
        Level.entries.drop(1).forEach { assertTrue(Advice.reminderMinutes(it, Activity.WORK, adult)!! in 15..20) }
    }

    @Test fun homeIsGentlerThanWork() {
        assertEquals(30, Advice.reminderMinutes(Level.DANGER, Activity.HOME, adult))
        assertEquals(22, Advice.reminderMinutes(Level.DANGER, Activity.HOME, older))
    }

    @Test fun aHotDayAtHomeStaysWithinTheCap() {
        // 15 waking hours, all Extreme caution: a cup every 45 min would be 20; the cap is 14.
        assertEquals(14, Advice.cupsToday(List(15) { Level.EXTREME_CAUTION }, Activity.HOME, adult))
    }

    @Test fun aMildDayStillAsksForEight() {
        assertEquals(8, Advice.cupsToday(List(15) { Level.SAFE }, Activity.HOME, adult))
    }

    @Test fun neverMoreThanFourCupsAnHour() {
        Level.entries.forEach { l -> Activity.entries.forEach { a ->
            Advice.reminderMinutes(l, a, older)?.let { assertTrue(60.0 / it <= Advice.MAX_CUPS_PER_HOUR) }
        } }
    }
}
