import SwiftUI
import Shared
import AVFoundation

struct MuzzaMediaItem: Codable, Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let thumbnail: String
    let kind: String
}

struct MuzzaMediaSection: Codable, Identifiable {
    let title: String
    let items: [MuzzaMediaItem]
    var id: String { title }
}

struct MuzzaBridgeResponse: Codable {
    let sections: [MuzzaMediaSection]?
    let items: [MuzzaMediaItem]?
    let chips: [String]?
    let error: String?
}

@MainActor
final class MuzzaPlayer: ObservableObject {
    private var avPlayer: AVPlayer?
    @Published var current: MuzzaMediaItem?
    @Published var isPlaying = false
    @Published var liked: [MuzzaMediaItem]

    init() {
        liked = (try? UserDefaults.standard.data(forKey: "muzza.likedItems").flatMap {
            JSONDecoder().decode([MuzzaMediaItem].self, from: $0)
        }) ?? []
    }

    func play(_ item: MuzzaMediaItem) {
        guard item.kind == "song" else { return }
        current = item
        IosBridgeKt.fetchIosPlayerUrl(videoId: item.id) { [weak self] rawUrl in
            guard let self, let url = URL(string: rawUrl), !rawUrl.isEmpty else { return }
            DispatchQueue.main.async {
                let player = AVPlayer(url: url)
                self.avPlayer = player
                player.play()
                self.isPlaying = true
            }
        }
    }

    func toggle() {
        guard let avPlayer else { return }
        if isPlaying { avPlayer.pause() } else { avPlayer.play() }
        isPlaying.toggle()
    }

    func toggleLike(_ item: MuzzaMediaItem) {
        if liked.contains(where: { $0.id == item.id }) {
            liked.removeAll { $0.id == item.id }
        } else {
            liked.insert(item, at: 0)
        }
        if let data = try? JSONEncoder().encode(liked) {
            UserDefaults.standard.set(data, forKey: "muzza.likedItems")
        }
    }
}

struct ContentView: View {
    @StateObject private var player = MuzzaPlayer()
    @State private var tab = 0
    @State private var searchPresented = false
    @State private var settingsPresented = false
    @AppStorage("muzza.darkTheme") private var darkTheme = true
    @AppStorage("muzza.pureBlack") private var pureBlack = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $tab) {
                HomeView(player: player, searchPresented: $searchPresented, settingsPresented: $settingsPresented)
                    .tag(0).tabItem { Label("Home", systemImage: "house.fill") }
                ExploreView(player: player)
                    .tag(1).tabItem { Label("Explore", systemImage: "safari") }
                LibraryView(player: player)
                    .tag(2).tabItem { Label("Library", systemImage: "music.note.list") }
            }
            .tint(accent)
            .preferredColorScheme(darkTheme ? .dark : .light)

            if player.current != nil {
                MiniPlayer(player: player)
                    .padding(.bottom, 49)
            }
        }
        .background(pureBlack ? Color.black : Color(.systemBackground))
        .sheet(isPresented: $searchPresented) { SearchView(player: player) }
        .sheet(isPresented: $settingsPresented) { SettingsView() }
    }

    private var accent: Color {
        Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255)
    }
}

private struct HomeView: View {
    @ObservedObject var player: MuzzaPlayer
    @Binding var searchPresented: Bool
    @Binding var settingsPresented: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaMediaSection] = []
    @State private var chips: [String] = []
    @State private var selectedChip: String?
    @State private var loading = true
    @State private var error: String?
    @State private var generation = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    HomeHeader(searchPresented: $searchPresented, settingsPresented: $settingsPresented)
                    if loading { HomeSkeleton() }
                    if let error {
                        InnerTubeError(message: error) { generation += 1 }
                    }
                    if !chips.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                Chip(title: "All", selected: selectedChip == nil) { selectedChip = nil }
                                ForEach(chips, id: \.self) { title in
                                    Chip(title: title, selected: selectedChip == title) {
                                        selectedChip = title
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    if !loading && error == nil && sections.isEmpty {
                        Text("No recommendations returned")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 16)
                    }
                    ForEach(sections) { section in
                        MuzzaSection(title: section.title) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(alignment: .top, spacing: 12) {
                                    ForEach(section.items.prefix(24)) { item in
                                        ArtworkCard(item: item) { player.play(item) }
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                }
                .padding(.bottom, 84)
            }
            .toolbar(.hidden, for: .navigationBar)
            .task(id: generation) { load() }
            .refreshable { generation += 1 }
        }
    }

    private func load() {
        loading = true
        error = nil
        IosBridgeKt.fetchIosHome(hideExplicit: hideExplicit) { raw in
            DispatchQueue.main.async {
                loading = false
                guard let data = raw.data(using: .utf8),
                      let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) else {
                    error = "Invalid JSON response from InnerTube."
                    return
                }
                if let message = response.error, !message.isEmpty {
                    error = message
                }
                sections = response.sections ?? []
                chips = response.chips ?? []
            }
        }
    }
}

