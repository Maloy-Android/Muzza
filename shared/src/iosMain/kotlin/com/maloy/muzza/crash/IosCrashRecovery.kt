@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package com.maloy.muzza.crash
 
import kotlin.native.setUnhandledExceptionHook

object IosCrashRecovery {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        setUnhandledExceptionHook { error ->
            record(error)
        }
    }

    fun record(error: Throwable) {
        println(buildString {
            append(error::class.simpleName ?: "Throwable")
            append(": ")
            append(error.message.orEmpty())
            append('\n')
            append(error.stackTraceToString())
        })
    }
}
