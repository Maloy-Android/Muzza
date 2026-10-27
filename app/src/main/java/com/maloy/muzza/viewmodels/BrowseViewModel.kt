package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.utils.reportException
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val browseId: String? = savedStateHandle.get<String>("browseId")

    val items = MutableStateFlow<List<YTItem>?>(emptyList())
    val title = MutableStateFlow<String?>(null)
    val navTitle = savedStateHandle.get<String>("title")
    private val _isRefreshing = MutableStateFlow(false)
    private val params = savedStateHandle.get<String>("params")
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()


    fun load(){
        viewModelScope.launch {
            browseId?.let {
                YouTube.browse(browseId, params).onSuccess { result ->
                    title.value = navTitle
                    val allItems = result.items.flatMap { it.items }
                    items.value = allItems.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                }.onFailure {
                    reportException(it)
                }
            }
        }
    }
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                load()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh browse content"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    init{
        viewModelScope.launch {
            load()
        }
    }
}
