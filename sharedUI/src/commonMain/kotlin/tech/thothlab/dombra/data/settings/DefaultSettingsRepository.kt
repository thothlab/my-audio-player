package tech.thothlab.dombra.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.PlaybackSnapshot
import tech.thothlab.dombra.domain.ports.SettingsRepository

/**
 * Настройки поверх multiplatform-settings (SharedPreferences / Preferences /
 * NSUserDefaults / localStorage). Модель — JSON-строка: толерантный декодинг
 * с дефолтами переживает добавление полей (§5.12).
 */
class DefaultSettingsRepository(
    private val settingsStore: Settings = Settings(),
) : SettingsRepository {

    private val log = Log.withTag("Settings")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val state = MutableStateFlow(load())

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(state.value).withAllSections()
        state.value = updated
        settingsStore.putString(KEY_SETTINGS, json.encodeToString(AppSettings.serializer(), updated))
    }

    override suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        settingsStore.putString(
            KEY_PLAYBACK,
            json.encodeToString(PlaybackSnapshot.serializer(), snapshot),
        )
    }

    override suspend fun loadPlaybackSnapshot(): PlaybackSnapshot? =
        settingsStore.getStringOrNull(KEY_PLAYBACK)?.let { raw ->
            runCatching { json.decodeFromString(PlaybackSnapshot.serializer(), raw) }
                .onFailure { log.w { "playback snapshot rejected: invalid format" } }
                .getOrNull()
        }

    override suspend fun secret(key: String): String? =
        settingsStore.getStringOrNull("$KEY_SECRET_PREFIX$key")?.ifEmpty { null }

    override suspend fun setSecret(key: String, value: String?) {
        if (value.isNullOrEmpty()) {
            settingsStore.remove("$KEY_SECRET_PREFIX$key")
        } else {
            settingsStore.putString("$KEY_SECRET_PREFIX$key", value)
        }
    }

    private fun load(): AppSettings =
        settingsStore.getStringOrNull(KEY_SETTINGS)?.let { raw ->
            runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
                .onFailure { log.w { "settings rejected, using defaults" } }
                .getOrNull()
        }?.withAllSections() ?: AppSettings()

    private companion object {
        const val KEY_SETTINGS = "dombra.settings.v1"
        const val KEY_PLAYBACK = "dombra.playback.v1"
        const val KEY_SECRET_PREFIX = "dombra.secret."
    }
}
