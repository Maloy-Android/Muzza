package com.maloy.muzza.di

import com.maloy.muzza.data.db.DatabaseDriverFactory
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {

    // SQLDelight driver
    single { DatabaseDriverFactory(androidContext()) }

    // Settings backed by SharedPreferences
    single<ObservableSettings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("muzza_settings", android.content.Context.MODE_PRIVATE)
        )
    }
}
