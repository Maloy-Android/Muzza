package com.maloy.muzza.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

fun saveLanguagePreference(context: Context, languageCode: String) {
    val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    sharedPreferences.edit { putString("app_language", languageCode) }
}


fun updateLanguage(context: Context, languageCode: String) {
    val locale = Locale(languageCode)
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(locale))
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}