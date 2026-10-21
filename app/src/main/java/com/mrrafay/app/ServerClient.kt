package com.mrrafay.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** POST {server}/api/say  (Authorization: Bearer <RAFAY_TOKEN>)  ->  plain text jawab. */
object ServerClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)   // free hosting cold start + device tasks
        .build()

    fun say(baseUrl: String, token: String, text: String): String {
        if (baseUrl.isBlank()) return "Server URL set nahi hai"
        val body = JSONObject().put("text", text).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/say")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            return if (r.isSuccessful) s.ifBlank { "Theek hai" } else "Server error ${r.code}"
        }
    }

    /** POST {server}/api/chat  ->  {"reply": "..."} */
    fun chat(baseUrl: String, token: String, text: String): String {
        if (baseUrl.isBlank()) return "Server URL set nahi hai (main screen par save karein)"
        val body = JSONObject().put("text", text).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/chat")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (r.code == 401) return "Token galat hai (main screen par RAFAY_TOKEN check karein)"
            if (!r.isSuccessful) return "Server error ${r.code}"
            return try { JSONObject(s).optString("reply", "").ifBlank { "Theek hai" } } catch (e: Exception) { s }
        }
    }
}
