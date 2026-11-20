package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.EventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(repository: MusicRepository) : ViewModel() {
    val recentEvents: StateFlow<List<EventEntity>> = repository.getRecentEvents(100)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
