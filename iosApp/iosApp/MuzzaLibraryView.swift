import SwiftUI

private enum MuzzaLibraryCategory: String, CaseIterable {
    case playlists = "Playlists"
    case songs = "Songs"
    case albums = "Albums"
    case artists = "Artists"
    case downloads = "Downloads"
    case history = "History"
}

struct MuzzaLibraryView: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var category: MuzzaLibraryCategory = .playlists
    @State private var remoteRefresh = 0
    @State private var showPlaylistDialog = false
    @State private var playlistName = ""
    @State private var syncPlaylist = false
    @State private var playlistToRename: MuzzaLiveItem?
    @State private var showDelete = false
    @State private var playlistToDelete: MuzzaLiveItem?

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

    private var recent: [MuzzaLiveItem] {
        playback.recent.filter { !hideExplicit || !($0.explicit ?? false) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
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
                    if playback.isLibraryLoading && !playback.libraryLoaded {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let error = playback.libraryError {
                        MuzzaErrorView(message: error) { Task { await playback.refreshLibrary() } }
                    } else {
                        categoryContent
                        if account.profile != nil && category != .history && category != .downloads {
                            MuzzaYouTubeLibraryContent(
                                category: category,
                                account: account,
                                playback: playback,
                                excludedIDs: Set(localItems(for: category).map(\.id)),
                                refreshToken: remoteRefresh
                            )
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .refreshable {
                await playback.refreshLibrary()
                if account.profile != nil && category != .history && category != .downloads {
                    remoteRefresh += 1
                }
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
    }

    @ViewBuilder
    private var categoryContent: some View {
        switch category {
        case .playlists:
            Button {
                playlistToRename = nil
                playlistName = ""
                syncPlaylist = false
                showPlaylistDialog = true
            } label: {
                Label("New playlist", systemImage: "plus").font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading).padding()
                    .background(Color(.secondarySystemFill)).clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
            if playback.playlists.isEmpty {
                MuzzaEmptyView(symbol: "music.note.list", message: "No playlists yet.")
            } else {
                ForEach(playback.playlists) { playlist in
                    NavigationLink(value: playlist) {
                        HStack(spacing: 12) {
                            MuzzaArtwork(item: playlist, size: 58)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(playlist.title).font(.headline).lineLimit(1)
                                Text(playlist.subtitle).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
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
        case .songs:
            if liked.isEmpty {
                MuzzaEmptyView(symbol: "heart", message: "No liked songs yet.")
            } else {
                ForEach(Array(liked.enumerated()), id: \.offset) { index, item in
                    MuzzaSongRow(item: item, playback: playback, queue: liked, queueIndex: index)
                }
            }
        case .albums:
            MuzzaSavedGrid(items: playback.albums, playback: playback, symbol: "square.stack", emptyMessage: "No saved albums yet.")
        case .artists:
            MuzzaSavedGrid(items: playback.artists, playback: playback, symbol: "person", emptyMessage: "No saved artists yet.")
        case .downloads:
            let items = playback.downloads.filter { !hideExplicit || !($0.explicit ?? false) }
            if items.isEmpty {
                MuzzaEmptyView(symbol: "arrow.down.circle", message: "No downloaded songs yet.")
            } else {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    MuzzaSongRow(item: item, playback: playback, queue: items, queueIndex: index)
                }
            }
        case .history:
            if recent.isEmpty {
                MuzzaEmptyView(symbol: "clock", message: "No listening history yet.")
            } else {
                ForEach(Array(recent.enumerated()), id: \.offset) { index, item in
                    MuzzaSongRow(item: item, playback: playback, queue: recent, queueIndex: index)
                }
            }
        }
    }

    private func localItems(for category: MuzzaLibraryCategory) -> [MuzzaLiveItem] {
        switch category {
        case .playlists: return playback.playlists
        case .songs: return liked
        case .albums: return playback.albums
        case .artists: return playback.artists
        case .downloads: return playback.downloads
        case .history: return recent
        }
    }
}

private struct MuzzaYouTubeLibraryContent: View {
    let category: MuzzaLibraryCategory
    @ObservedObject var account: MuzzaAccount
    @ObservedObject var playback: MuzzaPlayback
    let excludedIDs: Set<String>
    let refreshToken: Int
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var items: [MuzzaLiveItem] = []
    @State private var continuation: String?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()
    @State private var loadedKey: String?
    @State private var loadedContinuations: Set<String> = []
    init(
        category: MuzzaLibraryCategory,
        account: MuzzaAccount,
        playback: MuzzaPlayback,
        excludedIDs: Set<String>,
        refreshToken: Int
    ) {
        self.category = category
        _account = ObservedObject(wrappedValue: account)
        _playback = ObservedObject(wrappedValue: playback)
        self.excludedIDs = excludedIDs
        self.refreshToken = refreshToken
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("YouTube Music").font(.title2.bold())
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                if item.kind == "song" {
                    MuzzaSongRow(item: item, playback: playback, queue: items, queueIndex: index)
                } else {
                    NavigationLink(value: item) {
                        HStack(spacing: 12) {
                            MuzzaArtwork(item: item, size: 58)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.title).font(.headline).lineLimit(1)
                                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if ["album", "artist", "playlist"].contains(item.kind) {
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
            if isLoading {
                ProgressView().frame(maxWidth: .infinity).padding()
            } else if let loadError {
                MuzzaErrorView(message: loadError) { Task { await reload() } }
            } else if items.isEmpty {
                MuzzaEmptyView(symbol: "music.note.list", message: "Your YouTube Music library is empty.")
            }
            if let pageError {
                MuzzaErrorView(message: pageError) { Task { await loadMore() } }
            } else if continuation != nil && !isLoading {
                ProgressView().frame(maxWidth: .infinity).task(id: continuation) { await loadMore() }
            }
        }
        .task(id: "\(category.rawValue)-\(account.revision)-\(playback.libraryChangeToken)-\(hideExplicit)-\(refreshToken)") {
            await reload()
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        let key = "\(category.rawValue)-\(account.revision)-\(hideExplicit)"
        let canPreserveItems = loadedKey == key
        isLoading = true
        loadError = nil
        pageError = nil
        if !canPreserveItems {
            continuation = nil
        }
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.youtubeLibrary(
                category: category.rawValue.lowercased(), hideExplicit: hideExplicit, continuation: nil
            )
            guard !Task.isCancelled, requestID == id else { return }
            let freshItems = response.items.filter { !excludedIDs.contains($0.id) }
            let retainedItems = items.filter { !excludedIDs.contains($0.id) }
            items = uniqueItems(canPreserveItems ? freshItems + retainedItems : freshItems)
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
            let response = try await MuzzaAPI.youtubeLibrary(
                category: category.rawValue.lowercased(), hideExplicit: hideExplicit, continuation: token
            )
            guard !Task.isCancelled, requestID == id else { return }
            loadedContinuations.insert(token)
            items = uniqueItems(items + response.items.filter { !excludedIDs.contains($0.id) })
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
            LazyVGrid(columns: columns, alignment: .leading, spacing: 20) {
                ForEach(items) { item in
                    NavigationLink(value: item) {
                        VStack(alignment: .leading, spacing: 8) {
                            MuzzaArtwork(item: item, size: 130)
                            Text(item.title).font(.subheadline.bold()).lineLimit(2)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if ["album", "artist", "playlist"].contains(item.kind) {
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

    init(item: MuzzaLiveItem, playback: MuzzaPlayback) {
        self.item = item
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    if !item.thumbnail.isEmpty {
                        MuzzaArtwork(item: item, size: 200)
                    }
                    Text(item.title).font(.largeTitle.bold())
                    if !item.subtitle.isEmpty {
                        Text(item.subtitle).foregroundStyle(.secondary)
                    }
                    HStack {
                        if let first = songs.first {
                            Button {
                                playback.play(first, in: songs, startingAt: 0)
                            } label: {
                                Label("Play", systemImage: "play.fill")
                            }
                            .buttonStyle(.borderedProminent)
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
                    }
                }
                ForEach(Array(songs.enumerated()), id: \.offset) { index, song in
                    MuzzaSongRow(
                        item: song, playback: playback, queue: songs, queueIndex: index,
                        playlist: item.kind == "localPlaylist" ? item : nil,
                        onPlaylistChange: { songs = visible($0) }
                    )
                }
                ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                    MuzzaMediaSectionView(section: section, playback: playback)
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
                    ProgressView().frame(maxWidth: .infinity).task(id: continuation) { await loadMore() }
                }
            }
            .padding(.horizontal, 16).padding(.bottom, 100)
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
