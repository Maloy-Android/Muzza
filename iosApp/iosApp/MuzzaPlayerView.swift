import SwiftUI

struct MuzzaMiniPlayer: View {
    @ObservedObject var playback: MuzzaPlayback
    let open: () -> Void

    var body: some View {
        if let item = playback.current {
            HStack(spacing: 10) {
                Button(action: open) {
                    HStack(spacing: 10) {
                        MuzzaArtwork(item: item, size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Button { playback.toggleLike(item) } label: {
                    Image(systemName: playback.isLiked(item) ? "heart.fill" : "heart")
                }
                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                .accessibilityLabel(playback.isLiked(item) ? "Remove like" : "Like")
                Button { playback.toggle() } label: {
                    if playback.isLoadingAudio {
                        ProgressView().frame(width: 24, height: 24)
                    } else {
                        Image(systemName: playback.isPlaying ? "pause.fill" : "play.fill")
                            .frame(width: 24, height: 24)
                    }
                }
                .accessibilityLabel(playback.isPlaying ? "Pause" : "Play")
            }
            .padding(10).background(.regularMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 16)).padding(.horizontal)
        }
    }
}

struct MuzzaPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @State private var showQueue = false

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 28) {
                    HStack {
                        Button { dismiss() } label: { Image(systemName: "chevron.down").font(.title3.bold()) }
                            .accessibilityLabel("Close player")
                        Spacer()
                        Text("Now playing").font(.headline)
                        Spacer()
                        Button { showQueue = true } label: { Image(systemName: "list.bullet").font(.title3) }
                            .accessibilityLabel("Queue")
                    }
                    if let item = playback.current {
                        MuzzaArtwork(item: item, size: max(0, min(geometry.size.width - 48, 420)))
                            .padding(.vertical, 12)
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.title2.bold()).lineLimit(2)
                                Text(item.subtitle).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Button { playback.toggleLike(item) } label: {
                                Image(systemName: playback.isLiked(item) ? "heart.fill" : "heart")
                                    .font(.title2)
                            }
                            .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                            .accessibilityLabel(playback.isLiked(item) ? "Remove like" : "Like")
                        }
                        VStack(spacing: 6) {
                            Slider(
                                value: Binding(
                                    get: { min(playback.position, max(playback.duration, 1)) },
                                    set: { playback.seek(to: $0) }
                                ),
                                in: 0...max(playback.duration, 1)
                            )
                            .disabled(playback.duration <= 0)
                            HStack {
                                Text(timeText(playback.position))
                                Spacer()
                                Text(timeText(playback.duration))
                            }
                            .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                        HStack(spacing: 42) {
                            Button { playback.previous() } label: {
                                Image(systemName: "backward.end.fill").font(.title2)
                            }
                            .accessibilityLabel("Previous")
                            Button { playback.toggle() } label: {
                                Group {
                                    if playback.isLoadingAudio {
                                        ProgressView()
                                    } else {
                                        Image(systemName: playback.isPlaying ? "pause.fill" : "play.fill")
                                            .font(.title)
                                    }
                                }
                                .frame(width: 72, height: 72)
                                .background(.regularMaterial).clipShape(Circle())
                            }
                            .accessibilityLabel(playback.isPlaying ? "Pause" : "Play")
                            Button { playback.next() } label: {
                                Image(systemName: "forward.end.fill").font(.title2)
                            }
                            .disabled(playback.queueIndex + 1 >= playback.queue.count)
                            .accessibilityLabel("Next")
                        }
                        Button { showQueue = true } label: {
                            Label("Queue", systemImage: "list.bullet")
                        }
                        .buttonStyle(.bordered)
                    } else {
                        MuzzaEmptyView(symbol: "music.note", message: "No song selected.")
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height, alignment: .top)
            }
            .background(
                LinearGradient(
                    colors: [Color(.secondarySystemBackground), Color(.systemBackground)],
                    startPoint: .top, endPoint: .bottom
                )
            )
        }
        .sheet(isPresented: $showQueue) { MuzzaQueueView(playback: playback) }
    }

    private func timeText(_ value: Double) -> String {
        guard value.isFinite, value > 0 else { return "0:00" }
        let seconds = Int(value)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

private struct MuzzaQueueView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(playback.queue.enumerated()), id: \.offset) { index, item in
                    HStack {
                        MuzzaSongRow(item: item, playback: playback, queue: playback.queue, queueIndex: index)
                        if index == playback.queueIndex {
                            Image(systemName: "speaker.wave.2.fill").foregroundStyle(Color.accentColor)
                        }
                    }
                }
            }
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct MuzzaSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
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
                        .onChange(of: persistentQueue) { playback.queuePreferenceChanged($0) }
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
