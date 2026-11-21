import SwiftUI
import Shared
import AVFoundation

@main
struct MuzzaApp: App {
    init() {
        configureAppAudioSession()
        KoinIosKt.initKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }

    private func configureAppAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        try? audioSession.setCategory(.playback, mode: .default)
        try? audioSession.setActive(true)
    }
}
