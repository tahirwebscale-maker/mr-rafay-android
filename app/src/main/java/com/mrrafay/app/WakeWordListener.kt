package com.mrrafay.app

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import java.io.File

/**
 * Porcupine wake-word detector (on-device, offline).
 * assets/mr_rafay.ppn ho to wohi, warna test ke liye built-in "JARVIS".
 * start() error text lautata hai (ya null agar sab theek).
 */
class WakeWordListener(
    private val ctx: Context,
    private val prefs: Prefs,
    private val onWake: () -> Unit
) : WakeEngine {
    private var mgr: PorcupineManager? = null

    override fun start(): String? = try {
        if (mgr == null) mgr = build()
        mgr!!.start()
        null
    } catch (e: PorcupineException) {
        e.message ?: "Porcupine error"
    } catch (e: Exception) {
        e.message ?: "wake word error"
    }

    /** Mic chhodne ke liye (SpeechRecognizer se pehle). */
    override fun stop() { try { mgr?.stop() } catch (_: Exception) {} }

    override fun release() {
        try { mgr?.stop() } catch (_: Exception) {}
        try { mgr?.delete() } catch (_: Exception) {}
        mgr = null
    }

    private fun build(): PorcupineManager {
        val builder = PorcupineManager.Builder()
            .setAccessKey(prefs.accessKey)
            .setSensitivity(0.7f)
        val ppn = copyAsset("mr_rafay.ppn")
        if (ppn != null) builder.setKeywordPath(ppn)
        else builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
        return builder.build(ctx, PorcupineManagerCallback { _ -> onWake() })
    }

    private fun copyAsset(name: String): String? {
        if (ctx.assets.list("")?.contains(name) != true) return null
        val out = File(ctx.filesDir, name)
        ctx.assets.open(name).use { input ->
            if (!out.exists() || out.length() != input.available().toLong()) {
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return out.absolutePath
    }
}
