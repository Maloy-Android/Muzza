package com.maloy.innertube.models.body

import com.maloy.innertube.models.Context
import kotlinx.serialization.Serializable
@Serializable
data class LibraryBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    sealed class Target {
        @Serializable
        data class VideoTarget(val videoId: String) : Target()
    }
}