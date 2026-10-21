# Mr. Rafay - Native Android App (Kotlin)

Foreground service (microphone) + Porcupine wake word "Mr. Rafay" + SpeechRecognizer + `/api/say` + TextToSpeech.
Flow: wake word (offline, screen off bhi) -> beep -> aap ka command -> `POST {server}/api/say` -> jawab bol kar -> dobara wake word.

## Files
```
settings.gradle.kts, build.gradle.kts, gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/mr_rafay.ppn      <- aap ki wake-word file (neeche step 1)
app/src/main/java/com/mrrafay/app/
  MainActivity.kt        settings UI + permissions + Start/Stop
  ForegroundService.kt   24/7 service, poora flow chalata hai
  WakeWordListener.kt    Porcupine (mic ka malik jab tak wake nahi hota)
  SpeechCapture.kt       wake ke baad command sunta hai
  ServerClient.kt        /api/say call (OkHttp)
  Speaker.kt             TTS (Arabic text par Arabic voice)
  Prefs.kt, Status.kt
```

## Setup
1. **Wake word (OPTIONAL - Picovoice key na ho to free Vosk engine khud chalta hai, neeche dekho)**: console.picovoice.ai par free account > AccessKey copy karo > Porcupine > phrase `Mister Rafay`
   (bol-chaal wala spelling behtar chalta hai) > Language English > Platform **Android** > Train > Download.
   File ka naam `mr_rafay.ppn` karke `app/src/main/assets/` me rakho. (File na ho to test ke liye "JARVIS" bolna hoga.)
   Picovoice ki free-tier terms/limits khud check kar lein.
2. **Build**: Android Studio (Iguana/naya) > Open > is folder ko kholo > Gradle sync >
   phone par USB debugging ON karke Run, ya Build > Build APK(s) aur APK phone me install karo.
   Agar `porcupine-android:3.0.1` na mile to Maven Central par latest version dekh kar `app/build.gradle.kts` me badal do.
3. **App me**: Server URL (https), RAFAY_TOKEN, Picovoice AccessKey bharo > **Save & Start** > mic + notification permission Allow.
4. **Battery**: "Battery: Unrestricted karo" dabao. Xiaomi/Oppo/Vivo/Samsung par Settings me app ko "Autostart" aur "No restrictions / Never sleeping" bhi do, warna OS service maar deta hai.
5. Test: screen band karo, phone rakh do, "Mr. Rafay" bolo -> beep -> "battery batao" -> jawab bol kar aayega.

## Server
Aap ka hub (`server.py`) ka `/api/say` endpoint use hota hai. Server URL `https://` hi chalega (app cleartext HTTP block karti hai).
Render free plan so jata hai: pehla jawab 30-60 sec le sakta hai (app 150 sec tak intezar karti hai) - UptimeRobot se `/health` ping karte raho.

## Sach (limits)
- Ye project yahan compile/test nahi ho saka (Android SDK nahi tha). Pehli build me chhoti compile errors aa sakti hain - Android Studio ka error text bhej dena, theek kar dunga.
- Reboot ke baad service khud start NAHI hoti: Android 14+ boot se microphone service start karne nahi deta. Phone restart ke baad app ek baar kholkar Start dabana hoga.
- Jab tak service chalti hai Android notification + mic ka green dot dikhata hai (OS ka rule). Mic hamesha chalne se battery zyada lagti hai.
- Call chal rahi ho ya doosri app mic le le to us waqt wake detection ruk jati hai; baad me app khud dobara start karti hai.
- Wake word aur "Hey Google" ek hi waqt mic share nahi karte; Google Assistant "Hey Google" band rakhna behtar hai.
- Token phone me private SharedPreferences me hai (plain). Phone lock/encryption on rakhein.

---
# UPDATE: Voice se app control (Accessibility) - Facebook / Messenger / Instagram / WhatsApp

