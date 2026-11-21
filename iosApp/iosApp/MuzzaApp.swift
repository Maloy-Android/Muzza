import SwiftUI
import Foundation
import Shared

@main
struct MuzzaApp: App {
    init() {
        let locale = NSLocale.current
        let gl = locale.countryCode?.trimmingCharacters(in: .whitespacesAndNewlines)
        let hl = locale.languageCode?.trimmingCharacters(in: .whitespacesAndNewlines)
        IosBridgeKt.configureIosLocale(
            gl: gl?.isEmpty == false ? gl! : "US",
            hl: hl?.isEmpty == false ? hl! : "en"
        )
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
