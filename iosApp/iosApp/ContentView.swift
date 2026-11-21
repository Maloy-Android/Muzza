import SwiftUI
import Shared
import AVFoundation

struct MuzzaMediaItem: Codable, Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let thumbnail: String
}

struct MuzzaMediaSection: Codable, Identifiable {
    let title: String
    let items: [MuzzaMediaItem]

    var id: String { title }
}

struct MuzzaBridgeResponse: Codable {
    let sections: [MuzzaMediaSection]?
    let items: [MuzzaMediaItem]?
    let error: String?
}

@MainActor
final class MuzzaAudioPlayer: ObservableObject {
    private var player: AVPlayer?
    @Published var currentItem: MuzzaMediaItem?
    @Published var isPlaying = false

    func play(_ item: MuzzaMediaItem) {
        currentItem = item
        IosBridgeKt.fetchIosPlayerUrl(videoId: item.id) { [weak self] urlString in
            guard let self, let url = URL(string: urlString), !urlString.isEmpty else { return }
            DispatchQueue.main.async {
                let avPlayer = AVPlayer(url: url)
                self.player = avPlayer
                avPlayer.play()
                self.isPlaying = true
            }
        }
    }

    func toggle() {
        guard let player else { return }
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
    }
}

struct ContentView: View {
    @State private var selectedTab = 0
    @StateObject private var player = MuzzaAudioPlayer()
    @State private var showSearch = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                HomeView(player: player)
                    .tag(0)
                    .tabItem { Label("Home", systemImage: "house.fill") }

                LibraryView()
                    .tag(1)
                    .tabItem { Label("Library", systemImage: "music.note.list") }

                ExploreView(player: player)
                    .tag(2)
                    .tabItem { Label("Explore", systemImage: "safari") }

                SettingsView()
                    .tag(3)
                    .tabItem { Label("Settings", systemImage: "gearshape.fill") }
            }
            .tint(Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255))
            .preferredColorScheme(.dark)

            if player.currentItem != nil {
                MiniPlayer(player: player)
                    .padding(.bottom, 49)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .sheet(isPresented: $showSearch) {
            SearchView(player: player)
        }
    }
}

private struct HomeView: View {
    @ObservedObject var player: MuzzaAudioPlayer
    @State private var sections: [MuzzaMediaSection] = []
    @State private var isLoading = false
    @State private var showSearch = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Good evening")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                            Text("Muzza")
                                .font(.largeTitle.bold())
                        }
                        Spacer()
                        Button {
                            showSearch = true
                        } label: {
                            Image(systemName: "magnifyingglass")
                                .font(.title3.weight(.semibold))
                                .frame(width: 44, height: 44)
                                .background(.ultraThinMaterial)
                                .clipShape(Circle())
                        }
                    }

                    if isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .tint(Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255))
                    }

                    if sections.isEmpty && !isLoading {
                        MuzzaFallbackCard(
                            title: "Reconnect",
                            subtitle: "Tap to load InnerTube recommendations"
                        ) {
                            loadHome()
                        }
                    }

                    ForEach(sections) { section in
                        MuzzaSection(title: section.title) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(section.items.prefix(20)) { item in
                                        MuzzaCard(item: item) {
                                            player.play(item)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding()
                .padding(.bottom, 70)
            }
            .navigationTitle("Muzza")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                loadHome()
            }
            .sheet(isPresented: $showSearch) {
                SearchView(player: player)
            }
        }
    }

    private func loadHome() {
        guard !isLoading else { return }
        isLoading = true
        IosBridgeKt.fetchIosHome { raw in
            DispatchQueue.main.async {
                isLoading = false
                guard let data = raw.data(using: .utf8),
                      let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) else {
                    return
                }
                sections = response.sections ?? []
            }
        }
    }
}

private struct ExploreView: View {
    @ObservedObject var player: MuzzaAudioPlayer
    @State private var sections: [MuzzaMediaSection] = []
    @State private var isLoading = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Explore")
                        .font(.largeTitle.bold())

                    if isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    }

                    ForEach(sections) { section in
                        MuzzaSection(title: section.title) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(section.items.prefix(20)) { item in
                                        MuzzaCard(item: item) {
                                            player.play(item)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding()
                .padding(.bottom, 70)
            }
            .navigationBarTitleDisplayMode(.inline)
            .task {
                isLoading = true
                IosBridgeKt.fetchIosExplore { raw in
                    DispatchQueue.main.async {
                        isLoading = false
                        if let data = raw.data(using: .utf8),
                           let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) {
                            sections = response.sections ?? []
                        }
                    }
                }
            }
        }
    }
}

private struct SearchView: View {
    @ObservedObject var player: MuzzaAudioPlayer
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var items: [MuzzaMediaItem] = []
    @State private var isLoading = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField("Search songs", text: $query)
                        .textInputAutocapitalization(.never)
                }
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
                ForEach(items) { item in
                    Button {
                        player.play(item)
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            AsyncImage(url: URL(string: item.thumbnail)) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Rectangle().fill(.ultraThinMaterial)
                            }
                            .frame(width: 52, height: 52)
                            .clipShape(RoundedRectangle(cornerRadius: 10))

                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.title).lineLimit(1)
                                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "play.circle.fill")
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Search")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onSubmit(of: .text) {
                runSearch()
            }
            .onChange(of: query) { newValue in
                if newValue.count >= 3 {
                    runSearch()
                }
            }
        }
    }

    private func runSearch() {
        guard !query.isEmpty, !isLoading else { return }
        isLoading = true
        IosBridgeKt.searchIosMusic(query: query) { raw in
            DispatchQueue.main.async {
                isLoading = false
                if let data = raw.data(using: .utf8),
                   let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) {
                    items = response.items ?? []
                }
            }
        }
    }
}

