package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: com.metrolist.innertubex.models.Context,
    val params: String,
)
