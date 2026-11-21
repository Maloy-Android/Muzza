import SwiftUI
import WebKit

struct MuzzaAccountView: View {
    @ObservedObject var account: MuzzaAccount
    @ObservedObject var playback: MuzzaPlayback
    @AppStorage("muzza.syncLikes") private var syncLikes = true
    @State private var showWebLogin = false
    @State private var showCookieLogin = false

    init(account: MuzzaAccount, playback: MuzzaPlayback) {
        _account = ObservedObject(wrappedValue: account)
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        List {
            Section("YouTube Music") {
                if account.isBusy {
                    ProgressView()
                } else if let profile = account.profile {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(profile.name).font(.headline)
                        if let detail = profile.email ?? profile.channelHandle {
                            Text(detail).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Button("Sign out", role: .destructive) {
                        Task {
                            _ = await account.signOut()
                            if account.profile == nil { playback.stopForSignOut() }
                        }
                    }
                } else {
                    Button("Sign in with YouTube") { showWebLogin = true }
                    Button("Use a session cookie") { showCookieLogin = true }
                    if account.hasStoredSession {
                        Button("Retry saved session") { Task { await account.restore() } }
                    }
                    if account.hasStoredSession || account.error != nil {
                        Button("Clear saved session", role: .destructive) {
                            Task {
                                _ = await account.signOut()
                                if account.profile == nil { playback.stopForSignOut() }
                            }
                        }
                    }
                }
                Toggle("Sync liked songs with YouTube Music", isOn: $syncLikes)
                    .disabled(account.profile == nil || account.isBusy)
            }
            if let error = account.error {
                Section {
                    Text(error).font(.footnote).foregroundStyle(.secondary).textSelection(.enabled)
                }
            }
            Section {
                Text("Your session is stored in the iPhone Keychain and is not shared with Muzza's developer.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showWebLogin) { MuzzaWebLoginView(account: account) }
        .sheet(isPresented: $showCookieLogin) { MuzzaCookieLoginView(account: account) }
    }
}

private struct MuzzaCookieLoginView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var account: MuzzaAccount
    @State private var cookie = ""

    init(account: MuzzaAccount) {
        _account = ObservedObject(wrappedValue: account)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("YouTube session cookie", text: $cookie)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("Sign in") {
                        let value = cookie
                        cookie = ""
                        Task {
                            if await account.signIn(cookie: value) { dismiss() }
                        }
                    }
                    .disabled(cookie.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || account.isBusy)
                } footer: {
                    Text("Enter a cookie from your own YouTube Music session that includes SAPISID. Never send it to anyone.")
                }
                if account.isBusy { ProgressView() }
                if let error = account.error {
                    Text(error).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Session cookie")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(account.isBusy)
                }
            }
        }
        .interactiveDismissDisabled(account.isBusy)
    }
}

private struct MuzzaWebLoginView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var account: MuzzaAccount
    @State private var webError: String?
    @State private var reloadToken = 0
    @State private var isHandlingSession = false

    init(account: MuzzaAccount) {
        _account = ObservedObject(wrappedValue: account)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                MuzzaLoginWebView(reloadToken: reloadToken) { cookie in
                    guard !isHandlingSession else { return }
                    isHandlingSession = true
                    Task {
                        if await account.signIn(cookie: cookie) {
                            dismiss()
                        } else {
                            webError = account.error
                            isHandlingSession = false
                        }
                    }
                } onError: { message in
                    webError = message
                }
                if account.isBusy {
                    ProgressView().padding().background(.regularMaterial).clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .navigationTitle("Sign in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(account.isBusy)
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let webError {
                    HStack {
                        Text(webError).font(.footnote).foregroundStyle(.secondary)
                        Spacer()
                        Button("Retry") {
                            self.webError = nil
                            reloadToken += 1
                        }
                    }
                    .padding().background(.regularMaterial)
                }
            }
        }
        .interactiveDismissDisabled(account.isBusy)
    }
}

private struct MuzzaLoginWebView: UIViewRepresentable {
    let reloadToken: Int
    let onSession: (String) -> Void
    let onError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.uiDelegate = context.coordinator
        let address = "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
        if let url = URL(string: address) {
            view.load(URLRequest(url: url))
        } else {
            onError("Invalid YouTube sign-in address.")
        }
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.reloadToken != reloadToken {
            context.coordinator.reloadToken = reloadToken
            context.coordinator.sentSession = false
            view.reload()
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: MuzzaLoginWebView
        var reloadToken = 0
        var sentSession = false

        init(_ parent: MuzzaLoginWebView) { self.parent = parent }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !sentSession, webView.url?.host?.lowercased() == "music.youtube.com",
                  let target = URL(string: "https://music.youtube.com/youtubei/v1/") else { return }
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                guard let self, !self.sentSession else { return }
                do {
                    let header = try MuzzaCookieFilter.header(from: cookies, for: target)
                    self.sentSession = true
                    self.parent.onSession(header)
                } catch MuzzaCookieError.missingSession {
                } catch {
                    self.parent.onError(error.localizedDescription)
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            report(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            report(error)
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard navigationAction.targetFrame?.isMainFrame != false else {
                decisionHandler(.allow)
                return
            }
            guard let url = navigationAction.request.url, url.scheme?.lowercased() == "https",
                  let host = url.host?.lowercased(),
                  ["google.com", "youtube.com", "gstatic.com", "googleusercontent.com"].contains(where: {
                      host == $0 || host.hasSuffix("." + $0)
                  }) else {
                decisionHandler(.cancel)
                parent.onError("Only Google and YouTube sign-in pages are allowed.")
                return
            }
            decisionHandler(.allow)
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
            return nil
        }

        private func report(_ error: Error) {
            guard (error as NSError).code != NSURLErrorCancelled else { return }
            parent.onError(error.localizedDescription)
        }
    }
}
