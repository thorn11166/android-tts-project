package com.fishaudio.tts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoiceModel(
    val id: String,
    val title: String,
    val description: String = "",
    val languages: List<String> = emptyList(),
    @SerialName("cover_image") val coverImage: String? = null
)

@Serializable
data class VoiceListResponse(
    val items: List<VoiceModel>,
    val total: Int
)
