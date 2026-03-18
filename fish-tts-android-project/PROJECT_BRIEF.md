# Fish Audio Android TTS Provider — Project Brief

## Overview

This project is a **standalone Android system TTS (Text-to-Speech) provider** backed by the Fish Audio cloud API. When installed, it registers itself as a selectable TTS engine in Android's system settings (alongside Google TTS, etc.), allowing any app on the device — including accessibility services, navigation apps, screen readers, and third-party apps — to use Fish Audio voices for speech synthesis.

---

## Forked Repositories (on Justin's GitHub)

The following Fish Audio repositories have been forked and should be referenced for API contracts, SDK patterns, and documentation:

### Essential
| Repo | Purpose |
|---|---|
| `fishaudio/fish-audio-python` | Primary API reference. Full SDK implementing TTS conversion, streaming WebSocket, voice cloning, voice model management, prosody controls, error handling. Re-implement in Kotlin for Android. |
| `fishaudio/docs` | Official Fish Audio API docs (MDX). Contains authentication, endpoint specs, model IDs (`s1`, `s2`, `s2-pro`), emotion/tone tag syntax, latency modes, and request/response schemas. |
| `fishaudio/fish-speech` | The underlying SOTA TTS model. Useful for understanding model capabilities, multi-speaker tokens, and as a reference for a potential future on-device/self-hosted mode. |

### Useful Reference
| Repo | Purpose |
|---|---|
| `fishaudio/fish-audio-typescript` | Secondary SDK reference. Cross-referencing with Python SDK clarifies ambiguous API behavior. |
| `fishaudio/fish-audio-go` | Tertiary SDK reference. Lower priority but useful for edge case resolution. |

---

## Architecture

### Android Component: `TextToSpeechService`

The app extends Android's `TextToSpeechService` — **not** `TextToSpeech`. This is what registers it as a system engine.

**Required method implementations:**
- `onSynthesizeText(SynthesisRequest, SynthesisCallback)` — calls Fish Audio API, pipes PCM audio back to Android
- `onIsLanguageAvailable(lang, country, variant)` — declares supported languages to Android
- `onStop()` — cancels in-flight API requests cleanly

### AndroidManifest.xml Service Declaration

```xml
<service
    android:name=".service.FishTtsService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_TEXT_TO_SPEECH_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent-filter>
    <meta-data
        android:name="android.speech.tts"
        android:resource="@xml/tts_engine" />
</service>
```

`res/xml/tts_engine.xml`:
```xml
<tts-engine
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.yourapp.ui.SettingsActivity" />
```

---

## Project Structure

```
app/
├── service/
│   └── FishTtsService.kt              # Extends TextToSpeechService — core engine
├── api/
│   ├── FishAudioApiService.kt         # OkHttp REST client for TTS + voice listing
│   └── FishAudioWebSocketClient.kt    # Streaming WebSocket client for low-latency TTS
├── audio/
│   └── AudioStreamPlayer.kt           # Decodes incoming audio, feeds PCM to SynthesisCallback
├── model/
│   ├── VoiceModel.kt                  # Data class mapping Fish Audio voice model
│   └── TtsRequest.kt                  # Serializable TTS request body
├── repository/
│   └── VoiceRepository.kt             # Caches voice list, manages selected voice
├── ui/
│   ├── SettingsActivity.kt            # API key, model, latency, prosody — opened from Android TTS settings
│   └── VoicePickerActivity.kt         # Browse/search/select Fish Audio voice catalog
└── data/
    └── PreferencesManager.kt          # DataStore wrapper for all persisted settings
```

---

## Fish Audio API Integration

### TTS Synthesis Endpoint (REST)
```
POST https://api.fish.audio/v1/tts
Authorization: Bearer {api_key}
Content-Type: application/json

{
  "text": "Hello world",
  "reference_id": "802e3bc2b27e49c2995d23ef70e6ac89",
  "format": "pcm",
  "latency": "balanced",
  "prosody": {
    "speed": 1.0,
    "volume": 0
  }
}
```

### TTS Streaming Endpoint (WebSocket)
```
wss://api.fish.audio/v1/tts/live
```
Same payload structure. Stream PCM chunks directly into `SynthesisCallback.audioAvailable()` as they arrive.

### Voice Listing
```
GET https://api.fish.audio/v1/model?page_size=20&page_number=1
Authorization: Bearer {api_key}
```

### Authentication
All requests use: `Authorization: Bearer {api_key}`

---

## Audio Pipeline

### Format Strategy
Request `format=pcm` from the Fish Audio API. `SynthesisCallback` requires raw 16-bit PCM — this avoids any decode step.

