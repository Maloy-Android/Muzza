import SwiftUI
import Shared

struct MuzzaLiveItem: Codable, Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let thumbnail: String
    let kind: String
}

struct MuzzaLiveSection: Codable, Identifiable {
    let title: String
    let items: [MuzzaLiveItem]
    var id: String { title }
}

struct MuzzaLiveResponse: Codable {
    let sections: [MuzzaLiveSection]?
    let items: [MuzzaLiveItem]?
    let chips: [String]?
    let error: String?
}

struct ContentView: View {
    @State private var selectedTab = 0
    @State private var showSearch = false
    @State private var showSettings = false
    @AppStorage("muzza.darkTheme") private var darkTheme = true
    @AppStorage("muzza.pureBlack") private var pureBlack = false

    var body: some View {
        TabView(selection: $selectedTab) {
            MuzzaHomeView(showSearch: $showSearch, showSettings: $showSettings)
                .tag(0).tabItem { Label("Home", systemImage: "house.fill") }
            MuzzaExploreView().tag(1).tabItem { Label("Explore", systemImage: "safari") }
            MuzzaLibraryView().tag(2).tabItem { Label("Library", systemImage: "music.note.list") }
        }
        .tint(accent)
        .preferredColorScheme(darkTheme ? .dark : .light)
        .background(pureBlack ? Color.black : Color(.systemBackground))
        .sheet(isPresented: $showSearch) { MuzzaSearchView() }
        .sheet(isPresented: $showSettings) { MuzzaSettingsView() }
    }

    private var accent: Color {
        Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255)
    }
}

private struct MuzzaHomeView: View {
    @Binding var showSearch: Bool
    @Binding var showSettings: Bool
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var sections: [MuzzaLiveSection] = []
    @State private var chips: [String] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var generation = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Good evening").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
                            Text("Muzza").font(.largeTitle.bold())
                        }
                        Spacer()
                        HStack(spacing: 8) {
                            Button { showSearch = true } label: { MuzzaCircleButton("magnifyingglass") }
                            Button { showSettings = true } label: { MuzzaCircleButton("gearshape") }
                        }
                    }

                    if isLoading {
                        MuzzaLoadingRows()
                    }

                    if let loadError {
                        MuzzaInnerTubeError(message: loadError) {
                            generation += 1
                        }
                    }

                    if !chips.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(chips, id: \.self) { chip in
                                    Text(chip)
                                        .font(.subheadline.weight(.semibold))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 9)
                                        .background(Color(.secondarySystemFill))
                                        .clipShape(Capsule())
                                }
                            }.padding(.horizontal, 16)
                        }
                    }

                    ForEach(sections) { section in
                        MuzzaHomeSection(title: section.title) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(alignment: .top, spacing: 12) {
                                    ForEach(section.items.prefix(24)) { item in
                                        MuzzaLiveCard(item: item)
                                    }
                                }.padding(.horizontal, 16)
                            }
                        }
                    }

                    MuzzaHomeSection(title: "Quick picks") {
                        MuzzaHorizontalCards(items: [
                            MuzzaPreview(title: "Liked music", subtitle: "Songs you saved", symbol: "heart.fill"),
                            MuzzaPreview(title: "Daily mix", subtitle: "Made for you", symbol: "sparkles"),
                            MuzzaPreview(title: "Recently played", subtitle: "Pick up where you left off", symbol: "clock.fill")
                        ])
                    }
                    MuzzaHomeSection(title: "Forgotten favorites") {
                        MuzzaHorizontalCards(items: [
                            MuzzaPreview(title: "Rediscover", subtitle: "Tracks worth hearing again", symbol: "arrow.clockwise"),
                            MuzzaPreview(title: "Mix 1", subtitle: "Muzza selection", symbol: "music.note"),
                            MuzzaPreview(title: "Mix 2", subtitle: "Muzza selection", symbol: "music.quarternote.3")
                        ])
                    }
                    MuzzaHomeSection(title: "Keep listening") {
                        MuzzaHorizontalCards(items: [
                            MuzzaPreview(title: "Continue", subtitle: "Your last queue", symbol: "play.fill"),
                            MuzzaPreview(title: "Downloads", subtitle: "Available offline", symbol: "arrow.down.circle.fill"),
                            MuzzaPreview(title: "History", subtitle: "Recently listened", symbol: "clock.arrow.circlepath")
                        ])
                    }
                }.padding().padding(.bottom, 84)
            }.toolbar(.hidden, for: .navigationBar)
                .task(id: generation) { loadHome() }
                .refreshable { generation += 1 }
        }
    }

    private func loadHome() {
        isLoading = true
        loadError = nil
        IosBridgeKt.fetchIosHome(hideExplicit: hideExplicit) { raw in
            DispatchQueue.main.async {
                isLoading = false
                guard let data = raw.data(using: .utf8),
                      let response = try? JSONDecoder().decode(MuzzaLiveResponse.self, from: data) else {
                    loadError = "The InnerTube bridge returned invalid JSON."
                    return
                }
                if let message = response.error, !message.isEmpty {
                    loadError = message
                }
                sections = response.sections ?? []
                chips = response.chips ?? []
            }
        }
    }
}

