package com.mrrafay.app

/** Wake-word engine ka common interface (Porcupine ya Vosk). start() error text lautata hai ya null. */
interface WakeEngine {
    fun start(): String?
    fun stop()
    fun release()
}
