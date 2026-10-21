package com.mrrafay.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Server ka jawab bol kar sunata hai. Arabic text ho to Arabic voice. */
class Speaker(ctx: Context, private val lang: () -> String) {
    private var ready = false
    private var pending: (() -> Unit)? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { fire() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { fire() }
                })
            }
        }
    }

    private fun fire() { val p = pending; pending = null; p?.invoke() }

    fun speak(text: String, onDone: () -> Unit) {
        val clean = text.replace(Regex("https?://\\S+"), "").trim().ifEmpty { "Theek hai" }.take(1500)
        if (!ready) { onDone(); return }
        val arabic = clean.any { it in '\u0600'..'\u06FF' }
        tts.language = if (arabic) Locale("ar") else Locale.forLanguageTag(lang())
        pending = onDone
        if (tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "rafay") != TextToSpeech.SUCCESS) fire()
    }

    fun shutdown() { try { tts.stop(); tts.shutdown() } catch (_: Exception) {} }
}
