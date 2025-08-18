package com.dreamify.app.playback.queues

import androidx.media3.common.MediaItem
import com.dreamify.app.extensions.metadata
import com.dreamify.app.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit()
                )
            } else this
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else this

