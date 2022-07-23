package com.zionhuang.music.ui.fragments

import android.app.SearchManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.MaterialFadeThrough
import com.zionhuang.music.R
import com.zionhuang.music.constants.MediaConstants.EXTRA_QUEUE_DATA
import com.zionhuang.music.constants.MediaConstants.QUEUE_ALL_SONG
import com.zionhuang.music.databinding.LayoutRecyclerviewBinding
import com.zionhuang.music.extensions.addFastScroller
import com.zionhuang.music.extensions.addOnClickListener
import com.zionhuang.music.extensions.getQueryTextChangeFlow
import com.zionhuang.music.extensions.resolveColor
import com.zionhuang.music.models.QueueData
import com.zionhuang.music.ui.adapters.SongsAdapter
import com.zionhuang.music.ui.adapters.selection.SongItemDetailsLookup
import com.zionhuang.music.ui.adapters.selection.SongItemKeyProvider
import com.zionhuang.music.ui.fragments.base.BindingFragment
import com.zionhuang.music.utils.addActionModeObserver
import com.zionhuang.music.viewmodels.PlaybackViewModel
import com.zionhuang.music.viewmodels.SongsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SongsFragment : BindingFragment<LayoutRecyclerviewBinding>() {
    override fun getViewBinding() = LayoutRecyclerviewBinding.inflate(layoutInflater)

    private val playbackViewModel by activityViewModels<PlaybackViewModel>()
    private val songsViewModel by activityViewModels<SongsViewModel>()
    private val songsAdapter = SongsAdapter()
    private var tracker: SelectionTracker<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough().setDuration(resources.getInteger(R.integer.motion_duration_large).toLong())
        exitTransition = MaterialFadeThrough().setDuration(resources.getInteger(R.integer.motion_duration_large).toLong())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        songsAdapter.apply {
            popupMenuListener = songsViewModel.songPopupMenuListener
            sortInfo = songsViewModel.sortInfo
            downloadInfo = songsViewModel.downloadInfoLiveData
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songsAdapter
            addOnClickListener { pos, _ ->
                if (pos == 0) return@addOnClickListener
                playbackViewModel.playMedia(
                    requireActivity(), songsAdapter.getItemByPosition(pos)!!.id, bundleOf(
                        EXTRA_QUEUE_DATA to QueueData(QUEUE_ALL_SONG, sortInfo = songsViewModel.sortInfo.parcelize())
                    )
                )
            }
            addFastScroller { useMd2Style() }
        }

        tracker = SelectionTracker.Builder(
            "selectionId",
            binding.recyclerView,
            SongItemKeyProvider(songsAdapter),
            SongItemDetailsLookup(binding.recyclerView),
            StorageStrategy.createStringStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build().apply {
            songsAdapter.tracker = this
            addActionModeObserver(requireActivity(), this, R.menu.song_contextual_action_bar) { item ->
                val selectedMap = songsAdapter.snapshot().items
                    .filter { selection.contains(it.id) }
                    .associateBy { it.id }
                val songs = selection.toList().mapNotNull { selectedMap[it] }
                when (item.itemId) {
                    R.id.action_play_next -> songsViewModel.songPopupMenuListener.playNext(songs, requireContext())
                    R.id.action_add_to_queue -> songsViewModel.songPopupMenuListener.addToQueue(songs, requireContext())
                    R.id.action_add_to_playlist -> songsViewModel.songPopupMenuListener.addToPlaylist(songs, requireContext())
                    R.id.action_download -> songsViewModel.songPopupMenuListener.downloadSongs(selection.toList(), requireContext())
                    R.id.action_remove_download -> songsViewModel.songPopupMenuListener.removeDownloads(selection.toList(), requireContext())
                    R.id.action_delete -> songsViewModel.songPopupMenuListener.deleteSongs(songs)
                }
                true
            }
        }

        lifecycleScope.launch {
            songsViewModel.allSongsFlow.collectLatest {
                songsAdapter.submitData(it)
            }
        }

        songsViewModel.sortInfo.liveData.observe(viewLifecycleOwner) {
            songsAdapter.refresh()
        }

        songsViewModel.downloadInfoLiveData.observe(viewLifecycleOwner) { map ->
            map.forEach { (key, value) ->
                songsAdapter.setProgress(key, value)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> findNavController().navigate(SettingsFragmentDirections.openSettingsFragment())
        }
        return true
    }

    @OptIn(FlowPreview::class)
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search_and_settings, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.apply {
            findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.apply {
                setPadding(0, 2, 0, 2)
                setTextColor(requireContext().resolveColor(R.attr.colorOnSurface))
                setHintTextColor(requireContext().resolveColor(R.attr.colorOnSurfaceVariant))
            }
            setSearchableInfo(requireContext().getSystemService<SearchManager>()?.getSearchableInfo(requireActivity().componentName))
            viewLifecycleOwner.lifecycleScope.launch {
                getQueryTextChangeFlow()
                    .debounce(100.toDuration(DurationUnit.MILLISECONDS))
                    .collect { e ->
                        songsViewModel.query = e.query
                        songsAdapter.refresh()
                    }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        tracker?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tracker?.onSaveInstanceState(outState)
    }
}