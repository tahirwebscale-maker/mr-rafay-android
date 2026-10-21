package com.mrrafay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Free, offline wake word (koi account/AccessKey nahi). Vosk ko sirf wake words ki grammar di jati hai:
 * jaise hi unme se koi lafz sunai de, wake fire hota hai. Prefs.wakeWords me comma se alfaaz badal sakte hain
 * (jo lafz model ki dictionary me nahi hota Vosk usay khud ignore kar deta hai).
 */
class VoskWakeListener(
    private val ctx: Context,
    private val prefs: Prefs,
    private val onWake: () -> Unit
) : WakeEngine {

    private var model: Model? = null
    private var rec: Recognizer? = null
    private var service: SpeechService? = null
    @Volatile private var fired = false

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = check(hypothesis, "partial")
        override fun onResult(hypothesis: String?) = check(hypothesis, "text")
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) { Status.set("Vosk error: ${exception?.message}") }
        override fun onTimeout() {}
    }

    private fun check(json: String?, key: String) {
        if (fired || json == null) return
        val t = try { JSONObject(json).optString(key) } catch (_: Exception) { "" }
        // grammar me sirf wake words + [unk] hain; [unk] ke ilawa kuch mila to wake word suna gaya
        if (t.replace("[unk]", "").isBlank()) return
        fired = true
        onWake()
    }

    override fun start(): String? {
        return try {
            if (!ModelManager.isReady(ctx)) return "Wake model download nahi hua - app me Save & Start dabayein (internet chahiye)"
            val m = model ?: Model(ModelManager.dir(ctx).absolutePath).also { model = it }
            val words = prefs.wakeWords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val grammar = JSONArray(words + "[unk]").toString()
            val r = Recognizer(m, 16000.0f, grammar)
            val svc = SpeechService(r, 16000.0f)
            fired = false
            if (!svc.startListening(listener)) {
                svc.shutdown(); r.close()
                return "Mic nahi mila (koi aur app use kar rahi hai?)"
            }
            rec = r; service = svc
            null
        } catch (e: Exception) {
            e.message ?: "Vosk error"
        }
    }

    override fun stop() {
        try { service?.stop() } catch (_: Exception) {}
        try { service?.shutdown() } catch (_: Exception) {}
        service = null
        try { rec?.close() } catch (_: Exception) {}
        rec = null
    }

    override fun release() {
        stop()
        try { model?.close() } catch (_: Exception) {}
        model = null
    }
}
