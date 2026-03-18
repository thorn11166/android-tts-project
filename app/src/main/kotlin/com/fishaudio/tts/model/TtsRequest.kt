package com.fishaudio.tts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
