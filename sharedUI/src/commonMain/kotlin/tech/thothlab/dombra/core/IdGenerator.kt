package tech.thothlab.dombra.core

import kotlin.random.Random

/** Генератор идентификаторов (§7.2 ТЗ). Детерминируемая реализация — в тестах. */
fun interface IdGenerator {
    fun newId(): String
}

class RandomIdGenerator(private val random: Random = Random.Default) : IdGenerator {
    override fun newId(): String {
        val chars = "0123456789abcdef"
        return buildString(32) { repeat(32) { append(chars[random.nextInt(16)]) } }
    }
}
