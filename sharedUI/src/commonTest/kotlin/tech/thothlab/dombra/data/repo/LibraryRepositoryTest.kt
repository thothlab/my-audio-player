package tech.thothlab.dombra.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tech.thothlab.dombra.data.store.InMemoryLibraryStore
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.ports.TrackSort

internal fun testTrack(
    stableId: String,
    title: String,
    artist: String = "Artist",
    album: String = "Album",
    trackNo: Int? = null,
    addedAt: Long = 0L,
): Track {
    val artistId = Artist.idFor(artist)
    val albumId = Album.idFor(artistId, album)
    return Track(
        stableId = stableId, albumId = albumId, artistId = artistId,
        title = title, artistName = artist, albumTitle = album,
        trackNo = trackNo, format = AudioFormat.MP3,
        sourceUri = "/music/$stableId.mp3", sourceDisplayName = "$stableId.mp3",
        fileSize = 1000L, modificationTime = 1L, addedAt = addedAt,
    )
}

class LibraryRepositoryTest {

    private val store = InMemoryLibraryStore()
    private val repo = DefaultLibraryRepository(store)

    @Test
    fun searchIsCaseInsensitiveAndEmptySafe() = runTest {
        store.upsertTracks(
            listOf(
                testTrack("1", "Sunrise", artist = "Alpha"),
                testTrack("2", "Moonlight", artist = "Beta"),
            ),
        )
        assertEquals(2, repo.tracks(query = "").first().size)
        assertEquals(listOf("Sunrise"), repo.tracks(query = "sunRISE").first().map { it.title })
        assertEquals(listOf("Moonlight"), repo.tracks(query = "beta").first().map { it.title })
        assertTrue(repo.tracks(query = "   ").first().size == 2)
    }

    @Test
    fun sortByAddedDateDescending() = runTest {
        store.upsertTracks(
            listOf(
                testTrack("1", "Old", addedAt = 100),
                testTrack("2", "New", addedAt = 300),
                testTrack("3", "Mid", addedAt = 200),
            ),
        )
        assertEquals(
            listOf("New", "Mid", "Old"),
            repo.tracks(sort = TrackSort.ADDED_DATE).first().map { it.title },
        )
    }

    @Test
    fun albumTracksInTrackOrder() = runTest {
        store.upsertTracks(
            listOf(
                testTrack("1", "Третий", album = "LP", trackNo = 3),
                testTrack("2", "Первый", album = "LP", trackNo = 1),
                testTrack("3", "Второй", album = "LP", trackNo = 2),
            ),
        )
        val albumId = Album.idFor(Artist.idFor("Artist"), "LP")
        assertEquals(
            listOf("Первый", "Второй", "Третий"),
            repo.albumTracks(albumId).first().map { it.title },
        )
    }

    @Test
    fun favoritesReactive() = runTest {
        store.upsertTracks(listOf(testTrack("1", "A"), testTrack("2", "B")))
        repo.setFavorite("2", true)
        assertEquals(listOf("B"), repo.favoriteTracks().first().map { it.title })
        repo.setFavorite("2", false)
        assertEquals(emptyList(), repo.favoriteTracks().first().map { it.title })
    }

    @Test
    fun removeFromLibraryPrunesOrphans() = runTest {
        store.upsertArtists(listOf(Artist(Artist.idFor("Solo"), "Solo")))
        store.upsertAlbums(
            listOf(Album(Album.idFor(Artist.idFor("Solo"), "Single"), Artist.idFor("Solo"), "Single")),
        )
        store.upsertTracks(listOf(testTrack("1", "Only", artist = "Solo", album = "Single")))
        repo.removeFromLibrary("1")
        assertEquals(emptyList(), repo.artists().first())
        assertEquals(emptyList(), repo.albums().first())
    }
}
