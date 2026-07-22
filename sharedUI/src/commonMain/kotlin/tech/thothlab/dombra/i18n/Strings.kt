package tech.thothlab.dombra.i18n

import androidx.compose.runtime.compositionLocalOf
import tech.thothlab.dombra.domain.model.AppLanguage
import tech.thothlab.dombra.domain.model.SortOrder
import tech.thothlab.dombra.domain.model.ThemeMode

/**
 * Локализованные строки UI. Runtime-переключение языка без пересоздания Activity и на всех
 * таргетах: [LocalStrings] держит [Strings] для текущего [AppLanguage] (из настроек),
 * `compositionLocalOf` — потребители перекомпонуются при смене языка. Каждая строка задаётся
 * один раз обоими вариантами через [t]; параметризованные и множественные — функциями.
 * По умолчанию (SYSTEM) — русский.
 */
class Strings(lang: AppLanguage) {
    private val en = lang == AppLanguage.ENGLISH
    private fun t(ru: String, eng: String) = if (en) eng else ru

    // Общее / тулбары
    val mediaLibrary = t("Медиатека", "Library")
    val settings = t("Настройки", "Settings")
    val back = t("Назад", "Back")
    val done = t("Готово", "Done")
    val cancel = t("Отмена", "Cancel")
    val close = t("Закрыть", "Close")
    val loading = t("Загрузка…", "Loading…")

    // Медиатека — разделы
    val allSongs = t("Все песни", "All songs")
    val likedSongs = t("Любимые песни", "Liked songs")
    val playlists = t("Плейлисты", "Playlists")
    val artists = t("Исполнители", "Artists")
    val albums = t("Альбомы", "Albums")
    val openFiles = t("Открыть файлы", "Open files")
    val yourFavorites = t("Ваше избранное", "Your favorites")
    val yourPlaylists = t("Ваши плейлисты", "Your playlists")
    val noPlaylistsTitle = t("Пока нет плейлистов", "No playlists yet")
    val noPlaylistsBody = t("Создайте первый и добавляйте в него любимые треки", "Create one and start adding your favorite tracks")
    val browseByArtists = t("Просмотр по исполнителям", "Browse by artist")
    val browseByAlbums = t("Просмотр по альбомам", "Browse by album")
    val importMusicFiles = t("Импортировать музыкальные файлы", "Import music files")
    fun songsCount(n: Int) = if (en) "$n songs" else "$n песен"
    fun tracksCount(n: Int) = if (en) "$n tracks" else "$n треков"
    fun albumsCount(n: Int) = if (en) "$n albums" else "$n альбомов"
    val playlist = t("Плейлист", "Playlist")

    // Списки / сортировка
    val sortTitle = t("СОРТИРОВКА", "SORT")
    val albumSortAZ = t("По алфавиту", "Alphabetical")
    val albumSortRecent = t("По дате добавления", "Date added")
    val albumSortYear = t("По году", "By year")
    fun sortLabel(o: SortOrder) = when (o) {
        SortOrder.MANUAL -> t("Вручную", "Manual")
        SortOrder.DATE_ADDED_DESC -> t("Дата добавления · новые", "Date added · newest")
        SortOrder.DATE_ADDED_ASC -> t("Дата добавления · старые", "Date added · oldest")
        SortOrder.TITLE_AZ -> t("Название · А–Я", "Title · A–Z")
        SortOrder.TITLE_ZA -> t("Название · Я–А", "Title · Z–A")
        SortOrder.ARTIST_AZ -> t("Исполнитель · А–Я", "Artist · A–Z")
        SortOrder.ARTIST_ZA -> t("Исполнитель · Я–А", "Artist · Z–A")
        SortOrder.SIZE_DESC -> t("Размер · большие", "Size · largest")
        SortOrder.SIZE_ASC -> t("Размер · маленькие", "Size · smallest")
    }

    // Поиск
    val searchAll = t("Все", "All")
    val songs = t("Песни", "Songs")
    val searchPlaceholder = t("Название, исполнитель, альбом", "Title, artist, album")
    val nothingFound = t("Ничего не найдено", "Nothing found")
    val artistLabel = t("исполнитель", "artist")
    val albumLabel = t("альбом", "album")

    // Плеер / плейлисты
    val noTrack = t("нет трека", "no track")
    val nowPlaying = t("СЕЙЧАС ИГРАЕТ", "NOW PLAYING")
    val addToPlaylist = t("Добавить в плейлист", "Add to playlist")
    val createPlaylist = t("Создать плейлист", "Create playlist")
    val newPlaylist = t("Новый плейлист", "New playlist")
    val create = t("Создать", "Create")
    val undo = t("Отменить", "Undo")
    val stop = t("Остановить", "Stop")
    val name = t("Название", "Name")
    val playNext = t("Играть следующим", "Play next")
    val addToQueue = t("В очередь", "Add to queue")

