package com.maloy.innertube.models.body

import com.maloy.innertube.models.Context
import kotlinx.serialization.Serializable
@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)