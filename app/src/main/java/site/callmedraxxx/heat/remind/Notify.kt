package site.callmedraxxx.heat.remind

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.FamilyPlace
import site.callmedraxxx.heat.data.Store
import site.callmedraxxx.heat.heat.Level
import site.callmedraxxx.heat.heat.Tip
import site.callmedraxxx.heat.ui.AlertActivity
import site.callmedraxxx.heat.ui.MainActivity
import site.callmedraxxx.heat.ui.levelName
import site.callmedraxxx.heat.ui.tipText
import kotlin.math.roundToInt

object Notify {
    private const val CH_WATER = "water"
    private const val CH_ALERT = "alerts"
    private const val CH_CHECKIN = "checkin"

    const val ID_WATER = 1
    const val ID_MORNING = 2
    const val ID_ALERT = 3
    const val ID_CHECKIN = 4
    const val ID_EVENING = 5
    const val ID_FAMILY = 10
    private const val ID_FAMILY_NIGHT = 2000

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WATER, ctx.getString(R.string.ch_water), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, ctx.getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_CHECKIN, ctx.getString(R.string.ch_checkin), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun open(ctx: Context, tab: String? = null) = PendingIntent.getActivity(ctx, (tab ?: "").hashCode(),
        Intent(ctx, MainActivity::class.java).putExtra("tab", tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun action(ctx: Context, what: String) = PendingIntent.getBroadcast(ctx, what.hashCode(),
        Intent(ctx, ActionReceiver::class.java).setAction(what), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun post(ctx: Context, id: Int, n: NotificationCompat.Builder) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        channels(ctx)
        NotificationManagerCompat.from(ctx).notify(id, n.build())
    }

    private fun base(ctx: Context, ch: String) = NotificationCompat.Builder(ctx, ch)
        .setSmallIcon(R.drawable.ic_stat).setColor(0xFFFF6A2B.toInt()).setAutoCancel(true)

    /** A water reminder, or a cooling-only one when [coolingNote] says why there is no water. */
    fun reminder(ctx: Context, s: Store, level: Level, minutesAgo: Int?, tip: Tip, coolingNote: String?) {
        val coolingOnly = coolingNote != null
        val title = when {
            coolingOnly -> ctx.getString(R.string.n_cool_title)
            minutesAgo == null || minutesAgo > 180 -> ctx.getString(R.string.n_water_title_first)
            else -> ctx.getString(R.string.n_water_title, minutesAgo)
        }
        val body = (if (coolingNote != null) "$coolingNote " else "") + tipText(ctx, tip)
        val n = base(ctx, CH_WATER).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(levelName(ctx, level)).setContentIntent(open(ctx, "water"))
            .setTimeoutAfter(15 * 60_000L)
        if (!coolingOnly) n.addAction(0, ctx.getString(R.string.a_drank), action(ctx, ActionReceiver.DRANK))
        n.addAction(0, ctx.getString(R.string.a_cooled), action(ctx, ActionReceiver.COOLED))
            .addAction(0, ctx.getString(R.string.a_snooze), action(ctx, ActionReceiver.SNOOZE))
        post(ctx, ID_WATER, n)
    }

    fun morning(ctx: Context, place: String, peak: Double, level: Level, from: Int?, to: Int?) {
        val title = ctx.getString(R.string.n_morning_title, levelName(ctx, level), place, peak.roundToInt())
        val body = if (from != null && to != null) ctx.getString(R.string.n_morning_hours, from, to)
        else ctx.getString(R.string.n_morning_plain)
        post(ctx, ID_MORNING, base(ctx, CH_ALERT).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open(ctx)))
    }

    /** An hour before Danger: heads-up, and full screen where the phone allows it. */
    fun preDanger(ctx: Context, place: String, from: Int, to: Int, peak: Double, level: Level) {
        val full = PendingIntent.getActivity(ctx, 7, Intent(ctx, AlertActivity::class.java)
            .putExtra("place", place).putExtra("from", from).putExtra("to", to).putExtra("peak", peak).putExtra("level", level.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = ctx.getString(R.string.n_alert_title, place, levelName(ctx, level).lowercase(), from)
        val body = ctx.getString(R.string.n_alert_body, peak.roundToInt(), to)
        post(ctx, ID_ALERT, base(ctx, CH_ALERT).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(full, true).setContentIntent(full))
    }

    fun checkin(ctx: Context, s: Store, feels: Double) {
        val who = s.contactName.ifBlank { ctx.getString(R.string.your_contact) }
        val body = ctx.getString(R.string.n_checkin_body, feels.roundToInt(), who)
        post(ctx, ID_CHECKIN, base(ctx, CH_CHECKIN)
            .setContentTitle(if (s.profile.name.isBlank()) ctx.getString(R.string.n_checkin_title) else ctx.getString(R.string.n_checkin_title_named, s.profile.name))
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(false).setOngoing(true)
            .setContentIntent(open(ctx, "checkin"))
            .addAction(0, ctx.getString(R.string.a_ok), action(ctx, ActionReceiver.OK)))
    }

    fun escalated(ctx: Context, who: String, sent: Boolean) {
        val text = if (sent) ctx.getString(R.string.n_texted, who) else ctx.getString(R.string.n_text_failed, who)
        post(ctx, ID_CHECKIN, base(ctx, CH_CHECKIN).setContentTitle(text).setContentIntent(open(ctx, "checkin"))
            .addAction(0, ctx.getString(R.string.a_ok), action(ctx, ActionReceiver.OK)))
    }

    fun family(ctx: Context, fp: FamilyPlace) {
        val peak = fp.maxHeatIndex ?: return
        post(ctx, ID_FAMILY + fp.place.key.hashCode() % 1000, base(ctx, CH_ALERT)
            .setContentTitle(ctx.getString(R.string.n_family_title, fp.label))
            .setContentText(ctx.getString(R.string.n_family_body, fp.place.name, peak.roundToInt()))
            .setContentIntent(open(ctx, "family")))
    }

    fun evening(ctx: Context, nightLow: Double) {
        val body = ctx.getString(R.string.n_evening_body)
        post(ctx, ID_EVENING, base(ctx, CH_ALERT).setContentTitle(ctx.getString(R.string.n_evening_title, nightLow.roundToInt()))
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open(ctx)))
    }

    fun familyNight(ctx: Context, fp: FamilyPlace) =
        post(ctx, ID_FAMILY_NIGHT + fp.place.key.hashCode() % 1000, base(ctx, CH_ALERT)
            .setContentTitle(ctx.getString(R.string.n_family_night, fp.label))
            .setContentText(ctx.getString(R.string.n_family_night_body, fp.place.name))
            .setContentIntent(open(ctx, "family")))

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