    // Очередь
    val queue = t("Очередь", "Queue")
    val played = t("Проиграно", "Played")
    val clearQueue = t("Очистить", "Clear")
    val upNext = t("Далее", "Up next")
    val listen = t("Слушать", "Play")
    val shuffle = t("Микс", "Shuffle")
    fun addedTo(pl: String) = if (en) "Added to «$pl»" else "Добавлено в «$pl»"
    fun trackWillBeAdded(title: String) =
        if (en) "Track «$title» will be added right away" else "Трек «$title» будет добавлен сразу"

    // Текст песни
    val lyrics = t("Текст", "Lyrics")
    val lyricsTitle = t("ТЕКСТ ПЕСНИ", "LYRICS")
    val lyricsLoading = t("Загрузка текста…", "Loading lyrics…")
    val noLyricsTitle = t("Нет текста", "No lyrics")
    val noLyricsBody = t("Для этого трека текст не найден", "Lyrics not found for this track")

    // Эквалайзер
    val eqFlat = t("Плоский", "Flat")
    val eqRock = t("Рок", "Rock")
    val eqJazz = t("Джаз", "Jazz")
    val eqCustom = t("Свой", "Custom")
    val preamp = t("Предусиление", "Preamp")

    // Настройки
    val theme = t("Тема", "Theme")
    val accentColor = t("Цвет акцента", "Accent color")
    val sound = t("Звук", "Sound")
    val language = t("Язык", "Language")
    val russian = t("Русский", "Russian")
    val english = t("Английский", "English")
    val systemTheme = t("Системная", "System")
    val lightTheme = t("Светлая", "Light")
    val darkTheme = t("Тёмная", "Dark")
    fun themeName(m: ThemeMode) = when (m) {
        ThemeMode.SYSTEM -> systemTheme
        ThemeMode.LIGHT -> lightTheme
        ThemeMode.DARK -> darkTheme
    }
    val replayGain = t("Нормализация громкости", "ReplayGain")
    val sleepTimer = t("Таймер сна", "Sleep timer")
    val sleepOff = t("Выкл", "Off")
    val sleepEndOfTrack = t("До конца трека", "End of track")
    fun sleepMinutes(n: Int) = if (en) "$n min" else "$n мин"
    val equalizer = t("Эквалайзер", "Equalizer")
    val sources = t("Источники", "Sources")

    // Сервер / Subsonic
    val server = t("Сервер", "Server")
    val serverCard = "Сервер (Navidrome / Subsonic)".let { if (en) "Server (Navidrome / Subsonic)" else it }
    val serverSubsonic = t("Сервер Subsonic", "Subsonic server")
    val notConnected = t("Не подключён", "Not connected")
    val serverAddress = t("Адрес сервера", "Server address")
    val username = t("Имя пользователя", "Username")
    val password = t("Пароль", "Password")
    val checkAndConnect = t("Проверить и подключить", "Check and connect")
    val disconnectServer = t("Отключить сервер", "Disconnect server")
    val searchServer = t("Поиск по серверу", "Search server")
    val subsonicNote = t(
        "Navidrome / OpenSubsonic. Стрим без потерь качества.",
        "Navidrome / OpenSubsonic. Lossless streaming.",
    )
    val passwordNote = t(
        "Пароль не хранится в открытом виде — только salt + token (md5). Поток запрашивается как format=raw.",
        "The password isn't stored in plaintext — only salt + token (md5). Stream is requested as format=raw.",
    )
    fun connectedTo(label: String) = if (en) "Connected: $label" else "Подключён: $label"
    fun connectFailed(msg: String?) =
        if (en) "Couldn't connect: $msg" else "Не удалось подключиться: $msg"

    // Онбординг
    val welcomeTitle = t("Добро пожаловать в Dombra", "Welcome to Dombra")
    val welcomeBody = t(
        "Плеер для музыки без потери качества — ALAC, FLAC, MP3, Opus и другое. Локально и с вашего сервера.",
        "A lossless music player — ALAC, FLAC, MP3, Opus and more. Local and from your server.",
    )
    val addMusicTitle = t("Добавьте свою музыку", "Add your music")
    val addMusicBody = t(
        "Выберите папку с музыкой на устройстве. Подключить свой сервер можно позже в настройках.",
        "Pick a music folder on your device. You can connect your own server later in settings.",
    )
    val pickFolder = t("Выбрать папку", "Pick folder")
    val skip = t("Пропустить", "Skip")
    val continueBtn = t("Продолжить", "Continue")
    val start = t("Начать", "Start")

    // Сплеш
    val splashSubtitle = t("МУЗЫКА БЕЗ ПОТЕРЬ", "LOSSLESS MUSIC")
}

/** Локализованные строки текущего языка (по умолчанию — русский). */
val LocalStrings = compositionLocalOf { Strings(AppLanguage.RUSSIAN) }
