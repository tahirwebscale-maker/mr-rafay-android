package com.mrrafay.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Wake ke baad ek command sunta hai. Sab callbacks main thread par. */
class SpeechCapture(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var timeout: Runnable? = null

    fun listen(lang: String, onText: (String) -> Unit, onFail: (String) -> Unit) {
        destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onFail("recognizer nahi mila"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        var done = false

        fun finish(block: () -> Unit) {
            if (done) return
            done = true
            timeout?.let { main.removeCallbacks(it) }
            main.post {
                try { r.cancel(); r.destroy() } catch (_: Exception) {}
                if (rec === r) rec = null
            }
            block()
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) { finish { onFail("error $error") } }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish { if (t.isNullOrBlank()) onFail("khali") else onText(t) }
            }
        })

        val tmo = Runnable { finish { onFail("timeout") } }
        timeout = tmo
        main.postDelayed(tmo, 15_000)

        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        })
    }

    fun destroy() {
        timeout?.let { main.removeCallbacks(it) }
        rec?.let { try { it.cancel(); it.destroy() } catch (_: Exception) {} }
        rec = null
    }
}
