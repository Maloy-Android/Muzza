import SwiftUI

private enum MuzzaSearchCategory: String, CaseIterable {
    case songs, videos, albums, artists, profiles, playlists, community

    var title: String {
        switch self {
        case .songs: return "Songs"
        case .videos: return "Videos"
        case .albums: return "Albums"
        case .artists: return "Artists"
        case .profiles: return "Profiles"
        case .playlists: return "Playlists"
        case .community: return "Community"
        }
    }
}

struct MuzzaSearchView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var playback: MuzzaPlayback
    @AppStorage("muzza.hideExplicit") private var hideExplicit = false
    @State private var query = ""
    @State private var category: MuzzaSearchCategory = .songs
    @State private var items: [MuzzaLiveItem] = []
    @State private var continuation: String?
    @State private var isLoading = false
    @State private var searched = false
    @State private var searchError: String?
    @State private var pageError: String?
    @State private var requestID = UUID()
    @State private var loadedContinuations: Set<String> = []

    init(playback: MuzzaPlayback) {
        _playback = ObservedObject(wrappedValue: playback)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField("Search Muzza", text: $query)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.search)
                            .onSubmit {
                                Task { await search() }
                            }
                        if !query.isEmpty {
                            Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }
                                .accessibilityLabel("Clear search")
                        }
                    }
                    .padding(12).background(Color(.secondarySystemFill))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(MuzzaSearchCategory.allCases, id: \.self) { value in
                                Button { category = value } label: {
                                    Text(LocalizedStringKey(value.title)).font(.subheadline.weight(.semibold))
                                        .padding(.horizontal, 15).padding(.vertical, 9)
                                        .background(category == value ? Color.accentColor : Color(.secondarySystemFill))
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    searchResults
                    if isLoading {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    } else if let searchError {
                        MuzzaErrorView(message: searchError) { Task { await search() } }
                    } else if searched && items.isEmpty {
                        MuzzaEmptyView(symbol: "magnifyingglass", message: "No results found.")
                    }
                    if let pageError {
                        MuzzaErrorView(message: pageError) { Task { await loadMore() } }
                    } else if continuation != nil && !isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .onAppear { Task { await loadMore() } }
                    }
                }
                .padding(16).padding(.bottom, 30)
            }
            .modifier(MuzzaPageBackground())
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .navigationDestination(for: MuzzaLiveItem.self) { MuzzaCollectionView(item: $0, playback: playback) }
            .task(id: "\(category.rawValue)-\(hideExplicit)") { await search() }
        }
    }

    @ViewBuilder
    private var searchResults: some View {
        if [.albums, .artists, .playlists, .profiles].contains(category) {
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)],
                spacing: 16
            ) {
                ForEach(items) { item in
                    NavigationLink(value: item) {
                        VStack(alignment: .leading, spacing: 8) {
                            MuzzaArtwork(item: item, size: 130)
                            Text(item.title).font(.subheadline.bold()).lineLimit(2)
                            if !item.subtitle.isEmpty {
                                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if ["album", "artist", "playlist"].contains(item.kind) {
                            Button {
                                playback.toggleSaved(item)
                            } label: {
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
        } else {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                if item.kind == "song" {
                    MuzzaSongRow(item: item, playback: playback, queue: items, queueIndex: index)
                } else {
                    NavigationLink(value: item) {
                        HStack(spacing: 12) {
                            MuzzaArtwork(item: item, size: 54)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func search() async {
        let id = UUID()
        requestID = id
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        items = []
        continuation = nil
        loadedContinuations.removeAll()
        searchError = nil
        pageError = nil
        searched = false
        guard !text.isEmpty else {
            isLoading = false
            return
        }
        isLoading = true
        defer { if requestID == id { isLoading = false } }
        do {
            try await Task.sleep(nanoseconds: 350_000_000)
            let response = try await MuzzaAPI.search(
                query: text, category: category.rawValue, hideExplicit: hideExplicit, continuation: nil
            )
            guard !Task.isCancelled, requestID == id else { return }
            items = uniqueItems(response.items)
            continuation = response.continuation
            searched = true
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            searchError = error.localizedDescription
        }
    }

    private func loadMore() async {
        guard !isLoading, let token = continuation, !loadedContinuations.contains(token) else { return }
        let id = requestID
        isLoading = true
        pageError = nil
        defer { if requestID == id { isLoading = false } }
        do {
            let response = try await MuzzaAPI.search(
                query: query.trimmingCharacters(in: .whitespacesAndNewlines),
                category: category.rawValue, hideExplicit: hideExplicit, continuation: token
            )
            guard !Task.isCancelled, requestID == id else { return }
            loadedContinuations.insert(token)
            items = uniqueItems(items + response.items)
            if response.continuation == token || response.continuation.map({ loadedContinuations.contains($0) }) == true {
                pageError = "YouTube returned an invalid continuation."
                continuation = nil
            } else {
                isLoading = false
                continuation = response.continuation
            }
        } catch {
            guard !Task.isCancelled, requestID == id else { return }
            pageError = error.localizedDescription
        }
    }

    private func uniqueItems(_ items: [MuzzaLiveItem]) -> [MuzzaLiveItem] {
        var seen = Set<String>()
        return items.filter { seen.insert("\($0.kind):\($0.id)").inserted }
    }
}
