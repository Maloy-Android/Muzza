package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackTrackingBody(
    val context: com.metrolist.innertubex.models.Context,
    val videoId: String,
    val cpn: String,
    val playlistId: String? = null,
    val ver: String = "2",
)