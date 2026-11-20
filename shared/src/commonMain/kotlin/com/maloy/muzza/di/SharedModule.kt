package com.maloy.muzza.di

import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.data.repository.MusicRepositoryImpl
import com.maloy.muzza.presentation.viewmodel.AlbumViewModel
import com.maloy.muzza.presentation.viewmodel.ArtistViewModel
import com.maloy.muzza.presentation.viewmodel.ExploreViewModel
import com.maloy.muzza.presentation.viewmodel.HistoryViewModel
import com.maloy.muzza.presentation.viewmodel.HomeViewModel
import com.maloy.muzza.presentation.viewmodel.LibraryAlbumsViewModel
import com.maloy.muzza.presentation.viewmodel.LibraryArtistsViewModel
import com.maloy.muzza.presentation.viewmodel.LibraryPlaylistsViewModel
import com.maloy.muzza.presentation.viewmodel.LocalPlaylistViewModel
import com.maloy.muzza.presentation.viewmodel.LocalSearchViewModel
import com.maloy.muzza.presentation.viewmodel.LyricsViewModel
import com.maloy.muzza.presentation.viewmodel.OnlinePlaylistViewModel
import com.maloy.muzza.presentation.viewmodel.OnlineSearchViewModel
import com.maloy.muzza.presentation.viewmodel.PlayerViewModel
import com.maloy.muzza.presentation.viewmodel.SettingsViewModel
import com.maloy.muzza.util.AppSettings
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val sharedModule = module {

    // Repository
    singleOf(::MusicRepositoryImpl) bind MusicRepository::class

    // Settings
    single { AppSettings(get()) }

    // ViewModels
    viewModelOf(::HomeViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::AlbumViewModel)
    viewModelOf(::ArtistViewModel)
    viewModelOf(::LibraryAlbumsViewModel)
    viewModelOf(::LibraryArtistsViewModel)
    viewModelOf(::LibraryPlaylistsViewModel)
    viewModelOf(::LocalPlaylistViewModel)
    viewModelOf(::OnlinePlaylistViewModel)
    viewModelOf(::OnlineSearchViewModel)
    viewModelOf(::LocalSearchViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::LyricsViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::SettingsViewModel)
}
