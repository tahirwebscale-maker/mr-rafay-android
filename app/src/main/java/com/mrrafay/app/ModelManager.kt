package com.mrrafay.app

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Vosk ka chhota English model (~40 MB) pehli baar khud download karta hai. Koi account/key nahi. */
object ModelManager {
    private const val DIR_NAME = "vosk-model"
    private const val URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    fun dir(ctx: Context) = File(ctx.filesDir, DIR_NAME)
    fun isReady(ctx: Context) = File(dir(ctx), ".ready").exists()

    /** Background thread par chalta hai; onDone(null) = kamyab, warna error text. */
    fun download(ctx: Context, onDone: (String?) -> Unit) {
        Thread {
            val err = try { doDownload(ctx); null } catch (e: Exception) { e.message ?: "download fail" }
            onDone(err)
        }.start()
    }

    private class Counting(inp: InputStream, val onBytes: (Long) -> Unit) : FilterInputStream(inp) {
        private var n = 0L
        override fun read(): Int { val b = super.read(); if (b >= 0) bump(1); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val k = super.read(b, off, len); if (k > 0) bump(k.toLong()); return k
        }
        private fun bump(k: Long) { n += k; onBytes(n) }
    }

    private fun doDownload(ctx: Context) {
        val target = dir(ctx)
        target.deleteRecursively(); target.mkdirs()
        val root = target.canonicalPath + File.separator
        http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("download error ${r.code}")
            val body = r.body ?: throw IOException("khali jawab")
            val total = body.contentLength()
            var lastPct = -1
            val counting = Counting(body.byteStream()) { n ->
                if (total > 0) {
                    val pct = (n * 100 / total).toInt()
                    if (pct != lastPct && pct % 5 == 0) { lastPct = pct; Status.set("Wake model download: $pct%") }
                }
            }
            ZipInputStream(counting.buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val name = e.name.substringAfter('/', "")        // upar wala folder hata do
                    if (name.isNotEmpty()) {
                        val out = File(target, name)
                        if (!out.canonicalPath.startsWith(root)) throw IOException("zip path galat")
                        if (e.isDirectory) out.mkdirs()
                        else { out.parentFile?.mkdirs(); out.outputStream().use { zin.copyTo(it) } }
                    }
                    e = zin.nextEntry
                }
            }
        }
        File(target, ".ready").writeText("ok")
        Status.set("Wake model tayyar")
    }
}
