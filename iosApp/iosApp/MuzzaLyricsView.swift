import SwiftUI

struct MuzzaLyricsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @State private var rawLyrics = ""
    @State private var lines: [MuzzaLyricLine] = []
    @State private var activeLine: Int?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var requestID = UUID()
    @State private var showLyricsEditor = false
    @State private var editedLyrics = ""

    init(playback: MuzzaPlayback) {
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let loadError {
                    MuzzaErrorView(message: loadError) { Task { await load() } }.padding()
                } else if lines.isEmpty {
                    ScrollView {
                        Text(rawLyrics).frame(maxWidth: .infinity, alignment: .leading).padding()
                    }
                } else {
                    ScrollViewReader { proxy in
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 22) {
                                ForEach(lines) { line in
                                    Button { playback.seek(to: line.time) } label: {
                                        Text(line.text.isEmpty ? "♪" : line.text)
                                            .font(.title2.weight(activeLine == line.id ? .bold : .medium))
                                            .foregroundStyle(activeLine == line.id ? Color.primary : Color.secondary)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .buttonStyle(.plain)
                                    .id(line.id)
                                }
                            }
                            .padding(24).padding(.vertical, 40)
                        }
                        .onChange(of: playback.position) { _ in updateActiveLine(proxy) }
                        .onAppear { updateActiveLine(proxy) }
                    }
                }
            }
            .modifier(MuzzaPageBackground())
            .navigationTitle("Lyrics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if playback.current != nil {
                        Button {
                            editedLyrics = rawLyrics
                            showLyricsEditor = true
                        } label: {
                            Image(systemName: "pencil")
                        }
                        .accessibilityLabel("Edit or add lyrics")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Text("Lyrics from LRCLIB or KuGou")
                    .font(.caption).foregroundStyle(.secondary).padding(8)
            }
            .task(id: playback.current?.id) { await load() }
            .sheet(isPresented: $showLyricsEditor) {
                NavigationStack {
                    TextEditor(text: $editedLyrics)
                        .padding()
                        .navigationTitle("Edit Lyrics")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Cancel") { showLyricsEditor = false }
                            }
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Save") {
                                    saveManualLyrics()
                                    showLyricsEditor = false
                                }
                            }
                        }
                }
            }
        }
    }

    private func load() async {
        let id = UUID()
        requestID = id
        isLoading = true
        loadError = nil
        rawLyrics = ""
        lines = []
        activeLine = nil
        defer { if requestID == id { isLoading = false } }
        guard let item = playback.current else {
            loadError = "No song selected."
            return
        }
        let cacheKey = "muzza.lyrics.\(item.id)"
        if let cached = UserDefaults.standard.string(forKey: cacheKey) {
            let parsed = try? MuzzaLyricsParser.parse(cached)
            rawLyrics = cached
            lines = parsed ?? []
            if !cached.isEmpty {
                defer { if requestID == id { isLoading = false } }
                return
            }
        }
        do {
            let text = try await MuzzaAPI.lyrics(for: item, duration: playback.duration)
            let parsed = try MuzzaLyricsParser.parse(text)
            guard !Task.isCancelled, requestID == id, playback.current?.id == item.id else { return }
            rawLyrics = text
            lines = parsed
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            loadError = error.localizedDescription
        }
    }

    private func updateActiveLine(_ proxy: ScrollViewProxy) {
        let next = lines.lastIndex { $0.time <= playback.position }
        guard next != activeLine else { return }
        activeLine = next
        if let next {
            withAnimation(.easeInOut(duration: 0.25)) {
                proxy.scrollTo(lines[next].id, anchor: .center)
            }
        }
    }

    private func saveManualLyrics() {
        guard let item = playback.current else { return }
        let text = editedLyrics.trimmingCharacters(in: .whitespacesAndNewlines)
        let cacheKey = "muzza.lyrics.\(item.id)"
        if text.isEmpty {
            UserDefaults.standard.removeObject(forKey: cacheKey)
            rawLyrics = ""
            lines = []
        } else {
            UserDefaults.standard.set(text, forKey: cacheKey)
            rawLyrics = text
            lines = (try? MuzzaLyricsParser.parse(text)) ?? []
        }
    }
}
