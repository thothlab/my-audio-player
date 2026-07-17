package tech.thothlab.dombra.domain.ports

import tech.thothlab.dombra.core.DombraResult
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.Track

/** Artwork: файловый/платформенный кэш, ключ — stableId трека или id альбома. */
interface ArtworkRepository {
    suspend fun store(key: String, bytes: ByteArray)
    suspend fun load(key: String): ByteArray?
    suspend fun contains(key: String): Boolean
    suspend fun clearAll()
}

interface LyricsRepository {
    /**
     * Порядок поиска (§5.10): embedded → кэш → сеть → null (empty state).
     * networkAllowed=false ограничивает поиск локальными источниками.
     */
    suspend fun lyricsFor(track: Track, networkAllowed: Boolean = true): DombraResult<Lyrics?>
    suspend fun invalidate(trackStableId: String)
}

/** Единый DTO внешних данных исполнителя (§5.6 ТЗ). */
data class ExternalArtistInfo(
    val externalId: String,
    val name: String,
    val profile: String?,
    val imageUrls: List<String>,
    val source: String,
    val fetchedAt: Long,
)

interface ExternalMusicInfoProvider {
    val providerName: String
    val isConfigured: Boolean
    suspend fun searchArtist(name: String): DombraResult<ExternalArtistInfo?>
}

/** Версионированный снапшот пользовательских данных (§5.13 ТЗ). */
interface SyncProvider {
    val providerName: String

    /** false = «только на этом устройстве». */
    val isAvailable: Boolean

    suspend fun upload(snapshotJson: String): DombraResult<Unit>
    suspend fun download(): DombraResult<String?>
}
