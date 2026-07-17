package tech.thothlab.dombra.data.store.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.EqBand
import tech.thothlab.dombra.domain.model.EqPreset
import tech.thothlab.dombra.domain.model.EqPresetType
import tech.thothlab.dombra.domain.model.EqSettings
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.LyricsLine
import tech.thothlab.dombra.domain.model.LyricsType
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistItem
import tech.thothlab.dombra.domain.model.PlaylistWithItems
import tech.thothlab.dombra.domain.model.ReplayGain
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.TrackAvailability

/** Room-реализация LibraryStore (android/jvm/ios). */
class RoomLibraryStore(db: DombraDatabase) : LibraryStore {

    private val dao = db.dao()

    // Tracks

    override fun tracksFlow(): Flow<List<Track>> =
        dao.tracksFlow().map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertTracks(tracks: List<Track>) =
        dao.upsertTracks(tracks.map { it.toEntity() })

    override suspend fun getTrack(stableId: String): Track? = dao.getTrack(stableId)?.toDomain()

    override suspend fun getAllTracks(): List<Track> = dao.getAllTracks().map { it.toDomain() }

    override suspend fun deleteTracks(stableIds: Collection<String>) =
        dao.deleteTracksCascade(stableIds)

    override suspend fun updateAvailability(stableId: String, availability: TrackAvailability) =
        dao.updateAvailability(stableId, availability.name)

    // Artists / Albums

    override fun artistsFlow(): Flow<List<Artist>> =
        dao.artistsFlow().map { rows -> rows.map { it.toDomain() } }

    override fun albumsFlow(): Flow<List<Album>> =
        dao.albumsFlow().map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertArtists(artists: List<Artist>) =
        dao.upsertArtists(artists.map { it.toEntity() })

    override suspend fun upsertAlbums(albums: List<Album>) =
        dao.upsertAlbums(albums.map { it.toEntity() })

    override suspend fun getArtist(id: String): Artist? = dao.getArtist(id)?.toDomain()

    override suspend fun getAlbum(id: String): Album? = dao.getAlbum(id)?.toDomain()

    override suspend fun pruneOrphans() = dao.pruneOrphans()

    // Favorites

    override fun favoritesFlow(): Flow<Set<String>> = dao.favoritesFlow().map { it.toSet() }

    override suspend fun getFavorites(): Set<String> = dao.getFavorites().toSet()

    override suspend fun setFavorite(stableId: String, favorite: Boolean) {
        if (favorite) dao.insertFavorite(FavoriteEntity(stableId)) else dao.deleteFavorite(stableId)
    }

    // Playlists

    override fun playlistsFlow(): Flow<List<Playlist>> =
        dao.playlistsFlow().map { rows -> rows.map { it.toDomain() } }

    override fun playlistFlow(id: String): Flow<PlaylistWithItems?> =
        combine(dao.playlistRowFlow(id), dao.playlistItemsFlow(id)) { row, items ->
            row?.let { PlaylistWithItems(it.toDomain(), items.map { item -> item.toDomain() }) }
        }

    override suspend fun getPlaylist(id: String): PlaylistWithItems? {
        val row = dao.getPlaylistRow(id) ?: return null
        return PlaylistWithItems(row.toDomain(), dao.getPlaylistItems(id).map { it.toDomain() })
    }

    override suspend fun getAllPlaylists(): List<PlaylistWithItems> {
        val itemsByPlaylist = dao.getAllPlaylistItems().groupBy { it.playlistId }
        return dao.getAllPlaylistRows().map { row ->
            PlaylistWithItems(
                row.toDomain(),
                itemsByPlaylist[row.id].orEmpty().map { it.toDomain() },
            )
        }
    }

    override suspend fun upsertPlaylist(playlist: Playlist) =
        dao.upsertPlaylistRow(playlist.toEntity())

    override suspend fun deletePlaylist(id: String) = dao.deletePlaylistRow(id)

    override suspend fun replaceItems(playlistId: String, items: List<PlaylistItem>) {
        dao.getPlaylistRow(playlistId) ?: return
        val renumbered = items.mapIndexed { index, item ->
            PlaylistItemEntity(playlistId, index, item.trackStableId)
        }
        dao.replacePlaylistItems(playlistId, renumbered)
    }

    // Lyrics

    override suspend fun getLyrics(stableId: String): Lyrics? = dao.getLyrics(stableId)?.toDomain()

    override suspend fun saveLyrics(lyrics: Lyrics) = dao.upsertLyrics(lyrics.toEntity())

    override suspend fun deleteLyrics(stableId: String) = dao.deleteLyrics(stableId)

    // EQ

    override fun eqSettingsFlow(): Flow<EqSettings> =
        dao.eqSettingsFlow().map { it?.toDomain() ?: EqSettings() }

    override suspend fun saveEqSettings(settings: EqSettings) =
        dao.upsertEqSettings(settings.toEntity())

    override fun eqPresetsFlow(): Flow<List<EqPreset>> =
        dao.eqPresetsFlow().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getEqBands(presetId: String): List<EqBand> =
        dao.getEqBands(presetId).map { it.toDomain() }

    override suspend fun saveEqPreset(preset: EqPreset, bands: List<EqBand>) =
        dao.saveEqPreset(
            preset.toEntity(),
            bands.map { EqBandEntity(preset.id, it.index, it.frequencyHz, it.gainDb, it.bandwidth) },
        )

