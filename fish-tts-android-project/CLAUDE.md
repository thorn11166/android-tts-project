# CLAUDE.md — Claude Code Instructions

## Project: Fish Audio Android TTS Provider

This file tells Claude Code everything it needs to know to work on this project.

---

## What We're Building

A **standalone Android system TTS engine** that uses the Fish Audio cloud API for speech synthesis. It registers as a native TTS provider in Android settings, meaning any app on the device can use it — accessibility services, navigation, screen readers, etc.

Read `PROJECT_BRIEF.md` in full before writing any code. It contains the complete architecture, API contracts, audio pipeline design, and implementation priority order.

---

## Forked Reference Repositories

These are on Justin's GitHub. Clone or reference them as needed:

- **`fish-audio-python`** — Primary API reference. All endpoint shapes, auth patterns, streaming, error types are here. You're re-implementing this in Kotlin.
- **`docs`** — Official API documentation. Check here for current model IDs, request schemas, and any endpoint you're unsure about.
- **`fish-speech`** — The underlying TTS model. Reference for understanding capabilities and potential future on-device mode.
- **`fish-audio-typescript`** — Secondary SDK reference for cross-checking API behavior.
- **`fish-audio-go`** — Tertiary reference.

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) — required for modern TTS service APIs
- **Target SDK:** 35
- **Build system:** Gradle (Kotlin DSL preferred)
- **Key libraries:** OkHttp, kotlinx.serialization, DataStore, Kotlin Coroutines, Coil, Material Components

---

## Critical Android Contract

The entire app hinges on correctly implementing `TextToSpeechService`. The three non-negotiable methods:

```kotlin
override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
    // 1. Call Fish Audio API with request.charSequenceText
    // 2. Feed PCM bytes to callback.audioAvailable()
    // 3. Call callback.done() when finished
    // 4. Call callback.error() on failure
}

override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int {
    // Return LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, or LANG_NOT_SUPPORTED
}

override fun onStop() {
    // Cancel any in-flight HTTP/WebSocket request immediately
}
```

Always call `callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)` before any `audioAvailable()` calls.

---

## Audio Format

Request `"format": "pcm"` from the Fish Audio API. Do NOT request MP3 or Opus — that would require decoding before feeding to `SynthesisCallback`, adding unnecessary complexity.

Expected PCM parameters:
- Sample rate: **24000 Hz**
- Encoding: `AudioFormat.ENCODING_PCM_16BIT`
- Channels: **Mono (1)**

---

## API Base URL

```
https://api.fish.audio
```

Streaming WebSocket:
```
wss://api.fish.audio/v1/tts/live
```

Auth header on every request:
```
Authorization: Bearer {api_key}
```

---

## Preferences Keys (DataStore)

Use these keys consistently:
```kotlin
object PrefKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
    val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
    val MODEL = stringPreferencesKey("model")           // "s1", "s2", "s2-pro"
    val LATENCY = stringPreferencesKey("latency")       // "normal", "balanced"
    val SPEED = floatPreferencesKey("speed")            // 0.5 - 2.0
    val VOLUME = intPreferencesKey("volume")            // dB offset
}
```

---

## Implementation Order

Work through this sequence — don't skip ahead:

1. **`FishTtsService.kt`** — Bind the service, hardcode a test voice ID and API key, verify Android picks it up as a TTS engine
2. **`FishAudioApiService.kt`** — REST client, PCM synthesis, basic error handling
3. **`PreferencesManager.kt`** + **`SettingsActivity.kt`** — Replace hardcoded values with DataStore-backed settings
4. **`FishAudioWebSocketClient.kt`** — Streaming WebSocket replaces REST call for lower latency
5. **`VoiceRepository.kt`** — Fetch + cache voice list from `GET /v1/model`
6. **`VoicePickerActivity.kt`** — UI for browsing and selecting voices
7. **Polish** — Android `Voice` object mapping, language availability, rate limit handling, caching TTL

---

## Build & CI

A GitHub Actions workflow for automated APK builds is described in `PROJECT_BRIEF.md`. Set it up early so every push produces a downloadable APK artifact.

---

## Notes from Prior Work

- Justin has experience with Android TTS server apps (TTS Server Android + Fish.audio plugin), Flutter/GetX apps, and GitHub Actions APK build pipelines. Code style and patterns consistent with those projects are preferred.
- Favor coroutines over RxJava.
- Material Design 3 for UI.
- No hardcoded API keys in committed code — always from DataStore.
