package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackBody(
    val context: com.metrolist.innertubex.models.Context,
    val feedbackTokens: List<String>,
    val isFeedbackTokenUnencrypted: Boolean = false,
    val shouldMerge: Boolean = false,
)