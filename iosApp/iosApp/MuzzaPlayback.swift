import AVFoundation
import Foundation
import MediaPlayer
import SwiftUI

struct MuzzaSavedQueue: Codable {
    let items: [MuzzaLiveItem]
    let index: Int
    let tokens: [UUID]?
    let originalItems: [MuzzaLiveItem]?
    let originalTokens: [UUID]?
    let shuffled: Bool?
    let radioEndpoint: MuzzaRadioEndpoint?
    let radioContinuation: String?
}

private struct MuzzaBackupSnapshot: Codable {
    let formatVersion: Int
    let queue: MuzzaSavedQueue?
    let preferences: [String: String]
    let liked: [MuzzaLiveItem]
    let playlists: [MuzzaLiveItem]
    let albums: [MuzzaLiveItem]
    let artists: [MuzzaLiveItem]
    let downloads: [MuzzaLiveItem]
    let recent: [MuzzaLiveItem]
}

struct MuzzaQueueEntry: Identifiable {
    let id: UUID
    let item: MuzzaLiveItem
    let index: Int
}

@MainActor
final class MuzzaPlayback: ObservableObject {
    @Published private(set) var current: MuzzaLiveItem?
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoadingAudio = false
    @Published private(set) var position = 0.0
    @Published private(set) var duration = 0.0
    @Published private(set) var volume = 1.0
    @Published private(set) var playbackRate = 1.0
    @Published private(set) var queue: [MuzzaLiveItem] = []
    @Published private(set) var queueIndex = 0
    @Published private(set) var isShuffling = false
    @Published private(set) var repeatMode: MuzzaRepeatMode = .off
    @Published private(set) var isLoadingRadio = false
    @Published private(set) var hasRadioContinuation = false
    @Published private(set) var sleepTimerActive = false
    @Published private(set) var sleepTimerRemaining = 0.0
    @Published private(set) var liked: [MuzzaLiveItem] = []
    @Published private(set) var playlists: [MuzzaLiveItem] = []
    @Published private(set) var albums: [MuzzaLiveItem] = []
    @Published private(set) var artists: [MuzzaLiveItem] = []
    @Published private(set) var downloads: [MuzzaLiveItem] = []
    @Published private(set) var downloading: Set<String> = []
    @Published private var likeOverrides: [String: Bool] = [:]
    @Published private var hasSignedInAccount = false
    @Published private(set) var recent: [MuzzaLiveItem] = []
    @Published private(set) var isLibraryLoading = false
    @Published private(set) var isLibraryBusy = false
    @Published private(set) var libraryLoaded = false
    @Published private(set) var libraryChangeToken = 0
    @Published var libraryError: String?
    @Published var message: String?

    private let player = AVPlayer()
    private var playbackTask: Task<Void, Never>?
    private var radioTask: Task<Void, Never>?
    private var sleepTimerTask: Task<Void, Never>?
    private var sleepAtTrackEnd = false
    private var downloadTasks: [String: Task<Void, Never>] = [:]
    private var playbackID = UUID()
    private var recordedPlaybackID: UUID?
    private var queueTokens: [UUID] = []
    private var originalQueue: [MuzzaLiveItem] = []
    private var originalTokens: [UUID] = []
    private var queueIdentity = UUID()
    private var queueRevision = 0
    private var radioEndpoint: MuzzaRadioEndpoint?
    private var radioContinuation: String?
    private var pendingRadioAdvance: UUID?
    private var radioRequestID = UUID()
    private var libraryRevision = 0
    private var recentRevision = 0
    private var resourceLoader: MuzzaResourceLoader?
    private var itemObservation: NSKeyValueObservation?
    private var playerObservation: NSKeyValueObservation?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var audioRouteObserver: NSObjectProtocol?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []

    init() {
        isShuffling = UserDefaults.standard.bool(forKey: "muzza.shuffle")
        if let savedRate = UserDefaults.standard.object(forKey: "muzza.playbackRate") as? Double,
           savedRate >= 0.5, savedRate <= 2.0 {
            playbackRate = savedRate
        }
        if let savedMode = UserDefaults.standard.string(forKey: "muzza.repeatMode") {
            if let mode = MuzzaRepeatMode(rawValue: savedMode) {
                repeatMode = mode
            } else {
                message = "The saved repeat setting is invalid."
            }
        }
        restoreQueue()
        observePlayer()
        configureAudioRouteObserver()
        configureRemoteCommands()
        Task { await refreshLibrary() }
    }

    deinit {
        playbackTask?.cancel()
        radioTask?.cancel()
        for task in downloadTasks.values { task.cancel() }
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
        if let audioRouteObserver { NotificationCenter.default.removeObserver(audioRouteObserver) }
        for (command, target) in remoteTargets { command.removeTarget(target) }
        resourceLoader?.invalidate()
    }

    func isLiked(_ item: MuzzaLiveItem) -> Bool {
        if let override = likeOverrides[item.id] { return override }
        if liked.contains(where: { $0.id == item.id }) { return true }
        let sync = UserDefaults.standard.object(forKey: "muzza.syncLikes") as? Bool ?? true
        return sync && hasSignedInAccount && item.remoteLiked == true
    }

    func isSaved(_ item: MuzzaLiveItem) -> Bool {
        switch item.kind {
        case "album": return albums.contains { $0.id == item.id }
        case "artist": return artists.contains { $0.id == item.id }
        case "playlist": return playlists.contains { $0.kind == "playlist" && $0.id == item.id }
        default: return false
        }
    }

    func isDownloaded(_ item: MuzzaLiveItem) -> Bool {
        downloads.contains { $0.id == item.id }
    }

    var canSyncPlaylists: Bool { hasSignedInAccount }

    var isAutoplayEnabled: Bool { radioEndpoint != nil || hasRadioContinuation || isLoadingRadio }

    var queueEntries: [MuzzaQueueEntry] {
        zip(queueTokens, queue).enumerated().map { index, entry in
            MuzzaQueueEntry(id: entry.0, item: entry.1, index: index)
        }
    }

