package com.mrrafay.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var a11yView: TextView
    private lateinit var urlF: EditText
    private lateinit var tokenF: EditText
    private lateinit var keyF: EditText
    private lateinit var wakeF: EditText
    private lateinit var sttF: EditText
    private lateinit var ttsF: EditText
    private lateinit var dTokenF: EditText
    private lateinit var dNameF: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }
        fun field(hint: String, value: String, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint
            setText(value)
            setSingleLine()
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
        }

        root.addView(TextView(this).apply { text = "Mr. Rafay"; textSize = 26f })
        statusView = TextView(this).apply { text = Status.text; setPadding(0, pad / 2, 0, 0) }
        root.addView(statusView)
        a11yView = TextView(this).apply { setPadding(0, 0, 0, pad / 2) }
        root.addView(a11yView)

        // Sab se upar: Start/Stop taake hamesha nazar aayein
        root.addView(button("Save & Start") { save(); ensurePermsThenStart() })
        root.addView(button("Abhi bolo (mic test, wake word ke baghair)") { talkNow() })
        root.addView(button("Chat kholo (text se command)") { save(); startActivity(Intent(this, ChatActivity::class.java)) })
        root.addView(button("Stop") { stopService(Intent(this, RafayForegroundService::class.java)) })

        root.addView(label("Server URL (https://...up.railway.app / onrender.com)"))
        urlF = field("https://your-app.up.railway.app", prefs.serverUrl); root.addView(urlF)
        root.addView(label("Access token (RAFAY_TOKEN)"))
        tokenF = field("token", prefs.token, secret = true); root.addView(tokenF)
        root.addView(label("Device token (hub ka DEVICE_TOKEN) - app control ke liye"))
        dTokenF = field("DEVICE_TOKEN", prefs.deviceToken, secret = true); root.addView(dTokenF)
        root.addView(label("Device name (hub par, default phone)"))
        dNameF = field("phone", prefs.deviceName); root.addView(dNameF)
        root.addView(label("Wake words (free offline engine, comma se)"))
        wakeF = field("mister rafay,rafay,rafi", prefs.wakeWords); root.addView(wakeF)
        root.addView(label("Picovoice AccessKey - OPTIONAL (khali chhodein to free Vosk engine chalega)"))
        keyF = field("optional", prefs.accessKey, secret = true); root.addView(keyF)
        root.addView(label("Speech language (en-IN, en-GB, ar-SA, ur-PK ...)"))
        sttF = field("en-IN", prefs.sttLang); root.addView(sttF)
        root.addView(label("Voice-reply language (en-IN, en-US, ar ...)"))
        ttsF = field("en-IN", prefs.ttsLang); root.addView(ttsF)

        root.addView(button("1) Accessibility ON karo (app control)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("2) 'Display over other apps' do (apps kholne ke liye)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("3) Battery: Unrestricted karo") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        })
        root.addView(button("App info (Allow restricted settings ke liye)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        statusView.text = Status.text
        a11yView.text = "Accessibility: " + if (RafayAccessibilityService.instance != null) "ON ✔" else "OFF ✖"
        Status.listener = { t -> runOnUiThread { statusView.text = t } }
    }

    override fun onPause() {
        Status.listener = null
        super.onPause()
    }

    private fun save() {
        prefs.serverUrl = urlF.text.toString()
        prefs.token = tokenF.text.toString()
        prefs.deviceToken = dTokenF.text.toString()
        prefs.deviceName = dNameF.text.toString()
        prefs.wakeWords = wakeF.text.toString().ifBlank { "mister rafay,mr rafay,rafay" }
        prefs.accessKey = keyF.text.toString()
        prefs.sttLang = sttF.text.toString().ifBlank { "en-IN" }
        prefs.ttsLang = ttsF.text.toString().ifBlank { "en-IN" }
        Status.set("Settings save ho gayin")
        statusView.text = Status.text
    }

    private fun ensurePermsThenStart() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startFlow() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startFlow()
        else { Status.set("Mic permission zaroori hai"); statusView.text = Status.text }
    }

    /** Key na ho to pehle free Vosk model (~40 MB) download, phir service start. */
    private fun startFlow() {
        if (prefs.accessKey.isBlank() && !ModelManager.isReady(this)) {
            Status.set("Wake model download ho raha hai (~40 MB, internet chahiye)...")
            statusView.text = Status.text
            ModelManager.download(applicationContext) { err ->
                runOnUiThread {
                    if (err == null) startSvc()
                    else { Status.set("Model download fail: $err"); statusView.text = Status.text }
                }
            }
        } else startSvc()
    }

    private fun startSvc() {
        startForegroundService(Intent(this, RafayForegroundService::class.java))
    }

    private fun talkNow() {
        save()
        startForegroundService(
            Intent(this, RafayForegroundService::class.java).setAction(RafayForegroundService.ACTION_LISTEN)
        )
    }
}
