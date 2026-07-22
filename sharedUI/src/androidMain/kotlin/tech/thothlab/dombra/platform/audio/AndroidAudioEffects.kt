package tech.thothlab.dombra.platform.audio

import android.media.audiofx.Equalizer
import kotlin.math.abs
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.presentation.player.AudioEffects

/**
 * Реальный эквалайзер через `android.media.audiofx.Equalizer`, привязанный к audio-session
 * ExoPlayer. UI даёт 10 полос (32 Гц … 16 кГц); устройство обычно имеет меньше полос —
 * значения мапятся на ближайшую по центральной частоте полосу устройства.
 */
class AndroidAudioEffects(audioSessionId: Int) : AudioEffects {

    private val log = Log.withTag("AudioFx")
    private val eq: Equalizer? = runCatching { Equalizer(0, audioSessionId) }
        .onFailure { log.w { "Equalizer init failed: ${it.message}" } }
        .getOrNull()

    override val supported: Boolean get() = eq != null

    override fun setEqualizerEnabled(enabled: Boolean) {
        runCatching { eq?.enabled = enabled }
    }

    override fun setEqualizerBands(freqsHz: IntArray, levelsDb: FloatArray) {
        val e = eq ?: return
        runCatching {
            val bands = e.numberOfBands.toInt()
            val range = e.bandLevelRange // [min, max] в миллибелах
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            for (b in 0 until bands) {
                val centerHz = e.getCenterFreq(b.toShort()) / 1000 // милиГц → Гц
                val ui = freqsHz.indices.minByOrNull { abs(freqsHz[it] - centerHz) } ?: continue
                val mb = (levelsDb[ui] * 100f).toInt().coerceIn(minMb, maxMb) // дБ → миллибел
                e.setBandLevel(b.toShort(), mb.toShort())
            }
        }
    }

    fun release() {
        runCatching { eq?.release() }
    }
}
