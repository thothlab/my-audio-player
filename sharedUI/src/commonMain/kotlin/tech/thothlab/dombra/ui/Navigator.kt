package tech.thothlab.dombra.ui

import androidx.compose.runtime.mutableStateListOf
import tech.thothlab.dombra.domain.model.HomeSectionId

/** Экраны приложения (D-10 — собственная лёгкая навигация вместо Nav3 alpha). */
sealed interface Screen {
    /** Главный экран-каталог «Медиатека». */
    data object Home : Screen

    /** Раздел «Медиатеки» (все песни / любимые / плейлисты / исполнители / альбомы). */
    data class Collection(val section: HomeSectionId) : Screen

    /** Список треков конкретной группы (исполнитель / альбом / плейлист). */
    data class Tracks(val title: String, val ref: TrackListRef) : Screen

    data object Search : Screen

    /** Экран удалённого источника (Subsonic/Navidrome): подключение или просмотр. */
    data object Server : Screen

    /** Треки удалённого альбома. */
    data class RemoteAlbum(val albumId: String, val title: String) : Screen

    data object Player : Screen
    data object Settings : Screen

    /** Экран эквалайзера (полосы + предусиление). */
    data object Equalizer : Screen
}

/** Источник списка треков для [Screen.Tracks]. */
sealed interface TrackListRef {
    data class Artist(val id: String) : TrackListRef
    data class Album(val id: String) : TrackListRef
    data class Playlist(val id: String) : TrackListRef
}

/** Лёгкий стек навигации: push/pop, root не выталкивается. */
class Navigator(root: Screen) {
    private val stack = mutableStateListOf(root)

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
