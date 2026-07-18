package tech.thothlab.dombra.data.remote.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.thothlab.dombra.core.Md5

/** Конфиг подключения к Subsonic/OpenSubsonic-серверу. Пароль не хранится — только соль + токен. */
@Serializable
data class SubsonicConfig(
    val baseUrl: String,
    val username: String,
    val salt: String,
    val token: String,
    val label: String = "",
) {
    companion object {
        /** Строит конфиг из пароля: случайная соль + token = md5(password + salt). */
        fun fromPassword(baseUrl: String, username: String, password: String, random: Random = Random): SubsonicConfig {
            val salt = (0 until 12).map { HEX[random.nextInt(16)] }.joinToString("")
            return SubsonicConfig(
                baseUrl = baseUrl.trim().trimEnd('/'),
                username = username.trim(),
                salt = salt,
                token = Md5.hex(password + salt),
                label = baseUrl.trim().removePrefix("https://").removePrefix("http://").trimEnd('/'),
            )
        }

        private const val HEX = "0123456789abcdef"
    }
}

class SubsonicException(message: String) : Exception(message)

/** Тонкий клиент Subsonic REST (Navidrome). Стрим/обложки — по URL, метаданные — через json. */
class SubsonicClient(
    private val http: HttpClient,
    val config: SubsonicConfig,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun urlFor(method: String, params: List<Pair<String, String>>): String {
        val common = buildString {
            append("u=").append(config.username.encodeURLParameter())
            append("&t=").append(config.token)
            append("&s=").append(config.salt)
            append("&v=1.16.1&c=dombra&f=json")
        }
        val extra = params.joinToString("") { "&${it.first}=${it.second.encodeURLParameter()}" }
        return "${config.baseUrl}/rest/$method.view?$common$extra"
    }

    private suspend fun request(method: String, params: List<Pair<String, String>> = emptyList()): SubsonicResponse {
        val text = http.get(urlFor(method, params)).bodyAsText()
        val resp = json.decodeFromString(SubsonicEnvelope.serializer(), text).response
        if (resp.status != "ok") throw SubsonicException(resp.error?.message ?: "Сервер вернул ошибку")
        return resp
    }

    suspend fun ping() { request("ping") }

    suspend fun albums(type: String = "alphabeticalByName", size: Int = 100, offset: Int = 0): List<SubAlbum> =
        request("getAlbumList2", listOf("type" to type, "size" to size.toString(), "offset" to offset.toString()))
            .albumList2?.album ?: emptyList()

    suspend fun artists(): List<SubArtist> =
        request("getArtists").artists?.index?.flatMap { it.artist } ?: emptyList()

    suspend fun albumTracks(id: String): List<SubSong> =
        request("getAlbum", listOf("id" to id)).album?.song ?: emptyList()

    suspend fun starred(): Starred2 =
        request("getStarred2").starred2 ?: Starred2()

    suspend fun playlists(): List<SubPlaylist> =
        request("getPlaylists").playlists?.playlist ?: emptyList()

    suspend fun playlistTracks(id: String): List<SubSong> =
        request("getPlaylist", listOf("id" to id)).playlist?.entry ?: emptyList()

    suspend fun search(query: String, songCount: Int = 30, artistCount: Int = 20, albumCount: Int = 20): SearchResult3 =
        request(
            "search3",
            listOf(
                "query" to query,
                "songCount" to songCount.toString(),
                "artistCount" to artistCount.toString(),
                "albumCount" to albumCount.toString(),
            ),
        ).searchResult3 ?: SearchResult3()

    /** URL потока в исходном качестве (без транскода) — lossless. */
    fun streamUrl(songId: String): String = urlFor("stream", listOf("id" to songId, "format" to "raw"))

    fun coverArtUrl(coverArtId: String, size: Int = 512): String =
        urlFor("getCoverArt", listOf("id" to coverArtId, "size" to size.toString()))
}
