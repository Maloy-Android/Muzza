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
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Text("Lyrics from LRCLIB or KuGou")
                    .font(.caption).foregroundStyle(.secondary).padding(8)
            }
            .task(id: playback.current?.id) { await load() }
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
}
