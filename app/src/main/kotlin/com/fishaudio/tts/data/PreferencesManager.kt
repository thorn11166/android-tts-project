package com.fishaudio.tts.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fish_tts_prefs")

object PrefKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
    val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
    val MODEL = stringPreferencesKey("model")       // "s1", "s2", "s2-pro"
    val LATENCY = stringPreferencesKey("latency")   // "normal", "balanced"
    val SPEED = floatPreferencesKey("speed")        // 0.5 - 2.0
    val VOLUME = intPreferencesKey("volume")        // dB offset
    val VOICE_CACHE = stringPreferencesKey("voice_cache")
    val VOICE_CACHE_TIME = longPreferencesKey("voice_cache_time")
}

class PreferencesManager(private val context: Context) {

    val apiKey: Flow<String> = context.dataStore.data.map { it[PrefKeys.API_KEY] ?: "" }
    val selectedVoiceId: Flow<String> = context.dataStore.data.map { it[PrefKeys.SELECTED_VOICE_ID] ?: "" }
    val selectedVoiceName: Flow<String> = context.dataStore.data.map { it[PrefKeys.SELECTED_VOICE_NAME] ?: "Default" }
    val model: Flow<String> = context.dataStore.data.map { it[PrefKeys.MODEL] ?: "s2" }
    val latency: Flow<String> = context.dataStore.data.map { it[PrefKeys.LATENCY] ?: "balanced" }
    val speed: Flow<Float> = context.dataStore.data.map { it[PrefKeys.SPEED] ?: 1.0f }
    val volume: Flow<Int> = context.dataStore.data.map { it[PrefKeys.VOLUME] ?: 0 }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[PrefKeys.API_KEY] = value }
    }

    suspend fun setSelectedVoice(id: String, name: String) {
        context.dataStore.edit {
            it[PrefKeys.SELECTED_VOICE_ID] = id
            it[PrefKeys.SELECTED_VOICE_NAME] = name
        }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[PrefKeys.MODEL] = value }
    }

    suspend fun setLatency(value: String) {
        context.dataStore.edit { it[PrefKeys.LATENCY] = value }
    }

    suspend fun setSpeed(value: Float) {
        context.dataStore.edit { it[PrefKeys.SPEED] = value }
    }

    suspend fun setVolume(value: Int) {
        context.dataStore.edit { it[PrefKeys.VOLUME] = value }
    }

    suspend fun setVoiceCache(json: String, timestamp: Long) {
        context.dataStore.edit {
            it[PrefKeys.VOICE_CACHE] = json
            it[PrefKeys.VOICE_CACHE_TIME] = timestamp
        }
    }

    suspend fun getVoiceCache(): Pair<String, Long> {
        var json = ""
        var time = 0L
        context.dataStore.data.collect { prefs ->
            json = prefs[PrefKeys.VOICE_CACHE] ?: ""
            time = prefs[PrefKeys.VOICE_CACHE_TIME] ?: 0L
        }
        return Pair(json, time)
    }
}
