package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable
@Serializable
data class CreatePlaylistBody(
    val context: com.metrolist.innertubex.models.Context,
    val title: String,
    val privacyStatus: String = PrivacyStatus.PRIVATE,
    val videoIds: List<String>? = null
) {
    object PrivacyStatus {
        const val PRIVATE = "PRIVATE"
    }
}