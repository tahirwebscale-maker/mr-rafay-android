package com.mrrafay.app

/** Service aur Activity ke beech chhota status channel. */
object Status {
    @Volatile var text: String = "Band hai"
    @Volatile var listener: ((String) -> Unit)? = null
    fun set(t: String) { text = t; listener?.invoke(t) }
}
