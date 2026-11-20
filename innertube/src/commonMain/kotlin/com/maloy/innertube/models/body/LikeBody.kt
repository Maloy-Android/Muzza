package com.maloy.innertube.models.body

import kotlinx.serialization.Serializable
@Serializable
data class LikeBody(
    val context: com.metrolist.innertubex.models.Context,
    val target: Target,
) {
    @Serializable
    sealed class Target {
        @Serializable
        data class VideoTarget(val videoId: String) : Target()
        @Serializable
        data class PlaylistTarget(val playlistId: String) : Target()
    }
}