private struct HomeHeader: View {
    @Binding var searchPresented: Bool
    @Binding var settingsPresented: Bool

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(greeting).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                Text("Muzza").font(.largeTitle.bold())
            }
            Spacer()
            HStack(spacing: 8) {
                Button { searchPresented = true } label: { HeaderButton(systemName: "magnifyingglass") }
                Button { settingsPresented = true } label: { HeaderButton(systemName: "gearshape") }
            }
        }

        private struct HeaderButton: View {
            let systemName: String
            var body: some View {
                Image(systemName: systemName)
                    .font(.headline)
                    .frame(width: 42, height: 42)
                    .background(.ultraThinMaterial)
                    .clipShape(Circle())
            }
        }

        private struct Chip: View {
            let title: String
            let selected: Bool
            let action: () -> Void

            var body: some View {
                Button(action: action) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(selected ? .white : .primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(selected ? Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255) : Color(.secondarySystemFill))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }

        private struct ExploreView: View {
            @ObservedObject var player: MuzzaPlayer
            @State private var sections: [MuzzaMediaSection] = []
            @State private var loading = true
            @State private var error: String?

            var body: some View {
                NavigationStack {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 24) {
                            Text("Explore").font(.largeTitle.bold()).padding(.horizontal, 16).padding(.top, 8)
                            if loading { ProgressView().frame(maxWidth: .infinity) }
                            if let error {
                                Text(error).font(.caption).foregroundStyle(.secondary).padding(.horizontal, 16)
                            }
                            ForEach(sections) { section in
                                MuzzaSection(title: section.title) {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(alignment: .top, spacing: 12) {
                                            ForEach(section.items.prefix(24)) { item in
                                                ArtworkCard(item: item) { player.play(item) }
                                            }
                                        }
                                        .padding(.horizontal, 16)
                                    }
                                }
                            }
                        }
                        .padding(.bottom, 84)
                    }
                    .toolbar(.hidden, for: .navigationBar)
                    .task { load() }
                    .refreshable { load() }
                }
            }

            private func load() {
                loading = true
                error = nil
                IosBridgeKt.fetchIosExplore { raw in
                    DispatchQueue.main.async {
                        loading = false
                        guard let data = raw.data(using: .utf8),
                              let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) else {
                            error = "Explore returned invalid data."
                            return
                        }
                        if let message = response.error, !message.isEmpty { error = message }
                        sections = response.sections ?? []
                    }
                }
            }
        }

        private struct LibraryView: View {
            @ObservedObject var player: MuzzaPlayer
            @State private var category = 0

            var body: some View {
                NavigationStack {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 20) {
                            Text("Library").font(.largeTitle.bold()).padding(.horizontal, 16).padding(.top, 8)
                            Picker("Library", selection: $category) {
                                Text("Playlists").tag(0)
                                Text("Albums").tag(1)
                                Text("Songs").tag(2)
                                Text("Artists").tag(3)
                            }
                            .pickerStyle(.segmented)
                            .padding(.horizontal, 16)

                            LazyVGrid(columns: [
                                GridItem(.flexible(), spacing: 12),
                                GridItem(.flexible(), spacing: 12)
                            ], spacing: 12) {
                                LibraryTile(title: "Liked music", detail: "\(player.liked.count) songs", symbol: "heart.fill", tint: .red)
                                LibraryTile(title: "Downloads", detail: "Offline music", symbol: "arrow.down.circle.fill", tint: .green)
                                LibraryTile(title: "Local", detail: "On this iPhone", symbol: "iphone", tint: .blue)
                                LibraryTile(title: "Most played", detail: "Top tracks", symbol: "chart.bar.fill", tint: .orange)
                                LibraryTile(title: "Recent", detail: "Listening history", symbol: "clock.fill", tint: .purple)
                                LibraryTile(title: "Albums", detail: "Saved albums", symbol: "square.stack.fill", tint: .teal)
                                LibraryTile(title: "Artists", detail: "Saved artists", symbol: "mic.fill", tint: .pink)
                                LibraryTile(title: "Playlists", detail: "Your playlists", symbol: "music.note.list", tint: .indigo)
                            }
                            .padding(.horizontal, 16)

                            if category == 2 {
                                if player.liked.isEmpty {
                                    EmptyLibrary(title: "No liked songs", subtitle: "Tap the heart on the mini-player.")
                                } else {
                                    Text("Liked songs").font(.title3.bold()).padding(.horizontal, 16)
                                    ForEach(player.liked) { item in
                                        LibrarySongRow(item: item) { player.play(item) }
                                            .padding(.horizontal, 16)
                                    }
                                }

                                private struct SearchView: View {
                                    @ObservedObject var player: MuzzaPlayer
                                    @Environment(\.dismiss) private var dismiss
                                    @State private var query = ""
                                    @State private var items: [MuzzaMediaItem] = []
                                    @State private var loading = false
                                    @State private var error: String?

                                    var body: some View {
                                        NavigationStack {
                                            List {
                                                TextField("Search Muzza", text: $query)
                                                    .textInputAutocapitalization(.never)
                                                    .autocorrectionDisabled()
                                                if loading { ProgressView().frame(maxWidth: .infinity) }
                                                if let error { Text(error).font(.caption).foregroundStyle(.secondary) }
                                                ForEach(items) { item in
                                                    Button {
                                                        player.play(item)
                                                        dismiss()
                                                    } label: {
                                                        SearchRow(item: item)
                                                    }
                                                    .buttonStyle(.plain)
                                                }
                                            }
                                            .navigationTitle("Search")
                                            .toolbar {
                                                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                                            }
                                            .onSubmit(of: .text) { search() }
                                            .onChange(of: query) { value in if value.count >= 3 { search() } }
                                        }
                                    }

                                    private func search() {
                                        guard !query.isEmpty else { return }
                                        loading = true
                                        error = nil
                                        IosBridgeKt.searchIosMusic(query: query) { raw in
                                            DispatchQueue.main.async {
                                                loading = false
                                                guard let data = raw.data(using: .utf8),
                                                      let response = try? JSONDecoder().decode(MuzzaBridgeResponse.self, from: data) else {
                                                    error = "Search returned invalid data."
                                                    return
                                                }
                                                if let message = response.error, !message.isEmpty { error = message }
                                                items = response.items ?? []
                                            }
                                        }
                                    }
                                }

                                private struct SettingsView: View {
                                    @Environment(\.dismiss) private var dismiss
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
                                                    Picker("Audio quality", selection: $audioQuality) {
                                                        Text("Auto").tag("Auto")
                                                        Text("High").tag("High")
                                                        Text("Low").tag("Low")
                                                    }
                                                }

                                                private struct InnerTubeError: View {
                                                    let message: String
                                                    let retry: () -> Void
                                                    var body: some View {
                                                        VStack(alignment: .leading, spacing: 8) {
                                                            Label("InnerTube did not return data", systemImage: "exclamationmark.triangle.fill").font(.headline)
                                                            Text(message).font(.caption).foregroundStyle(.secondary)
                                                            Button("Try again", action: retry).buttonStyle(.borderedProminent)
                                                        }
                                                        .padding()
                                                        .frame(maxWidth: .infinity, alignment: .leading)
                                                        .padding(.horizontal, 16)
                                                    }
                                                }

                                                private struct MuzzaSection<Content: View>: View {
                                                    let title: String
                                                    @ViewBuilder let content: Content
                                                    var body: some View {
                                                        VStack(alignment: .leading, spacing: 12) {
                                                            Text(title).font(.title3.bold()).padding(.horizontal, 16)
                                                            content
                                                        }
                                                    }
                                                }

                                                private struct ArtworkCard: View {
                                                    let item: MuzzaMediaItem
                                                    let action: () -> Void
                                                    var body: some View {
                                                        Button(action: action) {
                                                            VStack(alignment: .leading, spacing: 8) {
                                                                AsyncImage(url: URL(string: item.thumbnail)) { phase in
                                                                    if let image = phase.image {
                                                                        image.resizable().scaledToFill()
                                                                    } else {
                                                                        ZStack {
                                                                            RoundedRectangle(cornerRadius: 18).fill(
                                                                                LinearGradient(colors: [
                                                                                    Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255),
                                                                                    Color(red: 70 / 255, green: 20 / 255, blue: 42 / 255)
                                                                                ], startPoint: .topLeading, endPoint: .bottomTrailing)
                                                                            )
                                                                            Image(systemName: "music.note").font(.system(size: 36, weight: .semibold)).foregroundStyle(.white)
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
                                                }

                                                private struct LibraryTile: View {
                                                    let title: String
                                                    let detail: String
                                                    let symbol: String
                                                    let tint: Color
                                                    var body: some View {
                                                        VStack(alignment: .leading, spacing: 14) {
                                                            Image(systemName: symbol).font(.title2).foregroundStyle(tint)
                                                            Spacer()
                                                            VStack(alignment: .leading, spacing: 2) {
                                                                Text(title).font(.headline).lineLimit(1)
                                                                Text(detail).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                                            }
                                                        }
                                                        .frame(maxWidth: .infinity, minHeight: 142, alignment: .leading)
                                                        .padding()
                                                        .background(.ultraThinMaterial)
                                                        .clipShape(RoundedRectangle(cornerRadius: 18))
                                                    }
                                                }

                                                private struct EmptyLibrary: View {
                                                    let title: String
                                                    let subtitle: String
                                                    var body: some View {
                                                        VStack(spacing: 10) {
                                                            Image(systemName: "heart").font(.system(size: 42)).foregroundStyle(.secondary)
                                                            Text(title).font(.headline)
                                                            Text(subtitle).font(.caption).foregroundStyle(.secondary)
                                                        }
                                                        .frame(maxWidth: .infinity)
                                                        .padding(.vertical, 60)
                                                    }
                                                }

                                                private struct LibrarySongRow: View {
                                                    let item: MuzzaMediaItem
                                                    let action: () -> Void
                                                    var body: some View {
                                                        Button(action: action) {
                                                            HStack(spacing: 12) {
                                                                AsyncImage(url: URL(string: item.thumbnail)) { image in
                                                                    image.resizable().scaledToFill()
                                                                } placeholder: {
                                                                    Rectangle().fill(.secondary.opacity(0.12))
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

                                                private struct SearchRow: View {
                                                    let item: MuzzaMediaItem
                                                    var body: some View {
                                                        HStack(spacing: 12) {
                                                            AsyncImage(url: URL(string: item.thumbnail)) { image in
                                                                image.resizable().scaledToFill()
                                                            } placeholder: {
                                                                Rectangle().fill(.secondary.opacity(0.12))
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
                                                }

                                                private struct MiniPlayer: View {
                                                    @ObservedObject var player: MuzzaPlayer
                                                    var body: some View {
                                                        HStack(spacing: 10) {
                                                            AsyncImage(url: URL(string: player.current?.thumbnail ?? "")) { image in
                                                                image.resizable().scaledToFill()
                                                            } placeholder: {
                                                                RoundedRectangle(cornerRadius: 8).fill(.secondary.opacity(0.12))
                                                            }
                                                            .frame(width: 44, height: 44)
                                                            .clipShape(RoundedRectangle(cornerRadius: 8))
                                                            VStack(alignment: .leading, spacing: 2) {
                                                                Text(player.current?.title ?? "").font(.subheadline.weight(.semibold)).lineLimit(1)
                                                                Text(player.current?.subtitle ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                                            }
                                                            Spacer()
                                                            if let item = player.current {
                                                                Button {
                                                                    player.toggleLike(item)
                                                                } label: {
                                                                    Image(systemName: player.liked.contains(where: { $0.id == item.id }) ? "heart.fill" : "heart")
                                                                        .foregroundStyle(player.liked.contains(where: { $0.id == item.id }) ? Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255) : .secondary)
                                                                }
                                                                .buttonStyle(.plain)
                                                            }
                                                            Button { player.toggle() } label: {
                                                                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                                            }
                                                            .buttonStyle(.plain)
                                                        }
                                                        .padding(10)
                                                        .background(.regularMaterial)
                                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                                        .padding(.horizontal)
                                                    }
                                                }

                                                private struct HomeSkeleton: View {
                                                    var body: some View {
                                                        VStack(alignment: .leading, spacing: 16) {
                                                            ForEach(0..<3, id: \.self) { _ in
                                                                VStack(alignment: .leading, spacing: 10) {
                                                                    RoundedRectangle(cornerRadius: 6).fill(.secondary.opacity(0.14)).frame(width: 150, height: 18).padding(.horizontal, 16)
                                                                    ScrollView(.horizontal, showsIndicators: false) {
                                                                        HStack(spacing: 12) {
                                                                            ForEach(0..<4, id: \.self) { _ in
                                                                                RoundedRectangle(cornerRadius: 18).fill(.secondary.opacity(0.12)).frame(width: 156, height: 156)
                                                                            }
                                                                        }
                                                                        .padding(.horizontal, 16)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                Section("Playback") {
                                                    Toggle("Persistent queue", isOn: $persistentQueue)
                                                }
                                                Section("About") {
                                                    HStack { Text("App"); Spacer(); Text("Muzza").foregroundStyle(.secondary) }
                                                    HStack { Text("Platform"); Spacer(); Text("iOS").foregroundStyle(.secondary) }
                                                }
                                            }
                                            .navigationTitle("Settings")
                                            .toolbar {
                                                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.bottom, 84)
                    }
                    .toolbar(.hidden, for: .navigationBar)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 6...11: return "Good morning"
        case 12...17: return "Good afternoon"
        case 18...23: return "Good evening"
        default: return "Good night"
        }
    }
}
