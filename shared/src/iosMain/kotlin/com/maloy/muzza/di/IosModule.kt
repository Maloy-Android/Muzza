package com.maloy.muzza.di

import com.maloy.muzza.data.db.DatabaseDriverFactory
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

val iosModule = module {

    // SQLDelight driver
    single { DatabaseDriverFactory() }

    // Settings backed by NSUserDefaults
    single<ObservableSettings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
}
