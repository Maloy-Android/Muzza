package com.dreamify.innertube.models.body

import com.dreamify.innertube.models.Context
import kotlinx.serialization.Serializable
@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)