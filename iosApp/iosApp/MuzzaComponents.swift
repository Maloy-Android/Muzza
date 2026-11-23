import SwiftUI

struct MuzzaPageBackground: ViewModifier {
    @AppStorage("muzza.pureBlack") private var pureBlack = false
    @Environment(\.colorScheme) private var colorScheme

    init() {}

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
        HStack(spacing: 12) {
            Text(LocalizedStringKey(title))
                .font(.system(size: 29, weight: .bold))
                .lineLimit(1)
            Spacer()
            Button(action: search) {
                Image(systemName: "magnifyingglass")
                    .font(.title3.weight(.semibold))
                    .frame(width: 38, height: 38)
                    .background(Color(.secondarySystemFill))
                    .clipShape(Circle())
            }
                .accessibilityLabel("Search")
            Button(action: settings) {
                Image(systemName: "gearshape")
                    .font(.title3.weight(.semibold))
                    .frame(width: 38, height: 38)
                    .background(Color(.secondarySystemFill))
                    .clipShape(Circle())
            }
                .accessibilityLabel("Settings")
        }
        .padding(.bottom, 6)
    }
}

struct MuzzaArtwork: View {
    let item: MuzzaLiveItem
    let size: CGFloat
    var aspectRatio: CGFloat = 1
    @State private var modelImage: UIImage?
    private static let imageCache = NSCache<NSURL, UIImage>()

    var body: some View {
        Group {
            if let modelImage {
                Image(uiImage: modelImage).resizable().scaledToFill()
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size / max(aspectRatio, 0.1))
        .modifier(MuzzaArtworkClip(kind: item.kind, size: size))
        .task(id: item.thumbnail) {
            modelImage = nil
            let rawURL = item.thumbnail.hasPrefix("//") ? "https:\(item.thumbnail)" : item.thumbnail
            guard var components = URLComponents(string: rawURL),
                  ["https", "http"].contains(components.scheme?.lowercased() ?? "") else { return }
            components.scheme = "https"
            guard let url = components.url else { return }
            let key = url as NSURL
            if let cached = Self.imageCache.object(forKey: key) {
                modelImage = cached
            } else {
                await loadImage(from: url, cacheKey: key)
            }
        }
    }

    private func loadImage(from url: URL, cacheKey: NSURL) async {
        do {
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            request.setValue("image/avif,image/webp,image/*,*/*;q=0.8", forHTTPHeaderField: "Accept")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw MuzzaAPIError.requestFailed("The artwork image could not be loaded.")
            }
            guard !Task.isCancelled, let image = UIImage(data: data) else { return }
            Self.imageCache.setObject(image, forKey: cacheKey)
            modelImage = image
        } catch {
            if !Task.isCancelled { modelImage = nil }
        }
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

private struct MuzzaArtworkClip: ViewModifier {
    let kind: String
    let size: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if kind == "artist" {
            content.clipShape(Circle())
        } else {
            content.clipShape(RoundedRectangle(cornerRadius: max(8, size * 0.06)))
        }
    }
}

private func stripeColor(_ value: Int64?) -> Color {
    guard let value else { return .accentColor }
    let argb = UInt32(truncatingIfNeeded: value)
    return Color(
        red: Double((argb >> 16) & 0xFF) / 255,
        green: Double((argb >> 8) & 0xFF) / 255,
        blue: Double(argb & 0xFF) / 255
    )
}

struct MuzzaPlayerSlider: View {
    @ObservedObject var playback: MuzzaPlayback
    @ObservedObject private var timeline: MuzzaPlaybackProgress
    @State private var previewProgress: Double?

    init(playback: MuzzaPlayback) {
        _playback = ObservedObject(wrappedValue: playback)
        _timeline = ObservedObject(wrappedValue: playback.progress)
    }

    private var progress: Double {
        let value = previewProgress ?? (timeline.duration > 0 ? timeline.position / timeline.duration : 0)
        return min(max(value, 0), 1)
    }

    private var displayedPosition: Double {
        previewProgress.map { $0 * timeline.duration } ?? timeline.position
    }

