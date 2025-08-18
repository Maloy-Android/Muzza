package com.dreamify.innertube.models.body

import com.dreamify.innertube.models.Context
import kotlinx.serialization.Serializable
@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)