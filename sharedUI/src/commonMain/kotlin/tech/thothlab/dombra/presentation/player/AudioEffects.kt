package tech.thothlab.dombra.presentation.player

/**
 * Аудио-эффекты плеера (эквалайзер). Реальная реализация — Android (`android.media.audiofx`),
 * привязана к audio-session ExoPlayer в `PlaybackService`; прочие платформы — no-op.
 * Управляется через [AudioEffectsHolder] (процесс-синглтон): сервис ставит реализацию,
 * контроллер задаёт вкл/выкл, экран эквалайзера — уровни полос.
 */
interface AudioEffects {
    val supported: Boolean
    fun setEqualizerEnabled(enabled: Boolean)
    /** Уровни по частотам UI (Гц → дБ). Реализация мапит их на реальные полосы устройства. */
    fun setEqualizerBands(freqsHz: IntArray, levelsDb: FloatArray)
}

object NoOpAudioEffects : AudioEffects {
    override val supported: Boolean = false
    override fun setEqualizerEnabled(enabled: Boolean) = Unit
    override fun setEqualizerBands(freqsHz: IntArray, levelsDb: FloatArray) = Unit
}

/**
 * Процесс-синглтон: `PlaybackService` ставит реальную реализацию при появлении audio-session.
 * Помнит последнее заданное состояние (вкл/выкл + уровни) и переприменяет его при подмене
 * реализации — иначе enabled/полосы, выставленные до появления session, потерялись бы.
 */
object AudioEffectsHolder {
    @Volatile private var impl: AudioEffects = NoOpAudioEffects
    @Volatile private var lastEnabled: Boolean = false
    @Volatile private var lastFreqs: IntArray? = null
    @Volatile private var lastLevels: FloatArray? = null

    var current: AudioEffects
        get() = impl
        set(value) {
            impl = value
            value.setEqualizerEnabled(lastEnabled)
            val f = lastFreqs
            val l = lastLevels
            if (f != null && l != null) value.setEqualizerBands(f, l)
        }

    val supported: Boolean get() = impl.supported

    fun setEnabled(enabled: Boolean) {
        lastEnabled = enabled
        impl.setEqualizerEnabled(enabled)
    }

    fun setBands(freqsHz: IntArray, levelsDb: FloatArray) {
        lastFreqs = freqsHz
        lastLevels = levelsDb
        impl.setEqualizerBands(freqsHz, levelsDb)
    }
}