    var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.secondary.opacity(0.24))
                        .frame(height: 4)
                    Capsule()
                        .fill(Color.accentColor)
                        .frame(width: geometry.size.width * progress, height: 4)
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 14, height: 14)
                        .offset(x: min(max(geometry.size.width * progress - 7, 0), max(geometry.size.width - 14, 0)))
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            previewProgress = scrubProgress(value.location.x, width: geometry.size.width)
                        }
                        .onEnded { value in
                            let next = scrubProgress(value.location.x, width: geometry.size.width)
                            playback.seek(to: next * timeline.duration)
                            previewProgress = nil
                        }
                )
            }
            .frame(height: 28)
            .disabled(timeline.duration <= 0)
            HStack {
                Text(timeText(displayedPosition))
                Spacer()
                Text(timeText(timeline.duration))
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)
        }
        .accessibilityElement()
        .accessibilityLabel("Playback position")
        .accessibilityValue(Text("\(timeText(displayedPosition)) of \(timeText(timeline.duration))"))
        .accessibilityAdjustableAction { direction in
            guard timeline.duration > 0 else { return }
            let step = max(timeline.duration * 0.05, 5)
            switch direction {
            case .increment:
                playback.seek(to: min(timeline.position + step, timeline.duration))
            case .decrement:
                playback.seek(to: max(timeline.position - step, 0))
            @unknown default:
                break
            }
        }
    }

    private func scrubProgress(_ x: CGFloat, width: CGFloat) -> Double {
        guard width > 0 else { return 0 }
        return min(max(Double(x / width), 0), 1)
    }

    private func timeText(_ value: Double) -> String {
        guard value.isFinite, value > 0 else { return "0:00" }
        let seconds = Int(value)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

struct MuzzaMediaSectionView: View {
    let section: MuzzaLiveSection
    @ObservedObject var playback: MuzzaPlayback
    var layout: Layout = .horizontal
    var mediaAspectRatio: CGFloat = 1
    var showsTitle = true
    var onOpenLibrary: (() -> Void)? = nil

    enum Layout: Equatable {
        case horizontal
        case grid
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if (showsTitle && !section.title.isEmpty) || section.moreItem != nil {
                HStack {
                    if showsTitle && !section.title.isEmpty {
                        Text(LocalizedStringKey(section.title)).font(.title2.bold())
                    }
                    Spacer()
                    if let moreItem = section.moreItem {
                        if moreItem.kind == "library", let onOpenLibrary {
                            Button(action: onOpenLibrary) {
                                seeAllLabel
                            }
                            .buttonStyle(.plain)
                        } else {
                            NavigationLink(value: moreItem) {
                                seeAllLabel
                            }
                        }
                    }
                }
            }
            if section.items.allSatisfy({ $0.kind == "genre" }) {
                if layout == .grid {
                    LazyVGrid(
                        columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)],
                        alignment: .leading,
                        spacing: 8
                    ) {
                        ForEach(section.items) { item in
                            NavigationLink(value: item) {
                                HStack(spacing: 0) {
                                    Rectangle()
                                        .fill(stripeColor(item.stripeColor))
                                        .frame(width: 4)
                                    Text(item.title)
                                        .font(.subheadline.weight(.semibold))
                                        .lineLimit(1)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.leading, 8)
                                        .padding(.trailing, 12)
                                }
                                .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 48, alignment: .leading)
                                .background(Color(.secondarySystemFill))
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHGrid(
                            rows: Array(repeating: GridItem(.fixed(48), spacing: 8), count: 4),
                            alignment: .top,
                            spacing: 8
                        ) {
                            ForEach(section.items) { item in
                                NavigationLink(value: item) {
                                    HStack(spacing: 0) {
                                        Rectangle()
                                            .fill(stripeColor(item.stripeColor))
                                            .frame(width: 4)
                                        Text(item.title)
                                            .font(.subheadline.weight(.semibold))
                                            .lineLimit(1)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .padding(.leading, 8)
                                            .padding(.trailing, 12)
                                    }
                                    .frame(width: 180, height: 48)
                                    .background(Color(.secondarySystemFill))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            } else if layout == .grid {
                LazyVGrid(
                    columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                    alignment: .leading,
                    spacing: 12
                ) {
                    ForEach(section.items) { item in
                        mediaItemView(item)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(section.items) { item in mediaItemView(item) }
                    }
                }
            }
        }
    }

    private var seeAllLabel: some View {
        HStack(spacing: 5) {
            Text("See all")
            Image(systemName: "chevron.right")
        }
        .font(.subheadline.weight(.semibold))
    }

    @ViewBuilder
    private func mediaItemView(_ item: MuzzaLiveItem) -> some View {
        let isVideo = item.isVideoSong == true
        let size: CGFloat = layout == .grid ? (isVideo ? 174 : 160) : (isVideo ? 200 : 164)
        let aspectRatio = layout == .grid
            ? mediaAspectRatio
            : (mediaAspectRatio == 1 && isVideo ? 16.0 / 9.0 : mediaAspectRatio)
        if item.kind == "song" {
            Button { playback.play(item, in: section.items, queueTitle: section.title) } label: {
                MuzzaMediaCard(
                    item: item,
                    size: size,
                    aspectRatio: aspectRatio,
                    fillsWidth: layout == .grid,
                    isNowPlaying: playback.current?.id == item.id && playback.isPlaying
                )
            }
            .buttonStyle(.plain)
            .frame(maxWidth: layout == .grid ? .infinity : nil, alignment: .leading)
            .contextMenu {
                Button("Start radio") { playback.startRadio(item) }
                Button("Play next") { playback.enqueue(item, playNext: true) }
                Button("Add to queue") { playback.enqueue(item, playNext: false) }
                Button(playback.isLiked(item) ? "Remove like" : "Like") {
                    playback.toggleLike(item)
                }
                .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                if playback.isDownloaded(item) {
                    Button(role: .destructive) {
                        Task { _ = await playback.removeDownload(item) }
                    } label: {
                        Label("Remove download", systemImage: "arrow.down.circle")
                    }
                } else {
                    Button("Download") { playback.download(item) }
                }
                songNavigationLinks(for: item)
            }
        } else {
            NavigationLink(value: item) {
                MuzzaMediaCard(
                    item: item,
                    size: size,
                    aspectRatio: aspectRatio,
                    fillsWidth: layout == .grid
                )
            }
            .buttonStyle(.plain)
            .frame(maxWidth: layout == .grid ? .infinity : nil, alignment: .leading)
            .contextMenu {
                if ["album", "artist", "playlist"].contains(item.kind) {
                    if item.kind == "album" || item.kind == "playlist" {
                        MuzzaCollectionPlaybackActions(item: item, playback: playback)
                    }
                    Button { playback.toggleSaved(item) } label: {
                        Label(
                            playback.isSaved(item) ? "Remove from library" : "Save",
                            systemImage: playback.isSaved(item) ? "bookmark.slash" : "bookmark"
                        )
                    }
                    .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
                }
            }
        }
    }

    @ViewBuilder
    private func songNavigationLinks(for item: MuzzaLiveItem) -> some View {
        if item.album != nil || !(item.artists ?? []).isEmpty {
            Section("Navigate") {
                if let album = item.album {
                    NavigationLink {
                        MuzzaCollectionView(
                            item: muzzaCollectionItem(
                                id: album.id, title: album.title, subtitle: item.subtitle,
                                thumbnail: item.thumbnail, kind: "album"
                            ),
                            playback: playback
                        )
                    } label: {
                        Label("View album", systemImage: "square.stack")
                    }
                }
                ForEach(Array((item.artists ?? []).enumerated()), id: \.offset) { _, artist in
                    if let id = artist.id {
                        NavigationLink {
                            MuzzaCollectionView(
                                item: muzzaCollectionItem(
                                    id: id, title: artist.name, subtitle: "Artist",
                                    thumbnail: artist.thumbnail ?? "", kind: "artist"
                                ),
                                playback: playback
                            )
                        } label: {
                            Label("View \(artist.name)", systemImage: "person")
                        }
                    }
                }
            }
        }
    }
}

struct MuzzaCollectionPlaybackActions: View {
    let item: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback

    var body: some View {
        Button("Play") { playback.playCollection(item) }
            .disabled(playback.isLoadingCollection)
        Button("Shuffle") { playback.playCollection(item, shuffled: true) }
            .disabled(playback.isLoadingCollection)
        Button("Start radio") { playback.playCollection(item, radio: true) }
            .disabled(playback.isLoadingCollection)
    }
}

struct MuzzaMediaCard: View {
    let item: MuzzaLiveItem
    let size: CGFloat
    let aspectRatio: CGFloat
    var fillsWidth = false
    var isNowPlaying = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            artwork
            Text(item.title)
                .font(.subheadline.bold())
                .lineLimit(2)
                .frame(maxWidth: .infinity, minHeight: 40, maxHeight: 40, alignment: .topLeading)
            Text(item.subtitle.isEmpty ? " " : item.subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .frame(maxWidth: .infinity, minHeight: 30, maxHeight: 30, alignment: .topLeading)
        }
        .frame(maxWidth: fillsWidth ? .infinity : nil, alignment: .leading)
        .frame(width: fillsWidth ? nil : size, alignment: .leading)
    }

    @ViewBuilder
    private var artwork: some View {
        if fillsWidth {
            GeometryReader { geometry in
                artworkContent(size: geometry.size.width)
            }
            .aspectRatio(aspectRatio, contentMode: .fit)
        } else {
            artworkContent(size: size)
        }
    }

    private func artworkContent(size: CGFloat) -> some View {
        MuzzaArtwork(item: item, size: size, aspectRatio: aspectRatio)
            .overlay(alignment: .bottomTrailing) {
                if item.kind == "song" {
                    Image(systemName: isNowPlaying ? "waveform" : "play.fill")
                        .font(.caption.bold()).foregroundStyle(.white)
                        .padding(9).background(.black.opacity(0.65)).clipShape(Circle())
                        .padding(6)
                }
            }
    }
}

func muzzaCollectionItem(
    id: String, title: String, subtitle: String, thumbnail: String, kind: String
) -> MuzzaLiveItem {
    MuzzaLiveItem(
        id: id, title: title, subtitle: subtitle, thumbnail: thumbnail, kind: kind,
        params: nil, explicit: false, duration: nil, entryId: nil, downloadPath: nil,
        remoteLiked: nil
    )
}

struct MuzzaSongRow: View {
    let item: MuzzaLiveItem
    @ObservedObject var playback: MuzzaPlayback
    let queue: [MuzzaLiveItem]
    var queueIndex: Int? = nil
    var usesCurrentQueue = false
    var queueTitle: String?
    var playlist: MuzzaLiveItem? = nil
    var onPlaylistChange: (([MuzzaLiveItem]) -> Void)? = nil
    @State private var duplicatePlaylist: MuzzaLiveItem?
    @State private var showDuplicate = false

    init(
        item: MuzzaLiveItem,
        playback: MuzzaPlayback,
        queue: [MuzzaLiveItem],
        queueIndex: Int? = nil,
        usesCurrentQueue: Bool = false,
        queueTitle: String? = nil,
        playlist: MuzzaLiveItem? = nil,
        onPlaylistChange: (([MuzzaLiveItem]) -> Void)? = nil
    ) {
        self.item = item
        self.playback = playback
        self.queue = queue
        self.queueIndex = queueIndex
        self.usesCurrentQueue = usesCurrentQueue
        self.queueTitle = queueTitle
        self.playlist = playlist
        self.onPlaylistChange = onPlaylistChange
    }

    var body: some View {
        HStack(spacing: 8) {
            Button {
                if usesCurrentQueue, let queueIndex {
                    playback.playQueued(at: queueIndex)
                } else {
                    playback.play(item, in: queue, startingAt: queueIndex, queueTitle: queueTitle)
                }
            } label: {
                HStack(spacing: 12) {
                    MuzzaArtwork(
                        item: item,
                        size: item.isVideoSong == true ? 72 : 54,
                        aspectRatio: item.isVideoSong == true ? 16.0 / 9.0 : 1
                    )
                        .overlay {
                            if playback.current?.id == item.id && playback.isPlaying {
                                Image(systemName: "waveform")
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                                    .padding(7)
                                    .background(.black.opacity(0.7), in: Circle())
                            }
                        }
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
                    playback.startRadio(item)
                } label: {
                    Label("Start radio", systemImage: "dot.radiowaves.left.and.right")
                }
                Button {
                    playback.enqueue(item, playNext: true)
                } label: {
                    Label("Play next", systemImage: "text.line.first.and.arrowtriangle.forward")
                }
                Button {
                    playback.enqueue(item, playNext: false)
                } label: {
                    Label("Add to queue", systemImage: "text.badge.plus")
                }
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

                songNavigationLinks

                let addablePlaylists = playback.playlists.filter {
                    $0.kind == "localPlaylist" ||
                        ($0.kind == "playlist" && $0.isEditable == true && playback.canSyncPlaylists)
                }
                if !addablePlaylists.isEmpty {
                    Menu("Add to playlist") {
                        ForEach(addablePlaylists) { target in
                            Button(target.title) { add(to: target, allowDuplicate: false) }
                        }
                    }
                    .disabled(playback.isLibraryBusy)
                }
                if playback.isDownloaded(item) {
                    Button(role: .destructive) {
                        Task { _ = await playback.removeDownload(item) }
                    } label: {
                        Label("Remove download", systemImage: "arrow.down.circle")
                    }
                    .disabled(playback.isLibraryBusy)
                } else {
                    Button {
                        playback.download(item)
                    } label: {
                        Label("Download", systemImage: "arrow.down.circle")
                    }
                    .disabled(!playback.libraryLoaded || playback.isLibraryBusy || playback.downloading.contains(item.id))
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
                if playback.downloading.contains(item.id) {
                    ProgressView().frame(width: 36, height: 44)
                } else {
                    Image(systemName: "ellipsis").frame(width: 36, height: 44)
                }
            }
            .accessibilityLabel("Song actions")
        }
        .contextMenu {
            Button("Start radio") { playback.startRadio(item) }
            Button("Play next") { playback.enqueue(item, playNext: true) }
            Button("Add to queue") { playback.enqueue(item, playNext: false) }
            Button(playback.isLiked(item) ? "Remove like" : "Like") {
                playback.toggleLike(item)
            }
            .disabled(!playback.libraryLoaded || playback.isLibraryBusy)
            let addablePlaylists = playback.playlists.filter {
                $0.kind == "localPlaylist" ||
                    ($0.kind == "playlist" && $0.isEditable == true && playback.canSyncPlaylists)
            }
            if !addablePlaylists.isEmpty {
                Menu("Add to playlist") {
                    ForEach(addablePlaylists) { target in
                        Button(target.title) { add(to: target, allowDuplicate: false) }
                    }
                }
                .disabled(playback.isLibraryBusy)
            }
            if playback.isDownloaded(item) {
                Button(role: .destructive) {
                    Task { _ = await playback.removeDownload(item) }
                } label: {
                    Label("Remove download", systemImage: "arrow.down.circle")
                }
            } else {
                Button("Download") { playback.download(item) }
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
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button {
                playback.enqueue(item, playNext: false)
            } label: {
                Label("Queue", systemImage: "text.badge.plus")
            }
            .tint(.accentColor)
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button {
                playback.enqueue(item, playNext: true)
            } label: {
                Label("Play next", systemImage: "text.line.first.and.arrowtriangle.forward")
            }
            .tint(.orange)
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

    @ViewBuilder
    private var songNavigationLinks: some View {
        if item.album != nil || !(item.artists ?? []).isEmpty {
            Section("Navigate") {
                if let album = item.album {
                    NavigationLink {
                        MuzzaCollectionView(
                            item: muzzaCollectionItem(
                                id: album.id, title: album.title, subtitle: item.subtitle,
                                thumbnail: item.thumbnail, kind: "album"
                            ),
                            playback: playback
                        )
                    } label: {
                        Label("View album", systemImage: "square.stack")
                    }
                }
                ForEach(Array((item.artists ?? []).enumerated()), id: \.offset) { _, artist in
                    if let id = artist.id {
                        NavigationLink {
                            MuzzaCollectionView(
                                item: muzzaCollectionItem(
                                    id: id, title: artist.name, subtitle: "Artist",
                                    thumbnail: artist.thumbnail ?? "", kind: "artist"
                                ),
                                playback: playback
                            )
                        } label: {
                            Label("View \(artist.name)", systemImage: "person")
                        }
                    }
                }
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
