import SwiftUI

private enum MuzzaLibraryCategory: String, CaseIterable {
    case playlists = "Playlists"
    case songs = "Songs"
    case albums = "Albums"
    case artists = "Artists"
    case downloads = "Downloads"
    case history = "History"
    case stats = "Statistics"
}

private enum MuzzaLibrarySort: String, CaseIterable {
    case recent = "Recently Added"
    case title = "Title"
    case reverseTitle = "Title (Z-A)"
}

struct MuzzaLibraryView: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var category: MuzzaLibraryCategory = .playlists
    @State private var showPlaylistDialog = false
    @State private var playlistName = ""
    @State private var syncPlaylist = false
    @State private var playlistToRename: MuzzaLiveItem?
    @State private var showDelete = false
    @State private var playlistToDelete: MuzzaLiveItem?
    @State private var showClearHistory = false
    @State private var showClearDownloads = false
    @AppStorage("muzza.librarySort") private var sortMode: MuzzaLibrarySort = .recent
    @State private var isSelecting = false
    @State private var selectedIDs: Set<String> = []

    init(
        playback: MuzzaPlayback,
        account: MuzzaAccount,
        showSearch: Binding<Bool>,
        showSettings: Binding<Bool>
    ) {
        _playback = ObservedObject(wrappedValue: playback)
        _account = ObservedObject(wrappedValue: account)
        _showSearch = showSearch
        _showSettings = showSettings
    }

    private var liked: [MuzzaLiveItem] {
        playback.liked.filter { !hideExplicit || !($0.explicit ?? false) }
    }

    private var likedPlaylist: MuzzaLiveItem {
        MuzzaLiveItem(
            id: "liked-songs",
            title: "Liked Songs",
            subtitle: "\(liked.count) \(liked.count == 1 ? "song" : "songs")",
            thumbnail: "",
            kind: "likedPlaylist",
            params: nil,
            explicit: false,
            duration: nil,
            entryId: nil,
            downloadPath: nil,
            remoteLiked: nil
        )
    }

    private var recent: [MuzzaLiveItem] {
        playback.recent.filter { !hideExplicit || !($0.explicit ?? false) }
    }

    private var sortedLiked: [MuzzaLiveItem] {
        sortMode == .recent ? liked : sortByTitle(liked)
    }

    private var sortedDownloads: [MuzzaLiveItem] {
        let items = playback.downloads.filter { !hideExplicit || !($0.explicit ?? false) }
        return sortMode == .recent ? items : sortByTitle(items)
    }

    private var sortedRecent: [MuzzaLiveItem] {
        sortMode == .recent ? recent : sortByTitle(recent)
    }

    private var sortedAlbums: [MuzzaLiveItem] {
        sortMode == .recent ? playback.albums : sortByTitle(playback.albums)
    }

    private var sortedArtists: [MuzzaLiveItem] {
        sortMode == .recent ? playback.artists : sortByTitle(playback.artists)
    }

    private var sortedPlaylists: [MuzzaLiveItem] {
        sortMode == .recent ? playback.playlists : sortByTitle(playback.playlists)
    }

    private func sortByTitle(_ items: [MuzzaLiveItem]) -> [MuzzaLiveItem] {
        items.sorted {
            let result = $0.title.localizedCaseInsensitiveCompare($1.title)
            return sortMode == .title ? result == .orderedAscending : result == .orderedDescending
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    MuzzaHeader("Library", search: { showSearch = true }, settings: { showSettings = true })
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(MuzzaLibraryCategory.allCases, id: \.self) { item in
                                Button { category = item } label: {
                                    Text(LocalizedStringKey(item.rawValue)).font(.subheadline.weight(.semibold))
                                        .padding(.horizontal, 15).padding(.vertical, 9)
                                        .background(category == item ? Color.accentColor : Color(.secondarySystemFill))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    if [.songs, .albums, .artists, .downloads, .history, .playlists].contains(category) {
                        HStack {
                            Spacer()
                            if category == .history && !recent.isEmpty {
                                Button(role: .destructive) {
                                    showClearHistory = true
                                } label: {
                                    Label("Clear", systemImage: "trash")
                                        .font(.caption.weight(.semibold))
                                }
                            }
                            if category == .downloads && !playback.downloads.isEmpty {
                                Button(role: .destructive) {
                                    showClearDownloads = true
                                } label: {
                                    Label("Clear", systemImage: "trash")
                                        .font(.caption.weight(.semibold))
                                }
                            }
                            Menu {
                                ForEach(MuzzaLibrarySort.allCases, id: \.self) { mode in
                                    Button {
                                        sortMode = mode
                                    } label: {
                                        Label(
                                            mode.rawValue,
                                            systemImage: sortMode == mode ? "checkmark" : "arrow.up.arrow.down"
                                        )
                                    }
                                }
                            } label: {
                                Label("Sort", systemImage: "arrow.up.arrow.down")
                                    .font(.caption.weight(.semibold))
                            }
                            if [.songs, .downloads, .history].contains(category) {
                                Button(isSelecting ? "Done" : "Select") {
                                    isSelecting.toggle()
                                    if !isSelecting { selectedIDs.removeAll() }
                                }
                                .font(.caption.weight(.semibold))
                            }
                        }
                    }
                    if isSelecting && !selectedIDs.isEmpty {
                        HStack(spacing: 10) {
                            if category == .songs || category == .downloads || category == .history {
                                Button {
                                    batchDownload()
                                } label: {
                                    Label("Download", systemImage: "arrow.down.circle")
                                }
                                .buttonStyle(.bordered)
                            }
                            if category == .downloads {
                                Button(role: .destructive) {
                                    batchRemoveDownloads()
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                                .buttonStyle(.bordered)
                            }
                            if category == .songs {
                                Button(role: .destructive) {
                                    batchUnlike()
                                } label: {
                                    Label("Unlike", systemImage: "heart.slash")
                                }
                                .buttonStyle(.bordered)
                            }
                            if category == .songs || category == .downloads || category == .history {
                                let addablePlaylists = playback.playlists.filter {
                                    $0.kind == "localPlaylist" ||
                                        ($0.kind == "playlist" && $0.isEditable == true && playback.canSyncPlaylists)
                                }
                                if !addablePlaylists.isEmpty {
                                    Menu {
                                        ForEach(addablePlaylists) { playlist in
                                            Button("Add to \(playlist.title)") {
                                                batchAdd(to: playlist)
                                            }
                                        }
                                    } label: {
                                        Label("Playlist", systemImage: "text.badge.plus")
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                    if playback.isLibraryLoading && !playback.libraryLoaded {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let error = playback.libraryError, !playback.libraryLoaded {
                        MuzzaErrorView(message: error) { Task { await playback.refreshLibrary() } }
                    } else {
                        if let error = playback.libraryError {
                            MuzzaErrorView(message: error) { Task { await playback.refreshLibrary() } }
                        }
                        categoryContent
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .refreshable {
                await playback.refreshLibrary()
            }
        }
        .sheet(isPresented: $showPlaylistDialog) {
            NavigationStack {
                Form {
                    TextField("Playlist name", text: $playlistName)
                        .textInputAutocapitalization(.words)
                    if playlistToRename == nil {
                        if account.profile != nil {
                            Toggle("Sync with YouTube Music", isOn: $syncPlaylist)
                        }
                        Text(syncPlaylist && account.profile != nil
                             ? "This playlist will be created in your YouTube Music account."
                             : "The playlist is stored on this device.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .navigationTitle(playlistToRename == nil ? "New playlist" : "Rename playlist")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            showPlaylistDialog = false
                            playlistName = ""
                            playlistToRename = nil
                            syncPlaylist = false
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(playlistToRename == nil ? "Create" : "Save") {
                            let name = playlistName
                            let playlist = playlistToRename
                            let shouldSync = syncPlaylist && account.profile != nil && playlist == nil
                            Task {
                                let succeeded: Bool
                                if let playlist {
                                    succeeded = await playback.renamePlaylist(playlist, name: name)
                                } else {
                                    succeeded = await playback.createPlaylist(name, syncToYouTube: shouldSync)
                                }
                                if succeeded {
                                    showPlaylistDialog = false
                                    playlistName = ""
                                    playlistToRename = nil
                                    syncPlaylist = false
                                }
                            }
                        }
                        .disabled(playlistName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
        }
        .confirmationDialog("Delete playlist?", isPresented: $showDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let playlist = playlistToDelete {
                    Task { _ = await playback.deletePlaylist(playlist) }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(playlistToDelete?.title ?? "")
        }
        .confirmationDialog("Clear listening history?", isPresented: $showClearHistory, titleVisibility: .visible) {
            Button("Clear History", role: .destructive) {
                Task { _ = await playback.clearHistory() }
            }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Clear all downloads?", isPresented: $showClearDownloads, titleVisibility: .visible) {
            Button("Clear Downloads", role: .destructive) {
                Task { _ = await playback.clearDownloads() }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var categoryContent: some View {
        switch category {
        case .playlists:
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                NavigationLink(value: likedPlaylist) {
                    HStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [Color.accentColor.opacity(0.95), Color.accentColor.opacity(0.38)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                            Image(systemName: "heart.fill")
                                .font(.title)
                                .foregroundStyle(.white)
                        }
                        .frame(width: 56, height: 56)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Liked Songs").font(.subheadline.bold()).lineLimit(1)
                            Text(likedPlaylist.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(Color(.secondarySystemFill))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)

                Button {
                    playlistToRename = nil
                    playlistName = ""
                    syncPlaylist = false
                    showPlaylistDialog = true
                } label: {
                    HStack(spacing: 10) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(.secondarySystemFill))
                            Image(systemName: "plus")
                                .font(.title)
                        }
                        .frame(width: 56, height: 56)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("New playlist").font(.subheadline.bold()).lineLimit(1)
                            Text("Create locally or sync").font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(Color(.secondarySystemFill).opacity(0.55))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
            }
            if sortedPlaylists.isEmpty {
                MuzzaEmptyView(symbol: "music.note.list", message: "No playlists yet.")
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 12) {
                    ForEach(sortedPlaylists) { playlist in
                        NavigationLink(value: playlist) {
                            VStack(alignment: .leading, spacing: 8) {
                                MuzzaArtwork(item: playlist, size: 156)
                                Text(playlist.title).font(.headline).lineLimit(1)
                                Text(playlist.subtitle).font(.caption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            MuzzaCollectionPlaybackActions(item: playlist, playback: playback)
                            if playlist.kind == "localPlaylist" {
                                Button {
                                    playlistToRename = playlist
                                    playlistName = playlist.title
                                    showPlaylistDialog = true
                                } label: {
                                    Label("Rename playlist", systemImage: "pencil")
                                }
                                Button(role: .destructive) {
                                    playlistToDelete = playlist
                                    showDelete = true
                                } label: {
                                    Label("Delete playlist", systemImage: "trash")
                                }
                            } else {
                                Button {
                                    playback.toggleSaved(playlist)
                                } label: {
                                    Label("Remove from library", systemImage: "bookmark.slash")
                                }
                            }
                        }
                    }
                }
            }
        case .songs:
            if sortedLiked.isEmpty {
                MuzzaEmptyView(symbol: "heart", message: "No liked songs yet.")
            } else {
                ForEach(Array(sortedLiked.enumerated()), id: \.offset) { index, item in
                    selectableSongRow(item: item, queue: sortedLiked, index: index)
                }
            }
        case .albums:
            MuzzaSavedGrid(items: sortedAlbums, playback: playback, symbol: "square.stack", emptyMessage: "No saved albums yet.")
        case .artists:
            MuzzaSavedGrid(items: sortedArtists, playback: playback, symbol: "person", emptyMessage: "No saved artists yet.")
        case .downloads:
            let items = sortedDownloads
            if items.isEmpty {
                MuzzaEmptyView(symbol: "arrow.down.circle", message: "No downloaded songs yet.")
            } else {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    selectableSongRow(item: item, queue: items, index: index)
                }
            }
        case .history:
            if sortedRecent.isEmpty {
                MuzzaEmptyView(symbol: "clock", message: "No listening history yet.")
            } else {
                ForEach(Array(sortedRecent.enumerated()), id: \.offset) { index, item in
                    selectableSongRow(item: item, queue: sortedRecent, index: index)
                }
            }
        case .stats:
            MuzzaListeningStatsView(playback: playback)
        }
    }

    @ViewBuilder
    private func selectableSongRow(item: MuzzaLiveItem, queue: [MuzzaLiveItem], index: Int) -> some View {
        HStack(spacing: 10) {
            if isSelecting {
                Button {
                    if selectedIDs.contains(item.id) {
                        selectedIDs.remove(item.id)
                    } else {
                        selectedIDs.insert(item.id)
                    }
                } label: {
                    Image(systemName: selectedIDs.contains(item.id) ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                }
                .buttonStyle(.plain)
            }
            MuzzaSongRow(
                item: item, playback: playback, queue: queue, queueIndex: index,
                queueTitle: category.rawValue
            )
        }
    }

    private func batchDownload() {
        let items = (category == .songs ? sortedLiked : category == .downloads ? sortedDownloads : sortedRecent)
            .filter { selectedIDs.contains($0.id) }
        for item in items { playback.download(item) }
        selectedIDs.removeAll()
    }

    private func batchRemoveDownloads() {
        let items = sortedDownloads.filter { selectedIDs.contains($0.id) }
        Task {
            for item in items { _ = await playback.removeDownload(item) }
            selectedIDs.removeAll()
        }
    }

    private func batchUnlike() {
        let items = sortedLiked.filter { selectedIDs.contains($0.id) }
        Task {
            await playback.removeLikes(items)
            selectedIDs.removeAll()
        }
    }

    private func batchAdd(to playlist: MuzzaLiveItem) {
        let items = (category == .songs ? sortedLiked : category == .downloads ? sortedDownloads : sortedRecent)
            .filter { selectedIDs.contains($0.id) }
        Task {
            for item in items {
                do {
                    try await playback.add(item, to: playlist, allowDuplicate: false)
                } catch {
                    playback.message = error.localizedDescription
                    break
                }
            }
            selectedIDs.removeAll()
        }
    }
}

private struct MuzzaListeningStatsView: View {
    let playback: MuzzaPlayback
    @State private var period: MuzzaStatsPeriod = .month

    private enum MuzzaStatsPeriod: String, CaseIterable {
        case week = "7 days"
        case month = "30 days"
        case year = "Year"
        case all = "All"

        var days: Int? {
            switch self {
            case .week: return 7
            case .month: return 30
            case .year: return 365
            case .all: return nil
            }
        }
    }

    private var periodItems: [MuzzaLiveItem] {
        playback.recentItems(withinDays: period.days)
    }

    private var listenedDuration: Int {
        periodItems.reduce(0) { total, item in
            total + max(item.duration ?? 0, 0)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Period", selection: $period) {
                ForEach(MuzzaStatsPeriod.allCases, id: \.self) { value in
                    Text(value.rawValue).tag(value)
                }
            }
            .pickerStyle(.segmented)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                MuzzaStatCard(
                    title: "Liked",
                    value: "\(playback.liked.count)",
                    icon: "heart.fill"
                )
                MuzzaStatCard(
                    title: "Period tracks",
                    value: "\(periodItems.count)",
                    icon: "clock.fill"
                )
                MuzzaStatCard(
                    title: "Downloads",
                    value: "\(playback.downloads.count)",
                    icon: "arrow.down.circle.fill"
                )
                MuzzaStatCard(
                    title: "Period minutes",
                    value: "\(listenedDuration / 60)",
                    icon: "headphones"
                )
            }
        }
    }
}

private struct MuzzaStatCard: View {
    let title: String
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon).font(.title3).foregroundStyle(Color.accentColor)
            Text(value).font(.title.bold()).monospacedDigit()
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 110, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemFill))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct MuzzaSavedGrid: View {
    let items: [MuzzaLiveItem]
    let playback: MuzzaPlayback
    let symbol: String
    let emptyMessage: String
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    init(items: [MuzzaLiveItem], playback: MuzzaPlayback, symbol: String, emptyMessage: String) {
        self.items = items
        self.playback = playback
        self.symbol = symbol
        self.emptyMessage = emptyMessage
    }

    var body: some View {
        if items.isEmpty {
            MuzzaEmptyView(symbol: symbol, message: emptyMessage)
        } else {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
                ForEach(items) { item in
                    NavigationLink(value: item) {
                        VStack(alignment: .leading, spacing: 8) {
                            MuzzaArtwork(item: item, size: 156)
                            Text(item.title).font(.subheadline.bold()).lineLimit(2)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if ["album", "artist", "playlist"].contains(item.kind) {
                            if item.kind == "album" || item.kind == "playlist" {
                                MuzzaCollectionPlaybackActions(item: item, playback: playback)
                            }
                            Button {
                                playback.toggleSaved(item)
                            } label: {
                                Label(
                                    playback.isSaved(item) ? "Remove from library" : "Save",
                                    systemImage: playback.isSaved(item) ? "bookmark.slash" : "bookmark"
                                )
                            }
                            .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                        }
                    }
                }
            }
        }
    }
}

struct MuzzaCollectionView: View {
    let item: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var songs: [MuzzaLiveItem] = []
    @State private var sections: [MuzzaLiveSection] = []
    @State private var continuation: String?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()
    @State private var loadedKey: String?
    @State private var loadedContinuations: Set<String> = []
    @State private var showPlaylistEditor = false

    private var usesVerticalGrid: Bool {
        item.kind == "moodAndGenres"
            || item.kind == "newReleases"
            || (item.kind == "browse" && [
                "FEmusic_mixed_for_you", "FEmusic_charts", "FEmusic_listen_again"
            ].contains(where: item.id.hasPrefix))
            || (item.kind == "genre" && item.id == "FEmusic_new_releases_videos")
    }

    private var isLargeBrowsePage: Bool {
        item.kind == "newReleases"
            || (item.kind == "browse" && [
            "FEmusic_mixed_for_you", "FEmusic_charts", "FEmusic_listen_again"
            ].contains(where: item.id.hasPrefix))
    }

    init(item: MuzzaLiveItem, playback: MuzzaPlayback) {
        self.item = item
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                if !isLargeBrowsePage {
                    VStack(alignment: .leading, spacing: 12) {
                        if !item.thumbnail.isEmpty {
                            HStack {
                                Spacer()
                                MuzzaArtwork(item: item, size: 220)
                                Spacer()
                            }
                            .padding(.top, 8)
                        }
                        Text(item.title).font(.largeTitle.bold())
                        if !item.subtitle.isEmpty {
                            Text(item.subtitle).foregroundStyle(.secondary)
                        }
                        if let artists = item.artists, !artists.isEmpty {
                            HStack(spacing: 6) {
                                ForEach(Array(artists.enumerated()), id: \.offset) { index, artist in
                                    if let artistID = artist.id {
                                        NavigationLink(value: muzzaCollectionItem(
                                            id: artistID,
                                            title: artist.name,
                                            subtitle: "Artist",
                                            thumbnail: artist.thumbnail ?? "",
                                            kind: "artist"
                                        )) {
                                            Text(artist.name)
                                        }
                                        .buttonStyle(.plain)
                                    } else {
                                        Text(artist.name)
                                    }
                                    if index < artists.count - 1 { Text(",").foregroundStyle(.secondary) }
                                }
                            }
                            .font(.subheadline)
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                if let first = songs.first {
                                    Button {
                                        playback.play(first, in: songs, startingAt: 0)
                                    } label: {
                                        Label("Play", systemImage: "play.fill")
                                    }
                                    .buttonStyle(.borderedProminent)
                                    Button {
                                        playback.playShuffled(songs)
                                    } label: {
                                        Label("Shuffle", systemImage: "shuffle")
                                    }
                                    .buttonStyle(.bordered)
                                    Button {
                                        playback.startRadio(first, queueTitle: item.title)
                                    } label: {
                                        Label("Start radio", systemImage: "dot.radiowaves.left.and.right")
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(playback.isLoadingRadio)
                                }
                                if ["album", "artist", "playlist"].contains(item.kind) {
                                    Button { playback.toggleSaved(item) } label: {
                                        Label {
                                            Text(LocalizedStringKey(playback.isSaved(item) ? "Saved on device" : "Save on device"))
                                        } icon: {
                                            Image(systemName: playback.isSaved(item) ? "bookmark.fill" : "bookmark")
                                        }
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                                }
                                if item.kind == "localPlaylist" && !songs.isEmpty {
                                    Button { showPlaylistEditor = true } label: {
                                        Label("Edit playlist", systemImage: "pencil")
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(playback.isLibraryBusy)
                                }
                                if !songs.isEmpty {
                                    let missingDownloads = songs.filter {
                                        $0.kind == "song" && !playback.isDownloaded($0)
                                    }
                                    if !missingDownloads.isEmpty {
                                        Button {
                                            for song in missingDownloads { playback.download(song) }
                                        } label: {
                                            Label("Download offline", systemImage: "arrow.down.circle")
                                        }
                                        .buttonStyle(.bordered)
                                        .disabled(!playback.libraryLoaded)
                                    }
                                }
                            }
                        }
                    }
                }
                ForEach(Array(songs.enumerated()), id: \.offset) { index, song in
                    MuzzaSongRow(
                        item: song, playback: playback, queue: songs, queueIndex: index,
                        queueTitle: item.title,
                        playlist: item.kind == "localPlaylist" ? item : nil,
                        onPlaylistChange: { songs = visible($0) }
                    )
                }
                ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                    MuzzaMediaSectionView(
                        section: section,
                        playback: playback,
                        layout: usesVerticalGrid ? .grid : .horizontal,
                        mediaAspectRatio: item.kind == "genre" && item.id == "FEmusic_new_releases_videos"
                            ? 16.0 / 9.0 : 1,
                        showsTitle: !isLargeBrowsePage
                            || section.title.caseInsensitiveCompare(item.title) != .orderedSame
                    )
                }
                if isLoading {
                    ProgressView().frame(maxWidth: .infinity).padding()
                } else if let loadError {
                    MuzzaErrorView(message: loadError) { Task { await reload() } }
                } else if songs.isEmpty && sections.isEmpty {
                    MuzzaEmptyView(symbol: "music.note.list", message: "There are no items here yet.")
                }
                if let pageError {
                    MuzzaErrorView(message: pageError) { Task { await loadMore() } }
                } else if continuation != nil && !isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .onAppear { Task { await loadMore() } }
                }
            }
            .padding(.horizontal, usesVerticalGrid ? 8 : 16).padding(.bottom, 100)
        }
        .modifier(MuzzaPageBackground())
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .task(id: "\(item.kind)-\(item.id)-\(hideExplicit)") { await reload() }
        .refreshable { await reload() }
        .sheet(isPresented: $showPlaylistEditor) {
            MuzzaPlaylistEditorView(playlist: item, playback: playback, items: songs) {
                songs = visible($0)
            }
        }
    }

    private func visible(_ items: [MuzzaLiveItem]) -> [MuzzaLiveItem] {
        items.filter { !hideExplicit || !($0.explicit ?? false) }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        let key = "\(item.kind)-\(item.id)-\(hideExplicit)"
        let canPreserveItems = loadedKey == key
        isLoading = true
        loadError = nil
        pageError = nil
        if !canPreserveItems {
            continuation = nil
        }
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.collection(item, hideExplicit: hideExplicit)
            guard !Task.isCancelled, requestID == id else { return }
            songs = uniqueItems(canPreserveItems ? visible(response.items) + songs : visible(response.items))
            sections = mergedSections(response.sections, preserving: canPreserveItems ? sections : [])
            continuation = response.continuation
            loadedKey = key
            loadedContinuations.removeAll()
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation, !loadedContinuations.contains(token) else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.collection(item, hideExplicit: hideExplicit, continuation: token)
            guard !Task.isCancelled, requestID == id else { return }
            loadedContinuations.insert(token)
            songs = uniqueItems(songs + visible(response.items))
            sections = mergedSections(response.sections, preserving: sections)
            if response.continuation == token || response.continuation.map({ loadedContinuations.contains($0) }) == true {
                pageError = "YouTube returned an invalid continuation."
                continuation = nil
            } else {
                isLoading = false
                continuation = response.continuation
            }
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            pageError = error.localizedDescription
        }
    }

    private func uniqueItems(_ items: [MuzzaLiveItem]) -> [MuzzaLiveItem] {
        var seen = Set<String>()
        return items.filter { seen.insert("\($0.kind):\($0.id)").inserted }
    }

    private func mergedSections(
        _ fresh: [MuzzaLiveSection],
        preserving old: [MuzzaLiveSection]
    ) -> [MuzzaLiveSection] {
        var merged = fresh
        for oldSection in old {
            if let index = merged.firstIndex(where: {
                $0.title == oldSection.title && $0.moreItem?.id == oldSection.moreItem?.id
            }) {
                let current = merged[index]
                var seen = Set<String>()
                let items = (current.items + oldSection.items).filter {
                    seen.insert("\($0.kind):\($0.id)").inserted
                }
                merged[index] = MuzzaLiveSection(
                    title: current.title,
                    items: items,
                    moreItem: current.moreItem ?? oldSection.moreItem
                )
            } else {
                merged.append(oldSection)
            }
        }
        return merged
    }
}
