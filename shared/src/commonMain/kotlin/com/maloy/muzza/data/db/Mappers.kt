package com.maloy.muzza.data.db

import com.maloy.muzza.db.Album
import com.maloy.muzza.db.Artist
import com.maloy.muzza.db.Format
import com.maloy.muzza.db.Playlist
import com.maloy.muzza.db.Song
import com.maloy.muzza.db.album.SelectBookmarked as BookmarkedAlbum
import com.maloy.muzza.db.artist.SelectBookmarked as BookmarkedArtist
import com.maloy.muzza.db.playlist.SelectBookmarked as BookmarkedPlaylist
import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.domain.model.FormatEntity
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.SongEntity
import kotlinx.datetime.LocalDateTime

// ── Song ──────────────────────────────────────────────────────────────────

fun Song.toModel() = SongEntity(
    id = id,
    title = title,
    duration = duration.toInt(),
    thumbnailUrl = thumbnailUrl,
    albumId = albumId,
    albumName = albumName,
    liked = liked != 0L,
    totalPlayTime = totalPlayTime,
    inLibrary = inLibrary?.let { LocalDateTime.parse(it) },
    dateDownload = dateDownload?.let { LocalDateTime.parse(it) },
    artistName = artistName,
    isLocal = isLocal != 0L,
    localPath = localPath,
    contentUri = contentUri,
    isVideoSong = isVideoSong != 0L,
    explicit = explicit != 0L,
    isFromCache = isFromCache != 0L,
)

fun SongEntity.toDb() = Song(
    id = id,
    title = title,
    duration = duration.toLong(),
    thumbnailUrl = thumbnailUrl,
    albumId = albumId,
    albumName = albumName,
    liked = if (liked) 1L else 0L,
    totalPlayTime = totalPlayTime,
    inLibrary = inLibrary?.toString(),
    dateDownload = dateDownload?.toString(),
    artistName = artistName,
    isLocal = if (isLocal) 1L else 0L,
    localPath = localPath,
    contentUri = contentUri,
    isVideoSong = if (isVideoSong) 1L else 0L,
    explicit = if (explicit) 1L else 0L,
    isFromCache = if (isFromCache) 1L else 0L,
)

// ── Album ─────────────────────────────────────────────────────────────────

fun Album.toModel() = AlbumEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    year = year?.toInt(),
    thumbnailUrl = thumbnailUrl,
    themeColor = themeColor?.toInt(),
    songCount = songCount.toInt(),
    duration = duration.toInt(),
    lastUpdateTime = LocalDateTime.parse(lastUpdateTime),
    bookmarkedAt = bookmarkedAt?.let { LocalDateTime.parse(it) },
)

fun AlbumEntity.toDb() = Album(
    id = id,
    playlistId = playlistId,
    title = title,
    year = year?.toLong(),
    thumbnailUrl = thumbnailUrl,
    themeColor = themeColor?.toLong(),
    songCount = songCount.toLong(),
    duration = duration.toLong(),
    lastUpdateTime = lastUpdateTime.toString(),
    bookmarkedAt = bookmarkedAt?.toString(),
)

// ── Artist ────────────────────────────────────────────────────────────────

fun Artist.toModel() = ArtistEntity(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    channelId = channelId,
    lastUpdateTime = LocalDateTime.parse(lastUpdateTime),
    bookmarkedAt = bookmarkedAt?.let { LocalDateTime.parse(it) },
    isProfile = isProfile != 0L,
)

fun ArtistEntity.toDb() = Artist(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    channelId = channelId,
    lastUpdateTime = lastUpdateTime.toString(),
    bookmarkedAt = bookmarkedAt?.toString(),
    isProfile = if (isProfile) 1L else 0L,
)

// ── Playlist ──────────────────────────────────────────────────────────────

