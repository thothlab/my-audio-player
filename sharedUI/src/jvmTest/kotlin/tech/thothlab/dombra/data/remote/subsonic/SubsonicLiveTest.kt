package tech.thothlab.dombra.data.remote.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Живая проверка Subsonic-клиента против реального сервера.
 * Учётка передаётся через env (DOMBRA_SUB_URL/USER/PASS) — секреты в git не попадают.
 * Без env тест — no-op (пропускается).
 */
class SubsonicLiveTest {
    @Test
    fun liveBrowse() {
        val url = System.getenv("DOMBRA_SUB_URL") ?: return
        val user = System.getenv("DOMBRA_SUB_USER") ?: return
        val pass = System.getenv("DOMBRA_SUB_PASS") ?: return

        val http = HttpClient(OkHttp)
        val client = SubsonicClient(http, SubsonicConfig.fromPassword(url, user, pass))
        runBlocking {
            client.ping()
            val albums = client.albums(size = 5)
            println("[SUB] albums=${albums.size} first='${albums.firstOrNull()?.name}'")
            check(albums.isNotEmpty()) { "no albums" }

            val songs = client.albumTracks(albums.first().id)
            val s0 = songs.first()
            println("[SUB] albumTracks=${songs.size} first='${s0.title}' suffix=${s0.suffix} size=${s0.size}")
            println("[SUB] stream=${client.streamUrl(s0.id).take(90)}")
            check(songs.isNotEmpty()) { "no songs" }

            val artists = client.artists()
            val playlists = client.playlists()
            val starred = client.starred()
            println("[SUB] artists=${artists.size} playlists=${playlists.size} starredSongs=${starred.song.size}")
            check(artists.isNotEmpty()) { "no artists" }
        }
        http.close()
    }
}