    override suspend fun deleteEqPreset(presetId: String) = dao.deleteEqPresetCascade(presetId)
}

// --- Маппинг entity <-> domain ---

private val lyricsJson = Json { ignoreUnknownKeys = true }
private val lyricsLinesSerializer = ListSerializer(LyricsLine.serializer())

private fun TrackEntity.toDomain() = Track(
    stableId = stableId, albumId = albumId, artistId = artistId,
    title = title, artistName = artistName, albumTitle = albumTitle,
    albumArtist = albumArtist, year = year, trackNo = trackNo, discNo = discNo,
    durationMs = durationMs, sampleRate = sampleRate, bitDepth = bitDepth, channels = channels,
    format = AudioFormat.entries.firstOrNull { it.name == format } ?: AudioFormat.UNKNOWN,
    sourceUri = sourceUri, sourceDisplayName = sourceDisplayName,
    fileSize = fileSize, modificationTime = modificationTime,
    replayGain = ReplayGain(rgTrackGainDb, rgAlbumGainDb, rgTrackPeak, rgAlbumPeak),
    hasEmbeddedArt = hasEmbeddedArt, hasEmbeddedLyrics = hasEmbeddedLyrics,
    availability = TrackAvailability.entries.firstOrNull { it.name == availability }
        ?: TrackAvailability.AVAILABLE,
    addedAt = addedAt,
)

private fun Track.toEntity() = TrackEntity(
    stableId = stableId, albumId = albumId, artistId = artistId,
    title = title, artistName = artistName, albumTitle = albumTitle,
    albumArtist = albumArtist, year = year, trackNo = trackNo, discNo = discNo,
    durationMs = durationMs, sampleRate = sampleRate, bitDepth = bitDepth, channels = channels,
    format = format.name,
    sourceUri = sourceUri, sourceDisplayName = sourceDisplayName,
    fileSize = fileSize, modificationTime = modificationTime,
    rgTrackGainDb = replayGain.trackGainDb, rgAlbumGainDb = replayGain.albumGainDb,
    rgTrackPeak = replayGain.trackPeak, rgAlbumPeak = replayGain.albumPeak,
    hasEmbeddedArt = hasEmbeddedArt, hasEmbeddedLyrics = hasEmbeddedLyrics,
    availability = availability.name,
    addedAt = addedAt,
)

private fun ArtistEntity.toDomain() = Artist(id = id, name = name)
private fun Artist.toEntity() = ArtistEntity(id = id, name = name)

private fun AlbumEntity.toDomain() =
    Album(id = id, artistId = artistId, title = title, year = year, albumArtist = albumArtist)

private fun Album.toEntity() =
    AlbumEntity(id = id, artistId = artistId, title = title, year = year, albumArtist = albumArtist)

private fun PlaylistEntity.toDomain() = Playlist(
    id = id, slug = slug, title = title,
    createdAt = createdAt, updatedAt = updatedAt, lastPlayedAt = lastPlayedAt,
    customCoverUri = customCoverUri,
    folderSyncPath = folderSyncPath, folderSyncEnabled = folderSyncEnabled,
)

private fun Playlist.toEntity() = PlaylistEntity(
    id = id, slug = slug, title = title,
    createdAt = createdAt, updatedAt = updatedAt, lastPlayedAt = lastPlayedAt,
    customCoverUri = customCoverUri,
    folderSyncPath = folderSyncPath, folderSyncEnabled = folderSyncEnabled,
)

private fun PlaylistItemEntity.toDomain() =
    PlaylistItem(playlistId = playlistId, position = position, trackStableId = trackStableId)

private fun LyricsEntity.toDomain() = Lyrics(
    trackStableId = trackStableId,
    type = LyricsType.entries.firstOrNull { it.name == type } ?: LyricsType.PLAIN,
    lines = lyricsJson.decodeFromString(lyricsLinesSerializer, linesJson),
    source = source,
    fetchedAt = fetchedAt,
)

private fun Lyrics.toEntity() = LyricsEntity(
    trackStableId = trackStableId,
    type = type.name,
    linesJson = lyricsJson.encodeToString(lyricsLinesSerializer, lines),
    source = source,
    fetchedAt = fetchedAt,
)

private fun EqPresetEntity.toDomain() = EqPreset(
    id = id, name = name, builtIn = builtIn,
    type = EqPresetType.entries.firstOrNull { it.name == type } ?: EqPresetType.MANUAL,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun EqPreset.toEntity() = EqPresetEntity(
    id = id, name = name, builtIn = builtIn, type = type.name,
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun EqBandEntity.toDomain() = EqBand(
    presetId = presetId, index = bandIndex,
    frequencyHz = frequencyHz, gainDb = gainDb, bandwidth = bandwidth,
)

private fun EqSettingsEntity.toDomain() = EqSettings(
    enabled = enabled, activePresetId = activePresetId,
    globalGainDb = globalGainDb, updatedAt = updatedAt,
)

private fun EqSettings.toEntity() = EqSettingsEntity(
    id = 0, enabled = enabled, activePresetId = activePresetId,
    globalGainDb = globalGainDb, updatedAt = updatedAt,
)
