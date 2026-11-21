package com.maloy.muzza.di

import org.koin.core.context.startKoin

private var koinInitialized = false

fun initKoinIos() {
    if (koinInitialized) return
    koinInitialized = true
    startKoin {
        modules(iosModule, sharedModule)
    }
}
