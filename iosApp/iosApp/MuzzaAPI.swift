import Foundation
import Shared

struct MuzzaLiveItem: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let thumbnail: String
    let kind: String
    let params: String?
    let explicit: Bool?
    let duration: Int?
    let entryId: Int64?
    let downloadPath: String?
    let remoteLiked: Bool?
    let artists: [MuzzaArtistMetadata]? = nil
    let album: MuzzaAlbumMetadata? = nil
    let playlistId: String? = nil
    let playlistTitle: String? = nil
    let isEditable: Bool? = nil
}

struct MuzzaArtistMetadata: Codable, Hashable {
    let id: String?
    let name: String
    let thumbnail: String?
    let isProfile: Bool?
}

struct MuzzaAlbumMetadata: Codable, Hashable {
    let id: String
    let title: String
}

struct MuzzaLiveSection: Codable {
    let title: String
    let items: [MuzzaLiveItem]
    let moreItem: MuzzaLiveItem?
}

struct MuzzaHomeChip: Codable, Identifiable {
    let title: String
    let params: String
    var id: String { params }
}

struct MuzzaAccountProfile: Decodable {
    let name: String
    let email: String?
    let channelHandle: String?
    let thumbnailUrl: String?
}

struct MuzzaRadioEndpoint: Codable {
    let videoId: String?
    let playlistId: String?
    let playlistSetVideoId: String?
    let params: String?
    let index: Int?
}

struct MuzzaLiveResponse: Decodable {
    let sections: [MuzzaLiveSection]
    let items: [MuzzaLiveItem]
    let playlists: [MuzzaLiveItem]
    let albums: [MuzzaLiveItem]
    let artists: [MuzzaLiveItem]
    let downloads: [MuzzaLiveItem]
    let recent: [MuzzaLiveItem]
    let chips: [MuzzaHomeChip]
    let continuation: String?
    let audioUrl: String?
    let headers: [String: String]
    let contentLength: Int64?
    let lyrics: String?
    let account: MuzzaAccountProfile?
    let radioEndpoint: MuzzaRadioEndpoint?
    let currentIndex: Int?
    let errorCode: String?
    let error: String?
}

enum MuzzaAPIError: LocalizedError {
    case invalidResponse(Error)
    case requestFailed(String)
    case invalidAudioURL
    case duplicateSong

    var errorDescription: String? {
        switch self {
        case .invalidResponse(let error):
            return "Could not read the response: \(error.localizedDescription)"
        case .requestFailed(let message):
            return message
        case .invalidAudioURL:
            return "YouTube did not return a valid audio URL."
        case .duplicateSong:
            return "This song is already in the playlist."
        }
    }
}

