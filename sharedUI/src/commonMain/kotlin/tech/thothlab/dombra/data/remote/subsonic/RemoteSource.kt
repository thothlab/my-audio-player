package tech.thothlab.dombra.data.remote.subsonic

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.ports.SettingsRepository

/** Удалённый источник музыки (Subsonic/OpenSubsonic). Держит подключение и отдаёт просмотр/поиск. */
interface RemoteSourceRepository {
    val config: StateFlow<SubsonicConfig?>

    /** Подключиться: генерирует соль/токен, проверяет `ping`, сохраняет конфиг. */
    suspend fun connect(baseUrl: String, username: String, password: String): Result<Unit>
    suspend fun disconnect()

    suspend fun albums(): List<SubAlbum>
    suspend fun artists(): List<SubArtist>
    suspend fun playlists(): List<SubPlaylist>
    suspend fun starred(): Starred2
    suspend fun albumTracks(albumId: String): List<Track>
    suspend fun playlistTracks(playlistId: String): List<Track>
    suspend fun search(query: String): SearchResult3
    fun songToTrack(song: SubSong): Track

    fun coverArtUrl(coverArtId: String?, size: Int = 512): String?
}

class DefaultRemoteSourceRepository(
    private val http: HttpClient,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) : RemoteSourceRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _config = MutableStateFlow<SubsonicConfig?>(null)
    override val config: StateFlow<SubsonicConfig?> = _config.asStateFlow()
    private var client: SubsonicClient? = null

    init {
        scope.launch {
            settings.secret(KEY)?.let { raw ->
                runCatching { json.decodeFromString(SubsonicConfig.serializer(), raw) }.getOrNull()?.let { cfg ->
                    _config.value = cfg
                    client = SubsonicClient(http, cfg)
                }
            }
        }
    }

    override suspend fun connect(baseUrl: String, username: String, password: String): Result<Unit> {
        val cfg = SubsonicConfig.fromPassword(baseUrl, username, password)
        val candidate = SubsonicClient(http, cfg)
        return runCatching { candidate.ping() }.map {
            settings.setSecret(KEY, json.encodeToString(SubsonicConfig.serializer(), cfg))
            _config.value = cfg
            client = candidate
        }
    }

    override suspend fun disconnect() {
        settings.setSecret(KEY, null)
        _config.value = null
        client = null
    }

    override suspend fun albums(): List<SubAlbum> = client?.albums() ?: emptyList()
    override suspend fun artists(): List<SubArtist> = client?.artists() ?: emptyList()
    override suspend fun playlists(): List<SubPlaylist> = client?.playlists() ?: emptyList()
    override suspend fun starred(): Starred2 = client?.starred() ?: Starred2()
    override suspend fun albumTracks(albumId: String): List<Track> =
        client?.let { c -> c.albumTracks(albumId).map { it.toTrack(c) } } ?: emptyList()
    override suspend fun playlistTracks(playlistId: String): List<Track> =
        client?.let { c -> c.playlistTracks(playlistId).map { it.toTrack(c) } } ?: emptyList()
    override suspend fun search(query: String): SearchResult3 = client?.search(query) ?: SearchResult3()
    override fun songToTrack(song: SubSong): Track = client?.let { song.toTrack(it) } ?: song.toTrack(null)

    override fun coverArtUrl(coverArtId: String?, size: Int): String? =
        if (coverArtId == null) null else client?.coverArtUrl(coverArtId, size)

    companion object {
        const val KEY = "subsonic.config"
    }
}

/** Маппинг удалённой песни в доменный [Track]: sourceUri = URL потока (проигрывается в T07). */
internal fun SubSong.toTrack(client: SubsonicClient?): Track = Track(
    stableId = "subsonic:$id",
    albumId = "subsonic:al:$albumId",
    artistId = "subsonic:ar:$artistId",
    title = title.ifBlank { id },
    artistName = artist,
    albumTitle = album,
    trackNo = track,
    year = year,
    durationMs = duration?.let { it * 1000L },
    sampleRate = samplingRate,
    bitDepth = bitDepth,
    format = AudioFormat.byExtension(suffix ?: ""),
    sourceUri = client?.streamUrl(id) ?: "subsonic:$id",
    sourceDisplayName = title,
    fileSize = size,
    modificationTime = 0L,
    hasEmbeddedArt = coverArt != null,
    addedAt = 0L,
)
