import SwiftUI

struct MuzzaPageBackground: ViewModifier {
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.background(pureBlack && colorScheme == .dark ? Color.black : Color(.systemBackground))
    }
}

struct MuzzaHeader: View {
    let title: String
    let search: () -> Void
    let settings: () -> Void

    init(_ title: String, search: @escaping () -> Void, settings: @escaping () -> Void) {
        self.title = title
        self.search = search
        self.settings = settings
    }

    var body: some View {
        HStack(spacing: 20) {
            Text(LocalizedStringKey(title)).font(.largeTitle.bold())
            Spacer()
            Button(action: search) { Image(systemName: "magnifyingglass").font(.title3) }
                .accessibilityLabel("Search")
            Button(action: settings) { Image(systemName: "gearshape").font(.title3) }
                .accessibilityLabel("Settings")
        }
        .padding(.bottom, 8)
    }
}

struct MuzzaArtwork: View {
    let item: MuzzaLiveItem
    let size: CGFloat

    var body: some View {
        Group {
            if let url = URL(string: item.thumbnail), url.scheme?.lowercased() == "https" {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: max(8, size * 0.06)))
    }

    private var placeholder: some View {
        Rectangle()
            .fill(Color(.secondarySystemFill))
            .overlay {
                Image(systemName: item.kind == "artist" ? "person.fill" : "music.note")
                    .font(.system(size: max(16, size * 0.24)))
                    .foregroundStyle(.secondary)
            }
    }
}

struct MuzzaMediaSectionView: View {
    let section: MuzzaLiveSection
    @ObservedObject var playback: MuzzaPlayback

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !section.title.isEmpty {
                Text(LocalizedStringKey(section.title)).font(.title2.bold())
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(Array(section.items.enumerated()), id: \.offset) { _, item in
                        if item.kind == "genre" {
                            NavigationLink(value: item) {
                                Text(item.title).font(.subheadline.weight(.semibold))
                                    .frame(width: 156, height: 62, alignment: .leading)
                                    .padding(.horizontal, 12)
                                    .background(Color(.secondarySystemFill))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        } else if item.kind == "song" {
                            Button { playback.play(item, in: section.items) } label: {
                                MuzzaMediaCard(item: item)
                            }
                            .buttonStyle(.plain)
                        } else {
                            NavigationLink(value: item) { MuzzaMediaCard(item: item) }
                                .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}

private struct MuzzaMediaCard: View {
    let item: MuzzaLiveItem

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            MuzzaArtwork(item: item, size: 150)
                .overlay(alignment: .bottomTrailing) {
                    if item.kind == "song" {
                        Image(systemName: "play.fill")
                            .font(.caption.bold()).foregroundStyle(.white)
                            .padding(9).background(.black.opacity(0.65)).clipShape(Circle())
                            .padding(6)
                    }
                }
            Text(item.title).font(.subheadline.bold()).lineLimit(2)
            if !item.subtitle.isEmpty {
                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
        }
        .frame(width: 150, alignment: .leading)
    }
}

struct MuzzaSongRow: View {
    let item: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback
    let queue: [MuzzaLiveItem]
    var queueIndex: Int? = nil
    var playlist: MuzzaLiveItem? = nil
    var onPlaylistChange: (([MuzzaLiveItem]) -> Void)? = nil
    @State private var duplicatePlaylist: MuzzaLiveItem?
    @State private var showDuplicate = false

    init(
        item: MuzzaLiveItem,
        playback: MuzzaPlayback,
        queue: [MuzzaLiveItem],
        queueIndex: Int? = nil,
        playlist: MuzzaLiveItem? = nil,
        onPlaylistChange: (([MuzzaLiveItem]) -> Void)? = nil
    ) {
        self.item = item
        self.playback = playback
        self.queue = queue
        self.queueIndex = queueIndex
        self.playlist = playlist
        self.onPlaylistChange = onPlaylistChange
    }

    var body: some View {
        HStack(spacing: 8) {
            Button { playback.play(item, in: queue, startingAt: queueIndex) } label: {
                HStack(spacing: 12) {
                    MuzzaArtwork(item: item, size: 54)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Menu {
                Button {
                    playback.toggleLike(item)
                } label: {
                    Label {
                        Text(LocalizedStringKey(playback.isLiked(item) ? "Remove like" : "Like"))
                    } icon: {
                        Image(systemName: playback.isLiked(item) ? "heart.slash" : "heart")
                    }
                }
                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)

                let localPlaylists = playback.playlists.filter { $0.kind == "localPlaylist" }
                if !localPlaylists.isEmpty {
                    Menu("Add to playlist") {
                        ForEach(localPlaylists) { target in
                            Button(target.title) { add(to: target, allowDuplicate: false) }
                        }
                    }
                    .disabled(playback.isLibraryBusy)
                }
                if let playlist {
                    Button(role: .destructive) {
                        Task {
                            do {
                                let updated = try await playback.remove(item, from: playlist)
                                onPlaylistChange?(updated)
                            } catch {
                                playback.message = error.localizedDescription
                            }
                        }
                    } label: {
                        Label("Remove from playlist", systemImage: "minus.circle")
                    }
                    .disabled(playback.isLibraryBusy)
                }
            } label: {
                Image(systemName: "ellipsis").frame(width: 36, height: 44)
            }
            .accessibilityLabel("Song actions")
        }
        .confirmationDialog("This song is already in the playlist.", isPresented: $showDuplicate, titleVisibility: .visible) {
            if let target = duplicatePlaylist {
                Button("Add anyway") { add(to: target, allowDuplicate: true) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func add(to playlist: MuzzaLiveItem, allowDuplicate: Bool) {
        Task {
            do {
                try await playback.add(item, to: playlist, allowDuplicate: allowDuplicate)
            } catch MuzzaAPIError.duplicateSong {
                duplicatePlaylist = playlist
                showDuplicate = true
            } catch {
                playback.message = error.localizedDescription
            }
        }
    }
}

struct MuzzaErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Could not load music", systemImage: "exclamationmark.triangle")
                .font(.headline)
            Text(message).font(.footnote).foregroundStyle(.secondary).textSelection(.enabled)
            Button("Try again", action: retry).buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 16)
    }
}

struct MuzzaEmptyView: View {
    let symbol: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: symbol).font(.largeTitle).foregroundStyle(.secondary)
            Text(LocalizedStringKey(message)).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 50)
    }
}
