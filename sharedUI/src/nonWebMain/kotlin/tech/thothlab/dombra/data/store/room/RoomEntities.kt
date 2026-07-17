package tech.thothlab.dombra.data.store.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Схема v1 (§6.1 ТЗ). Enum'ы хранятся строками, ReplayGain — плоскими колонками,
 * строки текста (Lyrics) — JSON'ом: маппинг в доменные модели в RoomLibraryStore.
 *
 * Дубликаты (T03): unique stableId (PK tracks), unique (artistId, title) у альбомов,
 * unique slug у плейлистов. Id артистов/альбомов детерминированы от имени, поэтому
 * unique-индексы — защитная сетка, а не рабочий путь.
 */

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "albums",
    indices = [Index(value = ["artistId", "title"], unique = true)],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val title: String,
    val year: Int?,
    val albumArtist: String?,
)

@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artistId")],
)
data class TrackEntity(
    @PrimaryKey val stableId: String,
    val albumId: String,
    val artistId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val albumArtist: String?,
    val year: Int?,
    val trackNo: Int?,
    val discNo: Int?,
    val durationMs: Long?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val format: String,
    val sourceUri: String,
    val sourceDisplayName: String,
    val fileSize: Long,
    val modificationTime: Long,
    val rgTrackGainDb: Double?,
    val rgAlbumGainDb: Double?,
    val rgTrackPeak: Double?,
    val rgAlbumPeak: Double?,
    val hasEmbeddedArt: Boolean,
    val hasEmbeddedLyrics: Boolean,
    val availability: String,
    val addedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackStableId: String,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["slug"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long?,
    val customCoverUri: String?,
    val folderSyncPath: String?,
    val folderSyncEnabled: Boolean,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val position: Int,
    val trackStableId: String,
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackStableId: String,
    val type: String,
    val linesJson: String,
    val source: String,
    val fetchedAt: Long,
)

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val builtIn: Boolean,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "eq_bands",
    primaryKeys = ["presetId", "bandIndex"],
    foreignKeys = [
        ForeignKey(
            entity = EqPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class EqBandEntity(
    val presetId: String,
    val bandIndex: Int,
    val frequencyHz: Double,
    val gainDb: Double,
    val bandwidth: Double,
)

/** Единственная строка с id=0. */
@Entity(tableName = "eq_settings")
data class EqSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean,
    val activePresetId: String?,
    val globalGainDb: Double,
    val updatedAt: Long,
)
