import SwiftUI
import Shared

@main
struct MuzzaApp: App {

    init() {
        KoinIosKt.initKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
