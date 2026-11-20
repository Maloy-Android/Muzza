package com.maloy.muzza.util

import kotlinx.coroutines.CoroutineDispatcher

/** Platform-specific IO dispatcher */
expect val ioDispatcher: CoroutineDispatcher

/** Platform-specific Main dispatcher */
expect val mainDispatcher: CoroutineDispatcher
