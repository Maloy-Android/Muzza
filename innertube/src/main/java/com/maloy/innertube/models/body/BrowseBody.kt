package com.maloy.innertube.models.body

import com.maloy.innertube.models.Context
import com.maloy.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: List<Continuation>
)