fun Playlist.toModel() = PlaylistEntity(
    id = id,
    name = name,
    browseId = browseId,
    playlistAuthorsId = playlistAuthorsId,
    playlistAuthorName = playlistAuthorName,
    playlistAuthorAvatarUrl = playlistAuthorAvatarUrl,
    createdAt = createdAt?.let { LocalDateTime.parse(it) },
    lastUpdateTime = lastUpdateTime?.let { LocalDateTime.parse(it) },
    isEditable = isEditable != 0L,
    bookmarkedAt = bookmarkedAt?.let { LocalDateTime.parse(it) },
    remoteSongCount = remoteSongCount?.toInt(),
    playEndpointParams = playEndpointParams,
    thumbnailUrl = thumbnailUrl,
    shuffleEndpointParams = shuffleEndpointParams,
    radioEndpointParams = radioEndpointParams,
    description = description,
    isLocal = isLocal != 0L,
)

fun PlaylistEntity.toDb() = Playlist(
    id = id,
    name = name,
    browseId = browseId,
    playlistAuthorsId = playlistAuthorsId,
    playlistAuthorName = playlistAuthorName,
    playlistAuthorAvatarUrl = playlistAuthorAvatarUrl,
    createdAt = createdAt?.toString(),
    lastUpdateTime = lastUpdateTime?.toString(),
    isEditable = if (isEditable) 1L else 0L,
    bookmarkedAt = bookmarkedAt?.toString(),
    remoteSongCount = remoteSongCount?.toLong(),
    playEndpointParams = playEndpointParams,
    thumbnailUrl = thumbnailUrl,
    shuffleEndpointParams = shuffleEndpointParams,
    radioEndpointParams = radioEndpointParams,
    description = description,
    isLocal = if (isLocal) 1L else 0L,
)

// ── Format ────────────────────────────────────────────────────────────────

fun Format.toModel() = FormatEntity(
    id = id,
    itag = itag?.toInt(),
    mimeType = mimeType,
    codecs = codecs,
    bitrate = bitrate,
    sampleRate = sampleRate?.toInt(),
    contentLength = contentLength,
    loudnessDb = loudnessDb,
    playbackUrl = playbackUrl,
    expiredAt = expiredAt,
)

fun FormatEntity.toDb() = Format(
    id = id,
    itag = itag?.toLong(),
    mimeType = mimeType,
    codecs = codecs,
    bitrate = bitrate,
    sampleRate = sampleRate?.toLong(),
    contentLength = contentLength,
    loudnessDb = loudnessDb,
    playbackUrl = playbackUrl,
    expiredAt = expiredAt,
)

fun BookmarkedAlbum.toModel() = AlbumEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    year = year?.toInt(),
    thumbnailUrl = thumbnailUrl,
    themeColor = themeColor?.toInt(),
    songCount = songCount.toInt(),
    duration = duration.toInt(),
    lastUpdateTime = LocalDateTime.parse(lastUpdateTime),
    bookmarkedAt = bookmarkedAt.let { LocalDateTime.parse(it) },
)

fun BookmarkedArtist.toModel() = ArtistEntity(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    channelId = channelId,
    lastUpdateTime = LocalDateTime.parse(lastUpdateTime),
    bookmarkedAt = LocalDateTime.parse(bookmarkedAt),
    isProfile = isProfile != 0L,
)

fun BookmarkedPlaylist.toModel() = PlaylistEntity(
    id = id,
    name = name,
    browseId = browseId,
    playlistAuthorsId = playlistAuthorsId,
    playlistAuthorName = playlistAuthorName,
    playlistAuthorAvatarUrl = playlistAuthorAvatarUrl,
    createdAt = createdAt?.let { LocalDateTime.parse(it) },
    lastUpdateTime = lastUpdateTime?.let { LocalDateTime.parse(it) },
    isEditable = isEditable != 0L,
    bookmarkedAt = LocalDateTime.parse(bookmarkedAt),
    remoteSongCount = remoteSongCount?.toInt(),
    playEndpointParams = playEndpointParams,
    thumbnailUrl = thumbnailUrl,
    shuffleEndpointParams = shuffleEndpointParams,
    radioEndpointParams = radioEndpointParams,
    description = description,
    isLocal = isLocal != 0L,
)
