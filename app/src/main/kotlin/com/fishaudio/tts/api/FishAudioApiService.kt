package com.fishaudio.tts.api

import android.media.AudioFormat
import com.fishaudio.tts.model.TtsRequest
import com.fishaudio.tts.model.VoiceListResponse
import com.fishaudio.tts.model.VoiceModel
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object FishAudioApiService {

    private const val BASE_URL = "https://api.fish.audio"
    const val SAMPLE_RATE = 24000
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHANNEL_COUNT = 1

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Synthesizes text and streams raw PCM bytes to [onChunk].
     * Returns a Result indicating success or failure.
     */
    suspend fun synthesize(
        apiKey: String,
        ttsRequest: TtsRequest,
        onChunk: suspend (ByteArray, Int, Int) -> Unit
    ): Result<Unit> = runCatching {
        val body = json.encodeToString(ttsRequest)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/v1/tts")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("TTS request failed: ${response.code} ${response.message}")
        }

        val responseBody = response.body ?: error("Empty response body")
        val buffer = ByteArray(4096)
        val stream = responseBody.byteStream()
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            if (!coroutineContext.isActive) break
            onChunk(buffer, 0, bytesRead)
        }
    }

    /**
     * Lists available voice models, paginated.
     */
    fun listVoices(
        apiKey: String,
        pageSize: Int = 20,
        pageNumber: Int = 1
    ): Result<List<VoiceModel>> = runCatching {
        val request = Request.Builder()
            .url("$BASE_URL/v1/model?page_size=$pageSize&page_number=$pageNumber")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            error("Voice list request failed: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: error("Empty response body")
        json.decodeFromString<VoiceListResponse>(responseBody).items
    }
}
