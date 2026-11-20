package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: com.metrolist.innertubex.models.Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)
