import Foundation
import Security
import SwiftUI

private struct MuzzaAccountSession: Codable {
    let cookie: String
    let visitorData: String?
}

private enum MuzzaKeychainError: LocalizedError {
    case status(OSStatus)
    case invalidData

    var errorDescription: String? {
        switch self {
        case .status(let status): return "Could not access the iPhone Keychain (error \(status))."
        case .invalidData: return "The saved YouTube session could not be read."
        }
    }
}

private enum MuzzaKeychain {
    private static var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: (Bundle.main.bundleIdentifier ?? "com.maloy.muzza") + ".youtube-session",
            kSecAttrAccount as String: "current",
            kSecAttrSynchronizable as String: false
        ]
    }

    static func load() throws -> MuzzaAccountSession? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw MuzzaKeychainError.status(status) }
        guard let data = result as? Data else { throw MuzzaKeychainError.invalidData }
        return try JSONDecoder().decode(MuzzaAccountSession.self, from: data)
    }

    static func save(_ session: MuzzaAccountSession) throws {
        let data = try JSONEncoder().encode(session)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var request = query
            request[kSecValueData as String] = data
            request[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let addStatus = SecItemAdd(request as CFDictionary, nil)
            guard addStatus == errSecSuccess else { throw MuzzaKeychainError.status(addStatus) }
        } else if status != errSecSuccess {
            throw MuzzaKeychainError.status(status)
        }
    }

    static func delete() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw MuzzaKeychainError.status(status)
        }
    }
}

@MainActor
final class MuzzaAccount: ObservableObject {
    @Published private(set) var profile: MuzzaAccountProfile?
    @Published private(set) var isBusy = false
    @Published private(set) var hasStoredSession = false
    @Published private(set) var revision = 0
    @Published var error: String?

    init() {
        Task { await restore() }
    }

    func restore() async {
        guard !isBusy else { return }
        isBusy = true
        error = nil
        defer { isBusy = false }
        do {
            guard let session = try MuzzaKeychain.load() else {
                hasStoredSession = false
                return
            }
            hasStoredSession = true
            let account = try await MuzzaAPI.validateAccount(cookie: session.cookie, visitorData: session.visitorData)
            try await MuzzaAPI.activateAccount(cookie: session.cookie, visitorData: session.visitorData)
            profile = account
            revision += 1
        } catch {
            self.error = error.localizedDescription
        }
    }

    func signIn(cookie: String, visitorData: String? = nil) async -> Bool {
        guard !isBusy else {
            error = "An account operation is already in progress."
            return false
        }
        isBusy = true
        error = nil
        defer { isBusy = false }
        do {
            let previous = try MuzzaKeychain.load()
            let session = MuzzaAccountSession(cookie: cookie, visitorData: visitorData)
            let account = try await MuzzaAPI.validateAccount(cookie: cookie, visitorData: visitorData)
            try MuzzaKeychain.save(session)
            do {
                try await MuzzaAPI.activateAccount(cookie: cookie, visitorData: visitorData)
            } catch {
                if let previous { try MuzzaKeychain.save(previous) } else { try MuzzaKeychain.delete() }
                throw error
            }
            profile = account
            hasStoredSession = true
            revision += 1
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    func signOut() async -> Bool {
        guard !isBusy else {
            error = "An account operation is already in progress."
            return false
        }
        isBusy = true
        error = nil
        defer { isBusy = false }
        do {
            try await MuzzaAPI.clearAccount()
            profile = nil
            revision += 1
            try MuzzaKeychain.delete()
            hasStoredSession = false
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
}