@MainActor
enum MuzzaAPI {
    private static func request(
        _ start: (@escaping (String) -> Void) -> Void
    ) async throws -> MuzzaLiveResponse {
        let raw = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            start { continuation.resume(returning: $0) }
        }
        try Task.checkCancellation()
        let response: MuzzaLiveResponse
        do {
            response = try JSONDecoder().decode(MuzzaLiveResponse.self, from: Data(raw.utf8))
        } catch {
            throw MuzzaAPIError.invalidResponse(error)
        }
        if response.errorCode == "duplicate_song" {
            throw MuzzaAPIError.duplicateSong
        }
        if let message = response.error, !message.isEmpty {
            throw MuzzaAPIError.requestFailed(message)
        }
        return response
    }

    private static func encode(_ item: MuzzaLiveItem) throws -> String {
        let data = try JSONEncoder().encode(item)
        guard let json = String(data: data, encoding: .utf8) else {
            throw MuzzaAPIError.requestFailed("Could not encode the song.")
        }
        return json
    }

    static func home(hideExplicit: Bool, params: String?, continuation: String?) async throws -> MuzzaLiveResponse {
        try await request {
            IosBridgeKt.fetchIosHome(
                hideExplicit: hideExplicit, params: params, continuation: continuation, completion: $0
            )
        }
    }

    static func explore(hideExplicit: Bool) async throws -> MuzzaLiveResponse {
        try await request { IosBridgeKt.fetchIosExplore(hideExplicit: hideExplicit, completion: $0) }
    }

    static func search(
        query: String, category: String, hideExplicit: Bool, continuation: String?
    ) async throws -> MuzzaLiveResponse {
        try await request {
            IosBridgeKt.searchIosMusic(
                query: query, category: category, hideExplicit: hideExplicit,
                continuation: continuation, completion: $0
            )
        }
    }

    static func collection(
        _ item: MuzzaLiveItem, hideExplicit: Bool, continuation: String? = nil
    ) async throws -> MuzzaLiveResponse {
        if item.kind == "likedPlaylist" {
            return try await request { IosLibraryKt.fetchIosLikedPlaylist(completion: $0) }
        }
        if item.kind == "localPlaylist" {
            return try await request { IosLibraryKt.fetchIosLocalPlaylist(id: item.id, completion: $0) }
        }
        return try await request {
            IosBridgeKt.fetchIosCollection(
                kind: item.kind, id: item.id, params: item.params, hideExplicit: hideExplicit,
                continuation: continuation, completion: $0
            )
        }
    }

    static func audio(for item: MuzzaLiveItem) async throws -> MuzzaLiveResponse {
        try await request {
            IosPlaybackResolverKt.fetchIosPlayerUrl(
                videoId: item.id, isExplicit: item.explicit ?? false, completion: $0
            )
        }
    }

    static func radio(
        endpoint: MuzzaRadioEndpoint, continuation: String?, hideExplicit: Bool
    ) async throws -> MuzzaLiveResponse {
        let data = try JSONEncoder().encode(endpoint)
        guard let json = String(data: data, encoding: .utf8) else {
            throw MuzzaAPIError.requestFailed("Could not encode the radio endpoint.")
        }
        return try await request {
            IosBridgeKt.fetchIosRadio(
                endpointJson: json, continuation: continuation, hideExplicit: hideExplicit, completion: $0
            )
        }
    }

    static func lyrics(for item: MuzzaLiveItem, duration: Double) async throws -> String {
        let json = try encode(item)
        let seconds = duration.isFinite && duration > 0 ? Int32(min(duration, Double(Int32.max))) : 0
        let response = try await request {
            IosLyricsKt.fetchIosLyrics(json: json, durationSeconds: seconds, completion: $0)
        }
        guard let lyrics = response.lyrics, !lyrics.isEmpty else {
            throw MuzzaAPIError.requestFailed("No synchronized lyrics were found.")
        }
        return lyrics
    }

    static func library() async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.fetchIosLibrary(completion: $0) }
    }

    static func syncRemoteLikes() async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.syncIosLikedPlaylist(completion: $0) }
    }

    static func youtubeLibrary(
        category: String, hideExplicit: Bool, continuation: String?
    ) async throws -> MuzzaLiveResponse {
        try await request {
            IosBridgeKt.fetchIosYouTubeLibrary(
                category: category, hideExplicit: hideExplicit, continuation: continuation, completion: $0
            )
        }
    }

    static func validateAccount(cookie: String, visitorData: String?) async throws -> MuzzaAccountProfile {
        let response = try await request {
            IosAccountKt.validateIosAccount(cookie: cookie, visitorData: visitorData, completion: $0)
        }
        guard let account = response.account else {
            throw MuzzaAPIError.requestFailed("YouTube did not confirm the account.")
        }
        return account
    }

    static func activateAccount(cookie: String, visitorData: String?) async throws {
        _ = try await request {
            IosAccountKt.activateIosAccount(cookie: cookie, visitorData: visitorData, completion: $0)
        }
    }

    static func clearAccount() async throws {
        _ = try await request { IosAccountKt.clearIosAccount(completion: $0) }
    }

    static func importLikes(_ json: String) async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.importIosLikes(json: json, completion: $0) }
    }

    static func setLiked(_ item: MuzzaLiveItem, liked: Bool) async throws -> MuzzaLiveResponse {
        let json = try encode(item)
        let sync = UserDefaults.standard.object(forKey: "muzza.syncLikes") as? Bool ?? true
        return try await request {
            IosLibraryKt.setIosSongLiked(json: json, liked: liked, syncRemote: sync, completion: $0)
        }
    }

    static func setSaved(_ item: MuzzaLiveItem, saved: Bool) async throws -> MuzzaLiveResponse {
        let json = try encode(item)
        return try await request { IosLibraryKt.setIosCollectionSaved(json: json, saved: saved, completion: $0) }
    }

    static func recordPlay(_ item: MuzzaLiveItem) async throws -> MuzzaLiveResponse {
        let json = try encode(item)
        return try await request { IosLibraryKt.recordIosPlay(json: json, completion: $0) }
    }

    static func clearHistory() async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.clearIosHistory(completion: $0) }
    }

    static func downloadInfo(id: String) async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.fetchIosDownload(id: id, completion: $0) }
    }

    static func markDownloaded(_ item: MuzzaLiveItem, filename: String) async throws -> MuzzaLiveResponse {
        let json = try encode(item)
        return try await request { IosLibraryKt.markIosDownload(json: json, filename: filename, completion: $0) }
    }

    static func clearDownload(id: String) async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.clearIosDownload(id: id, completion: $0) }
    }

    static func createPlaylist(_ name: String, syncToYouTube: Bool) async throws -> MuzzaLiveResponse {
        try await request {
            IosLibraryKt.createIosPlaylist(name: name, syncToYouTube: syncToYouTube, completion: $0)
        }
    }

    static func renamePlaylist(_ playlist: MuzzaLiveItem, name: String) async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.renameIosPlaylist(id: playlist.id, name: name, completion: $0) }
    }

    static func reorderPlaylist(_ playlist: MuzzaLiveItem, items: [MuzzaLiveItem]) async throws -> MuzzaLiveResponse {
        let ids = try items.map { item -> Int64 in
            guard let id = item.entryId else {
                throw MuzzaAPIError.requestFailed("The playlist entry is missing its identifier.")
            }
            return id
        }
        let data = try JSONEncoder().encode(ids)
        guard let json = String(data: data, encoding: .utf8) else {
            throw MuzzaAPIError.requestFailed("Could not encode the playlist order.")
        }
        return try await request {
            IosLibraryKt.reorderIosPlaylist(id: playlist.id, entriesJson: json, completion: $0)
        }
    }

    static func add(
        _ item: MuzzaLiveItem, to playlist: MuzzaLiveItem, allowDuplicate: Bool
    ) async throws -> MuzzaLiveResponse {
        let json = try encode(item)
        return try await request {
            IosLibraryKt.addIosSongToPlaylist(
                id: playlist.id, json: json, allowDuplicate: allowDuplicate, completion: $0
            )
        }
    }

    static func remove(_ item: MuzzaLiveItem, from playlist: MuzzaLiveItem) async throws -> MuzzaLiveResponse {
        guard let entryId = item.entryId else {
            throw MuzzaAPIError.requestFailed("The playlist entry is missing its identifier.")
        }
        return try await request {
            IosLibraryKt.removeIosSongFromPlaylist(id: playlist.id, entryId: entryId, completion: $0)
        }
    }

    static func deletePlaylist(_ playlist: MuzzaLiveItem) async throws -> MuzzaLiveResponse {
        try await request { IosLibraryKt.deleteIosPlaylist(id: playlist.id, completion: $0) }
    }
}
