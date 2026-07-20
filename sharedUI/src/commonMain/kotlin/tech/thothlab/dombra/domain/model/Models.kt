package tech.thothlab.dombra.domain.model

import kotlinx.serialization.Serializable

/**
 * Доменная модель (§6.1 ТЗ).
 *
 * Идентификаторы:
 * - Track.stableId — детерминированный контентный hash физического файла
 *   (sha256 of fileSize + head64KiB + tail64KiB), см. StableId.kt. Переживает переименование.
 * - Artist.id / Album.id — детерминированные ключи от нормализованного имени,
 *   поэтому повторный scan идемпотентен на любом хранилище.
 */

@Serializable
data class Artist(
    val id: String,
    val name: String,
) {
    companion object {
        const val UNKNOWN_NAME = "Unknown artist"
        fun idFor(name: String): String = "artist:" + name.trim().lowercase()
    }
}

@Serializable
data class Album(
    val id: String,
    val artistId: String,
    val title: String,
    val year: Int? = null,
    val albumArtist: String? = null,
) {
    companion object {
        const val UNKNOWN_TITLE = "Unknown album"
        fun idFor(artistKey: String, title: String): String =
            "album:" + artistKey.removePrefix("artist:") + "|" + title.trim().lowercase()
    }
}

enum class TrackAvailability { AVAILABLE, UNAVAILABLE }

@Serializable
data class ReplayGain(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
) {
    val isEmpty: Boolean
        get() = trackGainDb == null && albumGainDb == null && trackPeak == null && albumPeak == null
}

/** Контейнер/кодек по факту детекции содержимого, не по расширению. */
enum class AudioFormat(val displayName: String, val extensions: Set<String>) {
    MP3("MP3", setOf("mp3")),
    FLAC("FLAC", setOf("flac")),
    WAV("WAV", setOf("wav")),
    M4A("AAC/M4A", setOf("m4a", "aac")),
    OGG_VORBIS("OGG Vorbis", setOf("ogg")),
    OPUS("Opus", setOf("opus")),
    DSF("DSD (DSF)", setOf("dsf")),
    DFF("DSD (DFF)", setOf("dff")),
    UNKNOWN("Unknown", emptySet());

    companion object {
        val supportedExtensions: Set<String> =
            entries.flatMap { it.extensions }.toSet()

        fun byExtension(ext: String): AudioFormat =
            entries.firstOrNull { ext.lowercase() in it.extensions } ?: UNKNOWN
    }
}

@Serializable
data class Track(
    val stableId: String,
    val albumId: String,
    val artistId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val format: AudioFormat = AudioFormat.UNKNOWN,
    val sourceUri: String,
    val sourceDisplayName: String,
    val fileSize: Long,
    val modificationTime: Long,
    val replayGain: ReplayGain = ReplayGain(),
    val hasEmbeddedArt: Boolean = false,
    val hasEmbeddedLyrics: Boolean = false,
    val availability: TrackAvailability = TrackAvailability.AVAILABLE,
    val addedAt: Long,
)

@Serializable
data class Playlist(
    val id: String,
    val slug: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long? = null,
    val customCoverUri: String? = null,
    val folderSyncPath: String? = null,
    val folderSyncEnabled: Boolean = false,
)

@Serializable
data class PlaylistItem(
    val playlistId: String,
    val position: Int,
    val trackStableId: String,
)

data class PlaylistWithItems(
    val playlist: Playlist,
    val items: List<PlaylistItem>,
)

enum class LyricsType { PLAIN, SYNCED }

@Serializable
data class LyricsLine(
    val timeMs: Long? = null,
    val text: String,
)

@Serializable
data class Lyrics(
    val trackStableId: String,
    val type: LyricsType,
    val lines: List<LyricsLine>,
    val source: String,
    val fetchedAt: Long,
)

@Serializable
data class EqPreset(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val type: EqPresetType,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class EqPresetType { MANUAL, IMPORTED, BUILT_IN }

@Serializable
data class EqBand(
    val presetId: String,
    val index: Int,
    val frequencyHz: Double,
    val gainDb: Double,
    val bandwidth: Double = 1.0,
)

@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    val activePresetId: String? = null,
    val globalGainDb: Double = 0.0,
    val updatedAt: Long = 0L,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Accent-палитра — 8 образцов из дизайн-макета (экран «Настройки · Акцент»),
 * в точном порядке макета. Дефолт — Plum #b11491 (акцент Aurora Glass в обеих темах).
 * Порядок и hex сверены по handoff (turn-2 §5.6 / turn-8 §6.4).
 */
enum class AccentColor(val hex: String, val label: String) {
    AURORA("ff4d7d", "Rose"),
    VIOLET("b11491", "Plum"),
    RED("e11d48", "Crimson"),
    BLUE("38bdf8", "Sky"),
    GREEN("22c55e", "Green"),
    ORANGE("f59e0b", "Amber"),
    TEAL("2dd4bf", "Teal"),
    PURPLE("7c5cff", "Indigo"),
}

enum class AppLanguage(val tag: String) { SYSTEM(""), RUSSIAN("ru"), ENGLISH("en") }

enum class DeletionPolicy { LIBRARY_ONLY, DELETE_FILE }

enum class DsdMode { AUTO, PCM, DOP }

enum class ReplayGainMode { OFF, TRACK, ALBUM }

enum class HomeSectionId { ALL_SONGS, LIKED_SONGS, PLAYLISTS, ARTISTS, ALBUMS, ADD_SONGS }

@Serializable
data class HomeSection(
    val id: HomeSectionId,
    val visible: Boolean = true,
)

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.VIOLET,
    val homeSections: List<HomeSection> = HomeSectionId.entries.map { HomeSection(it) },
    val deletionPolicy: DeletionPolicy = DeletionPolicy.LIBRARY_ONLY,
    val dsdMode: DsdMode = DsdMode.PCM,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val replayGainPreampDb: Double = 0.0,
    val showLyricsButton: Boolean = true,
    val showSleepTimerButton: Boolean = false,
    /** Включён ли эквалайзер (UI/представление; аудио-DSP — отдельная задача). */
    val equalizerEnabled: Boolean = false,
    /** Пройден ли онбординг первого запуска (PRD-03 T01). */
    val onboardingDone: Boolean = false,
    /** Порядок сортировки по ключу коллекции (PRD-03 T03): "all"/"liked"/"artist:id"/… */
    val sortOrders: Map<String, SortOrder> = emptyMap(),
) {
    /** Толерантность к новым секциям: недостающие добавляются в конец видимыми. */
    fun withAllSections(): AppSettings {
        val known = homeSections.map { it.id }.toSet()
        val missing = HomeSectionId.entries.filter { it !in known }
        return if (missing.isEmpty()) this
        else copy(homeSections = homeSections + missing.map { HomeSection(it) })
    }
}

enum class RepeatMode { OFF, ALL, ONE }

/** Строка очереди: один трек может входить в очередь многократно (§5.8 ТЗ). */
@Serializable
data class QueueEntry(
    val entryId: String,
    val trackStableId: String,
)

@Serializable
data class PlaybackSnapshot(
    val queue: List<QueueEntry> = emptyList(),
    val originalOrder: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val timestamp: Long = 0L,
)

/** Статистика индексации (§5.3 ТЗ). */
@Serializable
data class ScanStats(
    val found: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val unavailable: Int = 0,
    val failed: Int = 0,
)
