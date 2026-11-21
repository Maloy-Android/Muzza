import SwiftUI
import Foundation

@main
struct MuzzaApp: App {
    init() {
        NSSetUncaughtExceptionHandler { exception in
            let message = "\(exception.name.rawValue): \(exception.reason ?? "")\n\(exception.callStackSymbols.joined(separator: "\n"))"
            let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
                .appendingPathComponent("Muzza", isDirectory: true)
            if let directory {
                try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                try? message.write(to: directory.appendingPathComponent("native-crash.txt"), atomically: true, encoding: .utf8)
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
