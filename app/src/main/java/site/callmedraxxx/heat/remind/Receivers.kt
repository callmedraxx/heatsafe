package site.callmedraxxx.heat.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import site.callmedraxxx.heat.data.LogEntry
import site.callmedraxxx.heat.data.Store
import kotlin.concurrent.thread

/** Runs [work] off the main thread and keeps the receiver alive until it finishes. */
private fun BroadcastReceiver.background(work: () -> Unit) {
    val done = goAsync()
    thread { try { work() } finally { done.finish() } }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = background {
        val action = intent.action ?: return@background
        if (action != Engine.REFRESH && !Engine.onAlarm(ctx, action)) return@background
        when (action) {
            Engine.REFRESH -> Engine.refresh(ctx)
            Engine.REMIND -> Engine.onRemind(ctx)
            Engine.MORNING -> Engine.onMorning(ctx)
            Engine.EVENING -> Engine.onEvening(ctx)
            Engine.PRE_DANGER -> Engine.onPreDanger(ctx)
            Engine.ESCALATE -> Engine.onEscalate(ctx)
        }
    }
}

/** The buttons on a notification: log it, clear it, and plan the next one from now. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val DRANK = "site.callmedraxxx.heat.DRANK"
        const val COOLED = "site.callmedraxxx.heat.COOLED"
        const val SNOOZE = "site.callmedraxxx.heat.SNOOZE"
        const val OK = "site.callmedraxxx.heat.OK"
    }

    override fun onReceive(ctx: Context, intent: Intent) = background {
        val s = Store(ctx)
        when (intent.action) {
            DRANK -> { s.addLog(LogEntry.WATER); Notify.cancel(ctx, Notify.ID_WATER) }
            COOLED -> { s.addLog(LogEntry.COOL); Notify.cancel(ctx, Notify.ID_WATER) }
            OK -> { s.addLog(LogEntry.OK); Notify.cancel(ctx, Notify.ID_CHECKIN) }
            SNOOZE -> {
                Notify.cancel(ctx, Notify.ID_WATER)
                val am = ctx.getSystemService(AlarmManager::class.java)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10 * 60_000L,
                    PendingIntent.getBroadcast(ctx, Engine.REMIND.hashCode(), Intent(ctx, AlarmReceiver::class.java).setAction(Engine.REMIND),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                return@background
            }
        }
        Engine.plan(ctx)
    }
}

/** Alarms do not survive a restart or an app update; set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = background { Engine.refresh(ctx) }
}
