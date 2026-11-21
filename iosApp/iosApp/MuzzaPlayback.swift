import AVFoundation
import Foundation
import MediaPlayer
import SwiftUI

private struct MuzzaSavedQueue: Codable {
    let items: [MuzzaLiveItem]
    let index: Int
}

@MainActor
final class MuzzaPlayback: ObservableObject {
    @Published private(set) var current: MuzzaLiveItem?
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoadingAudio = false
    @Published private(set) var position = 0.0
    @Published private(set) var duration = 0.0
    @Published private(set) var queue: [MuzzaLiveItem] = []
    @Published private(set) var queueIndex = 0
    @Published private(set) var liked: [MuzzaLiveItem] = []
    @Published private(set) var playlists: [MuzzaLiveItem] = []
    @Published private(set) var albums: [MuzzaLiveItem] = []
    @Published private(set) var artists: [MuzzaLiveItem] = []
    @Published private(set) var recent: [MuzzaLiveItem] = []
    @Published private(set) var isLibraryLoading = false
    @Published private(set) var isLibraryBusy = false
    @Published private(set) var libraryLoaded = false
    @Published var libraryError: String?
    @Published var message: String?

    private let player = AVPlayer()
    private var playbackTask: Task<Void, Never>?
    private var playbackID = UUID()
    private var recordedPlaybackID: UUID?
    private var libraryRevision = 0
    private var recentRevision = 0
    private var resourceLoader: MuzzaResourceLoader?
    private var itemObservation: NSKeyValueObservation?
    private var playerObservation: NSKeyValueObservation?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []

    init() {
        restoreQueue()
        observePlayer()
        configureRemoteCommands()
        Task { await refreshLibrary() }
    }

    deinit {
        playbackTask?.cancel()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
        for (command, target) in remoteTargets { command.removeTarget(target) }
        resourceLoader?.invalidate()
    }

    func isLiked(_ item: MuzzaLiveItem) -> Bool {
        liked.contains { $0.id == item.id }
    }

    func isSaved(_ item: MuzzaLiveItem) -> Bool {
        switch item.kind {
        case "album": return albums.contains { $0.id == item.id }
        case "artist": return artists.contains { $0.id == item.id }
        case "playlist": return playlists.contains { $0.kind == "playlist" && $0.id == item.id }
        default: return false
        }
    }

