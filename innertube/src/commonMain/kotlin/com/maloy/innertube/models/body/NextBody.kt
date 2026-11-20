package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class NextBody(
    val context: com.metrolist.innertubex.models.Context,
    val videoId: String?,
    val playlistId: String?,
    val playlistSetVideoId: String?,
    val index: Int?,
    val params: String?,
    val continuation: String?,
)
