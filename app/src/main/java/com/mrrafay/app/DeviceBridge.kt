package com.mrrafay.app

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hub se long-poll karta hai (/api/device/<name>/poll), tasks PhoneTools se chalata hai,
 * natija /result par bhejta hai. Router/port-forward ki zaroorat nahi.
 */
class DeviceBridge(private val ctx: Context, private val prefs: Prefs) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val worker = Executors.newSingleThreadExecutor()   // UI actions ek ek karke
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "rafay-bridge").also { it.isDaemon = true; it.start() }
    }

    fun stop() { running = false; thread?.interrupt(); worker.shutdownNow() }

    private fun req(path: String) = Request.Builder()
        .url(prefs.serverUrl.trimEnd('/') + path)
        .header("X-Device-Token", prefs.deviceToken)

    private fun register() {
        val body = JSONObject().put("schemas", PhoneTools.schemas()).toString().toRequestBody(json)
        http.newCall(req("/api/device/${prefs.deviceName}/register").post(body).build()).execute().use {
            if (!it.isSuccessful) throw IOException("register ${it.code}")
        }
    }

    private fun loop() {
        var registered = false
        while (running) {
            try {
                if (!registered) { register(); registered = true }
                http.newCall(req("/api/device/${prefs.deviceName}/poll").get().build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("poll ${r.code}")
                    val j = JSONObject(r.body?.string().orEmpty())
                    if (j.optBoolean("need_register")) { registered = false; return@use }
                    val arr = j.optJSONArray("tasks") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.getJSONObject(i)
                        worker.execute { handle(t) }
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                registered = false
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }
    }

    private fun handle(t: JSONObject) {
        val out = try {
            PhoneTools.run(ctx, t.getString("tool"), t.optJSONObject("args") ?: JSONObject())
        } catch (e: Exception) { "Error: ${e.message}" }
        val body = JSONObject().put("id", t.getString("id")).put("result", out.take(5000))
        val f = File(out.trim())
        if (f.isFile && f.length() < 5_000_000 && (out.endsWith(".jpg") || out.endsWith(".png"))) {
            body.put("file_b64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)).put("file_ext", f.extension)
            f.delete()
        }
        repeat(3) {
            try {
                http.newCall(req("/api/device/${prefs.deviceName}/result").post(body.toString().toRequestBody(json)).build())
                    .execute().close()
                return
            } catch (e: Exception) { Thread.sleep(3000) }
        }
    }
}
