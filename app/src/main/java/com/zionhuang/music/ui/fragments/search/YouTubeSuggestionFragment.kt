package com.zionhuang.music.ui.fragments.search

import android.app.SearchManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialFadeThrough
import com.zionhuang.music.R
import com.zionhuang.music.databinding.LayoutRecyclerviewBinding
import com.zionhuang.music.extensions.addOnClickListener
import com.zionhuang.music.extensions.getQueryTextChangeFlow
import com.zionhuang.music.extensions.resolveColor
import com.zionhuang.music.ui.adapters.SearchSuggestionAdapter
import com.zionhuang.music.ui.fragments.base.BindingFragment
import com.zionhuang.music.viewmodels.SuggestionViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class YouTubeSuggestionFragment : BindingFragment<LayoutRecyclerviewBinding>() {
    override fun getViewBinding() = LayoutRecyclerviewBinding.inflate(layoutInflater)

    private val viewModel by viewModels<SuggestionViewModel>()
    private lateinit var searchView: SearchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(resources.getInteger(R.integer.motion_duration_large).toLong())
        exitTransition = MaterialFadeThrough().setDuration(resources.getInteger(R.integer.motion_duration_large).toLong())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val suggestionAdapter = SearchSuggestionAdapter { query ->
            viewModel.fillQuery(query)
        }
        binding.recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = suggestionAdapter
            addOnClickListener { pos, _ ->
                search(suggestionAdapter.getQueryByPosition(pos))
            }
        }
        viewModel.apply {
            onFillQuery.observe(viewLifecycleOwner) { query ->
                searchView.setQuery(query, false)
            }
            query.observe(viewLifecycleOwner) { query ->
                viewModel.fetchSuggestions(query)
            }
            suggestions.observe(viewLifecycleOwner) { dataSet ->
                suggestionAdapter.setDataSet(dataSet)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.search_view, menu)
        searchView = menu.findItem(R.id.search_view).actionView as SearchView
        setupSearchView()
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchView() {
        val searchManager: SearchManager = requireContext().getSystemService()!!
        searchView.apply {
            isIconified = false
            findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.apply {
                setPadding(0, 2, 0, 2)
                setTextColor(requireContext().resolveColor(R.attr.colorOnSurface))
                setHintTextColor(requireContext().resolveColor(R.attr.colorOnSurfaceVariant))
            }
            setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
            isSubmitButtonEnabled = false
            maxWidth = Int.MAX_VALUE
            setOnCloseListener { true }
            viewLifecycleOwner.lifecycleScope.launch {
                getQueryTextChangeFlow()
                    .debounce(100.toDuration(DurationUnit.MILLISECONDS))
                    .collect { e ->
                        if (e.isSubmitted) {
                            search(e.query.orEmpty())
                        } else {
                            viewModel.setQuery(e.query)
                        }
                    }
            }
            setQuery(viewModel.query.value, false)
        }

    }

    private fun search(query: String) {
        searchView.clearFocus()
        val action = YouTubeSuggestionFragmentDirections.actionSuggestionFragmentToSearchResultFragment(query)
        NavHostFragment.findNavController(this).navigate(action)
    }
}