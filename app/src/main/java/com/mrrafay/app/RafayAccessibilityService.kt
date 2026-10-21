package com.mrrafay.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Screen padhna + tap/type/scroll/swipe/screenshot. Ye sab calls background thread se karein
 * (DeviceBridge ka worker thread karta hai). Voice -> hub brain -> phone__ui_* tools -> yahan.
 */
class RafayAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: RafayAccessibilityService? = null
    }

    private val lastNodes = ArrayList<AccessibilityNodeInfo>()

    override fun onServiceConnected() { instance = this; Status.set("Accessibility ON") }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }

    // ---------- helpers ----------
    private fun label(n: AccessibilityNodeInfo): String =
        n.text?.toString() ?: n.contentDescription?.toString() ?: ""

    private fun matches(n: AccessibilityNodeInfo, ql: String): Boolean =
        n.text?.toString()?.lowercase()?.contains(ql) == true ||
        n.contentDescription?.toString()?.lowercase()?.contains(ql) == true ||
        n.hintText?.toString()?.lowercase()?.contains(ql) == true ||
        n.viewIdResourceName?.lowercase()?.contains(ql) == true

    private fun collect(pred: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.isVisibleToUser && pred(n)) out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return out
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
        }
        val r = Rect(); n.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    // ---------- read ----------
    fun readScreen(max: Int = 120): String {
        val root = rootInActiveWindow ?: return "Screen padh nahi sakta (locked ya secure screen?)"
        val sb = StringBuilder("pkg=${root.packageName}\n")
        lastNodes.clear()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || lastNodes.size >= max || !n.isVisibleToUser) return
            val t = n.text?.toString()?.take(80)
            val d = n.contentDescription?.toString()?.take(80)
            val h = n.hintText?.toString()?.take(60)
            if (!t.isNullOrBlank() || !d.isNullOrBlank() || !h.isNullOrBlank() || n.isClickable || n.isEditable) {
                val r = Rect(); n.getBoundsInScreen(r)
                val flags = listOfNotNull(
                    if (n.isClickable) "click" else null,
                    if (n.isEditable) "edit" else null,
                    if (n.isScrollable) "scroll" else null,
                    if (n.isChecked) "checked" else null
                ).joinToString(",")
                lastNodes.add(n)
                sb.append("[${lastNodes.size - 1}] ${n.className?.toString()?.substringAfterLast('.')} ")
                  .append("\"${t.orEmpty()}\" desc=\"${d.orEmpty()}\" hint=\"${h.orEmpty()}\" ")
                  .append("id=${n.viewIdResourceName?.substringAfter(':').orEmpty()} {$flags} @${r.centerX()},${r.centerY()}\n")
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString().take(8000)
    }

    // ---------- act ----------
    fun click(query: String, index: Int?, nth: Int): String {
        val node: AccessibilityNodeInfo? = if (index != null) {
            lastNodes.getOrNull(index)?.also { it.refresh() }
        } else {
            val ql = query.lowercase()
            val all = collect { matches(it, ql) }
            val exact = all.filter { label(it).equals(query, ignoreCase = true) }
            (if (exact.isNotEmpty()) exact else all).getOrNull(nth)
        }
        val nd = node ?: return "nahi mila: $query"
        return if (clickNode(nd)) "clicked" else "click fail"
    }

    fun typeText(text: String, target: String?, submit: Boolean): String {
        val byTarget = if (target != null) collect { it.isEditable && matches(it, target.lowercase()) }.firstOrNull() else null
        val node = byTarget
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: collect { it.isEditable }.firstOrNull()
            ?: return "Koi text field nahi mila (pehle search/comment box par click karo)"
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) return "type fail"
        if (submit) {
            if (Build.VERSION.SDK_INT >= 30) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } else return "typed (submit ke liye Send/Search button click karo)"
        }
        return "typed"
    }

    fun scroll(dir: String): String {
        val sc = collect { it.isScrollable }.firstOrNull()
        val action = if (dir == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        if (sc != null && sc.performAction(action)) return "scrolled"
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f; val h = dm.heightPixels.toFloat()
        val ok = if (dir == "up") swipe(x, h * 0.3f, x, h * 0.75f, 300) else swipe(x, h * 0.75f, x, h * 0.3f, 300)
        return if (ok) "swiped" else "scroll fail"
    }

    fun global(action: String): String {
        val a = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return "unknown action"
        }
        return if (performGlobalAction(a)) "ok" else "fail"
    }

    fun waitFor(text: String, seconds: Int): String {
        val end = System.currentTimeMillis() + seconds.coerceIn(1, 30) * 1000L
        val ql = text.lowercase()
        while (System.currentTimeMillis() < end) {
            if (ql.isNotBlank() && collect { matches(it, ql) }.isNotEmpty()) return "mil gaya: $text"
            Thread.sleep(500)
        }
        return if (ql.isBlank()) "wait poora" else "nahi aaya: $text"
    }

    // ---------- gestures ----------
    fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durMs: Long): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, durMs)

    private fun gesture(path: Path, durMs: Long): Boolean {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durMs)).build()
        val latch = CountDownLatch(1)
        var ok = false
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(d: GestureDescription?) { latch.countDown() }
        }, null)
        latch.await(4, TimeUnit.SECONDS)
        return ok
    }

    // ---------- screenshot (Android 11+) ----------
    fun screenshotFile(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        var path: String? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hb = result.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp != null) {
                        val f = File(cacheDir, "shot_${System.currentTimeMillis()}.jpg")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 60, it) }
                        path = f.absolutePath
                    }
                } finally { latch.countDown() }
            }
            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(6, TimeUnit.SECONDS)
        return path
    }
}
