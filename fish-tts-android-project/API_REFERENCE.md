# Fish Audio API — Quick Reference

## Authentication
All requests require:
```
Authorization: Bearer {your_api_key}
```

---

## TTS Synthesis (REST)

**Endpoint:** `POST https://api.fish.audio/v1/tts`

**Request:**
```json
{
  "text": "Text to synthesize",
  "reference_id": "802e3bc2b27e49c2995d23ef70e6ac89",
  "format": "pcm",
  "latency": "balanced",
  "prosody": {
    "speed": 1.0,
    "volume": 0
  }
}
```

**Response:** Raw audio bytes in requested format

**Format options:** `pcm`, `mp3`, `opus`, `wav`
**Latency options:** `normal` (better quality), `balanced` (lower latency)

---

## TTS Streaming (WebSocket)

**Endpoint:** `wss://api.fish.audio/v1/tts/live`

Send same JSON payload. Receive PCM audio chunks as they are generated.
Feed directly to `SynthesisCallback.audioAvailable()`.

---

## Voice Listing

**Endpoint:** `GET https://api.fish.audio/v1/model`

**Query params:**
- `page_size` — number of results (max 20)
- `page_number` — page index (1-based)

**Response:**
```json
{
  "items": [
    {
      "id": "802e3bc2b27e49c2995d23ef70e6ac89",
      "title": "Voice Name",
      "description": "...",
      "languages": ["en"],
      "cover_image": "https://..."
    }
  ],
  "total": 2000000
}
```

---

## Voice Creation (Clone Upload)

**Endpoint:** `POST https://api.fish.audio/v1/model`

**Body:** multipart/form-data
- `title` — voice name
- `voices` — audio file(s) (WAV, 10–20 seconds, clean audio)
- `description` — optional

---

## Models

| ID | Notes |
|---|---|
| `s1` | Previous gen, stable |
| `s2` | Latest, 10M+ hours training, 50 languages |
| `s2-pro` | Higher quality S2 variant |

---

## Emotion / Tone Tags (S1 + S2)

Embed in text directly:
```
[whisper] Hello there [normal] how are you today
[excited] This is amazing! [professional broadcast tone] Welcome to the news.
[pitch up] Higher voice here
```

---

## Rate Limits

| Plan | Limit |
|---|---|
| Free | 100 requests/minute |
| Paid (base) | 500 requests/minute |

---

## PCM Audio Parameters

When requesting `format=pcm`:
- Sample rate: **24000 Hz**
- Bit depth: **16-bit**
- Channels: **Mono**

Android `SynthesisCallback.start()` call:
```kotlin
callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
```

---

## SDK References (Forked)

- Python: `github.com/{your-username}/fish-audio-python`
- TypeScript: `github.com/{your-username}/fish-audio-typescript`
- Go: `github.com/{your-username}/fish-audio-go`
- Docs: `github.com/{your-username}/docs`
