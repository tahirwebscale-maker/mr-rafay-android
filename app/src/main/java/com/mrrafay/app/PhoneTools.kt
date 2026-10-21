package com.mrrafay.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject

/** Hub ko dikhne wale phone tools (naam hub par "phone__<name>" ban jata hai) aur unka executor. */
object PhoneTools {

    private fun s(name: String, desc: String, vararg p: Triple<String, String, String>,
                  req: List<String> = emptyList()): JSONObject {
        val props = JSONObject()
        p.forEach { (n, t, d) -> props.put(n, JSONObject().put("type", t).put("description", d)) }
        return JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req))
        )
    }

    fun schemas(): JSONArray = JSONArray().apply {
        put(s("open_app", "Phone par app kholo (naam ya package: Facebook, Messenger, Instagram, WhatsApp, YouTube, com.whatsapp ...).",
            Triple("app", "string", "app ka naam ya package"), req = listOf("app")))
        put(s("open_url", "Link/deep link kholo (https://..., whatsapp://..., instagram://user?username=...). package optional.",
            Triple("url", "string", "link"), Triple("package", "string", "optional app package"), req = listOf("url")))
        put(s("ui_read_screen", "Maujooda screen ke elements padho: [index] class \"text\" desc hint id {click,edit,scroll} @x,y. Har action ke baad dobara padho."))
        put(s("ui_click", "Screen par element click karo: query (text/desc/hint/id ka hissa) ya index (ui_read_screen se). nth = kaun sa match (0 se).",
            Triple("query", "string", "text/desc/hint/id"), Triple("index", "integer", "ui_read_screen ka index"),
            Triple("nth", "integer", "match number, default 0")))
        put(s("ui_type", "Text field me text likho. target = field ka hint/text (optional), submit=true to Enter/Search dabao.",
            Triple("text", "string", "likhna hai"), Triple("target", "string", "field ka hint/naam"),
            Triple("submit", "boolean", "Enter dabana hai?"), req = listOf("text")))
        put(s("ui_scroll", "Scroll karo.", Triple("direction", "string", "down ya up")))
        put(s("ui_global", "System button: back, home, recents, notifications.",
            Triple("action", "string", "back|home|recents|notifications"), req = listOf("action")))
        put(s("ui_tap", "Coordinates par tap (jab element tree me na ho).",
            Triple("x", "number", "x"), Triple("y", "number", "y"), req = listOf("x", "y")))
        put(s("ui_swipe", "Swipe (x1,y1 -> x2,y2).",
            Triple("x1", "number", ""), Triple("y1", "number", ""), Triple("x2", "number", ""), Triple("y2", "number", ""),
            req = listOf("x1", "y1", "x2", "y2")))
        put(s("ui_screenshot", "Phone screen ka screenshot (Android 11+)."))
        put(s("ui_wait", "Screen par text aane ka intezar (max 30 sec), ya sirf seconds wait.",
            Triple("text", "string", "intezar wala text"), Triple("seconds", "integer", "max seconds")))
        put(s("wake_screen", "Screen on karo (lock screen ho to khud unlock karna hoga)."))
        put(s("whatsapp_send", "WhatsApp par number ko text bhejo (international number, bina +). Chat khol kar Send dabata hai.",
            Triple("number", "string", "e.g. 923001234567"), Triple("text", "string", "message"),
            req = listOf("number", "text")))
    }

    fun run(ctx: Context, name: String, a: JSONObject): String {
        when (name) {
            "open_app" -> return openApp(ctx, a.getString("app"))
            "open_url" -> return openUrl(ctx, a.getString("url"), a.optString("package", ""))
            "wake_screen" -> return wakeScreen(ctx)
        }
        val svc = RafayAccessibilityService.instance
            ?: return "Accessibility service ON nahi hai (Settings > Accessibility > Mr. Rafay)"
        return when (name) {
            "ui_read_screen" -> svc.readScreen()
            "ui_click" -> svc.click(a.optString("query", ""), if (a.has("index")) a.getInt("index") else null, a.optInt("nth", 0))
            "ui_type" -> svc.typeText(a.getString("text"), a.optString("target", "").ifBlank { null }, a.optBoolean("submit", false))
            "ui_scroll" -> svc.scroll(a.optString("direction", "down"))
            "ui_global" -> svc.global(a.getString("action"))
            "ui_tap" -> if (svc.tap(a.getDouble("x").toFloat(), a.getDouble("y").toFloat())) "tapped" else "tap fail"
            "ui_swipe" -> if (svc.swipe(a.getDouble("x1").toFloat(), a.getDouble("y1").toFloat(),
                a.getDouble("x2").toFloat(), a.getDouble("y2").toFloat(), 350)) "swiped" else "swipe fail"
            "ui_screenshot" -> svc.screenshotFile() ?: "screenshot fail (Android 11+ chahiye ya screen secure hai)"
            "ui_wait" -> svc.waitFor(a.optString("text", ""), a.optInt("seconds", 3))
            "whatsapp_send" -> whatsappSend(ctx, svc, a.getString("number"), a.getString("text"))
            else -> "Unknown tool $name"
        }
    }

    // ---------- intents ----------
    private fun launch(ctx: Context, i: Intent): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Accessibility service ke context se launch (background restriction se bachne ke liye)
        val c: Context = RafayAccessibilityService.instance ?: ctx
        return try { c.startActivity(i); "opened" } catch (e: Exception) { "open fail: ${e.message}" }
    }

    private fun openApp(ctx: Context, q: String): String {
        val pm = ctx.packageManager
        pm.getLaunchIntentForPackage(q)?.let { return launch(ctx, it) }
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(main, 0)
            .firstOrNull { it.loadLabel(pm).toString().contains(q, ignoreCase = true) }
            ?: return "App nahi mili: $q"
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "launch intent nahi mila"
        return launch(ctx, li)
    }

    private fun openUrl(ctx: Context, url: String, pkg: String): String {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (pkg.isNotBlank()) i.setPackage(pkg)
        return launch(ctx, i)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context): String {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "rafay:screen")
            .acquire(10_000)
        return "screen on kiya (lock screen ho to khud unlock karna hoga)"
    }

    private fun whatsappSend(ctx: Context, svc: RafayAccessibilityService, number: String, text: String): String {
        val digits = number.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(text))
        val r = launch(ctx, Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        if (r != "opened") return r
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(800)
            if (svc.click("send", null, 0) == "clicked") return "message bhej diya ($digits)"
        }
        return "Send button nahi mila - ui_read_screen se dekho"
    }
}
