import SwiftUI
import Foundation

struct ContentView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tag(0)
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }

            LibraryView()
                .tag(1)
                .tabItem {
                    Label("Library", systemImage: "music.note.list")
                }

            ExploreView()
                .tag(2)
                .tabItem {
                    Label("Explore", systemImage: "safari")
                }

            SettingsView()
                .tag(3)
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
        }
        .tint(Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255))
        .preferredColorScheme(.dark)
    }
}

private struct HomeView: View {
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
                        Button {} label: {
                            Image(systemName: "magnifyingglass")
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(.primary)
                                .frame(width: 44, height: 44)
                                .background(.ultraThinMaterial)
                                .clipShape(Circle())
                        }
                    }

                    MuzzaSection(title: "Quick picks") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                MuzzaCard(title: "Liked music", subtitle: "Songs you saved", symbol: "heart.fill")
                                MuzzaCard(title: "Daily mix", subtitle: "Made for you", symbol: "sparkles")
                                MuzzaCard(title: "Recently played", subtitle: "Pick up where you left off", symbol: "clock.fill")
                            }
                        }
                    }

                    MuzzaSection(title: "Forgotten favorites") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                MuzzaCard(title: "Rediscover", subtitle: "Tracks worth hearing again", symbol: "arrow.clockwise")
                                MuzzaCard(title: "Mix 1", subtitle: "Muzza selection", symbol: "music.note")
                                MuzzaCard(title: "Mix 2", subtitle: "Muzza selection", symbol: "music.quarternote.3")
                            }
                        }
                    }

                    MuzzaSection(title: "Keep listening") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                MuzzaCard(title: "Continue", subtitle: "Your last queue", symbol: "play.fill")
                                MuzzaCard(title: "Downloads", subtitle: "Available offline", symbol: "arrow.down.circle.fill")
                                MuzzaCard(title: "History", subtitle: "Recently listened", symbol: "clock.arrow.circlepath")
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Muzza")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
        }
    }
}

private struct LibraryView: View {
    @State private var category = 0

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
                .padding()
            }
            .navigationTitle("Library")
        }
    }
}

private struct ExploreView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Explore")
                        .font(.largeTitle.bold())

                    LazyVGrid(columns: [
                        GridItem(.flexible(), spacing: 12),
                        GridItem(.flexible(), spacing: 12)
                    ], spacing: 12) {
                        ExploreCard(title: "Moods", symbol: "face.smiling")
                        ExploreCard(title: "Genres", symbol: "guitars")
                        ExploreCard(title: "New releases", symbol: "sparkles")
                        ExploreCard(title: "Charts", symbol: "chart.line.uptrend.xyaxis")
                    }
                }
                .padding()
            }
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct SettingsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Appearance") {
                    Label("Dark theme", systemImage: "moon.fill")
                    Label("Pure black", systemImage: "circle.grid.cross")
                }
                Section("Content") {
                    Label("Explicit content", systemImage: "e.square")
                    Label("Content language", systemImage: "globe")
                }
                Section("Playback") {
                    Label("Audio quality", systemImage: "waveform")
                    Label("Persistent queue", systemImage: "list.bullet")
                }
                Section("About") {
                    Label("Muzza", systemImage: "music.note")
                    Label("iOS startup recovery", systemImage: "checkmark.shield")
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle("Settings")
        }
    }
}

private struct MuzzaSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title)
                    .font(.title3.bold())
                Spacer()
            }
            content
        }
    }
}

private struct MuzzaCard: View {
    let title: String
    let subtitle: String
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 18)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255).opacity(0.95),
                                Color(red: 80 / 255, green: 20 / 255, blue: 48 / 255)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                Image(systemName: symbol)
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 156, height: 156)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .frame(width: 156, alignment: .leading)
        }
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
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                Text(count)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .leading)
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct ExploreCard: View {
    let title: String
    let symbol: String

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Image(systemName: symbol)
                .font(.title)
                .foregroundStyle(.white)
            Spacer()
            Text(title)
                .font(.headline)
        }
        .frame(maxWidth: .infinity, minHeight: 150, alignment: .leading)
        .padding()
        .background(
            LinearGradient(
                colors: [
                    Color(red: 237 / 255, green: 85 / 255, blue: 100 / 255).opacity(0.85),
                    Color(red: 70 / 255, green: 20 / 255, blue: 42 / 255)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}
