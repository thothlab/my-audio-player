package tech.thothlab.dombra.data.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import tech.thothlab.dombra.data.repo.testTrack
import tech.thothlab.dombra.domain.model.EqBand
import tech.thothlab.dombra.domain.model.EqPreset
import tech.thothlab.dombra.domain.model.EqPresetType
import tech.thothlab.dombra.domain.model.EqSettings
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.LyricsLine
import tech.thothlab.dombra.domain.model.LyricsType
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistItem

/** Контракт LibraryStore против персистентной (web) реализации. */
class PersistentLibraryStoreTest : LibraryStoreContractTest() {

    override fun createStore(): LibraryStore =
        PersistentLibraryStore(persist = {}, restore = { null })

    /**
     * Сценарий «Перезапуск вкладки» (specs/storage): всё, что записано первой
     * «вкладкой», после restore+load видно второй. Снапшот гоняется через JSON —
     * как в реальном kstore/localStorage.
     */
    @Test
    fun tabRestartRestoresEverything() = runTest {
        var persisted: String? = null
        val json = Json
        fun store() = PersistentLibraryStore(
            persist = { persisted = json.encodeToString(LibrarySnapshot.serializer(), it) },
            restore = { persisted?.let { json.decodeFromString(LibrarySnapshot.serializer(), it) } },
        )

        val first = store()
        first.upsertTracks(listOf(testTrack("a", "Alpha"), testTrack("b", "Beta")))
        first.setFavorite("a", true)
        first.upsertPlaylist(Playlist("p1", "mix", "Mix", createdAt = 1L, updatedAt = 1L))
        first.replaceItems("p1", listOf(PlaylistItem("p1", 0, "b"), PlaylistItem("p1", 1, "a")))
        first.saveLyrics(
            Lyrics("a", LyricsType.SYNCED, listOf(LyricsLine(0L, "Строка")), "lrclib", 5L),
        )
        val preset = EqPreset("pr", "Rock", builtIn = false, type = EqPresetType.MANUAL, createdAt = 1L, updatedAt = 1L)
        first.saveEqPreset(preset, listOf(EqBand("pr", 0, 60.0, 3.0)))
        first.saveEqSettings(EqSettings(enabled = true, activePresetId = "pr", updatedAt = 2L))

        val second = store()
        second.load()

        assertEquals(first.getAllTracks().toSet(), second.getAllTracks().toSet())
        assertEquals(setOf("a"), second.getFavorites())
        val playlist = second.getPlaylist("p1")
        assertEquals("Mix", playlist?.playlist?.title)
        assertEquals(listOf("b", "a"), playlist?.items?.map { it.trackStableId })
        assertEquals(first.getLyrics("a"), second.getLyrics("a"))
        assertEquals(listOf(preset), second.eqPresetsFlow().first())
        assertEquals(listOf(EqBand("pr", 0, 60.0, 3.0)), second.getEqBands("pr"))
        val settings = second.eqSettingsFlow().first()
        assertTrue(settings.enabled)
        assertEquals("pr", settings.activePresetId)
    }

    @Test
    fun loadWithoutSnapshotKeepsStoreEmpty() = runTest {
        val store = PersistentLibraryStore(persist = {}, restore = { null })
        store.load()
        assertEquals(emptyList(), store.getAllTracks())
        assertEquals(emptyList(), store.getAllPlaylists())
    }
}
