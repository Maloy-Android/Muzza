package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.maloy.muzza.util.AppSettings
import kotlinx.coroutines.flow.Flow

class SettingsViewModel(val settings: AppSettings) : ViewModel() {
    val hideExplicitFlow: Flow<Boolean> = settings.hideExplicitFlow()
    val contentLanguageFlow: Flow<String> = settings.contentLanguageFlow()
    val contentCountryFlow: Flow<String> = settings.contentCountryFlow()
    val audioQualityFlow: Flow<String> = settings.audioQualityFlow()
}
