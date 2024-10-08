package com.maloy.innertube.models.body

import com.maloy.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>
)

@Serializable
sealed class Action {
    @Serializable
    data class RenamePlaylistAction(
        val action: String = "ACTION_SET_PLAYLIST_NAME",
        val playlistName: String
    ) : Action()
}