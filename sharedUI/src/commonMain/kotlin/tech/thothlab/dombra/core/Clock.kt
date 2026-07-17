package tech.thothlab.dombra.core

/** Источник времени (§7.2 ТЗ). Все timestamps в домене — epoch millis UTC. */
fun interface Clock {
    fun nowMs(): Long
}

internal expect fun systemNowMs(): Long

class SystemClock : Clock {
    override fun nowMs(): Long = systemNowMs()
}
