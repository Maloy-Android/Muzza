import AVFoundation
import Foundation

enum MuzzaDownloadError: LocalizedError {
    case invalidURL
    case invalidFilename
    case unavailableDirectory
    case invalidResponse
    case incompleteFile
    case missingFile
    case cleanupFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "The download URL is invalid."
        case .invalidFilename: return "The downloaded audio has an invalid filename."
        case .unavailableDirectory: return "The download directory is unavailable."
        case .invalidResponse: return "The audio server did not return a complete download."
        case .incompleteFile: return "The audio download is incomplete."
        case .missingFile: return "The downloaded audio file is missing."
        case .cleanupFailed(let message): return "Could not remove an invalid download: \(message)"
        }
    }
}

private final class MuzzaDownloadDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let destination = request.url, MuzzaResourceLoader.isAllowed(destination) else {
            completionHandler(nil)
            return
        }
        var redirected = request
        if response.url?.host?.lowercased() != destination.host?.lowercased() {
            redirected.setValue(nil, forHTTPHeaderField: "Cookie")
            redirected.setValue(nil, forHTTPHeaderField: "Authorization")
        }
        completionHandler(redirected)
    }
}

enum MuzzaDownloadStore {
    static func isSafeFilename(_ filename: String) -> Bool {
        guard filename.hasSuffix(".m4a"), filename == URL(fileURLWithPath: filename).lastPathComponent else {
            return false
        }
        let stem = String(filename.dropLast(4))
        guard let id = UUID(uuidString: stem) else { return false }
        return id.uuidString.lowercased() == stem
    }

    static func fileURL(for filename: String) throws -> URL {
        guard isSafeFilename(filename) else { throw MuzzaDownloadError.invalidFilename }
        guard let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            throw MuzzaDownloadError.unavailableDirectory
        }
        return base.appendingPathComponent("Muzza", isDirectory: true)
            .appendingPathComponent("Downloads", isDirectory: true)
            .appendingPathComponent(filename, isDirectory: false)
    }

    static func download(url: URL, headers: [String: String], expectedLength: Int64?) async throws -> String {
        guard MuzzaResourceLoader.isAllowed(url) else { throw MuzzaDownloadError.invalidURL }
        guard !headers.keys.contains(where: { $0.lowercased() == "range" }) else {
            throw MuzzaDownloadError.invalidResponse
        }
        let hasRange = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.contains(where: { $0.name.lowercased() == "range" }) == true
        if hasRange {
            guard let expectedLength, expectedLength > 0 else { throw MuzzaDownloadError.invalidResponse }
        }
        let delegate = MuzzaDownloadDelegate()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        var request = URLRequest(url: url)
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let (temporary, response) = try await session.download(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              let finalURL = http.url, MuzzaResourceLoader.isAllowed(finalURL) else {
            throw MuzzaDownloadError.invalidResponse
        }
        let attributes = try FileManager.default.attributesOfItem(atPath: temporary.path)
        guard let size = (attributes[.size] as? NSNumber)?.int64Value, size > 0 else {
            throw MuzzaDownloadError.incompleteFile
        }
        if response.expectedContentLength > 0 && size != response.expectedContentLength {
            throw MuzzaDownloadError.incompleteFile
        }
        if let expectedLength, expectedLength > 0, size != expectedLength {
            throw MuzzaDownloadError.incompleteFile
        }
        let filename = UUID().uuidString.lowercased() + ".m4a"
        let destination = try fileURL(for: filename)
        try FileManager.default.createDirectory(
            at: destination.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        try FileManager.default.moveItem(at: temporary, to: destination)
        do {
            let asset = AVURLAsset(url: destination)
            let playable = try await asset.load(.isPlayable)
            let audioTracks = try await asset.loadTracks(withMediaType: .audio)
            guard playable && !audioTracks.isEmpty else { throw MuzzaDownloadError.invalidResponse }
        } catch {
            do {
                try FileManager.default.removeItem(at: destination)
            } catch let cleanupError {
                throw MuzzaDownloadError.cleanupFailed(cleanupError.localizedDescription)
            }
            throw error
        }
        return filename
    }

    static func existingFileURL(for filename: String) throws -> URL {
        let url = try fileURL(for: filename)
        guard FileManager.default.fileExists(atPath: url.path) else { throw MuzzaDownloadError.missingFile }
        return url
    }

    static func removeFileIfPresent(_ filename: String) throws {
        let url = try fileURL(for: filename)
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }
}
