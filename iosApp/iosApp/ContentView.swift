import Foundation
import SwiftUI

enum MuzzaAccent: String, CaseIterable {
    case coral, blue, green, purple, orange, pink

    var title: String { rawValue.capitalized }

    var color: Color {
        switch self {
        case .coral: return Color(red: 237.0 / 255, green: 85.0 / 255, blue: 100.0 / 255)
        case .blue: return .blue
        case .green: return .green
        case .purple: return .purple
        case .orange: return .orange
        case .pink: return .pink
        }
    }
}

enum MuzzaThemePreference {
    static var initialValue: String {
        let defaults = UserDefaults.standard
        if let stored = defaults.string(forKey: "muzza.themeMode"),
           ["system", "dark", "light"].contains(stored) {
            return stored
        }
        let migrated: String
        if let legacyDark = defaults.object(forKey: "muzza.darkTheme") as? Bool {
            migrated = legacyDark ? "dark" : "light"
        } else {
            migrated = "system"
        }
        defaults.set(migrated, forKey: "muzza.themeMode")
        return migrated
    }
}

enum MuzzaCacheSettings {
    static func apply() {
        let defaults = UserDefaults.standard
        let memoryMB = min(max(defaults.object(forKey: "muzza.cacheMemoryMB") as? Int ?? 16, 0), 64)
        let diskMB = min(max(defaults.object(forKey: "muzza.cacheDiskMB") as? Int ?? 100, 0), 500)
        URLCache.shared = URLCache(
            memoryCapacity: max(0, memoryMB) * 1_024 * 1_024,
            diskCapacity: max(0, diskMB) * 1_024 * 1_024,
            diskPath: "muzza-url-cache"
        )
    }
}