PCM parameters expected by Android:
- Sample rate: 22050 Hz or 24000 Hz (confirm from Fish Audio API response headers)
- Encoding: `AudioFormat.ENCODING_PCM_16BIT`
- Channels: Mono

### Streaming Flow
```
Fish Audio WebSocket
        |
   PCM chunks arrive
        |
   SynthesisCallback.audioAvailable(byte[], offset, length)
        |
   Android plays audio in real-time
```

### Request Cancellation
Track the active WebSocket/HTTP call in `FishTtsService`. In `onStop()`, cancel it immediately to prevent audio bleed between utterances.

---

## Settings Activity

Opened via the "Settings" button in Android's TTS system settings. Must expose:

1. **API Key** — masked text field, stored securely in DataStore
2. **Selected Voice** — displays current voice name, taps to open VoicePicker
3. **Model** — dropdown: `s1`, `s2`, `s2-pro`
4. **Latency Mode** — toggle: `normal` (better quality) / `balanced` (faster)
5. **Speed** — slider, range 0.5x – 2.0x (maps to prosody.speed)
6. **Volume** — slider (maps to prosody.volume in dB)
7. **Test Button** — synthesizes a short phrase with current settings

---

## Voice Picker Activity

- Paginated list/grid of Fish Audio voice models from `GET /v1/model`
- Show: voice name, language, avatar image, sample playback button
- Filter by language
- Search by name
- Selecting a voice saves its `reference_id` to DataStore
- Support for custom voice ID entry (paste a reference_id directly)

---

## Recommended Libraries

| Library | Use |
|---|---|
| `OkHttp` | HTTP client + WebSocket (single dependency covers both) |
| `kotlinx.serialization` | JSON serialization/deserialization |
| `DataStore (Preferences)` | Persisting API key, voice selection, settings |
| `Kotlin Coroutines` | Async API calls inside the TTS service |
| `Coil` | Voice avatar image loading in VoicePicker |
| `Material Components` | UI components for Settings + VoicePicker |

---

## Key Technical Considerations

### 1. Thread Model
`onSynthesizeText` is called on a background thread by Android. Coroutine scope tied to the service lifecycle is appropriate. Use `Dispatchers.IO` for network calls.

### 2. SynthesisCallback Lifecycle
Always call `callback.start(sampleRateInHz, audioFormat, channelCount)` before any `audioAvailable()` calls, and `callback.done()` when synthesis completes. If an error occurs, call `callback.error()`.

### 3. Voice Mapping to Android Voice Objects
Override `onGetDefaultVoiceNameFor(lang, country, variant)` and return a voice name. Override `onLoadVoice(voiceName)` to allow Android to pre-select voices. Map Fish Audio `reference_id` values to Android `Voice` objects with appropriate locale.

### 4. Language Support
Fish Audio supports: English, Japanese, Korean, Chinese, French, German, Arabic, Spanish, and more. Return `TextToSpeech.LANG_AVAILABLE` or `TextToSpeech.LANG_COUNTRY_AVAILABLE` appropriately in `onIsLanguageAvailable`.

### 5. Caching
Cache the voice list locally (DataStore or Room) with a TTL. Avoid hitting `GET /v1/model` on every synthesis request.

### 6. Rate Limits
Free plan: 100 requests/minute. Paid plans start at 500. Implement basic request queuing to handle bursts gracefully.

---

## Fish Audio Models Reference

| Model ID | Description |
|---|---|
| `s1` | Previous generation, stable, widely supported |
| `s2` | Latest SOTA model, trained on 10M+ hours, 50 languages |
| `s2-pro` | Higher quality variant of S2 |

All `s1` and `s2` models support emotion/tone markers embedded in text, e.g. `[whisper]`, `[excited]`, `[professional broadcast tone]`.

---

## GitHub Actions / Build

Set up a GitHub Actions workflow for automated APK builds (consistent with Justin's existing pattern from other Android projects):

```yaml
# .github/workflows/build.yml
name: Build APK
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build debug APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

---

## Implementation Priority Order

1. `FishTtsService.kt` — get the service binding working with a hardcoded test voice
2. `FishAudioApiService.kt` — REST client, PCM format, basic synthesis
3. `PreferencesManager.kt` + `SettingsActivity.kt` — API key entry, make it configurable
4. `FishAudioWebSocketClient.kt` — replace REST with streaming for lower latency
5. `VoiceRepository.kt` + `VoicePickerActivity.kt` — voice catalog browsing
6. Polish: voice mapping to Android Voice objects, language availability, caching, error handling
