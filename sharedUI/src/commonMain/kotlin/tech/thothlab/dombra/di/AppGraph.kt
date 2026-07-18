package tech.thothlab.dombra.di

import tech.thothlab.dombra.domain.ports.ArtworkRepository
import tech.thothlab.dombra.domain.ports.LibraryIndexer
import tech.thothlab.dombra.domain.ports.LibraryRepository
import tech.thothlab.dombra.domain.ports.PlaylistRepository
import tech.thothlab.dombra.domain.ports.SettingsRepository
import tech.thothlab.dombra.presentation.player.PlaybackController

/**
 * Композиционный корень приложения (§7.3 ТЗ, решение D-09 — ручной DI).
 * Каждая платформа строит свой граф со своим `AudioEngine`/`StorageProvider`/БД
 * и передаёт его в общий UI.
 */
interface AppGraph {
    val playback: PlaybackController
    val library: LibraryRepository
    val playlists: PlaylistRepository
    val indexer: LibraryIndexer
    val artwork: ArtworkRepository
    val settings: SettingsRepository

    /** Проиндексировать выбранную папку (SAF-дерево / каталог) и закрепить доступ. */
    suspend fun importTree(treeUri: String, displayName: String)

    /** Ручное обновление: ре-скан всех ранее закреплённых источников (идемпотентно). */
    suspend fun refresh()
}
