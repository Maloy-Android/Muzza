import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    @State private var showSearch = false
    @State private var showSettings = false
    @State private var showPlayer = false
    @StateObject private var playback = MuzzaPlayback()
    @AppStorage("muzza.darkTheme") private var darkTheme = true

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                MuzzaHomeView(playback: playback, showSearch: $showSearch, showSettings: $showSettings)
                    .tag(0).tabItem { Label("Home", systemImage: "house.fill") }
                MuzzaExploreView(playback: playback, showSearch: $showSearch, showSettings: $showSettings)
                    .tag(1).tabItem { Label("Explore", systemImage: "safari") }
                MuzzaLibraryView(playback: playback, showSearch: $showSearch, showSettings: $showSettings)
                    .tag(2).tabItem { Label("Library", systemImage: "music.note.list") }
            }
            if playback.current != nil {
                MuzzaMiniPlayer(playback: playback) { showPlayer = true }
                    .padding(.bottom, 49)
            }
        }
        .tint(Color(red: 237.0 / 255, green: 85.0 / 255, blue: 100.0 / 255))
        .preferredColorScheme(darkTheme ? .dark : .light)
        .sheet(isPresented: $showSearch) { MuzzaSearchView(playback: playback) }
        .sheet(isPresented: $showSettings) { MuzzaSettingsView(playback: playback) }
        .fullScreenCover(isPresented: $showPlayer) { MuzzaPlayerView(playback: playback) }
        .alert("Muzza", isPresented: Binding(
            get: { playback.message != nil },
            set: { if !$0 { playback.message = nil } }
        )) {
            Button("OK", role: .cancel) { playback.message = nil }
        } message: {
            Text(playback.message ?? "")
        }
    }
}

private struct MuzzaHomeView: View {
    @ObservedObject var playback: MuzzaPlayback
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaLiveSection] = []
    @State private var chips: [MuzzaHomeChip] = []
    @State private var selectedParams: String?
    @State private var continuation: String?
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()

    private var quickPicks: [MuzzaLiveItem] {
        Array(playback.recent.filter { !hideExplicit || !($0.explicit ?? false) }.prefix(4))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    MuzzaHeader("Home", search: { showSearch = true }, settings: { showSettings = true })
                    if !quickPicks.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Quick picks").font(.title2.bold())
                            ForEach(Array(quickPicks.enumerated()), id: \.offset) { index, item in
                                MuzzaSongRow(item: item, playback: playback, queue: quickPicks, queueIndex: index)
                            }
                        }
                    }
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
                        ProgressView().frame(maxWidth: .infinity).task { await loadMore() }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 100)
            }
            .modifier(MuzzaPageBackground())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .task(id: "\(hideExplicit)-\(selectedParams ?? "")") { await reload() }
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        pageError = nil
        sections = []
        chips = []
        continuation = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.home(hideExplicit: hideExplicit, params: selectedParams, continuation: nil)
            guard !Task.isCancelled, requestID == id else { return }
            sections = response.sections
            chips = response.chips
            continuation = response.continuation
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.home(hideExplicit: hideExplicit, params: selectedParams, continuation: token)
            guard !Task.isCancelled, requestID == id else { return }
            sections.append(contentsOf: response.sections)
            if response.continuation == token {
                continuation = nil
            } else {
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
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaLiveSection] = []
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var requestID = UUID()

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    MuzzaHeader("Explore", search: { showSearch = true }, settings: { showSettings = true })
                    ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                        MuzzaMediaSectionView(section: section, playback: playback)
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
            .task(id: hideExplicit) { await reload() }
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        sections = []
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
