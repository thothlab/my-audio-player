package tech.thothlab.dombra.data.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import tech.thothlab.dombra.data.repo.testTrack
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.EqBand
import tech.thothlab.dombra.domain.model.EqPreset
import tech.thothlab.dombra.domain.model.EqPresetType
import tech.thothlab.dombra.domain.model.EqSettings
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.LyricsLine
import tech.thothlab.dombra.domain.model.LyricsType
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistItem
import tech.thothlab.dombra.domain.model.TrackAvailability

/**
 * Контракт LibraryStore: один и тот же набор тестов гоняется против
 * InMemory (commonTest), Room (jvmTest) и web-хранилища — поведение
 * реализаций обязано совпадать (T03, specs/storage).
 */
abstract class LibraryStoreContractTest {

    protected abstract fun createStore(): LibraryStore

    private fun playlist(id: String, slug: String = id) = Playlist(
        id = id, slug = slug, title = "Playlist $id", createdAt = 1L, updatedAt = 1L,
    )

    private fun item(playlistId: String, position: Int, trackId: String) =
        PlaylistItem(playlistId = playlistId, position = position, trackStableId = trackId)

    // --- Tracks ---

    @Test
    fun upsertAndGetTracks() = runTest {
        val store = createStore()
        store.upsertTracks(listOf(testTrack("a", "Alpha"), testTrack("b", "Beta")))
        assertEquals("Alpha", store.getTrack("a")?.title)
        assertEquals(setOf("Alpha", "Beta"), store.getAllTracks().map { it.title }.toSet())
        assertEquals(2, store.tracksFlow().first().size)
        assertNull(store.getTrack("missing"))
    }

    @Test
    fun upsertOverwritesByStableId() = runTest {
        val store = createStore()
        store.upsertTracks(listOf(testTrack("a", "Old title")))
        store.upsertTracks(listOf(testTrack("a", "New title")))
        assertEquals("New title", store.getTrack("a")?.title)
        assertEquals(1, store.getAllTracks().size)
    }

    @Test
    fun trackFieldsSurviveRoundtrip() = runTest {
        val store = createStore()
        val track = testTrack("rt", "Полночь", artist = "Ночь", album = "Зима").copy(
            year = 2001, trackNo = 7, discNo = 2,
            durationMs = 123_456L, sampleRate = 96_000, bitDepth = 24, channels = 2,
            replayGain = tech.thothlab.dombra.domain.model.ReplayGain(
                trackGainDb = -6.5, albumGainDb = -5.0, trackPeak = 0.98, albumPeak = 0.99,
            ),
            hasEmbeddedArt = true, hasEmbeddedLyrics = true,
        )
        store.upsertTracks(listOf(track))
        assertEquals(track, store.getTrack("rt"))
    }

    @Test
    fun deleteTracksAlsoRemovesFavoriteAndLyrics() = runTest {
        val store = createStore()
        store.upsertTracks(listOf(testTrack("a", "A"), testTrack("b", "B")))
        store.setFavorite("a", true)
        store.saveLyrics(
            Lyrics("a", LyricsType.PLAIN, listOf(LyricsLine(text = "line")), "embedded", 1L),
        )
        store.deleteTracks(listOf("a"))
        assertNull(store.getTrack("a"))
        assertNotNull(store.getTrack("b"))
        assertEquals(emptySet(), store.getFavorites())
        assertNull(store.getLyrics("a"))
    }

    @Test
    fun updateAvailability() = runTest {
        val store = createStore()
        store.upsertTracks(listOf(testTrack("a", "A")))
        store.updateAvailability("a", TrackAvailability.UNAVAILABLE)
        assertEquals(TrackAvailability.UNAVAILABLE, store.getTrack("a")?.availability)
        // несуществующий id — no-op
        store.updateAvailability("missing", TrackAvailability.UNAVAILABLE)
    }

    // --- Artists / Albums ---

    @Test
    fun artistsAndAlbumsRoundtrip() = runTest {
        val store = createStore()
        val artist = Artist(Artist.idFor("Solo"), "Solo")
        val album = Album(Album.idFor(artist.id, "LP"), artist.id, "LP", year = 1999, albumArtist = "Solo")
        store.upsertArtists(listOf(artist))
        store.upsertAlbums(listOf(album))
        assertEquals(artist, store.getArtist(artist.id))
        assertEquals(album, store.getAlbum(album.id))
        assertEquals(listOf(artist), store.artistsFlow().first())
        assertEquals(listOf(album), store.albumsFlow().first())
    }

    @Test
    fun pruneOrphansKeepsUsedAndDropsUnused() = runTest {
        val store = createStore()
        val used = Artist(Artist.idFor("Used"), "Used")
        val orphan = Artist(Artist.idFor("Orphan"), "Orphan")
        val usedAlbum = Album(Album.idFor(used.id, "Kept"), used.id, "Kept")
        val orphanAlbum = Album(Album.idFor(orphan.id, "Gone"), orphan.id, "Gone")
        store.upsertArtists(listOf(used, orphan))
        store.upsertAlbums(listOf(usedAlbum, orphanAlbum))
        store.upsertTracks(listOf(testTrack("t", "T", artist = "Used", album = "Kept")))

        store.pruneOrphans()

        assertEquals(listOf(used), store.artistsFlow().first())
        assertEquals(listOf(usedAlbum), store.albumsFlow().first())
    }

