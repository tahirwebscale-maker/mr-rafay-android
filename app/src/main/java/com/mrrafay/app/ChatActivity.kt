package com.mrrafay.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Text chat: EditText + Send -> POST {server}/api/chat -> jawab bubble me. Server URL/token main screen se. */
class ChatActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var log: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()

        log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        scroll = ScrollView(this).apply { addView(log) }
        input = EditText(this).apply {
            hint = "Command likho..."
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { send(); true } else false
            }
        }
        sendBtn = Button(this).apply { text = "Send"; setOnClickListener { send() } }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendBtn)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row)
        }
        setContentView(root)
        add(false, "Salaam! Command likhein, main server par bhej dunga.")
    }

    private fun add(mine: Boolean, msg: String): TextView {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(if (mine) Color.WHITE else Color.BLACK)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(if (mine) 0xFF2B5CFF.toInt() else 0xFFE6E8EE.toInt())
            }
            setTextIsSelectable(true)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            topMargin = (6 * dp).toInt()
        }
        log.addView(tv, lp)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        return tv
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        add(true, t)
        val pending = add(false, "...")
        sendBtn.isEnabled = false
        io.execute {
            val reply = try {
                ServerClient.chat(prefs.serverUrl, prefs.token, t)
            } catch (e: Exception) {
                "Server se rabta nahi hua: ${e.message}"
            }
            runOnUiThread {
                pending.text = reply
                sendBtn.isEnabled = true
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
