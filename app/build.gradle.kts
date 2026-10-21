plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrrafay.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mrrafay.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }   // APK chhota
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Latest version Maven Central par "ai.picovoice:porcupine-android" search karke update kar lein
    implementation("ai.picovoice:porcupine-android:3.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Free offline wake word (koi key nahi): Vosk. jna ko aar ke taur par alag se, duplicate se bachne ke liye exclude
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}
