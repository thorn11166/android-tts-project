package com.fishaudio.tts.repository

import android.content.Context
import com.fishaudio.tts.api.FishAudioApiService
import com.fishaudio.tts.data.PreferencesManager
import com.fishaudio.tts.model.VoiceModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Cache TTL: 1 hour
private const val CACHE_TTL_MS = 60 * 60 * 1000L

class VoiceRepository(context: Context) {

    private val prefs = PreferencesManager(context)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getVoices(apiKey: String, pageSize: Int = 20, pageNumber: Int = 1): Result<List<VoiceModel>> {
        // Try cache first (only for page 1)
        if (pageNumber == 1) {
            val (cachedJson, cacheTime) = prefs.getVoiceCache()
            val age = System.currentTimeMillis() - cacheTime
            if (cachedJson.isNotEmpty() && age < CACHE_TTL_MS) {
                return runCatching { json.decodeFromString<List<VoiceModel>>(cachedJson) }
            }
        }

        val result = FishAudioApiService.listVoices(apiKey, pageSize, pageNumber)
        if (result.isSuccess && pageNumber == 1) {
            val voices = result.getOrThrow()
            prefs.setVoiceCache(json.encodeToString(voices), System.currentTimeMillis())
        }
        return result
    }

    suspend fun getSelectedVoiceId(): String =
        prefs.selectedVoiceId.firstOrNull() ?: ""

    suspend fun setSelectedVoice(id: String, name: String) =
        prefs.setSelectedVoice(id, name)
}