private struct LibraryView: View {
    @State private var category = 0
    @State private var likedSongs: [MuzzaMediaItem] = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Picker("Library", selection: $category) {
                        Text("Library").tag(0)
                        Text("Liked").tag(1)
                        Text("Downloaded").tag(2)
                    }
                    .pickerStyle(.segmented)

                    if category == 1 {
                        if likedSongs.isEmpty {
                            EmptyLibraryState(title: "No liked songs", subtitle: "Liked tracks will appear here.")
                        } else {
                            LazyVStack(spacing: 8) {
                                ForEach(likedSongs) { item in
                                    LibrarySongRow(item: item)
                                }
                            }
                        }
                    } else {
                        LazyVGrid(columns: [
                            GridItem(.flexible(), spacing: 12),
                            GridItem(.flexible(), spacing: 12)
                        ], spacing: 12) {
                            LibraryCard(title: "Liked songs", count: "0 songs", symbol: "heart.fill")
                            LibraryCard(title: "Albums", count: "0 albums", symbol: "square.stack.fill")
                            LibraryCard(title: "Artists", count: "0 artists", symbol: "mic.fill")
                            LibraryCard(title: "Playlists", count: "0 playlists", symbol: "music.note.list")
                            LibraryCard(title: "Downloads", count: "0 tracks", symbol: "arrow.down.circle.fill")
                            LibraryCard(title: "History", count: "Recent activity", symbol: "clock.fill")
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Library")
        }
    }
}

private struct SettingsView: View {
    @AppStorage("muzza.darkTheme") private var darkTheme = true
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @AppStorage("muzza.persistentQueue") private var persistentQueue = true
    @AppStorage("muzza.audioQuality") private var audioQuality = "Auto"

    var body: some View {
        NavigationStack {
            Form {
                Section("Appearance") {
                    Toggle("Dark theme", isOn: $darkTheme)
                    Toggle("Pure black", isOn: $pureBlack)
                }
                Section("Content") {
                    Toggle("Hide explicit content", isOn: $hideExplicit)
                }
                Section("Playback") {
                    Picker("Audio quality", selection: $audioQuality) {
                        Text("Auto").tag("Auto")
                        Text("High").tag("High")
                        Text("Low").tag("Low")
                    }
                    Toggle("Persistent queue", isOn: $persistentQueue)
                }
                Section("About") {
                    LabeledContent("App", Text("Muzza"))
                    LabeledContent("iOS", Text("Native SwiftUI"))
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle("Settings")
        }
    }
}

private struct MiniPlayer: View {
    @ObservedObject var player: MuzzaAudioPlayer

    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: URL(string: player.currentItem?.thumbnail ?? "")) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                RoundedRectangle(cornerRadius: 8).fill(.ultraThinMaterial)
            }
            .frame(width: 44, height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(player.currentItem?.title ?? "")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(player.currentItem?.subtitle ?? "")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                player.toggle()
            } label: {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.title3)
            }
            .buttonStyle(.plain)
        }
        .padding(10)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal)
    }
}

private struct MuzzaSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title3.bold())
            content
        }
    }
}

private struct MuzzaCard: View {
    let item: MuzzaMediaItem
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                AsyncImage(url: URL(string: item.thumbnail)) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else if phase.error != nil {
                        fallbackArtwork
                    } else {
                        ZStack {
                            RoundedRectangle(cornerRadius: 18).fill(.ultraThinMaterial)
                            ProgressView()
                        }
                    }
                }
                .frame(width: 156, height: 156)
                .clipShape(RoundedRectangle(cornerRadius: 18))

                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title).font(.headline).lineLimit(1)
                    Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                .frame(width: 156, alignment: .leading)
            }
        }
        .buttonStyle(.plain)
    }

    private var fallbackArtwork: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255),
                            Color(red: 70 / 255, green: 20 / 255, blue: 42 / 255)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Image(systemName: "music.note")
                .font(.system(size: 36, weight: .semibold))
                .foregroundStyle(.white)
        }
    }
}

private struct MuzzaFallbackCard: View {
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "arrow.clockwise")
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

private struct LibraryCard: View {
    let title: String
    let count: String
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255))
            Spacer()
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline).lineLimit(1)
                Text(count).font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .leading)
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct EmptyLibraryState: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "music.note")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)
            Text(title).font(.headline)
            Text(subtitle).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 80)
    }
}

private struct LibrarySongRow: View {
    let item: MuzzaMediaItem

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: item.thumbnail)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Rectangle().fill(.ultraThinMaterial)
            }
            .frame(width: 52, height: 52)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading) {
                Text(item.title).lineLimit(1)
                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }
}
