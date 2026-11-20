package com.maloy.muzza.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
actual val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
