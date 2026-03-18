package com.fishaudio.tts.api

import android.media.AudioFormat
import com.fishaudio.tts.model.TtsRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FishAudioWebSocketClient {

    private const val WS_URL = "wss://api.fish.audio/v1/tts/live"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no timeout for streaming
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null

    /**
     * Opens a WebSocket stream and delivers PCM chunks via [onChunk].
     * Suspends until the stream completes or an error occurs.
     */
    suspend fun synthesize(
        apiKey: String,
        ttsRequest: TtsRequest,
        onChunk: suspend (ByteArray, Int, Int) -> Unit
    ): Result<Unit> = runCatching {
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(WS_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    ws.send(json.encodeToString(ttsRequest))
                }

                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    if (cont.isActive) {
                        val data = bytes.toByteArray()
                        // Use runBlocking-safe callback approach: deliver via callback
                        // Since suspendCancellableCoroutine runs in a coroutine context,
                        // we use a blocking queue pattern here
                        try {
                            kotlinx.coroutines.runBlocking { onChunk(data, 0, data.size) }
                        } catch (e: Exception) {
                            ws.close(1000, "Cancelled")
                        }
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(1000, null)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }

            webSocket = client.newWebSocket(request, listener)

            cont.invokeOnCancellation {
                webSocket?.close(1000, "Cancelled")
            }
        }
    }

    fun close() {
        webSocket?.close(1000, "Closed")
        webSocket = null
    }
}
