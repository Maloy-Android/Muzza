package com.dreamify.app.extensions

import android.content.BroadcastReceiver
import android.content.Context
import com.dreamify.app.constants.LikedAutoDownloadKey
import com.dreamify.app.constants.LikedAutodownloadMode
import com.dreamify.app.utils.dataStore
import com.dreamify.app.utils.get
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun <T> Flow<T>.collect(scope: CoroutineScope, action: suspend (value: T) -> Unit) {
    scope.launch {
        collect(action)
    }
}

fun <T> Flow<T>.collectLatest(scope: CoroutineScope, action: suspend (value: T) -> Unit) {
    scope.launch {
        collectLatest(action)
    }
}

fun Context.getLikeAutoDownload(): LikedAutodownloadMode {
    return dataStore[LikedAutoDownloadKey].toEnum(LikedAutodownloadMode.OFF)
}

val SilentHandler = CoroutineExceptionHandler { _, _ -> }
