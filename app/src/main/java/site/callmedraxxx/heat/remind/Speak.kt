package site.callmedraxxx.heat.remind

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Says a reminder out loud with the phone's own text-to-speech, in the app's language when
 * the phone has a voice for it, else in English. Quiet when the ringer is off, like a
 * notification sound. Blocks the calling (background) thread until the sentence is spoken,
 * so a receiver stays alive for it, and never for more than [MAX_WAIT].
 */
object Speak {
    private const val MAX_WAIT = 20_000L

    fun say(ctx: Context, text: String, locale: Locale = Locale.getDefault()) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val ready = CountDownLatch(1)
        var ok = false
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(ctx.applicationContext) { status -> ok = status == TextToSpeech.SUCCESS; ready.countDown() }
        if (!ready.await(5, TimeUnit.SECONDS) || !ok) { tts.shutdown(); return }
        try {
            val voice = if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) locale else Locale.ENGLISH
            tts.language = voice
            tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            val spoken = CountDownLatch(1)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) = spoken.countDown()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = spoken.countDown()
                override fun onError(id: String?, code: Int) = spoken.countDown()
            })
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "heatsafe") == TextToSpeech.SUCCESS)
                spoken.await(MAX_WAIT, TimeUnit.MILLISECONDS)
        } finally {
            tts.shutdown()
        }
    }
}