    // --- Favorites ---

    @Test
    fun favoritesSetUnsetIdempotent() = runTest {
        val store = createStore()
        store.setFavorite("a", true)
        store.setFavorite("a", true)
        store.setFavorite("b", true)
        assertEquals(setOf("a", "b"), store.getFavorites())
        assertEquals(setOf("a", "b"), store.favoritesFlow().first())
        store.setFavorite("a", false)
        store.setFavorite("a", false)
        assertEquals(setOf("b"), store.getFavorites())
    }

    // --- Playlists ---

    @Test
    fun playlistUpsertKeepsItems() = runTest {
        val store = createStore()
        store.upsertPlaylist(playlist("p1"))
        store.replaceItems("p1", listOf(item("p1", 0, "a"), item("p1", 1, "b")))

        store.upsertPlaylist(playlist("p1").copy(title = "Renamed"))

        val loaded = store.getPlaylist("p1")
        assertEquals("Renamed", loaded?.playlist?.title)
        assertEquals(listOf("a", "b"), loaded?.items?.map { it.trackStableId })
    }

    @Test
    fun replaceItemsRenumbersPositions() = runTest {
        val store = createStore()
        store.upsertPlaylist(playlist("p1"))
        // позиции нарочно «дырявые» и не по порядку
        store.replaceItems("p1", listOf(item("p1", 5, "x"), item("p1", 9, "y"), item("p1", 2, "z")))
        val items = store.getPlaylist("p1")?.items.orEmpty()
        assertEquals(listOf(0, 1, 2), items.map { it.position })
        assertEquals(listOf("x", "y", "z"), items.map { it.trackStableId })
    }

    @Test
    fun replaceItemsOnMissingPlaylistIsNoop() = runTest {
        val store = createStore()
        store.replaceItems("ghost", listOf(item("ghost", 0, "a")))
        assertNull(store.getPlaylist("ghost"))
        assertEquals(emptyList(), store.getAllPlaylists())
    }

    @Test
    fun deletePlaylistRemovesItems() = runTest {
        val store = createStore()
        store.upsertPlaylist(playlist("p1"))
        store.upsertPlaylist(playlist("p2"))
        store.replaceItems("p1", listOf(item("p1", 0, "a")))
        store.deletePlaylist("p1")
        assertNull(store.getPlaylist("p1"))
        assertEquals(listOf("p2"), store.getAllPlaylists().map { it.playlist.id })
        assertNull(store.playlistFlow("p1").first())
        assertEquals("p2", store.playlistFlow("p2").first()?.playlist?.id)

        // пересоздание с тем же id не должно увидеть старые элементы
        store.upsertPlaylist(playlist("p1"))
        assertEquals(emptyList(), store.getPlaylist("p1")?.items)
    }

    // --- Lyrics ---

    @Test
    fun lyricsRoundtripAndDelete() = runTest {
        val store = createStore()
        val synced = Lyrics(
            trackStableId = "a",
            type = LyricsType.SYNCED,
            lines = listOf(LyricsLine(0L, "Первая"), LyricsLine(5_000L, "Вторая")),
            source = "lrclib",
            fetchedAt = 42L,
        )
        store.saveLyrics(synced)
        assertEquals(synced, store.getLyrics("a"))
        store.deleteLyrics("a")
        assertNull(store.getLyrics("a"))
    }

    // --- EQ ---

    @Test
    fun eqSettingsDefaultWhenEmpty() = runTest {
        val store = createStore()
        assertEquals(EqSettings(), store.eqSettingsFlow().first())
    }

    @Test
    fun eqPresetSaveReplaceDelete() = runTest {
        val store = createStore()
        val preset = EqPreset("pr1", "Rock", builtIn = false, type = EqPresetType.MANUAL, createdAt = 1L, updatedAt = 1L)
        val bands = listOf(
            EqBand("pr1", 0, 60.0, 3.0),
            EqBand("pr1", 1, 1_000.0, -2.0),
        )
        store.saveEqPreset(preset, bands)
        store.saveEqSettings(EqSettings(enabled = true, activePresetId = "pr1", globalGainDb = -1.0, updatedAt = 2L))

        assertEquals(listOf(preset), store.eqPresetsFlow().first())
        assertEquals(bands, store.getEqBands("pr1"))
        assertEquals("pr1", store.eqSettingsFlow().first().activePresetId)

        // повторное сохранение заменяет полосы целиком
        val narrower = listOf(EqBand("pr1", 0, 120.0, 1.5))
        store.saveEqPreset(preset.copy(updatedAt = 3L), narrower)
        assertEquals(narrower, store.getEqBands("pr1"))

        // удаление активного пресета сбрасывает activePresetId, но не остальные настройки
        store.deleteEqPreset("pr1")
        assertEquals(emptyList(), store.eqPresetsFlow().first())
        assertEquals(emptyList(), store.getEqBands("pr1"))
        val settings = store.eqSettingsFlow().first()
        assertNull(settings.activePresetId)
        assertTrue(settings.enabled)
    }
}

class InMemoryLibraryStoreTest : LibraryStoreContractTest() {
    override fun createStore(): LibraryStore = InMemoryLibraryStore()
}
