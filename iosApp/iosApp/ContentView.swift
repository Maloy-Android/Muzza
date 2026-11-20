import SwiftUI
import UIKit
import Shared

struct ContentView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // Hosts the entire Compose Multiplatform UI inside SwiftUI
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
