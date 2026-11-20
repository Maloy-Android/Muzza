package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable
@Serializable
data class PlaylistDeleteBody(
    val context: com.metrolist.innertubex.models.Context,
    val playlistId: String
)