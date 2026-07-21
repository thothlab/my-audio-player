package tech.thothlab.dombra.data.lyrics

import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.LyricsLine
import tech.thothlab.dombra.domain.model.LyricsType

/**
 * Разбор встроенного текста песни в [Lyrics]. Если текст содержит LRC-таймкоды
 * `[mm:ss.xx]` — это синхронизированный текст (караоке), иначе — простой (§5.10 ТЗ).
 * Строки LRC-метаданных (`[ar:…]`, `[ti:…]`, `[offset:…]`) при синхронном разборе
 * игнорируются, так как их «время» не является цифровым таймкодом.
 */
object LyricsParser {

    // [mm:ss], [mm:ss.xx] или [mm:ss.xxx]; час-компонент допускаем как mmm (до 3 цифр).
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(stableId: String, raw: String, source: String, fetchedAt: Long): Lyrics {
        val hasSync = TIMESTAMP.containsMatchIn(raw)
        return Lyrics(
            trackStableId = stableId,
            type = if (hasSync) LyricsType.SYNCED else LyricsType.PLAIN,
            lines = if (hasSync) parseSynced(raw) else parsePlain(raw),
            source = source,
            fetchedAt = fetchedAt,
        )
    }

    private fun parsePlain(raw: String): List<LyricsLine> =
        raw.replace("\r\n", "\n").split("\n").map { LyricsLine(timeMs = null, text = it.trim()) }

    private fun parseSynced(raw: String): List<LyricsLine> {
        val out = mutableListOf<LyricsLine>()
        for (line in raw.replace("\r\n", "\n").split("\n")) {
            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty()) continue // строки-метаданные (`[ar:…]`) и пустые
            val text = line.substring(stamps.last().range.last + 1).trim()
            // Один текст может нести несколько таймкодов (повтор строки).
            for (m in stamps) {
                out += LyricsLine(timeMs = timeMsOf(m), text = text)
            }
        }
        return out.sortedBy { it.timeMs ?: 0L }
    }

    private fun timeMsOf(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100 // десятые доли
            2 -> frac.toLong() * 10 // сотые (стандарт LRC)
            else -> frac.take(3).toLong() // миллисекунды
        }
        return (min * 60 + sec) * 1000 + fracMs
    }
}
