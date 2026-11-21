import AVFoundation
import Foundation
import UniformTypeIdentifiers

private enum MuzzaMediaError: LocalizedError {
    case invalidURL
    case invalidResponse
    case unsupportedRange
    case incompleteResponse

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "The audio URL is invalid."
        case .invalidResponse: return "The audio server returned an invalid response."
        case .unsupportedRange: return "The audio server does not support seeking."
        case .incompleteResponse: return "The audio download ended unexpectedly."
        }
    }
}

final class MuzzaResourceLoader: NSObject, AVAssetResourceLoaderDelegate, URLSessionDataDelegate {
    private struct Pending {
        let request: AVAssetResourceLoadingRequest
        let task: URLSessionDataTask
        let offset: Int64
        let length: Int64?
        var received: Int64 = 0
        var totalLength: Int64?
    }

    private let url: URL
    private let headers: [String: String]
    private let queue = DispatchQueue(label: "com.maloy.muzza.audio-resource-loader")
    private var pending: [Int: Pending] = [:]
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        let delegateQueue = OperationQueue()
        delegateQueue.maxConcurrentOperationCount = 1
        delegateQueue.underlyingQueue = queue
        return URLSession(configuration: configuration, delegate: self, delegateQueue: delegateQueue)
    }()

    init(url: URL, headers: [String: String]) {
        self.url = url
        self.headers = headers
        super.init()
    }

    func makeAsset() throws -> AVURLAsset {
        guard Self.isAllowed(url), var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw MuzzaMediaError.invalidURL
        }
        components.scheme = "muzza-media"
        guard let assetURL = components.url else { throw MuzzaMediaError.invalidURL }
        let asset = AVURLAsset(url: assetURL)
        asset.resourceLoader.setDelegate(self, queue: queue)
        return asset
    }

    func invalidate() {
        queue.async {
            self.session.invalidateAndCancel()
            for entry in self.pending.values {
                entry.request.finishLoading(with: URLError(.cancelled))
            }
            self.pending.removeAll()
        }
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        let dataRequest = loadingRequest.dataRequest
        let requestedOffset = dataRequest?.requestedOffset ?? 0
        let offset = max(requestedOffset, dataRequest?.currentOffset ?? 0)
        let requestedLength = Int64(dataRequest?.requestedLength ?? 1)
        guard requestedOffset >= 0, requestedLength >= 0,
              requestedOffset <= Int64.max - requestedLength else {
            loadingRequest.finishLoading(with: MuzzaMediaError.unsupportedRange)
            return true
        }
        let end = requestedOffset + requestedLength
        let length = dataRequest?.requestsAllDataToEndOfResource == true ? nil : max(0, end - offset)
        var request = URLRequest(url: url)
        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }
        if let length, length > 0, offset <= Int64.max - length {
            request.setValue("bytes=\(offset)-\(offset + length - 1)", forHTTPHeaderField: "Range")
        } else {
            request.setValue("bytes=\(offset)-", forHTTPHeaderField: "Range")
        }
        let task = session.dataTask(with: request)
        pending[task.taskIdentifier] = Pending(request: loadingRequest, task: task, offset: offset, length: length)
        task.resume()
        return true
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, didCancel loadingRequest: AVAssetResourceLoadingRequest) {
        guard let entry = pending.first(where: { $0.value.request === loadingRequest }) else { return }
        pending.removeValue(forKey: entry.key)
        entry.value.task.cancel()
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        guard var entry = pending[dataTask.taskIdentifier] else {
            completionHandler(.cancel)
            return
        }
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 || http.statusCode == 206 else {
            finish(dataTask, error: MuzzaMediaError.invalidResponse)
            completionHandler(.cancel)
            return
        }

        let total: Int64
        if http.statusCode == 206 {
            guard let range = http.value(forHTTPHeaderField: "Content-Range"),
                  let parsed = Self.parseContentRange(range), parsed.start == entry.offset else {
                finish(dataTask, error: MuzzaMediaError.invalidResponse)
                completionHandler(.cancel)
                return
            }
            total = parsed.total
        } else {
            guard entry.offset == 0 else {
                finish(dataTask, error: MuzzaMediaError.unsupportedRange)
                completionHandler(.cancel)
                return
            }
            total = response.expectedContentLength
        }
        guard total > 0, entry.offset < total else {
            finish(dataTask, error: MuzzaMediaError.invalidResponse)
            completionHandler(.cancel)
            return
        }
        entry.totalLength = total
        pending[dataTask.taskIdentifier] = entry

        if let information = entry.request.contentInformationRequest {
            information.contentLength = total
            information.isByteRangeAccessSupported = http.statusCode == 206
            information.contentType = response.mimeType.flatMap { UTType(mimeType: $0)?.identifier }
                ?? UTType.mpeg4Audio.identifier
        }
        if entry.request.dataRequest == nil || entry.length == 0 {
            finish(dataTask)
            completionHandler(.cancel)
        } else {
            completionHandler(.allow)
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard var entry = pending[dataTask.taskIdentifier], let request = entry.request.dataRequest else { return }
        let remaining = entry.length.map { max(0, $0 - entry.received) }
        let count = remaining.map { min(data.count, Int(clamping: $0)) } ?? data.count
        if count > 0 {
            request.respond(with: Data(data.prefix(count)))
            entry.received += Int64(count)
        }
        pending[dataTask.taskIdentifier] = entry
        if let length = entry.length, entry.received >= length {
            finish(dataTask)
            dataTask.cancel()
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let entry = pending[task.taskIdentifier] else { return }
        if let error {
            finish(task, error: error)
            return
        }
        if let length = entry.length, let total = entry.totalLength,
           entry.received < min(length, total - entry.offset) {
            finish(task, error: MuzzaMediaError.incompleteResponse)
        } else {
            finish(task)
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let destination = request.url, Self.isAllowed(destination) else {
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

    private func finish(_ task: URLSessionTask, error: Error? = nil) {
        guard let entry = pending.removeValue(forKey: task.taskIdentifier) else { return }
        if let error {
            entry.request.finishLoading(with: error)
        } else {
            entry.request.finishLoading()
        }
    }

    static func isAllowed(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https", url.user == nil, url.password == nil,
              url.port == nil || url.port == 443, let host = url.host?.lowercased() else { return false }
        return host == "googlevideo.com" || host.hasSuffix(".googlevideo.com")
            || host == "youtube.com" || host.hasSuffix(".youtube.com")
    }

    static func parseContentRange(_ value: String) -> (start: Int64, total: Int64)? {
        let parts = value.split(separator: "/", omittingEmptySubsequences: false)
        guard parts.count == 2, let total = Int64(parts[1]), total > 0 else { return nil }
        let unitAndRange = parts[0].split(separator: " ")
        guard unitAndRange.count == 2, unitAndRange[0].lowercased() == "bytes" else { return nil }
        let bounds = unitAndRange[1].split(separator: "-", omittingEmptySubsequences: false)
        guard bounds.count == 2, let start = Int64(bounds[0]), let end = Int64(bounds[1]),
              start >= 0, end >= start, end < total else { return nil }
        return (start, total)
    }
}
