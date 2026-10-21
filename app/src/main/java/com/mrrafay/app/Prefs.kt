package com.mrrafay.app

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rafay", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim()).apply()
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()
    var accessKey: String
        get() = sp.getString("pv", "") ?: ""
        set(v) = sp.edit().putString("pv", v.trim()).apply()
    var sttLang: String
        get() = sp.getString("stt", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("stt", v.trim()).apply()
    var ttsLang: String
        get() = sp.getString("tts", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("tts", v.trim()).apply()
    /** Vosk wake words (comma se). Jo lafz model me nahi hota wo ignore ho jata hai. */
    var wakeWords: String
        get() = sp.getString("wake", "mister rafay,mr rafay,rafay,mister rafi,rafi,raffi,rafael,rafa") ?: ""
        set(v) = sp.edit().putString("wake", v.trim()).apply()
    /** Hub ka DEVICE_TOKEN (device bridge ke liye) */
    var deviceToken: String
        get() = sp.getString("dtoken", "") ?: ""
        set(v) = sp.edit().putString("dtoken", v.trim()).apply()
    var deviceName: String
        get() = sp.getString("dname", "phone") ?: "phone"
        set(v) = sp.edit().putString("dname", v.trim().ifBlank { "phone" }).apply()
}
