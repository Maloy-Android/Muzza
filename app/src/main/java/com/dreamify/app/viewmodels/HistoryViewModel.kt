package com.dreamify.app.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.pages.HistoryPage
import com.dreamify.app.constants.HistorySource
import com.dreamify.app.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
) : ViewModel() {
    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)
    private val lastMonday = thisMonday.minusDays(7)
    var historySource = MutableStateFlow(HistorySource.LOCAL)
    val historyPage = mutableStateOf<HistoryPage?>(null)

    val events = database.events()
        .map { events ->
            events.groupBy {
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
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    init {
        viewModelScope.launch(Dispatchers.IO) {
            historyPage.value = YouTube.musicHistory().getOrNull()
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
