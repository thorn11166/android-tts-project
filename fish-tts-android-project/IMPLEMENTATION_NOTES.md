# FishTtsService — Implementation Notes

## File: `app/src/main/kotlin/com/yourapp/service/FishTtsService.kt`

This is the heart of the app. Key things to know:

### Service Lifecycle
- Android binds to this service via the `BIND_TEXT_TO_SPEECH_SERVICE` permission
- `onSynthesizeText` is called on a **background thread** — safe to do blocking/suspending network calls
- `onStop` can be called at **any time**, including mid-synthesis — always cancel in-flight requests

### SynthesisCallback Contract (must follow exactly)
```
callback.start(sampleRate, encoding, channels)
  → callback.audioAvailable(bytes, offset, length)  [1 to N times]
  → callback.done()
OR
  → callback.error()
```

Never call `audioAvailable` before `start`. Never call `done` or `error` more than once.

### Coroutine Scope
Create a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` as a class field.
Cancel it in `onDestroy()`. Use it for all network calls.

### Cancellation Pattern
```kotlin
private var activeJob: Job? = null

override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
    activeJob?.cancel()
    activeJob = scope.launch {
        // ... synthesize
    }
}

override fun onStop() {
    activeJob?.cancel()
}
```

---

## File: `app/src/main/kotlin/com/yourapp/api/FishAudioApiService.kt`

### OkHttp Setup
```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

### TTS Request Body (kotlinx.serialization)
```kotlin
@Serializable
data class TtsRequest(
    val text: String,
    @SerialName("reference_id") val referenceId: String,
    val format: String = "pcm",
    val latency: String = "balanced",
    val prosody: Prosody = Prosody()
)

@Serializable
data class Prosody(
    val speed: Float = 1.0f,
    val volume: Int = 0
)
```

### Streaming PCM to SynthesisCallback
```kotlin
// Read response body in chunks and feed to callback
val responseBody = response.body ?: return callback.error()
callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
val buffer = ByteArray(4096)
val stream = responseBody.byteStream()
var bytesRead: Int
while (stream.read(buffer).also { bytesRead = it } != -1) {
    if (!isActive) break  // coroutine cancelled
    callback.audioAvailable(buffer, 0, bytesRead)
}
callback.done()
```

---

## File: `app/src/main/res/xml/tts_engine.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<tts-engine
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.yourapp.ui.SettingsActivity" />
```

---

## File: `app/src/main/AndroidManifest.xml` (service block)

```xml
<service
    android:name=".service.FishTtsService"
    android:label="@string/tts_engine_name"
    android:permission="android.permission.BIND_TEXT_TO_SPEECH_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent-filter>
    <meta-data
        android:name="android.speech.tts"
        android:resource="@xml/tts_engine" />
</service>

<activity
    android:name=".ui.SettingsActivity"
    android:label="@string/settings_title"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
    </intent-filter>
</activity>
```

---

## Language Support Map

Map Fish Audio supported languages to Android locale codes:

```kotlin
val supportedLanguages = mapOf(
    "en" to Locale.ENGLISH,
    "ja" to Locale.JAPANESE,
    "ko" to Locale.KOREAN,
    "zh" to Locale.CHINESE,
    "fr" to Locale.FRENCH,
    "de" to Locale.GERMAN,
    "ar" to Locale("ar"),
    "es" to Locale("es")
)

override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int {
    return if (supportedLanguages.containsKey(lang.lowercase())) {
        TextToSpeech.LANG_COUNTRY_AVAILABLE
    } else {
        TextToSpeech.LANG_NOT_SUPPORTED
    }
}
```

---

## build.gradle.kts Dependencies

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("io.coil-kt:coil:2.6.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
}

plugins {
    kotlin("plugin.serialization") version "1.9.23"
}
```