## Flow
"Mr. Rafay, Facebook kholo, Siraj ki latest post par 'Mubarak ho' comment karo"
-> wake word + STT -> `/api/say` -> hub ka Claude brain -> `phone__*` tools -> **DeviceBridge** (app hub se long-poll karti hai)
-> **RafayAccessibilityService** screen padhta hai, tap/type/scroll karta hai -> brain har step ke baad screen dobara padhta hai -> jawab bol kar.
Brain loop me hub ab 40 steps tak chalta hai.

Phone tools: open_app, open_url (deep links), ui_read_screen, ui_click, ui_type, ui_scroll, ui_global (back/home/recents),
ui_tap, ui_swipe, ui_screenshot, ui_wait, wake_screen, whatsapp_send (number + text, Send khud dabata hai).
Messenger = com.facebook.orca, Facebook = com.facebook.katana, Instagram = com.instagram.android, WhatsApp = com.whatsapp.

## Setup (extra)
1. App me **Device token** = hub ka `DEVICE_TOKEN`, **Device name** = `phone`. (Termux wala device_agent usi naam se na chalayen.)
2. Button 1: Settings > Accessibility > Installed apps > **Mr. Rafay** > ON.
   Android 13+ par sideloaded app ke liye pehle: App info > teen nuqte (⋮) > **Allow restricted settings**, phir Accessibility ON.
3. Button 2: "Display over other apps" ON (Android 10+ background se apps kholne ke liye sab se pakka tareeqa).
4. Server taraf `server.py` + `brain.py` update (naya mr_rafay.zip) deploy karo.

## Sach (limits)
- Phone **unlocked aur screen on** ho. Lock screen par Accessibility kuch nahi kar sakta (PIN/fingerprint bypass nahi hoti). `wake_screen` sirf screen jagata hai.
- Facebook/Instagram/Messenger ki screens aksar adhoora accessibility tree deti hain (custom views, labels gayab). Kabhi kabhi `ui_tap` coordinates ya `ui_screenshot` chahiye hoga; app update par layout badalne se flow toot sakta hai. WhatsApp zyada stable hai.
- Banking/secure (FLAG_SECURE) screens padhne ya screenshot se block hoti hain.
- Ye personal-use tool hai. Meta apps ki shartein automation ko mana karti hain: apne account se, apne dost ko, ek ek kaam tak rakhein; bulk/loop (sab ko comment/message) se account restrict ho sakta hai.
- Play Store par Accessibility + automation wali app approve nahi hoti; sirf APK sideload.


---
# UPDATE: Picovoice key ke baghair (free offline wake word - Vosk)
- **AccessKey ab optional hai.** Field khali ho to app khud free offline **Vosk** engine use karti hai (koi account/email nahi).
  Pehli baar "Save & Start" dabane par ~40 MB ka chhota English model khud download hota hai (internet chahiye), phir service start.
- Pehle Start isliye nahi chal raha tha ke code AccessKey khali hone par Start rok deta tha (sirf upar status line me likhta tha). Ab roka nahi jata.
- **Wake words**: app ke "Wake words" field me comma se likhein (default: mister rafay, mr rafay, rafay, mister rafi, rafi, raffi, rafael, rafa).
  Vosk ki dictionary me "rafay" shayad na ho, isliye milte-julte lafz diye hain; jo lafz model me nahi hota wo khud ignore hota hai.
  Agar aap ka naam sahi pehchana na jaye to koi aam English lafz rakh lein (jaise "jarvis" ya "computer") ya "rafi" jaisa sound-alike.
- **Test**: "Abhi bolo" button (ya notification ka "Bolo" button) wake word ke baghair seedha mic kholta hai - isse pehle voice->server->jawab ka poora raasta check karein.
- Sach: Vosk Porcupine jitna sahi nahi; thori galat triggers/miss ho sakte hain, aur battery thori zyada lagti hai. Phir bhi key ke baghair ye sab se aasan raasta hai.
- Gradle me `com.sun.jna duplicate class` error aaye to `vosk-android` dependency me jna ka exclude pehle se laga hai; phir bhi na chale to Vosk ke Android demo ki build.gradle se milayein.
