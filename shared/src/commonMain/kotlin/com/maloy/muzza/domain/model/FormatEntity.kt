package com.maloy.muzza.domain.model

data class FormatEntity(
    val id: String,
    val itag: Int? = null,
    val mimeType: String? = null,
    val codecs: String? = null,
    val bitrate: Long? = null,
    val sampleRate: Int? = null,
    val contentLength: Long? = null,
    val loudnessDb: Double? = null,
    val playbackUrl: String? = null,
    val expiredAt: Long? = null,
)
