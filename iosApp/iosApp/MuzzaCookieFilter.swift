import Foundation

enum MuzzaCookieError: LocalizedError {
    case invalidTarget
    case invalidCookie
    case missingSession

    var errorDescription: String? {
        switch self {
        case .invalidTarget: return "Invalid YouTube sign-in address."
        case .invalidCookie: return "The YouTube session contains an invalid cookie."
        case .missingSession: return "YouTube has not provided a complete sign-in session."
        }
    }
}

enum MuzzaCookieFilter {
    static func header(from cookies: [HTTPCookie], for url: URL, now: Date = Date()) throws -> String {
        guard url.scheme?.lowercased() == "https", url.host?.lowercased() == "music.youtube.com" else {
            throw MuzzaCookieError.invalidTarget
        }
        let host = "music.youtube.com"
        let path = url.path.isEmpty ? "/" : url.path
        let matching = cookies.filter { cookie in
            let domain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased()
            let domainMatches = host == domain || host.hasSuffix("." + domain)
            let cookiePath = cookie.path
            let pathMatches = path == cookiePath || (
                path.hasPrefix(cookiePath) && (cookiePath.hasSuffix("/") || path.dropFirst(cookiePath.count).first == "/")
            )
            return domainMatches && pathMatches && (cookie.expiresDate.map { $0 > now } ?? true)
        }.sorted { first, second in
            if first.path.count != second.path.count { return first.path.count > second.path.count }
            if first.domain.count != second.domain.count { return first.domain.count > second.domain.count }
            return first.name < second.name
        }

        let separators = CharacterSet(charactersIn: "()<>@,;:\\\"/[]?={} \t")
        var names = Set<String>()
        var parts: [String] = []
        for cookie in matching where names.insert(cookie.name).inserted {
            let validName = !cookie.name.isEmpty && cookie.name.unicodeScalars.allSatisfy {
                $0.value >= 33 && $0.value <= 126 && !separators.contains($0)
            }
            let validValue = cookie.value.unicodeScalars.allSatisfy {
                $0.value >= 32 && $0.value <= 126 && $0 != ";"
            }
            guard validName && validValue else { throw MuzzaCookieError.invalidCookie }
            parts.append("\(cookie.name)=\(cookie.value)")
        }
        guard names.contains("SAPISID") else { throw MuzzaCookieError.missingSession }
        return parts.joined(separator: "; ")
    }
}
