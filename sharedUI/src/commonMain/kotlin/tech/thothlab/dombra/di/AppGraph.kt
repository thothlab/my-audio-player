package tech.thothlab.dombra.di

import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.presentation.player.PlaybackController

/**
 * Композиционный корень приложения (§7.3 ТЗ, решение D-09 — ручной DI).
 * Каждая платформа строит свой граф со своим `AudioEngine`/`StorageProvider`/БД
 * и передаёт его в общий UI. По мере роста UI сюда добавляются репозитории.
 *
 * `demoTrack` — временный: пока нет импорта (T04) и экрана библиотеки (T06),
 * даёт что проиграть для проверки тракта. Убрать вместе с появлением библиотеки.
 */
interface AppGraph {
    val playback: PlaybackController
    val demoTrack: Track?
}