private struct MuzzaExploreView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Explore").font(.largeTitle.bold())
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        MuzzaLibraryTile(title: "Moods", detail: "Find a feeling", symbol: "face.smiling", tint: .pink)
                        MuzzaLibraryTile(title: "Genres", detail: "Browse styles", symbol: "guitars", tint: .orange)
                        MuzzaLibraryTile(title: "New releases", detail: "Latest music", symbol: "sparkles", tint: .red)
                        MuzzaLibraryTile(title: "Charts", detail: "Popular now", symbol: "chart.line.uptrend.xyaxis", tint: .purple)
                    }
                }.padding().padding(.bottom, 84)
            }.toolbar(.hidden, for: .navigationBar)
        }
    }
}

private struct MuzzaLibraryView: View {
    @State private var category = 0
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Library").font(.largeTitle.bold())
                    Picker("Library", selection: $category) {
                        Text("Playlists").tag(0)
                        Text("Albums").tag(1)
                        Text("Songs").tag(2)
                        Text("Artists").tag(3)
                    }.pickerStyle(.segmented)
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        MuzzaLibraryTile(title: "Liked music", detail: "0 songs", symbol: "heart.fill", tint: .red)
                        MuzzaLibraryTile(title: "Downloads", detail: "Offline music", symbol: "arrow.down.circle.fill", tint: .green)
                        MuzzaLibraryTile(title: "Local", detail: "On this iPhone", symbol: "iphone", tint: .blue)
                        MuzzaLibraryTile(title: "Most played", detail: "Top tracks", symbol: "chart.bar.fill", tint: .orange)
                        MuzzaLibraryTile(title: "Recent", detail: "Listening history", symbol: "clock.fill", tint: .purple)
                        MuzzaLibraryTile(title: "Albums", detail: "Saved albums", symbol: "square.stack.fill", tint: .teal)
                        MuzzaLibraryTile(title: "Artists", detail: "Saved artists", symbol: "mic.fill", tint: .pink)
                        MuzzaLibraryTile(title: "Playlists", detail: "Your playlists", symbol: "music.note.list", tint: .indigo)
                    }
                }.padding().padding(.bottom, 84)
            }.toolbar(.hidden, for: .navigationBar)
        }
    }
}

private struct MuzzaSearchView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    var body: some View {
        NavigationStack {
            List {
                TextField("Search Muzza", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .navigationTitle("Search")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

private struct MuzzaSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("muzza.darkTheme") private var darkTheme = true
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @AppStorage("muzza.persistentQueue") private var persistentQueue = true
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
                    Toggle("Persistent queue", isOn: $persistentQueue)
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct MuzzaCircleButton: View {
    let symbol: String
    init(_ symbol: String) { self.symbol = symbol }
    var body: some View {
        Image(systemName: symbol).font(.headline).frame(width: 42, height: 42)
            .background(.ultraThinMaterial).clipShape(Circle())
    }
}

private struct MuzzaHomeSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title3.bold()).padding(.horizontal, 16)
            content
        }
    }
}

private struct MuzzaPreview: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let symbol: String
}

private struct MuzzaHorizontalCards: View {
    let items: [MuzzaPreview]
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 18).fill(
                                LinearGradient(colors: [
                                    Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255),
                                    Color(red: 70 / 255, green: 20 / 255, blue: 42 / 255)
                                ], startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                            Image(systemName: item.symbol).font(.system(size: 36, weight: .semibold)).foregroundStyle(.white)
                        }.frame(width: 156, height: 156)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title).font(.headline).lineLimit(1)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }.frame(width: 156, alignment: .leading)
                    }
                }
            }.padding(.horizontal, 16)
        }
    }
}

private struct MuzzaLibraryTile: View {
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
        }.frame(maxWidth: .infinity, minHeight: 142, alignment: .leading).padding()
            .background(.ultraThinMaterial).clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct MuzzaLoadingRows: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 10) {
                    RoundedRectangle(cornerRadius: 6).fill(.secondary.opacity(0.14))
                        .frame(width: 150, height: 18).padding(.horizontal, 16)
                    HStack(spacing: 12) {
                        ForEach(0..<4, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 18).fill(.secondary.opacity(0.12))
                                .frame(width: 156, height: 156)
                        }
                    }.padding(.horizontal, 16)
                }
            }
        }
    }
}

private struct MuzzaInnerTubeError: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("InnerTube did not return data", systemImage: "exclamationmark.triangle.fill").font(.headline)
            Text(message).font(.caption).foregroundStyle(.secondary)
            Button("Try again", action: retry).buttonStyle(.borderedProminent)
        }.padding().frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16)
    }
}

private struct MuzzaLiveCard: View {
    let item: MuzzaLiveItem
    var body: some View {
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
                        Image(systemName: "music.note").foregroundStyle(.white)
                    }
                }
            }.frame(width: 156, height: 156).clipShape(RoundedRectangle(cornerRadius: 18))
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).font(.headline).lineLimit(1)
                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }.frame(width: 156, alignment: .leading)
        }
    }
}
