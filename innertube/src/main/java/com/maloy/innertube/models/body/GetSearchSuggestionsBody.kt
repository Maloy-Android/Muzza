package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: com.metrolist.innertubex.models.Context,
    val input: String,
)
