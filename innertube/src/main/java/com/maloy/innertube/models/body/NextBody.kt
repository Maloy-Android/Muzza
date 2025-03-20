package com.maloy.innertube.models.body

import com.maloy.innertube.models.Context
import com.maloy.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String?,
    val playlistId: String?,
    val playlistSetVideoId: String?,
    val index: Int?,
    val params: String?,
    val continuation: List<Continuation>,
)