    func refreshLibrary() async {
        guard !isLibraryLoading else { return }
        isLibraryLoading = true
        libraryError = nil
        libraryRevision += 1
        let revision = libraryRevision
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
            if revision == libraryRevision { applyLibrary(response) }
            libraryLoaded = true
        } catch {
            libraryError = error.localizedDescription
        }
    }

    func toggleLike(_ item: MuzzaLiveItem) {
        guard libraryLoaded, !isLibraryBusy else { return }
        let next = !isLiked(item)
        Task {
            _ = await changeLibrary { try await MuzzaAPI.setLiked(item, liked: next) }
        }
    }

    func toggleSaved(_ item: MuzzaLiveItem) {
        guard libraryLoaded, !isLibraryBusy else { return }
        let next = !isSaved(item)
        Task {
            _ = await changeLibrary { try await MuzzaAPI.setSaved(item, saved: next) }
        }
    }

    func createPlaylist(_ name: String) async -> Bool {
        await changeLibrary { try await MuzzaAPI.createPlaylist(name) }
    }

    func add(_ item: MuzzaLiveItem, to playlist: MuzzaLiveItem) {
        Task {
            _ = await changeLibrary { try await MuzzaAPI.add(item, to: playlist) }
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
        defer { isLibraryBusy = false }
        let updated = try await MuzzaAPI.remove(item, from: playlist)
        let library = try await MuzzaAPI.library()
        if revision == libraryRevision { applyLibrary(library) }
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
        defer { isLibraryBusy = false }
        do {
            let response = try await action()
            if revision == libraryRevision { applyLibrary(response) }
            return true
        } catch {
            message = error.localizedDescription
            return false
        }
    }

    private func applyLibrary(_ response: MuzzaLiveResponse) {
        liked = response.items
        playlists = response.playlists
        albums = response.albums
        artists = response.artists
        recent = response.recent
        libraryError = nil
    }

    func play(_ item: MuzzaLiveItem, in items: [MuzzaLiveItem]? = nil, startingAt requestedIndex: Int? = nil) {
        guard item.kind == "song" else {
            message = "This item is not a playable song."
            return
        }
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
        persistQueue()
        start(item)
    }

    func toggle() {
        if isPlaying || isLoadingAudio {
            pause()
        } else {
            resume()
        }
    }

    func pause() {
        if isLoadingAudio {
            playbackTask?.cancel()
            playbackID = UUID()
            isLoadingAudio = false
            player.replaceCurrentItem(with: nil)
            resourceLoader?.invalidate()
            resourceLoader = nil
        }
        player.pause()
    }

    func resume() {
        guard let current else { return }
        if player.currentItem == nil {
            start(current)
        } else {
            if duration > 0, position >= duration - 0.5 { seek(to: 0) }
            player.play()
        }
    }

    func next() {
        guard queueIndex + 1 < queue.count else {
            player.pause()
            return
        }
        queueIndex += 1
        persistQueue()
        start(queue[queueIndex])
    }

    func previous() {
        if position > 3 || queueIndex == 0 {
            seek(to: 0)
        } else {
            queueIndex -= 1
            persistQueue()
            start(queue[queueIndex])
        }
    }

    func seek(to seconds: Double) {
        guard seconds.isFinite, seconds >= 0 else { return }
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

    private func start(_ item: MuzzaLiveItem) {
        playbackTask?.cancel()
        playbackID = UUID()
        let id = playbackID
        player.pause()
        player.replaceCurrentItem(with: nil)
        resourceLoader?.invalidate()
        resourceLoader = nil
        itemObservation = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let failureObserver { NotificationCenter.default.removeObserver(failureObserver) }
        endObserver = nil
        failureObserver = nil
        current = item
        position = 0
        duration = 0
        isLoadingAudio = true
        updateNowPlaying()

        playbackTask = Task {
            do {
                let response = try await MuzzaAPI.audio(for: item)
                guard !Task.isCancelled, id == playbackID else { return }
                guard let rawURL = response.audioUrl, let url = URL(string: rawURL),
                      url.scheme?.lowercased() == "https" else {
                    throw MuzzaAPIError.invalidAudioURL
                }
                let userAgent = response.headers.first { $0.key.lowercased() == "user-agent" }?.value
                let needsHeaders = response.headers.keys.contains { $0.lowercased() != "user-agent" }
                let asset: AVURLAsset
                if needsHeaders {
                    let loader = MuzzaResourceLoader(url: url, headers: response.headers)
                    asset = try loader.makeAsset()
                    resourceLoader = loader
                } else if let userAgent {
                    asset = AVURLAsset(url: url, options: [AVURLAssetHTTPUserAgentKey: userAgent])
                } else {
                    asset = AVURLAsset(url: url)
                }
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                let playerItem = AVPlayerItem(asset: asset)
                observe(playerItem, playbackID: id)
                player.replaceCurrentItem(with: playerItem)
                player.play()
            } catch {
                guard !Task.isCancelled, id == playbackID else { return }
                isLoadingAudio = false
                resourceLoader?.invalidate()
                resourceLoader = nil
                message = error.localizedDescription
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
                    self.message = item.error?.localizedDescription ?? "The audio could not be played."
                default:
                    break
                }
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.next() }
        }
        failureObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main
        ) { [weak self] notification in
            let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
            Task { @MainActor in
                self?.message = error?.localizedDescription ?? "Playback stopped unexpectedly."
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
        recentRevision += 1
        let revision = recentRevision
        Task {
            do {
                let response = try await MuzzaAPI.recordPlay(item)
                if revision == recentRevision { recent = response.recent }
            } catch {
                libraryError = error.localizedDescription
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
        do {
            let data = try JSONEncoder().encode(MuzzaSavedQueue(items: queue, index: queueIndex))
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
            current = queue[queueIndex]
        } catch {
            message = "Could not restore the queue: \(error.localizedDescription)"
        }
    }
}
