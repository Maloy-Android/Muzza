package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: com.metrolist.innertubex.models.Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
