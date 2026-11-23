import Foundation
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

private struct MuzzaBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    static var writableContentTypes: [UTType] { [.json] }

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct MuzzaMiniPlayer: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject private var timeline: MuzzaPlaybackProgress
    let open: () -> Void

    init(playback: MuzzaPlayback, open: @escaping () -> Void) {
        _playback = ObservedObject(wrappedValue: playback)
        _timeline = ObservedObject(wrappedValue: playback.progress)
        self.open = open
    }

    private var progress: CGFloat {
        guard timeline.duration > 0 else { return 0 }
        return min(max(CGFloat(timeline.position / timeline.duration), 0), 1)
    }

    var body: some View {
        if let item = playback.current {
            ZStack(alignment: .bottom) {
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
                    Button { playback.next() } label: {
                        Image(systemName: "forward.end.fill")
                            .frame(width: 24, height: 24)
                    }
                    .disabled(!playback.canAdvance)
                    .accessibilityLabel("Next")
                }
                .padding(10)
                .padding(.bottom, 2)

                GeometryReader { geometry in
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: geometry.size.width * progress, height: 2)
                }
                .frame(height: 2)
            }
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal)
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
        NavigationStack {
            GeometryReader { geometry in
                ScrollView {
                    VStack(spacing: 28) {
                        HStack {
                            Button { dismiss() } label: { Image(systemName: "chevron.down").font(.title3.bold()) }
                                .accessibilityLabel("Close player")
                            Spacer()
                            Text(playback.queueTitle ?? "Now playing").font(.headline).lineLimit(1)
                            Spacer()
                            Button { showQueue = true } label: { Image(systemName: "list.bullet").font(.title3) }
                                .accessibilityLabel("Queue")
                        }
                        if let item = playback.current {
                            MuzzaArtwork(item: item, size: max(0, min(geometry.size.width - 48, 420)))
                                .padding(.vertical, 12)
                            HStack(alignment: .center) {
                                VStack(alignment: .leading, spacing: 4) {
                                    if let album = item.album {
                                        NavigationLink(value: collectionItem(
                                            id: album.id, title: album.title, subtitle: item.subtitle,
                                            thumbnail: item.thumbnail, kind: "album"
                                        )) {
                                            Text(item.title).font(.title2.bold()).lineLimit(2)
                                                .foregroundStyle(.primary)
                                        }
                                        .buttonStyle(.plain)
                                    } else {
                                        Text(item.title).font(.title2.bold()).lineLimit(2)
                                    }
                                    artistLinks(for: item)
                                }
                                Spacer()
                                Button { playback.toggleLike(item) } label: {
                                    Image(systemName: playback.isLiked(item) ? "heart.fill" : "heart")
                                        .font(.title2)
                                }
                                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                                .accessibilityLabel(playback.isLiked(item) ? "Remove like" : "Like")
                                Button { showPlayerMenu = true } label: {
                                    Image(systemName: "ellipsis")
                                        .font(.title2)
                                        .frame(width: 36, height: 36)
                                        .background(Color(.secondarySystemFill))
                                        .clipShape(Circle())
                                }
                                .accessibilityLabel("Player menu")
                            }
                            MuzzaPlayerSlider(playback: playback)
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
                                    Image(systemName: "list.bullet")
                                }
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(Color(.secondarySystemFill))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .buttonStyle(.plain)
                                .accessibilityLabel("Queue")
                                Button { showLyrics = true } label: {
                                    Image(systemName: "text.alignleft")
                                }
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(Color(.secondarySystemFill))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .buttonStyle(.plain)
                                .accessibilityLabel("Lyrics")
                                Button { playback.startRadio(item) } label: {
                                    Image(systemName: "dot.radiowaves.left.and.right")
                                }
                                .disabled(playback.isLoadingRadio)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(Color(.secondarySystemFill))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .buttonStyle(.plain)
                                .accessibilityLabel("Start radio")
                            }
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
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .toolbar(.hidden, for: .navigationBar)
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 20)
                .onEnded { value in
                    guard value.translation.height > 120,
                          value.translation.height > abs(value.translation.width) else { return }
                    dismiss()
                }
        )
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

    @ViewBuilder
    private func artistLinks(for item: MuzzaLiveItem) -> some View {
        let artists = item.artists ?? []
        if artists.isEmpty {
            Text(item.subtitle).foregroundStyle(.secondary).lineLimit(1)
        } else {
            HStack(spacing: 4) {
                ForEach(Array(artists.enumerated()), id: \.offset) { index, artist in
                    if let id = artist.id {
                        NavigationLink(value: collectionItem(
                            id: id, title: artist.name, subtitle: "Artist",
                            thumbnail: artist.thumbnail ?? "", kind: "artist"
                        )) {
                            Text(artist.name).foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Text(artist.name).foregroundStyle(.secondary)
                    }
                    if index < artists.count - 1 { Text(", ").foregroundStyle(.secondary) }
                }
            }
            .lineLimit(1)
        }
    }

    private func collectionItem(
        id: String, title: String, subtitle: String, thumbnail: String, kind: String
    ) -> MuzzaLiveItem {
        MuzzaLiveItem(
            id: id, title: title, subtitle: subtitle, thumbnail: thumbnail, kind: kind,
            params: nil, explicit: false, duration: nil, entryId: nil, downloadPath: nil,
            remoteLiked: nil
        )
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
    @AppStorage("muzza.themeMode") private var themeMode = MuzzaThemePreference.initialValue
    @AppStorage("muzza.accentColor") private var accentColor = MuzzaAccent.coral.rawValue
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @AppStorage("muzza.persistentQueue") private var persistentQueue = true
    @AppStorage("muzza.contentLanguage") private var contentLanguage = ""
    @AppStorage("muzza.contentCountry") private var contentCountry = ""
    @AppStorage("muzza.audioQuality") private var audioQuality = "auto"
    @AppStorage("muzza.normalizeAudio") private var normalizeAudio = false
    @AppStorage("muzza.skipSilence") private var skipSilence = false
    @AppStorage("muzza.autoplayBluetooth") private var autoplayBluetooth = false
    @AppStorage("muzza.autoSkipError") private var autoSkipError = false
    @AppStorage("muzza.pauseListenHistory") private var pauseListenHistory = false
    @AppStorage("muzza.pauseSearchHistory") private var pauseSearchHistory = false
    @AppStorage("muzza.cacheMemoryMB") private var cacheMemoryMB = 16
    @AppStorage("muzza.cacheDiskMB") private var cacheDiskMB = 100
    @State private var showBackupExporter = false
    @State private var showBackupImporter = false
    @State private var backupData = Data()
    @State private var backupError: String?
    @State private var notificationAuthorization = false
    @AppStorage("muzza.notificationsEnabled") private var notificationsEnabled = false

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
                    Picker("Accent color", selection: $accentColor) {
                        ForEach(MuzzaAccent.allCases, id: \.rawValue) { accent in
                            HStack {
                                Circle().fill(accent.color).frame(width: 12, height: 12)
                                Text(accent.title)
                            }
                            .tag(accent.rawValue)
                        }
                    }
                    Picker("Theme", selection: $themeMode) {
                        Text("Follow system").tag("system")
                        Text("Dark").tag("dark")
                        Text("Light").tag("light")
                    }
                    Toggle("Pure black", isOn: $pureBlack)
                }
                Section("Content") {
                    Toggle("Hide explicit content", isOn: $hideExplicit)
                    TextField("Content language", text: $contentLanguage)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Content country", text: $contentCountry)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    Toggle("Pause search history", isOn: $pauseSearchHistory)
                    Text("Language and country are saved locally; content requests currently use service defaults.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Playback") {
                    Toggle("Persistent queue", isOn: $persistentQueue)
                        .onChange(of: persistentQueue) { playback.queuePreferenceChanged($0) }
                    Picker("Audio quality", selection: $audioQuality) {
                        Text("Auto").tag("auto")
                        Text("High").tag("high")
                        Text("Standard").tag("standard")
                        Text("Low").tag("low")
                    }
                    Toggle("Normalize audio", isOn: $normalizeAudio)
                    Toggle("Skip silence", isOn: $skipSilence)
                    Toggle("Autoplay on Bluetooth", isOn: $autoplayBluetooth)
                    Toggle("Skip playback errors", isOn: $autoSkipError)
                    Toggle("Pause listening history", isOn: $pauseListenHistory)
                    Text("Quality, normalization and silence preferences are saved but aren't applied by the current iOS audio pipeline.")
                        .font(.footnote).foregroundStyle(.secondary)
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
                Section("Cache") {
                    Picker("Memory cache", selection: $cacheMemoryMB) {
                        Text("Off").tag(0)
                        Text("16 MB").tag(16)
                        Text("32 MB").tag(32)
                        Text("64 MB").tag(64)
                    }
                    Picker("Disk cache", selection: $cacheDiskMB) {
                        Text("Off").tag(0)
                        Text("100 MB").tag(100)
                        Text("250 MB").tag(250)
                        Text("500 MB").tag(500)
                    }
                    Button("Clear cached web responses", role: .destructive) {
                        URLCache.shared.removeAllCachedResponses()
                    }
                    Text("This cache is separate from songs you explicitly download.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Downloads") {
                    Text("Keep Muzza open while a song is downloading.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Notifications") {
                    Toggle("Playback notifications", isOn: $notificationsEnabled)
                        .onChange(of: notificationsEnabled) { enabled in
                            if enabled {
                                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                                    DispatchQueue.main.async {
                                        notificationAuthorization = granted
                                        notificationsEnabled = granted
                                    }
                                }
                            }
                        }
                    Text(notificationAuthorization
                         ? "iOS notifications are authorized."
                         : "iOS will ask for permission before notifications are enabled.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Recommendations") {
                    Button("Reset local recommendation state", role: .destructive) {
                        UserDefaults.standard.removeObject(forKey: "muzza.queue")
                        UserDefaults.standard.removeObject(forKey: "muzza.shuffle")
                    playback.clearQueue()
                        playback.message = "Local recommendation state was reset. Refresh Home to load a fresh feed."
                    }
                    Text("Resets Muzza's local radio/queue state. Account-side YouTube recommendations are controlled by YouTube Music.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Backup & Import") {
                    Button("Export library backup") {
                        do {
                            backupData = try playback.exportBackup()
                            showBackupExporter = true
                        } catch {
                            backupError = error.localizedDescription
                        }
                    }
                    Button("Import backup, M3U or Spotify export") {
                        showBackupImporter = true
                    }
                    Text("Backup includes queue, preferences and cached library metadata. Spotify imports support exported JSON/CSV track metadata and match tracks through Muzza.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .onChange(of: cacheMemoryMB) { _ in MuzzaCacheSettings.apply() }
            .onChange(of: cacheDiskMB) { _ in MuzzaCacheSettings.apply() }
            .navigationTitle("Settings")
            .task {
                let settings = await UNUserNotificationCenter.current().notificationSettings()
                notificationAuthorization = settings.authorizationStatus == .authorized
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .fileExporter(
                isPresented: $showBackupExporter,
                document: MuzzaBackupDocument(data: backupData),
                contentType: .json,
                defaultFilename: "Muzza-Backup-\(Self.dateStamp).muzza.json"
            ) { _ in }
            .fileImporter(
                isPresented: $showBackupImporter,
                allowedContentTypes: [.json, .plainText, .commaSeparatedText],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    guard let url = urls.first,
                          url.startAccessingSecurityScopedResource() else {
                        backupError = "The selected file could not be accessed."
                        return
                    }
                    defer { url.stopAccessingSecurityScopedResource() }
                    guard let data = try? Data(contentsOf: url) else {
                        backupError = "The selected file could not be read."
                        return
                    }
                    Task {
                        let restored: Bool
                        if url.pathExtension.lowercased() == "m3u"
                            || url.pathExtension.lowercased() == "m3u8" {
                            restored = await playback.importM3U(data)
                        } else if url.pathExtension.lowercased() == "csv" {
                            restored = await playback.importPlaylistExport(data)
                        } else if url.pathExtension.lowercased() == "json" {
                            let jsonObject = try? JSONSerialization.jsonObject(with: data)
                            var isBackup = false
                            if let object = jsonObject as? [String: Any],
                               let formatVersion = object["formatVersion"] as? Int {
                                isBackup = formatVersion == 1
                            }
                            if isBackup {
                                restored = await playback.importBackup(data)
                            } else {
                                restored = await playback.importPlaylistExport(data)
                            }
                        } else {
                            restored = await playback.importBackup(data)
                        }
                        if !restored && playback.message == nil {
                            backupError = "The selected backup or M3U file could not be imported."
                        }
                    }
                case .failure(let error):
                    backupError = error.localizedDescription
                }
            }
            .alert("Import or export failed", isPresented: Binding(
                get: { backupError != nil },
                set: { if !$0 { backupError = nil } }
            )) {
                Button("OK", role: .cancel) { backupError = nil }
            } message: {
                Text(backupError ?? "")
            }
        }
    }

    private static var dateStamp: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
