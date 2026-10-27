package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.filterExplicit
import com.maloy.innertube.pages.HistoryPage
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    val database: MusicDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)
    private val lastMonday = thisMonday.minusDays(7)
    val historyPage = MutableStateFlow<HistoryPage?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val events = combine(
        database.events(),
        context.dataStore.data
            .map { it[HideExplicitKey] ?: false }
            .distinctUntilChanged()
    ) { events, hideExplicit ->
        val filtered = if (hideExplicit) {
            events.filter { !it.song.song.explicit }
        } else {
            events
        }
        filtered.groupBy {
            val date = it.event.timestamp.toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
            when {
                daysAgo == 0 -> DateAgo.Today
                daysAgo == 1 -> DateAgo.Yesterday
                date >= thisMonday -> DateAgo.ThisWeek
                date >= lastMonday -> DateAgo.LastWeek
                else -> DateAgo.Other(date.withDayOfMonth(1))
            }
        }.toSortedMap(compareBy { dateAgo ->
            when (dateAgo) {
                DateAgo.Today -> 0L
                DateAgo.Yesterday -> 1L
                DateAgo.ThisWeek -> 2L
                DateAgo.LastWeek -> 3L
                is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
            }
        }).mapValues { entry ->
            entry.value.distinctBy { it.song.id }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        fetchRemoteHistory()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                fetchRemoteHistory()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh remote history"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyPage.value = YouTube.musicHistory().getOrNull()?.filterExplicit(context.dataStore.get(HideExplicitKey, false))
        }
    }
}

sealed class DateAgo {
    data object Today : DateAgo()
    data object Yesterday : DateAgo()
    data object ThisWeek : DateAgo()
    data object LastWeek : DateAgo()
    class Other(val date: LocalDate) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}
