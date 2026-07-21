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
import tech.thothlab.dombra.data.lyrics.DefaultLyricsRepository
import tech.thothlab.dombra.data.remote.subsonic.DefaultRemoteSourceRepository
import tech.thothlab.dombra.data.repo.DefaultLibraryRepository
import tech.thothlab.dombra.data.repo.DefaultPlaylistRepository
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
 *
 * ПРОЦЕСС-СИНГЛТОН: один граф на процесс, переживает пересоздание Activity
 * (сворачивание/восстановление, поворот, смена системной темы). Иначе fresh-граф
 * получал бы пустую очередь плеера, а media3-сессия продолжала бы играть — и в UI
 * пропадал мини-плеер (звук есть, «сейчас играет» нет). Всё в графе на
 * applicationContext, поэтому Activity не утекает; заодно уходит утечка Room/scope/
 * MediaController на каждом пересоздании.
 */
private val graphLock = Any()

@Volatile private var cachedGraph: AppGraph? = null

fun createAndroidAppGraph(context: Context): AppGraph {
    cachedGraph?.let { return it }
    return synchronized(graphLock) {
        cachedGraph ?: buildAndroidAppGraph(context.applicationContext).also { cachedGraph = it }
    }
}

private fun buildAndroidAppGraph(context: Context): AppGraph {
    val app = context.applicationContext
    val dispatchers = AndroidDispatchers
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val db = Room.databaseBuilder<DombraDatabase>(
        context = app,
        name = app.getDatabasePath(DombraDatabase.FILE_NAME).absolutePath,
    ).buildDombraDatabase()
    val store = RoomLibraryStore(db)
    val storage = ContentUriStorageProvider(app, dispatchers.io)
    val artworkRepo = JavaFileArtworkRepository(app.cacheDir, dispatchers)
    val clock = SystemClock()
    val settingsRepo = DefaultSettingsRepository(
        SharedPreferencesSettings(app.getSharedPreferences("dombra", Context.MODE_PRIVATE)),
    )
    val engine = Media3AudioEngine(app)

    val controller = PlaybackController(
        engine = engine,
        capability = Media3Capability(),
        store = store,
        storage = storage,
        settings = settingsRepo,
        idGenerator = RandomIdGenerator(),
        clock = clock,
        scope = scope,
        random = Random.Default,
    )
    val libraryIndexer = DefaultLibraryIndexer(storage, store, artworkRepo, clock, dispatchers)
    val lyricsRepo = DefaultLyricsRepository(store, storage, clock)
    val libraryRepo = DefaultLibraryRepository(store)
    val playlistRepo = DefaultPlaylistRepository(store, clock, RandomIdGenerator())
    val httpClient = tech.thothlab.dombra.platform.createAndroidHttpClient()
    val remoteRepo = DefaultRemoteSourceRepository(httpClient, settingsRepo, scope)

    return object : AppGraph {
        override val playback = controller
        override val library: LibraryRepository = libraryRepo
        override val playlists = playlistRepo
        override val indexer: LibraryIndexer = libraryIndexer
        override val artwork = artworkRepo
        override val settings = settingsRepo
        override val remote = remoteRepo
        override val lyrics = lyricsRepo

        override suspend fun importTree(treeUri: String, displayName: String) {
            val log = Log.withTag("Import")
            val dir = SourceRef(uri = treeUri, displayName = displayName, isDirectory = true)
            storage.persistAccess(dir)
            log.i { "import start: ${Log.redactUri(treeUri)}" }
            libraryIndexer.scan(listOf(dir), fullScan = true).collect { event ->
                log.i { "scan: $event" }
            }
        }

        override suspend fun refresh() {
            // Ре-скан всех ранее закреплённых SAF-деревьев (persistable permissions).
            val sources = app.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { perm ->
                    SourceRef(
                        uri = perm.uri.toString(),
                        displayName = perm.uri.lastPathSegment ?: "Папка",
                        isDirectory = true,
                    )
                }
            if (sources.isEmpty()) return
            libraryIndexer.scan(sources, fullScan = true).collect { }
        }
    }
}

private object AndroidDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}
