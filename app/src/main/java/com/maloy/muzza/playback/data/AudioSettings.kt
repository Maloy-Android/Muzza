package com.maloy.muzza.playback.data

import com.maloy.muzza.db.entities.FormatEntity

data class AudioSettings(
    val volume: Float,
    val muted: Boolean,
    val normalizeAudio: Boolean,
    val format: FormatEntity?
)