import SwiftUI

struct MuzzaPlaylistEditorView: View {
    @Environment(\.dismiss) private var dismiss
    let playlist: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback
    let onSave: ([MuzzaLiveItem]) -> Void
    @State private var items: [MuzzaLiveItem]
    @State private var isSaving = false
    @State private var saveError: String?

    init(
        playlist: MuzzaLiveItem,
        playback: MuzzaPlayback,
        items: [MuzzaLiveItem],
        onSave: @escaping ([MuzzaLiveItem]) -> Void
    ) {
        self.playlist = playlist
        _playback = ObservedObject(wrappedValue: playback)
        _items = State(initialValue: items)
        self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(items, id: \.entryId) { item in
                    HStack(spacing: 12) {
                        MuzzaArtwork(item: item, size: 48)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                }
                .onMove { items.move(fromOffsets: $0, toOffset: $1) }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("Edit playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        isSaving = true
                        Task {
                            do {
                                let updated = try await playback.reorder(playlist, items: items)
                                onSave(updated)
                                dismiss()
                            } catch {
                                saveError = error.localizedDescription
                                isSaving = false
                            }
                        }
                    }
                    .disabled(isSaving)
                }
            }
            .alert("Could not save playlist", isPresented: Binding(
                get: { saveError != nil },
                set: { if !$0 { saveError = nil } }
            )) {
                Button("OK", role: .cancel) { saveError = nil }
            } message: {
                Text(saveError ?? "")
            }
        }
        .interactiveDismissDisabled(isSaving)
    }
}