    var canAdvance: Bool {
        guard !queue.isEmpty else { return false }
        return MuzzaQueueRules.nextIndex(
            current: queueIndex, count: queue.count, repeatMode: repeatMode, automatic: false
        ) != nil || hasRadioContinuation || isLoadingRadio
    }

    func refreshLibrary() async {
        guard !isLibraryLoading else { return }
        isLibraryLoading = true
        libraryError = nil
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryLoading = false }
        do {
            let response: MuzzaLiveResponse
            if let legacy = UserDefaults.standard.data(forKey: "muzza.liked") {
                guard let json = String(data: legacy, encoding: .utf8) else {
                    throw MuzzaAPIError.requestFailed("Could not read the previous liked songs.")
                }
                response = try await MuzzaAPI.importLikes(json)
                UserDefaults.standard.removeObject(forKey: "muzza.liked")
            } else {
                response = try await MuzzaAPI.library()
            }
            if revision == libraryRevision {
                applyLibrary(response, includeRecent: recentAtStart == recentRevision)
            }
            libraryLoaded = true
        } catch {
            libraryError = error.localizedDescription
        }
    }

    func toggleLike(_ item: MuzzaLiveItem) {
        guard libraryLoaded, !isLibraryBusy else { return }
        let next = !isLiked(item)
        Task {
            if await changeLibrary({ try await MuzzaAPI.setLiked(item, liked: next) }) {
                likeOverrides[item.id] = next
            }
        }
    }

    func toggleSaved(_ item: MuzzaLiveItem) {
        guard libraryLoaded, !isLibraryBusy else { return }
        let next = !isSaved(item)
        Task {
            _ = await changeLibrary { try await MuzzaAPI.setSaved(item, saved: next) }
        }
    }

    func clearHistory() async -> Bool {
        await changeLibrary { try await MuzzaAPI.clearHistory() }
    }

    func exportBackup() throws -> Data {
        let defaults = UserDefaults.standard
        let preferenceKeys = [
            "muzza.themeMode", "muzza.accentColor", "muzza.pureBlack", "muzza.hideExplicit",
            "muzza.persistentQueue", "muzza.contentLanguage", "muzza.contentCountry",
            "muzza.audioQuality", "muzza.normalizeAudio", "muzza.skipSilence",
            "muzza.autoplayBluetooth", "muzza.autoSkipError", "muzza.pauseListenHistory",
            "muzza.pauseSearchHistory", "muzza.cacheMemoryMB", "muzza.cacheDiskMB",
            "muzza.syncLikes", "muzza.shuffle", "muzza.playbackRate", "muzza.repeatMode"
        ]
        var preferences: [String: String] = [:]
        for key in preferenceKeys {
            if let value = defaults.object(forKey: key) {
                preferences[key] = String(describing: value)
            }
        }
        let savedQueue = queue.isEmpty ? nil : MuzzaSavedQueue(
            items: queue,
            index: queueIndex,
            tokens: queueTokens,
            originalItems: originalQueue,
            originalTokens: originalTokens,
            shuffled: isShuffling,
            radioEndpoint: radioEndpoint,
            radioContinuation: radioContinuation
        )
        let snapshot = MuzzaBackupSnapshot(
            formatVersion: 1,
            queue: savedQueue,
            preferences: preferences,
            liked: liked,
            playlists: playlists,
            albums: albums,
            artists: artists,
            downloads: downloads,
            recent: recent
        )
        return try JSONEncoder().encode(snapshot)
    }

    func importBackup(_ data: Data) async -> Bool {
        do {
            let snapshot = try JSONDecoder().decode(MuzzaBackupSnapshot.self, from: data)
            guard snapshot.formatVersion == 1 else {
                throw MuzzaAPIError.requestFailed("This Muzza backup format is not supported.")
            }
            let defaults = UserDefaults.standard
            for (key, stringValue) in snapshot.preferences {
                if key.hasSuffix("Mode") || key.hasSuffix("Color") || key.hasSuffix("Language")
                    || key.hasSuffix("Country") || key.hasSuffix("Quality") || key.hasSuffix("repeatMode")
                    || key == "muzza.themeMode" {
                    defaults.set(stringValue, forKey: key)
                } else if key.hasSuffix("MB") || key == "muzza.playbackRate" {
                    if let doubleValue = Double(stringValue) {
                        defaults.set(key.hasSuffix("MB") ? Int(doubleValue) : doubleValue, forKey: key)
                    }
                } else if ["true", "false"].contains(stringValue.lowercased()) {
                    defaults.set(stringValue.lowercased() == "true", forKey: key)
                } else if let intValue = Int(stringValue) {
                    defaults.set(intValue, forKey: key)
                } else {
                    defaults.set(stringValue, forKey: key)
                }
            }
            if let savedRate = defaults.object(forKey: "muzza.playbackRate") as? Double,
               savedRate >= 0.5, savedRate <= 2.0 {
                playbackRate = savedRate
                player.rate = Float(savedRate)
            }
            if let savedMode = defaults.string(forKey: "muzza.repeatMode"),
               let mode = MuzzaRepeatMode(rawValue: savedMode) {
                repeatMode = mode
            }
            isShuffling = defaults.bool(forKey: "muzza.shuffle")
            if let queueData = snapshot.queue {
                let savedQueueData = try JSONEncoder().encode(queueData)
                defaults.set(savedQueueData, forKey: "muzza.queue")
            } else {
                defaults.removeObject(forKey: "muzza.queue")
            }
            liked = snapshot.liked
            playlists = snapshot.playlists
            albums = snapshot.albums
            artists = snapshot.artists
            downloads = snapshot.downloads
            recent = snapshot.recent
            libraryLoaded = true
            libraryChangeToken += 1
            restoreQueue()
            if hasSignedInAccount {
                for item in snapshot.liked.filter({ $0.kind == "song" }) {
                    _ = try? await MuzzaAPI.setLiked(item, liked: true)
                }
                for item in snapshot.albums + snapshot.artists + snapshot.playlists {
                    _ = try? await MuzzaAPI.setSaved(item, saved: true)
                }
            }
            await refreshLibrary()
            message = "Library backup restored."
            return true
        } catch {
            message = "Could not restore the backup: \(error.localizedDescription)"
            return false
        }
    }

    func importM3U(_ data: Data) async -> Bool {
        guard let text = String(data: data, encoding: .utf8) else {
            message = "The M3U file could not be read."
            return false
        }
        var queries: [String] = []
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, !line.hasPrefix("#") else { continue }
            if line.lowercased().hasSuffix(".m3u") || line.lowercased().hasSuffix(".m3u8") {
                continue
            }
            queries.append(line)
        }
        var titleMetadata: [String] = []
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.hasPrefix("#EXTINF:"), let comma = line.firstIndex(of: ",") {
                let title = String(line[line.index(after: comma)...]).trimmingCharacters(in: .whitespaces)
                if !title.isEmpty { titleMetadata.append(title) }
            }
        }
        if !titleMetadata.isEmpty {
            queries = titleMetadata
        }
        guard !queries.isEmpty else {
            message = "No playable entries were found in the M3U file."
            return false
        }
        var songs: [MuzzaLiveItem] = []
        for query in queries.prefix(250) {
            do {
                let response = try await MuzzaAPI.search(query: query, category: "songs", hideExplicit: false, continuation: nil)
                if let song = response.items.first(where: { $0.kind == "song" }) {
                    songs.append(song)
                }
            } catch {
                continue
            }
        }
        guard let first = songs.first else {
            message = "M3U entries could not be matched to songs."
            return false
        }
        play(first, in: songs)
        message = "Imported \(songs.count) of \(queries.count) M3U entries."
        return true
    }

    func createPlaylist(_ name: String, syncToYouTube: Bool = false) async -> Bool {
        await changeLibrary { try await MuzzaAPI.createPlaylist(name, syncToYouTube: syncToYouTube) }
    }

    func setAutoplayEnabled(_ enabled: Bool) {
        if enabled {
            guard let current else {
                message = "Start a song before enabling autoplay."
                return
            }
            startRadio(current)
        } else {
            cancelRadio()
        }
    }

    func setVolume(_ value: Double) {
        guard value.isFinite else { return }
        volume = min(max(value, 0), 1)
        player.volume = Float(volume)
    }

    func setPlaybackRate(_ value: Double) {
        guard value.isFinite else { return }
        playbackRate = min(max(value, 0.5), 2.0)
        UserDefaults.standard.set(playbackRate, forKey: "muzza.playbackRate")
        if isPlaying {
            player.rate = Float(playbackRate)
        }
    }

    func startSleepTimer(minutes: Int) {
        guard minutes > 0 else { return }
        cancelSleepTimer()
        sleepTimerActive = true
        let deadline = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepTimerTask = Task {
            while !Task.isCancelled {
                let remaining = max(deadline.timeIntervalSinceNow, 0)
                sleepTimerRemaining = remaining
                if remaining <= 0 {
                    cancelSleepTimer()
                    pause()
                    return
                }
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }
    }

    func startSleepTimerAtEndOfSong() {
        guard duration > position else {
            pause()
            return
        }
        cancelSleepTimer()
        sleepAtTrackEnd = true
        sleepTimerActive = true
        sleepTimerRemaining = duration - position
    }

    func cancelSleepTimer() {
        sleepTimerTask?.cancel()
        sleepTimerTask = nil
        sleepAtTrackEnd = false
        sleepTimerActive = false
        sleepTimerRemaining = 0
    }

    func renamePlaylist(_ playlist: MuzzaLiveItem, name: String) async -> Bool {
        await changeLibrary { try await MuzzaAPI.renamePlaylist(playlist, name: name) }
    }

    func add(_ item: MuzzaLiveItem, to playlist: MuzzaLiveItem, allowDuplicate: Bool) async throws {
        guard libraryLoaded, !isLibraryBusy else {
            throw MuzzaAPIError.requestFailed("The library is not ready.")
        }
        isLibraryBusy = true
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryBusy = false }
        let response = try await MuzzaAPI.add(item, to: playlist, allowDuplicate: allowDuplicate)
        if revision == libraryRevision {
            applyLibrary(response, includeRecent: recentAtStart == recentRevision)
        }
    }

    func deletePlaylist(_ playlist: MuzzaLiveItem) async -> Bool {
        await changeLibrary { try await MuzzaAPI.deletePlaylist(playlist) }
    }

    func remove(_ item: MuzzaLiveItem, from playlist: MuzzaLiveItem) async throws -> [MuzzaLiveItem] {
        guard !isLibraryBusy else { throw MuzzaAPIError.requestFailed("The library is busy.") }
        isLibraryBusy = true
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryBusy = false }
        let updated = try await MuzzaAPI.remove(item, from: playlist)
        let library = try await MuzzaAPI.library()
        if revision == libraryRevision {
            applyLibrary(library, includeRecent: recentAtStart == recentRevision)
        }
        return updated.items
    }

    func reorder(_ playlist: MuzzaLiveItem, items: [MuzzaLiveItem]) async throws -> [MuzzaLiveItem] {
        guard !isLibraryBusy else { throw MuzzaAPIError.requestFailed("The library is busy.") }
        isLibraryBusy = true
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryBusy = false }
        let updated = try await MuzzaAPI.reorderPlaylist(playlist, items: items)
        let library = try await MuzzaAPI.library()
        if revision == libraryRevision {
            applyLibrary(library, includeRecent: recentAtStart == recentRevision)
        }
        return updated.items
    }

    private func changeLibrary(_ action: () async throws -> MuzzaLiveResponse) async -> Bool {
        guard libraryLoaded, !isLibraryBusy else {
            message = "The library is not ready."
            return false
        }
        isLibraryBusy = true
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryBusy = false }
        do {
            let response = try await action()
            if revision == libraryRevision {
                applyLibrary(response, includeRecent: recentAtStart == recentRevision)
            }
            return true
        } catch {
            message = error.localizedDescription
            return false
        }
    }

    private func applyLibrary(_ response: MuzzaLiveResponse, includeRecent: Bool) {
        liked = response.items
        playlists = response.playlists
        albums = response.albums
        artists = response.artists
        downloads = response.downloads
        if includeRecent { recent = response.recent }
        libraryError = nil
        libraryChangeToken += 1
    }

    func play(_ item: MuzzaLiveItem, in items: [MuzzaLiveItem]? = nil, startingAt requestedIndex: Int? = nil) {
        guard item.kind == "song" else {
            message = "This item is not a playable song."
            return
        }
        cancelRadio()
        queueIdentity = UUID()
        queueRevision += 1
        let songs = (items ?? [item]).filter { $0.kind == "song" }
        let selectedIndex = requestedIndex.flatMap { index in
            songs.indices.contains(index) && songs[index].id == item.id ? index : nil
        } ?? songs.firstIndex(where: { $0.id == item.id })
        if let index = selectedIndex {
            queue = songs
            queueIndex = index
        } else {
            queue = [item]
            queueIndex = 0
        }
        queueTokens = queue.map { _ in UUID() }
        originalQueue = queue
        originalTokens = queueTokens
        if isShuffling && !shuffleUpcoming() {
            isShuffling = false
            UserDefaults.standard.set(false, forKey: "muzza.shuffle")
        }
        persistQueue()
        start(item)
    }

    func playShuffled(_ items: [MuzzaLiveItem]) {
        let songs = items.filter { $0.kind == "song" }
        guard let first = songs.shuffled().first else {
            message = "This item is not a playable song."
            return
        }
        cancelRadio()
        queueIdentity = UUID()
        queueRevision += 1

        var shuffled = songs
        shuffled.shuffle()
        let originalTokens = songs.map { _ in UUID() }
        let shuffledTokens = shuffled.map { item in
            if let index = songs.firstIndex(where: { $0.id == item.id }) {
                return originalTokens[index]
            }
            return UUID()
        }

        queue = shuffled
        queueTokens = shuffledTokens
        queueIndex = max(0, shuffled.firstIndex(where: { $0.id == first.id }) ?? 0)
        originalQueue = songs
        originalTokens = originalTokens
        isShuffling = true
        UserDefaults.standard.set(true, forKey: "muzza.shuffle")
        persistQueue()
        start(queue[queueIndex])
    }

    func playQueued(at index: Int) {
        guard queue.indices.contains(index) else {
            message = "The selected queue item is unavailable."
            return
        }
        pendingRadioAdvance = nil
        queueIndex = index
        queueRevision += 1
        persistQueue()
        start(queue[index])
    }

    func startRadio(_ item: MuzzaLiveItem) {
        guard item.kind == "song" else {
            message = "This item is not a playable song."
            return
        }
        if current?.id == item.id, queueTokens.indices.contains(queueIndex),
           player.currentItem != nil || isLoadingAudio {
            let token = queueTokens[queueIndex]
            let selected = current ?? item
            cancelRadio()
            queueIdentity = UUID()
            queueRevision += 1
            queue = [selected]
            queueTokens = [token]
            originalQueue = queue
            originalTokens = queueTokens
            queueIndex = 0
            persistQueue()
        } else {
            play(item)
        }

        guard let selected = current, queueTokens.indices.contains(queueIndex) else { return }
        let currentToken = queueTokens[queueIndex]
        let identity = queueIdentity
        let revision = queueRevision
        let endpoint = MuzzaRadioEndpoint(
            videoId: item.id, playlistId: nil, playlistSetVideoId: nil, params: nil, index: nil
        )
        let requestID = UUID()
        radioRequestID = requestID
        isLoadingRadio = true
        radioTask = Task {
            defer {
                if queueIdentity == identity && radioRequestID == requestID {
                    isLoadingRadio = false
                    radioTask = nil
                }
            }
            do {
                let response = try await MuzzaAPI.radio(
                    endpoint: endpoint, continuation: nil,
                    hideExplicit: UserDefaults.standard.bool(forKey: "muzza.hideExplicit")
                )
                guard !Task.isCancelled, queueIdentity == identity else { return }
                guard queueRevision == revision else {
                    pendingRadioAdvance = nil
                    message = "Radio was not applied because the queue changed."
                    return
                }
                guard let nextEndpoint = response.radioEndpoint,
                      response.items.contains(where: { $0.id != selected.id }) else {
                    throw MuzzaAPIError.requestFailed("YouTube did not return radio tracks.")
                }
                var items = response.items
                var index = response.currentIndex.flatMap { candidate in
                    items.indices.contains(candidate) && items[candidate].id == selected.id ? candidate : nil
                } ?? items.firstIndex(where: { $0.id == selected.id })
                if index == nil {
                    items.insert(selected, at: 0)
                    index = 0
                }
                guard let index else { throw MuzzaAPIError.requestFailed("The radio queue is invalid.") }
                var tokens = items.map { _ in UUID() }
                tokens[index] = currentToken
                queue = items
                queueTokens = tokens
                queueIndex = index
                originalQueue = items
                originalTokens = tokens
                if isShuffling && !shuffleUpcoming() {
                    isShuffling = false
                    UserDefaults.standard.set(false, forKey: "muzza.shuffle")
                }
                radioEndpoint = nextEndpoint
                radioContinuation = response.continuation
                hasRadioContinuation = response.continuation != nil
                queueRevision += 1
                persistQueue()
                if pendingRadioAdvance == currentToken {
                    pendingRadioAdvance = nil
                    isLoadingRadio = false
                    next()
                }
            } catch {
                guard !Task.isCancelled, queueIdentity == identity else { return }
                pendingRadioAdvance = nil
                message = "Could not start radio: \(error.localizedDescription)"
            }
        }
    }

    private func loadMoreRadio(advance: Bool) {
        guard let endpoint = radioEndpoint, let token = radioContinuation,
              queueTokens.indices.contains(queueIndex) else { return }
        if advance { pendingRadioAdvance = queueTokens[queueIndex] }
        guard !isLoadingRadio else { return }
        let identity = queueIdentity
        let requestID = UUID()
        radioRequestID = requestID
        isLoadingRadio = true
        radioTask = Task {
            defer {
                if queueIdentity == identity && radioRequestID == requestID {
                    isLoadingRadio = false
                    radioTask = nil
                }
            }
            do {
                let response = try await MuzzaAPI.radio(
                    endpoint: endpoint, continuation: token,
                    hideExplicit: UserDefaults.standard.bool(forKey: "muzza.hideExplicit")
                )
                guard !Task.isCancelled, queueIdentity == identity else { return }
                guard let nextEndpoint = response.radioEndpoint, response.continuation != token else {
                    throw MuzzaAPIError.requestFailed("YouTube returned an invalid radio continuation.")
                }
                var items = response.items
                if items.first?.id == queue.last?.id { items.removeFirst() }
                radioEndpoint = nextEndpoint
                radioContinuation = response.continuation
                hasRadioContinuation = response.continuation != nil
                if !items.isEmpty {
                    let tokens = items.map { _ in UUID() }
                    originalQueue.append(contentsOf: items)
                    originalTokens.append(contentsOf: tokens)
                    var additions = Array(zip(items, tokens))
                    if isShuffling { additions.shuffle() }
                    queue.append(contentsOf: additions.map(\.0))
                    queueTokens.append(contentsOf: additions.map(\.1))
                    queueRevision += 1
                }
                persistQueue()
                let currentToken = queueTokens.indices.contains(queueIndex) ? queueTokens[queueIndex] : nil
                let shouldAdvance = pendingRadioAdvance != nil && pendingRadioAdvance == currentToken
                pendingRadioAdvance = nil
                isLoadingRadio = false
                if shouldAdvance {
                    if items.isEmpty {
                        player.pause()
                        if response.continuation != nil {
                            message = "No additional radio tracks were returned. Try again."
                        }
                    } else {
                        next()
                    }
                }
            } catch {
                guard !Task.isCancelled, queueIdentity == identity else { return }
                pendingRadioAdvance = nil
                message = "Could not load more radio tracks: \(error.localizedDescription)"
            }
        }
    }

    private func cancelRadio() {
        radioTask?.cancel()
        radioRequestID = UUID()
        radioTask = nil
        isLoadingRadio = false
        radioEndpoint = nil
        radioContinuation = nil
        hasRadioContinuation = false
        pendingRadioAdvance = nil
    }

    func enqueue(_ item: MuzzaLiveItem, playNext: Bool) {
        guard item.kind == "song" else {
            message = "This item is not a playable song."
            return
        }
        let token = UUID()
        if queue.isEmpty {
            queue = [item]
            queueTokens = [token]
            originalQueue = queue
            originalTokens = queueTokens
            queueIndex = 0
            current = item
            queueRevision += 1
            persistQueue()
            updateNowPlaying()
            return
        }
        guard queueTokens.indices.contains(queueIndex), queue.count == queueTokens.count,
              originalQueue.count == originalTokens.count,
              let originalIndex = originalTokens.firstIndex(of: queueTokens[queueIndex]) else {
            message = "The queue could not be updated."
            return
        }
        let insertion = playNext ? queueIndex + 1 : queue.count
        queue.insert(item, at: insertion)
        queueTokens.insert(token, at: insertion)
        if isShuffling {
            let originalInsertion = playNext ? originalIndex + 1 : originalQueue.count
            originalQueue.insert(item, at: originalInsertion)
            originalTokens.insert(token, at: originalInsertion)
        } else {
            originalQueue = queue
            originalTokens = queueTokens
        }
        queueRevision += 1
        persistQueue()
    }

    func moveQueue(from offsets: IndexSet, to destination: Int) {
        guard !offsets.isEmpty, offsets.allSatisfy({ queue.indices.contains($0) }),
              destination >= 0, destination <= queue.count, queue.count == queueTokens.count,
              queueTokens.indices.contains(queueIndex) else {
            message = "The queue could not be reordered."
            return
        }
        let currentToken = queueTokens[queueIndex]
        var reordered = queue
        var reorderedTokens = queueTokens
        reordered.move(fromOffsets: offsets, toOffset: destination)
        reorderedTokens.move(fromOffsets: offsets, toOffset: destination)
        guard let index = reorderedTokens.firstIndex(of: currentToken) else {
            message = "The current song is missing from the queue."
            return
        }
        queue = reordered
        queueTokens = reorderedTokens
        queueIndex = index
        if !isShuffling {
            originalQueue = queue
            originalTokens = queueTokens
        }
        queueRevision += 1
        persistQueue()
    }

    func removeFromQueue(at offsets: IndexSet) {
        guard !offsets.isEmpty, offsets.allSatisfy({ queue.indices.contains($0) }),
              queueTokens.count == queue.count, queueTokens.indices.contains(queueIndex),
              originalQueue.count == originalTokens.count else {
            message = "The queue could not be updated."
            return
        }
        let wasPlaying = isPlaying || isLoadingAudio
        let removedCurrent = offsets.contains(queueIndex)
        let newIndex = MuzzaQueueRules.indexAfterRemoving(current: queueIndex, count: queue.count, indices: offsets)
        let removedTokens = Set(offsets.map { queueTokens[$0] })
        var remaining = queue
        var remainingTokens = queueTokens
        remaining.remove(atOffsets: offsets)
        remainingTokens.remove(atOffsets: offsets)
        let originals = zip(originalTokens, originalQueue).filter { !removedTokens.contains($0.0) }
        guard remaining.count == originals.count, remaining.isEmpty || newIndex != nil else {
            message = "The queue could not be updated."
            return
        }
        queue = remaining
        queueTokens = remainingTokens
        originalTokens = originals.map(\.0)
        originalQueue = originals.map(\.1)
        if let newIndex {
            queueIndex = newIndex
            if removedCurrent {
                if wasPlaying {
                    start(queue[newIndex])
                } else {
                    stopCurrentItem()
                    current = queue[newIndex]
                    updateNowPlaying()
                }
            }
        } else {
            cancelRadio()
            queueIdentity = UUID()
            stopCurrentItem()
            queueIndex = 0
            current = nil
            updateNowPlaying()
        }
        queueRevision += 1
        persistQueue()
    }

    func removePlayedFromQueue() {
        guard queueIndex > 0 else { return }
        removeFromQueue(at: IndexSet(integersIn: 0..<queueIndex))
    }

    func removeUpcomingFromQueue() {
        guard queue.indices.contains(queueIndex), queueIndex + 1 < queue.count else { return }
        removeFromQueue(at: IndexSet(integersIn: (queueIndex + 1)..<queue.count))
    }

    func clearQueue() {
        cancelRadio()
        queueIdentity = UUID()
        queueRevision += 1
        queue = []
        queueTokens = []
        originalQueue = []
        originalTokens = []
        queueIndex = 0
        stopCurrentItem()
        current = nil
        updateNowPlaying()
        persistQueue()
    }

    func toggle() {
        if isPlaying || isLoadingAudio {
            pause()
        } else {
            resume()
        }
    }

    func pause() {
        pendingRadioAdvance = nil
        if isLoadingAudio {
            stopCurrentItem()
        } else {
            player.pause()
        }
    }

    func resume() {
        guard let current else { return }
        if player.currentItem == nil {
            start(current)
        } else {
            if duration > 0, position >= duration - 0.5 { seek(to: 0) }
            player.playImmediately(atRate: Float(playbackRate))
        }
    }

    func next(finishedNaturally: Bool = false) {
        if queueTokens.indices.contains(queueIndex), queueIndex == queue.count - 1,
           !(finishedNaturally && repeatMode == .one) {
            if isLoadingRadio {
                pendingRadioAdvance = queueTokens[queueIndex]
                return
            }
            if radioContinuation != nil {
                loadMoreRadio(advance: true)
                return
            }
        }
        guard let index = MuzzaQueueRules.nextIndex(
            current: queueIndex, count: queue.count, repeatMode: repeatMode, automatic: finishedNaturally
        ) else {
            player.pause()
            return
        }
        queueIndex = index
        queueRevision += 1
        persistQueue()
        start(queue[queueIndex])
    }

    func previous() {
        pendingRadioAdvance = nil
        if position > 3 {
            seek(to: 0)
            return
        }
        guard let index = MuzzaQueueRules.previousIndex(
            current: queueIndex, count: queue.count, repeatMode: repeatMode
        ) else {
            seek(to: 0)
            return
        }
        queueIndex = index
        queueRevision += 1
        persistQueue()
        start(queue[index])
    }

    func toggleShuffle() {
        if queue.isEmpty {
            isShuffling.toggle()
        } else if isShuffling {
            guard queueTokens.indices.contains(queueIndex),
                  originalQueue.count == originalTokens.count,
                  let index = originalTokens.firstIndex(of: queueTokens[queueIndex]) else {
                message = "The queue order could not be restored."
                return
            }
            queue = originalQueue
            queueTokens = originalTokens
            queueIndex = index
            isShuffling = false
        } else {
            originalQueue = queue
            originalTokens = queueTokens
            guard shuffleUpcoming() else { return }
            isShuffling = true
        }
        UserDefaults.standard.set(isShuffling, forKey: "muzza.shuffle")
        queueRevision += 1
        persistQueue()
    }

    func cycleRepeat() {
        switch repeatMode {
        case .off: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .off
        }
        UserDefaults.standard.set(repeatMode.rawValue, forKey: "muzza.repeatMode")
    }

    private func shuffleUpcoming() -> Bool {
        guard queue.count == queueTokens.count,
              let shuffled = MuzzaQueueRules.shuffleUpcoming(Array(zip(queue, queueTokens)), currentIndex: queueIndex) else {
            message = "The queue could not be shuffled."
            return false
        }
        queue = shuffled.map(\.0)
        queueTokens = shuffled.map(\.1)
        return true
    }

    func seek(to seconds: Double) {
        guard seconds.isFinite, seconds >= 0 else { return }
        pendingRadioAdvance = nil
        let target = duration > 0 ? min(seconds, duration) : seconds
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
    }

    func queuePreferenceChanged(_ enabled: Bool) {
        if enabled {
            persistQueue()
        } else {
            UserDefaults.standard.removeObject(forKey: "muzza.queue")
        }
    }

    func download(_ item: MuzzaLiveItem) {
        guard item.kind == "song", libraryLoaded, !downloading.contains(item.id), !isDownloaded(item) else {
            message = "This song cannot be downloaded right now."
            return
        }
        downloading.insert(item.id)
        downloadTasks[item.id] = Task {
            var filename: String?
            defer {
                downloading.remove(item.id)
                downloadTasks.removeValue(forKey: item.id)
            }
            do {
                let stream = try await MuzzaAPI.audio(for: item)
                guard let rawURL = stream.audioUrl, let url = URL(string: rawURL) else {
                    throw MuzzaAPIError.invalidAudioURL
                }
                let saved = try await MuzzaDownloadStore.download(
                    url: url, headers: stream.headers, expectedLength: stream.contentLength
                )
                filename = saved
                try Task.checkCancellation()
                guard !isLibraryBusy else { throw MuzzaAPIError.requestFailed("The library is busy. Try again.") }
                libraryRevision += 1
                let revision = libraryRevision
                let recentAtStart = recentRevision
                let response: MuzzaLiveResponse
                do {
                    isLibraryBusy = true
                    defer { isLibraryBusy = false }
                    response = try await MuzzaAPI.markDownloaded(item, filename: saved)
                }
                if revision == libraryRevision {
                    applyLibrary(response, includeRecent: recentAtStart == recentRevision)
                }
                filename = nil
            } catch {
                if let filename {
                    do {
                        try MuzzaDownloadStore.removeFileIfPresent(filename)
                    } catch let cleanupError {
                        message = "Could not remove an unfinished download: \(cleanupError.localizedDescription)"
                        return
                    }
                }
                if !Task.isCancelled && !(error is CancellationError) { message = error.localizedDescription }
            }
        }
    }

    func removeDownload(_ item: MuzzaLiveItem) async -> Bool {
        guard libraryLoaded, !isLibraryBusy,
              let saved = downloads.first(where: { $0.id == item.id }),
              let filename = saved.downloadPath else {
            message = "The downloaded song is unavailable."
            return false
        }
        isLibraryBusy = true
        libraryRevision += 1
        let revision = libraryRevision
        let recentAtStart = recentRevision
        defer { isLibraryBusy = false }
        do {
            try MuzzaDownloadStore.removeFileIfPresent(filename)
            let response = try await MuzzaAPI.clearDownload(id: item.id)
            if revision == libraryRevision {
                applyLibrary(response, includeRecent: recentAtStart == recentRevision)
            }
            return true
        } catch {
            message = error.localizedDescription
            return false
        }
    }

    func clearDownloads() async -> Bool {
        let items = downloads
        guard !items.isEmpty else { return true }
        var succeeded = true
        for item in items {
            if await removeDownload(item) == false {
                succeeded = false
            }
        }
        return succeeded
    }

    func stopForSignOut() {
        for task in downloadTasks.values { task.cancel() }
        cancelRadio()
        stopCurrentItem()
        hasSignedInAccount = false
        likeOverrides.removeAll()
        persistQueue()
        updateNowPlaying()
    }

    private func stopCurrentItem() {
        playbackTask?.cancel()
        playbackID = UUID()
        player.pause()
        player.replaceCurrentItem(with: nil)
        resourceLoader?.invalidate()
        resourceLoader = nil
        itemObservation = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
        endObserver = nil
        failureObserver = nil
        isLoadingAudio = false
        isPlaying = false
        position = 0
        duration = 0
    }

    func accountDidChange(isSignedIn: Bool) {
        hasSignedInAccount = isSignedIn
        likeOverrides.removeAll()
    }

    private func start(_ item: MuzzaLiveItem) {
        stopCurrentItem()
        let id = playbackID
        current = item
        position = 0
        duration = 0
        isLoadingAudio = true
        updateNowPlaying()

        playbackTask = Task {
            do {
                let download = try await MuzzaAPI.downloadInfo(id: item.id)
                guard !Task.isCancelled, id == playbackID else { return }
                let asset: AVURLAsset
                if let stored = download.items.first {
                    guard let filename = stored.downloadPath else { throw MuzzaDownloadError.missingFile }
                    asset = AVURLAsset(url: try MuzzaDownloadStore.existingFileURL(for: filename))
                } else {
                    let response = try await MuzzaAPI.audio(for: item)
                    guard !Task.isCancelled, id == playbackID else { return }
                    guard let rawURL = response.audioUrl, let url = URL(string: rawURL),
                          url.scheme?.lowercased() == "https" else {
                        throw MuzzaAPIError.invalidAudioURL
                    }
                    let userAgent = response.headers.first { $0.key.lowercased() == "user-agent" }?.value
                    let needsHeaders = response.headers.keys.contains { $0.lowercased() != "user-agent" }
                    if needsHeaders {
                        let loader = MuzzaResourceLoader(url: url, headers: response.headers)
                        asset = try loader.makeAsset()
                        resourceLoader = loader
                    } else if let userAgent {
                        asset = AVURLAsset(url: url, options: [AVURLAssetHTTPUserAgentKey: userAgent])
                    } else {
                        asset = AVURLAsset(url: url)
                    }
                }
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                let playerItem = AVPlayerItem(asset: asset)
                observe(playerItem, playbackID: id)
                player.replaceCurrentItem(with: playerItem)
                player.playImmediately(atRate: Float(playbackRate))
            } catch {
                guard !Task.isCancelled, id == playbackID else { return }
                isLoadingAudio = false
                resourceLoader?.invalidate()
                resourceLoader = nil
                if UserDefaults.standard.bool(forKey: "muzza.autoSkipError") {
                    message = nil
                    next()
                } else {
                    message = error.localizedDescription
                }
            }
        }
    }

    private func observe(_ item: AVPlayerItem, playbackID id: UUID) {
        itemObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self, self.playbackID == id, self.player.currentItem === item else { return }
                switch item.status {
                case .readyToPlay:
                    self.isLoadingAudio = false
                case .failed:
                    self.isLoadingAudio = false
                    self.player.pause()
                    self.player.replaceCurrentItem(with: nil)
                    self.resourceLoader?.invalidate()
                    self.resourceLoader = nil
                    if UserDefaults.standard.bool(forKey: "muzza.autoSkipError") {
                        self.message = nil
                        self.next()
                    } else {
                        self.message = item.error?.localizedDescription ?? "The audio could not be played."
                    }
                default:
                    break
                }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.playbackID == id, self.player.currentItem === item else { return }
                if self.sleepAtTrackEnd {
                    self.cancelSleepTimer()
                    self.pause()
                    return
                }
                self.next(finishedNaturally: true)
            }
        }
        failureObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main
        ) { [weak self] notification in
            let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
            Task { @MainActor in
                guard let self, self.playbackID == id, self.player.currentItem === item else { return }
                self.isLoadingAudio = false
                self.player.pause()
                self.player.replaceCurrentItem(with: nil)
                self.resourceLoader?.invalidate()
                self.resourceLoader = nil
                if UserDefaults.standard.bool(forKey: "muzza.autoSkipError") {
                    self.message = nil
                    self.next()
                } else {
                    self.message = error?.localizedDescription ?? "Playback stopped unexpectedly."
                }
            }
        }
    }

    private func observePlayer() {
        playerObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor in
                guard let self else { return }
                self.isPlaying = player.timeControlStatus == .playing
                if self.isPlaying, self.recordedPlaybackID != self.playbackID, let item = self.current {
                    self.recordedPlaybackID = self.playbackID
                    self.record(item)
                }
                self.updateNowPlaying()
            }
        }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] time in
            Task { @MainActor in
                guard let self else { return }
                let seconds = time.seconds
                self.position = seconds.isFinite ? max(0, seconds) : 0
                if let length = self.player.currentItem?.duration.seconds, length.isFinite, length > 0 {
                    self.duration = length
                }
                self.updateNowPlaying()
            }
        }
    }

    private func record(_ item: MuzzaLiveItem) {
        guard !UserDefaults.standard.bool(forKey: "muzza.pauseListenHistory") else { return }
        recentRevision += 1
        let revision = recentRevision
        Task {
            do {
                let response = try await MuzzaAPI.recordPlay(item)
                if revision == recentRevision { recent = response.recent }
            } catch {
                libraryError = error.localizedDescription
                message = "Could not save listening history: \(error.localizedDescription)"
            }
        }
    }

    private func configureAudioRouteObserver() {
        audioRouteObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard UserDefaults.standard.bool(forKey: "muzza.autoplayBluetooth"),
                  let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  AVAudioSession.RouteChangeReason(rawValue: rawReason) == .newDeviceAvailable,
                  AVAudioSession.sharedInstance().currentRoute.outputs.contains(where: {
                      [.bluetoothA2DP, .bluetoothHFP, .bluetoothLE].contains($0.portType)
                  }) else { return }
            Task { @MainActor in
                guard let self, self.current != nil, !self.isPlaying else { return }
                self.resume()
            }
        }
    }

    private func configureRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        remoteTargets.append((commands.playCommand, commands.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }))
        remoteTargets.append((commands.pauseCommand, commands.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }))
        remoteTargets.append((commands.nextTrackCommand, commands.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }
            return .success
        }))
        remoteTargets.append((commands.previousTrackCommand, commands.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }
            return .success
        }))
        remoteTargets.append((commands.changePlaybackPositionCommand, commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(to: event.positionTime) }
            return .success
        }))
    }

    private func updateNowPlaying() {
        guard let current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: current.title,
            MPMediaItemPropertyArtist: current.subtitle,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private var queuePersistenceEnabled: Bool {
        UserDefaults.standard.object(forKey: "muzza.persistentQueue") as? Bool ?? true
    }

    private func persistQueue() {
        guard queuePersistenceEnabled else { return }
        guard !queue.isEmpty else {
            UserDefaults.standard.removeObject(forKey: "muzza.queue")
            return
        }
        guard queue.indices.contains(queueIndex), queue.count == queueTokens.count,
              queue.count == originalQueue.count, originalQueue.count == originalTokens.count,
              Set(queueTokens) == Set(originalTokens) else {
            message = "The queue could not be saved."
            return
        }
        do {
            let data = try JSONEncoder().encode(MuzzaSavedQueue(
                items: queue,
                index: queueIndex,
                tokens: queueTokens,
                originalItems: originalQueue,
                originalTokens: originalTokens,
                shuffled: isShuffling,
                radioEndpoint: radioEndpoint,
                radioContinuation: radioContinuation
            ))
            UserDefaults.standard.set(data, forKey: "muzza.queue")
        } catch {
            message = "Could not save the queue: \(error.localizedDescription)"
        }
    }

    private func restoreQueue() {
        guard queuePersistenceEnabled, let data = UserDefaults.standard.data(forKey: "muzza.queue") else { return }
        do {
            let saved = try JSONDecoder().decode(MuzzaSavedQueue.self, from: data)
            guard saved.items.indices.contains(saved.index), saved.items.allSatisfy({ $0.kind == "song" }) else {
                throw MuzzaAPIError.requestFailed("The saved queue is invalid.")
            }
            queue = saved.items
            queueIndex = saved.index
            if let tokens = saved.tokens, let originals = saved.originalItems,
               let originalIDs = saved.originalTokens, let shuffled = saved.shuffled {
                guard tokens.count == saved.items.count, originalIDs.count == originals.count,
                      Set(tokens).count == tokens.count, Set(originalIDs).count == originalIDs.count,
                      Set(tokens) == Set(originalIDs) else {
                    throw MuzzaAPIError.requestFailed("The saved queue order is invalid.")
                }
                let originalByID = Dictionary(uniqueKeysWithValues: zip(originalIDs, originals))
                guard zip(tokens, saved.items).allSatisfy({ originalByID[$0.0] == $0.1 }) else {
                    throw MuzzaAPIError.requestFailed("The saved queue order is invalid.")
                }
                queueTokens = tokens
                originalQueue = originals
                originalTokens = originalIDs
                isShuffling = shuffled
                UserDefaults.standard.set(shuffled, forKey: "muzza.shuffle")
            } else if saved.tokens == nil && saved.originalItems == nil &&
                        saved.originalTokens == nil && saved.shuffled == nil {
                queueTokens = queue.map { _ in UUID() }
                originalQueue = queue
                originalTokens = queueTokens
                if isShuffling && !shuffleUpcoming() {
                    throw MuzzaAPIError.requestFailed("The saved queue could not be shuffled.")
                }
            } else {
                throw MuzzaAPIError.requestFailed("The saved queue order is incomplete.")
            }
            guard saved.radioContinuation == nil || saved.radioEndpoint != nil else {
                throw MuzzaAPIError.requestFailed("The saved radio queue is incomplete.")
            }
            radioEndpoint = saved.radioEndpoint
            radioContinuation = saved.radioContinuation
            hasRadioContinuation = saved.radioContinuation != nil
            current = queue[queueIndex]
        } catch {
            queue = []
            queueIndex = 0
            queueTokens = []
            originalQueue = []
            originalTokens = []
            current = nil
            cancelRadio()
            message = "Could not restore the queue: \(error.localizedDescription)"
        }
    }
}
