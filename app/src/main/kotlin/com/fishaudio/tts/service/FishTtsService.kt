package com.fishaudio.tts.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.fishaudio.tts.api.FishAudioApiService
import com.fishaudio.tts.api.FishAudioWebSocketClient
import com.fishaudio.tts.data.PreferencesManager
import com.fishaudio.tts.model.Prosody
import com.fishaudio.tts.model.TtsRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class FishTtsService : TextToSpeechService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private lateinit var prefs: PreferencesManager

    private val supportedLanguages = mapOf(
        "en" to Locale.ENGLISH,
        "ja" to Locale.JAPANESE,
        "ko" to Locale.KOREAN,
        "zh" to Locale.CHINESE,
        "fr" to Locale.FRENCH,
        "de" to Locale.GERMAN,
        "ar" to Locale("ar"),
        "es" to Locale("es"),
        "pt" to Locale("pt"),
        "it" to Locale("it"),
        "nl" to Locale("nl"),
        "pl" to Locale("pl"),
        "ru" to Locale("ru"),
        "tr" to Locale("tr"),
        "vi" to Locale("vi"),
        "th" to Locale("th"),
        "id" to Locale("id"),
        "ms" to Locale("ms"),
        "hi" to Locale("hi")
    )

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int {
        return if (supportedLanguages.containsKey(lang.lowercase())) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onLoadLanguage(lang: String, country: String, variant: String): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onStop() {
        activeJob?.cancel()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val apiKey = prefs.apiKey.firstOrNull() ?: ""
            val voiceId = prefs.selectedVoiceId.firstOrNull() ?: ""
            val latency = prefs.latency.firstOrNull() ?: "balanced"
            val speed = prefs.speed.firstOrNull() ?: 1.0f
            val volume = prefs.volume.firstOrNull() ?: 0

            if (apiKey.isEmpty()) {
                callback.error()
                return@launch
            }

            if (voiceId.isEmpty()) {
                callback.error()
                return@launch
            }

            val text = request.charSequenceText?.toString() ?: run {
                callback.error()
                return@launch
            }

            val ttsRequest = TtsRequest(
                text = text,
                referenceId = voiceId,
                latency = latency,
                prosody = Prosody(speed = speed, volume = volume)
            )

            var started = false

            val result = FishAudioApiService.synthesize(
                apiKey = apiKey,
                ttsRequest = ttsRequest,
                onChunk = { bytes, offset, length ->
                    if (!started) {
                        callback.start(
                            FishAudioApiService.SAMPLE_RATE,
                            FishAudioApiService.AUDIO_FORMAT,
                            FishAudioApiService.CHANNEL_COUNT
                        )
                        started = true
                    }
                    callback.audioAvailable(bytes, offset, length)
                }
            )

            if (result.isSuccess) {
                if (!started) {
                    // Empty synthesis — still need to call start/done
                    callback.start(
                        FishAudioApiService.SAMPLE_RATE,
                        FishAudioApiService.AUDIO_FORMAT,
                        FishAudioApiService.CHANNEL_COUNT
                    )
                }
                callback.done()
            } else {
                callback.error()
            }
        }
    }
}
