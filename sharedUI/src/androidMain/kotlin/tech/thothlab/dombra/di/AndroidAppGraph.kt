package tech.thothlab.dombra.di

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.thothlab.dombra.core.RandomIdGenerator
import tech.thothlab.dombra.core.SystemClock
import tech.thothlab.dombra.data.settings.DefaultSettingsRepository
import tech.thothlab.dombra.data.store.InMemoryLibraryStore
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.ports.FileStat
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider
import tech.thothlab.dombra.platform.audio.Media3AudioEngine
import tech.thothlab.dombra.platform.audio.Media3Capability
import tech.thothlab.dombra.presentation.player.PlaybackController

/**
 * Android-граф (§7.3 ТЗ). Сейчас — скелет вертикального среза: media3-движок +
 * демо-трек из ассетов, чтобы проверить тракт UI → PlaybackController → ExoPlayer
 * на устройстве. Room-store, SAF-storage (T04) и импорт подключатся дальше.
 */
fun createAndroidAppGraph(context: Context): AppGraph {
    val app = context.applicationContext
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    val store = InMemoryLibraryStore()
    val settings = DefaultSettingsRepository(
        SharedPreferencesSettings(app.getSharedPreferences("dombra", Context.MODE_PRIVATE)),
    )
    val engine = Media3AudioEngine(app)

    val controller = PlaybackController(
        engine = engine,
        capability = Media3Capability(),
        store = store,
        storage = AssetStorageProvider,
        settings = settings,
        idGenerator = RandomIdGenerator(),
        clock = SystemClock(),
        scope = scope,
        random = Random.Default,
    )

    return object : AppGraph {
        override val playback = controller
        override val demoTrack = DEMO_TRACK
    }
}

/** Демо-трек из `androidApp/src/main/assets/fixture.mp3`. Временный, до импорта (T04). */
private val DEMO_TRACK: Track = run {
    val artistId = Artist.idFor("Dombra")
    Track(
        stableId = "demo-fixture",
        albumId = Album.idFor(artistId, "Demo"),
        artistId = artistId,
        title = "Демо-фикстура",
        artistName = "Dombra",
        albumTitle = "Demo",
        format = AudioFormat.MP3,
        sourceUri = "asset:///fixture.mp3",
        sourceDisplayName = "fixture.mp3",
        fileSize = 0L,
        modificationTime = 0L,
        addedAt = 0L,
    )
}

/**
 * Скелет-storage: демо-трек лежит в ассетах и всегда доступен. Настоящий
 * доступ к файлам (SAF/content URI) — T04, тогда этот стаб уходит.
 */
private object AssetStorageProvider : StorageProvider {
    override suspend fun stat(ref: SourceRef): FileStat? = FileStat(size = 0L, modificationTime = 0L)
    override suspend fun listAudioFiles(dir: SourceRef): List<SourceRef> = emptyList()
    override suspend fun readBytes(ref: SourceRef, offset: Long, length: Long): ByteArray = ByteArray(0)
    override suspend fun persistAccess(ref: SourceRef) = Unit
    override val canDeleteFiles: Boolean = false
    override suspend fun deleteFile(ref: SourceRef): Boolean = false
}
