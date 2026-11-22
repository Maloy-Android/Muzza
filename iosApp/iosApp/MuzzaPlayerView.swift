import SwiftUI

struct MuzzaMiniPlayer: View {
    @ObservedObject var playback: MuzzaPlayback
    let open: () -> Void

    var body: some View {
        if let item = playback.current {
            HStack(spacing: 10) {
                Button(action: open) {
                    HStack(spacing: 10) {
                        MuzzaArtwork(item: item, size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button { playback.toggleLike(item) } label: {
                    Image(systemName: playback.isLiked(item) ? "heart.fill" : "heart")
                }
                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                .accessibilityLabel(playback.isLiked(item) ? "Remove like" : "Like")
                Button { playback.toggle() } label: {
                    if playback.isLoadingAudio {
                        ProgressView().frame(width: 24, height: 24)
                    } else {
                        Image(systemName: playback.isPlaying ? "pause.fill" : "play.fill")
                            .frame(width: 24, height: 24)
                    }
                }
                .accessibilityLabel(playback.isPlaying ? "Pause" : "Play")
            }
            .padding(10).background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16)).padding(.horizontal)
        }
    }
}

struct MuzzaPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @State private var showQueue = false
    @State private var showLyrics = false
    @State private var showPlayerMenu = false

    init(playback: MuzzaPlayback) {
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 28) {
                    HStack {
                        Button { dismiss() } label: { Image(systemName: "chevron.down").font(.title3.bold()) }
                            .accessibilityLabel("Close player")
                        Spacer()
                        Text("Now playing").font(.headline)
                        Spacer()
                        Button { showQueue = true } label: { Image(systemName: "list.bullet").font(.title3) }
                            .accessibilityLabel("Queue")
                    }
                    if let item = playback.current {
                        MuzzaArtwork(item: item, size: max(0, min(geometry.size.width - 48, 420)))
                            .padding(.vertical, 12)
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.title2.bold()).lineLimit(2)
                                Text(item.subtitle).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Button { playback.toggleLike(item) } label: {
                                Image(systemName: playback.isLiked(item) ? "heart.fill" : "heart")
                                    .font(.title2)
                            }
                            .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                            .accessibilityLabel(playback.isLiked(item) ? "Remove like" : "Like")
                        }
                        VStack(spacing: 6) {
                            Slider(
                                value: Binding(
                                    get: { min(playback.position, max(playback.duration, 1)) },
                                    set: { playback.seek(to: $0) }
                                ),
                                in: 0...max(playback.duration, 1)
                            )
                            .disabled(playback.duration <= 0)
                            HStack {
                                Text(timeText(playback.position))
                                Spacer()
                                Text(timeText(playback.duration))
                            }
                            .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                        HStack {
                            Button { playback.toggleShuffle() } label: {
                                Image(systemName: "shuffle").font(.title3)
                                    .foregroundStyle(playback.isShuffling ? Color.accentColor : Color.secondary)
                            }
                            .accessibilityLabel("Shuffle")
                            .accessibilityValue(playback.isShuffling ? "On" : "Off")
                            Spacer(minLength: 12)
                            Button { playback.previous() } label: {
                                Image(systemName: "backward.end.fill").font(.title2)
                            }
                            .accessibilityLabel("Previous")
                            Spacer(minLength: 12)
                            Button { playback.toggle() } label: {
                                Group {
                                    if playback.isLoadingAudio {
                                        ProgressView()
                                    } else {
                                        Image(systemName: playback.isPlaying ? "pause.fill" : "play.fill")
                                            .font(.title)
                                    }
                                }
                                .frame(width: 72, height: 72)
                                .background(.regularMaterial).clipShape(Circle())
                            }
                            .accessibilityLabel(playback.isPlaying ? "Pause" : "Play")
                            Spacer(minLength: 12)
                            Button { playback.next() } label: {
                                if playback.isLoadingRadio && playback.queueIndex == playback.queue.count - 1 {
                                    ProgressView().frame(width: 24, height: 24)
                                } else {
                                    Image(systemName: "forward.end.fill").font(.title2)
                                }
                            }
                            .disabled(!playback.canAdvance)
                            .accessibilityLabel("Next")
                            Spacer(minLength: 12)
                            Button { playback.cycleRepeat() } label: {
                                Image(systemName: playback.repeatMode == .one ? "repeat.1" : "repeat")
                                    .font(.title3)
                                    .foregroundStyle(playback.repeatMode == .off ? Color.secondary : Color.accentColor)
                            }
                            .accessibilityLabel("Repeat")
                            .accessibilityValue(Text(LocalizedStringKey(playback.repeatMode.rawValue.capitalized)))
                        }
                        HStack(spacing: 16) {
                            Button { showQueue = true } label: {
                                Label("Queue", systemImage: "list.bullet")
                            }
                            Button { showLyrics = true } label: {
                                Label("Lyrics", systemImage: "text.alignleft")
                            }
                            Button { playback.startRadio(item) } label: {
                                Label("Radio", systemImage: "dot.radiowaves.left.and.right")
                            }
                            .disabled(playback.isLoadingRadio)
                            Button { showPlayerMenu = true } label: {
                                Label("More", systemImage: "ellipsis")
                            }
                        }
                        .buttonStyle(.bordered)
                    } else {
                        MuzzaEmptyView(symbol: "music.note", message: "No song selected.")
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height, alignment: .top)
            }
            .background(
                LinearGradient(
                    colors: [Color(.secondarySystemBackground), Color(.systemBackground)],
                    startPoint: .top, endPoint: .bottom
                )
            )
        }
        .sheet(isPresented: $showQueue) { MuzzaQueueView(playback: playback) }
        .sheet(isPresented: $showLyrics) { MuzzaLyricsView(playback: playback) }
        .sheet(isPresented: $showPlayerMenu) {
            if let item = playback.current {
                MuzzaPlayerActionsView(item: item, playback: playback)
            }
        }
    }

    private func timeText(_ value: Double) -> String {
        guard value.isFinite, value > 0 else { return "0:00" }
        let seconds = Int(value)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

private struct MuzzaPlayerActionsView: View {
    let item: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback
    @State private var showSleepTimerOptions = false

    var body: some View {
        NavigationStack {
            List {
                Section("Playback") {
                    NavigationLink {
                        MuzzaQueueView(playback: playback)
                    } label: {
                        Label("Queue", systemImage: "list.bullet")
                    }
                    HStack {
                        Label("Playback speed", systemImage: "speedometer")
                        Spacer()
                        Slider(
                            value: Binding(
                                get: { playback.playbackRate },
                                set: { playback.setPlaybackRate($0) }
                            ),
                            in: 0.5...2.0,
                            step: 0.05
                        )
                        .frame(width: 150)
                        Text(String(format: "%.2gx", playback.playbackRate))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                    Toggle(
                        "Autoplay",
                        isOn: Binding(
                            get: { playback.isAutoplayEnabled },
                            set: { playback.setAutoplayEnabled($0) }
                        )
                    )
                    Button {
                        showSleepTimerOptions = true
                    } label: {
                        HStack {
                            Label("Sleep timer", systemImage: "moon.zzz")
                            Spacer()
                            if playback.sleepTimerActive {
                                Text(timeText(playback.sleepTimerRemaining))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    HStack {
                        Label("Volume", systemImage: playback.volume == 0 ? "speaker.slash" : "speaker.wave.2")
                        Slider(
                            value: Binding(
                                get: { playback.volume },
                                set: { playback.setVolume($0) }
                            ),
                            in: 0...1
                        )
                        .accessibilityLabel("Volume")
                    }
                    Button {
                        playback.startRadio(item)
                    } label: {
                        Label("Start radio", systemImage: "dot.radiowaves.left.and.right")
                    }
                    Button {
                        playback.enqueue(item, playNext: true)
                    } label: {
                        Label("Play next", systemImage: "text.line.first.and.arrowtriangle.forward")
                    }
                    Button {
                        playback.enqueue(item, playNext: false)
                    } label: {
                        Label("Add to queue", systemImage: "text.badge.plus")
                    }
                    if let url = URL(string: "https://music.youtube.com/watch?v=\(item.id)") {
                        ShareLink(item: url) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }
                    if playback.isDownloaded(item) {
                        Button(role: .destructive) {
                            Task { _ = await playback.removeDownload(item) }
                        } label: {
                            Label("Remove download", systemImage: "arrow.down.circle")
                        }
                        .disabled(playback.isLibraryBusy)
                    } else {
                        Button {
                            playback.download(item)
                        } label: {
                            Label(
                                playback.downloading.contains(item.id) ? "Downloading…" : "Download / cache",
                                systemImage: "arrow.down.circle"
                            )
                        }
                        .disabled(!playback.libraryLoaded || playback.isLibraryBusy || playback.downloading.contains(item.id))
                    }
                }

                if item.album != nil || !(item.artists ?? []).isEmpty || item.playlistId != nil {
                    Section("Navigate") {
                        if let album = item.album {
                            NavigationLink {
                                MuzzaCollectionView(item: collectionItem(
                                    id: album.id, title: album.title, subtitle: item.subtitle,
                                    thumbnail: item.thumbnail, kind: "album"
                                ), playback: playback)
                            } label: {
                                Label("View album", systemImage: "square.stack")
                            }
                        }
                        ForEach(Array((item.artists ?? []).enumerated()), id: \.offset) { _, artist in
                            if let artistID = artist.id {
                                NavigationLink {
                                    MuzzaCollectionView(item: collectionItem(
                                        id: artistID, title: artist.name, subtitle: "Artist",
                                        thumbnail: artist.thumbnail ?? "", kind: "artist"
                                    ), playback: playback)
                                } label: {
                                    Label("View \(artist.name)", systemImage: "person")
                                }
                            }
                        }
                        if let playlistId = item.playlistId {
                            NavigationLink {
                                MuzzaCollectionView(item: collectionItem(
                                    id: playlistId, title: item.playlistTitle ?? "Playlist",
                                    subtitle: "", thumbnail: "", kind: "playlist"
                                ), playback: playback)
                            } label: {
                                Label("View playlist", systemImage: "music.note.list")
                            }
                        }
                    }
                }

                let addablePlaylists = playback.playlists.filter {
                    $0.kind == "localPlaylist" ||
                        ($0.kind == "playlist" && $0.isEditable == true && playback.canSyncPlaylists)
                }
                if !addablePlaylists.isEmpty {
                    Section("Playlist") {
                        ForEach(addablePlaylists) { playlist in
                            Button {
                                Task {
                                    do {
                                        try await playback.add(item, to: playlist, allowDuplicate: false)
                                    } catch {
                                        playback.message = error.localizedDescription
                                    }
                                }
                            } label: {
                                Label("Add to \(playlist.title)", systemImage: "text.badge.plus")
                            }
                            .disabled(playback.isLibraryBusy)
                        }
                    }
                }
            }
            .navigationTitle("Player menu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .confirmationDialog("Sleep timer", isPresented: $showSleepTimerOptions) {
                Button("15 minutes") { playback.startSleepTimer(minutes: 15) }
                Button("30 minutes") { playback.startSleepTimer(minutes: 30) }
                Button("45 minutes") { playback.startSleepTimer(minutes: 45) }
                Button("60 minutes") { playback.startSleepTimer(minutes: 60) }
                Button("End of current song") { playback.startSleepTimerAtEndOfSong() }
                if playback.sleepTimerActive {
                    Button("Cancel timer", role: .destructive) { playback.cancelSleepTimer() }
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }

    @Environment(\.dismiss) private var dismiss

    private func collectionItem(
        id: String, title: String, subtitle: String, thumbnail: String, kind: String
    ) -> MuzzaLiveItem {
        MuzzaLiveItem(
            id: id, title: title, subtitle: subtitle, thumbnail: thumbnail, kind: kind,
            params: nil, explicit: false, duration: nil, entryId: nil, downloadPath: nil,
            remoteLiked: nil
        )
    }

    private func timeText(_ value: Double) -> String {
        guard value.isFinite, value > 0 else { return "0:00" }
        let seconds = Int(value)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

private struct MuzzaQueueView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @State private var showClearConfirmation = false

    init(playback: MuzzaPlayback) {
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(playback.queueEntries) { entry in
                    HStack {
                        MuzzaSongRow(
                            item: entry.item, playback: playback, queue: playback.queue,
                            queueIndex: entry.index, usesCurrentQueue: true
                        )
                        if entry.index == playback.queueIndex {
                            Image(systemName: "speaker.wave.2.fill").foregroundStyle(Color.accentColor)
                        }
                    }
                }
                .onMove { playback.moveQueue(from: $0, to: $1) }
                .onDelete { playback.removeFromQueue(at: $0) }
                if playback.isLoadingRadio {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    EditButton()
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !playback.queue.isEmpty {
                        Menu {
                            Button("Remove Played") {
                                playback.removePlayedFromQueue()
                            }
                            .disabled(playback.queueIndex == 0)
                            Button("Remove Upcoming") {
                                playback.removeUpcomingFromQueue()
                            }
                            .disabled(playback.queueIndex + 1 >= playback.queue.count)
                            Button("Clear Queue", role: .destructive) {
                                showClearConfirmation = true
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                        }
                    }
                }
            }
            .confirmationDialog("Clear the queue?", isPresented: $showClearConfirmation, titleVisibility: .visible) {
                Button("Clear Queue", role: .destructive) {
                    playback.clearQueue()
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }
}

struct MuzzaSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @AppStorage("muzza.darkTheme") private var darkTheme = true
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @AppStorage("muzza.persistentQueue") private var persistentQueue = true

    init(playback: MuzzaPlayback, account: MuzzaAccount) {
        _playback = ObservedObject(wrappedValue: playback)
        _account = ObservedObject(wrappedValue: account)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Account") {
                    NavigationLink {
                        MuzzaAccountView(account: account, playback: playback)
                    } label: {
                        HStack {
                            Image(systemName: "person.crop.circle")
                            if let profile = account.profile {
                                Text(profile.name)
                            } else {
                                Text("Sign in")
                            }
                        }
                    }
                }
                Section("Appearance") {
                    Toggle("Dark theme", isOn: $darkTheme)
                    Toggle("Pure black", isOn: $pureBlack)
                }
                Section("Content") {
                    Toggle("Hide explicit content", isOn: $hideExplicit)
                }
                Section("Playback") {
                    Toggle("Persistent queue", isOn: $persistentQueue)
                        .onChange(of: persistentQueue) { playback.queuePreferenceChanged($0) }
                    Picker("Playback speed", selection: Binding(
                        get: { playback.playbackRate },
                        set: { playback.setPlaybackRate($0) }
                    )) {
                        Text("0.5x").tag(0.5)
                        Text("0.75x").tag(0.75)
                        Text("1x").tag(1.0)
                        Text("1.25x").tag(1.25)
                        Text("1.5x").tag(1.5)
                        Text("1.75x").tag(1.75)
                        Text("2x").tag(2.0)
                    }
                }
                Section("Downloads") {
                    Text("Keep Muzza open while a song is downloading.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
