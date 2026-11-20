package com.maloy.muzza

import android.app.Application
import com.maloy.muzza.di.androidModule
import com.maloy.muzza.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class MuzzaApp : Application() {

    companion object {
        lateinit var instance: MuzzaApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MuzzaApp)
            modules(androidModule, sharedModule)
        }
    }
}
