package tech.thothlab.dombra.data.store.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DombraDao {

    // --- Tracks ---

    @Query("SELECT * FROM tracks")
    fun tracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE stableId = :stableId")
    suspend fun getTrack(stableId: String): TrackEntity?

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE stableId IN (:stableIds)")
    suspend fun deleteTrackRows(stableIds: Collection<String>)

    @Query("DELETE FROM favorites WHERE trackStableId IN (:stableIds)")
    suspend fun deleteFavoriteRows(stableIds: Collection<String>)

    @Query("DELETE FROM lyrics WHERE trackStableId IN (:stableIds)")
    suspend fun deleteLyricsRows(stableIds: Collection<String>)

    /** Как InMemory: удаление трека забирает с собой favorite и кэш лирики. */
    @Transaction
    suspend fun deleteTracksCascade(stableIds: Collection<String>) {
        deleteTrackRows(stableIds)
        deleteFavoriteRows(stableIds)
        deleteLyricsRows(stableIds)
    }

    @Query("UPDATE tracks SET availability = :availability WHERE stableId = :stableId")
    suspend fun updateAvailability(stableId: String, availability: String)

    // --- Artists / Albums ---

    @Query("SELECT * FROM artists")
    fun artistsFlow(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM albums")
    fun albumsFlow(): Flow<List<AlbumEntity>>

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtist(id: String): ArtistEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbum(id: String): AlbumEntity?

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM tracks)")
    suspend fun deleteOrphanAlbums()

    /** Выполнять после deleteOrphanAlbums: в albums остаются только используемые. */
    @Query(
        "DELETE FROM artists WHERE id NOT IN " +
            "(SELECT artistId FROM tracks UNION SELECT artistId FROM albums)",
    )
    suspend fun deleteOrphanArtists()

    @Transaction
    suspend fun pruneOrphans() {
        deleteOrphanAlbums()
        deleteOrphanArtists()
    }

    // --- Favorites ---

    @Query("SELECT trackStableId FROM favorites")
    fun favoritesFlow(): Flow<List<String>>

    @Query("SELECT trackStableId FROM favorites")
    suspend fun getFavorites(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackStableId = :stableId")
    suspend fun deleteFavorite(stableId: String)

    // --- Playlists ---

    @Query("SELECT * FROM playlists")
    fun playlistsFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlistRowFlow(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun playlistItemsFlow(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistRows(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistRow(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId, position")
    suspend fun getAllPlaylistItems(): List<PlaylistItemEntity>

    @Upsert
    suspend fun upsertPlaylistRow(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItems(playlistId: String)

    @Insert
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Transaction
    suspend fun replacePlaylistItems(playlistId: String, items: List<PlaylistItemEntity>) {
        deletePlaylistItems(playlistId)
        insertPlaylistItems(items)
    }

    // --- Lyrics ---

    @Query("SELECT * FROM lyrics WHERE trackStableId = :stableId")
    suspend fun getLyrics(stableId: String): LyricsEntity?

    @Upsert
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics WHERE trackStableId = :stableId")
    suspend fun deleteLyrics(stableId: String)

    // --- EQ ---

    @Query("SELECT * FROM eq_settings WHERE id = 0")
    fun eqSettingsFlow(): Flow<EqSettingsEntity?>

    @Upsert
    suspend fun upsertEqSettings(settings: EqSettingsEntity)

    @Query("SELECT * FROM eq_presets")
    fun eqPresetsFlow(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_bands WHERE presetId = :presetId ORDER BY bandIndex")
    suspend fun getEqBands(presetId: String): List<EqBandEntity>

    @Upsert
    suspend fun upsertEqPresetRow(preset: EqPresetEntity)

    @Query("DELETE FROM eq_bands WHERE presetId = :presetId")
    suspend fun deleteEqBands(presetId: String)

    @Insert
    suspend fun insertEqBands(bands: List<EqBandEntity>)

    @Transaction
    suspend fun saveEqPreset(preset: EqPresetEntity, bands: List<EqBandEntity>) {
        upsertEqPresetRow(preset)
        deleteEqBands(preset.id)
        insertEqBands(bands)
    }

    @Query("DELETE FROM eq_presets WHERE id = :presetId")
    suspend fun deleteEqPresetRow(presetId: String)

    @Query("UPDATE eq_settings SET activePresetId = NULL WHERE activePresetId = :presetId")
    suspend fun clearActiveEqPreset(presetId: String)

    /** Как InMemory: удаление активного пресета сбрасывает activePresetId. */
    @Transaction
    suspend fun deleteEqPresetCascade(presetId: String) {
        deleteEqPresetRow(presetId)
        clearActiveEqPreset(presetId)
    }
}