struct ContentView: View {
    @State private var selectedTab = 0
    @State private var showSearch = false
    @State private var showSettings = false
    @State private var showPlayer = false
    @StateObject private var playback = MuzzaPlayback()
    @StateObject private var account = MuzzaAccount()
    @AppStorage("muzza.themeMode") private var themeMode = MuzzaThemePreference.initialValue
    @AppStorage("muzza.accentColor") private var accentColor = MuzzaAccent.coral.rawValue

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                MuzzaHomeView(playback: playback, account: account, showSearch: $showSearch, showSettings: $showSettings)
                    .opacity(selectedTab == 0 ? 1 : 0)
                    .allowsHitTesting(selectedTab == 0)
                MuzzaExploreView(playback: playback, account: account, showSearch: $showSearch, showSettings: $showSettings)
                    .opacity(selectedTab == 1 ? 1 : 0)
                    .allowsHitTesting(selectedTab == 1)
                MuzzaLibraryView(playback: playback, account: account, showSearch: $showSearch, showSettings: $showSettings)
                    .opacity(selectedTab == 2 ? 1 : 0)
                    .allowsHitTesting(selectedTab == 2)
            }
            MuzzaBottomNavigation(selectedTab: $selectedTab)
                .padding(.horizontal, 4)
            if playback.current != nil {
                MuzzaMiniPlayer(playback: playback) { showPlayer = true }
                    .padding(.horizontal, 4)
                    .padding(.bottom, 72)
            }
        }
        .tint(MuzzaAccent(rawValue: accentColor)?.color ?? MuzzaAccent.coral.color)
        .preferredColorScheme(themeMode == "dark" ? .dark : (themeMode == "light" ? .light : nil))
        .onAppear { MuzzaCacheSettings.apply() }
        .sheet(isPresented: $showSearch) { MuzzaSearchView(playback: playback) }
        .sheet(isPresented: $showSettings) { MuzzaSettingsView(playback: playback, account: account) }
        .fullScreenCover(isPresented: $showPlayer) { MuzzaPlayerView(playback: playback) }
        .onChange(of: account.revision) { _ in
            playback.accountDidChange(isSignedIn: account.profile != nil)
            Task { await playback.refreshLibrary() }
        }
        .alert("Muzza", isPresented: Binding(
            get: { playback.message != nil },
            set: { if !$0 { playback.message = nil } }
        )) {
            Button("OK", role: .cancel) { playback.message = nil }
        } message: {
            Text(playback.message ?? "")
        }
    }

    private struct MuzzaBottomNavigation: View {
        @Binding var selectedTab: Int

        private enum Tab: Int, CaseIterable, Identifiable {
            case home
            case explore
            case library

            var id: Int { rawValue }

            var title: String {
                switch self {
                case .home: return "Home"
                case .explore: return "Explore"
                case .library: return "Library"
                }
            }

            var icon: String {
                switch self {
                case .home: return "house.fill"
                case .explore: return "safari.fill"
                case .library: return "music.note.list"
                }
            }
        }

        var body: some View {
            HStack(spacing: 8) {
                ForEach(Tab.allCases) { tab in
                    let selected = selectedTab == tab.rawValue
                    Button {
                        selectedTab = tab.rawValue
                    } label: {
                        VStack(spacing: 3) {
                            ZStack {
                                if selected {
                                    Capsule()
                                        .fill(Color.accentColor.opacity(0.16))
                                        .frame(width: 64, height: 30)
                                }
                                Image(systemName: tab.icon)
                                    .font(.system(size: 17, weight: .semibold))
                            }
                            .frame(height: 30)
                            Text(tab.title)
                                .font(.caption2.weight(selected ? .bold : .semibold))
                        }
                        .foregroundStyle(selected ? Color.accentColor : .secondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 6)
            .padding(.bottom, 6)
            .background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(Color(.separator).opacity(0.35), lineWidth: 0.5)
            )
            .shadow(color: .black.opacity(0.16), radius: 12, y: 4)
            .padding(.bottom, 0)
        }
    }
}

private struct MuzzaHomeView: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaLiveSection] = []
    @State private var chips: [MuzzaHomeChip] = []
    @State private var selectedParams: String?
    @State private var continuation: String?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()
    @State private var loadedParams: String?
    @State private var loadedContinuations: Set<String> = []

    init(
        playback: MuzzaPlayback,
        account: MuzzaAccount,
        showSearch: Binding<Bool>,
        showSettings: Binding<Bool>
    ) {
        _playback = ObservedObject(wrappedValue: playback)
        _account = ObservedObject(wrappedValue: account)
        _showSearch = showSearch
        _showSettings = showSettings
    }

    private var quickPicks: [MuzzaLiveItem] {
        Array(playback.recent.filter { !hideExplicit || !($0.explicit ?? false) }.prefix(4))
    }

    private func mergedSections(_ fresh: [MuzzaLiveSection], preserving old: [MuzzaLiveSection]) -> [MuzzaLiveSection] {
        var merged = fresh
        for oldSection in old {
            if let index = merged.firstIndex(where: {
                $0.title == oldSection.title && $0.moreItem?.id == oldSection.moreItem?.id
            }) {
                let current = merged[index]
                var seen = Set<String>()
                let items = (current.items + oldSection.items).filter {
                    seen.insert("\($0.kind):\($0.id)").inserted
                }
                merged[index] = MuzzaLiveSection(
                    title: current.title,
                    items: items,
                    moreItem: current.moreItem ?? oldSection.moreItem
                )
            } else {
                merged.append(oldSection)
            }
        }
        return merged
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    MuzzaHeader("Home", search: { showSearch = true }, settings: { showSettings = true })
                    if !chips.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(chips) { chip in
                                    Button {
                                        selectedParams = selectedParams == chip.params ? nil : chip.params
                                    } label: {
                                        Text(chip.title)
                                            .font(.subheadline.weight(.semibold))
                                            .padding(.horizontal, 16).padding(.vertical, 10)
                                            .background(selectedParams == chip.params ? Color.accentColor : Color(.secondarySystemFill))
                                            .clipShape(Capsule())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    if !quickPicks.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Quick picks").font(.title2.bold())
                            ForEach(Array(quickPicks.enumerated()), id: \.offset) { index, item in
                                MuzzaSongRow(item: item, playback: playback, queue: quickPicks, queueIndex: index)
                            }
                        }
                    }
                    ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                        MuzzaMediaSectionView(section: section, playback: playback)
                    }
                    if isLoading {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let loadError {
                        MuzzaErrorView(message: loadError) { Task { await reload() } }
                    } else if sections.isEmpty {
                        MuzzaEmptyView(symbol: "music.note", message: "No recommendations are available.")
                    }
                    if let pageError {
                        MuzzaErrorView(message: pageError) { Task { await loadMore() } }
                    } else if continuation != nil && !isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .onAppear { Task { await loadMore() } }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .task(id: "\(hideExplicit)-\(selectedParams ?? "")-\(account.revision)") { await reload() }
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        let canPreserveLoadedSections = loadedParams == selectedParams
        isLoading = true
        loadError = nil
        pageError = nil
        if !canPreserveLoadedSections {
            continuation = nil
        }
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.home(hideExplicit: hideExplicit, params: selectedParams, continuation: nil)
            guard !Task.isCancelled, requestID == id else { return }
            sections = mergedSections(response.sections, preserving: canPreserveLoadedSections ? sections : [])
            chips = response.chips
            continuation = response.continuation
            loadedParams = selectedParams
            loadedContinuations.removeAll()
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation, !loadedContinuations.contains(token) else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.home(hideExplicit: hideExplicit, params: selectedParams, continuation: token)
            guard !Task.isCancelled, requestID == id else { return }
            loadedContinuations.insert(token)
            sections = mergedSections(response.sections, preserving: sections)
            if response.continuation == token || response.continuation.map({ loadedContinuations.contains($0) }) == true {
                pageError = "YouTube returned an invalid continuation."
                continuation = nil
            } else {
                isLoading = false
                continuation = response.continuation
            }
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            pageError = error.localizedDescription
        }
    }
}

private struct MuzzaExploreView: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject var account: MuzzaAccount
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaLiveSection] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var requestID = UUID()

    init(playback: MuzzaPlayback, account: MuzzaAccount, showSearch: Binding<Bool>, showSettings: Binding<Bool>) {
        _playback = ObservedObject(wrappedValue: playback)
        _account = ObservedObject(wrappedValue: account)
        _showSearch = showSearch
        _showSettings = showSettings
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    MuzzaHeader("Explore", search: { showSearch = true }, settings: { showSettings = true })
                    ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                        MuzzaMediaSectionView(
                            section: section,
                            playback: playback,
                            mediaAspectRatio: section.title == "New clips" ? 16.0 / 9.0 : 1
                        )
                    }
                    if isLoading {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let loadError {
                        MuzzaErrorView(message: loadError) { Task { await reload() } }
                    } else if sections.isEmpty {
                        MuzzaEmptyView(symbol: "safari", message: "No music is available to explore.")
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .task(id: "\(hideExplicit)-\(account.revision)") { await reload() }
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.explore(hideExplicit: hideExplicit)
            guard !Task.isCancelled, requestID == id else { return }
            sections = response.sections
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }
}
