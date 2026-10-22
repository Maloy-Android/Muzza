package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: com.metrolist.innertubex.models.Context,
    val query: String?,
    val params: String?,
)
