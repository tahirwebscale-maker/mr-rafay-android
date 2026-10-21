package com.mrrafay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.util.concurrent.Executors

/**
 * 24/7 foreground service (microphone type):
 * wake word sunta hai -> beep -> command sunta hai -> /api/say -> TTS se bolta hai -> dobara wake word.
 */
class RafayForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "com.mrrafay.app.STOP"
        const val ACTION_LISTEN = "com.mrrafay.app.LISTEN"
        private const val CHANNEL = "rafay"
        private const val NOTIF_ID = 1
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private var wake: WakeEngine? = null
    private var speech: SpeechCapture? = null
    private var speaker: Speaker? = null
    private var wl: PowerManager.WakeLock? = null
    private var bridge: DeviceBridge? = null
    private var started = false
    @Volatile private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        try {
            goForeground("Mr. Rafay sun raha hai")
        } catch (e: Exception) {
            // Android 12+/14: background se mic service start allowed nahi -> app kholkar dobara Start dabayein
            Status.set("Service start nahi hui: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) { started = true; init() }
        if (intent?.action == ACTION_LISTEN) main.post { onWakeWord() }   // notification/app button se manual 'bolo'
        return START_STICKY
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mr. Rafay", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RafayForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getService(
            this, 2, Intent(this, RafayForegroundService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Mr. Rafay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_btn_speak_now, "Bolo", talk)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun init() {
        prefs = Prefs(this)
        // Screen off par CPU jaga rakhne ke liye (battery zyada khata hai)
        wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rafay:wake")
            .apply { setReferenceCounted(false); acquire() }
        speaker = Speaker(this) { prefs.ttsLang }
        speech = SpeechCapture(this)
        // AccessKey ho to Porcupine, warna free offline Vosk
        wake = if (prefs.accessKey.isNotBlank()) WakeWordListener(this, prefs) { main.post { onWakeWord() } }
               else VoskWakeListener(this, prefs) { main.post { onWakeWord() } }
        if (prefs.deviceToken.isNotBlank() && prefs.serverUrl.isNotBlank()) {
            bridge = DeviceBridge(this, prefs).also { it.start() }   // phone UI tools hub se jurte hain
        }
        startWake()
    }

    private fun startWake() {
        val err = wake?.start()
        if (err != null) Status.set("Wake word error: $err")
        else Status.set("Sun raha hoon - 'Mr. Rafay' boliye")
    }

    private fun onWakeWord() {
        if (busy) return
        busy = true
        wake?.stop()                       // mic SpeechRecognizer ke liye khali karo
        Status.set("Ji, boliye...")
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
        main.postDelayed({
            val s = speech
            if (s == null) { resume(); return@postDelayed }
            s.listen(
                prefs.sttLang,
                onText = { text -> handleText(text) },
                onFail = { why -> Status.set("Suna nahi ($why)"); resume() }
            )
        }, 350)
    }

    private fun handleText(text: String) {
        Status.set("Aap: $text")
        io.execute {
            val reply = try {
                ServerClient.say(prefs.serverUrl, prefs.token, text)
            } catch (e: Exception) {
                "Server se rabta nahi hua"
            }
            main.post {
                Status.set("Rafay: ${reply.take(200)}")
                val sp = speaker
                if (sp == null) resume() else sp.speak(reply) { main.post { resume() } }
            }
        }
    }

    private fun resume() {
        busy = false
        startWake()
    }

    override fun onDestroy() {
        bridge?.stop()
        wake?.release()
        speech?.destroy()
        speaker?.shutdown()
        try { wl?.release() } catch (_: Exception) {}
        io.shutdownNow()
        Status.set("Band hai")
        super.onDestroy()
    }
}
