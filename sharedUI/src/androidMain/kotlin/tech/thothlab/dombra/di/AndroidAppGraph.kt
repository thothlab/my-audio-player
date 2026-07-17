package tech.thothlab.dombra.di

import android.content.Context
import androidx.room.Room
import com.russhwolf.settings.SharedPreferencesSettings
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.core.RandomIdGenerator
import tech.thothlab.dombra.core.SystemClock
import tech.thothlab.dombra.data.indexer.DefaultLibraryIndexer
import tech.thothlab.dombra.data.repo.DefaultLibraryRepository
import tech.thothlab.dombra.data.settings.DefaultSettingsRepository
import tech.thothlab.dombra.data.store.room.DombraDatabase
import tech.thothlab.dombra.data.store.room.RoomLibraryStore
import tech.thothlab.dombra.data.store.room.buildDombraDatabase
import tech.thothlab.dombra.domain.ports.LibraryIndexer
import tech.thothlab.dombra.domain.ports.LibraryRepository
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.platform.ContentUriStorageProvider
import tech.thothlab.dombra.platform.JavaFileArtworkRepository
import tech.thothlab.dombra.platform.audio.Media3AudioEngine
import tech.thothlab.dombra.platform.audio.Media3Capability
import tech.thothlab.dombra.presentation.player.PlaybackController

/**
 * Android-граф (§7.3 ТЗ). media3-движок + SAF-хранилище + Room-store + индексатор.
 * Библиотека и доступ к папке (persistable permission) переживают рестарт.
 */
fun createAndroidAppGraph(context: Context): AppGraph {
    val app = context.applicationContext
    val dispatchers = AndroidDispatchers
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val db = Room.databaseBuilder<DombraDatabase>(
        context = app,
        name = app.getDatabasePath(DombraDatabase.FILE_NAME).absolutePath,
    ).buildDombraDatabase()
    val store = RoomLibraryStore(db)
    val storage = ContentUriStorageProvider(app, dispatchers.io)
    val artwork = JavaFileArtworkRepository(app.cacheDir, dispatchers)
    val clock = SystemClock()
    val settings = DefaultSettingsRepository(
        SharedPreferencesSettings(app.getSharedPreferences("dombra", Context.MODE_PRIVATE)),
    )
    val engine = Media3AudioEngine(app)

    val controller = PlaybackController(
        engine = engine,
        capability = Media3Capability(),
        store = store,
        storage = storage,
        settings = settings,
        idGenerator = RandomIdGenerator(),
        clock = clock,
        scope = scope,
        random = Random.Default,
    )
    val libraryIndexer = DefaultLibraryIndexer(storage, store, artwork, clock, dispatchers)
    val libraryRepo = DefaultLibraryRepository(store)

    return object : AppGraph {
        override val playback = controller
        override val library: LibraryRepository = libraryRepo
        override val indexer: LibraryIndexer = libraryIndexer

        override suspend fun importTree(treeUri: String, displayName: String) {
            val log = Log.withTag("Import")
            val dir = SourceRef(uri = treeUri, displayName = displayName, isDirectory = true)
            storage.persistAccess(dir)
            log.i { "import start: ${Log.redactUri(treeUri)}" }
            libraryIndexer.scan(listOf(dir), fullScan = true).collect { event ->
                log.i { "scan: $event" }
            }
        }
    }
}

private object AndroidDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
