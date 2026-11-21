package com.maloy.muzza.di

import org.koin.core.context.startKoin

private var koinInitialized = false

fun initKoinIos() {
    if (koinInitialized) return
    koinInitialized = true
    try {
        startKoin {
            modules(iosModule, sharedModule)
        }
    } catch (error: Throwable) {
        koinInitialized = false
        throw error
    }
}
