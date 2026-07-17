package tech.thothlab.dombra.data.store.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import tech.thothlab.dombra.core.ioDispatcher

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        LyricsEntity::class,
        EqPresetEntity::class,
        EqBandEntity::class,
        EqSettingsEntity::class,
    ],
    version = DombraDatabase.VERSION,
    exportSchema = true,
)
@ConstructedBy(DombraDatabaseConstructor::class)
abstract class DombraDatabase : RoomDatabase() {
    abstract fun dao(): DombraDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "dombra.db"
    }
}

/** Room-компилятор генерирует actual для каждого таргета (android/jvm/ios). */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object DombraDatabaseConstructor : RoomDatabaseConstructor<DombraDatabase> {
    override fun initialize(): DombraDatabase
}

/**
 * Реестр миграций: при повышении [DombraDatabase.VERSION] сюда добавляется
 * Migration(n, n+1), эталон прежней схемы остаётся в sharedUI/schemas/.
 */
internal val DOMBRA_MIGRATIONS: Array<Migration> = emptyArray()

/**
 * Единая точка сборки: bundled-драйвер SQLite, IO-диспетчер запросов и миграции
 * одинаковы на android/jvm/ios; платформенный AppGraph передаёт builder
 * со своим путём к файлу БД (или in-memory в тестах).
 */
fun RoomDatabase.Builder<DombraDatabase>.buildDombraDatabase(): DombraDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher())
        .addMigrations(*DOMBRA_MIGRATIONS)
        .build()
