package com.maloy.muzza.di

import org.koin.core.context.startKoin

/** Called from Swift @main to initialize Koin on iOS */
fun initKoinIos() {
    startKoin {
        modules(iosModule, sharedModule)
    }
}
