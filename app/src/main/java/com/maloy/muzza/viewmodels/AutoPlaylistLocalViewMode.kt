package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.ui.utils.DirectoryTree
import com.maloy.muzza.ui.utils.refreshLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Stack
import javax.inject.Inject

@HiltViewModel
class AutoPlaylistLocalViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    var folderPositionStack = Stack<DirectoryTree>()
    val databaseLink = database

    val inLocal = mutableStateOf(false)

    val localSongDirectoryTree = refreshLocal(context, database)

    fun getLocalSongs(context: Context, database: MusicDatabase): MutableStateFlow<DirectoryTree> {
        val directoryStructure = refreshLocal(context, database).value
        localSongDirectoryTree.value = directoryStructure
        return MutableStateFlow(directoryStructure)
    }
}