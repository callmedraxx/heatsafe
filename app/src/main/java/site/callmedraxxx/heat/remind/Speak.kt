package site.callmedraxxx.heat.remind

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.heat.Tip
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One spoken line: a recorded clip in res/raw (per language), and the text it says. */
class Line(val clip: Int, val text: Int)

/**
 * Says reminders out loud. Each line is a recorded clip shipped with the app, played through
 * the notification stream, so the voice is the same on every phone and works offline. A
 * language without clips falls back to the phone's text-to-speech. Quiet when the ringer is
 * off, like a notification sound. Blocks the calling (background) thread until the lines
 * are spoken, so a receiver stays alive for them, and never for more than [MAX_WAIT] each.
 */
object Speak {
    private const val MAX_WAIT = 20_000L
    private val ATTRS = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    val WATER = Line(R.raw.v_water, R.string.say_water)
    val COOL = Line(R.raw.v_cool, R.string.n_cool_title)
    val TARGET_MET = Line(R.raw.v_target_met, R.string.n_target_met)
    val FLUID_LIMIT = Line(R.raw.v_fluid_limit, R.string.n_fluid_limit)

    fun tip(t: Tip) = when (t) {
        Tip.SHADE -> Line(R.raw.v_tip_shade, R.string.tip_shade)
        Tip.WET_FACE -> Line(R.raw.v_tip_wet_face, R.string.tip_wet_face)
        Tip.DAMP_CLOTH -> Line(R.raw.v_tip_damp_cloth, R.string.tip_damp_cloth)
        Tip.COOL_WASH -> Line(R.raw.v_tip_cool_wash, R.string.tip_cool_wash)
        Tip.WET_SKIN_FIRST -> Line(R.raw.v_tip_wet_skin, R.string.tip_wet_skin)
    }

    fun say(ctx: Context, vararg lines: Line) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val spoken = lines.filter { !play(ctx, it.clip) }
        if (spoken.isNotEmpty()) tts(ctx, spoken.joinToString(" ") { ctx.getString(it.text) })
    }

    /** Plays one clip to the end. False when the current language has no recording of it. */
    private fun play(ctx: Context, clip: Int): Boolean {
        val mp = MediaPlayer()
        try {
            val fd = ctx.resources.openRawResourceFd(clip) ?: return false
            mp.setAudioAttributes(ATTRS)
            fd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val done = CountDownLatch(1)
            mp.setOnCompletionListener { done.countDown() }
            mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
            mp.prepare()
            mp.start()
            done.await(MAX_WAIT, TimeUnit.MILLISECONDS)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            mp.release()
        }
    }

    private fun tts(ctx: Context, text: String, locale: Locale = Locale.getDefault()) {
        val ready = CountDownLatch(1)
        var ok = false
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(ctx.applicationContext) { status -> ok = status == TextToSpeech.SUCCESS; ready.countDown() }
        if (!ready.await(5, TimeUnit.SECONDS) || !ok) { tts.shutdown(); return }
        try {
            tts.language = if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) locale else Locale.ENGLISH
            tts.setAudioAttributes(ATTRS)
            val done = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = done.countDown()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = done.countDown()
                override fun onError(id: String?, code: Int) = done.countDown()
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "heatsafe") == TextToSpeech.SUCCESS)
                done.await(MAX_WAIT, TimeUnit.MILLISECONDS)
        } finally {
            tts.shutdown()
        }
    }
}
