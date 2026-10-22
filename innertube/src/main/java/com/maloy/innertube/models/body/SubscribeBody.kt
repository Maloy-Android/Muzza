package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable
@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: com.metrolist.innertubex.models.Context,
)