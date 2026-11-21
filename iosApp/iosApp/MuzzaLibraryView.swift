import SwiftUI

enum MuzzaLibraryCategory: String, CaseIterable {
    case playlists = "Playlists"
    case songs = "Songs"
    case albums = "Albums"
    case artists = "Artists"
    case downloads = "Downloads"
    case history = "History"
}

private enum MuzzaLibrarySource: String, CaseIterable {
    case device = "On device"
    case youtube = "YouTube Music"
}

struct MuzzaLibraryView: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @Binding var category: MuzzaLibraryCategory
    @State private var source: MuzzaLibrarySource = .device
    @State private var remoteRefresh = 0
    @State private var showCreate = false
    @State private var playlistName = ""
    @State private var showDelete = false
    @State private var playlistToDelete: MuzzaLiveItem?

    init(
        playback: MuzzaPlayback,
        account: MuzzaAccount,
        showSearch: Binding<Bool>,
        showSettings: Binding<Bool>,
        category: Binding<MuzzaLibraryCategory>
    ) {
        _playback = ObservedObject(wrappedValue: playback)
        _account = ObservedObject(wrappedValue: account)
        _showSearch = showSearch
        _showSettings = showSettings
        _category = category
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
                    if account.profile != nil {
                        Picker("Library source", selection: $source) {
                            ForEach(MuzzaLibrarySource.allCases, id: \.self) { value in
                                Text(LocalizedStringKey(value.rawValue)).tag(value)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(MuzzaLibraryCategory.allCases.filter {
                                source == .device || ($0 != .history && $0 != .downloads)
                            }, id: \.self) { item in
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
                    if source == .youtube && account.profile != nil {
                        MuzzaYouTubeLibraryContent(
                            category: category, account: account, playback: playback, refreshToken: remoteRefresh
                        )
                    } else if playback.isLibraryLoading && !playback.libraryLoaded {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let error = playback.libraryError {
                        MuzzaErrorView(message: error) { Task { await playback.refreshLibrary() } }
                    } else {
                        categoryContent
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .refreshable {
                if source == .youtube {
                    remoteRefresh += 1
                } else {
                    await playback.refreshLibrary()
                }
            }
            .onChange(of: source) { value in
                if value == .youtube && (category == .history || category == .downloads) { category = .songs }
            }
            .onChange(of: account.revision) { _ in
                if account.profile == nil { source = .device }
            }
        }
        .alert("New playlist", isPresented: $showCreate) {
            TextField("Playlist name", text: $playlistName)
            Button("Cancel", role: .cancel) { playlistName = "" }
            Button("Create") {
                let name = playlistName
                Task {
                    if await playback.createPlaylist(name) { playlistName = "" }
                }
            }
            .disabled(playlistName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        } message: {
            Text("The playlist is stored on this device.")
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
            Button { showCreate = true } label: {
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
            MuzzaSavedGrid(items: playback.albums, symbol: "square.stack", emptyMessage: "No saved albums yet.")
        case .artists:
            MuzzaSavedGrid(items: playback.artists, symbol: "person", emptyMessage: "No saved artists yet.")
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
}

private struct MuzzaYouTubeLibraryContent: View {
    let category: MuzzaLibraryCategory
    @ObservedObject var account: MuzzaAccount
    @ObservedObject var playback: MuzzaPlayback
    let refreshToken: Int
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var items: [MuzzaLiveItem] = []
    @State private var continuation: String?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()

    init(category: MuzzaLibraryCategory, account: MuzzaAccount, playback: MuzzaPlayback, refreshToken: Int) {
        self.category = category
        _account = ObservedObject(wrappedValue: account)
        _playback = ObservedObject(wrappedValue: playback)
        self.refreshToken = refreshToken
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
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
                ProgressView().frame(maxWidth: .infinity).task { await loadMore() }
            }
        }
        .task(id: "\(category.rawValue)-\(account.revision)-\(playback.libraryChangeToken)-\(hideExplicit)-\(refreshToken)") {
            await reload()
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        pageError = nil
        items = []
        continuation = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.youtubeLibrary(
                category: category.rawValue.lowercased(), hideExplicit: hideExplicit, continuation: nil
            )
            guard !Task.isCancelled, requestID == id else { return }
            items = response.items
            continuation = response.continuation
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.youtubeLibrary(
                category: category.rawValue.lowercased(), hideExplicit: hideExplicit, continuation: token
            )
            guard !Task.isCancelled, requestID == id else { return }
            items.append(contentsOf: response.items)
            if response.continuation == token {
                pageError = "YouTube returned an invalid continuation."
            } else {
                continuation = response.continuation
            }
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            pageError = error.localizedDescription
        }
    }
}

private struct MuzzaSavedGrid: View {
    let items: [MuzzaLiveItem]
    let symbol: String
    let emptyMessage: String
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    init(items: [MuzzaLiveItem], symbol: String, emptyMessage: String) {
        self.items = items
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
                    ProgressView().frame(maxWidth: .infinity).task { await loadMore() }
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
    }

    private func visible(_ items: [MuzzaLiveItem]) -> [MuzzaLiveItem] {
        items.filter { !hideExplicit || !($0.explicit ?? false) }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        pageError = nil
        songs = []
        sections = []
        continuation = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.collection(item, hideExplicit: hideExplicit)
            guard !Task.isCancelled, requestID == id else { return }
            songs = visible(response.items)
            sections = response.sections
            continuation = response.continuation
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.collection(item, hideExplicit: hideExplicit, continuation: token)
            guard !Task.isCancelled, requestID == id else { return }
            songs.append(contentsOf: visible(response.items))
            if response.continuation == token {
                pageError = "YouTube returned an invalid continuation."
            } else {
                continuation = response.continuation
            }
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            pageError = error.localizedDescription
        }
    }
}
