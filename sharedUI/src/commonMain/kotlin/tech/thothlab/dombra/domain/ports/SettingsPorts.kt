package tech.thothlab.dombra.domain.ports

import kotlinx.coroutines.flow.Flow
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.EqBand
import tech.thothlab.dombra.domain.model.EqPreset
import tech.thothlab.dombra.domain.model.EqSettings
import tech.thothlab.dombra.domain.model.PlaybackSnapshot

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot)
    suspend fun loadPlaybackSnapshot(): PlaybackSnapshot?

    /** Локальное хранение API-ключей внешних провайдеров (не попадает в git/бандл). */
    suspend fun secret(key: String): String?
    suspend fun setSecret(key: String, value: String?)
}

interface EqRepository {
    val eqSettings: Flow<EqSettings>
    suspend fun updateEqSettings(transform: (EqSettings) -> EqSettings)

    fun presets(): Flow<List<EqPreset>>
    suspend fun bands(presetId: String): List<EqBand>
    suspend fun savePreset(preset: EqPreset, bands: List<EqBand>)
    suspend fun deletePreset(presetId: String)
}
